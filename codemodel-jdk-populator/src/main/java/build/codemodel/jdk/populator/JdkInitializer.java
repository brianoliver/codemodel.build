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

import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.FormalParameterDescriptor;
import build.codemodel.foundation.descriptor.ThrowableDescriptor;
import build.codemodel.foundation.descriptor.Traitable;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.framework.initialization.Initializer;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.descriptor.EnumConstantDescriptor;
import build.codemodel.jdk.descriptor.FieldInitializerDescriptor;
import build.codemodel.jdk.descriptor.ImportDeclaration;
import build.codemodel.jdk.descriptor.InitializerBlockDescriptor;
import build.codemodel.jdk.descriptor.JDKModuleDescriptor;
import build.codemodel.jdk.descriptor.JDKTypeDescriptor;
import build.codemodel.jdk.descriptor.MethodBodyDescriptor;
import build.codemodel.jdk.descriptor.OpenModule;
import build.codemodel.jdk.descriptor.RecordComponentDescriptor;
import build.codemodel.jdk.populator.descriptor.SourceLocation;
import build.codemodel.objectoriented.descriptor.ConstructorDescriptor;
import build.codemodel.objectoriented.descriptor.DeclarationOrder;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExportsTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.OpensTree;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UsesTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Initializes a {@link build.codemodel.foundation.CodeModel} by parsing Java source files using
 * {@code javac} and building {@link build.codemodel.jdk.descriptor.JDKTypeDescriptor}s.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public class JdkInitializer
    implements Initializer {

    private final List<File> sourceFiles;
    private final List<Path> sourceDirectories;
    private final List<JavaFileObject> javaFileObjects;
    private final List<Path> classpath;
    private final List<Path> modulePath;

    /**
     * When non-null, only compilation units whose source URI satisfies this predicate have their
     * descriptors registered. All compilation units are still compiled together, so types from
     * filtered-out units remain resolvable by javac. Used by rescan to include sibling sources
     * for type resolution without re-registering their already-present descriptors.
     */
    private Predicate<URI> registrationFilter = null;

    private DiagnosticListener<JavaFileObject> diagnosticListener = d -> {
    };
    private final List<String> extraOptions = new ArrayList<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Set at the start of initialize() for use by helper methods
    private CodeModel codeModel;
    private NameProvider nameProvider;
    private TypeMirrorResolver resolver;
    private DocTrees trees;
    private JdkExpressionConverter exprConverter;
    private JdkStatementConverter stmtConverter;

    /**
     * Body/initializer conversions deferred until every compilation unit's type descriptors have
     * been registered, so that a call to a type processed later in source order still resolves.
     * Each task re-establishes the expression converter's type context for its declaring class
     * before converting, since that context is mutated while scanning subsequent classes.
     */
    private final List<Runnable> deferredConversions = new ArrayList<>();

    /**
     * Creates a {@link JdkInitializer} with explicit source inputs.
     *
     * @param sourceFiles       individual {@link File} source files to parse
     * @param sourceDirectories directories to walk recursively for {@code .java} files
     * @param javaFileObjects   in-memory or pre-built {@link JavaFileObject} sources
     */
    public JdkInitializer(final List<File> sourceFiles,
                          final List<Path> sourceDirectories,
                          final List<JavaFileObject> javaFileObjects) {
        this(sourceFiles, sourceDirectories, javaFileObjects, List.of(), List.of());
    }

    /**
     * Creates a {@link JdkInitializer} with explicit source inputs and dependency paths.
     * Callers are responsible for assembling the classpath and module-path lists; this class
     * does no jar classification or module dependency resolution.
     *
     * @param sourceFiles       individual {@link File} source files to parse
     * @param sourceDirectories directories to walk recursively for {@code .java} files
     * @param javaFileObjects   in-memory or pre-built {@link JavaFileObject} sources
     * @param classpath         jars or directories to pass as {@code --class-path}
     * @param modulePath        jars or directories to pass as {@code --module-path}
     */
    public JdkInitializer(final List<File> sourceFiles,
                          final List<Path> sourceDirectories,
                          final List<JavaFileObject> javaFileObjects,
                          final List<Path> classpath,
                          final List<Path> modulePath) {
        this.sourceFiles = sourceFiles;
        this.sourceDirectories = sourceDirectories;
        this.javaFileObjects = javaFileObjects;
        this.classpath = classpath;
        this.modulePath = modulePath;
    }

    /**
     * Creates a {@link JdkInitializer} for a list of individual source files.
     *
     * @param files the {@link File}s to parse
     * @return a new {@link JdkInitializer}
     */
    public static JdkInitializer ofFiles(final List<File> files) {
        return new JdkInitializer(files, List.of(), List.of());
    }

    /**
     * Creates a {@link JdkInitializer} for a source directory, walked recursively for {@code .java} files.
     *
     * @param directory the root source {@link Path}
     * @return a new {@link JdkInitializer}
     */
    public static JdkInitializer ofDirectory(final Path directory) {
        return new JdkInitializer(List.of(), List.of(directory), List.of());
    }

    public JdkInitializer withDiagnosticListener(final DiagnosticListener<JavaFileObject> listener) {
        this.diagnosticListener = listener;
        return this;
    }

    /**
     * Appends additional options to the javac invocation.
     * May be called multiple times; options accumulate in call order.
     * Common uses include {@code --enable-preview} paired with {@code --source <version>},
     * or release-targeting via {@code --release <version>}.
     *
     * @param options the options to append; must not be {@code null}
     * @return this initializer, for chaining
     */
    public JdkInitializer withOptions(final List<String> options) {
        this.extraOptions.addAll(options);
        return this;
    }

    /**
     * Enables preview features for the given source version.
     * Equivalent to {@code withOptions(List.of("--enable-preview", "--source", String.valueOf(sourceVersion)))}.
     * Both flags are always required together; this method enforces that pairing.
     *
     * @param sourceVersion the Java source version (e.g. {@code 25})
     * @return this initializer, for chaining
     */
    public JdkInitializer withEnablePreview(final int sourceVersion) {
        return withOptions(List.of("--enable-preview", "--source", String.valueOf(sourceVersion)));
    }

    /**
     * Enables preview features for the current runtime's feature version.
     * Equivalent to {@link #withEnablePreview(int)} with {@code Runtime.version().feature()}.
     *
     * @return this initializer, for chaining
     */
    public JdkInitializer withEnablePreview() {
        return withEnablePreview(Runtime.version().feature());
    }

    /**
     * Restricts descriptor registration to compilation units whose source URI satisfies the given
     * predicate. Units that do not match are still compiled (providing type resolution context)
     * but produce no descriptors.
     *
     * @param filter predicate over source URIs; {@code null} means register all units
     * @return this initializer, for chaining
     */
    public JdkInitializer withRegistrationFilter(final Predicate<URI> filter) {
        this.registrationFilter = filter;
        return this;
    }

    /**
     * Convenience overload of {@link #rescan(JDKCodeModel, List, List, List, List, List, DiagnosticListener)}.
     */
    public static void rescan(final JDKCodeModel codeModel, final JavaFileObject updatedFile) {
        rescan(codeModel, updatedFile, List.of(), List.of());
    }

    /**
     * Convenience overload of {@link #rescan(JDKCodeModel, List, List, List, List, List, DiagnosticListener)}.
     */
    public static void rescan(final JDKCodeModel codeModel,
                              final JavaFileObject updatedFile,
                              final List<Path> classpath,
                              final List<Path> modulePath) {
        rescan(codeModel, updatedFile, List.of(), classpath, modulePath, List.of(), d -> {
        });
    }

    /**
     * Convenience overload of {@link #rescan(JDKCodeModel, List, List, List, List, List, DiagnosticListener)}.
     */
    public static void rescan(final JDKCodeModel codeModel,
                              final JavaFileObject updatedFile,
                              final List<JavaFileObject> contextFiles,
                              final List<Path> classpath,
                              final List<Path> modulePath,
                              final List<String> compilerOptions,
                              final DiagnosticListener<JavaFileObject> diagnosticListener) {
        rescan(codeModel, List.of(updatedFile), contextFiles, classpath, modulePath, compilerOptions,
            diagnosticListener);
    }

    /**
     * Drops all descriptors sourced from any of {@code updatedFiles} and re-analyzes them together,
     * in one compile, along with any {@code contextFiles} needed for symbol resolution.
     *
     * <p>Batching matters beyond convenience: if two files that reference each other are rescanned
     * one at a time, each rescan takes its own independent snapshot of the other file's on-disk
     * content, and those two snapshots can disagree (a watcher can catch a sibling mid-write between
     * the two calls, or -- as observed in practice -- a single filesystem write can itself emit a
     * transient delete event before its create/modify event lands, so a strictly-sequential rescan
     * can briefly see a perfectly valid sibling as deleted). Compiling every file that actually
     * changed together, in a single pass, means there is exactly one consistent view of the whole
     * batch and no window for one file's rescan to see another in a state that never really existed.
     *
     * <p>Every descriptor sourced from any of {@code updatedFiles} is evicted and the file is
     * re-registered from this compile, whether or not it produced anything (a caller passing an
     * empty-content {@link JavaFileObject} for a genuinely deleted file gets that file's descriptors
     * evicted with nothing to replace them, as expected). There is no protection against evicting a
     * still-existing file whose current on-disk content happens to be mid-edit and unparseable at
     * the moment of rescan -- callers are expected to decide deletion explicitly (e.g. by checking
     * the file still exists on disk) rather than relying on this method to infer it from a compile
     * that produces nothing.
     *
     * @param codeModel          the {@link JDKCodeModel} to rescan
     * @param updatedFiles       the new versions of the source files to analyze together
     * @param contextFiles       additional source files to include in the compilation for resolution
     *                           context only (not evicted; deduplicated against {@code updatedFiles})
     * @param classpath          jars or directories to pass as {@code --class-path}
     * @param modulePath         jars or directories to pass as {@code --module-path}
     * @param compilerOptions    additional {@code javac} argument tokens, resolved from the
     *                           project's own build configuration
     * @param diagnosticListener receives compiler diagnostics (errors, warnings) produced while
     *                           re-analyzing {@code updatedFiles} and {@code contextFiles}
     */
    public static void rescan(final JDKCodeModel codeModel,
                              final List<JavaFileObject> updatedFiles,
                              final List<JavaFileObject> contextFiles,
                              final List<Path> classpath,
                              final List<Path> modulePath,
                              final List<String> compilerOptions,
                              final DiagnosticListener<JavaFileObject> diagnosticListener) {
        final var uris = updatedFiles.stream().map(JavaFileObject::toUri)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        final var allFiles = new ArrayList<JavaFileObject>(updatedFiles);
        contextFiles.stream().filter(f -> !uris.contains(f.toUri())).forEach(allFiles::add);

        codeModel.typeDescriptors()
            .filter(d -> matchesAnyUri(d, uris))
            .forEach(codeModel::removeTypeDescriptor);
        codeModel.moduleDescriptors()
            .filter(d -> matchesAnyUri(d, uris))
            .forEach(codeModel::removeModuleDescriptor);
        new JdkInitializer(List.of(), List.of(), allFiles, classpath, modulePath)
            .withOptions(compilerOptions)
            .withRegistrationFilter(uris::contains)
            .withDiagnosticListener(diagnosticListener)
            .initialize(codeModel);
    }

    private static boolean matchesAnyUri(final Traitable traitable, final Set<URI> uris) {
        return traitable.getTrait(SourceLocation.FilePosition.class)
            .map(fp -> uris.contains(fp.uri()))
            .orElse(false);
    }

    /**
     * Parses all configured sources and registers their {@link build.codemodel.jdk.descriptor.JDKTypeDescriptor}s
     * into the given {@link CodeModel}.
     * May only be called once; throws {@link IllegalStateException} on repeated invocations.
     *
     * @param codeModel the {@link CodeModel} to populate
     */
    @Override
    public void initialize(final CodeModel codeModel) {
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("JdkInitializer.initialize() may only be called once");
        }
        this.codeModel = codeModel;
        this.nameProvider = codeModel.getNameProvider();
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // scanCompleted distinguishes a failure while scanning sources from a failure closing
        // fileManager afterward -- a close() IOException must not be reported as a scan failure,
        // reset the once-only guard, or discard an already fully-populated CodeModel.
        boolean scanCompleted = false;
        try (StandardJavaFileManager fileManager =
                 compiler.getStandardFileManager(diagnosticListener, null, null)) {

            final List<JavaFileObject> combined = collectSources(fileManager);
            if (combined.isEmpty()) {
                scanCompleted = true;
                return;
            }

            final var task =
                compiler.getTask(null, fileManager, diagnosticListener, buildOptions(), null, combined);
            final var javacTask = (JavacTask) task;
            final var compilationUnits = javacTask.parse();
            javacTask.analyze();
            this.trees = DocTrees.instance(javacTask);
            this.resolver = new TypeMirrorResolver(codeModel, javacTask.getElements(), this.trees, null);
            this.exprConverter = new JdkExpressionConverter(codeModel);
            this.stmtConverter = new JdkStatementConverter(codeModel, this.exprConverter);
            this.exprConverter.setStmtConverter(this.stmtConverter);
            this.exprConverter.setAnnotationResolver(this.resolver::createAnnotationTypeUsages);

            for (final var cut : compilationUnits) {
                final var sourceUri = cut.getSourceFile().toUri();
                final boolean register = registrationFilter == null || registrationFilter.test(sourceUri);
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitClass(final ClassTree classTree, final Void unused) {
                        if (!register) {
                            return null;
                        }
                        final TreePath classPath = getCurrentPath();
                        exprConverter.setTypeContext(trees, classPath.getCompilationUnit(),
                            mirror -> resolver.resolve(mirror, null), resolver::resolveTypeName);
                        final var element = trees.getElement(classPath);
                        if (element instanceof TypeElement typeElement
                            && (!typeElement.getQualifiedName().toString().isEmpty()
                            || typeElement.getNestingKind() == NestingKind.ANONYMOUS
                            || typeElement.getNestingKind() == NestingKind.LOCAL)) {
                            exprConverter.setEnclosingType(resolver.resolve(typeElement.asType(), null));
                            processTypeElement(typeElement, classPath);
                        }
                        return super.visitClass(classTree, unused);
                    }

                    @Override
                    public Void visitModule(final ModuleTree moduleTree, final Void unused) {
                        if (!register) {
                            return null;
                        }
                        final var modulePath = getCurrentPath();
                        final var moduleCut = modulePath.getCompilationUnit();
                        nameProvider.getModuleName(moduleTree.getName().toString()).ifPresent(moduleName -> {
                            final var descriptor = codeModel.createModuleDescriptor(
                                moduleName, JDKModuleDescriptor::of);
                            populateModuleDescriptor(descriptor, moduleTree, moduleCut, ann -> {
                                final var annotationPath = new TreePath(modulePath, ann.getAnnotationType());
                                final var element = trees.getElement(annotationPath);
                                return element instanceof TypeElement typeElement
                                    ? resolver.resolveTypeName(typeElement)
                                    : nameProvider.getTypeName(Optional.empty(), ann.getAnnotationType().toString());
                            });
                            addSourceLocation(moduleCut, moduleTree, descriptor);
                        });
                        return null;
                    }
                }.scan(cut, null);
            }

            deferredConversions.forEach(Runnable::run);
            deferredConversions.clear();
            scanCompleted = true;

        } catch (final IOException e) {
            if (scanCompleted) {
                // The scan itself already completed successfully; this IOException came from
                // closing fileManager afterward. The CodeModel is fully and correctly populated,
                // so this must not be reported as an initialization failure.
                return;
            }
            initialized.set(false);
            throw new RuntimeException("Failed to initialize CodeModel from source files", e);
        } catch (final RuntimeException e) {
            initialized.set(false);
            throw e;
        }
    }

    // --- Compiler options ---

    private List<String> buildOptions() {
        final var options = new ArrayList<>(extraOptions);
        if (!classpath.isEmpty()) {
            options.add("--class-path");
            options.add(String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        }
        if (!modulePath.isEmpty()) {
            options.add("--module-path");
            options.add(String.join(File.pathSeparator, modulePath.stream().map(Path::toString).toList()));
        }
        return options.isEmpty() ? null : options;
    }

    // --- Source collection ---

    private List<JavaFileObject> collectSources(final StandardJavaFileManager fileManager) throws IOException {
        final List<File> allFiles = new ArrayList<>(sourceFiles);

        for (final Path directory : sourceDirectories) {
            try (var stream = Files.walk(directory)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .forEach(allFiles::add);
            }
        }

        final List<JavaFileObject> combined = new ArrayList<>(javaFileObjects);
        if (!allFiles.isEmpty()) {
            for (final var jfo : fileManager.getJavaFileObjectsFromFiles(allFiles)) {
                combined.add(jfo);
            }
        }
        return combined;
    }

    // --- Type processing ---

    private void processTypeElement(final TypeElement typeElement,
                                    final TreePath classPath) {
        final var typeName = resolver.resolveTypeName(typeElement);

        // Skip if already registered (can happen with inner types visited by the scanner)
        if (codeModel.getTypeDescriptor(typeName).isPresent()) {
            return;
        }

        final var typeDescriptor = resolver.buildTypeDescriptor(typeName, typeElement);

        final var cut = classPath.getCompilationUnit();
        addSourceLocation(cut, classPath.getLeaf(), typeDescriptor);
        if (typeElement.getNestingKind() == NestingKind.TOP_LEVEL) {
            addImports(typeDescriptor, cut);
        }
        addSuperTypeUsageSourceLocations(typeDescriptor, classPath, cut);
        addRecordComponentSourceLocations(typeDescriptor, classPath, cut);
        addTypeParameterBoundSourceLocations(typeDescriptor, ((ClassTree) classPath.getLeaf()).getTypeParameters(), cut);

        final List<Runnable> bodyTasks = new ArrayList<>();
        processMembers(typeDescriptor, classPath, bodyTasks);

        if (!bodyTasks.isEmpty()) {
            deferredConversions.add(() -> {
                exprConverter.setTypeContext(trees, classPath.getCompilationUnit(),
                    mirror -> resolver.resolve(mirror, null), resolver::resolveTypeName);
                exprConverter.setEnclosingType(resolver.resolve(typeElement.asType(), null));
                bodyTasks.forEach(Runnable::run);
            });
        }
    }

    private void addImports(final JDKTypeDescriptor typeDescriptor, final CompilationUnitTree cut) {
        final var imports = cut.getImports();
        for (int i = 0; i < imports.size(); i++) {
            final var importTree = imports.get(i);
            final var qualifiedId = importTree.getQualifiedIdentifier().toString();
            final boolean isStatic = importTree.isStatic();
            final boolean isOnDemand = qualifiedId.endsWith(".*");
            final var name = isOnDemand ? qualifiedId.substring(0, qualifiedId.length() - 2) : qualifiedId;
            final var importDeclaration = isStatic && isOnDemand
                ? ImportDeclaration.ofStaticOnDemand(codeModel, name, i)
                : isStatic
                  ? ImportDeclaration.ofStatic(codeModel, name, i)
                  : isOnDemand
                    ? ImportDeclaration.ofOnDemand(codeModel, name, i)
                    : ImportDeclaration.of(codeModel, name, i);
            addSourceLocation(cut, importTree, importDeclaration);
            typeDescriptor.addTrait(importDeclaration);
        }
    }

    private void addSuperTypeUsageSourceLocations(final JDKTypeDescriptor typeDescriptor,
                                                  final TreePath classPath,
                                                  final CompilationUnitTree cut) {
        final var classTree = (ClassTree) classPath.getLeaf();

        final var extendsClause = classTree.getExtendsClause();
        if (extendsClause != null) {
            typeDescriptor.parentTypeUsage().ifPresent(usage -> addSourceLocation(cut, extendsClause, usage));
        }

        final var implementsClause = classTree.getImplementsClause();
        final var interfaceUsages = typeDescriptor.interfaceTypeUsages().toList();
        for (int i = 0; i < implementsClause.size() && i < interfaceUsages.size(); i++) {
            addSourceLocation(cut, implementsClause.get(i), interfaceUsages.get(i));
        }
    }

    private void addRecordComponentSourceLocations(final JDKTypeDescriptor typeDescriptor,
                                                   final TreePath classPath,
                                                   final CompilationUnitTree cut) {
        final var classTree = (ClassTree) classPath.getLeaf();
        final var componentTypeTrees = classTree.getMembers().stream()
            .filter(VariableTree.class::isInstance)
            .map(VariableTree.class::cast)
            .collect(Collectors.toMap(vt -> vt.getName().toString(), VariableTree::getType, (a, _) -> a));

        typeDescriptor.traits(RecordComponentDescriptor.class).forEach(component -> {
            final var typeTree = componentTypeTrees.get(component.name().toString());
            if (typeTree != null) {
                addSourceLocation(cut, typeTree, component.type());
            }
        });
    }

    private void addThrowsSourceLocations(final Traitable traitable,
                                          final List<? extends ExpressionTree> throwsTrees,
                                          final CompilationUnitTree cut) {
        final var throwables = traitable.traits(ThrowableDescriptor.class).toList();
        for (int i = 0; i < throwsTrees.size() && i < throwables.size(); i++) {
            addSourceLocation(cut, throwsTrees.get(i), throwables.get(i).throwable());
        }
    }

    private void addTypeParameterBoundSourceLocations(final Traitable traitable,
                                                      final List<? extends TypeParameterTree> typeParamTrees,
                                                      final CompilationUnitTree cut) {
        final var descriptor = traitable.getTrait(ParameterizedTypeDescriptor.class);
        if (descriptor.isEmpty()) {
            return;
        }
        final var typeVariables = descriptor.get().typeVariables().toList();
        for (int i = 0; i < typeParamTrees.size() && i < typeVariables.size(); i++) {
            final var bounds = typeParamTrees.get(i).getBounds();
            if (bounds.size() == 1) {
                typeVariables.get(i).upperBound()
                    .ifPresent(upper -> addSourceLocation(cut, bounds.get(0), upper));
            }
        }
    }

    private void processMembers(final JDKTypeDescriptor typeDescriptor,
                                final TreePath classPath,
                                final List<Runnable> bodyTasks) {
        final var cut = classPath.getCompilationUnit();
        final var classTree = (ClassTree) classPath.getLeaf();
        final var srcPositions = trees.getSourcePositions();
        final var sortedMembers = classTree.getMembers().stream()
            .sorted(java.util.Comparator.comparingLong(m -> srcPositions.getStartPosition(cut, m)))
            .toList();
        int enumConstantOrder = 0;
        int memberOrder = 0;
        final var writtenMethods = new HashSet<ExecutableElement>();
        for (final var member : sortedMembers) {
            final var path = new TreePath(classPath, member);
            final var elem = trees.getElement(path);
            if (member instanceof VariableTree vt && elem instanceof VariableElement ve) {
                if (ve.getKind() == ElementKind.FIELD) {
                    processField(typeDescriptor, ve, vt, cut, bodyTasks, memberOrder++);
                } else if (ve.getKind() == ElementKind.ENUM_CONSTANT) {
                    final var name = nameProvider.getIrreducibleName(ve.getSimpleName());
                    final var enumConstantDescriptor = EnumConstantDescriptor.of(codeModel, name, enumConstantOrder++);
                    bodyTasks.add(() -> enumConstantDescriptor.addTrait(new FieldInitializerDescriptor(exprConverter.convert(vt.getInitializer()))));
                    addSourceLocation(cut, vt, enumConstantDescriptor);
                    typeDescriptor.addTrait(enumConstantDescriptor);
                }
            } else if (member instanceof MethodTree mt && elem instanceof ExecutableElement ee) {
                if (ee.getKind() == ElementKind.CONSTRUCTOR) {
                    processConstructor(typeDescriptor, ee, mt, cut, bodyTasks, memberOrder++);
                } else if (ee.getKind() == ElementKind.METHOD) {
                    writtenMethods.add(ee);
                    processMethod(typeDescriptor, ee, mt, cut, bodyTasks, memberOrder++);
                }
            } else if (member instanceof BlockTree bt) {
                bodyTasks.add(() -> {
                    final var initializerBlockDescriptor =
                        new InitializerBlockDescriptor(codeModel, bt.isStatic(), stmtConverter.convertStatements(bt.getStatements()));
                    addSourceLocation(cut, bt, initializerBlockDescriptor);
                    typeDescriptor.addTrait(initializerBlockDescriptor);
                });
            }
        }

        if (classTree.getKind() == Tree.Kind.RECORD || classTree.getKind() == Tree.Kind.ENUM) {
            processImplicitMethods(typeDescriptor, classPath, writtenMethods, memberOrder);
        }
    }

    /**
     * Some compiler-synthesized methods are added directly onto the {@link TypeElement} without
     * ever gaining a corresponding {@link MethodTree} — unlike the canonical constructor, which
     * does get a synthetic tree. This is true of a record's component accessors and its
     * overridden {@code toString}/{@code equals}/{@code hashCode}, and of an enum's
     * {@code values()}/{@code valueOf(String)}. {@link #processMembers} only walks the
     * {@link ClassTree}, so these methods are otherwise never modeled at all. This fills them in
     * from {@code Elements} directly, with no source location or body since none was ever written.
     */
    private void processImplicitMethods(final JDKTypeDescriptor typeDescriptor,
                                        final TreePath classPath,
                                        final Set<ExecutableElement> writtenMethods,
                                        final int startOrder) {
        if (!(trees.getElement(classPath) instanceof TypeElement typeElement)) {
            return;
        }
        var memberOrder = startOrder;
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD
                && enclosed instanceof ExecutableElement ee
                && !writtenMethods.contains(ee)) {
                processImplicitMethod(typeDescriptor, ee, memberOrder++);
            }
        }
    }

    private void processImplicitMethod(final JDKTypeDescriptor typeDescriptor,
                                       final ExecutableElement methodElement,
                                       final int order) {
        final var returnType = resolver.resolve(methodElement.getReturnType(), methodElement);
        final var methodName = resolver.methodName(typeDescriptor, methodElement);
        final var formalParameters = resolver.getFormalParameters(methodElement, (_, _) -> {
        });

        final var methodDescriptor = MethodDescriptor.of(typeDescriptor, methodName, returnType, formalParameters);
        resolver.modifyMethod(methodDescriptor, methodElement);

        methodDescriptor.addTrait(new DeclarationOrder(order));
        typeDescriptor.addTrait(methodDescriptor);
    }

    private void processField(final JDKTypeDescriptor typeDescriptor,
                              final VariableElement fieldElement,
                              final VariableTree varTree,
                              final CompilationUnitTree cut,
                              final List<Runnable> bodyTasks,
                              final int order) {
        final var fieldDescriptor = resolver.createFieldDescriptor(fieldElement);
        addSourceLocation(cut, varTree, fieldDescriptor);
        if (varTree.getType() != null) {
            addSourceLocation(cut, varTree.getType(), fieldDescriptor.type());
        }

        fieldDescriptor.addTrait(new DeclarationOrder(order));
        typeDescriptor.addTrait(fieldDescriptor);

        if (varTree.getInitializer() != null) {
            bodyTasks.add(() -> fieldDescriptor.addTrait(
                new FieldInitializerDescriptor(exprConverter.convert(varTree.getInitializer()))));
        }
    }

    private void processConstructor(final JDKTypeDescriptor typeDescriptor,
                                    final ExecutableElement methodElement,
                                    final MethodTree ctorTree,
                                    final CompilationUnitTree cut,
                                    final List<Runnable> bodyTasks,
                                    final int order) {
        final var formalParameters = getFormalParameters(methodElement, ctorTree, cut);

        final var constructorDescriptor = ConstructorDescriptor.of(typeDescriptor, formalParameters);
        resolver.modifyConstructor(constructorDescriptor, methodElement);

        addSourceLocation(cut, ctorTree, constructorDescriptor);
        addThrowsSourceLocations(constructorDescriptor, ctorTree.getThrows(), cut);
        addTypeParameterBoundSourceLocations(constructorDescriptor, ctorTree.getTypeParameters(), cut);

        constructorDescriptor.addTrait(new DeclarationOrder(order));
        typeDescriptor.addTrait(constructorDescriptor);

        if (ctorTree.getBody() != null) {
            final var srcPositions = trees.getSourcePositions();
            final var bodyStart = srcPositions.getStartPosition(cut, ctorTree.getBody());
            final var realStmts = ctorTree.getBody().getStatements().stream()
                .filter(stmt -> {
                    final var pos = srcPositions.getStartPosition(cut, stmt);
                    return pos != Diagnostic.NOPOS && pos > bodyStart;
                })
                .toList();
            bodyTasks.add(() -> constructorDescriptor.addTrait(
                new MethodBodyDescriptor(stmtConverter.convertStatements(realStmts))));
        }
    }

    private Stream<FormalParameterDescriptor> getFormalParameters(final ExecutableElement methodElement,
                                                                  final MethodTree methodTree,
                                                                  final CompilationUnitTree cut) {
        final var paramTrees = methodTree.getParameters();
        final var index = new AtomicInteger();
        return resolver.getFormalParameters(methodElement, (_, pd) -> {
            final var i = index.getAndIncrement();
            if (i < paramTrees.size()) {
                addSourceLocation(cut, paramTrees.get(i), pd);
                if (paramTrees.get(i).getType() != null) {
                    addSourceLocation(cut, paramTrees.get(i).getType(), pd.type());
                }
            }
        });
    }

    private void processMethod(final JDKTypeDescriptor typeDescriptor,
                               final ExecutableElement methodElement,
                               final MethodTree methodTree,
                               final CompilationUnitTree cut,
                               final List<Runnable> bodyTasks,
                               final int order) {
        final var returnType = resolver.resolve(methodElement.getReturnType(), methodElement);
        final var methodName = resolver.methodName(typeDescriptor, methodElement);

        final var formalParameters = getFormalParameters(methodElement, methodTree, cut);

        final var methodDescriptor = MethodDescriptor.of(typeDescriptor, methodName, returnType, formalParameters);
        resolver.modifyMethod(methodDescriptor, methodElement);

        addSourceLocation(cut, methodTree, methodDescriptor);
        if (methodTree.getReturnType() != null) {
            addSourceLocation(cut, methodTree.getReturnType(), returnType);
        }
        addThrowsSourceLocations(methodDescriptor, methodTree.getThrows(), cut);
        addTypeParameterBoundSourceLocations(methodDescriptor, methodTree.getTypeParameters(), cut);

        methodDescriptor.addTrait(new DeclarationOrder(order));
        typeDescriptor.addTrait(methodDescriptor);

        if (methodTree.getBody() != null) {
            bodyTasks.add(() -> methodDescriptor.addTrait(
                new MethodBodyDescriptor(stmtConverter.convertBlock(methodTree.getBody()))));
        }
    }

    /**
     * A synthesized tree node can carry a real {@code start} with no {@code end} at all
     * ({@link Diagnostic#NOPOS}) — it was never actually written, so it has no source extent.
     * Skipping those avoids attaching a misleading position.
     */
    private void addSourceLocation(final CompilationUnitTree cut,
                                   final Tree tree,
                                   final Traitable traitable) {
        final var srcPositions = trees.getSourcePositions();
        final var start = srcPositions.getStartPosition(cut, tree);
        final var end = srcPositions.getEndPosition(cut, tree);
        if (start != Diagnostic.NOPOS && end != Diagnostic.NOPOS) {
            traitable.addTrait(SourceLocation.filePosition(cut.getSourceFile().toUri(), start, end));
        }
    }

    /**
     * Populates a {@link JDKModuleDescriptor}'s traits from a javac {@link ModuleTree}.
     *
     * @param descriptor                 the {@link JDKModuleDescriptor} to populate
     * @param moduleTree                 the {@link ModuleTree} produced by javac
     * @param cut                        the enclosing {@link CompilationUnitTree}, used to derive each
     *                                   directive's {@link SourceLocation.FilePosition}
     * @param annotationTypeNameResolver resolves an annotation's {@link AnnotationTree} to the
     *                                   module-qualified {@link TypeName} of its declaring type,
     *                                   matching how that type's descriptor was registered
     */
    private void populateModuleDescriptor(final JDKModuleDescriptor descriptor,
                                          final ModuleTree moduleTree,
                                          final CompilationUnitTree cut,
                                          final Function<AnnotationTree, TypeName> annotationTypeNameResolver) {

        if (moduleTree.getModuleType() == ModuleTree.ModuleKind.OPEN) {
            descriptor.addTrait(OpenModule.OPEN);
        }

        for (final AnnotationTree ann : moduleTree.getAnnotations()) {
            descriptor.addTrait(AnnotationTypeUsage.of(descriptor.codeModel(),
                annotationTypeNameResolver.apply(ann),
                Stream.empty()));
        }

        for (final var directive : moduleTree.getDirectives()) {
            switch (directive.getKind()) {
                case REQUIRES -> {
                    final var req = (RequiresTree) directive;
                    descriptor.addRequires(req.getModuleName().toString(), req.isTransitive(), req.isStatic(),
                            false, false, Optional.empty())
                        .ifPresent(reqDescriptor -> addSourceLocation(cut, directive, reqDescriptor));
                }
                case EXPORTS -> {
                    final var exp = (ExportsTree) directive;
                    descriptor.addExports(exp.getPackageName().toString(),
                            exp.getModuleNames() == null
                                ? Stream.empty()
                                : exp.getModuleNames().stream().map(Object::toString),
                            Optional.empty())
                        .ifPresent(expDescriptor -> addSourceLocation(cut, directive, expDescriptor));
                }
                case OPENS -> {
                    final var op = (OpensTree) directive;
                    descriptor.addOpens(op.getPackageName().toString(),
                            op.getModuleNames() == null
                                ? Stream.empty()
                                : op.getModuleNames().stream().map(Object::toString),
                            Optional.empty())
                        .ifPresent(opDescriptor -> addSourceLocation(cut, directive, opDescriptor));
                }
                case PROVIDES -> {
                    final var prov = (ProvidesTree) directive;
                    final var provDescriptor = descriptor.addProvides(prov.getServiceName().toString(),
                        prov.getImplementationNames().stream().map(Object::toString));
                    addSourceLocation(cut, directive, provDescriptor);
                }
                case USES -> {
                    final var uses = (UsesTree) directive;
                    final var usesDescriptor = descriptor.addUses(uses.getServiceName().toString());
                    addSourceLocation(cut, directive, usesDescriptor);
                }
                default -> { /* version directive — not modelled */ }
            }
        }
    }
}
