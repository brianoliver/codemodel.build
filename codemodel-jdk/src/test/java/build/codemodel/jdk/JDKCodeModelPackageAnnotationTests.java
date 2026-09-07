package build.codemodel.jdk;

import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.jdk.example.annotatedpkg.Widget;
import build.codemodel.jdk.example.plainpkg.Gadget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-path parity for {@code package-info.java}: resolving a type whose package is annotated
 * must register a {@link build.codemodel.foundation.descriptor.NamespaceDescriptor} carrying those
 * annotations, matching the javac-source and annotation-processor paths.
 *
 * @author reed.vonredwitz
 * @since Sep-2026
 */
class JDKCodeModelPackageAnnotationTests {

    private static JDKCodeModel createCodeModel() {
        return new JDKCodeModel(new NonCachingNameProvider());
    }

    @Test
    void shouldRegisterNamespaceDescriptorWithPackageAnnotations() {
        final var codeModel = createCodeModel();
        codeModel.getJDKTypeDescriptor(Widget.class).orElseThrow();

        final var namespace = codeModel.getNameProvider()
            .getNamespace("build.codemodel.jdk.example.annotatedpkg").orElseThrow();
        final var descriptor = codeModel.getNamespaceDescriptor(namespace).orElseThrow();

        assertThat(descriptor.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactlyInAnyOrder("PackageMarker", "Deprecated");
    }

    @Test
    void shouldNotRegisterNamespaceDescriptorForAnUnannotatedPackage() {
        final var codeModel = createCodeModel();
        codeModel.getJDKTypeDescriptor(Gadget.class).orElseThrow();

        final var namespace = codeModel.getNameProvider()
            .getNamespace("build.codemodel.jdk.example.plainpkg").orElseThrow();
        assertThat(codeModel.getNamespaceDescriptor(namespace)).isEmpty();
    }
}
