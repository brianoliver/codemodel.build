package build.codemodel.jdk;

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

import build.base.parsing.ParseException;
import build.base.version.Version;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.RequiresModuleDescriptor;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.usage.AnnotationValue;
import build.codemodel.jdk.descriptor.ExportsDescriptor;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.codemodel.jdk.descriptor.ModuleModifier;
import build.codemodel.jdk.descriptor.OpenModule;
import build.codemodel.jdk.descriptor.OpensDescriptor;
import build.codemodel.jdk.descriptor.ProvidesDescriptor;
import build.codemodel.jdk.descriptor.RequiresModifier;
import build.codemodel.jdk.descriptor.UsesDescriptor;
import build.codemodel.jdk.descriptor.VersionTrait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JDKModuleDescriptor} — source parsing via {@link JDKModuleDescriptor#parse},
 * plus JAR-extraction paths that don't require compiling a fixture (Automatic-Module-Name jars,
 * malformed jars, nonexistent paths). Bytecode extraction from a compiled {@code module-info.class}
 * is covered by {@code JDKModuleDescriptorExtractTests} in {@code codemodel-jdk-populator}, since
 * building that fixture requires {@code javax.tools.ToolProvider} ({@code java.compiler}), which
 * this module no longer depends on.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
class JDKModuleDescriptorTests {

    @TempDir
    Path tempDir;

    private static CodeModel newCodeModel() {
        return new JDKCodeModel(new NonCachingNameProvider());
    }

    private static JDKModuleDescriptor parse(final String source) throws ParseException {
        return JDKModuleDescriptor.parse(newCodeModel(), source);
    }

    // ---- module name / open flag -----------------------------------------

    @Test
    void parseSimpleModuleName() throws ParseException {
        final var md = parse("module com.example { }");
        assertThat(md.moduleName().toString()).isEqualTo("com.example");
    }

    @Test
    void parseOpenModule() throws ParseException {
        final var md = parse("open module com.example { }");
        assertThat(md.hasTrait(OpenModule.class)).isTrue();
    }

    @Test
    void parseClosedModule() throws ParseException {
        final var md = parse("module com.example { }");
        assertThat(md.hasTrait(OpenModule.class)).isFalse();
    }

    // ---- requires --------------------------------------------------------

    @Test
    void parseRequiresPlain() throws ParseException {
        final var md = parse("""
            module com.example {
                requires java.base;
            }
            """);
        assertThat(md.traits(RequiresModuleDescriptor.class)
            .map(r -> r.requiresModuleName().toString())
            .toList())
            .containsExactly("java.base");

        final var req = md.traits(RequiresModuleDescriptor.class).findFirst().orElseThrow();
        assertThat(req.hasTrait(RequiresModifier.class)).isFalse();
    }

    @Test
    void parseRequiresTransitive() throws ParseException {
        final var md = parse("""
            module com.example {
                requires transitive com.lib;
            }
            """);
        final var req = md.traits(RequiresModuleDescriptor.class).findFirst().orElseThrow();
        assertThat(req.traits(RequiresModifier.class).toList()).contains(RequiresModifier.TRANSITIVE);
        assertThat(req.traits(RequiresModifier.class).toList()).doesNotContain(RequiresModifier.STATIC);
    }

    @Test
    void parseRequiresStatic() throws ParseException {
        final var md = parse("""
            module com.example {
                requires static com.lib;
            }
            """);
        final var req = md.traits(RequiresModuleDescriptor.class).findFirst().orElseThrow();
        assertThat(req.traits(RequiresModifier.class).toList()).contains(RequiresModifier.STATIC);
        assertThat(req.traits(RequiresModifier.class).toList()).doesNotContain(RequiresModifier.TRANSITIVE);
    }

    @Test
    void parseMultipleRequires() throws ParseException {
        final var md = parse("""
            module com.example {
                requires java.base;
                requires transitive java.logging;
                requires static java.compiler;
            }
            """);
        assertThat(md.traits(RequiresModuleDescriptor.class).count()).isEqualTo(3);
    }

    // ---- exports ---------------------------------------------------------

    @Test
    void parseUnqualifiedExport() throws ParseException {
        final var md = parse("""
            module com.example {
                exports com.example.api;
            }
            """);
        final ExportsDescriptor exp = md.traits(ExportsDescriptor.class).findFirst().orElseThrow();
        assertThat(exp.packageName().toString()).isEqualTo("com.example.api");
        assertThat(exp.targetModuleNames()).isEmpty();
    }

    @Test
    void parseQualifiedExport() throws ParseException {
        final var md = parse("""
            module com.example {
                exports com.example.internal to com.consumer, com.tests;
            }
            """);
        final ExportsDescriptor exp = md.traits(ExportsDescriptor.class).findFirst().orElseThrow();
        assertThat(exp.packageName().toString()).isEqualTo("com.example.internal");
        assertThat(exp.targetModuleNames().stream().map(Object::toString).toList())
            .containsExactlyInAnyOrder("com.consumer", "com.tests");
    }

    // ---- opens -----------------------------------------------------------

    @Test
    void parseUnqualifiedOpens() throws ParseException {
        final var md = parse("""
            module com.example {
                opens com.example.impl;
            }
            """);
        final OpensDescriptor op = md.traits(OpensDescriptor.class).findFirst().orElseThrow();
        assertThat(op.packageName().toString()).isEqualTo("com.example.impl");
        assertThat(op.targetModuleNames()).isEmpty();
    }

    @Test
    void parseQualifiedOpens() throws ParseException {
        final var md = parse("""
            module com.example {
                opens com.example.impl to com.framework;
            }
            """);
        final OpensDescriptor op = md.traits(OpensDescriptor.class).findFirst().orElseThrow();
        assertThat(op.targetModuleNames().stream().map(Object::toString).toList())
            .containsExactly("com.framework");
    }

    // ---- uses / provides -------------------------------------------------

    @Test
    void parseUses() throws ParseException {
        final var md = parse("""
            module com.example {
                uses com.example.spi.MyService;
            }
            """);
        assertThat(md.traits(UsesDescriptor.class)
            .map(u -> u.serviceType().toString())
            .toList())
            .containsExactly("com.example.spi.MyService");
    }

    @Test
    void parseProvides() throws ParseException {
        final var md = parse("""
            module com.example {
                provides com.example.spi.MyService with com.example.impl.MyServiceImpl;
            }
            """);
        final ProvidesDescriptor prov = md.traits(ProvidesDescriptor.class).findFirst().orElseThrow();
        assertThat(prov.serviceType().toString()).isEqualTo("com.example.spi.MyService");
        assertThat(prov.implementationTypes().stream().map(Object::toString).toList())
            .containsExactly("com.example.impl.MyServiceImpl");
    }

    @Test
    void parseProvidesWithMultipleImplementations() throws ParseException {
        final var md = parse("""
            module com.example {
                provides com.spi.Service with com.impl.Impl1, com.impl.Impl2;
            }
            """);
        final ProvidesDescriptor prov = md.traits(ProvidesDescriptor.class).findFirst().orElseThrow();
        assertThat(prov.implementationTypes()).hasSize(2);
    }

    // ---- comments / imports ----------------------------------------------

    @Test
    void parseIgnoresSingleLineComments() throws ParseException {
        final var md = parse("""
            // this is a module
            module com.example {
                requires java.base; // always needed
            }
            """);
        assertThat(md.moduleName().toString()).isEqualTo("com.example");
        assertThat(md.traits(RequiresModuleDescriptor.class).count()).isEqualTo(1);
    }

    @Test
    void parseIgnoresMultilineComments() throws ParseException {
        final var md = parse("""
            /* copyright notice */
            module com.example {
                /* description */
                requires java.base;
            }
            """);
        assertThat(md.traits(RequiresModuleDescriptor.class).count()).isEqualTo(1);
    }

    @Test
    void parseSkipsImportStatements() throws ParseException {
        final var md = parse("""
            import com.example.SomeAnnotation;
            module com.example {
                requires java.base;
            }
            """);
        assertThat(md.moduleName().toString()).isEqualTo("com.example");
    }

    // ---- annotations -----------------------------------------------------

    @Test
    void parseSimpleAnnotationOnModule() throws ParseException {
        final var md = parse("@SomeAnnotation module com.example { }");
        assertThat(md.annotationClauses()
            .map(a -> a.typeName().toString())
            .toList())
            .containsExactly("SomeAnnotation");
    }

    @Test
    void parseAnnotationWithArguments() throws ParseException {
        final var md = parse("@SomeAnnotation(\"foo\") module com.example { }");
        assertThat(md.annotationClauses().count()).isEqualTo(1);
        assertThat(md.annotationClauses()
            .map(a -> a.typeName().toString())
            .toList())
            .containsExactly("SomeAnnotation");
    }

    @Test
    void parseAnnotationCapturesImplicitStringValue() throws ParseException {
        final var md = parse("@SomeAnnotation(\"foo\") module com.example { }");
        final var value = md.annotationClauses().findFirst().orElseThrow()
            .values().findFirst().orElseThrow();
        assertThat(value.name().toString()).isEqualTo("value");
        assertThat(value.value()).isInstanceOf(AnnotationValue.Value.Literal.class);
        assertThat(((AnnotationValue.Value.Literal) value.value()).value()).isEqualTo("foo");
    }

    @Test
    void parseAnnotationCapturesNamedLiteralArguments() throws ParseException {
        final var md = parse("@Cfg(bar = 1, baz = \"x\", flag = true) module com.example { }");
        final var values = md.annotationClauses().findFirst().orElseThrow().values().toList();
        assertThat(values.stream().map(v -> v.name().toString()).toList())
            .containsExactly("bar", "baz", "flag");
        assertThat(((AnnotationValue.Value.Literal) values.get(0).value()).value()).isEqualTo(1);
        assertThat(((AnnotationValue.Value.Literal) values.get(1).value()).value()).isEqualTo("x");
        assertThat(((AnnotationValue.Value.Literal) values.get(2).value()).value()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void parseAnnotationCapturesClassAndEnumArguments() throws ParseException {
        final var md = parse(
            "@Cfg(type = java.lang.String.class, policy = java.lang.annotation.RetentionPolicy.RUNTIME) module com.example { }");
        final var values = md.annotationClauses().findFirst().orElseThrow().values().toList();
        final var classRef = (AnnotationValue.Value.ClassRef) values.get(0).value();
        assertThat(classRef.typeName().canonicalName()).isEqualTo("java.lang.String");
        final var enumConstant = (AnnotationValue.Value.EnumConstant) values.get(1).value();
        assertThat(enumConstant.typeName().canonicalName()).isEqualTo("java.lang.annotation.RetentionPolicy");
        assertThat(enumConstant.constantName()).isEqualTo("RUNTIME");
    }

    @Test
    void parseAnnotationCapturesArrayArgument() throws ParseException {
        final var md = parse("@Cfg(names = {\"a\", \"b\", \"c\"}) module com.example { }");
        final var value = md.annotationClauses().findFirst().orElseThrow()
            .values().findFirst().orElseThrow().value();
        final var array = (AnnotationValue.Value.Array) value;
        assertThat(array.elements().stream()
            .map(e -> ((AnnotationValue.Value.Literal) e).value())
            .toList())
            .containsExactly("a", "b", "c");
    }

    @Test
    void parseAnnotationCapturesNestedAnnotationArgument() throws ParseException {
        final var md = parse("@Outer(inner = @Inner(id = 7)) module com.example { }");
        final var value = md.annotationClauses().findFirst().orElseThrow()
            .values().findFirst().orElseThrow().value();
        final var nested = (AnnotationValue.Value.Nested) value;
        assertThat(nested.annotation().typeName().toString()).isEqualTo("Inner");
        final var innerValue = nested.annotation().values().findFirst().orElseThrow();
        assertThat(innerValue.name().toString()).isEqualTo("id");
        assertThat(((AnnotationValue.Value.Literal) innerValue.value()).value()).isEqualTo(7);
    }

    @Test
    void parseAnnotationCapturesNegativeCharAndBinaryLiterals() throws ParseException {
        final var md = parse("@Cfg(n = -3, c = 'x', mask = 0b1010) module com.example { }");
        final var values = md.annotationClauses().findFirst().orElseThrow().values().toList();
        assertThat(((AnnotationValue.Value.Literal) values.get(0).value()).value()).isEqualTo(-3);
        assertThat(((AnnotationValue.Value.Literal) values.get(1).value()).value()).isEqualTo('x');
        assertThat(((AnnotationValue.Value.Literal) values.get(2).value()).value()).isEqualTo(0b1010);
    }

    @Test
    void parseAnnotationCapturesArrayOfEnumConstants() throws ParseException {
        final var md = parse(
            "@Cfg(policies = {java.lang.annotation.RetentionPolicy.RUNTIME, java.lang.annotation.RetentionPolicy.SOURCE})"
                + " module com.example { }");
        final var array = (AnnotationValue.Value.Array) md.annotationClauses().findFirst().orElseThrow()
            .values().findFirst().orElseThrow().value();
        assertThat(array.elements().stream()
            .map(e -> ((AnnotationValue.Value.EnumConstant) e).constantName())
            .toList())
            .containsExactly("RUNTIME", "SOURCE");
    }

    @Test
    void parseAnnotationCapturesHexAndTypedNumericLiterals() throws ParseException {
        final var md = parse(
            "@Cfg(mask = 0xFF, big = 0xCAFEL, count = 5L, ratio = 1.5f, span = 2.5d) module com.example { }");
        final var values = md.annotationClauses().findFirst().orElseThrow().values().toList();
        assertThat(((AnnotationValue.Value.Literal) values.get(0).value()).value()).isEqualTo(0xFF);
        assertThat(((AnnotationValue.Value.Literal) values.get(1).value()).value()).isEqualTo(0xCAFEL);
        assertThat(((AnnotationValue.Value.Literal) values.get(2).value()).value()).isEqualTo(5L);
        assertThat(((AnnotationValue.Value.Literal) values.get(3).value()).value()).isEqualTo(1.5f);
        assertThat(((AnnotationValue.Value.Literal) values.get(4).value()).value()).isEqualTo(2.5d);
    }

    @Test
    void parseAnnotationDropsArgumentsOnUnparseableValueShape() throws ParseException {
        final var md = parse("@Cfg(bad = {1, 2) module com.example { }");
        final var annotation = md.annotationClauses().findFirst().orElseThrow();
        assertThat(annotation.typeName().toString()).isEqualTo("Cfg");
        assertThat(annotation.values().count()).isEqualTo(0);
    }

    @Test
    void parseAnnotationDropsAllArgumentsWhenNestedAnnotationValueIsUnparseable() throws ParseException {
        final var md = parse("@Outer(id = 1, inner = @Inner(bad = {2, 3)) module com.example { }");
        final var annotation = md.annotationClauses().findFirst().orElseThrow();
        assertThat(annotation.typeName().toString()).isEqualTo("Outer");
        assertThat(annotation.values().count()).isEqualTo(0);
    }

    @Test
    void parseAnnotationSkipsUnattributableBareIdentifierValue() throws ParseException {
        final var md = parse("@Cfg(RUNTIME) module com.example { }");
        final var annotation = md.annotationClauses().findFirst().orElseThrow();
        assertThat(annotation.typeName().toString()).isEqualTo("Cfg");
        assertThat(annotation.values().count()).isEqualTo(0);
    }

    @Test
    void parseAnnotationDropsArgumentsOnTextBlockValue() throws ParseException {
        final var md = parse("@Cfg(doc = \"\"\"\ntext\n\"\"\") module com.example { }");
        final var annotation = md.annotationClauses().findFirst().orElseThrow();
        assertThat(annotation.typeName().toString()).isEqualTo("Cfg");
        assertThat(annotation.values().count()).isEqualTo(0);
    }

    @Test
    void parseQualifiedAnnotationOnModule() throws ParseException {
        final var md = parse("@some.pkg.Ann module com.example { }");
        assertThat(md.annotationClauses()
            .map(a -> a.typeName().toString())
            .toList())
            .containsExactly("some.pkg.Ann");
    }

    @Test
    void parseMultipleAnnotationsOnModule() throws ParseException {
        final var md = parse("@First @Second module com.example { }");
        assertThat(md.annotationClauses()
            .map(a -> a.typeName().toString())
            .toList())
            .containsExactlyInAnyOrder("First", "Second");
    }

    @Test
    void parseAnnotationWithOpenModule() throws ParseException {
        final var md = parse("@Ann open module com.example { }");
        assertThat(md.annotationClauses().count()).isEqualTo(1);
        assertThat(md.isOpen()).isTrue();
    }

    @Test
    void annotationClausesEmptyForUnannotatedModule() throws ParseException {
        assertThat(parse("module com.example { }").annotationClauses().count()).isEqualTo(0);
    }

    // ---- error handling --------------------------------------------------

    @Test
    void parseThrowsOnInvalidSyntax() {
        assertThatThrownBy(() -> parse("not a module"))
            .isInstanceOf(ParseException.class);
    }

    // ---- registered in CodeModel -----------------------------------------

    @Test
    void parsedDescriptorIsRegisteredInCodeModel() throws ParseException {
        final var codeModel = newCodeModel();
        JDKModuleDescriptor.parse(codeModel, "module com.registered { }");

        final var moduleName = codeModel.getNameProvider().getModuleName("com.registered").orElseThrow();
        assertThat(codeModel.getModuleDescriptor(moduleName)).isPresent();
    }

    // ---- VersionTrait ----------------------------------------------------

    @Test
    void versionTraitWrapsVersion() {
        final var trait = VersionTrait.of(Version.parse("1.2.3"));
        assertThat(trait.version()).isEqualTo(Version.parse("1.2.3"));
    }

    // ---- extract: Automatic-Module-Name fallback -------------------------

    @Test
    void extractAutoModuleName() throws Exception {
        final var result = JDKModuleDescriptor.extract(newCodeModel(),
            autoModuleJar("com.auto.module", null));
        assertThat(result).isPresent();
        assertThat(result.get().moduleName().toString()).isEqualTo("com.auto.module");
        assertThat(result.get().hasTrait(VersionTrait.class)).isFalse();
    }

    @Test
    void extractAutoModuleVersion() throws Exception {
        final var result = JDKModuleDescriptor.extract(newCodeModel(),
            autoModuleJar("com.auto.module", "3.1.0"));
        assertThat(result.get().hasTrait(VersionTrait.class)).isTrue();
        final var vt = result.get().traits(VersionTrait.class).findFirst().orElseThrow();
        assertThat(vt.version()).isEqualTo(Version.parse("3.1.0"));
    }

    // ---- extract: edge cases --------------------------------------------

    @Test
    void extractFromPlainJarReturnsEmpty() throws Exception {
        final Path jar = Files.createTempFile(tempDir, "plain", ".jar");
        try (var jos = new JarOutputStream(Files.newOutputStream(jar))) {
            jos.putNextEntry(new JarEntry("com/example/Foo.class"));
            jos.write(new byte[0]);
            jos.closeEntry();
        }
        assertThat(JDKModuleDescriptor.extract(newCodeModel(), jar)).isEmpty();
    }

    @Test
    void extractFromNonexistentPathThrows() {
        assertThatThrownBy(() ->
            JDKModuleDescriptor.extract(newCodeModel(), Path.of("no-such.jar")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- convenience accessors ------------------------------------------

    @Test
    void isOpenDelegatesToOpenModuleTrait() throws ParseException {
        assertThat(parse("open module com.example { }").isOpen()).isTrue();
        assertThat(parse("module com.example { }").isOpen()).isFalse();
    }

    @Test
    void versionAccessorEmptyWhenNotDeclared() throws ParseException {
        assertThat(parse("module com.example { }").version()).isEmpty();
    }

    @Test
    void isAutomaticSetForAutoModuleJar() throws Exception {
        final var result = JDKModuleDescriptor.extract(newCodeModel(),
            autoModuleJar("com.auto.module", null));
        assertThat(result.get().isAutomatic()).isTrue();
        assertThat(result.get().traits(ModuleModifier.class).toList())
            .containsExactly(ModuleModifier.AUTOMATIC);
    }

    @Test
    void isAutomaticFalseForRegularModule() throws ParseException {
        assertThat(parse("module com.example { }").isAutomatic()).isFalse();
    }

    @Test
    void requiresVersionEmptyForSourceParsed() throws ParseException {
        final var md = parse("module com.example { requires java.base; }");
        final var req = md.requiresClauses().findFirst().orElseThrow();
        assertThat(JDKModuleDescriptor.requiresVersion(req)).isEmpty();
    }

    // ---- include() -------------------------------------------------------

    @Test
    void includeDeduplicatesRequiresByModuleName() throws ParseException {
        final var base = parse("module com.a { requires java.base; }");
        final var other = parse("module com.b { requires java.base; requires java.logging; }");
        base.include(other);
        assertThat(base.requiresClauses()
            .map(r -> r.requiresModuleName().toString())
            .toList())
            .containsExactlyInAnyOrder("java.base", "java.logging");
    }

    @Test
    void includeDeduplicatesExportsByPackageName() throws ParseException {
        final var base = parse("module com.a { exports com.shared.api; }");
        final var other = parse("module com.b { exports com.shared.api; exports com.extra; }");
        base.include(other);
        assertThat(base.exportsClauses()
            .map(e -> e.packageName().toString())
            .toList())
            .containsExactlyInAnyOrder("com.shared.api", "com.extra");
    }

    @Test
    void includeDeduplicatesOpensByPackageName() throws ParseException {
        final var base = parse("module com.a { opens com.shared.internal; }");
        final var other = parse("module com.b { opens com.shared.internal; opens com.extra.internal; }");
        base.include(other);
        assertThat(base.opensClauses()
            .map(o -> o.packageName().toString())
            .toList())
            .containsExactlyInAnyOrder("com.shared.internal", "com.extra.internal");
    }

    @Test
    void includeDeduplicatesUsesByServiceType() throws ParseException {
        final var base = parse("module com.a { uses com.shared.Service; }");
        final var other = parse("module com.b { uses com.shared.Service; uses com.extra.Other; }");
        base.include(other);
        assertThat(base.usesClauses()
            .map(u -> u.serviceType().toString())
            .toList())
            .containsExactlyInAnyOrder("com.shared.Service", "com.extra.Other");
    }

    @Test
    void includeMergesDistinctDirectives() throws ParseException {
        final var base = parse("""
            module com.a {
                requires java.base;
                exports com.a.api;
                uses com.a.spi.Service;
                provides com.a.spi.Service with com.a.impl.ServiceImpl;
            }
            """);
        final var other = parse("""
            module com.b {
                requires java.logging;
                exports com.b.api;
                uses com.b.spi.Other;
                provides com.b.spi.Other with com.b.impl.OtherImpl;
            }
            """);
        base.include(other);
        assertThat(base.requiresClauses().count()).isEqualTo(2);
        assertThat(base.exportsClauses().count()).isEqualTo(2);
        assertThat(base.usesClauses().count()).isEqualTo(2);
        assertThat(base.providesClauses().count()).isEqualTo(2);
    }

    // ---- extractFresh() --------------------------------------------------

    @Test
    void extractFreshAutoModuleDoesNotRegister() throws Exception {
        final CodeModel codeModel = newCodeModel();
        final var result = JDKModuleDescriptor.extractFresh(codeModel,
            autoModuleJar("com.fresh.auto", null));
        assertThat(result).isPresent();
        assertThat(result.get().isAutomatic()).isTrue();
        final var moduleName = codeModel.getNameProvider()
            .getModuleName("com.fresh.auto").orElseThrow();
        assertThat(codeModel.getModuleDescriptor(moduleName)).isEmpty();
    }

    // ---- JAR-building helpers -------------------------------------------

    private Path autoModuleJar(final String moduleName, final String version) throws IOException {
        final Path jar = Files.createTempFile(tempDir, "auto-module", ".jar");
        final var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", moduleName);
        if (version != null) {
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        }
        try (var jos = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            // intentionally empty — only the manifest matters
        }
        return jar;
    }
}
