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
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.jdk.descriptor.MemberTypeDescriptor;
import build.codemodel.jdk.descriptor.MethodBodyDescriptor;
import build.codemodel.jdk.descriptor.ReceiverAnnotation;
import build.codemodel.jdk.descriptor.RecordComponentDescriptor;
import build.codemodel.jdk.expression.Lambda;
import build.codemodel.jdk.statement.CatchClause;
import build.codemodel.jdk.statement.EnhancedFor;
import build.codemodel.jdk.statement.LocalVariableDeclaration;
import build.codemodel.jdk.statement.Try;
import build.codemodel.objectoriented.descriptor.ConstructorDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for three related annotation-capture gaps: receiver parameter annotations,
 * type-parameter declaration annotations, and annotations on local variables/lambda
 * parameters/enhanced-for variables/catch parameters.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
class AnnotationCaptureGapsTests {

    @Test
    void shouldCaptureReceiverParameterAnnotationOnMethod() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Foo", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;
            
            @Target(ElementType.TYPE_USE)
            @interface NonNull {}
            
            public class Foo {
                public void run(@NonNull Foo this) {
                }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();

        final var receiverAnnotations = method.traits(ReceiverAnnotation.class)
            .map(ReceiverAnnotation::annotation)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(receiverAnnotations).containsExactly("NonNull");
    }

    @Test
    void shouldCaptureReceiverParameterAnnotationOnInnerClassConstructor() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Outer", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;
            
            @Target(ElementType.TYPE_USE)
            @interface NonNull {}
            
            public class Outer {
                public class Inner {
                    public Inner(@NonNull Outer Outer.this) {
                    }
                }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var outerTypeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Outer");
        final var outerDescriptor = codeModel.getTypeDescriptor(outerTypeName).orElseThrow();
        final var innerTypeName = outerDescriptor.traits(MemberTypeDescriptor.class)
            .map(MemberTypeDescriptor::memberTypeName)
            .filter(n -> n.name().toString().equals("Inner"))
            .findFirst().orElseThrow();
        final var constructor = codeModel.getTypeDescriptor(innerTypeName).orElseThrow()
            .traits(ConstructorDescriptor.class)
            .findFirst().orElseThrow();

        final var receiverAnnotations = constructor.traits(ReceiverAnnotation.class)
            .map(ReceiverAnnotation::annotation)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(receiverAnnotations).containsExactly("NonNull");
    }

    @Test
    void shouldCaptureAnnotationOnTypeParameterDeclaration() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Box", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;
            
            @interface Marker {}
            
            public class Box<@Marker T extends Number> {
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Box");
        final var typeParameters = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .getTrait(ParameterizedTypeDescriptor.class).orElseThrow()
            .typeVariables().toList();
        assertThat(typeParameters).hasSize(1);

        final var annotations = typeParameters.getFirst().traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(annotations).containsExactly("Marker");
    }

    @Test
    void shouldCaptureAnnotationOnLocalVariable() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Foo", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;
            
            @interface Local {}
            
            public class Foo {
                public void run() {
                    @Local String s = "x";
                }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();

        final var annotations = decl.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(annotations).containsExactly("Local");
    }

    @Test
    void shouldCaptureAnnotationOnLambdaParameter() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Foo", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;
            import java.util.function.Function;
            
            @interface Param {}
            
            public class Foo {
                public void run() {
                    Function<String, String> f = (@Param String s) -> s;
                }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();
        final var lambda = (Lambda) decl.initializer().orElseThrow();
        final var parameter = lambda.parameters().findFirst().orElseThrow();

        final var annotations = parameter.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(annotations).containsExactly("Param");
    }

    @Test
    void shouldCaptureAnnotationOnEnhancedForVariable() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Foo", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;
            import java.util.List;
            
            @interface Local {}
            
            public class Foo {
                public void run(List<String> items) {
                    for (@Local String item : items) {
                    }
                }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var enhancedFor = (EnhancedFor) body.statements().findFirst().orElseThrow();

        final var annotations = enhancedFor.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(annotations).containsExactly("Local");
    }

    @Test
    void shouldCaptureAnnotationOnCatchParameter() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Foo", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;
            
            @interface Local {}
            
            public class Foo {
                public void run() {
                    try {
                        throw new RuntimeException();
                    } catch (@Local RuntimeException e) {
                    }
                }
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var tryStatement = (Try) body.statements().findFirst().orElseThrow();
        final var catchClause = (CatchClause) tryStatement.catches().findFirst().orElseThrow();

        final var annotations = catchClause.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(annotations).containsExactly("Local");
    }

    @Test
    void shouldCaptureAnnotationOnEnumConstant() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Color", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;

            @interface Marker {}

            public enum Color {
                @Deprecated @Marker RED,
                GREEN;
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Color");
        final var constants = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(build.codemodel.jdk.descriptor.EnumConstantDescriptor.class)
            .toList();
        assertThat(constants).hasSize(2);

        final var red = constants.stream()
            .filter(c -> c.name().toString().equals("RED"))
            .findFirst().orElseThrow();
        final var green = constants.stream()
            .filter(c -> c.name().toString().equals("GREEN"))
            .findFirst().orElseThrow();

        assertThat(red.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactlyInAnyOrder("Deprecated", "Marker");
        assertThat(green.traits(AnnotationTypeUsage.class).toList()).isEmpty();
    }

    @Test
    void shouldCaptureRecordComponentOnlyAnnotation() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Point", """
            package build.codemodel.jdk.example;
            import java.lang.annotation.*;

            @Target(ElementType.RECORD_COMPONENT)
            @interface OnlyComponent {}

            public record Point(@OnlyComponent int x, int y) {
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Point");
        final var components = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(RecordComponentDescriptor.class)
            .toList();
        assertThat(components).hasSize(2);

        final var x = components.stream()
            .filter(c -> c.name().toString().equals("x"))
            .findFirst().orElseThrow();
        final var y = components.stream()
            .filter(c -> c.name().toString().equals("y"))
            .findFirst().orElseThrow();

        assertThat(x.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactly("OnlyComponent");
        assertThat(y.traits(AnnotationTypeUsage.class).toList()).isEmpty();
    }
}
