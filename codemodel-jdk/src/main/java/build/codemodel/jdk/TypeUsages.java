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

import build.base.foundation.Introspection;
import build.base.foundation.Lazy;
import build.codemodel.foundation.naming.Namespace;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.ArrayTypeUsage;
import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.foundation.usage.SpecificTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.foundation.usage.TypeVariableUsage;
import build.codemodel.foundation.usage.WildcardTypeUsage;
import build.codemodel.jdk.descriptor.JDKTypeDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Helper methods for working with JDK-based {@link TypeUsage}s.
 *
 * @author brian.oliver
 * @since Jun-2024
 */
public final class TypeUsages {

    /**
     * The JDK primitive {@link Class}es, keyed by the bare name (e.g. {@code "int"}) that a primitive
     * {@link TypeName#name()} carries. Derived from {@link Introspection#primitives()} rather than hardcoded, so
     * it can't drift from the JDK's actual set of primitive types.
     */
    private static final Map<String, Class<?>> PRIMITIVE_CLASSES_BY_NAME = Introspection.primitives()
        .collect(Collectors.toUnmodifiableMap(Class::getName, Function.identity()));

    /**
     * Prevent instantiation
     */
    private TypeUsages() {
        // prevent instantiation
    }

    /**
     * Determines whether the specified {@link TypeName} represents one of the JDK primitive types, which are
     * modeled with a synthetic {@code java.lang} namespace despite not actually residing there.
     *
     * @param typeName the {@link TypeName}
     * @return {@code true} if the {@link TypeName} represents a primitive type, {@code false} otherwise
     */
    public static boolean isPrimitive(final TypeName typeName) {
        return typeName.namespace()
            .map(namespace -> "java.lang".equals(namespace.toString()))
            .orElse(false)
            && PRIMITIVE_CLASSES_BY_NAME.containsKey(typeName.name().toString());
    }

    /**
     * Determines if the specified {@link TypeUsage} is for the {@code boolean} or {@code Boolean} type.
     *
     * @param typeUsage the {@link TypeUsage}
     * @return {@code true} if a boolean {@link TypeUsage}, {@code false} otherwise
     */
    public static boolean isBoolean(final TypeUsage typeUsage) {
        if (!(typeUsage instanceof SpecificTypeUsage specificTypeUsage)) {
            return false;
        }

        final var typeName = specificTypeUsage.typeName();
        return (isPrimitive(typeName) && typeName.name().toString().equals("boolean"))
            || typeName.canonicalName().equals("java.lang.Boolean");
    }

    /**
     * Determines the {@link Type} name for the given {@link TypeName} when used in the {@link Optional}ly specified
     * package {@link Namespace}.
     *
     * @param typeName          the {@link TypeName}
     * @param namespace         the {@link Optional} {@link Namespace}
     * @param importedTypeNames the {@link ImportedTypeNames} representing the currently imported {@link TypeName}s
     * @return the {@link Type} name
     */
    public static String getJDKTypeName(final TypeName typeName,
                                        final Optional<Namespace> namespace,
                                        final ImportedTypeNames importedTypeNames) {

        Objects.requireNonNull(typeName, "The TypeName must not be null");
        Objects.requireNonNull(namespace, "The Namespace must not be null");

        // java.lang types don't need importing or require fully-qualified-names
        if (typeName.namespace()
            .map(packageName -> packageName.toString().startsWith("java.lang"))
            .orElse(false)) {

            // TODO: include the enclosing typename if one is defined?
            return typeName.name().toString();
        }

        // TypeNames in the same Namespace don't need importing or require fully-qualified-names
        if (typeName.namespace().equals(namespace) && typeName.enclosingTypeName().isEmpty()) {

            // TODO: include the enclosing typename if one is defined?
            return typeName.name().toString();
        }

        // attempt to import the type name
        if (importedTypeNames.include(typeName)) {
            return typeName.name().toString();
        } else {
            return typeName.canonicalName();
        }
    }

    /**
     * Attempts to determine the {@link Type} declaration for the given {@link TypeUsage} as a variable when used
     * in the {@link Optional}ly specified package {@link Namespace}.
     *
     * @param typeUsage         the {@link TypeUsage}
     * @param namespace         the {@link Optional} {@link Namespace}
     * @param importedTypeNames the {@link ImportedTypeNames} representing the currently imported {@link TypeName}s
     * @return the {@link Optional} {@link Type} name, otherwise {@link Optional#empty()} if one can't be determined
     */
    public static Optional<String> getVariableTypeDeclaration(final TypeUsage typeUsage,
                                                              final Optional<Namespace> namespace,
                                                              final ImportedTypeNames importedTypeNames) {

        // handle Generic Type Usage
        if (typeUsage instanceof GenericTypeUsage genericTypeUsage) {
            return Optional.of(getJDKTypeName(genericTypeUsage.typeName(), namespace, importedTypeNames)
                + genericTypeUsage.parameters()
                .map(parameter -> getVariableTypeDeclaration(parameter, namespace, importedTypeNames).orElse(
                    "Object"))
                .collect(Collectors.joining(", ", "<", ">")));
        }

        // handle Array Type Usage
        if (typeUsage instanceof ArrayTypeUsage arrayTypeUsage) {
            return Optional.of(
                getVariableTypeDeclaration(arrayTypeUsage.type(), namespace, importedTypeNames) + "[]");
        }

        return typeUsage instanceof NamedTypeUsage namedTypeUsage
            ? Optional.of(getJDKTypeName(namedTypeUsage.typeName(), namespace, importedTypeNames))
            : Optional.empty();
    }

    /**
     * Attempts to obtain the {@link Class} given a {@link TypeUsage} using the specified {@link ClassLoader}.
     *
     * @param typeUsage   the {@link TypeUsage}
     * @param classLoader the {@link ClassLoader}
     * @return the {@link Optional} {@link Class} or {@link Optional#empty()} if there's no such {@link Class} available
     */
    public static Optional<Class<?>> getClass(final TypeUsage typeUsage,
                                              final ClassLoader classLoader) {

        if (classLoader == null) {
            return Optional.empty();
        }

        if (typeUsage instanceof ArrayTypeUsage arrayTypeUsage) {
            return getClass(arrayTypeUsage.type(), classLoader);
        }

        if (!(typeUsage instanceof NamedTypeUsage namedTypeUsage)) {
            return Optional.empty();
        }

        final var typeName = namedTypeUsage.typeName();

        if (isPrimitive(typeName)) {
            return Optional.ofNullable(PRIMITIVE_CLASSES_BY_NAME.get(typeName.name().toString()));
        }

        try {
            return Optional.ofNullable(classLoader.loadClass(typeName.binaryName()));
        } catch (final ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to obtain the {@link Class} given a {@link TypeUsage} using the {@link Thread} {@link ClassLoader}.
     *
     * @param typeUsage the {@link TypeUsage}
     * @return the {@link Optional} {@link Class} or {@link Optional#empty()} if there's no such {@link Class} available
     * @see #getClass(TypeUsage, ClassLoader)
     */
    public static Optional<Class<?>> getThreadContextClass(final TypeUsage typeUsage) {
        return getClass(typeUsage, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Attempts to obtain the {@link Class} given a {@link TypeUsage} using the System {@link ClassLoader}.
     *
     * @param typeUsage the {@link TypeUsage}
     * @return the {@link Optional} {@link Class} or {@link Optional#empty()} if there's no such {@link Class} available
     * @see #getClass(TypeUsage, ClassLoader)
     */
    public static Optional<Class<?>> getSystemClass(final TypeUsage typeUsage) {
        return getClass(typeUsage, ClassLoader.getSystemClassLoader());
    }

    /**
     * Attempts to obtain the {@link Class} given a {@link TypeUsage} using the Platform {@link ClassLoader}.
     *
     * @param typeUsage the {@link TypeUsage}
     * @return the {@link Optional} {@link Class} or {@link Optional#empty()} if there's no such {@link Class} available
     * @see #getClass(TypeUsage, ClassLoader)
     */
    public static Optional<Class<?>> getPlatformClass(final TypeUsage typeUsage) {
        return getClass(typeUsage, ClassLoader.getPlatformClassLoader());
    }

    /**
     * Attempts to obtain the {@link Class} of the first type parameter of a {@link GenericTypeUsage} using the
     * {@link Thread} {@link ClassLoader}.
     * <p>
     * For example, given {@code Optional<String>}, this returns {@code Optional.of(String.class)}.
     * Returns {@link Optional#empty()} if the {@link TypeUsage} is not a {@link GenericTypeUsage} or has no
     * type parameters.
     *
     * @param typeUsage the {@link TypeUsage}
     * @return the {@link Optional} {@link Class} of the first type parameter, or {@link Optional#empty()}
     */
    public static Optional<Class<?>> getFirstTypeParameterClass(final TypeUsage typeUsage) {
        if (typeUsage instanceof GenericTypeUsage gtu) {
            return gtu.parameters().findFirst()
                .flatMap(TypeUsages::getThreadContextClass);
        }
        return Optional.empty();
    }

    /**
     * Determines if {@code subtype} is a subtype of {@code supertype} per
     * <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.10">JLS 4.10</a>
     * subtyping - i.e. whether a value of {@code subtype} could stand in wherever {@code supertype} is
     * expected. Fully generic-aware: covariant through the class / interface hierarchy at the top level, but
     * a parameterized {@code supertype}'s own type arguments are matched by
     * <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS 4.5.1</a>
     * containment - invariant unless the argument is a wildcard - rather than by assignability, so
     * {@code List<Impl>} is <em>not</em> a subtype of {@code List<Base>} even though {@code Impl} is a
     * subtype of {@code Base}.
     *
     * <p>Where the erased hierarchy links {@code subtype} to {@code supertype} through a <em>different</em>
     * raw type (e.g. {@code ArrayList<Base>} against a {@code List<Base>} supertype, or
     * {@code class IntList extends ArrayList<Integer>} against {@code List<Integer>}), {@code subtype}'s
     * reified type arguments are substituted through the intervening generic supertypes (JLS 4.10.2) to
     * arrive at the instantiation of {@code supertype}'s raw type that {@code subtype} actually implements,
     * which is then compared positionally. Where that substitution can't be performed precisely - an
     * unresolvable descriptor, a type-variable arity mismatch, or a raw link in the chain - a raw
     * {@code subtype} falls back to erasure subtyping and a concretely-parameterized one is treated as not a
     * subtype rather than risk a false positive.
     *
     * <p>A top-level {@link WildcardTypeUsage} {@code supertype} is treated as a type-argument-position
     * construct and dispatched to the same JLS 4.5.1 containment check used for a parameterized
     * {@code supertype}'s arguments.
     *
     * @param subtype   the candidate subtype {@link TypeUsage}
     * @param supertype the (possibly wildcard-bearing or parameterized) supertype {@link TypeUsage}
     * @param codeModel the {@link JDKCodeModel} used to resolve each participating type's descriptor on
     *                  demand, so callers never need to have pre-scanned them
     * @return {@code true} if {@code subtype} is a subtype of {@code supertype}, {@code false} otherwise
     */
    public static boolean isAssignable(final TypeUsage subtype,
                                       final TypeUsage supertype,
                                       final JDKCodeModel codeModel) {

        if (supertype instanceof WildcardTypeUsage supertypeWildcard) {
            return contains(supertypeWildcard, subtype, codeModel);
        }

        if (subtype instanceof WildcardTypeUsage) {
            // a wildcard can only stand on the subtype side when the supertype is itself a wildcard
            // (handled above) - it can never be a subtype of a concrete or parameterized type
            return false;
        }

        if (!(supertype instanceof GenericTypeUsage supertypeGeneric)
            || supertypeGeneric.parameters().findAny().isEmpty()) {
            // supertype carries no type arguments for invariance to apply to - erased subtyping governs
            return isErasedSubtype(subtype, supertype, codeModel);
        }

        if (!(subtype instanceof NamedTypeUsage subtypeNamed)) {
            return false;
        }

        final var instantiation = instantiatedSupertype(
            supertypeGeneric.typeName(), subtypeNamed, codeModel, new HashSet<>());
        if (instantiation.isPresent()) {
            return argumentsContained(supertypeGeneric, instantiation.get(), codeModel);
        }

        // no parameterized instantiation of supertype's raw type is reachable from subtype: a raw subtype
        // is compatible with any parameterization (erased subtyping governs), a concretely-parameterized
        // one leaves the invariance unverifiable and is conservatively rejected
        final var subtypeIsRaw = !(subtype instanceof GenericTypeUsage subtypeGeneric)
            || subtypeGeneric.parameters().findAny().isEmpty();
        return subtypeIsRaw && isErasedSubtype(subtype, supertype, codeModel);
    }

    /**
     * Determines if {@code supertype} and {@code subtypeInstantiation} - already established to share a raw
     * type - have equal type-argument arity, with each of {@code supertype}'s arguments
     * {@linkplain #contains(TypeUsage, TypeUsage, JDKCodeModel) containing} the positionally corresponding
     * argument of {@code subtypeInstantiation} per JLS 4.5.1.
     */
    private static boolean argumentsContained(final GenericTypeUsage supertype,
                                              final GenericTypeUsage subtypeInstantiation,
                                              final JDKCodeModel codeModel) {

        final var supertypeArguments = supertype.parameters().toList();
        final var subtypeArguments = subtypeInstantiation.parameters().toList();

        return supertypeArguments.size() == subtypeArguments.size()
            && IntStream.range(0, supertypeArguments.size())
            .allMatch(i -> contains(supertypeArguments.get(i), subtypeArguments.get(i), codeModel));
    }

    /**
     * Determines if the type argument {@code container} <i>contains</i> {@code contained} per the containment
     * rules of
     * <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS 4.5.1</a> - i.e.
     * whether {@code Foo<contained>} is a subtype of {@code Foo<container>}. Containment is asymmetric.
     *
     * <p>A non-wildcard {@code container} contains only itself: a concrete type argument is invariant, so a
     * {@code Person} argument never matches a requested {@code Car} argument even where the two are otherwise
     * related. A generic {@code container} such as {@code List<String>} still recurses, matching only an
     * equal instantiation (or a raw {@code List} usage).
     *
     * <p>{@code ? super S} contains {@code contained} only when {@code contained} is itself {@code ? super C}
     * with {@code S} a subtype of {@code C} - an {@code extends}-bounded or unbounded {@code contained} has
     * no guaranteed lower bound and can never satisfy a {@code super} container, not even one bounded by
     * {@code Object}.
     *
     * <p>{@code ? extends S} - including the unbounded {@code ?}, which JLS treats as
     * {@code ? extends Object} - contains {@code contained} when {@code contained}'s <em>effective upper
     * bound</em> (its own {@code extends} bound, or {@code Object} if it is unbounded or only
     * {@code super}-bounded) is a subtype of {@code S}. A non-wildcard {@code contained} is contained by
     * {@code ? extends S} when it is a subtype of {@code S}, and by {@code ? super S} when {@code S} is a
     * subtype of it.
     */
    private static boolean contains(final TypeUsage container,
                                    final TypeUsage contained,
                                    final JDKCodeModel codeModel) {

        if (!(container instanceof WildcardTypeUsage containerWildcard)) {
            return !(contained instanceof WildcardTypeUsage)
                && isSameArgument(container, contained, codeModel);
        }

        if (contained instanceof WildcardTypeUsage containedWildcard) {
            return wildcardContainsWildcard(containerWildcard, containedWildcard, codeModel);
        }

        return containerWildcard.upperBound()
                .map(bound -> isAssignable(contained, bound, codeModel))
                .orElse(true)
            && containerWildcard.lowerBound()
                .map(bound -> isAssignable(bound, contained, codeModel))
                .orElse(true);
    }

    /**
     * Whether two non-wildcard type arguments are the same for invariance purposes - equal canonical name
     * for a plain type, or the same generic instantiation for a parameterized one. Sameness of a
     * parameterized argument is <em>mutual</em> containment (recursively, via
     * {@link #argumentsContained(GenericTypeUsage, GenericTypeUsage, JDKCodeModel)} both ways): a nested
     * wildcard is still invariant here, so {@code List<? extends Impl>} is not the same argument as
     * {@code List<? extends Base>} even though {@code Impl} is a subtype of {@code Base}. A raw
     * {@code contained} usage of {@code container}'s own raw type matches any parameterization.
     */
    private static boolean isSameArgument(final TypeUsage container,
                                          final TypeUsage contained,
                                          final JDKCodeModel codeModel) {

        if (!(container instanceof GenericTypeUsage containerGeneric)) {
            return container.canonicalName().equals(contained.canonicalName());
        }

        if (!(contained instanceof NamedTypeUsage containedNamed)
            || !containerGeneric.typeName().equals(containedNamed.typeName())) {
            return false;
        }

        return !(contained instanceof GenericTypeUsage containedGeneric)
            || containedGeneric.parameters().findAny().isEmpty()
            || (argumentsContained(containerGeneric, containedGeneric, codeModel)
                && argumentsContained(containedGeneric, containerGeneric, codeModel));
    }

    /**
     * Applies the two-wildcard cases of {@link #contains(TypeUsage, TypeUsage, JDKCodeModel)}:
     * {@code ? super S} contains {@code ? super C} iff {@code S} is a subtype of {@code C}; otherwise
     * (both effectively {@code extends}-bounded) containment compares effective upper bounds, requiring
     * {@code contained}'s to be a subtype of {@code container}'s.
     */
    private static boolean wildcardContainsWildcard(final WildcardTypeUsage container,
                                                    final WildcardTypeUsage contained,
                                                    final JDKCodeModel codeModel) {

        if (container.lowerBound().isPresent()) {
            final var containerLower = container.lowerBound().orElseThrow();

            return contained.lowerBound()
                .map(containedLower -> isAssignable(containerLower, containedLower, codeModel))
                .orElse(false);
        }

        final var containerUpper = container.upperBound()
            .orElseGet(() -> codeModel.getTypeUsage(Object.class));
        final var containedUpper = contained.upperBound()
            .orElseGet(() -> codeModel.getTypeUsage(Object.class));

        return isAssignable(containedUpper, containerUpper, codeModel);
    }

    /**
     * Walks {@code candidate}'s supertype hierarchy looking for the instantiation of {@code targetRawType}
     * that {@code candidate} actually implements, substituting {@code candidate}'s reified type arguments for
     * its declared type variables at each step (e.g. {@code ArrayList<Base>} carries {@code E = Base}, which
     * propagates through {@code List<E>} to yield {@code List<Base>}).
     *
     * <p>Returns {@link Optional#empty()} when the invariance can't be verified precisely: {@code candidate}'s
     * type descriptor (or that of an intervening supertype) isn't resolvable in {@code codeModel}, its declared
     * type-variable arity doesn't line up with the reified arguments, or a supertype in the chain is written
     * raw (erasing the arguments). Callers treat an empty result as incompatible.
     *
     * @param targetRawType the raw {@link TypeName} whose instantiation is sought
     * @param candidate     the {@link NamedTypeUsage} to walk upward from
     * @param codeModel     the {@link JDKCodeModel} used to resolve each type's descriptor on demand
     * @param visited       the raw {@link TypeName}s already visited on this path, guarding against cycles
     * @return the {@link GenericTypeUsage} instantiation of {@code targetRawType}, or {@link Optional#empty()}
     */
    private static Optional<GenericTypeUsage> instantiatedSupertype(final TypeName targetRawType,
                                                                    final NamedTypeUsage candidate,
                                                                    final JDKCodeModel codeModel,
                                                                    final Set<TypeName> visited) {

        if (candidate.typeName().equals(targetRawType)) {
            // only a genuinely parameterized instantiation lets the caller verify invariance; a raw link
            // that happens to land on the target leaves nothing to compare, so defer to erasure assignability
            return candidate instanceof GenericTypeUsage generic && generic.parameters().findAny().isPresent()
                ? Optional.of(generic)
                : Optional.empty();
        }

        if (!visited.add(candidate.typeName())) {
            return Optional.empty();
        }

        final var descriptor = codeModel.getJDKTypeDescriptor(candidate.typeName());
        if (descriptor.isEmpty()) {
            return Optional.empty();
        }

        final List<TypeVariableUsage> declared = descriptor.get()
            .getTrait(ParameterizedTypeDescriptor.class)
            .map(parameterized -> parameterized.typeVariables().toList())
            .orElse(List.of());

        final List<TypeUsage> arguments = candidate instanceof GenericTypeUsage generic
            ? generic.parameters().toList()
            : List.of();

        if (declared.size() != arguments.size()) {
            // a raw link in the chain (arguments erased), or a modeling mismatch - can't substitute precisely
            return Optional.empty();
        }

        final Map<TypeName, TypeUsage> substitution = new HashMap<>();
        for (var i = 0; i < declared.size(); i++) {
            substitution.put(declared.get(i).typeName(), arguments.get(i));
        }

        return directSupertypeUsages(descriptor.get())
            .map(parent -> substitute(parent, substitution, codeModel))
            .flatMap(parent -> parent instanceof NamedTypeUsage named
                ? instantiatedSupertype(targetRawType, named, codeModel, visited).stream()
                : Stream.empty())
            .findFirst();
    }

    /**
     * The direct superclass and directly-implemented interface {@link TypeUsage}s of {@code descriptor}, as
     * declared (i.e. still bearing {@code descriptor}'s own type variables where the supertype is generic).
     */
    private static Stream<NamedTypeUsage> directSupertypeUsages(final JDKTypeDescriptor descriptor) {
        return Stream.concat(descriptor.parentTypeUsage().stream(), descriptor.interfaceTypeUsages());
    }

    /**
     * Substitutes {@code substitution}'s type-variable bindings throughout {@code usage}, recursing into the
     * type arguments of a {@link GenericTypeUsage}, the bounds of a {@link WildcardTypeUsage}, and the
     * component of an {@link ArrayTypeUsage} (so a supertype clause such as {@code implements List<T[]>}
     * substitutes correctly). A {@link TypeVariableUsage} with no binding, and any other {@link TypeUsage},
     * is returned unchanged.
     */
    private static TypeUsage substitute(final TypeUsage usage,
                                        final Map<TypeName, TypeUsage> substitution,
                                        final JDKCodeModel codeModel) {

        // WildcardTypeUsage extends TypeVariableUsage, so this check must precede the TypeVariableUsage one
        if (usage instanceof WildcardTypeUsage wildcard) {
            return WildcardTypeUsage.of(codeModel,
                wildcard.lowerBound().map(bound -> Lazy.of(substitute(bound, substitution, codeModel))),
                wildcard.upperBound().map(bound -> Lazy.of(substitute(bound, substitution, codeModel))));
        }

        if (usage instanceof TypeVariableUsage variable) {
            return substitution.getOrDefault(variable.typeName(), usage);
        }

        if (usage instanceof GenericTypeUsage generic) {
            final var substituted = generic.parameters()
                .map(parameter -> substitute(parameter, substitution, codeModel))
                .toArray(TypeUsage[]::new);
            return GenericTypeUsage.of(codeModel, generic.typeName(), substituted);
        }

        if (usage instanceof ArrayTypeUsage array) {
            return ArrayTypeUsage.of(codeModel, Lazy.of(substitute(array.type(), substitution, codeModel)));
        }

        return usage;
    }

    /**
     * Determines if {@code subtype} is a subtype of {@code supertype} at the level of <em>erasure</em> - the
     * raw class / interface hierarchy only, ignoring any generic type arguments either side carries.
     * {@link #isAssignable(TypeUsage, TypeUsage, JDKCodeModel)} is the generic-aware check layered on top of
     * this one.
     *
     * <p>Scans either side into {@code codeModel} on demand via
     * {@link JDKCodeModel#getJDKTypeDescriptor(TypeName)} if not already present, so callers never need to
     * have pre-scanned the participating types themselves. A type that cannot be resolved to a loadable
     * {@link Class} at all (for example a purely source-modeled type with no corresponding runtime class) is
     * treated as not a subtype rather than failing the whole lookup. Identical {@link TypeName}s
     * short-circuit to {@code true} before either side needs to be resolved; a {@link TypeUsage} that isn't a
     * {@link NamedTypeUsage} (an array, a wildcard) falls back to plain canonical-name equality.
     */
    private static boolean isErasedSubtype(final TypeUsage subtype,
                                           final TypeUsage supertype,
                                           final JDKCodeModel codeModel) {

        if (!(subtype instanceof NamedTypeUsage subtypeNamed)
            || !(supertype instanceof NamedTypeUsage supertypeNamed)) {
            return subtype.canonicalName().equals(supertype.canonicalName());
        }

        if (subtypeNamed.typeName().equals(supertypeNamed.typeName())) {
            return true;
        }

        final var subtypeDescriptor = codeModel.getJDKTypeDescriptor(subtypeNamed.typeName());
        final var supertypeDescriptor = codeModel.getJDKTypeDescriptor(supertypeNamed.typeName());

        return subtypeDescriptor.isPresent()
            && supertypeDescriptor.isPresent()
            && subtypeDescriptor.get().isAssignableTo(supertypeDescriptor.get());
    }
}
