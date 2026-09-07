package build.codemodel.dependency.injection;

/*-
 * #%L
 * Dependency Injection
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

import build.base.foundation.stream.Streams;
import build.codemodel.foundation.descriptor.Traitable;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.descriptor.FieldType;
import build.codemodel.jdk.descriptor.JDKType;
import build.codemodel.jdk.descriptor.JDKTypeDescriptor;
import build.codemodel.jdk.descriptor.Static;
import build.codemodel.objectoriented.descriptor.Classification;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link JDKCodeModel}-based <a href="https://en.wikipedia.org/wiki/Adapter_pattern">Adapter</a> allowing the
 * runtime discovery of {@link InjectionPoint}s and <a href="https://jcp.org/en/jsr/detail?id=330">JSR-330</a>-compliant
 * <a href="https://en.wikipedia.org/wiki/Dependency_injection">Dependency Injection</a> of {@link Class}es
 * and {@link Object}s
 *
 * @author brian.oliver
 * @since Oct-2024
 */
public class InjectionFramework {

    /**
     * The {@link JDKCodeModel} to use to discover {@link InjectionPoint}s.
     */
    private final JDKCodeModel codeModel;

    /**
     * Registered custom scopes keyed by their scope annotation type.
     */
    private final ConcurrentHashMap<Class<? extends Annotation>, Scope> registeredScopes;

    /**
     * The {@link BindingGraphContributor} notified of every binding registration and dependency
     * resolution. Defaults to {@link BindingGraphContributor#NOOP}; replaced by Track 2 via
     * {@link #setBindingGraphContributor}.
     */
    private volatile BindingGraphContributor bindingGraphContributor = BindingGraphContributor.NOOP;

    /**
     * Constructs an {@link InjectionFramework}.
     *
     * @param codeModel the {@link JDKCodeModel}
     */
    public InjectionFramework(final JDKCodeModel codeModel) {
        this.codeModel = Objects
            .requireNonNull(codeModel, "The CodeModel must not be null");
        this.registeredScopes = new ConcurrentHashMap<>();
    }

    /**
     * Creates an {@link InjectionFramework} with a default non-caching {@link JDKCodeModel}.
     *
     * @return a new {@link InjectionFramework}
     */
    public static InjectionFramework create() {
        return new InjectionFramework(new JDKCodeModel(new NonCachingNameProvider()));
    }

    /**
     * Obtains the {@link JDKCodeModel} used by {@link InjectionFramework}.
     *
     * @return the {@link JDKCodeModel}
     */
    public JDKCodeModel codeModel() {
        return this.codeModel;
    }

    /**
     * Determines if the specified {@link JDKTypeDescriptor}, usually for an {@link Annotation}, is
     * annotated with {@link Qualifier}.
     *
     * @param annotationTypeDescriptor the {@link JDKTypeDescriptor}
     * @return {@code true} if the {@link JDKTypeDescriptor} has a {@link Qualifier} annotation, {@code false} otherwise
     */
    public boolean hasQualifierAnnotation(final JDKTypeDescriptor annotationTypeDescriptor) {
        return annotationTypeDescriptor != null
            && annotationTypeDescriptor.traits(AnnotationTypeUsage.class)
            .anyMatch(metaAnnotationTypeUsage -> metaAnnotationTypeUsage
                .typeName()
                .canonicalName()
                .equals(Qualifier.class.getCanonicalName()));
    }

    /**
     * Determines the {@link AnnotationTypeUsage}s of the specified {@link Traitable} that have the
     * {@link Qualifier} meta-annotation.
     *
     * @param traitable {@link Traitable}
     * @return the {@link Stream} of {@link Qualifier} {@link AnnotationTypeUsage}s
     */
    public Stream<AnnotationTypeUsage> getQualifierAnnotationTypes(final Traitable traitable) {
        if (traitable == null) {
            return Stream.empty();
        }

        return traitable.traits(AnnotationTypeUsage.class)
            .map(annotationTypeUsage -> this.codeModel.getJDKTypeDescriptor(annotationTypeUsage.typeName())
                .filter(this::hasQualifierAnnotation)
                .map(_ -> annotationTypeUsage)
                .orElse(null))
            .filter(Objects::nonNull);
    }

    /**
     * Creates an {@link InjectableDescriptor} for the specified injectable {@link Class} and
     * {@link JDKTypeDescriptor}, without modifying the {@link JDKTypeDescriptor} or adding the
     * {@link InjectableDescriptor} to it.
     *
     * @param injectableClass the injectable {@link Class}
     * @param typeDescriptor  the {@link JDKTypeDescriptor}
     * @return the {@link InjectableDescriptor}
     */
    private InjectableDescriptor createInjectableDescriptor(final Class<?> injectableClass,
                                                            final JDKTypeDescriptor typeDescriptor) {

        // the InjectionPoints in order of their discovery
        final var injectionPoints = new LinkedHashMap<String, InjectionPoint>();

        // the @PostInject MethodDescriptors in order of their discovery
        final var postInjectMethodDescriptors = new LinkedHashSet<MethodDescriptor>();

        // create a deque of the hierarchy of Classes to process
        // (with the current class at the front of the deque)
        final var deque = new ArrayDeque<Class<?>>();

        var nextInQueue = injectableClass;
        while (nextInQueue != null && !nextInQueue.equals(Object.class)) {
            deque.push(nextInQueue);
            nextInQueue = nextInQueue.getSuperclass();
        }

        // detect the InjectionPoints in the Classes (in reverse order ie: "super classes down")
        // so that super-class Injection Points are discovered and ordered first
        while (!deque.isEmpty()) {
            // obtain the TypeDescriptor for the injectable class from the ReflectionFramework
            // (we don't use reflection)
            final var currentClass = deque.pop();

            final var currentTypeDescriptor = this.codeModel
                .getJDKTypeDescriptor(currentClass)
                .orElseThrow(
                    () -> new InjectionException("Failed to discover the InjectionPoints for " + injectableClass));

            // include the declared non-static, non-final fields annotated with @Inject
            // (that's not already a FieldInjectionPoint)
            currentTypeDescriptor.declaredFields()
                .filter(fieldDescriptor ->
                    isInjectionPoint(fieldDescriptor.type())
                        && fieldDescriptor.getTrait(Classification.class)
                        .map(classification -> classification == Classification.CONCRETE)
                        .orElse(false))
                .forEach(fieldDescriptor -> {
                    final var fieldType = fieldDescriptor.trait(FieldType.class);
                    final var name = fieldDescriptor.fieldName() + " " + fieldType.field().toGenericString();
                    final var fieldInjectionPoint = FieldInjectionPoint.of(
                        currentTypeDescriptor,
                        fieldDescriptor,
                        this::getQualifierAnnotationTypes);

                    injectionPoints.put(name, fieldInjectionPoint);
                });

            // include the declared non-abstract non-static Methods annotated with @Inject
            // (that's not already a MethodInjectionPoint)
            // (removing those that are overridden)
            currentTypeDescriptor.declaredMethods()
                .forEach(methodDescriptor -> {
                    final var overrideKey = methodDescriptor.overrideKey();

                    // remove any previous MethodInjectionPoint with the same overrideKey
                    // (as the current class is overriding it)
                    injectionPoints.remove(overrideKey);

                    if (isInjectionPoint(methodDescriptor)) {
                        final var methodInjectionPoint = MethodInjectionPoint
                            .of(currentTypeDescriptor, methodDescriptor, this::getQualifierAnnotationTypes);

                        injectionPoints.put(overrideKey, methodInjectionPoint);
                    }

                    // include the MethodDescriptor as a @PostInject
                    if (isPostInject(methodDescriptor)) {
                        postInjectMethodDescriptors.add(methodDescriptor);
                    }
                });
        }

        // include the ConstructorInjectionPoint (there should be only one)
        // determine the constructor annotated with @Inject
        // (that's not already a ConstructorInjectionPoint)
        typeDescriptor.declaredConstructors()
            .filter(this::isInjectionPoint)
            .findFirst()
            .ifPresent(constructorDescriptor -> {
                final var constructorInjectionPoint = ConstructorInjectionPoint
                    .of(typeDescriptor, constructorDescriptor, this::getQualifierAnnotationTypes);

                injectionPoints.put("constructor", constructorInjectionPoint);
            });

        // the @PreDestroy MethodDescriptors in order of their discovery
        final var preDestroyMethodDescriptors = new LinkedHashSet<MethodDescriptor>();

        // re-walk the hierarchy to discover @PreDestroy (done after injection-point discovery so
        // the same deque structure is reused conceptually; in practice we need a second pass here)
        var nextForDestroy = injectableClass;
        final var destroyDeque = new ArrayDeque<Class<?>>();
        while (nextForDestroy != null && !nextForDestroy.equals(Object.class)) {
            destroyDeque.push(nextForDestroy);
            nextForDestroy = nextForDestroy.getSuperclass();
        }

        while (!destroyDeque.isEmpty()) {
            final var currentClass = destroyDeque.pop();
            this.codeModel.getJDKTypeDescriptor(currentClass).ifPresent(currentDescriptor ->
                currentDescriptor.declaredMethods()
                    .filter(this::isPreDestroy)
                    .forEach(preDestroyMethodDescriptors::add));
        }

        return InjectableDescriptor.of(
            typeDescriptor,
            injectionPoints.values().stream(),
            postInjectMethodDescriptors.stream(),
            preDestroyMethodDescriptors.stream());
    }

    /**
     * Obtains the {@link InjectableDescriptor} for the specified {@link Class}, using the {@link JDKCodeModel} if
     * necessary to establish one.
     *
     * @param injectableClass the {@link Class}
     * @return the {@link InjectableDescriptor} for the {@link Class}
     */
    public InjectableDescriptor getInjectableDescriptor(final Class<?> injectableClass) {

        // determine the TypeDescriptor for the potentially injectable class
        final JDKTypeDescriptor typeDescriptor = this.codeModel
            .getJDKTypeDescriptor(injectableClass)
            .orElseThrow(
                () -> new InjectionException("Failed to discover the InjectionPoints for " + injectableClass));

        return typeDescriptor.computeIfAbsent(InjectableDescriptor.class, _ ->
                createInjectableDescriptor(injectableClass, typeDescriptor))
            .orElseThrow();
    }

    /**
     * Obtains the {@link InjectableDescriptor} for the specified {@link JDKTypeDescriptor}, using the
     * {@link JDKTypeDescriptor} if necessary to establish one.
     *
     * @param typeDescriptor the {@link JDKTypeDescriptor}
     * @return the {@link InjectableDescriptor} for the {@link JDKTypeDescriptor}
     */
    public InjectableDescriptor getInjectableDescriptor(final JDKTypeDescriptor typeDescriptor) {

        Objects.requireNonNull(typeDescriptor, "The TypeDescriptor must not be null");

        final var injectableClass = typeDescriptor.getTrait(JDKType.class)
            .map(JDKType::type)
            .filter(Class.class::isInstance)
            .map(Class.class::cast)
            .orElseThrow(() -> new IllegalArgumentException("Can't determine Class for the TypeDescriptor"));

        return typeDescriptor.computeIfAbsent(InjectableDescriptor.class, _ ->
                createInjectableDescriptor(injectableClass, typeDescriptor))
            .orElseThrow();
    }

    /**
     * Determines if a {@link Traitable} represents an {@link InjectionPoint}, which means it is
     * non-{@code static}, non-{@code abstract} with an {@link AnnotationTypeUsage} for {@link Inject}.
     *
     * @param traitable the {@link Traitable}
     * @return {@code true} if the {@link Traitable} contains an {@link Inject} use, {@code false} otherwise
     */
    public boolean isInjectionPoint(final Traitable traitable) {
        return traitable.getTrait(Static.class).isEmpty()
            && traitable.getTrait(Classification.class)
            .map(classification -> classification != Classification.ABSTRACT)
            .orElse(true)
            && traitable.traits(AnnotationTypeUsage.class)
            .anyMatch(annotationTypeUsage -> annotationTypeUsage
                .typeName()
                .canonicalName()
                .equals(Inject.class.getCanonicalName()));
    }

    /**
     * Determines if the specified {@link JDKTypeDescriptor} is annotated as a {@link Singleton}.
     *
     * @param typeDescriptor the {@link JDKTypeDescriptor}
     * @return {@code true} if annotated as a {@link Singleton}, otherwise {@code false}
     */
    public boolean isSingleton(final JDKTypeDescriptor typeDescriptor) {
        return typeDescriptor != null
            && typeDescriptor.traits(AnnotationTypeUsage.class)
            .anyMatch(annotationTypeUsage -> annotationTypeUsage
                .typeName()
                .canonicalName()
                .equals(Singleton.class.getCanonicalName()));
    }

    /**
     * Determines if a {@link MethodDescriptor} represents an {@link PostInject} method, which means it is
     * non-{@code static}, non-{@code abstract} with an {@link AnnotationTypeUsage} for {@link PostInject}.
     *
     * @param descriptor the {@link MethodDescriptor}
     * @return {@code true} if the {@link Traitable} contains an {@link Inject} use, {@code false} otherwise
     */
    public boolean isPostInject(final MethodDescriptor descriptor) {
        return descriptor.getTrait(Static.class).isEmpty()
            && descriptor.getTrait(Classification.class)
            .map(classification -> classification != Classification.ABSTRACT)
            .orElse(true)
            && descriptor.traits(AnnotationTypeUsage.class)
            .anyMatch(annotationTypeUsage -> annotationTypeUsage
                .typeName()
                .canonicalName()
                .equals(PostInject.class.getCanonicalName()));
    }

    /**
     * Determines if a {@link MethodDescriptor} represents a {@link PreDestroy} method.
     *
     * @param descriptor the {@link MethodDescriptor}
     * @return {@code true} if annotated with {@link PreDestroy}
     */
    public boolean isPreDestroy(final MethodDescriptor descriptor) {
        return descriptor.getTrait(Static.class).isEmpty()
            && descriptor.getTrait(Classification.class)
            .map(classification -> classification != Classification.ABSTRACT)
            .orElse(true)
            && descriptor.traits(AnnotationTypeUsage.class)
            .anyMatch(a -> a.typeName().canonicalName().equals(PreDestroy.class.getCanonicalName()));
    }

    /**
     * Registers a {@link Scope} implementation for the specified scope annotation type. The scope is
     * applied whenever a class annotated with {@code scopeAnnotation} is bound via
     * {@link Context#bind(Class)}.
     *
     * @param scopeAnnotation the scope annotation class (must be annotated with {@link ScopeAnnotation})
     * @param scope           the {@link Scope} implementation to register
     */
    public void bindScope(final Class<? extends Annotation> scopeAnnotation, final Scope scope) {
        Objects.requireNonNull(scopeAnnotation, "The scope annotation class must not be null");
        Objects.requireNonNull(scope, "The Scope must not be null");
        this.registeredScopes.put(scopeAnnotation, scope);
    }

    /**
     * Returns the registered scope entry (annotation class + {@link Scope}) for the given
     * {@link JDKTypeDescriptor}, if the type carries a scope annotation that has been registered
     * via {@link #bindScope}.
     *
     * @param typeDescriptor the type descriptor to inspect
     * @return an {@link Optional} containing the matching entry, or empty if none
     */
    public Optional<java.util.Map.Entry<Class<? extends Annotation>, Scope>> findScopeEntry(
        final JDKTypeDescriptor typeDescriptor) {

        if (typeDescriptor == null) {
            return Optional.empty();
        }
        return typeDescriptor.traits(AnnotationTypeUsage.class)
            .filter(this::hasScopeAnnotation)
            .flatMap(annotation -> {
                final var name = annotation.typeName().canonicalName();
                return this.registeredScopes.entrySet().stream()
                    .filter(e -> {
                        final var canonical = e.getKey().getCanonicalName();
                        return name.equals(e.getKey().getName())
                            || (canonical != null && name.equals(canonical));
                    });
            })
            .findFirst();
    }

    /**
     * Returns the registered {@link Scope} for the given {@link JDKTypeDescriptor}, if the type carries
     * a scope annotation that has been registered via {@link #bindScope}.
     *
     * @param typeDescriptor the type descriptor to inspect
     * @return an {@link Optional} containing the matching {@link Scope}, or empty if none
     */
    public Optional<Scope> findScope(final JDKTypeDescriptor typeDescriptor) {
        return findScopeEntry(typeDescriptor).map(java.util.Map.Entry::getValue);
    }

    /**
     * Installs a {@link GraphBuildingContributor} and returns this framework for fluent chaining.
     * Every context created by this framework will contribute its bindings and resolved dependencies
     * to the graph, which can then be snapshotted via {@link Context#snapshot}.
     *
     * @return this {@link InjectionFramework}
     */
    public InjectionFramework withBindingGraph() {
        setBindingGraphContributor(new GraphBuildingContributor());
        return this;
    }

    /**
     * Installs a {@link BindingGraphContributor} that will be notified of every binding registration
     * and dependency resolution for all contexts created by this framework. Replaces the default
     * {@link BindingGraphContributor#NOOP}.
     *
     * @param contributor the contributor to install; must not be {@code null}
     */
    public void setBindingGraphContributor(final BindingGraphContributor contributor) {
        this.bindingGraphContributor = Objects.requireNonNull(
            contributor, "The BindingGraphContributor must not be null");
    }

    /**
     * Returns the currently installed {@link BindingGraphContributor}.
     *
     * @return the contributor
     */
    BindingGraphContributor bindingGraphContributor() {
        return this.bindingGraphContributor;
    }

    /**
     * Returns {@code true} if the given annotation usage is itself annotated with {@link ScopeAnnotation}.
     * Uses codemodel lookup as the primary path, and falls back to matching against registered scopes
     * by name to handle inner annotation types that the codemodel may represent using a different name
     * format.
     *
     * @param annotation the annotation to check
     * @return {@code true} if it is a scope annotation
     */
    private boolean hasScopeAnnotation(final AnnotationTypeUsage annotation) {
        // Primary: look up the annotation type in the codemodel and check for @ScopeAnnotation meta-annotation
        if (this.codeModel.getJDKTypeDescriptor(annotation.typeName())
            .map(desc -> desc.traits(AnnotationTypeUsage.class)
                .anyMatch(a -> a.typeName().canonicalName().equals(ScopeAnnotation.class.getCanonicalName())))
            .orElse(false)) {
            return true;
        }
        // Fallback: match against registered scope annotation classes by name.
        // Handles inner annotation types where the codemodel lookup may use a different name format
        // (binary '$' vs canonical '.') than Class.getCanonicalName().
        final var name = annotation.typeName().canonicalName();
        return this.registeredScopes.keySet().stream()
            .anyMatch(cls -> {
                final var canonical = cls.getCanonicalName();
                return name.equals(cls.getName()) || (canonical != null && name.equals(canonical));
            });
    }

    /**
     * Determines if a {@link MethodDescriptor} is an effective {@link Provides} method within the context of
     * {@code declaredMethods} - typically every {@link MethodDescriptor} in the provider type's hierarchy,
     * as produced by scanning a {@link JDKTypeDescriptor}.
     *
     * <p>A method counts as {@link Provides} if it is non-{@code static}, non-{@code abstract} and
     * non-{@code void}, and either carries the {@link Provides} annotation itself or overrides an
     * {@code abstract} method in {@code declaredMethods} that does. An {@code abstract} method has no way to
     * "opt out" of being overridden - unlike an {@link Inject} method, every concrete subclass is forced to
     * supply an implementation - so requiring {@link Provides} to be repeated on every concrete override of
     * an {@code abstract} {@link Provides} method is pure boilerplate, and forgetting to do so would silently
     * drop the provider rather than report an error.
     *
     * <p>This is the single predicate underlying {@link #resolveEffectivelyProvides}, so the two never
     * disagree on what counts as {@link Provides}.
     *
     * @param descriptor      the {@link MethodDescriptor} to test
     * @param declaredMethods the surrounding {@link MethodDescriptor}s, used to detect an overridden
     *                        {@code abstract} {@link Provides} declaration
     * @return {@code true} if {@code descriptor} is an effective {@link Provides} method
     */
    public boolean isProvides(final MethodDescriptor descriptor, final Collection<MethodDescriptor> declaredMethods) {
        return isProvidesShaped(descriptor)
            && (hasProvidesAnnotation(descriptor)
                || abstractProvidesOverrideKeys(declaredMethods).contains(descriptor.overrideKey()));
    }

    /**
     * Determines if a {@link MethodDescriptor} has the shape of a provider method - non-{@code static},
     * non-{@code abstract} and non-{@code void} - independent of whether it (or an {@code abstract}
     * declaration it overrides) carries the {@link Provides} annotation. This is the common structural gate
     * applied by {@link #isProvides} and {@link #resolveEffectivelyProvides}.
     *
     * @param descriptor the {@link MethodDescriptor}
     * @return {@code true} if the {@link MethodDescriptor} could serve as a provider method
     */
    private boolean isProvidesShaped(final MethodDescriptor descriptor) {
        return descriptor.getTrait(Static.class).isEmpty()
            && descriptor.getTrait(Classification.class)
            .map(classification -> classification != Classification.ABSTRACT)
            .orElse(true)
            && !returnsVoid(descriptor);
    }

    /**
     * Determines if a {@link MethodDescriptor}'s return type is {@code void}.
     *
     * @param descriptor the {@link MethodDescriptor}
     * @return {@code true} if the method returns {@code void}
     */
    private boolean returnsVoid(final MethodDescriptor descriptor) {
        return descriptor.returnType() instanceof NamedTypeUsage namedTypeUsage
            && namedTypeUsage.typeName().canonicalName().equals("void");
    }

    /**
     * Determines if a {@link MethodDescriptor} itself carries the {@link Provides} annotation, regardless of
     * its {@code static}/{@code abstract} classification.
     *
     * @param descriptor the {@link MethodDescriptor}
     * @return {@code true} if the {@link MethodDescriptor} has a {@link Provides} annotation, {@code false} otherwise
     */
    private boolean hasProvidesAnnotation(final MethodDescriptor descriptor) {
        return descriptor.traits(AnnotationTypeUsage.class)
            .anyMatch(annotationTypeUsage -> annotationTypeUsage
                .typeName()
                .canonicalName()
                .equals(Provides.class.getCanonicalName()));
    }

    /**
     * Determines the {@link MethodDescriptor}s within {@code allMethods} that are effectively {@link Provides}
     * methods, per {@link #isProvides(MethodDescriptor, Collection)}.
     *
     * @param allMethods the {@link MethodDescriptor}s to inspect, typically the result of scanning a
     *                   {@link JDKTypeDescriptor}'s hierarchy
     * @return the {@link Stream} of effectively-{@link Provides} {@link MethodDescriptor}s
     */
    public Stream<MethodDescriptor> resolveEffectivelyProvides(final Collection<MethodDescriptor> allMethods) {

        // This is the same predicate as isProvides(md, allMethods), inlined so the abstract-override-key
        // set is computed once for the whole collection rather than once per method.
        final var abstractProvidesOverrideKeys = abstractProvidesOverrideKeys(allMethods);

        return allMethods.stream()
            .filter(md -> isProvidesShaped(md)
                && (hasProvidesAnnotation(md) || abstractProvidesOverrideKeys.contains(md.overrideKey())));
    }

    /**
     * Collects the {@link MethodDescriptor#overrideKey() override keys} of the {@code abstract}
     * {@link Provides}-annotated methods in {@code methods}. A concrete method whose
     * {@link MethodDescriptor#overrideKey() override key} is in this set overrides an {@code abstract}
     * {@link Provides} declaration; whether that concrete method is itself an effective {@link Provides}
     * method is then decided by {@link #isProvidesShaped}, which independently rejects {@code void} returns.
     *
     * @param methods the {@link MethodDescriptor}s to inspect
     * @return the set of overridden {@code abstract} {@link Provides} override keys
     */
    private Set<String> abstractProvidesOverrideKeys(final Collection<MethodDescriptor> methods) {
        return methods.stream()
            .filter(md -> md.getTrait(Classification.class)
                .map(classification -> classification == Classification.ABSTRACT)
                .orElse(false))
            .filter(this::hasProvidesAnnotation)
            .map(MethodDescriptor::overrideKey)
            .collect(Collectors.toSet());
    }

    /**
     * Creates a new empty {@link Context}.
     *
     * @return a new {@link Context}
     */
    public Context newContext() {
        return new InjectionContext(this);
    }

    /**
     * Creates a new {@link Context} initialized with the specified {@link Resolver}s.
     *
     * @param resolvers the {@link Resolver}s
     * @return a new {@link Context}
     */
    public Context newContext(final Resolver<?>... resolvers) {
        final var context = new InjectionContext(this);

        Streams.of(resolvers)
            .filter(Objects::nonNull)
            .forEach(context::addResolver);

        return context;
    }

    /**
     * Creates a new {@link Context} with the specified {@link Module}s pre-installed.
     *
     * @param modules the {@link Module}s to install
     * @return a new {@link Context}
     */
    public Context newContext(final Module... modules) {
        final var context = new InjectionContext(this);

        Streams.of(modules)
            .filter(Objects::nonNull)
            .forEach(module -> module.configure(context));

        return context;
    }
}
