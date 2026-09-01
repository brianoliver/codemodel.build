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

import build.base.foundation.Lazy;
import build.base.graph.Graph;
import build.base.graph.Graphs;
import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.descriptor.MethodType;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An internal {@link Context} implementation.
 *
 * @author brian.oliver
 * @since Oct-2024
 */
class InjectionContext
    implements Context {

    /**
     * The {@link InjectionFramework} that created the {@link Context}.
     */
    private final InjectionFramework injectionFramework;

    /**
     * The {@link Binding}s in the {@link Context} by {@link Dependency}.
     */
    private final ConcurrentHashMap<Dependency, Binding<?>> bindingsByDependency;

    /**
     * The multibinding entries keyed by element {@link Dependency}.
     */
    private final ConcurrentHashMap<Dependency, MultiBindingEntry<?>> multiBindings;

    /**
     * The {@link Resolver} defined by the {@link Binding}s in this {@link Context}, so this {@link Context}
     * may be used to resolve {@link Binding}s of other {@link Context}s.
     */
    private final Resolver<Object> resolver;

    /**
     * {@link #resolver} without the {@link #resolveEmptyMultiBinding} last resort. This is what a child
     * {@link Context} inherits from this one via {@link #newContext()}: the child appends its own single
     * {@link #resolveEmptyMultiBinding} at the very end of its chain, so that across the whole
     * parent/child composition the empty-collection synthesis stays strictly last — behind every real
     * resolver, this context's and the child's alike.
     */
    private final Resolver<Object> chainableResolver;

    /**
     * The {@link ChainedResolver} of {@link Resolver}s added to this {@link InjectionContext}.
     */
    private final ChainedResolver chainedResolver;

    /**
     * The {@link BindingGraphContributor} notified of binding registrations and dependency resolutions.
     * Captured from the {@link InjectionFramework} at construction time.
     */
    private final BindingGraphContributor bindingGraphContributor;

    /**
     * Constructs an {@link InjectionContext}.
     *
     * @param injectionFramework the {@link InjectionFramework}
     */
    @SuppressWarnings("unchecked")
    InjectionContext(final InjectionFramework injectionFramework) {
        this.injectionFramework = Objects
            .requireNonNull(injectionFramework, "The Injection framework must not be null");
        this.bindingsByDependency = new ConcurrentHashMap<>();
        this.multiBindings = new ConcurrentHashMap<>();
        this.chainedResolver = ChainedResolver.create();
        this.bindingGraphContributor = injectionFramework.bindingGraphContributor();
        this.chainableResolver = ChainedResolver.create(
            dependency -> (Optional<Binding<Object>>) (Optional<?>) Dependency.resolve(
                dependency, this.bindingsByDependency, this.injectionFramework.codeModel()),
            dependency -> (Optional<Binding<Object>>) (Optional<?>) resolveMultiBinding(dependency),
            chainedResolver);
        this.resolver = ChainedResolver.create(
            this.chainableResolver,
            dependency -> (Optional<Binding<Object>>) (Optional<?>) resolveEmptyMultiBinding(dependency));
    }

    @Override
    public Context addResolver(final Resolver<?> resolver) {
        if (resolver != null) {
            this.chainedResolver.addResolver(resolver);
        }
        return this;
    }

    @Override
    public Context addResolver(final BiFunction<? super InjectionFramework, ? super Context, Resolver<?>> supplier) {

        return supplier == null
            ? this
            : addResolver(supplier.apply(this.injectionFramework, this));
    }

    @Override
    public <T> BindingBuilder<T> bind(final Class<T> bindingClass) {
        Objects.requireNonNull(bindingClass, "The Binding Class must not be null");

        return bind(this.injectionFramework.codeModel().getTypeUsage(bindingClass));
    }

    @Override
    public <T> BindingBuilder<T> bind(final TypeLiteral<T> typeLiteral) {
        Objects.requireNonNull(typeLiteral, "The TypeLiteral must not be null");

        return bind(this.injectionFramework.codeModel().getTypeUsage(typeLiteral.type()));
    }

    /**
     * Creates a {@link BindingBuilder} for the specified {@link TypeUsage}, shared by both
     * {@link #bind(Class)} and {@link #bind(TypeLiteral)}, whose only difference is how the {@link TypeUsage}
     * key was originally derived (a raw {@link Class}, versus a fully-parameterized generic {@link java.lang.reflect.Type}
     * captured by a {@link TypeLiteral}).
     *
     * @param typeUsage the {@link TypeUsage} to bind
     * @param <T>       the type of {@link Binding}
     * @return a new {@link BindingBuilder}
     */
    private <T> BindingBuilder<T> bind(final TypeUsage typeUsage) {
        final var codeModel = this.injectionFramework.codeModel();

        return new AbstractBindingBuilder<T>(this.injectionFramework, typeUsage) {
            @Override
            public Binding<T> to(final T value) {
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);

                return addBinding(dependency, SingletonValueBinding.of(dependency, value));
            }

            @Override
            public Binding<T> to(final Class<? extends T> concreteClass) {
                Objects.requireNonNull(concreteClass, "The Binding Value Class must not be null");

                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);

                final var typeDescriptor = codeModel.getJDKTypeDescriptor(concreteClass)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Could not resolve a TypeDescriptor for " + concreteClass));

                // check for a registered custom scope annotation on the concrete class
                final var scopeEntry = this.injectionFramework.findScopeEntry(typeDescriptor).orElse(null);
                if (scopeEntry != null) {
                    final ValueBinding<T> factory = new SupplierBinding<>(
                        dependency, () -> (T) InjectionContext.this.createUnscoped(concreteClass));
                    final Binding<?> scoped = scopeEntry.getValue().scope(factory);
                    return addBinding(dependency,
                        new CustomScopedClassBinding<>(dependency, concreteClass, scopeEntry.getKey(), scoped));
                }

                return addBinding(dependency, this.injectionFramework.isSingleton(typeDescriptor)
                    ? new LazySingletonClassBinding<>(dependency, concreteClass)
                    : new NonSingletonClassBinding<T>(dependency, concreteClass));
            }

            @Override
            public Binding<T> to(final Supplier<T> supplier) {
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);

                return addBinding(dependency, new SupplierBinding<>(dependency, supplier));
            }

            @Override
            public Binding<T> toOverriding(final T value) {
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);
                return replaceBinding(dependency, SingletonValueBinding.of(dependency, value));
            }

            @Override
            public Binding<T> toOverriding(final Class<? extends T> concreteClass) {
                Objects.requireNonNull(concreteClass, "The Binding Value Class must not be null");
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);
                final var typeDescriptor = codeModel.getJDKTypeDescriptor(concreteClass)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Could not resolve a TypeDescriptor for " + concreteClass));
                final var scopeEntry = this.injectionFramework.findScopeEntry(typeDescriptor);
                if (scopeEntry.isPresent()) {
                    final ValueBinding<T> factory = new SupplierBinding<>(
                        dependency, () -> (T) InjectionContext.this.createUnscoped(concreteClass));
                    final Binding<?> scoped = scopeEntry.get().getValue().scope(factory);
                    return replaceBinding(dependency,
                        new CustomScopedClassBinding<>(dependency, concreteClass, scopeEntry.get().getKey(), scoped));
                }
                return replaceBinding(dependency, this.injectionFramework.isSingleton(typeDescriptor)
                    ? new LazySingletonClassBinding<>(dependency, concreteClass)
                    : new NonSingletonClassBinding<T>(dependency, concreteClass));
            }

            @Override
            public Binding<T> toOverriding(final Supplier<T> supplier) {
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);
                return replaceBinding(dependency, new SupplierBinding<>(dependency, supplier));
            }
        };
    }

    /**
     * Builds a {@link BindingNode} description of the given {@link Binding} for the
     * {@link BindingGraphContributor}. Fields that cannot be populated in Track 1 are {@code null}.
     *
     * @param binding the binding to describe
     * @return the {@link BindingNode}
     */
    private BindingNode toBindingNode(final Binding<?> binding) {
        if (binding == null) {
            return null;
        }
        final BindingKind kind = switch (binding) {
            case LazySingletonClassBinding<?> ignored -> BindingKind.CLASS;
            case NonSingletonClassBinding<?> ignored -> BindingKind.CLASS;
            case CustomScopedClassBinding<?> ignored -> BindingKind.CLASS;
            case SingletonValueBinding<?> ignored -> BindingKind.VALUE;
            default -> BindingKind.SUPPLIER;
        };
        final Class<? extends Annotation> scopeAnnotation = switch (binding) {
            case LazySingletonClassBinding<?> ignored -> Singleton.class;
            case CustomScopedClassBinding<?> csb -> csb.scopeAnnotation();
            default -> null;
        };
        final var typeDescriptor = binding instanceof ClassBinding<?> cb
            ? this.injectionFramework.codeModel().getJDKTypeDescriptor(cb.concreteClass()).orElse(null)
            : null;
        return new BindingNode(typeDescriptor, scopeAnnotation, null, kind);
    }

    /**
     * Attempts to add the specified {@link Dependency} {@link Binding}.
     *
     * @param dependency the {@link Dependency}
     * @param binding    the {@link Binding}
     * @return the {@link Binding} that was added
     * @throws BindingAlreadyExistsException if a {@link Binding} for the {@link Dependency} already exists
     */
    private <T> Binding<T> addBinding(final Dependency dependency, final Binding<T> binding) {
        this.bindingsByDependency.compute(dependency, (_, existing) -> {
            if (existing != null) {
                throw new BindingAlreadyExistsException("Binding for [" + dependency + "] already exists!");
            }
            return binding;
        });
        this.bindingGraphContributor.contributeBinding(toBindingNode(binding));
        return binding;
    }

    /**
     * Replaces or adds the specified {@link Dependency} {@link Binding} without throwing when a binding
     * already exists.
     *
     * @param dependency the {@link Dependency}
     * @param binding    the {@link Binding}
     * @return the {@link Binding} that was registered
     */
    private <T> Binding<T> replaceBinding(final Dependency dependency, final Binding<T> binding) {
        this.bindingsByDependency.put(dependency, binding);
        this.bindingGraphContributor.contributeBinding(toBindingNode(binding));
        return binding;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> BindingBuilder<T> bind(final T value) {
        Objects.requireNonNull(value, "The value must not be null");

        final var codeModel = this.injectionFramework.codeModel();
        final var type = (Class<T>) value.getClass();
        final var typeUsage = codeModel.getTypeUsage(type);

        return new AbstractBindingBuilder<>(this.injectionFramework, typeUsage) {
            @Override
            public Binding<T> to(final T v) {
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);
                return addBinding(dependency, SingletonValueBinding.of(dependency, v));
            }

            @Override
            public Binding<T> to(final Class<? extends T> concreteClass) {
                Objects.requireNonNull(concreteClass, "The Binding Value Class must not be null");
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);
                final var typeDescriptor = codeModel.getJDKTypeDescriptor(concreteClass)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Could not resolve a TypeDescriptor for " + concreteClass));
                final var scopeEntry = this.injectionFramework.findScopeEntry(typeDescriptor);
                if (scopeEntry.isPresent()) {
                    final ValueBinding<T> factory = new SupplierBinding<>(
                        dependency, () -> (T) InjectionContext.this.createUnscoped(concreteClass));
                    final Binding<?> scoped = scopeEntry.get().getValue().scope(factory);
                    return addBinding(dependency,
                        new CustomScopedClassBinding<>(dependency, concreteClass, scopeEntry.get().getKey(), scoped));
                }
                return addBinding(dependency, this.injectionFramework.isSingleton(typeDescriptor)
                    ? new LazySingletonClassBinding<>(dependency, concreteClass)
                    : new NonSingletonClassBinding<>(dependency, concreteClass));
            }

            @Override
            public Binding<T> to(final Supplier<T> supplier) {
                final var dependency = IndependentDependency.of(
                    typeUsage,
                    this.injectionFramework::getQualifierAnnotationTypes);
                return addBinding(dependency, new SupplierBinding<>(dependency, supplier));
            }

            @Override
            public void asAllInterfaces() {
                asAllInterfaces(iface -> !iface.getPackageName().startsWith("java."));
            }

            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void asAllInterfaces(final Predicate<Class<?>> filter) {
                Objects.requireNonNull(filter, "The filter must not be null");
                collectInterfaces(type, new LinkedHashSet<>())
                    .stream()
                    .filter(filter)
                    .forEach(iface -> InjectionContext.this.bind((Class) iface).to(value));
            }
        };
    }

    /**
     * Collects all interfaces from the type hierarchy of the given class, including inherited ones,
     * into the provided accumulator set.
     *
     * @param type   the class to walk
     * @param result the set to accumulate interfaces into
     * @return the same set, populated
     */
    private static Set<Class<?>> collectInterfaces(final Class<?> type, final Set<Class<?>> result) {
        if (type == null || type == Object.class) {
            return result;
        }
        for (final Class<?> iface : type.getInterfaces()) {
            result.add(iface);
            collectInterfaces(iface, result);
        }
        collectInterfaces(type.getSuperclass(), result);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MultiBinder<T> bindSet(final Class<T> type) {
        Objects.requireNonNull(type, "The element type must not be null");
        final var created = new AtomicBoolean(false);
        final var elementDependency = IndependentDependency.of(
            this.injectionFramework.codeModel().getTypeUsage(type), _ -> Stream.empty());
        final var entry = (MultiBindingEntry<T>) this.multiBindings.compute(elementDependency, (_, existing) -> {
            if (existing != null) {
                return existing;
            }
            created.set(true);
            return new MultiBindingEntry<T>();
        });
        if (created.get()) {
            final var typeDescriptor = this.injectionFramework.codeModel()
                .getJDKTypeDescriptor(type).orElse(null);
            this.bindingGraphContributor.contributeBinding(
                new BindingNode(typeDescriptor, null, null, BindingKind.MULTI));
        }
        return new MultiBinder<T>() {
            @Override
            public MultiBinder<T> add(final T value) {
                entry.addValue(value);
                return this;
            }

            @Override
            public MultiBinder<T> add(final Class<? extends T> implementationClass) {
                entry.addSupplier(() -> InjectionContext.this.create(implementationClass));
                return this;
            }

            @Override
            public MultiBinder<T> add(final Supplier<? extends T> supplier) {
                entry.addSupplier(() -> supplier.get());
                return this;
            }
        };
    }

    /**
     * Attempts to resolve a multibinding for collection types ({@link Set}, {@link Collection},
     * {@link Iterable}, {@link java.util.stream.Stream}, {@link List}) from the contributions
     * registered on <em>this</em> context via {@code bindSet}.
     *
     * <p>Returns empty when the element type was never registered here, so the rest of the
     * {@link Resolver} chain — user-added resolvers and any parent context's resolver — gets a
     * chance to satisfy the injection point. {@link #resolveEmptyMultiBinding} is the last link in
     * that chain and supplies an empty collection only once everything else has declined.
     *
     * @param dependency the {@link Dependency} to resolve
     * @return the resolved {@link Binding}, or empty if this is not a supported collection type or its
     *         element type was never registered here via {@code bindSet}
     */
    @SuppressWarnings("unchecked")
    private Optional<? extends Binding<?>> resolveMultiBinding(final Dependency dependency) {
        if (!(dependency.typeUsage() instanceof GenericTypeUsage generic)
            || !isSupportedCollection(generic.typeName().canonicalName())) {
            return Optional.empty();
        }

        final String rawName = generic.typeName().canonicalName();
        return generic.parameters().findFirst()
            .flatMap(elementTypeUsage -> {
                final var elementDependency = IndependentDependency.of(elementTypeUsage, _ -> Stream.empty());
                return (Optional<MultiBindingEntry<Object>>) (Optional<?>) Dependency.resolve(
                    elementDependency, this.multiBindings, this.injectionFramework.codeModel());
            })
            .map(entry -> buildCollectionBinding(dependency, rawName, entry));
    }

    /**
     * Fallback multibinding resolver placed at the end of the {@link Resolver} chain: a supported
     * collection injection point whose element type was never registered via {@code bindSet} — here
     * or in any parent context — and which no other resolver could satisfy is bound to an empty
     * collection rather than reported as an unsatisfied dependency.
     *
     * @param dependency the {@link Dependency} to resolve
     * @return an empty-collection {@link Binding}, or empty if not a supported collection type
     */
    private Optional<? extends Binding<?>> resolveEmptyMultiBinding(final Dependency dependency) {
        if (!(dependency.typeUsage() instanceof GenericTypeUsage generic)
            || !isSupportedCollection(generic.typeName().canonicalName())) {
            return Optional.empty();
        }

        return Optional.of(
            buildCollectionBinding(dependency, generic.typeName().canonicalName(), new MultiBindingEntry<>()));
    }

    /**
     * Whether the given raw canonical type name is one of the five collection types the multibinding
     * system can inject: {@link Set}, {@link Collection}, {@link Iterable}, {@link java.util.stream.Stream},
     * {@link List}.
     */
    private static boolean isSupportedCollection(final String rawName) {
        return Set.class.getCanonicalName().equals(rawName)
            || Collection.class.getCanonicalName().equals(rawName)
            || Iterable.class.getCanonicalName().equals(rawName)
            || Stream.class.getCanonicalName().equals(rawName)
            || List.class.getCanonicalName().equals(rawName);
    }

    /**
     * Builds the {@link Binding} that presents the given {@link MultiBindingEntry}'s contributions as
     * the collection shape requested by {@code rawName}.
     */
    private static Binding<?> buildCollectionBinding(final Dependency dependency,
                                                     final String rawName,
                                                     final MultiBindingEntry<Object> entry) {
        return switch (rawName) {
            case "java.util.Set" -> ValueBinding.of(dependency, (Object) entry.buildSet());
            case "java.util.Collection", "java.lang.Iterable" ->
                ValueBinding.of(dependency, (Object) (Collection<?>) entry.buildSet());
            case "java.util.List" -> ValueBinding.of(dependency, (Object) List.copyOf(entry.buildSet()));
            case "java.util.stream.Stream" ->
                new SupplierBinding<>(dependency, (Supplier<Object>) () -> entry.buildSet().stream());
            default -> throw new IllegalStateException("Unsupported collection type: " + rawName);
        };
    }

    @Override
    public Resolver<Object> resolver() {
        return this.resolver;
    }

    @Override
    public <T> T inject(final T injectable)
        throws InjectionException {

        // we can't inject into arrays, enums, interfaces or primitives
        if (injectable == null
            || injectable.getClass().isArray()
            || injectable.getClass().isEnum()
            || injectable.getClass().isPrimitive()) {
            return injectable;
        }

        final var codeModel = this.injectionFramework.codeModel();
        final var typeUsage = codeModel.getTypeUsage(injectable.getClass());
        final var dependency = IndependentDependency.of(
            typeUsage,
            this.injectionFramework::getQualifierAnnotationTypes);

        return new ResolvableObject<>(dependency, injectable)
            .resolve();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(final Class<T> requiredClass)
        throws InjectionException {

        Objects.requireNonNull(requiredClass, "The required class must not be null");

        final var codeModel = this.injectionFramework.codeModel();
        final var typeUsage = codeModel.getTypeUsage(requiredClass);
        final var dependency = IndependentDependency.of(
            typeUsage,
            this.injectionFramework::getQualifierAnnotationTypes);

        return (T) getValue(Optional.empty(), dependency)
            .orElseGet(() -> new ResolvableClass<>(Optional.empty(), dependency, requiredClass)
                .resolve());
    }

    @Override
    public <T> T create(final TypeUsage typeUsage)
        throws InjectionException {

        return create(IndependentDependency.of(typeUsage, this.injectionFramework::getQualifierAnnotationTypes));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(final Dependency dependency)
        throws InjectionException {

        Objects.requireNonNull(dependency, "The Dependency must not be null");

        return (T) getValue(Optional.empty(), dependency)
            .orElseThrow(() -> new UnsatisfiedDependencyException(dependency));
    }

    @Override
    public Context validate() {
        // Build a directed dependency graph over all ClassBindings.
        // Edge: binding.dependency() → each of its injected dependencies.
        final var graphBuilder = Graph.<Dependency>directed();

        this.bindingsByDependency.values().stream()
            .filter(ClassBinding.class::isInstance)
            .map(b -> (ClassBinding<?>) b)
            .forEach(classBinding -> {
                final var bindingDep = classBinding.dependency();
                graphBuilder.addVertex(bindingDep);
                this.injectionFramework.getInjectableDescriptor(classBinding.concreteClass())
                    .injectionPoints()
                    .flatMap(InjectionPoint::dependencies)
                    .forEach(dep -> graphBuilder.addEdge(bindingDep, dep));
            });

        final var graph = graphBuilder.build();

        // 1. Cycle detection
        Graphs.findCycle(graph)
            .ifPresent(cycle -> {
                throw new CyclicDependencyException(cycle);
            });

        // 2. Unsatisfied dependency and 3. Scope violation detection
        final var problems = new ArrayList<String>();

        graph.edges().forEach(edge -> {
            final var dep = edge.to();

            // Unsatisfied: no explicit binding and not auto-resolvable
            if (!isResolvable(dep)) {
                problems.add("Unsatisfied dependency: [" + dep + "]"
                    + " required by [" + edge.from() + "]");
            }

            // Scope violation: @Singleton depends on a narrower-scoped binding
            final var fromBinding = this.bindingsByDependency.get(edge.from());
            final var toBinding = this.bindingsByDependency.get(dep);
            if (fromBinding instanceof LazySingletonClassBinding) {
                if (toBinding instanceof NonSingletonClassBinding) {
                    problems.add("Scope violation: singleton [" + edge.from()
                        + "] depends on prototype-scoped [" + dep + "]");
                } else if (toBinding instanceof CustomScopedClassBinding<?> csb) {
                    problems.add("Scope violation: singleton [" + edge.from()
                        + "] depends on @" + csb.scopeAnnotation().getSimpleName() + "-scoped [" + dep + "]");
                }
            }
        });

        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return this;
    }

    @Override
    public Context snapshot(final Path outputPath) {
        this.bindingGraphContributor.buildTrait().ifPresent(trait -> {
            this.injectionFramework.codeModel()
                .<JDKCodeModel, BindingGraphTrait>computeIfAbsent(
                    BindingGraphTrait.class, _ -> trait);
            WiringReportCompiler.writeReport(trait, outputPath);
        });
        return this;
    }

    /**
     * Returns {@code true} if the given {@link Dependency} can be satisfied — either by an explicit or
     * multibinding already registered, or by an auto-bindable {@link jakarta.inject.Singleton} class.
     * A supported collection injection point ({@code Set}, {@code Collection}, {@code Iterable},
     * {@code Stream}, {@code List}) is always satisfiable — it resolves to an empty collection when
     * nothing was contributed — so {@link #validate()} never reports one as unsatisfied.
     */
    private boolean isResolvable(final Dependency dependency) {
        if (this.resolver.resolve(dependency).isPresent()) {
            return true;
        }
        // auto-singleton: a @Singleton class that can be bound on-demand
        if (dependency.typeUsage() instanceof NamedTypeUsage namedTypeUsage) {
            try {
                final var clazz = Class.forName(namedTypeUsage.typeName().binaryName());
                return this.injectionFramework.codeModel()
                    .getJDKTypeDescriptor(clazz)
                    .map(this.injectionFramework::isSingleton)
                    .orElse(false);
            } catch (final ClassNotFoundException ignored) {
                return false;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Context initializeEagerSingletons() {
        // Collect all explicitly-bound singleton class bindings
        final var singletonBindings = this.bindingsByDependency.values().stream()
            .filter(LazySingletonClassBinding.class::isInstance)
            .map(b -> (LazySingletonClassBinding<Object>) b)
            .toList();

        if (singletonBindings.isEmpty()) {
            return this;
        }

        // Build a dependency graph limited to singleton vertices so we can find initialization order
        final var singletonDeps = singletonBindings.stream()
            .map(Binding::dependency)
            .collect(Collectors.toSet());

        final var graphBuilder = Graph.<Dependency>directed();
        singletonBindings.forEach(b -> {
            final var bindingDep = b.dependency();
            graphBuilder.addVertex(bindingDep);
            this.injectionFramework.getInjectableDescriptor(b.concreteClass())
                .injectionPoints()
                .flatMap(InjectionPoint::dependencies)
                .filter(singletonDeps::contains)
                .forEach(dep -> graphBuilder.addEdge(bindingDep, dep));
        });

        final var graph = graphBuilder.build();

        // Initialize each parallelizable layer; within a layer, initialize concurrently
        Graphs.parallelizableGroups(graph).forEach(layer ->
            layer.parallelStream().forEach(this::create));

        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void close() {
        // Collect all instantiated singleton and custom-scoped bindings
        final var instantiatedSingletons = this.bindingsByDependency.values().stream()
            .filter(LazySingletonClassBinding.class::isInstance)
            .map(b -> (LazySingletonClassBinding<Object>) b)
            .filter(b -> b.value().optional().isPresent())
            .toList();

        final var instantiatedCustomScoped = this.bindingsByDependency.values().stream()
            .filter(CustomScopedClassBinding.class::isInstance)
            .map(b -> (CustomScopedClassBinding<Object>) b)
            .filter(CustomScopedClassBinding::hasInstantiatedValues)
            .toList();

        if (instantiatedSingletons.isEmpty() && instantiatedCustomScoped.isEmpty()) {
            return;
        }

        // Build a dependency graph over all instantiated bindings
        final var instantiatedDeps = Stream.concat(
                instantiatedSingletons.stream().map(Binding::dependency),
                instantiatedCustomScoped.stream().map(Binding::dependency))
            .collect(Collectors.toSet());

        final var graphBuilder = Graph.<Dependency>directed();
        Stream.concat(
            instantiatedSingletons.stream().map(b -> (ClassBinding<Object>) b),
            instantiatedCustomScoped.stream().map(b -> (ClassBinding<Object>) b)
        ).forEach(b -> {
            final var bindingDep = b.dependency();
            graphBuilder.addVertex(bindingDep);
            this.injectionFramework.getInjectableDescriptor(b.concreteClass())
                .injectionPoints()
                .flatMap(InjectionPoint::dependencies)
                .filter(instantiatedDeps::contains)
                .forEach(dep -> graphBuilder.addEdge(bindingDep, dep));
        });

        final var graph = graphBuilder.build();

        // topologicalSort returns [leaf-first, root-last]; reverse for destruction (dependents first)
        final var destroyOrder = new ArrayList<>(Graphs.topologicalSort(graph));
        Collections.reverse(destroyOrder);

        // Index bindings by dependency for lookup during destruction
        final var depToBinding = Stream.concat(
                instantiatedSingletons.stream().map(b -> (ClassBinding<Object>) b),
                instantiatedCustomScoped.stream().map(b -> (ClassBinding<Object>) b))
            .collect(Collectors.toMap(Binding::dependency, b -> b));

        // Invoke @PreDestroy methods in destruction order
        destroyOrder.forEach(dep -> {
            final var binding = depToBinding.get(dep);
            final Stream<Object> instances = switch (binding) {
                case LazySingletonClassBinding<Object> lsb -> lsb.value().optional().stream();
                case CustomScopedClassBinding<Object> csb -> csb.instantiatedValues();
                default -> throw new IllegalStateException("Unexpected binding type: " + binding.getClass());
            };
            instances.forEach(instance ->
                this.injectionFramework.getInjectableDescriptor(binding.concreteClass())
                    .preDestroyMethods()
                    .filter(md -> md.hasTrait(MethodType.class))
                    .map(md -> md.trait(MethodType.class))
                    .map(MethodType::method)
                    .forEach(method -> {
                        try {
                            method.setAccessible(true);
                            method.invoke(instance);
                        } catch (final IllegalAccessException | InvocationTargetException e) {
                            throw new InjectionException(
                                "Invoking @PreDestroy method " + method + " on " + instance.getClass(), e);
                        }
                    }));
        });
    }

    /**
     * Creates an instance of the specified class bypassing any registered {@link Binding}s by
     * directly instantiating via {@link ResolvableClass}. Used by scope implementations to produce
     * a fresh unscoped instance without recursing into the scoped binding.
     *
     * @param <T>           the type
     * @param concreteClass the class to instantiate
     * @return a new injected instance
     */
    private <T> T createUnscoped(final Class<? extends T> concreteClass) {
        final var codeModel = this.injectionFramework.codeModel();
        final var typeUsage = codeModel.getTypeUsage(concreteClass);
        final var dependency = IndependentDependency.of(typeUsage, this.injectionFramework::getQualifierAnnotationTypes);
        return (T) new ResolvableClass<>(Optional.empty(), dependency, concreteClass).resolve();
    }

    @Override
    public Context newContext() {
        return this.injectionFramework.newContext(this.chainableResolver);
    }

    /**
     * Attempts to resolve an existing value for the specified {@link Dependency}.
     *
     * @param requiredBy the {@link Resolvable} this {@link Dependency} value is required by
     * @param dependency the {@link Dependency}
     * @param <T>        the type of value
     * @return the {@link Optional} resolved value
     */
    @SuppressWarnings("unchecked")
    private <T> Optional<T> getValue(final Optional<Resolvable<?>> requiredBy,
                                     final Dependency dependency) {

        final var codeModel = this.injectionFramework.codeModel();

        final var binding = this.resolver
            .resolve(dependency)
            .orElse(null);

        if (binding instanceof ValueBinding<Object> valueBinding) {
            return Optional.of((T) valueBinding.value());
        } else if (binding instanceof LazySingletonClassBinding<Object> lazySingletonClassBinding) {
            final var concreteClass = lazySingletonClassBinding.concreteClass();
            final var singletonTypeUsage = codeModel.getTypeUsage(concreteClass);
            final var singletonDependency = IndependentDependency.of(
                singletonTypeUsage,
                _ -> injectionFramework.getQualifierAnnotationTypes(binding.dependency().typeUsage()));

            return lazySingletonClassBinding.value()
                .computeIfAbsent(() -> new ResolvableClass<>(requiredBy, singletonDependency, concreteClass)
                    .resolve())
                .map(object -> (T) object)
                .optional();
        } else if (binding instanceof NonSingletonClassBinding<Object> nonSingletonClassBinding) {
            return Optional.of(new ResolvableClass<T>(
                requiredBy,
                dependency,
                (Class<? extends T>) nonSingletonClassBinding.concreteClass())
                .resolve());
        }

        if (binding == null) {
            // obtain the concrete Class for the Dependency
            final Optional<Class<T>> concreteClassOptional = resolveClassFrom(dependency);
            if (concreteClassOptional.isEmpty()) {
                // the Dependency does not name a class resolvable on this loader. For a top-level
                // request (no requiredBy) return empty so create(Class)'s own fallback — which holds
                // the caller's real Class reference — can take over; create(Dependency)/create(TypeUsage)
                // will still surface UnsatisfiedDependencyException via orElseThrow. A nested @Inject
                // dependency has no Class in hand, so it must fail here.
                if (requiredBy.isEmpty()) {
                    return Optional.empty();
                }
                throw new UnsatisfiedDependencyException(dependency);
            }
            final Class<T> concreteClass = concreteClassOptional.get();

            // obtain the TypeDescriptor for the Dependency
            final var typeDescriptor = codeModel.getJDKTypeDescriptor(concreteClass)
                .orElseThrow(() -> new UnsatisfiedDependencyException(dependency));

            if (this.injectionFramework.isSingleton(typeDescriptor)) {
                try {
                    bind(concreteClass).to(concreteClass);
                } catch (final BindingAlreadyExistsException ignored) {
                    // another thread won the race to register this singleton concurrently
                }
                return getValue(requiredBy, dependency);
            } else if (requiredBy.isEmpty()) {
                // when we don't have a requiredBy, that means we're trying to instantiate the class directly
                // (which is ok)
                return Optional.of(new ResolvableClass<T>(
                    requiredBy,
                    dependency,
                    concreteClass)
                    .resolve());
            }
        }

        return Optional.empty();
    }

    /**
     * Attempts to obtain the {@link Class} represented by the {@link Dependency} by name, using this
     * module's classloader.
     *
     * <p>Returns an empty {@link Optional} rather than throwing when the {@link Dependency} does not
     * name a resolvable class (it is not a {@link NamedTypeUsage}, or {@link Class#forName} cannot see
     * it on this loader). A top-level {@code create(Class)} still holds the caller's real
     * {@link Class} reference and has its own fallback, so {@link #getValue} returns empty for that
     * case and lets the fallback take over; a nested dependency has no such reference and fails there
     * instead.
     *
     * @param dependency the {@link Dependency}
     * @param <T>        the type of {@link Class}
     * @return the {@link Optional} {@link Class}
     */
    @SuppressWarnings("unchecked")
    private <T> Optional<Class<T>> resolveClassFrom(final Dependency dependency) {
        if (dependency.typeUsage() instanceof NamedTypeUsage namedTypeUsage) {
            try {
                return Optional.of((Class<T>) Class.forName(namedTypeUsage.typeName().binaryName()));
            } catch (final ClassNotFoundException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * Represents a {@link Dependency} that is to be resolved through <i>Dependency Injection</i> using
     * the {@link Binding}s provided by the {@link Context}.
     *
     * @param <T> the type to be resolved
     */
    private interface Resolvable<T> {

        /**
         * The {@link Resolvable} that established this {@link Resolvable} as it is required by the former to be
         * resolved.
         *
         * @return the {@link Optional} {@link Resolvable} that requires this {@link Resolvable}
         */
        Optional<Resolvable<?>> requiredBy();

        /**
         * The {@link Dependency} to be resolved.
         *
         * @return the {@link Dependency} to be resolved
         */
        Dependency dependency();

        /**
         * The {@link InjectableDescriptor} defined for the {@link Dependency}.
         *
         * @return the {@link InjectableDescriptor}
         */
        InjectableDescriptor injectableDescriptor();

        /**
         * Attempts to resolve the {@link Resolvable}.
         *
         * @return the resolved {@link Object}
         * @throws InjectionException should injection fail
         */
        T resolve()
            throws InjectionException;
    }

    /**
     * An {@code abstract} {@link Resolvable}.
     *
     * @param <T> the type to be resolved
     */
    private abstract class AbstractResolvable<T>
        implements Resolvable<T> {

        /**
         * The {@link Resolvable} that requires this {@link Resolvable} to be resolved.
         */
        private final Optional<Resolvable<?>> requiredBy;

        /**
         * The {@link Dependency} to be resolved.
         */
        private final Dependency dependency;

        /**
         * The {@link InjectableDescriptor} defined for the {@link Dependency}.
         */
        private final InjectableDescriptor injectableDescriptor;

        /**
         * Constructs an {@link AbstractResolvable}.
         *
         * @param requiredBy           the {@link Optional} {@link Resolvable} that requires this {@link Resolvable}
         * @param dependency           the {@link Dependency}
         * @param injectableDescriptor the {@link InjectableDescriptor} for the {@link Dependency}
         */
        protected AbstractResolvable(final Optional<Resolvable<?>> requiredBy,
                                     final Dependency dependency,
                                     final InjectableDescriptor injectableDescriptor) {

            this.requiredBy = requiredBy == null ? Optional.empty() : requiredBy;
            this.dependency = Objects.requireNonNull(dependency, "The Dependency must not be null");
            this.injectableDescriptor = Objects
                .requireNonNull(injectableDescriptor, "The InjectableDescriptor must not be null");
        }

        @Override
        public Optional<Resolvable<?>> requiredBy() {
            return this.requiredBy;
        }

        @Override
        public Dependency dependency() {
            return this.dependency;
        }

        @Override
        public InjectableDescriptor injectableDescriptor() {
            return this.injectableDescriptor;
        }

        /**
         * Resolves the {@link Dependency}s using the {@link Context}.
         *
         * @param dependencies the {@link Dependency}
         * @return an {@link IdentityHashMap} containing the resolved {@link Dependency}s
         */
        protected IdentityHashMap<Dependency, Object> resolveDependencies(final Stream<Dependency> dependencies) {

            // Dependencies are requiredBy this Resolvable
            final var requiredBy = Optional.<Resolvable<?>>of(this);

            // resolve the dependencies
            final var resolvedDependencies = new IdentityHashMap<Dependency, Object>();

            dependencies.forEach(dependency -> {
                resolvedDependencies.put(dependency,
                    getValue(requiredBy, dependency)
                        .orElseThrow(() -> new UnsatisfiedDependencyException(dependency, buildRequiredByChain())));

                // Track 2: injectionPoint is null until the resolution path threads it through
                InjectionContext.this.bindingGraphContributor.contributeDependency(
                    InjectionContext.this.toBindingNode(
                        InjectionContext.this.bindingsByDependency.get(this.dependency())),
                    InjectionContext.this.toBindingNode(
                        InjectionContext.this.bindingsByDependency.get(dependency)),
                    new DependencyEdge(null, dependency));
            });

            return resolvedDependencies;
        }

        /**
         * Builds a human-readable "required by" chain from this {@link Resolvable} up to the root, suitable for
         * inclusion in an {@link UnsatisfiedDependencyException} message.
         *
         * @return a multi-line string such as {@code "\n  required by Foo\n  required by Bar"}, or an empty string if
         * there are no requesters
         */
        private String buildRequiredByChain() {
            final var sb = new StringBuilder();
            var current = Optional.<Resolvable<?>>of(this);
            while (current.isPresent()) {
                sb.append("\n  required by ").append(current.get().dependency());
                current = current.get().requiredBy();
            }
            return sb.toString();
        }

        /**
         * Perform {@link FieldInjectionPoint} and {@link MethodInjectionPoint} injection into the specified
         * injectable {@link Object}.
         *
         * @param object               the {@link Object}
         * @param resolvedDependencies the resolved {@link Dependency}s
         */
        protected T inject(final T object,
                           final IdentityHashMap<Dependency, Object> resolvedDependencies) {

            this.injectableDescriptor.injectionPoints()
                .filter(injectionPoint -> !(injectionPoint instanceof ConstructorInjectionPoint))
                .forEach(injectionPoint -> {
                    // resolve the values for injection
                    final Object[] values = injectionPoint
                        .dependencies()
                        .map(resolvedDependencies::get)
                        .toArray();

                    injectionPoint.inject(object, values);
                });

            // invoke the @PostInject methods
            this.injectableDescriptor.postInjectionMethods()
                .map(methodDescriptor -> methodDescriptor.getTrait(MethodType.class).orElse(null))
                .filter(Objects::nonNull)
                .map(MethodType::method)
                .forEach(method -> {
                    try {
                        method.setAccessible(true);
                        method.invoke(object);
                    } catch (final IllegalAccessException | InvocationTargetException e) {
                        throw new InjectionException(
                            "Invoking @PostInject method " + method + " on " + object.getClass(), e);
                    }
                });

            return object;
        }
    }

    /**
     * Represents an {@link Object} upon which {@link Dependency}s need to be resolved through resolving
     * {@link FieldInjectionPoint}s and {@link MethodInjectionPoint}s.   As the {@link Object} already exists,
     * there's no need for resolving a {@link ConstructorInjectionPoint}.
     *
     * @param <T> the type of the {@link Object}
     */
    private class ResolvableObject<T>
        extends AbstractResolvable<T> {

        /**
         * The {@link Object} into which injection is to occur.
         */
        private final T object;

        /**
         * The {@link Dependency}s to be resolved for the {@link Object}
         */
        private final ArrayList<Dependency> dependenciesToBeResolved;

        /**
         * Constructs a {@link ResolvableObject}.
         *
         * @param dependency the {@link Dependency} for which the {@link Object} is being resolved
         * @param object     the {@link Object} to resolve
         */
        ResolvableObject(final Dependency dependency,
                         final T object) {

            super(
                Optional.empty(),
                dependency,
                InjectionContext.this.injectionFramework
                    .getInjectableDescriptor(
                        Objects.requireNonNull(object, "The Object must not be null")
                            .getClass()));

            this.object = object;
            this.dependenciesToBeResolved = new ArrayList<>();

            injectableDescriptor()
                .injectionPoints()
                .filter(injectionPoint -> !(injectionPoint instanceof ConstructorInjectionPoint))
                .flatMap(InjectionPoint::dependencies)
                .forEach(this.dependenciesToBeResolved::add);
        }

        @Override
        public T resolve()
            throws InjectionException {

            // return immediately when the object is not injectable
            if (!injectableDescriptor().isInjectable()) {
                return this.object;
            }

            // resolve the dependencies
            final var resolvedDependencies = resolveDependencies(this.dependenciesToBeResolved.stream());

            // perform injection using the resolved dependencies
            return inject(this.object, resolvedDependencies);
        }
    }

    /**
     * Represents a concrete {@link Class} to be instantiated either using the available
     * {@link ConstructorInjectionPoint} (thus constructor injection) or the {@code default} constructor, after which
     * remaining {@link Dependency}s will be injected through resolving {@link FieldInjectionPoint}s and
     * {@link MethodInjectionPoint}s.
     *
     * @param <T> the type of the {@link Object}
     */
    private class ResolvableClass<T>
        extends AbstractResolvable<T> {

        /**
         * The concrete {@link Class} to be instantiated and injected.
         */
        private final Class<? extends T> concreteClass;

        /**
         * The {@link Dependency}s to be resolved for the {@link Object}
         */
        private final ArrayList<Dependency> dependenciesToBeResolved;

        /**
         * The {@link Optional} {@link ConstructorInjectionPoint} to use for constructing the concrete {@link Class}.
         */
        private final Optional<ConstructorInjectionPoint> constructorInjectionPoint;

        /**
         * Constructs a {@link ResolvableClass}.
         *
         * @param dependency    the {@link Dependency} for which the {@link Object} is being resolved
         * @param requiredBy    the {@link Optional} {@link Resolvable} that requires this {@link Resolvable}
         * @param concreteClass the concrete {@link Class} to instantiate
         */
        ResolvableClass(final Optional<Resolvable<?>> requiredBy,
                        final Dependency dependency,
                        final Class<? extends T> concreteClass) {

            super(requiredBy,
                dependency,
                InjectionContext.this.injectionFramework
                    .getInjectableDescriptor(
                        Objects.requireNonNull(concreteClass, "The concrete Class must not be null")));

            // confirm none of the requireBy Resolvables are for the same Dependency (if we do, we have a cycle)
            requiredBy.ifPresent(resolvable -> {
                var current = resolvable;
                while (current != null) {
                    if (current.dependency().equals(dependency)) {
                        throw new CyclicDependencyException(
                            current.dependency(),
                            resolvable.dependency());
                    }
                    current = current.requiredBy().orElse(null);
                }
            });

            this.concreteClass = Objects.requireNonNull(concreteClass, "The concrete Class must not be null");
            this.dependenciesToBeResolved = new ArrayList<>();

            // determine the InjectionPoints that need resolving and their associated Dependencies
            final var constructorInjectionPoint = Lazy.<ConstructorInjectionPoint>empty();

            injectableDescriptor()
                .injectionPoints()
                .peek(injectionPoint -> injectionPoint
                    .dependencies()
                    .forEach(this.dependenciesToBeResolved::add))
                .forEach(injectionPoint -> {
                    // capture the ConstructorInjectionPoint to later use for construction
                    if (injectionPoint instanceof ConstructorInjectionPoint constructor) {
                        constructorInjectionPoint.set(constructor);
                    }
                });

            // retain the captured ConstructorInjectionPoint
            this.constructorInjectionPoint = constructorInjectionPoint.optional();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T resolve()
            throws InjectionException {

            // resolve the dependencies
            final var resolvedDependencies = resolveDependencies(this.dependenciesToBeResolved.stream());

            // construct the object
            final var object = this.constructorInjectionPoint
                .map(constructorInjectionPoint -> {
                    // attempt to instantiate the object using the resolved dependencies

                    // resolve values for injection
                    final Object[] values = constructorInjectionPoint.dependencies()
                        .map(resolvedDependencies::get)
                        .toArray();

                    // create the instance
                    return (T) constructorInjectionPoint.inject(null, values);
                })
                .orElseGet(() -> {
                    // locate and use the default constructor
                    for (final Constructor<?> constructor : this.concreteClass.getDeclaredConstructors()) {
                        if (constructor.getParameterCount() == 0) {
                            try {
                                // ensure the constructor is accessible
                                // (it may not be if it's an internal / private class we're creating)
                                constructor.setAccessible(true);

                                // create the instance
                                return (T) constructor.newInstance();
                            } catch (final InvocationTargetException
                                           | InstantiationException
                                           | IllegalAccessException e) {
                                throw new UnsatisfiedDependencyException(
                                    dependency(),
                                    "Failed to instantiate with default no-args constructor", e);
                            }
                        }
                    }

                    throw new UnsatisfiedDependencyException(dependency(), "Failed to locate no-args constructor");
                });

            // perform injection using the resolved dependencies
            return inject(object, resolvedDependencies);
        }
    }

    /**
     * Accumulates suppliers for a multibinding of element type {@code T}.
     *
     * @param <T> the element type
     */
    private static class MultiBindingEntry<T> {

        private final CopyOnWriteArrayList<Supplier<T>> suppliers = new CopyOnWriteArrayList<>();

        void addValue(final T value) {
            this.suppliers.add(() -> value);
        }

        void addSupplier(final Supplier<T> supplier) {
            this.suppliers.add(supplier);
        }

        Set<T> buildSet() {
            return this.suppliers.stream()
                .map(Supplier::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
