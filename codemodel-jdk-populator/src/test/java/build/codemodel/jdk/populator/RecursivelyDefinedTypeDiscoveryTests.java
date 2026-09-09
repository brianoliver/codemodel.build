package build.codemodel.jdk.populator;

import build.base.compile.testing.JavaFileObjects;
import build.codemodel.objectoriented.descriptor.FieldDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for handling recursively defined types via {@link JdkInitializer}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public class RecursivelyDefinedTypeDiscoveryTests {

    @Test
    void shouldHandleRecursiveGenericType() {
        final var moduleInfo = JavaFileObjects.forSourceString("module-info", """
            module com.example {
            }
            """);
        final var source = JavaFileObjects.forSourceString("Discover", """
            package com.example;
            public interface Discover<T extends Discover<T>> {
                Discover<T> discover();
            }
            """);
        final var initializer = new JdkInitializer(List.of(), List.of(), List.of(source, moduleInfo));
        final var codeModel = JdkInitializerTests.runInternal(initializer);

        final var moduleName = codeModel.getNameProvider().getModuleName("com.example");
        final var typeName = codeModel.getNameProvider().getTypeName(moduleName, "com.example.Discover");
        final var typeDescriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        assertThat(typeDescriptor.typeName()).isEqualTo(typeName);
        assertThat(typeDescriptor.traits(MethodDescriptor.class)).isNotEmpty();
        assertThat(typeDescriptor.traits(FieldDescriptor.class)).isEmpty();

        final var typeVar = typeDescriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow().typeVariables().findFirst().orElseThrow();
        // In the model T is scoped under its declaring type so it can't collide with a <T> declared
        // elsewhere (see typeName().name() vs typeName() below). That scoping is a model concern only:
        // when rendered, the variable - the declaration token and the recursive back-reference in its
        // own bound alike - reads as the plain identifier "T". toString() still keeps the module on
        // the raw type Discover; canonicalName() strips it.
        assertThat(typeVar.typeName().toString()).isEqualTo("com.example/com.example.Discover$T");
        assertThat(typeVar.typeName().name().toString()).isEqualTo("T");
        assertThat(typeVar.toString()).isEqualTo("T extends com.example/com.example.Discover<T>");
        assertThat(typeVar.canonicalName()).isEqualTo("T extends com.example.Discover<T>");
    }
}
