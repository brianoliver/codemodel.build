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
import build.codemodel.jdk.populator.descriptor.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@code package-info.java} handling: the populator must register a
 * {@link build.codemodel.foundation.descriptor.NamespaceDescriptor} for a package whose
 * declaration carries annotations and attach those annotations to it.
 *
 * @author reed.vonredwitz
 * @since Sep-2026
 */
class PackageAnnotationDiscoveryTests {

    private static final javax.tools.JavaFileObject MARKER = JavaFileObjects.forSourceString(
        "build.codemodel.jdk.example.pkg.ApiNote", """
        package build.codemodel.jdk.example.pkg;
        import java.lang.annotation.*;

        @Target(ElementType.PACKAGE)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface ApiNote {}
        """);

    @Test
    void shouldRegisterNamespaceDescriptorWithPackageAnnotation() {
        final var packageInfo = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.pkg.package-info", """
            @ApiNote
            @Deprecated
            package build.codemodel.jdk.example.pkg;
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(MARKER, packageInfo)));

        final var namespace = codeModel.getNameProvider()
            .getNamespace("build.codemodel.jdk.example.pkg").orElseThrow();
        final var descriptor = codeModel.getNamespaceDescriptor(namespace).orElseThrow();

        final var annotations = descriptor.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList();
        assertThat(annotations).containsExactlyInAnyOrder("ApiNote", "Deprecated");
    }

    @Test
    void shouldAttachSourceLocationToTheNamespaceDescriptor() {
        final var packageInfo = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.pkg.package-info", """
            @ApiNote
            package build.codemodel.jdk.example.pkg;
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(MARKER, packageInfo)));

        final var namespace = codeModel.getNameProvider()
            .getNamespace("build.codemodel.jdk.example.pkg").orElseThrow();
        final var descriptor = codeModel.getNamespaceDescriptor(namespace).orElseThrow();

        assertThat(descriptor.getTrait(SourceLocation.FilePosition.class)).isPresent();
    }

    @Test
    void shouldNotRegisterNamespaceDescriptorForAnUnannotatedPackage() {
        final var source = JavaFileObjects.forSourceString(
            "build.codemodel.jdk.example.plain.Foo", """
            package build.codemodel.jdk.example.plain;
            public class Foo {}
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var namespace = codeModel.getNameProvider()
            .getNamespace("build.codemodel.jdk.example.plain").orElseThrow();
        assertThat(codeModel.getNamespaceDescriptor(namespace)).isEmpty();
    }
}
