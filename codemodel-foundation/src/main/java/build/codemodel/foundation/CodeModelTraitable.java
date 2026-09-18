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

import build.base.foundation.Lazy;
import build.base.foundation.iterator.Iterators;
import build.codemodel.foundation.descriptor.NonSingular;
import build.codemodel.foundation.descriptor.Singular;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.TraitAware;
import build.codemodel.foundation.descriptor.Traitable;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The internal {@link Traitable} for an {@link Object} that was established by an {@link AbstractCodeModel}.
 *
 * @author brian.oliver
 * @since Mar-2024
 */
class CodeModelTraitable
    implements Traitable {

    /**
     * The {@link AbstractCodeModel} that established and in which the {@link Traitable} is defined.
     */
    private final AbstractCodeModel codeModel;

    /**
     * The {@link Traitable} {@link Object} for which the {@link Traitable} has been created.
     */
    private final Traitable object;

    /**
     * The {@link Trait}s arranged by registration {@link Class}, allowing for fast lookup.
     */
    private final ConcurrentHashMap<Class<?>, Set<Trait>> traitsByClass;

    /**
     * The {@link Trait}s arranged by {@link Singular} registration {@link Class}.
     */
    private final ConcurrentHashMap<Class<?>, Trait> singularTraitsByClass;

    /**
     * The registration {@link Class}es of {@link Trait}s that have been determined to be {@link Singular}.
     */
    private static final Set<Class<? extends Trait>> singularTraitClasses;

    /**
     * The registration {@link Class}es of {@link Trait}s that have been determined to be non-{@link Singular}.
     */
    private static final Set<Class<? extends Trait>> nonSingularTraitClasses;

    /**
     * The registration {@link Class} of {@link Trait} by {@link Class} of {@link Trait}.
     */
    private static final ConcurrentHashMap<Class<? extends Trait>, Class<? extends Trait>> registrationTraitClassByTraitClass;

    /**
     * Constructs a {@link CodeModelTraitable} for the specified {@link Object}.
     *
     * @param codeModel the {@link AbstractCodeModel} that established the {@link Traitable}
     * @param object    the {@link Traitable} {@link Object}
     */
    CodeModelTraitable(final AbstractCodeModel codeModel,
                       final Traitable object) {

        this.codeModel = Objects.requireNonNull(codeModel, "The CodeModel must not be null");
        this.object = Objects.requireNonNull(object, "The object must not be null");
        this.traitsByClass = new ConcurrentHashMap<>();
        this.singularTraitsByClass = new ConcurrentHashMap<>();
    }

    @Override
    public CodeModel codeModel() {
        return this.codeModel;
    }

    /**
     * Notifies the {@link #object} (when it's {@link TraitAware}) and indexes a {@link Trait} that was just added.
     * <p>
     * Must only be called outside of any {@link ConcurrentHashMap#compute} remapping function, so that arbitrary
     * {@link TraitAware} and index work never runs while a {@link ConcurrentHashMap} bin lock is held.
     *
     * @param trait the {@link Trait} that was added
     */
    private void notifyAdded(final Trait trait) {
        if (this.object instanceof TraitAware traitAware) {
            traitAware.onAddedTrait(trait);
        }

        this.codeModel.index().index(trait);
    }

    /**
     * Unindexes and notifies the {@link #object} (when it's {@link TraitAware}) of a {@link Trait} that was just
     * removed.
     * <p>
     * Must only be called outside of any {@link ConcurrentHashMap#compute} remapping function, so that arbitrary
     * {@link TraitAware} and index work never runs while a {@link ConcurrentHashMap} bin lock is held.
     *
     * @param trait the {@link Trait} that was removed
     */
    private void notifyRemoved(final Trait trait) {
        this.codeModel.index().unindex(trait);

        if (this.object instanceof TraitAware traitAware) {
            traitAware.onRemovedTrait(trait);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Iterator<T> iterator(final Class<T> type) {
        if (type == null) {
            return Iterators.empty();
        }

        if (Trait.class.isAssignableFrom(type)) {
            final var registrationClass = getRegistrationClass((Class<? extends Trait>) type);

            final var trait = this.singularTraitsByClass.get(registrationClass);

            if (trait != null) {
                return Iterators.ofNullable((T) trait);
            }

            // attempt to find the precise class first
            final var traits = this.traitsByClass.get(registrationClass);

            if (traits != null) {
                return (Iterator<T>) traits.iterator();
            }
        }

        // TODO: when the Type is an Object, we don't need to use an InstanceOfIterator

        // ouch... the required class isn't a registered class, so search them all!
        return Iterators.isInstanceOf(
            Iterators.concat(
                this.singularTraitsByClass.values().iterator(),
                Iterators.flatten(
                    Iterators.map(
                        this.traitsByClass.values().iterator(),
                        Set::iterator))),
            type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Traitable, T extends Trait> T createTrait(final Function<C, T> traitSupplier) {

        Objects.requireNonNull(traitSupplier, "The Trait Supplier function must not be null");

        final var trait = traitSupplier.apply((C) this.object);
        final var registrationClass = getRegistrationClass(trait.getClass());

        if (isSingular(registrationClass)) {
            final var previous = this.singularTraitsByClass.putIfAbsent(registrationClass, trait);
            if (previous != null) {
                throw new IllegalArgumentException("Trait [" + trait.getClass()
                    + "] with registration type [" + registrationClass + "] already exists");
            }

            notifyAdded(trait);
        } else {
            final var added = Lazy.<Trait>empty();
            this.traitsByClass.compute(registrationClass, (_, existing) -> {
                final var list = existing == null ? ConcurrentHashMap.<Trait>newKeySet() : existing;

                // add if the trait is not already present
                if (list.add(trait)) {
                    added.set(trait);
                }

                return list;
            });
            added.ifPresent(this::notifyAdded);
        }

        this.codeModel.index().reindexDynamic(this.object);
        return trait;
    }

    @Override
    public <T extends Trait> boolean removeTrait(final T trait) {
        if (trait == null) {
            return false;
        }

        final var removedTrait = Lazy.<T>empty();

        final var registrationClass = getRegistrationClass(trait.getClass());

        if (isSingular(registrationClass)) {
            this.singularTraitsByClass.compute(registrationClass, (_, existing) -> {
                if (existing == null) {
                    return null;
                }

                // ensure the existing is the same!
                if (trait == existing) {
                    removedTrait.set(trait);
                    return null;
                } else {
                    return existing;
                }
            });
        } else {
            this.traitsByClass.compute(registrationClass, (_, existing) -> {
                if (existing == null) {
                    return null;
                }

                if (existing.remove(trait)) {
                    removedTrait.set(trait);
                }

                return existing.isEmpty() ? null : existing;
            });
        }

        removedTrait.ifPresent(this::notifyRemoved);

        this.codeModel.index().reindexDynamic(this.object);
        return removedTrait.isPresent();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Traitable, T extends Trait> Optional<T> computeIfAbsent(final Class<T> traitClass,
                                                                              final Function<C, T> function) {

        Objects.requireNonNull(traitClass, "The Trait Class must not be null");
        Objects.requireNonNull(function, "The Function must not be null");

        final var traitable = (C) this.object;
        final var lazyTrait = Lazy.<T>empty();
        final var registrationClass = getRegistrationClass(traitClass);
        final var created = Lazy.<T>empty();

        if (isSingular(registrationClass)) {
            this.singularTraitsByClass.compute(registrationClass, (_, existing) -> {
                if (existing == null) {
                    final var newTrait = function.apply(traitable);

                    if (newTrait == null) {
                        return null;
                    }

                    lazyTrait.set(newTrait);
                    created.set(newTrait);

                    return newTrait;
                } else {
                    lazyTrait.set((T) existing);

                    return existing;
                }
            });
        } else {
            this.traitsByClass.compute(registrationClass, (_, existing) -> {
                if (existing == null || existing.isEmpty()) {
                    final var trait = function.apply(traitable);

                    if (trait == null) {
                        return null;
                    }

                    final var list = ConcurrentHashMap.<Trait>newKeySet();
                    list.add(trait);
                    lazyTrait.set(trait);
                    created.set(trait);

                    return list;
                }

                if (existing.size() == 1) {
                    lazyTrait.set((T) existing.iterator().next());
                    return existing;
                }

                throw new IllegalArgumentException(
                    "Attempted to create a single trait of type [" + traitClass + "], with registration type ["
                        + registrationClass + "], but there are " + existing.size());
            });
        }

        created.ifPresent(this::notifyAdded);

        this.codeModel.index().reindexDynamic(this.object);
        return lazyTrait.optional();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Traitable, T extends Trait> Optional<T> computeIfPresent(final Class<T> traitClass,
                                                                               final BiFunction<C, T, T> biFunction) {

        Objects.requireNonNull(traitClass, "The Trait Class must not be null");
        Objects.requireNonNull(biFunction, "The BiFunction must not be null");

        final var traitable = (C) this.object;
        final var lazyTrait = Lazy.<T>empty();
        final var registrationClass = getRegistrationClass(traitClass);

        final var removedTrait = Lazy.<T>empty();
        final var addedTrait = Lazy.<T>empty();

        if (isSingular(registrationClass)) {
            this.singularTraitsByClass.compute(registrationClass, (_, existing) -> {
                if (existing == null) {
                    return null;
                }

                final var newTrait = biFunction.apply(traitable, (T) existing);

                removedTrait.set((T) existing);

                if (newTrait != null) {
                    lazyTrait.set(newTrait);
                    addedTrait.set(newTrait);
                }

                return newTrait;
            });
        } else {
            this.traitsByClass.compute(registrationClass, (_, existing) -> {
                if (existing == null || existing.isEmpty()) {
                    return existing;
                }

                if (existing.size() != 1) {
                    throw new IllegalArgumentException(
                        "Attempted to compute a single trait of type [" + traitClass + "], with registration type ["
                            + registrationClass + "], but there are " + existing.size());
                }

                final var existingTrait = existing.iterator().next();
                final var replacementTrait = biFunction.apply(traitable, (T) existingTrait);

                removedTrait.set((T) existingTrait);
                existing.remove(existingTrait);

                if (replacementTrait != null) {
                    existing.add(replacementTrait);
                    lazyTrait.set(replacementTrait);
                    addedTrait.set(replacementTrait);
                }

                return existing.isEmpty() ? null : existing;
            });
        }

        removedTrait.ifPresent(this::notifyRemoved);
        addedTrait.ifPresent(this::notifyAdded);

        this.codeModel.index().reindexDynamic(this.object);
        return lazyTrait.optional();
    }

    @Override
    public Stream<Trait> traits() {
        return Stream.concat(
            this.singularTraitsByClass.values().stream(),
            this.traitsByClass.values().stream()
                .flatMap(Set::stream));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Stream<T> traits(final Class<T> requiredClass) {

        if (requiredClass == null) {
            return Stream.empty();
        }

        if (Trait.class.isAssignableFrom(requiredClass)) {
            final var registrationClass = getRegistrationClass((Class<? extends Trait>) requiredClass);

            final var trait = this.singularTraitsByClass.get(registrationClass);
            if (trait != null) {
                return Stream.of((T) trait);
            }

            // attempt to find the precise class first
            final var traits = this.traitsByClass.get(registrationClass);

            if (traits != null) {
                return (Stream<T>) traits.stream();
            }
        }

        // ouch... the required class isn't a concrete class, so search them all!
        return getTraitsAssignableTo(requiredClass);
    }

    /**
     * Obtains the {@link Trait}s that are assignable to the specified {@link Class}.
     *
     * @param <T>           the type of {@link Class}
     * @param requiredClass the required {@link Class}
     * @return a {@link Stream} of {@link Trait}s assignable to the specified {@link Class}
     */
    private <T> Stream<T> getTraitsAssignableTo(final Class<T> requiredClass) {
        return traits()
            .filter(requiredClass::isInstance)
            .map(requiredClass::cast);
    }

    @Override
    public Stream<Object> traits(final Class<?>... classes) {
        if (classes == null || classes.length == 0) {
            return Stream.empty();
        }

        return Stream.of(classes)
            .flatMap(this::traits);
    }

    @SuppressWarnings("unchecked")
    private <T> T getTraitOrNull(final Class<T> requiredClass)
        throws IllegalArgumentException {

        if (requiredClass == null) {
            return null;
        }

        // when the required class is a Trait, we can short-circuit the lookup by using the registration class
        if (Trait.class.isAssignableFrom(requiredClass)) {
            final var registrationClass = getRegistrationClass((Class<? extends Trait>) requiredClass);

            final var trait = this.singularTraitsByClass.get(registrationClass);

            if (trait != null) {
                return requiredClass.isInstance(trait) ? (T) trait : null;
            }

            final var traits = this.traitsByClass.get(registrationClass);

            if (traits == null || traits.isEmpty()) {
                return null;
            }

            if (traits.size() > 1) {
                throw new IllegalArgumentException(
                    "Attempted to obtain a single trait of type " + registrationClass + " but there are "
                        + traits.size());
            }

            final var first = (T) traits.iterator().next();
            return requiredClass.isInstance(first) ? first : null;
        } else {
            // exhaustively search :(
            return this.singularTraitsByClass.values().stream()
                .filter(requiredClass::isInstance)
                .map(requiredClass::cast)
                .findFirst()
                .or(() -> this.traitsByClass.values().stream()
                    .filter(requiredClass::isInstance)
                    .map(requiredClass::cast)
                    .findFirst())
                .orElse(null);
        }
    }

    @Override
    public <T> Optional<T> getTrait(final Class<T> requiredClass)
        throws IllegalArgumentException {

        return Optional.ofNullable(getTraitOrNull(requiredClass));
    }

    public <T> T trait(final Class<T> requiredClass)
        throws IllegalArgumentException {

        final var trait = getTraitOrNull(requiredClass);

        if (trait != null) {
            return trait;
        }
        throw new NoSuchElementException("No value present");
    }

    @Override
    public boolean hasTraits() {
        return !this.singularTraitsByClass.isEmpty() || !this.traitsByClass.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasTrait(final Class<?> requiredClass) {
        if (requiredClass == null) {
            return false;
        }

        if (Trait.class.isAssignableFrom(requiredClass)) {
            final var registrationClass = getRegistrationClass((Class<? extends Trait>) requiredClass);

            final var trait = this.singularTraitsByClass.get(registrationClass);
            if (trait != null) {
                return true;
            }

            // attempt to find the precise class first
            final var traits = this.traitsByClass.get(registrationClass);

            if (traits != null) {
                return true;
            }
            // if the registration class is the same as the required class and we've reached this point,
            // then we know that the trait is not present.
            if (registrationClass.equals(requiredClass)) {
                return false;
            }
        }

        // ouch... the required class isn't a concrete class, so search them all!
        return getTraitsAssignableTo(requiredClass)
            .findFirst()
            .isPresent();
    }

    @Override
    public int hashCode() {
        return this.object.hashCode();
    }

    @Override
    public String toString() {
        return this.traitsByClass.isEmpty()
            ? ""
            : this.traitsByClass.values().stream()
            .flatMap(Set::stream)
            .map(Object::toString)
            .collect(Collectors.joining(",", " [", "]"));
    }

    /**
     * Determines if the specified registration {@link Class} of {@link Trait} is {@link Singular}.
     *
     * @param registrationClass the {@link Class} of {@link Trait}
     * @return {@code true} if the specified registration {@link Class} of {@link Trait} is {@link Singular},
     * {@code false} otherwise
     */
    public static boolean isSingular(final Class<? extends Trait> registrationClass) {
        if (singularTraitClasses.contains(registrationClass)) {
            return true;
        } else if (nonSingularTraitClasses.contains(registrationClass)) {
            return false;
        }

        final var singular = registrationClass.getAnnotation(Singular.class);
        if (singular == null) {
            nonSingularTraitClasses.add(registrationClass);
        } else {
            singularTraitClasses.add(registrationClass);
        }

        return singular != null;
    }

    /**
     * Determines the registration {@link Class} for a {@link Trait} in the class hierarchy for the specified
     * {@link Class} of {@link Trait}.
     * <p>
     * When a {@link Class} in the hierarchy for the specified {@link Class}
     * (ie: including itself and parent interfaces/classes) is <strong>not</strong> annotated with {@link Singular} or
     * {@link NonSingular}, the most immediate non-abstract, non-anonymous, non-synthetic {@link Class} of the specified
     * {@link Class} is returned (which is usually the said {@link Class} itself).
     * <p>
     * Otherwise, the first {@link Class} in the hierarchy that is annotated with {@link Singular} or
     * {@link NonSingular} is returned.
     *
     * @param traitClass the {@link Class} of the {@link Trait}
     * @return the registration {@link Class} of the {@link Class} of {@link Trait}
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends Trait> getRegistrationClass(final Class<? extends Trait> traitClass) {

        // attempt to use the previously determined registration class of trait
        final var previous = registrationTraitClassByTraitClass.get(traitClass);

        if (previous != null) {
            return previous;
        }

        // attempt to determine the registration class of trait, based on the provided trait class
        return registrationTraitClassByTraitClass.compute(traitClass, (_, existing) -> {

            // short-circuit when it's already registered
            if (existing != null) {
                return existing;
            }

            // we use a deque to perform class hierarchy searching (instead of using recursion)
            final var hierarchy = new ArrayDeque<Class<?>>();

            // initially we assume the registration class is the provided class of trait
            Class<?> registrationClass = traitClass;

            // we also want to keep track of the first non-abstract, non-anonymous and non-lambda class
            // (ie: concrete trait) that we find just in case we don't find a class annotated with @Singular
            Class<? extends Trait> concreteClass = null;

            while (registrationClass != null) {
                // determine if the class is a concrete  (non-abstract, non-anonymous and non-synthetic)
                if (concreteClass == null
                    && Trait.class.isAssignableFrom(registrationClass)
                    && isConcrete(registrationClass)) {

                    concreteClass = (Class<Trait>) registrationClass;
                }

                // determine if the class is annotated as @Singular or @NonSingular
                final var singular = registrationClass.getAnnotation(Singular.class);
                final var nonSingular = registrationClass.getAnnotation(NonSingular.class);

                if (singular != null && nonSingular != null) {
                    throw new IllegalStateException(
                        "Both @Singular and @NonSingular annotations are defined for " + registrationClass
                            + ". Only one may be defined");
                }
                if (singular == null && nonSingular == null) {
                    // push the super class (if there is one)
                    final Class<?> superClass = registrationClass.getSuperclass();

                    if (superClass != null
                        && !superClass.equals(Object.class)) {
                        hierarchy.push(superClass);
                    }

                    // push the interfaces to search onto the deque
                    for (final Class<?> interfaceClass : registrationClass.getInterfaces()) {
                        hierarchy.push(interfaceClass);
                    }
                } else {
                    // ensure that the registration class is a Trait
                    if (Trait.class.isAssignableFrom(registrationClass)) {

                        if (singular != null) {
                            // remember that the Trait and its registration class is @Singular
                            singularTraitClasses.add(traitClass);
                            singularTraitClasses.add((Class<? extends Trait>) registrationClass);
                        }

                        if (nonSingular != null) {
                            // remember that the Trait and its registration class is @NonSingular
                            nonSingularTraitClasses.add(traitClass);
                            nonSingularTraitClasses.add((Class<? extends Trait>) registrationClass);
                        }

                        return (Class<? extends Trait>) registrationClass;
                    } else {
                        // we ignore classes that use @Singular or @NonSingular which are not Traits
                    }
                }

                // we couldn't determine the @Singular or @NonSingular, so we try something else on the deque
                registrationClass = hierarchy.isEmpty() ? null : hierarchy.pop();
            }

            // when there is no @Singular or @NonSingular annotation, we return the concrete class
            return concreteClass == null ? traitClass : concreteClass;
        });
    }

    /**
     * Determines if a {@link Class} is concrete and may be instantiated.
     *
     * @param c the {@link Class}
     * @return {@code true} if the {@link Class} is concrete, otherwise {@code false}
     */
    private static boolean isConcrete(final Class<?> c) {
        return !Modifier.isAbstract(c.getModifiers())
            && !c.isInterface()
            && !c.isAnonymousClass() && !c.isSynthetic();
    }

    static {
        singularTraitClasses = ConcurrentHashMap.newKeySet();
        nonSingularTraitClasses = ConcurrentHashMap.newKeySet();
        registrationTraitClassByTraitClass = new ConcurrentHashMap<>();
    }
}
