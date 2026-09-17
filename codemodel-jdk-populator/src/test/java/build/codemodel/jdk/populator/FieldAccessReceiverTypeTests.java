package build.codemodel.jdk.populator;

import build.base.compile.testing.JavaFileObjects;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.jdk.descriptor.MethodBodyDescriptor;
import build.codemodel.jdk.expression.FieldAccess;
import build.codemodel.jdk.statement.LocalVariableDeclaration;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link FieldAccess#receiverType()} resolution via {@link JdkInitializer}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
class FieldAccessReceiverTypeTests {

    @Test
    void shouldResolveReceiverTypeForFieldAccess() {
        final var itemSource = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Item", """
                package build.codemodel.jdk.example;
                public class Item {
                    public String value;
                }
                """);
        final var workerSource = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Worker", """
                package build.codemodel.jdk.example;
                public class Worker {
                    public void run(Item item) {
                        String v = item.value;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(itemSource, workerSource)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Worker");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var run = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();

        final var body = run.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();
        final var access = (FieldAccess) decl.initializer().orElseThrow();

        assertThat(access.fieldName()).isEqualTo("value");
        assertThat(access.receiverType()).isPresent();
        assertThat(access.receiverType().get()).isInstanceOf(NamedTypeUsage.class);
        final var receiverTypeName = ((NamedTypeUsage) access.receiverType().get()).typeName();
        assertThat(receiverTypeName.name().toString()).isEqualTo("Item");
        assertThat(access.toString()).isEqualTo("item.value");
    }

    @Test
    void shouldResolveReceiverTypeForChainedFieldAccess() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.Wrapper", """
                package build.codemodel.jdk.example;
                public class Wrapper {
                    public int length;
                    public static Wrapper forString(String s) { return new Wrapper(); }
                    public void run(String s) {
                        int n = Wrapper.forString(s).length;
                    }
                }
                """);

        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var typeName = codeModel.getEmptyModuleTypeName("build.codemodel.jdk.example.Wrapper");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var run = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();

        final var body = run.getTrait(MethodBodyDescriptor.class).orElseThrow().body();
        final var decl = (LocalVariableDeclaration) body.statements().findFirst().orElseThrow();
        final var access = (FieldAccess) decl.initializer().orElseThrow();

        assertThat(access.fieldName()).isEqualTo("length");
        assertThat(access.receiverType()).isPresent();
        assertThat(access.receiverType().get()).isInstanceOf(NamedTypeUsage.class);
        final var receiverTypeName = ((NamedTypeUsage) access.receiverType().get()).typeName();
        assertThat(receiverTypeName.name().toString()).isEqualTo("Wrapper");
        assertThat(access.toString()).isEqualTo("Wrapper.forString(s).length");
    }
}
