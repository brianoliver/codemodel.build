package build.codemodel.jdk.descriptor;

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

import build.base.parsing.Filter;
import build.base.parsing.ParseException;
import build.base.parsing.Scanner;
import build.base.version.Version;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.AbstractModuleDescriptor;
import build.codemodel.foundation.descriptor.RequiresModuleDescriptor;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.Namespace;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.AnnotationValue;
import build.codemodel.foundation.usage.SpecificTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * A {@link build.codemodel.foundation.descriptor.ModuleDescriptor} implementation for the
 * <i>JDK Code Model</i>, representing a JPMS module declaration.
 * <p>
 * Instances are created via:
 * <ul>
 *   <li>{@link #parse(CodeModel, Reader)} / {@link #parse(CodeModel, String)} — Scanner-based
 *       {@code module-info.java} source parser</li>
 *   <li>{@link #extract(CodeModel, Path)} — ClassFile API extraction from a compiled JAR</li>
 *   <li>{@link CodeModel#createModuleDescriptor(ModuleName, java.util.function.BiFunction)
 *       codeModel.createModuleDescriptor(name, JDKModuleDescriptor::of)} — programmatic construction</li>
 * </ul>
 * <p>
 * JPMS information is stored as {@link build.codemodel.foundation.descriptor.Trait}s:
 * {@link OpenModule}, {@link RequiresModuleDescriptor} (with optional {@link RequiresModifier}),
 * {@link ExportsDescriptor}, {@link OpensDescriptor}, {@link ProvidesDescriptor},
 * {@link UsesDescriptor}, and {@link VersionTrait}.
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
public final class JDKModuleDescriptor
    extends AbstractModuleDescriptor {

    /**
     * The filename of a JPMS module declaration source file.
     */
    public static final String SOURCE_FILENAME = "module-info.java";

    private JDKModuleDescriptor(final CodeModel codeModel,
                                final ModuleName moduleName) {
        super(codeModel, moduleName);
    }

    /**
     * Factory method compatible with
     * {@link CodeModel#createModuleDescriptor(ModuleName, java.util.function.BiFunction)}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param moduleName the {@link ModuleName}
     * @return a new {@link JDKModuleDescriptor}
     */
    public static JDKModuleDescriptor of(final CodeModel codeModel,
                                         final ModuleName moduleName) {
        return new JDKModuleDescriptor(codeModel, moduleName);
    }

    // ---- Populate from bytecode (ModuleAttribute) -------------------------

    /**
     * Populates this descriptor's traits from the ClassFile API's {@link ModuleAttribute}.
     * Called by {@link #extract(CodeModel, Path)} after creating the descriptor.
     *
     * @param mod the {@link ModuleAttribute} from a parsed {@code module-info.class}
     */
    public void populateFrom(final ModuleAttribute mod) {

        if (mod.moduleFlags().contains(AccessFlag.OPEN)) {
            addTrait(OpenModule.OPEN);
        }
        if (mod.moduleFlags().contains(AccessFlag.SYNTHETIC)) {
            addTrait(ModuleModifier.SYNTHETIC);
        }
        if (mod.moduleFlags().contains(AccessFlag.MANDATED)) {
            addTrait(ModuleModifier.MANDATED);
        }

        mod.moduleVersion()
            .map(v -> v.stringValue())
            .filter(v -> !v.isBlank())
            .flatMap(Version::tryParse)
            .ifPresent(v -> addTrait(VersionTrait.of(v)));

        mod.requires().forEach(req -> {
            final Optional<Version> version = req.requiresVersion()
                .map(v -> v.stringValue())
                .filter(v -> !v.isBlank())
                .flatMap(Version::tryParse);
            addRequires(req.requires().name().stringValue(),
                req.requiresFlags().contains(AccessFlag.TRANSITIVE),
                req.requiresFlags().contains(AccessFlag.STATIC_PHASE),
                req.requiresFlags().contains(AccessFlag.SYNTHETIC),
                req.requiresFlags().contains(AccessFlag.MANDATED),
                version);
        });

        mod.exports().forEach(exp -> {
            final Optional<PackageDirectiveModifier> modifier;
            if (exp.exportsFlags().contains(AccessFlag.MANDATED)) {
                modifier = Optional.of(PackageDirectiveModifier.MANDATED);
            } else if (exp.exportsFlags().contains(AccessFlag.SYNTHETIC)) {
                modifier = Optional.of(PackageDirectiveModifier.SYNTHETIC);
            } else {
                modifier = Optional.empty();
            }
            addExports(exp.exportedPackage().name().stringValue(),
                exp.exportsTo().stream().map(me -> me.name().stringValue()),
                modifier);
        });

        mod.opens().forEach(op -> {
            final Optional<PackageDirectiveModifier> modifier;
            if (op.opensFlags().contains(AccessFlag.MANDATED)) {
                modifier = Optional.of(PackageDirectiveModifier.MANDATED);
            } else if (op.opensFlags().contains(AccessFlag.SYNTHETIC)) {
                modifier = Optional.of(PackageDirectiveModifier.SYNTHETIC);
            } else {
                modifier = Optional.empty();
            }
            addOpens(op.openedPackage().name().stringValue(),
                op.opensTo().stream().map(me -> me.name().stringValue()),
                modifier);
        });

        mod.provides().forEach(prov ->
            addProvides(prov.provides().asInternalName(),
                prov.providesWith().stream().map(ce -> ce.asInternalName())));

        mod.uses().forEach(use -> addUses(use.asInternalName()));
    }

    // ---- Static factories ------------------------------------------------

    /**
     * Parses a {@code module-info.java} source and registers the resulting
     * {@link JDKModuleDescriptor} in the given {@link CodeModel}.
     *
     * @param codeModel the {@link CodeModel} to register the descriptor in
     * @param reader    the source {@link Reader}
     * @return the created and populated {@link JDKModuleDescriptor}
     * @throws ParseException if the source is not valid {@code module-info.java} syntax
     */
    public static JDKModuleDescriptor parse(final CodeModel codeModel,
                                            final Reader reader) throws ParseException {

        final Pattern NAME_PATTERN =
            Pattern.compile("([a-zA-Z][a-zA-Z0-9_$]*)(\\.[a-zA-Z][a-zA-Z0-9_$]*)*");

        final String CLOSE_BRACE = "}";
        final String COMMA = ",";
        final String EXPORTS = "exports";
        final String IMPORT = "import";
        final String MODULE = "module";
        final String OPEN = "open";
        final String OPEN_BRACE = "{";
        final String OPENS = "opens";
        final String PROVIDES = "provides";
        final String REQUIRES = "requires";
        final String SEMICOLON = ";";
        final String STATIC = "static";
        final String TO = "to";
        final String TRANSITIVE = "transitive";
        final String USES = "uses";
        final String WITH = "with";

        final Scanner scanner = new Scanner(reader)
            .register(Filter.WHITESPACE)
            .register(Filter.JAVA_SINGLE_LINE_COMMENT)
            .register(Filter.JAVA_MULTILINE_COMMENT);

        // skip any import statements that legally precede the module declaration
        while (scanner.follows(IMPORT)) {
            scanner.consume(IMPORT);
            scanner.consume(Pattern.compile("[^;]+"));
            scanner.consume(SEMICOLON);
        }

        // capture annotations that legally precede the module declaration (e.g. @SomeAnnotation or @Some.Annotation(bar = 1))
        final var annotations = new ArrayList<ParsedAnnotation>();
        while (scanner.follows(ANNOTATION_TOKEN)) {
            final String token = scanner.consume(ANNOTATION_TOKEN);
            final var values = new ArrayList<AnnotationValue>();
            if (scanner.follows("(")) {
                parseAnnotationArguments(codeModel, scanner.consumeBalanced('(', ')'), values);
            }
            annotations.add(new ParsedAnnotation(token.substring(1), values)); // strip leading @
        }

        final boolean open = scanner.optionallyConsume(OPEN).isPresent();
        scanner.consume(MODULE);
        final String rawName = scanner.consume(NAME_PATTERN);

        final ModuleName moduleName = codeModel.getNameProvider().getModuleName(rawName)
            .orElseThrow(() -> new ParseException(null, "Invalid module name", rawName));

        final JDKModuleDescriptor descriptor =
            codeModel.createModuleDescriptor(moduleName, JDKModuleDescriptor::of);

        if (open) {
            descriptor.computeIfAbsent(OpenModule.class, _ -> OpenModule.OPEN);
        }

        annotations.forEach(annotation ->
            descriptor.addTrait(AnnotationTypeUsage.of(codeModel,
                resolveTypeNameByFqn(codeModel, annotation.name()),
                annotation.values().stream())));

        scanner.consume(OPEN_BRACE);

        while (scanner.hasNext() && !scanner.follows(CLOSE_BRACE)) {
            if (scanner.optionallyConsume(REQUIRES).isPresent()) {
                final boolean isStatic = scanner.optionallyConsume(STATIC).isPresent();
                final boolean isTransitive = !isStatic && scanner.optionallyConsume(TRANSITIVE).isPresent();
                descriptor.addRequires(scanner.consume(NAME_PATTERN), isTransitive, isStatic,
                    false, false, Optional.empty());
                scanner.consume(SEMICOLON);
            } else if (scanner.optionallyConsume(EXPORTS).isPresent()) {
                descriptor.addExports(scanner.consume(NAME_PATTERN),
                    parsePackageTargets(scanner, TO, NAME_PATTERN, COMMA).stream(),
                    Optional.empty());
                scanner.consume(SEMICOLON);
            } else if (scanner.optionallyConsume(OPENS).isPresent()) {
                descriptor.addOpens(scanner.consume(NAME_PATTERN),
                    parsePackageTargets(scanner, TO, NAME_PATTERN, COMMA).stream(),
                    Optional.empty());
                scanner.consume(SEMICOLON);
            } else if (scanner.optionallyConsume(USES).isPresent()) {
                descriptor.addUses(scanner.consume(NAME_PATTERN));
                scanner.consume(SEMICOLON);
            } else if (scanner.optionallyConsume(PROVIDES).isPresent()) {
                final String svcName = scanner.consume(NAME_PATTERN);
                scanner.consume(WITH);
                final var providers = new ArrayList<String>();
                do {
                    providers.add(scanner.consume(NAME_PATTERN));
                    if (scanner.follows(COMMA)) {
                        scanner.consume(COMMA);
                    }
                } while (scanner.follows(NAME_PATTERN));
                descriptor.addProvides(svcName, providers.stream());
                scanner.consume(SEMICOLON);
            } else {
                throw new ParseException(
                    scanner.getLocation(),
                    "Invalid module-info.java syntax",
                    "exports, opens, uses, provides, or requires");
            }
        }

        scanner.consume(CLOSE_BRACE);

        return descriptor;
    }

    /**
     * Parses a {@code module-info.java} source string and registers the resulting
     * {@link JDKModuleDescriptor} in the given {@link CodeModel}.
     *
     * @param codeModel the {@link CodeModel} to register the descriptor in
     * @param source    the {@code module-info.java} source
     * @return the created and populated {@link JDKModuleDescriptor}
     * @throws ParseException if the source is not valid {@code module-info.java} syntax
     */
    public static JDKModuleDescriptor parse(final CodeModel codeModel,
                                            final String source) throws ParseException {

        return parse(codeModel, new StringReader(source));
    }

    /**
     * Extracts a {@link JDKModuleDescriptor} from the {@code module-info.class} inside the JAR at
     * the given {@link Path} and registers it in the given {@link CodeModel}.
     * Falls back to the {@code Automatic-Module-Name} manifest attribute for automatic modules.
     *
     * @param codeModel the {@link CodeModel} to register the descriptor in
     * @param path      the {@link Path} of a JAR file
     * @return the {@link Optional} {@link JDKModuleDescriptor}, or {@link Optional#empty()} if the
     * JAR carries no recognisable JPMS module information
     * @throws IllegalArgumentException if the path does not exist
     * @throws UncheckedIOException     if the JAR cannot be read
     */
    public static Optional<JDKModuleDescriptor> extract(final CodeModel codeModel,
                                                        final Path path) {
        return extractWith(codeModel, path,
            (cm, mn) -> cm.createModuleDescriptor(mn, JDKModuleDescriptor::of));
    }

    /**
     * Extracts a {@link JDKModuleDescriptor} from the {@code module-info.class} inside the JAR at
     * the given {@link Path} <em>without</em> registering it in the {@link CodeModel}.
     * Falls back to the {@code Automatic-Module-Name} manifest attribute for automatic modules.
     * <p>
     * Use this when the caller maintains its own descriptor cache keyed by artifact coordinates
     * (groupId:artifactId:version) so that two JARs sharing the same JPMS module name but carrying
     * different versions receive independent descriptor objects rather than sharing the single entry
     * that {@link CodeModel#createModuleDescriptor} would return.
     *
     * @param codeModel the {@link CodeModel} used for name resolution and child-descriptor creation
     * @param path      the {@link Path} of a JAR file
     * @return the {@link Optional} {@link JDKModuleDescriptor}, or {@link Optional#empty()} if the
     * JAR carries no recognisable JPMS module information
     * @throws IllegalArgumentException if the path does not exist
     * @throws UncheckedIOException     if the JAR cannot be read
     */
    public static Optional<JDKModuleDescriptor> extractFresh(final CodeModel codeModel,
                                                             final Path path) {
        return extractWith(codeModel, path, JDKModuleDescriptor::of);
    }

    private static Optional<JDKModuleDescriptor> extractWith(final CodeModel codeModel,
                                                             final Path path,
                                                             final BiFunction<CodeModel, ModuleName, JDKModuleDescriptor> factory) {

        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Path does not exist: " + path);
        }

        try (JarFile jarFile = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, Runtime.version())) {

            final var entry = Optional.ofNullable(jarFile.getJarEntry("module-info.class"));

            if (entry.isPresent()) {
                final byte[] bytes = jarFile.getInputStream(entry.get()).readAllBytes();
                final var classModel = ClassFile.of().parse(bytes);
                final Optional<ModuleAttribute> moduleAttr =
                    classModel.findAttribute(java.lang.classfile.Attributes.module());

                if (moduleAttr.isEmpty()) {
                    return Optional.empty();
                }

                final ModuleAttribute mod = moduleAttr.get();
                final String rawName = mod.moduleName().name().stringValue();

                return codeModel.getNameProvider().getModuleName(rawName).map(moduleName -> {
                    final var descriptor = factory.apply(codeModel, moduleName);
                    descriptor.populateFrom(mod);
                    return descriptor;
                });
            }

            // fallback: automatic module via Automatic-Module-Name manifest attribute
            final var manifest = jarFile.getManifest();
            if (manifest != null) {
                final String autoName =
                    manifest.getMainAttributes().getValue("Automatic-Module-Name");
                if (autoName != null && !autoName.isBlank()) {
                    return codeModel.getNameProvider().getModuleName(autoName.trim()).map(moduleName -> {
                        final var descriptor = factory.apply(codeModel, moduleName);
                        descriptor.addTrait(ModuleModifier.AUTOMATIC);
                        manifestVersion(manifest).ifPresent(v -> descriptor.addTrait(VersionTrait.of(v)));
                        return descriptor;
                    });
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read JAR: " + path, e);
        }

        return Optional.empty();
    }

    // ---- Convenience accessors -------------------------------------------

    /**
     * Returns the module version if one was recorded.
     *
     * @return the {@link Optional} {@link Version}
     */
    public Optional<Version> version() {
        return getTrait(VersionTrait.class).map(VersionTrait::version);
    }

    /**
     * Returns {@code true} if this is an open module ({@code open module ...}).
     *
     * @return {@code true} if the module is open
     */
    public boolean isOpen() {
        return hasTrait(OpenModule.class);
    }

    /**
     * Returns {@code true} if this is an automatic module (derived from a plain JAR via
     * {@code Automatic-Module-Name}). Never {@code true} for source-parsed descriptors.
     *
     * @return {@code true} if the module is automatic
     */
    public boolean isAutomatic() {
        return traits(ModuleModifier.class).anyMatch(m -> m == ModuleModifier.AUTOMATIC);
    }

    /**
     * Returns {@code true} if this module is marked {@code SYNTHETIC} in bytecode.
     * Never {@code true} for source-parsed descriptors.
     *
     * @return {@code true} if the module is synthetic
     */
    public boolean isSynthetic() {
        return traits(ModuleModifier.class).anyMatch(m -> m == ModuleModifier.SYNTHETIC);
    }

    /**
     * Returns {@code true} if this module is marked {@code MANDATED} in bytecode.
     * Never {@code true} for source-parsed descriptors.
     *
     * @return {@code true} if the module is mandated
     */
    public boolean isMandated() {
        return traits(ModuleModifier.class).anyMatch(m -> m == ModuleModifier.MANDATED);
    }

    /**
     * Returns the version declared on a {@code requires} clause, if any.
     * Only present in bytecode-extracted descriptors; always empty for source-parsed descriptors.
     *
     * @param req the {@link RequiresModuleDescriptor}
     * @return the {@link Optional} {@link Version}
     */
    public static Optional<Version> requiresVersion(final RequiresModuleDescriptor req) {
        return req.getTrait(RequiresVersionTrait.class).map(RequiresVersionTrait::version);
    }

    /**
     * Copies all JPMS directives from {@code other} into this descriptor, deduplicating
     * each directive type: {@code requires} by module name, {@code exports} and {@code opens}
     * by package name, {@code provides} and {@code uses} by service type.
     *
     * @param other the {@link JDKModuleDescriptor} whose directives should be merged in
     */
    public void include(final JDKModuleDescriptor other) {
        final Set<ModuleName> existingReqs = requiresClauses()
            .map(RequiresModuleDescriptor::requiresModuleName)
            .collect(Collectors.toSet());
        other.requiresClauses()
            .filter(r -> !existingReqs.contains(r.requiresModuleName()))
            .forEach(r -> {
                final var copy = RequiresModuleDescriptor.of(codeModel(), r.requiresModuleName());
                r.traits(RequiresModifier.class).forEach(copy::addTrait);
                requiresVersion(r).ifPresent(v -> copy.addTrait(RequiresVersionTrait.of(v)));
                addTrait(copy);
            });
        final Set<Namespace> existingExports = exportsClauses()
            .map(ExportsDescriptor::packageName)
            .collect(Collectors.toSet());
        other.exportsClauses()
            .filter(e -> !existingExports.contains(e.packageName()))
            .forEach(this::addTrait);
        final Set<Namespace> existingOpens = opensClauses()
            .map(OpensDescriptor::packageName)
            .collect(Collectors.toSet());
        other.opensClauses()
            .filter(o -> !existingOpens.contains(o.packageName()))
            .forEach(this::addTrait);
        final Set<TypeUsage> existingProvides = providesClauses()
            .map(ProvidesDescriptor::serviceType)
            .collect(Collectors.toSet());
        other.providesClauses()
            .filter(p -> !existingProvides.contains(p.serviceType()))
            .forEach(this::addTrait);
        final Set<TypeUsage> existingUses = usesClauses()
            .map(UsesDescriptor::serviceType)
            .collect(Collectors.toSet());
        other.usesClauses()
            .filter(u -> !existingUses.contains(u.serviceType()))
            .forEach(this::addTrait);
    }

    /**
     * Returns all {@code requires} clauses as a stream of {@link RequiresModuleDescriptor}s.
     * Named {@code requiresClauses} to avoid shadowing the foundation method
     * {@link build.codemodel.foundation.descriptor.ModuleDescriptor#requires()}.
     *
     * @return a {@link Stream} of {@link RequiresModuleDescriptor}
     */
    public Stream<RequiresModuleDescriptor> requiresClauses() {
        return traits(RequiresModuleDescriptor.class);
    }

    /**
     * Returns all {@code exports} clauses as a stream of {@link ExportsDescriptor}s.
     *
     * @return a {@link Stream} of {@link ExportsDescriptor}
     */
    public Stream<ExportsDescriptor> exportsClauses() {
        return traits(ExportsDescriptor.class);
    }

    /**
     * Returns all {@code opens} clauses as a stream of {@link OpensDescriptor}s.
     *
     * @return a {@link Stream} of {@link OpensDescriptor}
     */
    public Stream<OpensDescriptor> opensClauses() {
        return traits(OpensDescriptor.class);
    }

    /**
     * Returns all {@code provides} clauses as a stream of {@link ProvidesDescriptor}s.
     *
     * @return a {@link Stream} of {@link ProvidesDescriptor}
     */
    public Stream<ProvidesDescriptor> providesClauses() {
        return traits(ProvidesDescriptor.class);
    }

    /**
     * Returns all {@code uses} clauses as a stream of {@link UsesDescriptor}s.
     *
     * @return a {@link Stream} of {@link UsesDescriptor}
     */
    public Stream<UsesDescriptor> usesClauses() {
        return traits(UsesDescriptor.class);
    }

    /**
     * Returns all annotations declared on the {@code module} declaration as a stream of
     * {@link AnnotationTypeUsage}s. Only populated for source-parsed and javac-tree-based descriptors;
     * bytecode-extracted descriptors return an empty stream.
     *
     * @return a {@link Stream} of {@link AnnotationTypeUsage}
     */
    public Stream<AnnotationTypeUsage> annotationClauses() {
        return traits(AnnotationTypeUsage.class);
    }

    // ---- Private trait-adding helpers ------------------------------------
    // All three sources (ModuleTree, ModuleAttribute, Scanner) funnel through
    // these methods, which own the trait construction logic.

    private static ArrayList<String> parsePackageTargets(final Scanner scanner,
                                                         final String to,
                                                         final Pattern namePattern,
                                                         final String comma) {
        final var targets = new ArrayList<String>();
        if (scanner.optionallyConsume(to).isPresent()) {
            do {
                targets.add(scanner.consume(namePattern));
                if (scanner.follows(comma)) {
                    scanner.consume(comma);
                }
            } while (scanner.follows(namePattern));
        }
        return targets;
    }

    public Optional<RequiresModuleDescriptor> addRequires(final String rawName,
                                                          final boolean isTransitive,
                                                          final boolean isStatic,
                                                          final boolean isSynthetic,
                                                          final boolean isMandated,
                                                          final Optional<Version> version) {

        return codeModel().getNameProvider().getModuleName(rawName).map(reqName -> {
            // idempotent: skip if this module is already in the requires list
            final var existing = requiresClauses().filter(r -> r.requiresModuleName().equals(reqName)).findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
            final var reqDescriptor = RequiresModuleDescriptor.of(codeModel(), reqName);
            if (isTransitive) {
                reqDescriptor.addTrait(RequiresModifier.TRANSITIVE);
            }
            if (isStatic) {
                reqDescriptor.addTrait(RequiresModifier.STATIC);
            }
            if (isSynthetic) {
                reqDescriptor.addTrait(RequiresModifier.SYNTHETIC);
            }
            if (isMandated) {
                reqDescriptor.addTrait(RequiresModifier.MANDATED);
            }
            version.ifPresent(v -> reqDescriptor.addTrait(RequiresVersionTrait.of(v)));
            addTrait(reqDescriptor);
            return reqDescriptor;
        });
    }

    public Optional<ExportsDescriptor> addExports(final String rawPkg,
                                                  final Stream<String> rawTargets,
                                                  final Optional<PackageDirectiveModifier> modifier) {
        final String pkg = rawPkg.replace('/', '.');
        return codeModel().getNameProvider().getNamespace(pkg).map(ns -> {
            final var existing = exportsClauses().filter(e -> e.packageName().equals(ns)).findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
            final var exportsDescriptor = modifier
                .map(m -> ExportsDescriptor.of(codeModel(), ns, resolveModuleNames(rawTargets), m))
                .orElseGet(() -> ExportsDescriptor.of(codeModel(), ns, resolveModuleNames(rawTargets)));
            addTrait(exportsDescriptor);
            return exportsDescriptor;
        });
    }

    public Optional<OpensDescriptor> addOpens(final String rawPkg,
                                              final Stream<String> rawTargets,
                                              final Optional<PackageDirectiveModifier> modifier) {
        final String pkg = rawPkg.replace('/', '.');
        return codeModel().getNameProvider().getNamespace(pkg).map(ns -> {
            final var existing = opensClauses().filter(o -> o.packageName().equals(ns)).findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
            final var opensDescriptor = modifier
                .map(m -> OpensDescriptor.of(codeModel(), ns, resolveModuleNames(rawTargets), m))
                .orElseGet(() -> OpensDescriptor.of(codeModel(), ns, resolveModuleNames(rawTargets)));
            addTrait(opensDescriptor);
            return opensDescriptor;
        });
    }

    public ProvidesDescriptor addProvides(final String rawService,
                                          final Stream<String> rawProviders) {
        final var service = typeUsage(rawService);
        final var existing = providesClauses().filter(p -> p.serviceType().equals(service)).findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        final var impls = rawProviders.map(this::typeUsage);
        final var providesDescriptor = ProvidesDescriptor.of(codeModel(), service, impls);
        addTrait(providesDescriptor);
        return providesDescriptor;
    }

    public UsesDescriptor addUses(final String rawService) {
        final var service = typeUsage(rawService);
        final var existing = usesClauses().filter(u -> u.serviceType().equals(service)).findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        final var usesDescriptor = UsesDescriptor.of(codeModel(), service);
        addTrait(usesDescriptor);
        return usesDescriptor;
    }

    // ---- Private utilities -----------------------------------------------

    private Stream<ModuleName> resolveModuleNames(final Stream<String> rawNames) {
        return rawNames
            .map(n -> codeModel().getNameProvider().getModuleName(n))
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    private TypeUsage typeUsage(final String rawName) {
        final String canonical = rawName.replace('/', '.');
        return SpecificTypeUsage.of(codeModel(), resolveTypeNameByFqn(codeModel(), canonical));
    }

    // ---- Annotation-argument parsing (Scanner path only) ----------------
    // Best-effort textual parse: there is no javac AST or Elements at this call site, so
    // some element values can't be fully attributed from source text alone:
    //   - an unqualified identifier used as an enum constant (e.g. @Foo(RUNTIME) after a
    //     static import) can't be tied to an enum type, and is skipped;
    //   - a qualified constant reference Type.NAME is recorded as an EnumConstant even though
    //     it could equally be a reference to a static-final constant field;
    //   - integer literals are always modelled as int/long (their wrapper type), even where
    //     the element type would narrow them to byte/short.
    // The common JLS 9.6.1 shapes - string/char/numeric/boolean literals, X.class, nested
    // annotations and arrays of any of these - are captured. Text-block (""") values are not.
    // A structural parse failure at any nesting depth drops the whole top-level argument list,
    // leaving the annotation name-only (the prior behaviour).

    private record ParsedAnnotation(String name, List<AnnotationValue> values) {
    }

    private static final Pattern ANNOTATION_TOKEN = Pattern.compile("@[\\w.]+");
    private static final Pattern ANNOTATION_ELEMENT_NAME =
        Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern ANNOTATION_DOTTED_NAME =
        Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern ANNOTATION_STRING_LITERAL =
        Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern ANNOTATION_TEXT_BLOCK_OPEN = Pattern.compile("\"\"\"");
    private static final Pattern ANNOTATION_CHAR_LITERAL =
        Pattern.compile("'(?:\\\\u[0-9a-fA-F]{4}|\\\\[0-7]{1,3}|\\\\.|[^'\\\\])'");
    private static final Pattern ANNOTATION_NUMBER_LITERAL =
        Pattern.compile("[+-]?(?:0[xX][0-9a-fA-F_]+"
            + "|0[bB][01_]+"
            + "|\\.[\\d_]+(?:[eE][+-]?\\d+)?"
            + "|\\d[\\d_]*\\.?[\\d_]*(?:[eE][+-]?\\d+)?)[fFdDlL]?");

    /**
     * Parses the top-level argument list of a module annotation. Best-effort: any structural parse
     * failure - including one raised while parsing a nested annotation - discards every argument
     * parsed so far, leaving the annotation name-only.
     */
    private static void parseAnnotationArguments(final CodeModel codeModel,
                                                 final String body,
                                                 final List<AnnotationValue> out) {
        try {
            final var parsed = new ArrayList<AnnotationValue>();
            readAnnotationArguments(codeModel, body, parsed);
            out.addAll(parsed);
        } catch (final RuntimeException e) {
            // best-effort: a value we can't parse - a shape we don't recognise textually, or one
            // that lexes as a number but isn't a valid Java literal - leaves the annotation name-only
        }
    }

    /**
     * Reads a (possibly nested) annotation argument list into {@code out}, propagating any parse
     * failure to the caller so that {@link #parseAnnotationArguments} can drop the whole list.
     */
    private static void readAnnotationArguments(final CodeModel codeModel,
                                                final String body,
                                                final List<AnnotationValue> out) {
        if (body == null || body.isBlank()) {
            return;
        }
        final Scanner scanner = new Scanner(new StringReader(body))
            .register(Filter.WHITESPACE)
            .register(Filter.JAVA_SINGLE_LINE_COMMENT)
            .register(Filter.JAVA_MULTILINE_COMMENT);
        do {
            parseAnnotationArgument(codeModel, scanner).ifPresent(out::add);
        } while (scanner.optionallyConsume(",").isPresent());
    }

    private static Optional<AnnotationValue> parseAnnotationArgument(final CodeModel codeModel,
                                                                    final Scanner scanner) {
        if (scanner.follows(ANNOTATION_ELEMENT_NAME)) {
            final String head = scanner.consume(ANNOTATION_ELEMENT_NAME);
            if (scanner.optionallyConsume("=").isPresent()) {
                return parseAnnotationValue(codeModel, scanner)
                    .map(v -> AnnotationValue.of(codeModel, head, v));
            }
            // no '=' followed, so 'head' begins the implicit "value" element - a dotted name,
            // X.class, an enum reference, or true / false
            return parseNameValue(codeModel, scanner, head)
                .map(v -> AnnotationValue.of(codeModel, "value", v));
        }
        return parseAnnotationValue(codeModel, scanner)
            .map(v -> AnnotationValue.of(codeModel, "value", v));
    }

    private static Optional<AnnotationValue.Value> parseAnnotationValue(final CodeModel codeModel,
                                                                       final Scanner scanner) {
        if (scanner.follows(ANNOTATION_TEXT_BLOCK_OPEN)) {
            // text blocks would need incidental-whitespace stripping (JLS 3.10.6); rather than
            // risk a garbled value, bail so the whole argument list drops to name-only
            throw new IllegalStateException("text block annotation values are not supported");
        }
        if (scanner.follows("{")) {
            scanner.consume("{");
            final var elements = new ArrayList<AnnotationValue.Value>();
            if (!scanner.follows("}")) {
                do {
                    parseAnnotationValue(codeModel, scanner).ifPresent(elements::add);
                } while (scanner.optionallyConsume(",").isPresent());
            }
            scanner.consume("}");
            return Optional.of(new AnnotationValue.Value.Array(List.copyOf(elements)));
        }
        if (scanner.follows("@")) {
            scanner.consume("@");
            final String name = scanner.consume(ANNOTATION_DOTTED_NAME);
            final var nestedValues = new ArrayList<AnnotationValue>();
            if (scanner.follows("(")) {
                // readAnnotationArguments (not parseAnnotationArguments) so a failure inside the
                // nested annotation propagates and drops the whole top-level list
                readAnnotationArguments(codeModel, scanner.consumeBalanced('(', ')'), nestedValues);
            }
            return Optional.of(new AnnotationValue.Value.Nested(AnnotationTypeUsage.of(codeModel,
                resolveTypeNameByFqn(codeModel, name), nestedValues.stream())));
        }
        if (scanner.follows(ANNOTATION_STRING_LITERAL)) {
            final String raw = scanner.consume(ANNOTATION_STRING_LITERAL);
            return Optional.of(new AnnotationValue.Value.Literal(
                unescapeJavaLiteral(raw.substring(1, raw.length() - 1))));
        }
        if (scanner.follows(ANNOTATION_CHAR_LITERAL)) {
            final String raw = scanner.consume(ANNOTATION_CHAR_LITERAL);
            return Optional.of(new AnnotationValue.Value.Literal(
                unescapeJavaLiteral(raw.substring(1, raw.length() - 1)).charAt(0)));
        }
        if (scanner.follows(ANNOTATION_ELEMENT_NAME)) {
            return parseNameValue(codeModel, scanner, scanner.consume(ANNOTATION_ELEMENT_NAME));
        }
        if (scanner.follows(ANNOTATION_NUMBER_LITERAL)) {
            return Optional.of(new AnnotationValue.Value.Literal(
                parseNumberLiteral(scanner.consume(ANNOTATION_NUMBER_LITERAL))));
        }
        return Optional.empty();
    }

    /**
     * Continues parsing a value whose leading identifier {@code head} has already been consumed:
     * a {@code true}/{@code false} literal, a {@code Foo.class} reference, or a
     * {@code Type.CONSTANT} reference. A bare identifier (no dot) can't be attributed textually
     * and yields {@link Optional#empty()}. A dotted {@code Type.CONSTANT} is modelled as an
     * {@link AnnotationValue.Value.EnumConstant} - textually it is indistinguishable from a
     * reference to a static-final constant field, and enum constants are by far the common case.
     */
    private static Optional<AnnotationValue.Value> parseNameValue(final CodeModel codeModel,
                                                                 final Scanner scanner,
                                                                 final String head) {
        final var name = new StringBuilder(head);
        while (scanner.follows(".")) {
            scanner.consume(".");
            name.append('.').append(scanner.consume(ANNOTATION_ELEMENT_NAME));
        }
        final String full = name.toString();
        if (full.equals("true") || full.equals("false")) {
            return Optional.of(new AnnotationValue.Value.Literal(Boolean.valueOf(full)));
        }
        if (full.endsWith(".class")) {
            return Optional.of(new AnnotationValue.Value.ClassRef(resolveTypeNameByFqn(
                codeModel, full.substring(0, full.length() - ".class".length()))));
        }
        final int lastDot = full.lastIndexOf('.');
        if (lastDot < 0) {
            return Optional.empty();
        }
        return Optional.of(new AnnotationValue.Value.EnumConstant(
            resolveTypeNameByFqn(codeModel, full.substring(0, lastDot)),
            full.substring(lastDot + 1)));
    }

    private static Object parseNumberLiteral(final String raw) {
        final String s = raw.replace("_", "");
        final char last = s.charAt(s.length() - 1);
        final boolean hex = s.regionMatches(true, 0, "0x", 0, 2)
            || s.regionMatches(true, 1, "0x", 0, 2);
        final boolean binary = s.regionMatches(true, 0, "0b", 0, 2)
            || s.regionMatches(true, 1, "0b", 0, 2);
        final boolean longSuffix = last == 'l' || last == 'L';
        if (hex || binary) {
            // radix-prefixed integer literal: strip an optional sign, the 0x/0b prefix and an
            // optional trailing l/L before parsing, so hex digits d/f/D/F are never mistaken for
            // a float/double suffix. Parsed unsigned (JLS 3.10.1) then narrowed to int if unsuffixed.
            final int sign = s.charAt(0) == '-' ? -1 : 1;
            final int start = (sign < 0 || s.charAt(0) == '+' ? 1 : 0) + 2;
            final String digits = s.substring(start, longSuffix ? s.length() - 1 : s.length());
            final long value = sign * Long.parseUnsignedLong(digits, hex ? 16 : 2);
            return longSuffix ? (Object) value : (Object) (int) value;
        }
        if (longSuffix) {
            return Long.decode(s.substring(0, s.length() - 1));
        }
        if (last == 'f' || last == 'F') {
            return Float.valueOf(s.substring(0, s.length() - 1));
        }
        if (last == 'd' || last == 'D') {
            return Double.valueOf(s.substring(0, s.length() - 1));
        }
        if (s.indexOf('.') >= 0 || s.indexOf('e') > 0 || s.indexOf('E') > 0) {
            return Double.valueOf(s);
        }
        try {
            return Integer.decode(s);
        } catch (final NumberFormatException e) {
            return Long.decode(s);
        }
    }

    private static String unescapeJavaLiteral(final String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        final var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            c = s.charAt(++i);
            switch (c) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 's' -> sb.append(' ');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                case '0', '1', '2', '3', '4', '5', '6', '7' -> {
                    int octal = c - '0';
                    final int maxMore = c <= '3' ? 2 : 1;
                    for (int n = 0; n < maxMore && i + 1 < s.length()
                        && s.charAt(i + 1) >= '0' && s.charAt(i + 1) <= '7'; n++) {
                        octal = octal * 8 + (s.charAt(++i) - '0');
                    }
                    sb.append((char) octal);
                }
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Resolves a {@link TypeName} for a fully-qualified type name known only as a {@link String} -
     * there being no javac AST or {@code Elements} available at this call site (text-based
     * {@code module-info} parsing, or {@code provides}/{@code uses} directives naming arbitrary
     * types). Reflectively looks up the {@link ModuleName} declaring the type when it happens to be
     * loadable on this JVM (e.g. a real JDK type such as {@code java.lang.Deprecated}), without
     * running its static initializers. Falls back to no module when the type can't be loaded this
     * way (e.g. names in tests that don't correspond to real classes).
     */
    private static TypeName resolveTypeNameByFqn(final CodeModel codeModel, final String fullyQualifiedName) {
        final var nameProvider = codeModel.getNameProvider();
        try {
            final var clazz = Class.forName(fullyQualifiedName, false, JDKModuleDescriptor.class.getClassLoader());
            return nameProvider.getTypeName(clazz);
        } catch (final ClassNotFoundException | LinkageError e) {
            return nameProvider.getEmptyModuleTypeName(fullyQualifiedName);
        }
    }

    private static Optional<Version> manifestVersion(final Manifest manifest) {
        if (manifest == null) {
            return Optional.empty();
        }
        final String raw = manifest.getMainAttributes()
            .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Version.tryParse(raw.split("\\s+")[0]);
    }
}

