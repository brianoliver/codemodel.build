package build.codemodel.jdk.annotation.processor;

import build.base.compile.testing.JavaFileObjects;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.jdk.populator.descriptor.SourceLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@code package-info.java} handling on the annotation-processor path: a
 * discovered type's enclosing package, when annotated, must yield a
 * {@link build.codemodel.foundation.descriptor.NamespaceDescriptor} carrying those annotations.
 * Mirrors the javac-source-path coverage in {@code codemodel-jdk-populator}.
 */
class PackageAnnotationDiscoveryTests
    extends AnnotationProcessorTests {

    @Test
    void shouldRegisterNamespaceDescriptorWithPackageAnnotations() {
        final var marker = JavaFileObjects.forSourceString("com.example.ApiNote", """
            package com.example;
            import java.lang.annotation.*;

            @Target(ElementType.PACKAGE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface ApiNote {}
            """);
        final var packageInfo = JavaFileObjects.forSourceString("com.example.package-info", """
            @ApiNote
            @Deprecated
            package com.example;
            """);
        final var foo = JavaFileObjects.forSourceString("com.example.Foo", """
            package com.example;
            import build.codemodel.jdk.annotation.discovery.Discoverable;

            @Discoverable
            public class Foo {}
            """);

        final var processor = new AnnotationProcessor();
        compile(processor, marker, packageInfo, foo);

        final var codeModel = processor.getCodeModel().orElseThrow();
        final var namespace = codeModel.getNameProvider().getNamespace("com.example").orElseThrow();
        final var descriptor = codeModel.getNamespaceDescriptor(namespace).orElseThrow();

        assertThat(descriptor.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactlyInAnyOrder("ApiNote", "Deprecated");
        assertThat(descriptor.getTrait(SourceLocation.ElementRef.class)).isPresent();
    }

    @Test
    void shouldNotRegisterNamespaceDescriptorForAnUnannotatedPackage() {
        final var foo = JavaFileObjects.forSourceString("com.example.plain.Foo", """
            package com.example.plain;
            import build.codemodel.jdk.annotation.discovery.Discoverable;

            @Discoverable
            public class Foo {}
            """);

        final var processor = new AnnotationProcessor();
        compile(processor, foo);

        final var codeModel = processor.getCodeModel().orElseThrow();
        final var namespace = codeModel.getNameProvider().getNamespace("com.example.plain").orElseThrow();
        assertThat(codeModel.getNamespaceDescriptor(namespace)).isEmpty();
    }
}
