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
import build.codemodel.expression.Cast;
import build.codemodel.expression.NumericLiteral;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.imperative.Return;
import build.codemodel.jdk.descriptor.MethodBodyDescriptor;
import build.codemodel.jdk.expression.ArrayDimensionOrder;
import build.codemodel.jdk.expression.AssignmentOperator;
import build.codemodel.jdk.expression.BitwiseBinary;
import build.codemodel.jdk.expression.BitwiseOperator;
import build.codemodel.jdk.expression.CompoundAssignment;
import build.codemodel.jdk.expression.Identifier;
import build.codemodel.jdk.expression.InstanceOf;
import build.codemodel.jdk.expression.MethodInvocation;
import build.codemodel.jdk.expression.MethodReference;
import build.codemodel.jdk.expression.NewArray;
import build.codemodel.jdk.expression.NewObject;
import build.codemodel.jdk.expression.PostfixOperator;
import build.codemodel.jdk.expression.PostfixUnary;
import build.codemodel.jdk.expression.PrefixOperator;
import build.codemodel.jdk.expression.PrefixUnary;
import build.codemodel.jdk.expression.Symbol;
import build.codemodel.jdk.populator.descriptor.SourceLocation;
import build.codemodel.jdk.statement.ExpressionStatement;
import build.codemodel.jdk.statement.LocalVariableDeclaration;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests for correct capture of source-tree information in {@link JdkExpressionConverter}.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
class ExpressionCaptureFidelityTests {

    @Test
    void shouldCaptureCastTargetType() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Caster", """
                package build.codemodel.jdk.example;
                public class Caster {
                    public void run(Object obj) {
                        String s = (String) obj;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Caster");
        final var run = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();
        final var body = run.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();
        final var cast = (Cast) decl.initializer().orElseThrow();

        assertThat(cast.targetType()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) cast.targetType()).typeName().canonicalName())
            .isEqualTo("java.lang.String");
    }

    @Test
    void shouldCaptureInstanceOfPatternBindingVariable() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.PatternChecker", """
                package build.codemodel.jdk.example;
                public class PatternChecker {
                    public boolean check(Object obj) {
                        return obj instanceof String s;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.PatternChecker");
        final var check = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("check"))
            .findFirst().orElseThrow();
        final var body = check.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var instanceOf = (InstanceOf) returnStmt.expression().orElseThrow();

        assertThat(instanceOf.checkedType()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) instanceOf.checkedType()).typeName().canonicalName()).isEqualTo("java.lang.String");
        assertThat(instanceOf.bindingVariable()).isEqualTo(Optional.of("s"));
    }

    @Test
    void shouldCaptureNewObjectTypeArguments() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.ListFactory", """
                package build.codemodel.jdk.example;
                import java.util.ArrayList;
                public class ListFactory {
                    public ArrayList<String> create() {
                        return new ArrayList<String>();
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.ListFactory");
        final var create = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("create"))
            .findFirst().orElseThrow();
        final var body = create.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var newObject = (NewObject) returnStmt.expression().orElseThrow();

        assertThat(newObject.instantiatedType()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) newObject.instantiatedType()).typeName().canonicalName()).isEqualTo("java.util.ArrayList");

        final var typeArgs = newObject.typeArguments().toList();
        assertThat(typeArgs).hasSize(1);
        assertThat(typeArgs.getFirst()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) typeArgs.getFirst()).typeName().canonicalName()).isEqualTo("java.lang.String");
    }

    @Test
    void shouldCaptureMethodInvocationTypeWitnesses() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Widgets", """
                package build.codemodel.jdk.example;
                import java.util.Collections;
                import java.util.List;
                public class Widgets {
                    public List<String> empty() {
                        return Collections.<String>emptyList();
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Widgets");
        final var invocation = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(MethodInvocation.class)
            .findFirst()
            .orElseThrow();

        final var witnesses = invocation.typeWitnesses().toList();
        assertThat(witnesses).hasSize(1);
        assertThat(witnesses.getFirst()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) witnesses.getFirst()).typeName().canonicalName())
            .isEqualTo("java.lang.String");
        assertThat(witnesses.getFirst().getTrait(SourceLocation.FilePosition.class))
            .as("each type witness carries its own source position")
            .isPresent();
    }

    @Test
    void shouldCaptureMultipleMethodInvocationTypeWitnessesInOrder() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Pairs", """
                package build.codemodel.jdk.example;
                import java.util.Map;
                public class Pairs {
                    public Map.Entry<String, Integer> one() {
                        return Map.<String, Integer>entry("a", 1);
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Pairs");
        final var invocation = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(MethodInvocation.class)
            .findFirst()
            .orElseThrow();

        final var witnesses = invocation.typeWitnesses().toList();
        assertThat(witnesses)
            .extracting(w -> ((NamedTypeUsage) w).typeName().canonicalName())
            .containsExactly("java.lang.String", "java.lang.Integer");
        assertThat(witnesses).allSatisfy(w ->
            assertThat(w.getTrait(SourceLocation.FilePosition.class)).isPresent());
    }

    @Test
    void shouldCaptureMethodReferenceTypeWitnesses() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Refs", """
                package build.codemodel.jdk.example;
                import java.util.List;
                import java.util.function.Function;
                public class Refs {
                    public Function<Object[], List<String>> f() {
                        return Refs::<String>wrap;
                    }
                    static <T> List<T> wrap(Object[] a) {
                        return null;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Refs");
        final var reference = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(MethodReference.class)
            .findFirst()
            .orElseThrow();

        final var witnesses = reference.typeWitnesses().toList();
        assertThat(witnesses).hasSize(1);
        assertThat(witnesses.getFirst()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) witnesses.getFirst()).typeName().canonicalName())
            .isEqualTo("java.lang.String");
        assertThat(witnesses.getFirst().getTrait(SourceLocation.FilePosition.class))
            .as("each type witness carries its own source position")
            .isPresent();
    }

    @Test
    void shouldCaptureConstructorReferenceTypeWitnesses() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.CtorRefs", """
                package build.codemodel.jdk.example;
                import java.util.function.Function;
                public class CtorRefs {
                    static final class Box {
                        <T> Box(T seed) {
                        }
                    }
                    public Function<Object, Box> f() {
                        return Box::<String>new;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.CtorRefs");
        final var reference = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(MethodReference.class)
            .findFirst()
            .orElseThrow();

        final var witnesses = reference.typeWitnesses().toList();
        assertThat(witnesses).hasSize(1);
        assertThat(witnesses.getFirst()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) witnesses.getFirst()).typeName().canonicalName())
            .isEqualTo("java.lang.String");
        assertThat(witnesses.getFirst().getTrait(SourceLocation.FilePosition.class))
            .as("each type witness carries its own source position")
            .isPresent();
    }

    @Test
    void shouldCaptureNewObjectConstructorTypeWitnesses() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Maker", """
                package build.codemodel.jdk.example;
                public class Maker {
                    <T> Maker(T seed) {
                    }
                    public static Maker make() {
                        return new <String>Maker("x");
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Maker");
        final var newObject = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewObject.class)
            .findFirst()
            .orElseThrow();

        final var witnesses = newObject.typeWitnesses().toList();
        assertThat(witnesses).hasSize(1);
        assertThat(witnesses.getFirst()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) witnesses.getFirst()).typeName().canonicalName())
            .isEqualTo("java.lang.String");
        assertThat(newObject.typeArguments())
            .as("constructor type witnesses are distinct from the instantiated type's own arguments")
            .isEmpty();
        assertThat(witnesses.getFirst().getTrait(SourceLocation.FilePosition.class))
            .as("each type witness carries its own source position")
            .isPresent();
    }

    @Test
    void shouldLeaveTypeWitnessesEmptyWhenNoneWritten() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Plain", """
                package build.codemodel.jdk.example;
                import java.util.Collections;
                import java.util.List;
                public class Plain {
                    public List<String> empty() {
                        return Collections.emptyList();
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Plain");
        final var invocation = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(MethodInvocation.class)
            .findFirst()
            .orElseThrow();

        assertThat(invocation.typeWitnesses()).isEmpty();
    }

    @Test
    void shouldCaptureNewArrayElementType() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.ArrayFactory", """
                package build.codemodel.jdk.example;
                public class ArrayFactory {
                    public String[] create(int n) {
                        return new String[n];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.ArrayFactory");
        final var create = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("create"))
            .findFirst().orElseThrow();
        final var body = create.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var newArray = (NewArray) returnStmt.expression().orElseThrow();

        assertThat(newArray.elementType()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) newArray.elementType()).typeName().canonicalName()).isEqualTo("java.lang.String");
    }

    @Test
    void shouldCapturePerDimensionArrayCreationAnnotations() {
        final var annotationA = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.DimA", """
                package build.codemodel.jdk.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target(ElementType.TYPE_USE)
                public @interface DimA {
                }
                """);
        final var annotationB = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.DimB", """
                package build.codemodel.jdk.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target(ElementType.TYPE_USE)
                public @interface DimB {
                }
                """);
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.DimArrays", """
                package build.codemodel.jdk.example;
                public class DimArrays {
                    public Object create(int n) {
                        return new int @DimA [n] @DimB [4];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(annotationA, annotationB, source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.DimArrays");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        assertThat(newArray.traits(AnnotationTypeUsage.class))
            .extracting(
                usage -> usage.typeName().canonicalName(),
                usage -> usage.trait(ArrayDimensionOrder.class).dimension())
            .containsExactlyInAnyOrder(
                tuple("build.codemodel.jdk.example.DimA", 0),
                tuple("build.codemodel.jdk.example.DimB", 1));
        assertThat(newArray.traits(AnnotationTypeUsage.class))
            .allSatisfy(usage -> assertThat(usage.getTrait(SourceLocation.FilePosition.class))
                .as("each dimension annotation carries its own source position")
                .isPresent());
    }

    @Test
    void shouldAttachNoAnnotationTypeUsagesToAnUnannotatedArrayCreation() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.PlainArrays", """
                package build.codemodel.jdk.example;
                public class PlainArrays {
                    public int[][] create(int n) {
                        return new int[n][4];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.PlainArrays");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        assertThat(newArray.traits(AnnotationTypeUsage.class)).isEmpty();
        assertThat(newArray.elementType().traits(AnnotationTypeUsage.class)).isEmpty();
    }

    @Test
    void shouldCaptureBaseTypeArrayCreationAnnotation() {
        final var annotation = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Base", """
                package build.codemodel.jdk.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target(ElementType.TYPE_USE)
                public @interface Base {
                }
                """);
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.BaseArrays", """
                package build.codemodel.jdk.example;
                public class BaseArrays {
                    public Object create(int n) {
                        return new @Base int[n];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(annotation, source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.BaseArrays");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        assertThat(newArray.elementType().traits(AnnotationTypeUsage.class))
            .singleElement()
            .satisfies(usage ->
                assertThat(usage.typeName().canonicalName()).isEqualTo("build.codemodel.jdk.example.Base"));
    }

    @Test
    void shouldCaptureMultipleAnnotationsOnASingleArrayDimension() {
        final var annotationA = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.DimA", """
                package build.codemodel.jdk.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target(ElementType.TYPE_USE)
                public @interface DimA {
                }
                """);
        final var annotationB = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.DimB", """
                package build.codemodel.jdk.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target(ElementType.TYPE_USE)
                public @interface DimB {
                }
                """);
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.MultiDimAnno", """
                package build.codemodel.jdk.example;
                public class MultiDimAnno {
                    public Object create(int n) {
                        return new int @DimA @DimB [n];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(annotationA, annotationB, source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.MultiDimAnno");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        assertThat(newArray.traits(AnnotationTypeUsage.class))
            .extracting(
                usage -> usage.typeName().canonicalName(),
                usage -> usage.trait(ArrayDimensionOrder.class).dimension())
            .containsExactlyInAnyOrder(
                tuple("build.codemodel.jdk.example.DimA", 0),
                tuple("build.codemodel.jdk.example.DimB", 0));
    }

    @Test
    void shouldCaptureAnnotationOnATrailingArrayDimensionBracket() {
        final var annotation = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.DimB", """
                package build.codemodel.jdk.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target(ElementType.TYPE_USE)
                public @interface DimB {
                }
                """);
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.TrailingDimAnno", """
                package build.codemodel.jdk.example;
                public class TrailingDimAnno {
                    public Object create(int n) {
                        return new int[n] @DimB [];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(annotation, source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.TrailingDimAnno");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        assertThat(newArray.traits(AnnotationTypeUsage.class))
            .extracting(
                usage -> usage.typeName().canonicalName(),
                usage -> usage.trait(ArrayDimensionOrder.class).dimension())
            .containsExactly(tuple("build.codemodel.jdk.example.DimB", 1));
    }

    @Test
    void shouldCaptureABracketAnnotationOnAnArrayCreationWithInitializerAsABaseTypeAnnotation() {
        final var annotation = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.DimA", """
                package build.codemodel.jdk.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target(ElementType.TYPE_USE)
                public @interface DimA {
                }
                """);
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.InitializerDimAnno", """
                package build.codemodel.jdk.example;
                public class InitializerDimAnno {
                    public Object create() {
                        return new int @DimA [] {1, 2, 3};
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(annotation, source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.InitializerDimAnno");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        // With an array initializer there is no dimension expression, and javac's parser folds a
        // bracket-position annotation into NewArrayTree.getAnnotations() — the same slot as a genuine
        // base-type annotation (`new @DimA int[]{…}`), with no way to tell them apart. So it lands on
        // the element type, not as an ArrayDimensionOrder-tagged dimension annotation.
        assertThat(newArray.traits(AnnotationTypeUsage.class)).isEmpty();
        assertThat(newArray.elementType().traits(AnnotationTypeUsage.class))
            .singleElement()
            .satisfies(usage ->
                assertThat(usage.typeName().canonicalName()).isEqualTo("build.codemodel.jdk.example.DimA"));
    }

    @Test
    void shouldCaptureNewArrayInitializerValues() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.ArrayLiteralFactory", """
                package build.codemodel.jdk.example;
                public class ArrayLiteralFactory {
                    public int[] create() {
                        return new int[]{1, 2, 3};
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.ArrayLiteralFactory");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        final var initializers = newArray.initializers().toList();
        assertThat(initializers).hasSize(3);
        assertThat(initializers.stream()
            .map(expr -> (NumericLiteral) expr)
            .map(literal -> literal.value().intValue()))
            .containsExactly(1, 2, 3);

    }

    @Test
    void shouldCaptureQualifiedInstanceCreationOuterInstance() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Outer", """
                package build.codemodel.jdk.example;
                public class Outer {
                    public class Inner {
                    }
                    public Inner create(Outer outer) {
                        return outer.new Inner();
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Outer");
        final var newObject = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewObject.class)
            .findFirst()
            .orElseThrow();

        assertThat(newObject.outerInstance()).isPresent();
        final var outerIdentifier = (Identifier) newObject.outerInstance().orElseThrow();
        assertThat(outerIdentifier.name()).isEqualTo("outer");

        // the outer-instance qualifier is converted via the normal convert() dispatch, so it gets
        // Symbol resolution and a FilePosition just like any other identifier; trait() throws if missing
        outerIdentifier.trait(Symbol.class);
        outerIdentifier.trait(SourceLocation.FilePosition.class);
    }

    @Test
    void shouldCaptureBitwiseOperator() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Bits", """
                package build.codemodel.jdk.example;
                public class Bits {
                    public int run(int a, int b) { return a << b; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Bits");
        final var body = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow()
            .getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var bitwise = (BitwiseBinary) ((Return) body.statements().findFirst().orElseThrow()).expression().orElseThrow();

        assertThat(bitwise.operator()).isEqualTo(BitwiseOperator.LEFT_SHIFT);
    }

    @Test
    void shouldCapturePrefixOperator() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Counter", """
                package build.codemodel.jdk.example;
                public class Counter {
                    public int run(int x) { return ++x; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Counter");
        final var body = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow()
            .getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var prefix = (PrefixUnary) ((Return) body.statements().findFirst().orElseThrow()).expression().orElseThrow();

        assertThat(prefix.operator()).isEqualTo(PrefixOperator.INCREMENT);
    }

    @Test
    void shouldCapturePostfixOperator() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Postfix", """
                package build.codemodel.jdk.example;
                public class Postfix {
                    public void run(int[] arr) { arr[0]--; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Postfix");
        final var body = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow()
            .getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var postfix = (PostfixUnary) ((ExpressionStatement) body.statements().findFirst().orElseThrow()).expression();

        assertThat(postfix.operator()).isEqualTo(PostfixOperator.DECREMENT);
    }

    @Test
    void shouldCaptureAssignmentOperator() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Assigner", """
                package build.codemodel.jdk.example;
                public class Assigner {
                    public void run(int[] arr) { arr[0] += 1; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Assigner");
        final var body = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow()
            .getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var assign = (CompoundAssignment) ((ExpressionStatement) body.statements().findFirst().orElseThrow()).expression();

        assertThat(assign.operator()).isEqualTo(AssignmentOperator.PLUS);
    }

    @Test
    void shouldCaptureClassLiteralOnReferenceType() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.TypeTokens", """
                package build.codemodel.jdk.example;
                public class TypeTokens {
                    public Class<?> stringClass() { return String.class; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.TypeTokens");
        final var body = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("stringClass"))
            .findFirst().orElseThrow()
            .getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnExpr = ((Return) body.statements().findFirst().orElseThrow())
            .expression().orElseThrow();

        assertThat(returnExpr).isInstanceOf(build.codemodel.jdk.expression.ClassLiteral.class);
        final var literal = (build.codemodel.jdk.expression.ClassLiteral) returnExpr;
        assertThat(((NamedTypeUsage) literal.referencedType()).typeName().toString()).contains("String");
    }

    @Test
    void shouldCaptureAnonymousClassBody() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.RunnableFactory", """
                package build.codemodel.jdk.example;
                public class RunnableFactory {
                    public Runnable create() {
                        return new Runnable() {
                            @Override
                            public void run() {
                            }
                        };
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.RunnableFactory");

        final var newObject = codeModel.getTypeDescriptor(typeName).stream()
            .flatMap(td -> td.traits(MethodDescriptor.class))
            .map(m -> m.trait(MethodBodyDescriptor.class))
            .flatMap(mb -> mb.composition(NewObject.class))
            .findFirst()
            .orElseThrow();

        assertThat(newObject.instantiatedType()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) newObject.instantiatedType()).typeName().canonicalName())
            .as("NewObject only models the declared supertype, never the anonymous subclass itself")
            .isEqualTo("java.lang.Runnable");

        final var anonymousClassDescriptors = codeModel.typeDescriptors()
            .filter(td -> td.typeName().canonicalName().startsWith("build.codemodel.jdk.example.RunnableFactory."))
            .toList();

        assertThat(anonymousClassDescriptors)
            .as("the anonymous class itself should still get its own TypeDescriptor, "
                + "even though NewObject only models the declared supertype")
            .isNotEmpty();
    }

    @Test
    void shouldCaptureClassLiteralOnPrimitiveType() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.PrimitiveTokens", """
                package build.codemodel.jdk.example;
                public class PrimitiveTokens {
                    public Class<?> intClass() { return int.class; }
                    public Class<?> voidClass() { return void.class; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.PrimitiveTokens");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var intClassBody = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("intClass"))
            .findFirst().orElseThrow()
            .getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var intReturn = ((Return) intClassBody.statements().findFirst().orElseThrow())
            .expression().orElseThrow();

        assertThat(intReturn)
            .as("int.class should be a ClassLiteral, not a FieldAccess")
            .isInstanceOf(build.codemodel.jdk.expression.ClassLiteral.class);

        final var voidClassBody = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("voidClass"))
            .findFirst().orElseThrow()
            .getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var voidReturn = ((Return) voidClassBody.statements().findFirst().orElseThrow())
            .expression().orElseThrow();

        assertThat(voidReturn)
            .as("void.class should be a ClassLiteral, not a FieldAccess")
            .isInstanceOf(build.codemodel.jdk.expression.ClassLiteral.class);
    }
}
