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
     * Determines if {@code candidate} is compatible with the (possibly wildcard-bearing) {@code requested}
     * {@link TypeUsage} - i.e. whether {@code candidate} could stand in wherever {@code requested} is
     * expected, per ordinary JLS assignability (covariant: a subtype is always compatible with its
     * supertype).
     *
     * <p>This is distinct from the <i>invariant</i> rules governing {@code requested}'s own generic type
     * arguments, if it has any - per <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS 4.5.1</a>,
     * a concrete type argument (no wildcard) must match exactly, since {@code List<Impl>} is never
     * compatible with a requested {@code List<Base>} even though {@code Impl} is compatible with
     * {@code Base} at the top level. Each type argument is compared via
     * {@link #isArgumentCompatible(TypeUsage, TypeUsage, JDKCodeModel)}, which enforces that invariance
     * (relaxed only where {@code requested}'s argument is itself a wildcard, or {@code candidate} supplies
     * no type arguments at all - a raw usage, compatible with any parameterization of the same raw type).
     *
     * @param requested the (possibly wildcard-bearing) requested {@link TypeUsage}
     * @param candidate the candidate {@link TypeUsage}
     * @param codeModel the {@link JDKCodeModel} used to check bound assignability, scanning either side of the
     *                  comparison on demand if not already present
     * @return {@code true} if {@code candidate} is compatible with {@code requested}, {@code false} otherwise
     * @see #isAssignable(TypeUsage, TypeUsage, JDKCodeModel)
     */
    public static boolean isCompatible(final TypeUsage requested,
                                       final TypeUsage candidate,
                                       final JDKCodeModel codeModel) {

        if (requested instanceof WildcardTypeUsage wildcard) {
            return wildcardCompatible(wildcard, candidate, codeModel);
        }

        if (candidate instanceof WildcardTypeUsage) {
            // a wildcard can only appear on the candidate side when requested is itself a wildcard
            // (handled above) - a wildcard can never satisfy a requested concrete or generic type
            return false;
        }

        if (!(requested instanceof GenericTypeUsage requestedGeneric)) {
            // requested carries no type argument for invariance to apply to - ordinary (covariant)
            // JLS assignability governs the whole type
            return isAssignable(candidate, requested, codeModel);
        }

        if (!(candidate instanceof NamedTypeUsage candidateNamed)) {
            return false;
        }

        if (!requestedGeneric.typeName().equals(candidateNamed.typeName())) {
            // candidate isn't a usage of the same generic declaration as requested, so there's no
            // positional type argument list to compare directly against requested's. Substitute
            // candidate's reified type arguments (and any concrete instantiations its own supertypes are
            // written with, e.g. class IntList extends ArrayList<Integer>) through the intervening
            // generic supertypes to arrive at the instantiation of requested's raw type that candidate
            // actually implements (e.g. ArrayList<Base> -> List<Base>), then compare that positionally
            // against requested.
            final var instantiated = instantiatedSupertype(
                requestedGeneric.typeName(), candidateNamed, codeModel, new HashSet<>());
            if (instantiated.isPresent()) {
                return parametersCompatible(requestedGeneric, instantiated.get(), codeModel);
            }

            // the walk was inconclusive - no concrete instantiation of requested's raw type reachable
            // from candidate. If candidate carries no reified type argument of its own it's a raw usage,
            // compatible with any parameterization, so ordinary raw (erasure) assignability governs;
            // otherwise a raw/erased link somewhere in the chain leaves invariance unverifiable, so
            // conservatively treat it as incompatible rather than ignore the argument mismatch entirely.
            return (!(candidate instanceof GenericTypeUsage candidateGenericRawCheck)
                    || candidateGenericRawCheck.parameters().findAny().isEmpty())
                && isAssignable(candidate, requested, codeModel);
        }

        if (!(candidate instanceof GenericTypeUsage candidateGeneric)
            || candidateGeneric.parameters().findAny().isEmpty()) {
            // the candidate is a raw usage of the same raw type - compatible with any parameterization,
            // since there is no reified type argument to conflict with the requested wildcard
            return true;
        }

        return parametersCompatible(requestedGeneric, candidateGeneric, codeModel);
    }

    /**
     * Determines if {@code requestedGeneric} and {@code candidateGeneric} - already established to share a
     * common raw type - have the same number of type arguments, each pairwise compatible per
     * {@link #isArgumentCompatible(TypeUsage, TypeUsage, JDKCodeModel)}.
     */
    private static boolean parametersCompatible(final GenericTypeUsage requestedGeneric,
                                                final GenericTypeUsage candidateGeneric,
                                                final JDKCodeModel codeModel) {

        final var requestedParameters = requestedGeneric.parameters().toList();
        final var candidateParameters = candidateGeneric.parameters().toList();

        return requestedParameters.size() == candidateParameters.size()
            && IntStream.range(0, requestedParameters.size())
            .allMatch(i ->
                isArgumentCompatible(requestedParameters.get(i), candidateParameters.get(i), codeModel));
    }

    /**
     * Determines if {@code candidate} is compatible with {@code requested} in a generic type <i>argument</i>
     * position, per the invariant containment rules of
     * <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS 4.5.1</a> - unlike
     * {@link #isCompatible(TypeUsage, TypeUsage, JDKCodeModel)}, a concrete (non-wildcard) {@code requested}
     * argument requires an exact match rather than mere assignability, since generic type arguments are
     * invariant without a wildcard.
     *
     * @param requested the requested type argument
     * @param candidate the candidate type argument
     * @param codeModel the {@link JDKCodeModel} used to check wildcard bound assignability
     * @return {@code true} if {@code candidate} is compatible with {@code requested} in argument position
     * @see #isCompatible(TypeUsage, TypeUsage, JDKCodeModel)
     */
    private static boolean isArgumentCompatible(final TypeUsage requested,
                                                final TypeUsage candidate,
                                                final JDKCodeModel codeModel) {

        if (requested instanceof WildcardTypeUsage wildcard) {
            return wildcardCompatible(wildcard, candidate, codeModel);
        }

        if (candidate instanceof WildcardTypeUsage) {
            return false;
        }

        if (!(requested instanceof GenericTypeUsage requestedGeneric)) {
            // a concrete type argument is invariant per JLS 4.5.1 - matching List<Person> against
            // List<Car> must never collide, even where Person and Car are otherwise assignable
            return requested.canonicalName().equals(candidate.canonicalName());
        }

        if (!(candidate instanceof NamedTypeUsage candidateNamed)
            || !requestedGeneric.typeName().equals(candidateNamed.typeName())) {
            return false;
        }

        if (!(candidate instanceof GenericTypeUsage candidateGeneric)
            || candidateGeneric.parameters().findAny().isEmpty()) {
            return true;
        }

        return parametersCompatible(requestedGeneric, candidateGeneric, codeModel);
    }

    /**
     * Determines if {@code candidate} is compatible with a {@code requested} that is itself a
     * {@link WildcardTypeUsage}, shared between {@link #isCompatible(TypeUsage, TypeUsage, JDKCodeModel)} and
     * {@link #isArgumentCompatible(TypeUsage, TypeUsage, JDKCodeModel)} since a wildcard {@code requested} is
     * handled identically at the top level and in argument position - only a non-wildcard {@code requested}
     * distinguishes covariance from invariance.
     */
    private static boolean wildcardCompatible(final WildcardTypeUsage wildcard,
                                              final TypeUsage candidate,
                                              final JDKCodeModel codeModel) {

        if (candidate instanceof WildcardTypeUsage candidateWildcard) {
            return wildcardsCompatible(wildcard, candidateWildcard, codeModel);
        }

        return wildcard.upperBound()
                .map(bound -> isBoundAssignable(candidate, bound, codeModel))
                .orElse(true)
            && wildcard.lowerBound()
                .map(bound -> isBoundAssignable(bound, candidate, codeModel))
                .orElse(true);
    }

    /**
     * Checks whether {@code from} is assignable to {@code to} for the purpose of comparing a wildcard bound,
     * where {@code to} may itself be a parameterized generic type.
     * {@link #isAssignable(TypeUsage, TypeUsage, JDKCodeModel)} on its own is raw-hierarchy based - it would
     * report {@code List<Integer>} as assignable to {@code List<String>} purely because the raw types match -
     * so when {@code to} carries concrete type arguments the check is routed through
     * {@link #isCompatible(TypeUsage, TypeUsage, JDKCodeModel)} instead, which enforces the
     * <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS 4.5.1</a> argument
     * invariance (recursing back here for any wildcard bounds nested inside those arguments). The recursion
     * terminates because each hop strips one level of generic nesting.
     */
    private static boolean isBoundAssignable(final TypeUsage from,
                                             final TypeUsage to,
                                             final JDKCodeModel codeModel) {

        return to instanceof GenericTypeUsage toGeneric && toGeneric.parameters().findAny().isPresent()
            ? isCompatible(to, from, codeModel)
            : isAssignable(from, to, codeModel);
    }

    /**
     * Determines if {@code candidate} is <i>contained by</i> {@code requested} per the type argument
     * containment rules of
     * <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS 4.5.1</a> - i.e.
     * whether a {@code List<candidate>} usage would be assignable wherever a {@code List<requested>} usage is
     * expected. Containment is asymmetric.
     *
     * <p>When {@code requested} is {@code ? super S}, only a {@code candidate} that is itself
     * {@code ? super C} can be contained, and only when {@code S} is assignable to {@code C} (candidate's
     * lower bound reaches at least as low as {@code S}); an {@code extends}-bounded or unbounded
     * {@code candidate} has no guaranteed lower bound at all and can never satisfy a {@code super}
     * requirement, per JLS - not even one with a {@code super Object} bound.
     *
     * <p>When {@code requested} is {@code ? extends S} - including the unbounded {@code ?}, which JLS treats
     * as {@code ? extends Object} - containment reduces to comparing each side's <i>effective upper bound</i>:
     * {@code S} for {@code requested}, and for {@code candidate} either its own {@code extends} bound, or
     * {@code Object} if {@code candidate} is unbounded or only {@code super}-bounded (since neither guarantees
     * anything tighter) - requiring {@code candidate}'s effective upper bound to be assignable to
     * {@code requested}'s.
     */
    private static boolean wildcardsCompatible(final WildcardTypeUsage requested,
                                               final WildcardTypeUsage candidate,
                                               final JDKCodeModel codeModel) {

        if (requested.lowerBound().isPresent()) {
            final var requestedLower = requested.lowerBound().orElseThrow();

            return candidate.lowerBound()
                .map(candidateLower -> isBoundAssignable(requestedLower, candidateLower, codeModel))
                .orElse(false);
        }

        final var requestedUpper = requested.upperBound()
            .orElseGet(() -> codeModel.getTypeUsage(Object.class));
        final var candidateUpper = candidate.upperBound()
            .orElseGet(() -> codeModel.getTypeUsage(Object.class));

        return isBoundAssignable(candidateUpper, requestedUpper, codeModel);
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

        // WildcardTypeUsage extends TypeVariableUsage in the foundation model, so this check must come
        // first: a wildcard falling through to the TypeVariableUsage branch would be looked up by its
        // (synthetic) type name, never match a real binding, and come back with its bounds unsubstituted.
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
     * Determines if {@code from} is assignable to {@code to}, scanning either side into {@code codeModel} on
     * demand via {@link JDKCodeModel#getJDKTypeDescriptor(TypeName)} if not already present, so callers never
     * need to have pre-scanned the participating types themselves. Treats a type that cannot be resolved to a
     * loadable {@link Class} at all (for example a purely source-modeled type with no corresponding runtime
     * class) as not assignable, rather than failing the whole lookup. Identical {@link TypeName}s are treated
     * as assignable without a model lookup, short-circuiting before either side needs to be resolved.
     *
     * @param from      the {@link TypeUsage} to check assignability from
     * @param to        the {@link TypeUsage} to check assignability to
     * @param codeModel the {@link JDKCodeModel} used to scan either side into the model on demand
     * @return {@code true} if {@code from} is assignable to {@code to}, {@code false} otherwise
     */
    public static boolean isAssignable(final TypeUsage from,
                                       final TypeUsage to,
                                       final JDKCodeModel codeModel) {

        if (!(from instanceof NamedTypeUsage fromNamed) || !(to instanceof NamedTypeUsage toNamed)) {
            return from.canonicalName().equals(to.canonicalName());
        }

        if (fromNamed.typeName().equals(toNamed.typeName())) {
            return true;
        }

        final var fromDescriptor = codeModel.getJDKTypeDescriptor(fromNamed.typeName());
        final var toDescriptor = codeModel.getJDKTypeDescriptor(toNamed.typeName());

        return fromDescriptor.isPresent()
            && toDescriptor.isPresent()
            && fromDescriptor.get().isAssignableTo(toDescriptor.get());
    }
}
