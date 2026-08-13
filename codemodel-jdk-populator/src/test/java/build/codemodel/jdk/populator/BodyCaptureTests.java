package build.codemodel.jdk.populator;

import build.base.compile.testing.JavaFileObjects;
import build.codemodel.expression.NumericLiteral;
import build.codemodel.expression.StringLiteral;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.imperative.Return;
import build.codemodel.jdk.descriptor.EnumConstantDescriptor;
import build.codemodel.jdk.descriptor.FieldInitializerDescriptor;
import build.codemodel.jdk.descriptor.MethodBodyDescriptor;
import build.codemodel.jdk.expression.Lambda;
import build.codemodel.jdk.expression.NewObject;
import build.codemodel.jdk.statement.ExpressionStatement;
import build.codemodel.jdk.statement.LocalVariableDeclaration;
import build.codemodel.jdk.statement.Try;
import build.codemodel.objectoriented.descriptor.ConstructorDescriptor;
import build.codemodel.objectoriented.descriptor.FieldDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for method body and field initializer capture via {@link JdkInitializer}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
class BodyCaptureTests {

    @Test
    void shouldCaptureMethodBodyAsBlock() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.SimplePerson", """
                package build.codemodel.jdk.example;
                public class SimplePerson {
                    private String name;
                    public SimplePerson(String name) { this.name = name; }
                    public String getName() { return name; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.SimplePerson");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        // constructor has body
        final var ctor = descriptor.traits(ConstructorDescriptor.class).findFirst().orElseThrow();
        assertThat(ctor.getTrait(MethodBodyDescriptor.class)).isPresent();
        assertThat(ctor.getTrait(MethodBodyDescriptor.class).get().body().statements()).isNotEmpty();

        // getName has body
        final var getName = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("getName"))
            .findFirst().orElseThrow();
        assertThat(getName.getTrait(MethodBodyDescriptor.class)).isPresent();
        assertThat(getName.getTrait(MethodBodyDescriptor.class).get().body().statements()).isNotEmpty();

        // field 'name' has no initializer
        final var nameField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("name"))
            .findFirst().orElseThrow();
        assertThat(nameField.getTrait(FieldInitializerDescriptor.class)).isEmpty();
    }

    @Test
    void shouldCaptureFieldInitializer() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Defaults", """
                package build.codemodel.jdk.example;
                public class Defaults {
                    private int count = 0;
                    private String label = "hello";
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Defaults");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var count = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("count"))
            .findFirst().orElseThrow();
        assertThat(count.getTrait(FieldInitializerDescriptor.class)).isPresent();
        assertThat(count.getTrait(FieldInitializerDescriptor.class).get().initializer())
            .isInstanceOf(NumericLiteral.class);

        final var label = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("label"))
            .findFirst().orElseThrow();
        assertThat(label.getTrait(FieldInitializerDescriptor.class)).isPresent();
        assertThat(label.getTrait(FieldInitializerDescriptor.class).get().initializer())
            .isInstanceOf(StringLiteral.class);
    }

    @Test
    void shouldCaptureEnumConstantInitializer() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Suit", """
                package build.codemodel.jdk.example;
                public enum Suit {
                    HEARTS("red"),
                    SPADES("black");

                    private final String color;

                    Suit(String color) { this.color = color; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Suit");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var hearts = descriptor.traits(EnumConstantDescriptor.class)
            .filter(c -> c.name().toString().equals("HEARTS"))
            .findFirst().orElseThrow();
        assertThat(hearts.getTrait(FieldInitializerDescriptor.class)).isPresent();
        final var heartsInitializer = hearts.getTrait(FieldInitializerDescriptor.class).get().initializer();
        assertThat(heartsInitializer).isInstanceOf(NewObject.class);
        assertThat(((NewObject) heartsInitializer).args()
            .filter(StringLiteral.class::isInstance)
            .map(StringLiteral.class::cast)
            .map(StringLiteral::value))
            .containsExactly("red");
        assertThat(((NewObject) heartsInitializer).anonymousBodyType()).isEmpty();

        final var spades = descriptor.traits(EnumConstantDescriptor.class)
            .filter(c -> c.name().toString().equals("SPADES"))
            .findFirst().orElseThrow();
        assertThat(spades.getTrait(FieldInitializerDescriptor.class)).isPresent();
        final var spadesInitializer = spades.getTrait(FieldInitializerDescriptor.class).get().initializer();
        assertThat(((NewObject) spadesInitializer).args()
            .filter(StringLiteral.class::isInstance)
            .map(StringLiteral.class::cast)
            .map(StringLiteral::value))
            .containsExactly("black");
    }

    @Test
    void shouldCaptureEnumConstantInitializerWithNoArgsAsEmptyNewObject() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Direction", """
                package build.codemodel.jdk.example;
                public enum Direction {
                    NORTH, SOUTH, EAST, WEST
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Direction");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var north = descriptor.traits(EnumConstantDescriptor.class)
            .filter(c -> c.name().toString().equals("NORTH"))
            .findFirst().orElseThrow();
        assertThat(north.getTrait(FieldInitializerDescriptor.class)).isPresent();
        final var initializer = north.trait(FieldInitializerDescriptor.class).initializer();
        assertThat(initializer).isInstanceOf(NewObject.class);
        assertThat(((NewObject) initializer).args()).isEmpty();
    }

    @Test
    void shouldCaptureEnumConstantInitializerArgsWithClassBody() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Weekday", """
                package build.codemodel.jdk.example;
                public enum Weekday {
                    MONDAY("Mon") {
                        @Override
                        public String describe() { return "start of the week"; }
                    };

                    private final String abbreviation;

                    Weekday(String abbreviation) { this.abbreviation = abbreviation; }

                    public String describe() { return "just another day"; }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Weekday");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var monday = descriptor.traits(EnumConstantDescriptor.class)
            .filter(c -> c.name().toString().equals("MONDAY"))
            .findFirst().orElseThrow();
        assertThat(monday.getTrait(FieldInitializerDescriptor.class)).isPresent();
        final var mondayInitializer = monday.getTrait(FieldInitializerDescriptor.class).get().initializer();
        assertThat(mondayInitializer).isInstanceOf(NewObject.class);
        final var mondayNewObject = (NewObject) mondayInitializer;
        assertThat(mondayNewObject.args()
            .filter(StringLiteral.class::isInstance)
            .map(StringLiteral.class::cast)
            .map(StringLiteral::value))
            .containsExactly("Mon");

        assertThat(mondayNewObject.anonymousBodyType()).isPresent();
        final var bodyTypeDescriptor = codeModel.getTypeDescriptor(mondayNewObject.anonymousBodyType().get()).orElseThrow();
        final var describeOverride = bodyTypeDescriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("describe"))
            .findFirst().orElseThrow();
        assertThat(describeOverride.getTrait(MethodBodyDescriptor.class)).isPresent();
    }

    @Test
    void shouldCaptureLambdaParameters() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Sorter", """
                package build.codemodel.jdk.example;
                import java.util.Comparator;
                public class Sorter {
                    public Comparator<String> comparator() {
                        return (String a, String b) -> a.compareTo(b);
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Sorter");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var method = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("comparator"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var lambda = body.statements()
            .map(s -> s instanceof Return r ? r.expression().orElse(null) : null)
            .filter(e -> e instanceof Lambda)
            .map(e -> (Lambda) e)
            .findFirst().orElseThrow();

        final var params = lambda.parameters().toList();
        assertThat(params).hasSize(2);
        assertThat(params.get(0).name()).isEqualTo("a");
        assertThat(params.get(0).type()).isPresent();
        assertThat(params.get(0).type().orElseThrow()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) params.get(0).type().orElseThrow()).typeName().name().toString()).isEqualTo("String");
        assertThat(params.get(1).name()).isEqualTo("b");
        assertThat(params.get(1).type()).isPresent();
        assertThat(params.get(1).type().orElseThrow()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) params.get(1).type().orElseThrow()).typeName().name().toString()).isEqualTo("String");
    }

    @Test
    void shouldResolveImplicitLambdaParameterTypes() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.ImplicitSorter", """
                package build.codemodel.jdk.example;
                import java.util.Comparator;
                public class ImplicitSorter {
                    public Comparator<String> comparator() {
                        return (a, b) -> a.compareTo(b);
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.ImplicitSorter");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("comparator"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var lambda = body.statements()
            .map(s -> s instanceof Return r ? r.expression().orElse(null) : null)
            .filter(e -> e instanceof Lambda)
            .map(e -> (Lambda) e)
            .findFirst().orElseThrow();

        final var params = lambda.parameters().toList();
        assertThat(params).hasSize(2);
        assertThat(params.get(0).name()).isEqualTo("a");
        assertThat(params.get(0).type()).isPresent();
        assertThat(params.get(0).type().orElseThrow()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) params.get(0).type().orElseThrow()).typeName().name().toString()).isEqualTo("String");
        assertThat(params.get(1).name()).isEqualTo("b");
        assertThat(params.get(1).type()).isPresent();
        assertThat(params.get(1).type().orElseThrow()).isInstanceOf(NamedTypeUsage.class);
        assertThat(((NamedTypeUsage) params.get(1).type().orElseThrow()).typeName().name().toString()).isEqualTo("String");
    }

    @Test
    void shouldCaptureTryWithResources() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.ResourceUser", """
                package build.codemodel.jdk.example;
                import java.io.InputStream;
                import java.io.IOException;
                public class ResourceUser {
                    public InputStream open() { return null; }
                    public void use() throws IOException {
                        try (InputStream is = open()) {
                            is.read();
                        }
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.ResourceUser");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var method = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("use"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var tryStmt = body.statements()
            .filter(s -> s instanceof Try)
            .map(s -> (Try) s)
            .findFirst().orElseThrow();

        final var resources = tryStmt.resources().toList();
        assertThat(resources).hasSize(1);
        assertThat(resources.getFirst()).isInstanceOf(LocalVariableDeclaration.class);
        assertThat(((LocalVariableDeclaration) resources.getFirst()).name()).isEqualTo("is");
    }

    @Test
    void shouldCaptureTryWithEffectivelyFinalResource() {
        // Java 9+ allows an effectively-final variable as a try-with-resources resource
        // without re-declaring it. The resource is an expression, not a StatementTree.
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.EffectivelyFinalResource", """
                package build.codemodel.jdk.example;
                import java.io.InputStream;
                import java.io.IOException;
                public class EffectivelyFinalResource {
                    public void use(InputStream is) throws IOException {
                        try (is) {
                            is.read();
                        }
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.EffectivelyFinalResource");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var method = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("use"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var tryStmt = body.statements()
            .filter(s -> s instanceof Try)
            .map(s -> (Try) s)
            .findFirst().orElseThrow();

        final var resources = tryStmt.resources().toList();
        assertThat(resources).hasSize(1);
        assertThat(resources.getFirst()).isInstanceOf(ExpressionStatement.class);
    }

    @Test
    void shouldCaptureMultiCatchExceptionTypes() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.MultiCatcher", """
                package build.codemodel.jdk.example;
                import java.io.IOException;
                public class MultiCatcher {
                    public void run() {
                        try {
                            throw new IOException();
                        } catch (IOException | RuntimeException e) {
                            // handle
                        }
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.MultiCatcher");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var method = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var tryStmt = body.statements()
            .filter(s -> s instanceof Try)
            .map(s -> (Try) s)
            .findFirst().orElseThrow();

        final var catchClause = tryStmt.catches().findFirst().orElseThrow();
        final var exTypeNames = catchClause.exceptionTypes()
            .filter(t -> t instanceof NamedTypeUsage)
            .map(t -> ((NamedTypeUsage) t).typeName().name().toString())
            .toList();
        assertThat(exTypeNames).containsExactlyInAnyOrder("IOException", "RuntimeException");
    }

    @Test
    void shouldCaptureBareReturnAsEmptyOptional() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Greeter", """
                package build.codemodel.jdk.example;
                public class Greeter {
                    public void greet() {
                        return;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Greeter");
        final var method = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("greet"))
            .findFirst().orElseThrow();
        final var body = method.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var returnStmt = (Return) body.statements().findFirst().orElseThrow();

        assertThat(returnStmt.expression()).isEmpty();
    }

    @Test
    void shouldNotCaptureImplicitSuperCallInConstructorBody() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Foo", """
                package com.example;
                public class Foo {
                    public Foo() {
                        int x = 1;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();
        final var ctor = descriptor.traits(ConstructorDescriptor.class).findFirst().orElseThrow();
        final var body = ctor.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var statements = body.statements().toList();

        // Only one statement (int x = 1) — the javac-injected super() must not appear
        assertThat(statements).hasSize(1);
        assertThat(statements.getFirst()).isInstanceOf(LocalVariableDeclaration.class);
    }

    @Test
    void shouldCaptureStaticInitializerBlock() {
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

        final var staticInits = descriptor.traits(build.codemodel.jdk.descriptor.InitializerBlockDescriptor.class)
            .filter(build.codemodel.jdk.descriptor.InitializerBlockDescriptor::isStatic)
            .toList();
        assertThat(staticInits).hasSize(1);
        assertThat(staticInits.getFirst().body().statements()).isNotEmpty();
    }

    @Test
    void shouldCaptureInstanceInitializerBlock() {
        final var source = JavaFileObjects.forSourceString(
            "com.example.Counter", """
                package com.example;
                public class Counter {
                    private int count;
                    {
                        count = 0;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("com.example.Counter");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var instanceInits = descriptor.traits(build.codemodel.jdk.descriptor.InitializerBlockDescriptor.class)
            .filter(b -> !b.isStatic())
            .toList();
        assertThat(instanceInits).hasSize(1);
        assertThat(instanceInits.getFirst().body().statements()).isNotEmpty();
    }
}
