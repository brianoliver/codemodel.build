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
import build.codemodel.expression.Addition;
import build.codemodel.expression.Cast;
import build.codemodel.expression.Expression;
import build.codemodel.expression.Negative;
import build.codemodel.expression.NumericLiteral;
import build.codemodel.foundation.descriptor.ThrowableDescriptor;
import build.codemodel.foundation.usage.IntersectionTypeUsage;
import build.codemodel.imperative.Return;
import build.codemodel.jdk.descriptor.EnumConstantDescriptor;
import build.codemodel.jdk.descriptor.InitializerBlockDescriptor;
import build.codemodel.jdk.descriptor.JDKTypeDescriptor;
import build.codemodel.jdk.descriptor.MethodBodyDescriptor;
import build.codemodel.jdk.descriptor.PermitsTypeDescriptor;
import build.codemodel.jdk.descriptor.RecordComponentDescriptor;
import build.codemodel.jdk.expression.ArrayAccess;
import build.codemodel.jdk.expression.ClassLiteral;
import build.codemodel.jdk.expression.CompoundAssignment;
import build.codemodel.jdk.expression.InstanceOf;
import build.codemodel.jdk.expression.Lambda;
import build.codemodel.jdk.expression.NewArray;
import build.codemodel.jdk.expression.NewObject;
import build.codemodel.jdk.expression.Parenthesized;
import build.codemodel.jdk.expression.SwitchExpression;
import build.codemodel.jdk.expression.Ternary;
import build.codemodel.jdk.populator.descriptor.SourceLocation;
import build.codemodel.jdk.statement.EnhancedFor;
import build.codemodel.jdk.statement.LocalVariableDeclaration;
import build.codemodel.jdk.statement.Try;
import build.codemodel.objectoriented.descriptor.ConstructorDescriptor;
import build.codemodel.objectoriented.descriptor.FieldDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for source position capture ({@link SourceLocation}) via {@link JdkInitializer}.
 *
 * @author reed.vonredwitz
 * @since May-2026
 */
class SourceLocationTests {

    @Test
    void fieldShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int value;
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var field = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(FieldDescriptor.class).findFirst().orElseThrow();

        assertThat(field.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var location = field.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(location.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(location.endPosition()).isGreaterThan(location.startPosition());
    }

    @Test
    void methodShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void hello() {}
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();

        assertThat(method.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var location = method.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(location.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(location.endPosition()).isGreaterThan(location.startPosition());
    }

    @Test
    void laterDeclaredMemberShouldHaveHigherStartPosition() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Ordered", """
                package com.example;
                public class Ordered {
                    public int first;
                    public int second;
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Ordered");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var firstPos = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("first"))
            .findFirst().orElseThrow()
            .getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();

        final var secondPos = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("second"))
            .findFirst().orElseThrow()
            .getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();

        assertThat(secondPos).isGreaterThan(firstPos);
    }

    @Test
    void methodParameterShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void hello(String name) {}
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var param = method.formalParameters().findFirst().orElseThrow();

        assertThat(param.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var location = param.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(location.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(location.endPosition()).isGreaterThan(location.startPosition());
    }

    @Test
    void constructorParameterShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public Foo(String name) {}
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var ctor = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(ConstructorDescriptor.class).findFirst().orElseThrow();
        final var param = ctor.formalParameters().findFirst().orElseThrow();

        assertThat(param.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void localVariableDeclarationShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void run() {
                        String value = "hello";
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();

        assertThat(decl.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var location = decl.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(location.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(location.endPosition()).isGreaterThan(location.startPosition());
    }

    @Test
    void enhancedForLoopVariableShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.util.List;
                public class Foo {
                    public void run(List<String> values) {
                        for (String value : values) {}
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var loop = (EnhancedFor) body.statements().findFirst().orElseThrow();

        assertThat(loop.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void lambdaParameterShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.util.Comparator;
                public class Foo {
                    public Comparator<String> comparator() {
                        return (String a, String b) -> a.compareTo(b);
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var lambda = (Lambda) body.statements()
            .map(s -> s instanceof Return r ? r.expression().orElse(null) : (Expression) null)
            .filter(e -> e instanceof Lambda)
            .findFirst().orElseThrow();

        final var param = lambda.parameters().findFirst().orElseThrow();
        assertThat(param.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var location = param.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(location.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(location.endPosition()).isGreaterThan(location.startPosition());
    }

    @Test
    void enumConstantShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Color", """
                package com.example;
                public enum Color {
                    RED,
                    GREEN
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Color");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var red = descriptor.traits(EnumConstantDescriptor.class)
            .filter(c -> c.name().toString().equals("RED"))
            .findFirst().orElseThrow();
        final var green = descriptor.traits(EnumConstantDescriptor.class)
            .filter(c -> c.name().toString().equals("GREEN"))
            .findFirst().orElseThrow();

        assertThat(red.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var redLocation = red.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(redLocation.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(redLocation.endPosition()).isGreaterThan(redLocation.startPosition());

        final var greenLocation = green.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(greenLocation.startPosition()).isGreaterThan(redLocation.startPosition());
    }

    @Test
    void initializerBlockShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Singleton", """
                package com.example;
                public class Singleton {
                    private static final int VALUE;
                    static {
                        VALUE = 42;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Singleton");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();
        final var initBlock = descriptor.traits(InitializerBlockDescriptor.class).findFirst().orElseThrow();

        assertThat(initBlock.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var location = initBlock.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(location.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(location.endPosition()).isGreaterThan(location.startPosition());
    }

    @Test
    void extendsAndImplementsClauseTypeUsagesShouldCarryDistinctSourceLocations() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.io.Serializable;
                public class Foo extends java.util.AbstractList<String> implements Serializable, Cloneable {
                    public String get(int index) { return null; }
                    public int size() { return 0; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var descriptor = (JDKTypeDescriptor) codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var superUsage = descriptor.parentTypeUsage().orElseThrow();
        final var interfaceUsages = descriptor.interfaceTypeUsages().toList();
        assertThat(interfaceUsages).hasSize(2);
        final var serializableUsage = interfaceUsages.get(0);
        final var cloneableUsage = interfaceUsages.get(1);

        assertThat(superUsage.getTrait(SourceLocation.FilePosition.class)).isPresent();
        assertThat(serializableUsage.getTrait(SourceLocation.FilePosition.class)).isPresent();
        assertThat(cloneableUsage.getTrait(SourceLocation.FilePosition.class)).isPresent();

        final var superPos = superUsage.getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var serializablePos = serializableUsage.getTrait(SourceLocation.FilePosition.class)
            .orElseThrow().startPosition();
        final var cloneablePos = cloneableUsage.getTrait(SourceLocation.FilePosition.class)
            .orElseThrow().startPosition();

        // extends precedes implements, and each implemented interface is distinct and in source order
        assertThat(serializablePos).isGreaterThan(superPos);
        assertThat(cloneablePos).isGreaterThan(serializablePos);
    }

    @Test
    void permitsClauseTypeUsagesShouldCarryDistinctSourceLocations() {
        // permits order (Zeta, Alpha) deliberately differs from alphabetical, so a test that merely
        // sorted positions would not tell each usage's location apart from the other's.
        final var src = """
            package com.example;
            public sealed interface Shape permits Zeta, Alpha {}
            final class Zeta implements Shape {}
            final class Alpha implements Shape {}
            """;
        final var source = JavaFileObjects.forSourceString("com.example.Shape", src);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Shape");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();
        final var permits = descriptor.traits(PermitsTypeDescriptor.class).toList();
        assertThat(permits).hasSize(2);

        // Slice the source at each recorded position and key by the token it actually points at.
        final var positionsByToken = new HashMap<String, Long>();
        for (final var permit : permits) {
            final var location = permit.parentTypeUsage()
                .getTrait(SourceLocation.FilePosition.class).orElseThrow();
            final var token = src.substring((int) location.startPosition(), (int) location.endPosition());
            positionsByToken.put(token, location.startPosition());
        }

        assertThat(positionsByToken).containsOnlyKeys("Zeta", "Alpha");
        assertThat(positionsByToken.get("Zeta")).isLessThan(positionsByToken.get("Alpha"));
    }

    @Test
    void fieldDeclaredTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public String value;
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var field = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(FieldDescriptor.class).findFirst().orElseThrow();

        // the field declaration itself and its declared type usage are distinct positions
        final var fieldPos = field.getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var typePos = field.type().getTrait(SourceLocation.FilePosition.class)
            .orElseThrow().startPosition();
        assertThat(typePos).isGreaterThanOrEqualTo(fieldPos);
    }

    @Test
    void methodParameterDeclaredTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void hello(String name) {}
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var param = method.formalParameters().findFirst().orElseThrow();

        final var paramPos = param.getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var typePos = param.type().getTrait(SourceLocation.FilePosition.class)
            .orElseThrow().startPosition();
        assertThat(typePos).isEqualTo(paramPos);
    }

    @Test
    void methodReturnTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public String hello() { return null; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();

        assertThat(method.returnType().getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var methodPos = method.getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var returnTypePos = method.returnType().getTrait(SourceLocation.FilePosition.class)
            .orElseThrow().startPosition();
        assertThat(returnTypePos).isGreaterThan(methodPos);
    }

    @Test
    void catchClauseExceptionParameterShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void run() {
                        try {
                            System.out.println();
                        } catch (RuntimeException e) {
                        }
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var tryStatement = (Try) body.statements().findFirst().orElseThrow();
        final var catchClause = tryStatement.catches().findFirst().orElseThrow();

        assertThat(catchClause.getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var location = catchClause.getTrait(SourceLocation.FilePosition.class).orElseThrow();
        assertThat(location.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(location.endPosition()).isGreaterThan(location.startPosition());
    }

    @Test
    void recordComponentDeclaredTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Point", """
                package com.example;
                public record Point(int x, String y) {}
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Point");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();
        final var components = descriptor.traits(RecordComponentDescriptor.class).toList();
        assertThat(components).hasSize(2);

        final var x = components.stream().filter(c -> c.name().toString().equals("x")).findFirst().orElseThrow();
        final var y = components.stream().filter(c -> c.name().toString().equals("y")).findFirst().orElseThrow();

        assertThat(x.type().getTrait(SourceLocation.FilePosition.class)).isPresent();
        assertThat(y.type().getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var xPos = x.type().getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var yPos = y.type().getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        assertThat(yPos).isGreaterThan(xPos);
    }

    @Test
    void throwsClauseExceptionTypeUsagesShouldCarryDistinctSourceLocations() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.io.IOException;
                public class Foo {
                    public void run() throws IOException, InterruptedException {}
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var throwables = method.traits(ThrowableDescriptor.class).toList();
        assertThat(throwables).hasSize(2);

        final var firstPos = throwables.get(0).throwable()
            .getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var secondPos = throwables.get(1).throwable()
            .getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        assertThat(secondPos).isGreaterThan(firstPos);
    }

    @Test
    void typeParameterUpperBoundTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Box", """
                package com.example;
                public class Box<T extends Number> {
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Box");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();
        final var typeVariable = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow().typeVariables().findFirst().orElseThrow();

        final var upperBound = typeVariable.upperBound().orElseThrow();
        assertThat(upperBound.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void allInterfaceIntersectionTypeParameterBoundsShouldCarryDistinctSourceLocations() {
        // Every written bound (Comparable, Serializable, Cloneable) must point back at its own
        // token, not share one position or be skipped entirely.
        final var src = """
            package com.example;
            import java.io.Serializable;
            public class Box<T extends Comparable<T> & Serializable & Cloneable> {
            }
            """;

        final var positionsByToken = intersectionBoundPositions(src, "com.example.Box");

        assertThat(positionsByToken).containsOnlyKeys("Comparable<T>", "Serializable", "Cloneable");
        assertThat(positionsByToken.get("Comparable<T>"))
            .isLessThan(positionsByToken.get("Serializable"));
        assertThat(positionsByToken.get("Serializable"))
            .isLessThan(positionsByToken.get("Cloneable"));
    }

    @Test
    void classLedIntersectionTypeParameterBoundsShouldCarryDistinctSourceLocations() {
        // When the first bound is a class rather than an interface, the tree bounds and
        // IntersectionType.getBounds() must still line up so each token keeps its own position.
        final var src = """
            package com.example;
            import java.io.Serializable;
            public class Box<T extends Number & Comparable<T> & Serializable> {
            }
            """;

        final var positionsByToken = intersectionBoundPositions(src, "com.example.Box");

        assertThat(positionsByToken).containsOnlyKeys("Number", "Comparable<T>", "Serializable");
        assertThat(positionsByToken.get("Number"))
            .isLessThan(positionsByToken.get("Comparable<T>"));
        assertThat(positionsByToken.get("Comparable<T>"))
            .isLessThan(positionsByToken.get("Serializable"));
    }

    private static HashMap<String, Long> intersectionBoundPositions(final String src,
                                                                    final String typeName) {
        final var source = JavaFileObjects.forSourceString(typeName, src);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var descriptor = codeModel.getTypeDescriptor(
            codeModel.getEmptyModuleTypeName(typeName)).orElseThrow();
        final var typeVariable = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow().typeVariables().findFirst().orElseThrow();

        final var intersection = (IntersectionTypeUsage) typeVariable.upperBound().orElseThrow();

        final var positionsByToken = new HashMap<String, Long>();
        intersection.types().forEach(bound -> {
            final var location = bound.getTrait(SourceLocation.FilePosition.class).orElseThrow();
            positionsByToken.put(
                src.substring((int) location.startPosition(), (int) location.endPosition()),
                location.startPosition());
        });
        return positionsByToken;
    }

    @Test
    void localVariableDeclaredTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void run() {
                        String value = "hello";
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();

        assertThat(decl.type().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void enhancedForLoopVariableDeclaredTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.util.List;
                public class Foo {
                    public void run(List<String> values) {
                        for (String value : values) {}
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var loop = (EnhancedFor) body.statements().findFirst().orElseThrow();

        assertThat(loop.type().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void catchClauseExceptionTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void run() {
                        try {
                            System.out.println();
                        } catch (RuntimeException e) {
                        }
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var tryStatement = (Try) body.statements().findFirst().orElseThrow();
        final var catchClause = tryStatement.catches().findFirst().orElseThrow();
        final var exceptionType = catchClause.exceptionTypes().findFirst().orElseThrow();

        assertThat(exceptionType.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void multiCatchExceptionTypeUsagesShouldCarryDistinctSourceLocations() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void run() {
                        try {
                            System.out.println();
                        } catch (IllegalStateException | IllegalArgumentException e) {
                        }
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var tryStatement = (Try) body.statements().findFirst().orElseThrow();
        final var catchClause = tryStatement.catches().findFirst().orElseThrow();
        final var exceptionTypes = catchClause.exceptionTypes().toList();
        assertThat(exceptionTypes).hasSize(2);

        final var firstPos = exceptionTypes.get(0)
            .getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var secondPos = exceptionTypes.get(1)
            .getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        assertThat(secondPos).isGreaterThan(firstPos);
    }

    @Test
    void instanceOfPatternBindingTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public boolean check(Object obj) {
                        return obj instanceof String s;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var instanceOf = (InstanceOf) returnStmt.expression().orElseThrow();

        assertThat(instanceOf.checkedType().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void nonPatternInstanceOfTargetTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public boolean check(Object obj) {
                        return obj instanceof String;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var instanceOf = (InstanceOf) returnStmt.expression().orElseThrow();

        assertThat(instanceOf.checkedType().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void recordDeconstructionPatternTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    record Point(int x, int y) {}
                    public boolean check(Object obj) {
                        return obj instanceof Point(int x, int y);
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("check"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var instanceOf = (InstanceOf) returnStmt.expression().orElseThrow();

        assertThat(instanceOf.checkedType().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void classLiteralTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public Class<?> run() {
                        return String.class;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var classLiteral = (ClassLiteral) returnStmt.expression().orElseThrow();

        assertThat(classLiteral.referencedType().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void newObjectTypeAndTypeArgumentUsagesShouldCarryDistinctSourceLocations() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.util.ArrayList;
                public class Foo {
                    public ArrayList<String> create() {
                        return new ArrayList<String>();
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var newObject = (NewObject) returnStmt.expression().orElseThrow();

        assertThat(newObject.instantiatedType().getTrait(SourceLocation.FilePosition.class)).isPresent();
        final var typeArg = newObject.typeArguments().findFirst().orElseThrow();
        assertThat(typeArg.getTrait(SourceLocation.FilePosition.class)).isPresent();

        final var typePos = newObject.instantiatedType()
            .getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        final var typeArgPos = typeArg.getTrait(SourceLocation.FilePosition.class).orElseThrow().startPosition();
        assertThat(typeArgPos).isGreaterThan(typePos);
    }

    @Test
    void newArrayElementTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public String[] create(int n) {
                        return new String[n];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();
        final var newArray = (NewArray) returnStmt.expression().orElseThrow();

        assertThat(newArray.elementType().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void castTargetTypeUsageShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void run(Object obj) {
                        String s = (String) obj;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class).findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();
        final var cast = (Cast) decl.initializer().orElseThrow();

        assertThat(cast.targetType().getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void castExpressionNodeItselfShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public void run(Object obj) {
                        String s = (String) obj;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var cast = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(Cast.class)
            .findFirst()
            .orElseThrow();

        assertThat(cast.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void literalExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run() {
                        return 42;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var literal = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NumericLiteral.class)
            .findFirst()
            .orElseThrow();

        assertThat(literal.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void binaryExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(int a, int b) {
                        return a + b;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var addition = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(Addition.class)
            .findFirst()
            .orElseThrow();

        assertThat(addition.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void unaryExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(int a) {
                        return -a;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var negative = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(Negative.class)
            .findFirst()
            .orElseThrow();

        assertThat(negative.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void ternaryExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(boolean cond, int a, int b) {
                        return cond ? a : b;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var ternary = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(Ternary.class)
            .findFirst()
            .orElseThrow();

        assertThat(ternary.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void assignmentExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(int a) {
                        int b;
                        return b = a;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var assignment = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(CompoundAssignment.class)
            .findFirst()
            .orElseThrow();

        assertThat(assignment.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void compoundAssignmentExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(int a) {
                        int b = 0;
                        return b += a;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var compoundAssignment = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(CompoundAssignment.class)
            .findFirst()
            .orElseThrow();

        assertThat(compoundAssignment.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void arrayAccessExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(int[] arr) {
                        return arr[0];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var arrayAccess = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(ArrayAccess.class)
            .findFirst()
            .orElseThrow();

        assertThat(arrayAccess.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void parenthesizedExpressionShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(int a) {
                        return (a);
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var parenthesized = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(Parenthesized.class)
            .findFirst()
            .orElseThrow();

        assertThat(parenthesized.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void lambdaExpressionNodeItselfShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.util.function.Function;
                public class Foo {
                    public Function<Integer, Integer> run() {
                        return x -> x + 1;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var lambda = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(Lambda.class)
            .findFirst()
            .orElseThrow();

        assertThat(lambda.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void switchExpressionNodeItselfShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public int run(int a) {
                        return switch (a) {
                            case 1 -> 10;
                            default -> 0;
                        };
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var switchExpr = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(SwitchExpression.class)
            .findFirst()
            .orElseThrow();

        assertThat(switchExpr.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void instanceOfExpressionNodeItselfShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public boolean check(Object obj) {
                        return obj instanceof String s;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var instanceOf = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(InstanceOf.class)
            .findFirst()
            .orElseThrow();

        assertThat(instanceOf.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void classLiteralNodeItselfShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public Class<?> run() {
                        return String.class;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var classLiteral = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(ClassLiteral.class)
            .findFirst()
            .orElseThrow();

        assertThat(classLiteral.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void newObjectNodeItselfShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                import java.util.ArrayList;
                public class Foo {
                    public ArrayList<String> create() {
                        return new ArrayList<String>();
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var newObject = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewObject.class)
            .findFirst()
            .orElseThrow();

        assertThat(newObject.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void newArrayNodeItselfShouldCarrySourceLocation() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public String[] create(int n) {
                        return new String[n];
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var newArray = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .composition(NewArray.class)
            .findFirst()
            .orElseThrow();

        assertThat(newArray.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }
}
