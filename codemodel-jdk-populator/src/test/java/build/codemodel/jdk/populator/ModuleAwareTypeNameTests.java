package build.codemodel.jdk.populator;

/*-
 * #%L
 * JDK Code Model
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import build.base.compile.testing.JavaFileObjects;
import build.base.parsing.ParseException;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.AnnotationValue;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.codemodel.jdk.descriptor.UsesDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces four more instances of the same bug fixed for {@code resolveField}/
 * {@code resolveParameter}/{@code resolveMethod} in {@link ModuleAwareSymbolResolutionTests}: a
 * {@code TypeName} is built from a raw fully-qualified/simple name via
 * {@code nameProvider.getTypeName(Optional.empty(), name)}, hardcoding "no module" instead of
 * resolving the referenced type's actual module. Whenever the referenced type lives in a real
 * named module, the resulting {@code TypeName} won't match the one under which that type's
 * descriptor is actually registered.
 *
 * <p>Sites covered:
 * <ul>
 *   <li>{@code TypeMirrorResolver#resolveAnnotationValue}'s fallback for a {@code Class}-valued
 *       annotation member whose {@code TypeMirror} isn't a {@code DeclaredType} (e.g. an
 *       array-typed {@code .class} literal)</li>
 *   <li>{@code JDKModuleDescriptor#populateFrom(ModuleTree)} - annotations on a
 *       {@code module-info.java} parsed from the javac AST</li>
 *   <li>{@code JDKModuleDescriptor#parse(CodeModel, String)} - the same annotation case, but via
 *       the hand-rolled text parser (used for {@code module-info.class}/manifest inspection)</li>
 *   <li>{@code JDKModuleDescriptor#typeUsage(String)} - the service/implementation types named in
 *       {@code uses}/{@code provides ... with ...} directives</li>
 * </ul>
 */
class ModuleAwareTypeNameTests {

    @Test
    void classAnnotationValueForArrayTypeCarriesDeclaringModule() {
        final var source = JavaFileObjects.forSourceString("com.example.Annotated", """
            package com.example;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @interface HasClass {
                Class<?> value();
            }
            @HasClass(String[].class)
            public class Annotated {}
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Annotated");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var hasClassUsage = descriptor.traits(AnnotationTypeUsage.class)
            .filter(a -> a.typeName().name().toString().equals("HasClass"))
            .findFirst().orElseThrow();

        final var valueAttr = hasClassUsage.values().findFirst().orElseThrow();
        assertThat(valueAttr.value()).isInstanceOf(AnnotationValue.Value.ClassRef.class);
        final var classRef = (AnnotationValue.Value.ClassRef) valueAttr.value();

        assertThat(classRef.typeName().moduleName())
            .as("String[]'s element type java.lang.String is declared in java.base; the ClassRef's "
                + "TypeName must account for that module rather than assuming the unnamed module")
            .isPresent();
    }

    @Test
    void moduleAnnotationCarriesDeclaringModuleViaJavac() {
        final var source = JavaFileObjects.forSourceString("module-info", """
            @Deprecated
            module com.example {
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var moduleName = codeModel.getNameProvider().getModuleName("com.example").orElseThrow();
        final var descriptor = (JDKModuleDescriptor) codeModel.getModuleDescriptor(moduleName).orElseThrow();

        final var annotation = descriptor.annotationClauses().findFirst().orElseThrow();

        assertThat(annotation.typeName().moduleName())
            .as("@Deprecated (java.lang.Deprecated) is declared in java.base; resolution must "
                + "account for that module rather than assuming the unnamed module")
            .isPresent();
    }

    @Test
    void moduleAnnotationCarriesDeclaringModuleViaTextParser() throws ParseException {
        final var codeModel = new JDKCodeModel(new NonCachingNameProvider());

        final var descriptor = JDKModuleDescriptor.parse(codeModel,
            "@java.lang.Deprecated module com.example { }");

        final var annotation = descriptor.annotationClauses().findFirst().orElseThrow();

        assertThat(annotation.typeName().moduleName())
            .as("java.lang.Deprecated is declared in java.base; resolution must account for that "
                + "module rather than assuming the unnamed module")
            .isPresent();
    }

    @Test
    void usesServiceTypeCarriesDeclaringModuleViaTextParser() throws ParseException {
        final var codeModel = new JDKCodeModel(new NonCachingNameProvider());

        final var descriptor = JDKModuleDescriptor.parse(codeModel, """
            module com.example {
                uses java.nio.file.spi.FileSystemProvider;
            }
            """);

        final var uses = descriptor.traits(UsesDescriptor.class).findFirst().orElseThrow();
        final var serviceTypeName = ((NamedTypeUsage) uses.serviceType()).typeName();

        assertThat(serviceTypeName.moduleName())
            .as("java.nio.file.spi.FileSystemProvider is declared in java.base; resolution must "
                + "account for that module rather than assuming the unnamed module")
            .isPresent();
    }

    // ---- type variables ----------------------------------------------------
    //
    // TypeMirrorResolver#visitTypeVariable and JDKCodeModel#getTypeUsage(Type)'s TypeVariable
    // branch both build a type variable's TypeName from nothing but its simple name (e.g. "T"),
    // with no module, namespace, or enclosing-type distinction. Nothing currently looks up a
    // TypeDescriptor by a type variable's TypeName, so this doesn't manifest as a resolution
    // failure the way the sites above do - but it does mean two distinct type variables that
    // happen to share a name collide into an equal TypeName, which is observably wrong on its own.

    @Test
    void typeVariablesFromDifferentDeclaringTypesAreDistinguishableViaJavac() {
        final var source = JavaFileObjects.forSourceString("com.example.Types", """
            package com.example;
            public class Types {
                public static class Foo<T> {
                    public T identity(T value) { return value; }
                }
                public static class Bar<T> {
                    public T identity(T value) { return value; }
                }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var fooType = codeModel.getJDKTypeDescriptor("com.example.Types$Foo").orElseThrow();
        final var barType = codeModel.getJDKTypeDescriptor("com.example.Types$Bar").orElseThrow();

        final var fooReturnTypeName = ((NamedTypeUsage) fooType.traits(MethodDescriptor.class)
            .filter(md -> md.methodName().name().toString().equals("identity"))
            .findFirst().orElseThrow()
            .returnType()).typeName();
        final var barReturnTypeName = ((NamedTypeUsage) barType.traits(MethodDescriptor.class)
            .filter(md -> md.methodName().name().toString().equals("identity"))
            .findFirst().orElseThrow()
            .returnType()).typeName();

        assertThat(fooReturnTypeName)
            .as("Foo<T>.identity()'s T and Bar<T>.identity()'s T are distinct type variables "
                + "declared on distinct classes; their TypeNames must not collide just because "
                + "both happen to be named T")
            .isNotEqualTo(barReturnTypeName);
    }

    @Test
    void typeVariableTypeNameCarriesDeclaringModuleViaJavac() {
        final var moduleInfo = JavaFileObjects.forSourceString("module-info", """
            module com.example {
            }
            """);
        final var source = JavaFileObjects.forSourceString("com.example.Holder", """
            package com.example;
            public class Holder<T> {
                public T value() { return null; }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source, moduleInfo)));

        final var moduleName = codeModel.getNameProvider().getModuleName("com.example").orElseThrow();
        final var holder = codeModel.getJDKTypeDescriptor("com.example.Holder").orElseThrow();

        final var returnTypeName = ((NamedTypeUsage) holder.traits(MethodDescriptor.class)
            .filter(md -> md.methodName().name().toString().equals("value"))
            .findFirst().orElseThrow()
            .returnType()).typeName();

        assertThat(returnTypeName.moduleName())
            .as("Holder<T>'s T is declared on a class in module com.example; its TypeName must "
                + "carry that module rather than assuming the unnamed module")
            .contains(moduleName);

        // The declaration site (Holder's ParameterizedTypeDescriptor) must resolve T to the same
        // module-scoped TypeName - not the bare "T" that resolveTypeParameter used to hand back - so
        // a usage of T and its declaration line up.
        final var declaredTypeVariableName = holder.getTrait(ParameterizedTypeDescriptor.class).orElseThrow()
            .typeVariables()
            .findFirst().orElseThrow()
            .typeName();

        assertThat(declaredTypeVariableName.moduleName())
            .as("the declared type parameter <T> on Holder must carry its declaring module too")
            .contains(moduleName);
        assertThat(declaredTypeVariableName)
            .as("the declared <T> and a usage of T must resolve to the same TypeName")
            .isEqualTo(returnTypeName);
    }

    @Test
    void typeVariablesFromDifferentDeclaringTypesAreDistinguishableViaReflection() {
        final var codeModel = new JDKCodeModel(new NonCachingNameProvider());

        // java.util.List<E> and java.util.Set<E> both declare a type parameter named "E".
        final var listElementTypeVariable = List.class.getTypeParameters()[0];
        final var setElementTypeVariable = Set.class.getTypeParameters()[0];

        final var listElementTypeName =
            ((NamedTypeUsage) codeModel.getTypeUsage(listElementTypeVariable)).typeName();
        final var setElementTypeName =
            ((NamedTypeUsage) codeModel.getTypeUsage(setElementTypeVariable)).typeName();

        assertThat(listElementTypeName)
            .as("List<E>'s E and Set<E>'s E are distinct type variables declared on distinct "
                + "classes; their TypeNames must not collide just because both happen to be named E")
            .isNotEqualTo(setElementTypeName);

        final var javaBase = codeModel.getNameProvider().getModuleName("java.base");
        assertThat(listElementTypeName.moduleName())
            .as("List is in java.base, so the reflection path must scope its E under that module")
            .isEqualTo(javaBase);
        assertThat(listElementTypeName.name().toString()).isEqualTo("E");
    }
}
