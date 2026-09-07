package build.codemodel.jdk.annotation.processor;

/*-
 * #%L
 * JDK Annotation Processor
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

import build.base.foundation.Capture;
import build.base.foundation.Lazy;
import build.base.telemetry.Error;
import build.base.telemetry.TelemetryRecorder;
import build.base.telemetry.foundation.ObservableTelemetryRecorder;
import build.base.telemetry.javac.MessagerBasedTelemetryRecorder;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.PolymorphicNamespaceDescriptor;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.CachingNameProvider;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.SpecificTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.framework.Framework;
import build.codemodel.framework.Plugin;
import build.codemodel.framework.builder.FrameworkBuilder;
import build.codemodel.framework.compiler.Compilation;
import build.codemodel.framework.compiler.Compiler;
import build.codemodel.framework.compiler.TypeChecker;
import build.codemodel.framework.completer.Completer;
import build.codemodel.framework.initialization.Enricher;
import build.codemodel.framework.initialization.Initializer;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.TypeUsages;
import build.codemodel.jdk.annotation.discovery.AnnotationDiscovery;
import build.codemodel.jdk.annotation.discovery.Discoverable;
import build.codemodel.jdk.descriptor.EnumConstantDescriptor;
import build.codemodel.jdk.descriptor.JDKTypeDescriptor;
import build.codemodel.jdk.populator.TypeMirrorResolver;
import build.codemodel.jdk.populator.descriptor.SourceLocation;
import build.codemodel.objectoriented.descriptor.ConstructorDescriptor;
import build.codemodel.objectoriented.descriptor.DeclarationOrder;
import build.codemodel.objectoriented.descriptor.ExtendsTypeDescriptor;
import build.codemodel.objectoriented.descriptor.FieldDescriptor;
import build.codemodel.objectoriented.descriptor.ImplementsTypeDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import com.sun.source.util.Trees;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * A {@link Processor} to discover, reverse-engineer and process discoverable annotations from source and byte code.
 *
 * @author brian.oliver
 * @see AnnotationDiscovery
 * @see Discoverable
 * @since Jan-2024
 */
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class AnnotationProcessor
    extends AbstractProcessor {

    /**
     * The {@link Processor} option defining a regular expression to exclude types.
     */
    private final String EXCLUDED_TYPES_PATTERN = "codemodel.excluded.types.pattern";

    /**
     * The {@link AnnotationDiscovery} implementations to use for discovering types to reverse-engineer.
     */
    private final ArrayList<AnnotationDiscovery> annotationDiscoveries;

    /**
     * The {@link Lazy} initialized {@link TypeMirrorResolver} for resolving {@link javax.lang.model.type.TypeMirror}s.
     */
    private final Lazy<TypeMirrorResolver> lazyResolver;

    /**
     * The {@link Lazy} initialized {@link TelemetryRecorder} for building {@link CodeModel}s.
     */
    private final Lazy<ObservableTelemetryRecorder> lazyTelemetryRecorder;

    /**
     * The {@link Lazy} initialized {@link Framework} for building {@link CodeModel}s.
     */
    private final Lazy<Framework> lazyFramework;

    /**
     * The {@link Lazy} initialized {@link CodeModel} into which to reverse-engineer the compiler code model.
     */
    private final Lazy<CodeModel> lazyCodeModel;

    /**
     * The {@link Capture}d {@link Compilation}, available when a {@link CodeModel} has been successfully compiled.
     */
    private final Capture<Compilation> capturedCompilation;

    /**
     * The {@link Lazy}ily initialized {@link Predicate} for excluding type to discover/reverse-engineer.
     */
    private final Lazy<Predicate<String>> lazyExcludedTypesPredicate;

    /**
     * Constructs a {@link AnnotationProcessor}.
     */
    public AnnotationProcessor() {
        super();

        this.annotationDiscoveries = new ArrayList<>();

        this.lazyTelemetryRecorder = Lazy.empty();
        this.lazyFramework = Lazy.empty();
        this.lazyCodeModel = Lazy.empty();
        this.capturedCompilation = Capture.empty();
        this.lazyExcludedTypesPredicate = Lazy.empty();
        this.lazyResolver = Lazy.empty();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return this.annotationDiscoveries.stream()
            .flatMap(AnnotationDiscovery::getDiscoverableAnnotationTypes)
            .map(Class::getCanonicalName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(EXCLUDED_TYPES_PATTERN);
    }

    @Override
    public synchronized void init(final ProcessingEnvironment processing) {
        super.init(processing);

        final var messager = processing.getMessager();
        messager.printNote("Initializing " + this);

        // --------
        // establish a FrameworkBuilder to build a Framework suitable for Annotation Processing
        final var builder = new FrameworkBuilder();

        builder.withFileSystem(FileSystems::getDefault);
        builder.withNameProvider(() -> new CachingNameProvider(new NonCachingNameProvider()));

        builder.bind(Messager.class).to(messager);
        builder.bind(Filer.class).to(processing.getFiler());

        // use the ClassLoader of this AnnotationProcessor for loading Services from the Compiler-tooling Module/ClassPath!
        // (using the default ClassLoader will not find the Annotation-based Services!)
        final var serviceLoaderClassLoader = AnnotationProcessor.class.getClassLoader();
        builder.withPlugins(ServiceLoader.load(Plugin.class, serviceLoaderClassLoader));

        // --------
        // establish the TelemetryRecorder for the Framework
        final var telemetryRecorder = ObservableTelemetryRecorder.of(MessagerBasedTelemetryRecorder.of(messager));
        this.lazyTelemetryRecorder.set(telemetryRecorder);

        // --------
        // determine the excluded type filer
        try {
            Optional.ofNullable(processing.getOptions().get(EXCLUDED_TYPES_PATTERN))
                .map(Pattern::compile)
                .map(Pattern::asPredicate)
                .map(this.lazyExcludedTypesPredicate::set);
        } catch (final PatternSyntaxException e) {
            telemetryRecorder.warn(
                "The Code Model AnnotationProcessor option %s cannot be parsed as a regular expression", e);
        }

        // --------
        // establish the Framework to use for creating and compiling CodeModels
        final var framework = builder.build();
        this.lazyFramework.set(framework);

        messager.printNote("Loaded " + framework.plugins().count() + " Framework Plugin(s)");
        messager.printNote("Loaded " + framework
            .plugins(Initializer.class)
            .count() + " Initializer(s)");
        messager.printNote("Loaded " + framework
            .plugins(Enricher.class)
            .count() + " Enrichers(s)");
        messager.printNote("Loaded " + framework.plugins(TypeChecker.class).count() + " TypeChecker(s)");
        messager.printNote("Loaded " + framework.plugins(Compiler.class).count() + " Compiler(s)");
        messager.printNote("Loaded " + framework.plugins(Completer.class).count() + " Completer(s)");

        // --------
        // bootstrap the AnnotationDiscovery (using a ServiceLoader)
        final var bootstrapNameProvider = new NonCachingNameProvider();
        final var bootstrapCodeModel = new JDKCodeModel(bootstrapNameProvider);

        final var bootstrapInjectionFramework = new InjectionFramework(bootstrapCodeModel);
        final var bootstrapContext = bootstrapInjectionFramework.newContext();

        bootstrapContext.bind(FileSystem.class).to(framework.fileSystem());
        bootstrapContext.bind(NameProvider.class).to(framework.nameProvider());

        ServiceLoader.load(AnnotationDiscovery.class, serviceLoaderClassLoader).stream()
            .map(ServiceLoader.Provider::get)
            .peek(annotationDiscovery -> messager
                .printNote("Loaded Annotation Discovery: " + annotationDiscovery.getClass().getSimpleName()))
            .peek(bootstrapContext::inject)
            .forEach(this.annotationDiscoveries::add);

        messager.printNote("Loaded " + this.annotationDiscoveries.size() + " AnnotationDiscovery(s)");
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations,
                           final RoundEnvironment round) {

        final var processing = this.processingEnv;
        final var messager = processing.getMessager();
        final var framework = this.lazyFramework.get();
        final var telemetryRecorder = this.lazyTelemetryRecorder.get();

        // obtain the CodeModel, or compute a new one
        final var codeModel = this.lazyCodeModel.computeIfAbsent(() -> {
            final var newCodeModel = framework.newCodeModel(JDKCodeModel.class);
            messager.printNote(
                "Created CodeModel with " + newCodeModel.typeDescriptors().count() + " TypeDescriptor(s)");
            return newCodeModel;
        }).orElseThrow();

        if (!annotations.isEmpty()) {
            // perform annotation processing

            // --------
            // STAGE 1: Discover TypeDescriptors for the discoverable models
            messager.printNote("Stage 1: Discovering and Building Code Model");

            // establish a LinkedHashMap-based queue of pending TypeElements to be processed
            // (these may or may not be annotated, but we start with @Discoverable annotated ones!)
            final var pending = new LinkedHashMap<TypeName, TypeElement>();
            annotations.stream()
                .flatMap(annotation -> round.getElementsAnnotatedWith(annotation).stream())
                .filter(element -> element.getKind().isDeclaredType())
                .filter(TypeElement.class::isInstance)
                .map(TypeElement.class::cast)
                .forEach(typeElement -> {
                    final var typeName = resolver().resolveTypeName(typeElement);
                    include(codeModel, typeName, typeElement, pending);
                });

            while (!pending.isEmpty()) {
                // obtain the first entry for processing (but don't remove it)
                // ( we want to prevent recursive processing for recursively defined types)
                final var entry = pending.firstEntry();
                final var typeName = entry.getKey();
                final var typeElement = entry.getValue();

                createTypeDescriptor(codeModel, typeName, typeElement, pending);

                // now remove it!
                pending.remove(typeName);
            }

            // enrich the CodeModel
            framework.enrich(codeModel, telemetryRecorder);

            // attempt to type-check the CodeModel
            framework.typeCheck(codeModel, telemetryRecorder)
                .ifPresent(__ -> {
                    // dump out the TypeDescriptors
                    codeModel.typeDescriptors()
                        .forEach(typeDescriptor -> messager.printNote("TypeDescriptor: "
                            + typeDescriptor.typeName()
                            + (typeDescriptor.traits(ExtendsTypeDescriptor.class).findFirst().isPresent()
                            ? " extends " + typeDescriptor.traits(ExtendsTypeDescriptor.class)
                            .map(ExtendsTypeDescriptor::parentTypeUsage)
                            .map(Objects::toString)
                            .collect(Collectors.joining(", "))
                            : "")
                            + (typeDescriptor.traits(ImplementsTypeDescriptor.class).findFirst().isPresent()
                            ? " implements " + typeDescriptor.traits(ImplementsTypeDescriptor.class)
                            .map(ImplementsTypeDescriptor::parentTypeUsage)
                            .map(Objects::toString)
                            .collect(Collectors.joining(", "))
                            : "")
                            + (typeDescriptor.traits().findFirst().isPresent()
                            ? " Traits["
                              + typeDescriptor.traits().map(Object::getClass).map(Class::getSimpleName)
                            .collect(Collectors.joining(", ")) + "]"
                            : "")));

                    // --------
                    // STAGE 3: Compile the Code Model
                    messager.printNote("Stage 2: Compiling Code Model");
                    framework.compile(codeModel, telemetryRecorder)
                        .ifPresentOrElse(this.capturedCompilation::set, this.capturedCompilation::clear);
                });
        } else {
            // --------
            // STAGE 4: Complete Processing (the last round without any Annotations to Process)

            // perform finishing if there were no errors reported
            if (!telemetryRecorder.hasObserved(Error.class) && this.capturedCompilation.isPresent()) {

                this.capturedCompilation.ifPresent(compilation -> {
                    messager.printNote("Stage 3: Completing Compilation");
                    framework.complete(compilation, telemetryRecorder);
                });
            }
        }

        return false;
    }

    /**
     * Includes the specified {@link TypeUsage} for discovery should it not already discovered or pending discovery.
     *
     * @param codeModel        the {@link CodeModel}
     * @param typeUsage        the {@link TypeUsage}
     * @param enclosingElement the {@link Element} in which the {@link TypeUsage} is defined
     * @param pending          the pending {@link TypeElement}s to process
     */
    private void include(final CodeModel codeModel,
                         final TypeUsage typeUsage,
                         final Element enclosingElement,
                         final LinkedHashMap<TypeName, TypeElement> pending) {

        // only SpecificTypeUsages are discoverable
        if (typeUsage instanceof SpecificTypeUsage specificTypeUsage) {
            include(codeModel, specificTypeUsage.typeName(), enclosingElement, pending);
        }
    }

    /**
     * Includes the specified {@link TypeName} for discovery should it not already discovered or pending discovery.
     *
     * @param codeModel        the {@link CodeModel}
     * @param typeName         the {@link TypeName}
     * @param enclosingElement the {@link Element} in which the {@link TypeUsage} is defined
     * @param pending          the pending {@link TypeElement}s to process
     */
    private void include(final CodeModel codeModel,
                         final TypeName typeName,
                         final Element enclosingElement,
                         final LinkedHashMap<TypeName, TypeElement> pending) {

        // only include the TypeName when it's not excluded
        if (this.lazyExcludedTypesPredicate
            .map(predicate -> !predicate.test(typeName.canonicalName()))
            .orElse(true)) {

            // we only need to discover the Type when we don't already have a TypeDescriptor
            // and the type name is not already pending processing
            {
                if (codeModel.getTypeDescriptor(typeName).isEmpty() &&
                    !pending.containsKey(typeName)) {

                    final var processing = this.processingEnv;
                    final var messager = processing.getMessager();

                    // attempt to determine the TypeElement for the required type
                    final var typeElement = processing.getElementUtils().getTypeElement(typeName.canonicalName());

                    if (typeElement == null) {
                        // warn when the type is not primitive
                        if (!TypeUsages.isPrimitive(typeName)) {
                            messager.printWarning("Failed to determine TypeElement for " + typeName.canonicalName(),
                                enclosingElement);
                        }
                    } else {
                        pending.putLast(typeName, typeElement);
                    }
                }
            }
        }
    }

    private TypeMirrorResolver resolver() {
        return this.lazyResolver.computeIfAbsent(() -> {
            final var codeModel = this.lazyCodeModel.get();
            final var recorder = this.lazyTelemetryRecorder.get();
            return new TypeMirrorResolver(
                codeModel,
                processingEnv.getElementUtils(),
                Trees.instance(processingEnv),
                errorType -> recorder.error(
                    SourceLocation.elementRef(errorType.asElement()),
                    "Missing or undefined type " + errorType.asElement().getSimpleName()));
        }).orElseThrow();
    }

    /**
     * Creates a {@link FieldDescriptor} for the {@link TypeDescriptor} based on an {@link ExecutableElement}.
     *
     * @param codeModel      the {@link CodeModel}
     * @param typeDescriptor the {@link TypeDescriptor} in which to define the {@link FieldDescriptor}
     * @param fieldElement   the {@link TypeElement} for the <i>field</i>
     * @param pending        the pending {@link TypeElement}s to be processed
     * @return a {@link FieldDescriptor}
     */
    private FieldDescriptor createFieldDescriptor(final CodeModel codeModel,
                                                  final TypeDescriptor typeDescriptor,
                                                  final VariableElement fieldElement,
                                                  final LinkedHashMap<TypeName, TypeElement> pending,
                                                  final int order) {
        final var fieldDescriptor = resolver().createFieldDescriptor(fieldElement);
        fieldDescriptor.addTrait(SourceLocation.elementRef(fieldElement));
        fieldDescriptor.addTrait(new DeclarationOrder(order));

        typeDescriptor.addTrait(fieldDescriptor);

        // ensure the discovery of types used by the FieldDescriptor
        fieldDescriptor.parts(TypeUsage.class)
            .forEach(typeUsage -> include(codeModel, typeUsage, fieldElement, pending));

        return fieldDescriptor;
    }

    /**
     * Creates an {@link EnumConstantDescriptor} for the {@link TypeDescriptor} based on a
     * {@link VariableElement} of kind {@code ENUM_CONSTANT}.
     *
     * @param codeModel       the {@link CodeModel}
     * @param typeDescriptor  the {@link TypeDescriptor} in which to define the {@link EnumConstantDescriptor}
     * @param constantElement the {@link VariableElement} for the enum constant
     * @param order           the enum constant's ordinal position among its siblings
     * @return an {@link EnumConstantDescriptor}
     */
    private EnumConstantDescriptor createEnumConstantDescriptor(final CodeModel codeModel,
                                                                final TypeDescriptor typeDescriptor,
                                                                final VariableElement constantElement,
                                                                final int order) {
        final var name = codeModel.getNameProvider().getIrreducibleName(constantElement.getSimpleName());
        final var enumConstantDescriptor = EnumConstantDescriptor.of(codeModel, name, order);
        enumConstantDescriptor.addTrait(SourceLocation.elementRef(constantElement));

        typeDescriptor.addTrait(enumConstantDescriptor);

        return enumConstantDescriptor;
    }

    /**
     * Creates a {@link ConstructorDescriptor} for the {@link TypeDescriptor} based on an {@link ExecutableElement}.
     *
     * @param codeModel          the {@link CodeModel}
     * @param typeDescriptor     the {@link TypeDescriptor} in which to define the {@link ConstructorDescriptor}
     * @param constructorElement the {@link ExecutableElement} for the <i>constructor</i>
     * @param pending            the pending {@link TypeElement}s to be processed
     * @return a {@link ConstructorDescriptor}
     */
    private ConstructorDescriptor createConstructorDescriptor(final CodeModel codeModel,
                                                              final JDKTypeDescriptor typeDescriptor,
                                                              final ExecutableElement constructorElement,
                                                              final LinkedHashMap<TypeName, TypeElement> pending,
                                                              final int order) {
        final var formalParameters = resolver().getFormalParameters(constructorElement,
            (variable, pd) -> pd.addTrait(SourceLocation.elementRef(variable)));

        final var constructorDescriptor = ConstructorDescriptor.of(typeDescriptor, formalParameters);
        resolver().modifyConstructor(constructorDescriptor, constructorElement);

        constructorDescriptor.addTrait(SourceLocation.elementRef(constructorElement));
        constructorDescriptor.addTrait(new DeclarationOrder(order));

        typeDescriptor.addTrait(constructorDescriptor);

        constructorDescriptor.parts(TypeUsage.class)
            .forEach(typeUsage -> include(codeModel, typeUsage, constructorElement, pending));

        return constructorDescriptor;
    }

    /**
     * Creates a {@link MethodDescriptor} for the {@link TypeDescriptor} based on an {@link ExecutableElement}.
     *
     * @param codeModel      the {@link CodeModel}
     * @param typeDescriptor the {@link TypeDescriptor} in which the {@link MethodDescriptor} is being defined
     * @param methodElement  the {@link ExecutableElement} for the <i>constructor</i>
     * @param pending        the pending {@link TypeElement}s to be processed
     * @return a {@link MethodDescriptor}
     */
    private MethodDescriptor createMethodDescriptor(final CodeModel codeModel,
                                                    final JDKTypeDescriptor typeDescriptor,
                                                    final ExecutableElement methodElement,
                                                    final LinkedHashMap<TypeName, TypeElement> pending,
                                                    final int order) {
        final var returnType = resolver().resolve(methodElement.getReturnType(), methodElement);
        final var methodName = resolver().methodName(typeDescriptor, methodElement);

        final var formalParameters = resolver().getFormalParameters(methodElement,
            (variable, pd) -> pd.addTrait(SourceLocation.elementRef(variable)));

        final var methodDescriptor = MethodDescriptor.of(typeDescriptor, methodName, returnType, formalParameters);
        resolver().modifyMethod(methodDescriptor, methodElement);

        methodDescriptor.addTrait(SourceLocation.elementRef(methodElement));
        methodDescriptor.addTrait(new DeclarationOrder(order));


        // include the MethodDescriptor in the TypeDescriptor
        typeDescriptor.addTrait(methodDescriptor);

        // ensure the discovery of types used by the MethodPrototype
        methodDescriptor.parts(TypeUsage.class)
            .forEach(typeUsage -> include(codeModel, typeUsage, methodElement, pending));

        return methodDescriptor;
    }

    /**
     * Creates a {@link TypeDescriptor} using the {@link CodeModel} based on a {@link TypeElement}.
     *
     * @param codeModel   the {@link CodeModel}
     * @param typeName    the {@link TypeName}
     * @param typeElement the {@link TypeElement}
     * @param pending     the pending {@link TypeElement}s by {@link TypeName}s to process
     * @return a {@link TypeDescriptor}
     */
    private TypeDescriptor createTypeDescriptor(final CodeModel codeModel,
                                                final TypeName typeName,
                                                final TypeElement typeElement,
                                                final LinkedHashMap<TypeName, TypeElement> pending) {

        try {
            final var processing = this.processingEnv;
            final var messager = processing.getMessager();

            // attempt to find the existing TypePrototype for the TypeName
            final var existing = codeModel.getTypeDescriptor(typeName).orElse(null);
            if (existing != null) {
                return existing;
            }

            final var typeDescriptor = resolver().buildTypeDescriptor(typeName, typeElement);

            typeDescriptor.addTrait(SourceLocation.elementRef(typeElement));

            discoverPackageNamespace(codeModel, typeElement);

            messager.printNote("Discovered TypeName [" + typeName + "]", typeElement);

            // discover the FieldDescriptors, ConstructorDescriptors, and MethodDescriptors, sharing a single
            // DeclarationOrder counter across all three kinds (assigned in enclosedElements order, i.e. source
            // declaration order) so the trait reflects each member's position among all members, not just
            // among its own kind
            final var memberOrder = new AtomicInteger();
            final var enumConstantOrder = new AtomicInteger();
            for (final var enclosing : typeElement.getEnclosedElements()) {
                switch (enclosing.getKind()) {
                    case FIELD -> createFieldDescriptor(
                        codeModel, typeDescriptor, (VariableElement) enclosing, pending,
                        memberOrder.getAndIncrement());
                    case ENUM_CONSTANT -> createEnumConstantDescriptor(
                        codeModel, typeDescriptor, (VariableElement) enclosing,
                        enumConstantOrder.getAndIncrement());
                    case CONSTRUCTOR -> createConstructorDescriptor(
                        codeModel, typeDescriptor, (ExecutableElement) enclosing, pending,
                        memberOrder.getAndIncrement());
                    case METHOD -> createMethodDescriptor(
                        codeModel, typeDescriptor, (ExecutableElement) enclosing, pending,
                        memberOrder.getAndIncrement());
                    default -> {
                    }
                }
            }

            // ensure the discovery of types used by the TypePrototype
            typeDescriptor.composition(TypeUsage.class)
                .forEach(typeUsage -> include(codeModel, typeUsage, typeElement, pending));

            return typeDescriptor;
        } catch (final StackOverflowError e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Registers a {@link build.codemodel.foundation.descriptor.NamespaceDescriptor} for the package
     * enclosing a discovered {@link TypeElement} when that package's declaration
     * ({@code package-info.java}) carries annotations, attaching them via the same
     * annotation-resolution machinery used for type and member annotations. Mirrors
     * {@code JdkInitializer.processPackageAnnotations} on the javac-source path.
     *
     * <p>Only an annotated package produces a descriptor, so a package with no
     * {@code package-info.java} (or one without annotations) is left alone. Creation uses
     * {@code computeIfAbsent} semantics: the annotations are applied once, when the descriptor is
     * first created for the {@link build.codemodel.foundation.naming.Namespace}.
     */
    private void discoverPackageNamespace(final CodeModel codeModel, final TypeElement typeElement) {
        final PackageElement packageElement = this.processingEnv.getElementUtils().getPackageOf(typeElement);
        if (packageElement == null || packageElement.getAnnotationMirrors().isEmpty()) {
            return;
        }
        final var namespace = codeModel.getNameProvider()
            .getNamespace(packageElement.getQualifiedName().toString());
        if (namespace.isEmpty()) {
            return;
        }
        codeModel.createNamespaceDescriptor(namespace.get(), PolymorphicNamespaceDescriptor::of, descriptor -> {
            resolver().addTypeAnnotations(descriptor, packageElement);
            SourceLocation.elementRefOrEmpty(packageElement).ifPresent(descriptor::addTrait);
        });
    }

    /**
     * Obtains the {@link Optional}ly initialized {@link CodeModel}.
     *
     * @return the {@link Optional} {@link CodeModel}
     */
    public Optional<CodeModel> getCodeModel() {
        return this.lazyCodeModel.optional();
    }

    /**
     * Obtains the {@link Lazy}ily initialized {@link Framework}.
     *
     * @return the {@link Lazy} {@link Framework}
     */
    public Lazy<Framework> getFramework() {
        return this.lazyFramework;
    }
}
