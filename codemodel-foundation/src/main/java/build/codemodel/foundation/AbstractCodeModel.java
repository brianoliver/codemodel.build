package build.codemodel.foundation;

/*-
 * #%L
 * Code Model Foundation
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

import build.base.foundation.iterator.Iterators;
import build.base.marshalling.Marshal;
import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Out;
import build.base.marshalling.Unmarshal;
import build.base.mereology.HeapBasedCompositeIndex;
import build.base.query.Index;
import build.base.query.Match;
import build.codemodel.foundation.descriptor.ModuleDescriptor;
import build.codemodel.foundation.descriptor.NamespaceDescriptor;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.Traitable;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.Namespace;
import build.codemodel.foundation.naming.TypeName;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static build.base.foundation.iterator.Iterators.concat;

/**
 * An abstract {@link CodeModel} implementation.
 *
 * @author brian.oliver
 * @since Mar-2024
 */
public abstract class AbstractCodeModel
    implements CodeModel, Traitable {

    /**
     * The {@link NameProvider} used to establish the {@link CodeModel}.
     */
    private final NameProvider nameProvider;

    /**
     * The {@link TypeDescriptor}s by {@link TypeName}.
     */
    private final ConcurrentHashMap<TypeName, TypeDescriptor> typeDescriptors;

    /**
     * The {@link ModuleDescriptor}s by {@link ModuleName}.
     */
    private final ConcurrentHashMap<ModuleName, ModuleDescriptor> moduleDescriptors;

    /**
     * The {@link NamespaceDescriptor}s by {@link Namespace}.
     */
    private final ConcurrentHashMap<Namespace, NamespaceDescriptor> namespaceDescriptors;

    /**
     * The {@link Traitable} for the {@link CodeModel}.
     */
    private final Traitable traitable;

    /**
     * A {@link HeapBasedCompositeIndex} for the {@link CodeModel}.
     */
    private final HeapBasedCompositeIndex index;

    /**
     * Constructs an empty {@link AbstractCodeModel}.
     *
     * @param nameProvider the {@link NameProvider}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected AbstractCodeModel(final NameProvider nameProvider) {

        this.nameProvider = Objects.requireNonNull(nameProvider, "The NameProvider must not be null");

        this.typeDescriptors = new ConcurrentHashMap<>();
        this.moduleDescriptors = new ConcurrentHashMap<>();
        this.namespaceDescriptors = new ConcurrentHashMap<>();

        this.traitable = new CodeModelTraitable(this, this);

        this.index = new HeapBasedCompositeIndex(this);

        // include the CodeModel and its implementation class as a matchable
        this.index.add(CodeModel.class, this);
        this.index.add((Class) this.getClass(), this);

        // index @Indexable fields on the model itself so fresh and unmarshalled models are consistent
        this.index.index(this);
    }

    /**
     * {@link Unmarshal}s an {@link AbstractCodeModel} using a {@link Marshaller}.
     *
     * @param nameProvider         the {@link NameProvider}
     * @param marshaller           the {@link Marshaller}
     * @param traits               the {@link Traitable}
     * @param typeDescriptors      the {@link Stream} of {@link Marshalled} {@link TypeDescriptor}s
     * @param moduleDescriptors    the {@link Stream} of {@link Marshalled} {@link ModuleDescriptor}s
     * @param namespaceDescriptors the {@link Stream} of {@link Marshalled} {@link NamespaceDescriptor}s
     */
    protected AbstractCodeModel(final NameProvider nameProvider,
                                final Marshaller marshaller,
                                final Stream<Marshalled<Trait>> traits,
                                final Stream<Marshalled<TypeDescriptor>> typeDescriptors,
                                final Stream<Marshalled<ModuleDescriptor>> moduleDescriptors,
                                final Stream<Marshalled<NamespaceDescriptor>> namespaceDescriptors) {

        this(nameProvider);

        // include this CodeModel in the Marshaller so it can be used when unmarshalling
        marshaller.bind(CodeModel.class).to(this);

        // include this concrete CodeModel in the Marshaller so it can be used when unmarshalling
        marshaller.bind(this);

        // allow a subclass to initialize itself with the Marshaller
        prepareForUnmarshalling();

        traits.map(marshaller::unmarshal)
            .forEach(this::addTrait);

        typeDescriptors.map(marshaller::unmarshal)
            .forEach(typeDescriptor -> {
                this.typeDescriptors.put(typeDescriptor.typeName(), typeDescriptor);
                onCreatedTypeDescriptor(typeDescriptor);
                this.index.index(typeDescriptor);
            });

        moduleDescriptors.map(marshaller::unmarshal)
            .forEach(moduleDescriptor -> {
                this.moduleDescriptors.put(moduleDescriptor.moduleName(), moduleDescriptor);
                this.index.index(moduleDescriptor);
            });

        namespaceDescriptors.map(marshaller::unmarshal)
            .forEach(namespaceDescriptor -> {
                this.namespaceDescriptors.put(namespaceDescriptor.namespace(), namespaceDescriptor);
                this.index.index(namespaceDescriptor);
            });
    }

    /**
     * Allow subclasses to perform any initialization prior to unmarshalling.
     */
    protected void prepareForUnmarshalling() {
    }

    /**
     * {@link Marshal}s an {@link AbstractCodeModel} so it can be {@link Marshal}led.
     *
     * @param marshaller           the {@link Marshaller} to use for marshalling
     * @param traits               the {@link Marshalled} {@link Trait}s of the {@link CodeModel} itself
     * @param typeDescriptors      the {@link Stream} of marshallable {@link TypeDescriptor}s
     * @param moduleDescriptors    the {@link Stream} of marshallable {@link ModuleDescriptor}s
     * @param namespaceDescriptors the {@link Stream} of marshallable {@link NamespaceDescriptor}s
     */
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Stream<Marshalled<TypeDescriptor>>> typeDescriptors,
                           final Out<Stream<Marshalled<ModuleDescriptor>>> moduleDescriptors,
                           final Out<Stream<Marshalled<NamespaceDescriptor>>> namespaceDescriptors) {

        // only return Marshalled<Trait>s that are marshallable
        traits.set(this.traitable.traits()
            .filter(trait -> marshaller.isMarshallable(trait.getClass()))
            .map(marshaller::marshal));

        // only return Marshalled<TypeDescriptor>s that are marshallable
        typeDescriptors.set(this.typeDescriptors()
            .filter(typeDescriptor -> marshaller.isMarshallable(typeDescriptor.getClass()))
            .map(marshaller::marshal));

        // only return Marshalled<ModuleDescriptor>s that are marshallable
        moduleDescriptors.set(this.moduleDescriptors()
            .filter(moduleDescriptor -> marshaller.isMarshallable(moduleDescriptor.getClass()))
            .map(marshaller::marshal));

        // only return Marshalled<NamespaceDescriptor>s that are marshallable
        namespaceDescriptors.set(this.namespaceDescriptors()
            .filter(namespaceDescriptor -> marshaller.isMarshallable(namespaceDescriptor.getClass()))
            .map(marshaller::marshal));
    }

    @Override
    public NameProvider getNameProvider() {
        return this.nameProvider;
    }

    /**
     * Invoked when a {@link TypeDescriptor} is created for the first time.
     *
     * @param typeDescriptor the {@link TypeDescriptor}
     */
    protected void onCreatedTypeDescriptor(final TypeDescriptor typeDescriptor) {
        // by default, nothing to do
    }

    /**
     * Returns an {@link Iterator} over the parts of this {@link CodeModel}.
     * <p>
     * When {@code type} is {@link Object}, the iterator covers all {@link TypeDescriptor}s,
     * {@link ModuleDescriptor}s, {@link NamespaceDescriptor}s, and {@link Trait}s — but <em>not</em>
     * the {@link CodeModel} itself. This means {@link #parts()} and {@link #composition()} do not
     * include {@code this}. To include the model itself, use {@code traverse().reflexive(true)}.
     * <p>
     * When {@code type} is assignable from {@link CodeModel}, the iterator yields only {@code this}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Iterator<T> iterator(final Class<T> type) {
        if (type == null) {
            return Iterators.empty();
        }

        if (type == Object.class) {
            return (Iterator<T>) concat(
                this.typeDescriptors.values().iterator(),
                this.moduleDescriptors.values().iterator(),
                this.namespaceDescriptors.values().iterator(),
                this.traitable.iterator(type));
        } else if (CodeModel.class.isAssignableFrom(type)) {
            return (Iterator<T>) Iterators.of(this);
        } else if (TypeDescriptor.class.isAssignableFrom(type)) {
            return (Iterator<T>) this.typeDescriptors.values().iterator();
        } else if (ModuleDescriptor.class.isAssignableFrom(type)) {
            return (Iterator<T>) this.moduleDescriptors.values().iterator();
        } else if (NamespaceDescriptor.class.isAssignableFrom(type)) {
            return (Iterator<T>) this.namespaceDescriptors.values().iterator();
        } else {
            return this.traitable.iterator(type);
        }
    }

    /**
     * Provides subclass access to the {@link Index}.
     *
     * @return the {@link Index}
     */
    protected Index index() {
        return this.index;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends TypeDescriptor> T createTypeDescriptor(final TypeName typeName,
                                                                   final BiFunction<? super CodeModel, ? super TypeName, T> supplier,
                                                                   final Consumer<? super T> populate) {

        Objects.requireNonNull(typeName, "The TypeName must not be null");
        Objects.requireNonNull(supplier, "The Supplier must not be null");
        Objects.requireNonNull(populate, "The populate Consumer must not be null");

        return (T) this.typeDescriptors.computeIfAbsent(typeName, _ -> {
            final var descriptor = Objects.requireNonNull(
                supplier.apply(this, typeName),
                "The TypeDescriptor Supplier produced a null value");

            populate.accept(descriptor);

            onCreatedTypeDescriptor(descriptor);
            this.index.index(descriptor);

            return descriptor;
        });
    }

    @Override
    public Optional<TypeDescriptor> getTypeDescriptor(final TypeName typeName) {
        return Optional.ofNullable(this.typeDescriptors.get(typeName));
    }

    /**
     * Removes a {@link TypeDescriptor} from this model and unindexes it.
     * Has no effect if the descriptor is not registered.
     *
     * @param descriptor the {@link TypeDescriptor} to remove
     */
    public void removeTypeDescriptor(final TypeDescriptor descriptor) {
        if (this.typeDescriptors.remove(descriptor.typeName(), descriptor)) {
            this.index.unindex(descriptor);
        }
    }

    /**
     * Removes a {@link ModuleDescriptor} from this model and unindexes it.
     * Has no effect if the descriptor is not registered.
     *
     * @param descriptor the {@link ModuleDescriptor} to remove
     */
    public void removeModuleDescriptor(final ModuleDescriptor descriptor) {
        if (this.moduleDescriptors.remove(descriptor.moduleName(), descriptor)) {
            this.index.unindex(descriptor);
        }
    }

    /**
     * Removes a {@link NamespaceDescriptor} from this model and unindexes it.
     * Has no effect if the descriptor is not registered.
     *
     * @param descriptor the {@link NamespaceDescriptor} to remove
     */
    public void removeNamespaceDescriptor(final NamespaceDescriptor descriptor) {
        if (this.namespaceDescriptors.remove(descriptor.namespace(), descriptor)) {
            this.index.unindex(descriptor);
        }
    }

    @Override
    public Stream<TypeDescriptor> typeDescriptors() {
        return this.typeDescriptors.values().stream();
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <M extends ModuleDescriptor> M createModuleDescriptor(final ModuleName moduleName,
                                                                       final BiFunction<? super CodeModel, ? super ModuleName, M> supplier,
                                                                       final Consumer<? super M> populate) {

        Objects.requireNonNull(moduleName, "The ModuleName must not be null");
        Objects.requireNonNull(supplier, "The Supplier must not be null");
        Objects.requireNonNull(populate, "The populate Consumer must not be null");

        return (M) this.moduleDescriptors.computeIfAbsent(moduleName, _ -> {
            final var descriptor = Objects.requireNonNull(
                supplier.apply(this, moduleName),
                "The ModuleDescriptor Supplier produced a null value");

            populate.accept(descriptor);

            this.index.index(descriptor);

            return descriptor;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <N extends NamespaceDescriptor> N createNamespaceDescriptor(final Namespace namespace,
                                                                             final BiFunction<? super CodeModel, ? super Namespace, N> supplier,
                                                                             final Consumer<? super N> populate) {

        Objects.requireNonNull(namespace, "The Namespace must not be null");
        Objects.requireNonNull(supplier, "The Supplier must not be null");
        Objects.requireNonNull(populate, "The populate Consumer must not be null");

        return (N) this.namespaceDescriptors.computeIfAbsent(namespace, _ -> {
            final var descriptor = Objects.requireNonNull(
                supplier.apply(this, namespace),
                "The NamespaceDescriptor Supplier produced a null value");

            populate.accept(descriptor);

            this.index.index(descriptor);

            return descriptor;
        });
    }

    @Override
    public Stream<ModuleDescriptor> moduleDescriptors() {
        return this.moduleDescriptors.values().stream();
    }

    @Override
    public Optional<ModuleDescriptor> getModuleDescriptor(final ModuleName moduleName) {
        return Optional.ofNullable(this.moduleDescriptors.get(moduleName));
    }

    @Override
    public Stream<NamespaceDescriptor> namespaceDescriptors() {
        return this.namespaceDescriptors.values().stream();
    }

    @Override
    public Optional<NamespaceDescriptor> getNamespaceDescriptor(final Namespace namespace) {
        return Optional.ofNullable(this.namespaceDescriptors.get(namespace));
    }

    @Override
    public CodeModel codeModel() {
        return this;
    }

    @Override
    public <C extends Traitable, T extends Trait> T createTrait(final Function<C, T> traitSupplier) {
        return this.traitable.createTrait(traitSupplier);
    }

    @Override
    public <T extends Trait> boolean removeTrait(final T trait) {
        return this.traitable.removeTrait(trait);
    }

    @Override
    public <C extends Traitable, T extends Trait> Optional<T> computeIfAbsent(final Class<T> traitClass,
                                                                              final Function<C, T> function) {

        return this.traitable.computeIfAbsent(traitClass, function);
    }

    @Override
    public <C extends Traitable, T extends Trait> Optional<T> computeIfPresent(final Class<T> traitClass,
                                                                               final BiFunction<C, T, T> biFunction) {

        return this.traitable.computeIfPresent(traitClass, biFunction);
    }

    @Override
    public Stream<Trait> traits() {
        return this.traitable.traits();
    }

    @Override
    public <T> Stream<T> traits(final Class<T> requiredClass) {
        return this.traitable.traits(requiredClass);
    }

    @Override
    public Stream<Object> traits(final Class<?>... classes) {
        return this.traitable.traits(classes);
    }

    @Override
    public <T> Optional<T> getTrait(final Class<T> requiredClass)
        throws IllegalStateException {

        return this.traitable.getTrait(requiredClass);
    }

    @Override
    public <T> T trait(final Class<T> requiredClass)
        throws IllegalStateException {

        return this.traitable.trait(requiredClass);
    }

    @Override
    public final Traitable createTraitable(final Traitable object,
                                           final Consumer<? super Traitable> populate) {

        Objects.requireNonNull(object, "The object must not be null");
        Objects.requireNonNull(populate, "The populate Consumer must not be null");

        final var traitable = new CodeModelTraitable(this, object);

        populate.accept(traitable);

        return traitable;
    }

    @Override
    public boolean hasTraits() {
        return this.traitable.hasTraits();
    }

    @Override
    public boolean hasTrait(final Class<?> requiredClass) {
        return this.traitable.hasTrait(requiredClass);
    }

    @Override
    public <M> Match<M> match(final Class<M> matchableClass) {
        return this.index.match(matchableClass);
    }
}
