package build.codemodel.jdk;

import build.base.foundation.Lazy;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.Namespace;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.ArrayTypeUsage;
import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.foundation.usage.SpecificTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.foundation.usage.WildcardTypeUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TypeUsages}.
 *
 * @author brian.oliver
 * @since Jan-2025
 */
class TypeUsagesTests {

    /**
     * Creates a new {@link JDKCodeModel} for testing.
     *
     * @return a new {@link JDKCodeModel}
     */
    protected JDKCodeModel createCodeModel() {
        final var nameProvider = new NonCachingNameProvider();
        return new JDKCodeModel(nameProvider);
    }

    /**
     * Ensure a {@link Class} can be obtained for a {@link TypeUsage}.
     */
    @Test
    void shouldObtainClassForTypeUsage() {
        final var codeModel = createCodeModel();

        final var typeUsage = codeModel.getTypeUsage(String.class);

        final var typeUsageClass = TypeUsages.getSystemClass(typeUsage)
            .orElseThrow();

        assertThat(typeUsageClass)
            .isEqualTo(String.class);
    }

    /**
     * Ensure a {@link Class} can be obtained for a {@link GenericTypeUsage}.
     */
    @Test
    void shouldObtainClassForGenericTypeUsage() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();

        final var typeUsage = GenericTypeUsage.of(codeModel, nameProvider.getTypeName(Optional.class));

        final var typeUsageClass = TypeUsages.getSystemClass(typeUsage)
            .orElseThrow();

        assertThat(typeUsageClass)
            .isEqualTo(Optional.class);
    }

    /**
     * Ensure a {@link Class} can be obtained for a {@link ArrayTypeUsage}.
     */
    @Test
    void shouldObtainClassForArrayTypeUsage() {
        final var codeModel = createCodeModel();

        final var typeUsage = ArrayTypeUsage.of(codeModel,
            Lazy.of(codeModel.getTypeUsage(String.class)));

        final var typeUsageClass = TypeUsages.getSystemClass(typeUsage)
            .orElseThrow();

        assertThat(typeUsageClass)
            .isEqualTo(String.class);
    }

    // ---- fixtures for isCompatible / isAssignable ----

    static class Base {
    }

    static class Impl
        extends Base {
    }

    static class Unrelated {
    }

    static class Impl2
        extends Base {
    }

    /**
     * A generic interface whose superinterface argument nests a wildcard bounded by its own type variable
     * ({@code Supplier<List<? extends T>>} - a wildcard can't sit directly in an {@code extends} clause, but
     * can nest one level in). Substituting a concrete argument for {@code T} has to descend into that
     * wildcard bound: {@code NestedWildcardSupplier<Impl>} supplies {@code List<? extends Impl>}, not
     * {@code List<? extends T>}.
     */
    interface NestedWildcardSupplier<T>
        extends Supplier<List<? extends T>> {
    }

    /**
     * A non-generic subtype whose generic superclass is already fully instantiated ({@code ArrayList<Base>}) -
     * the "{@code class IntList extends ArrayList<Integer>}" shape. It declares no type variables of its own,
     * so the substitution map is empty and the concrete {@code Base} argument flows straight up.
     */
    static class BaseArrayList
        extends ArrayList<Base> {
    }

    /**
     * A raw {@code Class} usage (no reified generic parameter) carries no type argument to conflict with a
     * requested wildcard bound, so it should be compatible with a {@code Class<? extends Base>} request.
     */
    @Test
    void shouldBeCompatibleWhenCandidateIsRawUsage() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))));
        final var candidate = GenericTypeUsage.of(codeModel, classTypeName);

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isTrue();
    }

    /**
     * A concretely-parameterized {@code Class<Impl>} usage should be compatible with a
     * {@code Class<? extends Base>} request when {@code Impl} is actually assignable to {@code Base}, and
     * incompatible when it isn't.
     */
    @Test
    void shouldBeCompatibleOnlyWhenConcreteParameterSatisfiesWildcardBound() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))));

        final var assignableCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Impl.class));
        final var unrelatedCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Unrelated.class));

        assertThat(TypeUsages.isAssignable(assignableCandidate, requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(unrelatedCandidate, requested, codeModel))
            .isFalse();
    }

    /**
     * A candidate wildcard's bound is checked against the requested wildcard's bound rather than requiring
     * the two to be identical - a {@code Class<? extends Impl>} candidate should be compatible with a
     * {@code Class<? extends Base>} request since every {@code Impl} is a {@code Base}.
     */
    @Test
    void shouldBeCompatibleWhenCandidateWildcardBoundSatisfiesRequestedWildcardBound() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))));
        final var candidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class)))));

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isTrue();
    }

    /**
     * An unbounded wildcard ({@code Class<?>}) imposes no constraint, so any candidate parameterization -
     * including one bearing no relation at all to the other fixtures - should be compatible.
     */
    @Test
    void shouldBeCompatibleWithUnboundedWildcard() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(), Optional.empty()));
        final var candidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Unrelated.class));

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isTrue();
    }

    /**
     * A {@code ? super Impl} requested wildcard should be compatible with a concrete candidate parameter only
     * when {@code Impl} is assignable <strong>to</strong> the candidate (the candidate is a supertype of the
     * lower bound), not the other way around as with an {@code extends} bound.
     */
    @Test
    void shouldBeCompatibleOnlyWhenConcreteParameterSatisfiesWildcardLowerBound() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class))),
                Optional.empty()));

        final var supertypeCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Base.class));
        final var unrelatedCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Unrelated.class));

        assertThat(TypeUsages.isAssignable(supertypeCandidate, requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(unrelatedCandidate, requested, codeModel))
            .isFalse();
    }

    /**
     * Per <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS 4.5.1</a> type
     * argument containment, an {@code extends}-bounded wildcard can never be contained by a {@code super}
     * requirement, and a {@code super}-bounded wildcard can never be contained by an {@code extends}
     * requirement - regardless of how the two bounds relate to each other. {@code ? extends Base} is
     * incompatible with a {@code ? super Impl} candidate even though {@code Impl <: Base}, and
     * {@code ? super Impl} is incompatible with a {@code ? extends Base} candidate for the same reason.
     */
    @Test
    void shouldBeIncompatibleWhenExtendsAndSuperWildcardsArePaired() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var extendsBaseRequested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))));
        final var superImplCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class))),
                Optional.empty()));

        final var superImplRequested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class))),
                Optional.empty()));
        final var extendsBaseCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))));

        assertThat(TypeUsages.isAssignable(superImplCandidate, extendsBaseRequested, codeModel))
            .isFalse();
        assertThat(TypeUsages.isAssignable(extendsBaseCandidate, superImplRequested, codeModel))
            .isFalse();
    }

    /**
     * A {@code ? super} requested wildcard is only contained by a {@code candidate} that is itself
     * {@code super}-bounded and whose lower bound is reachable from the requested one - {@code ? super Impl}
     * is compatible with {@code ? super Base} (candidate's {@code super Base} accepts a strict superset of
     * what {@code super Impl} accepts, since {@code Impl <: Base}), but is incompatible with a
     * {@code ? super Base} requested against a narrower {@code ? super Impl} candidate reversed, or against an
     * unbounded candidate, since an unbounded wildcard has no guaranteed lower bound at all.
     */
    @Test
    void shouldBeCompatibleForSuperWildcardsOnlyWhenCandidateLowerBoundIsReachable() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class))),
                Optional.empty()));

        final var reachableCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class))),
                Optional.empty()));
        final var unreachableCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Unrelated.class))),
                Optional.empty()));
        final var unboundedCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.create(codeModel));

        assertThat(TypeUsages.isAssignable(reachableCandidate, requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(unreachableCandidate, requested, codeModel))
            .isFalse();
        assertThat(TypeUsages.isAssignable(unboundedCandidate, requested, codeModel))
            .isFalse();
    }

    /**
     * Containment of two {@code extends} bounds is directional, per JLS 4.5.1: {@code candidate}'s bound must
     * itself be assignable to {@code requested}'s bound, not merely related to it - sibling subtypes of a
     * common supertype (neither assignable to the other) must be treated as incompatible, since neither bound
     * is narrower than the other.
     */
    @Test
    void shouldBeIncompatibleForDisjointExtendsWildcards() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class)))));
        final var candidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Impl2.class)))));

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isFalse();
    }

    /**
     * A generic type appearing <em>inside</em> a wildcard bound is compared with full argument invariance, not
     * by erasure: a requested {@code ? extends List<Base>} is satisfied by a candidate {@code ? extends
     * List<Base>} (and by {@code ? extends ArrayList<Base>}, a subtype instantiation carrying the same
     * argument), but not by {@code ? extends List<Impl>} - {@code List<Impl>} is not assignable to
     * {@code List<Base>} even though the raw types match.
     */
    @Test
    void shouldCompareGenericWildcardBoundsInvariantly() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);
        final var listTypeName = nameProvider.getTypeName(List.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    codeModel.getTypeUsage(Base.class))))));

        final var matchingBound = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    codeModel.getTypeUsage(Base.class))))));
        final var subtypeBound = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, arrayListTypeName,
                    codeModel.getTypeUsage(Base.class))))));
        final var mismatchedArgumentBound = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    codeModel.getTypeUsage(Impl.class))))));

        assertThat(TypeUsages.isAssignable(matchingBound, requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(subtypeBound, requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(mismatchedArgumentBound, requested, codeModel))
            .isFalse();
    }

    /**
     * The invariant comparison of a generic type inside a wildcard bound applies to {@code super} bounds too:
     * a requested {@code ? super List<Base>} is satisfied by {@code ? super List<Base>} but not by
     * {@code ? super List<Impl>}.
     */
    @Test
    void shouldCompareGenericSuperWildcardBoundsInvariantly() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);
        final var listTypeName = nameProvider.getTypeName(List.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel,
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    codeModel.getTypeUsage(Base.class)))),
                Optional.empty()));

        final var matchingBound = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel,
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    codeModel.getTypeUsage(Base.class)))),
                Optional.empty()));
        final var mismatchedArgumentBound = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel,
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    codeModel.getTypeUsage(Impl.class)))),
                Optional.empty()));

        assertThat(TypeUsages.isAssignable(matchingBound, requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(mismatchedArgumentBound, requested, codeModel))
            .isFalse();
    }

    /**
     * A wildcard nested <em>inside</em> a non-wildcard type argument is still governed by that argument's
     * invariance per <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1">JLS
     * 4.5.1</a>: {@code List<? extends Impl>} and {@code List<? extends Base>} are distinct types, so
     * {@code Class<List<? extends Impl>>} is not a subtype of {@code Class<List<? extends Base>>} even though
     * {@code Impl} is a subtype of {@code Base}. The equal-instantiation case ({@code Class<List<? extends
     * Base>>} against itself) still holds.
     */
    @Test
    void shouldCompareWildcardsNestedInAnInvariantArgumentInvariantly() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);
        final var listTypeName = nameProvider.getTypeName(List.class);

        final var supertype = GenericTypeUsage.of(codeModel, classTypeName,
            GenericTypeUsage.of(codeModel, listTypeName,
                WildcardTypeUsage.of(codeModel, Optional.empty(),
                    Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class))))));
        final var equalInstantiation = GenericTypeUsage.of(codeModel, classTypeName,
            GenericTypeUsage.of(codeModel, listTypeName,
                WildcardTypeUsage.of(codeModel, Optional.empty(),
                    Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class))))));
        final var subtypeBoundInstantiation = GenericTypeUsage.of(codeModel, classTypeName,
            GenericTypeUsage.of(codeModel, listTypeName,
                WildcardTypeUsage.of(codeModel, Optional.empty(),
                    Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class))))));

        assertThat(TypeUsages.isAssignable(equalInstantiation, supertype, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(subtypeBoundInstantiation, supertype, codeModel))
            .isFalse();
    }

    /**
     * A wildcard can only ever appear on the candidate side when {@code requested} is itself a wildcard - if
     * {@code requested} is a plain concrete type, a wildcard candidate can never satisfy it and must be
     * treated as incompatible rather than falling through to a canonical-name comparison.
     */
    @Test
    void shouldBeIncompatibleWhenCandidateIsWildcardButRequestedIsNot() {
        final var codeModel = createCodeModel();

        final var requested = codeModel.getTypeUsage(Base.class);
        final var candidate = WildcardTypeUsage.create(codeModel);

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isFalse();
    }

    /**
     * {@link TypeUsages#isAssignable(TypeUsage, TypeUsage, JDKCodeModel)} should report a subtype as
     * assignable to its supertype, and two unrelated types as not assignable.
     */
    @Test
    void shouldDetermineAssignabilityBetweenNamedTypeUsages() {
        final var codeModel = createCodeModel();

        assertThat(TypeUsages.isAssignable(
            codeModel.getTypeUsage(Impl.class), codeModel.getTypeUsage(Base.class), codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(
            codeModel.getTypeUsage(Unrelated.class), codeModel.getTypeUsage(Base.class), codeModel))
            .isFalse();
    }

    /**
     * A requested type that isn't itself a {@link GenericTypeUsage} carries no type argument for JLS
     * invariance to apply to, so ordinary (covariant) assignability governs the whole comparison: a subtype
     * candidate is compatible with its supertype request, and an unrelated candidate isn't.
     */
    @Test
    void shouldUseOrdinaryAssignabilityForNonGenericRequestedTypes() {
        final var codeModel = createCodeModel();

        final var requested = codeModel.getTypeUsage(Base.class);

        assertThat(TypeUsages.isAssignable(codeModel.getTypeUsage(Base.class), requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(codeModel.getTypeUsage(Impl.class), requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(codeModel.getTypeUsage(Unrelated.class), requested, codeModel))
            .isFalse();
    }

    /**
     * Within a generic type argument, invariance still applies even though the same two types are
     * compatible at the top level - {@code List<Impl>} is never compatible with a requested
     * {@code List<Base>} type argument per JLS 4.5.1, even though {@code Impl} is itself compatible with
     * {@code Base} when compared directly (as in
     * {@link #shouldUseOrdinaryAssignabilityForNonGenericRequestedTypes()}).
     */
    @Test
    void shouldRemainInvariantForConcreteTypeArgumentsDespiteTopLevelAssignability() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName, codeModel.getTypeUsage(Base.class));
        final var candidate = GenericTypeUsage.of(codeModel, classTypeName, codeModel.getTypeUsage(Impl.class));

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isFalse();
    }

    /**
     * A candidate that isn't a {@link NamedTypeUsage} at all, or is a {@link NamedTypeUsage} of a different
     * raw type than the requested {@link GenericTypeUsage}, can never be compatible - matching by raw type
     * name is a prerequisite before any type argument is even considered.
     */
    @Test
    void shouldBeIncompatibleWhenCandidateRawTypeDiffersOrIsNotNamed() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);
        final var optionalTypeName = nameProvider.getTypeName(Optional.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName, codeModel.getTypeUsage(Base.class));

        final var differentRawTypeCandidate = GenericTypeUsage.of(codeModel, optionalTypeName,
            codeModel.getTypeUsage(Base.class));
        final var notNamedCandidate = ArrayTypeUsage.of(codeModel, Lazy.of(codeModel.getTypeUsage(Base.class)));

        assertThat(TypeUsages.isAssignable(differentRawTypeCandidate, requested, codeModel))
            .isFalse();
        assertThat(TypeUsages.isAssignable(notNamedCandidate, requested, codeModel))
            .isFalse();
    }

    /**
     * A candidate that is a subtype of {@code requested}'s raw type but under a <em>different</em> raw type
     * of its own (e.g. {@code ArrayList<Impl>} against a requested {@code List<Base>}) has its reified type
     * argument substituted through the intervening supertypes (here {@code ArrayList<Impl>}'s {@code E}
     * through {@code AbstractList<E>} to {@code List<E>}, yielding {@code List<Impl>}) and then compared
     * positionally against {@code requested}. {@code List<Impl>} is not compatible with a requested
     * {@code List<Base>}, so this remains incompatible - but now because the substituted argument genuinely
     * mismatches, not merely because the raw types differ.
     */
    @Test
    void shouldBeIncompatibleWhenDifferentRawTypeCandidateHasItsOwnTypeArgument() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var listTypeName = nameProvider.getTypeName(List.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var requested = GenericTypeUsage.of(codeModel, listTypeName, codeModel.getTypeUsage(Base.class));
        final var candidate = GenericTypeUsage.of(codeModel, arrayListTypeName, codeModel.getTypeUsage(Impl.class));

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isFalse();
    }

    /**
     * {@code isCompatible} verifies generic-argument invariance across a raw-type change by substituting the
     * candidate's reified type arguments through its generic supertypes. {@code ArrayList<Base>} is genuinely
     * compatible with a requested {@code List<Base>} per the JLS - {@code ArrayList<E>} implements
     * {@code List<E>}, so with {@code E = Base} the candidate really is a {@code List<Base>} - and
     * substituting {@code Base} for {@code ArrayList}'s {@code E} up through {@code AbstractList<E>} to
     * {@code List<E>} arrives at exactly that instantiation, which matches {@code requested} positionally.
     */
    @Test
    void shouldVerifyInvarianceAcrossARawTypeChange() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var listTypeName = nameProvider.getTypeName(List.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var requested = GenericTypeUsage.of(codeModel, listTypeName, codeModel.getTypeUsage(Base.class));
        final var candidate = GenericTypeUsage.of(codeModel, arrayListTypeName, codeModel.getTypeUsage(Base.class));

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isTrue();
    }

    /**
     * Supertype substitution descends into a wildcard bound nested inside a superinterface argument:
     * {@code NestedWildcardSupplier<T> extends Supplier<List<? extends T>>}, so
     * {@code NestedWildcardSupplier<Impl>} implements {@code Supplier<List<? extends Impl>>}. The outer
     * {@code Supplier} argument is invariant, so that instantiation is a subtype of
     * {@code Supplier<? extends List<? extends Base>>} (covariant outer wildcard, and {@code Impl <: Base})
     * but not of {@code Supplier<? extends List<? extends Unrelated>>}. Regression test for {@code substitute}
     * checking {@code TypeVariableUsage} before its {@code WildcardTypeUsage} subtype, which left the bound as
     * {@code ? extends T} and made even the matching check fail.
     */
    @Test
    void shouldSubstituteIntoAWildcardBoundNestedInASupertypeArgument() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var supplierTypeName = nameProvider.getTypeName(Supplier.class);
        final var listTypeName = nameProvider.getTypeName(List.class);

        final var candidate = GenericTypeUsage.of(codeModel,
            nameProvider.getTypeName(NestedWildcardSupplier.class), codeModel.getTypeUsage(Impl.class));

        final var matching = GenericTypeUsage.of(codeModel, supplierTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    WildcardTypeUsage.of(codeModel, Optional.empty(),
                        Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))))))));
        final var mismatched = GenericTypeUsage.of(codeModel, supplierTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, listTypeName,
                    WildcardTypeUsage.of(codeModel, Optional.empty(),
                        Optional.of(Lazy.of(codeModel.getTypeUsage(Unrelated.class)))))))));

        assertThat(TypeUsages.isAssignable(candidate, matching, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(candidate, mismatched, codeModel))
            .isFalse();
    }

    /**
     * Generic supertype substitution walks the whole chain, not just the immediate parent -
     * {@code ArrayList<Base>}'s {@code E = Base} propagates through {@code AbstractList} / {@code AbstractCollection}
     * up to {@code Collection<E>}, so {@code ArrayList<Base>} is compatible with a requested
     * {@code Collection<Base>} but not with {@code Collection<Impl>}.
     */
    @Test
    void shouldSubstituteThroughMultipleGenericSupertypes() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var collectionTypeName = nameProvider.getTypeName(Collection.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var candidate = GenericTypeUsage.of(codeModel, arrayListTypeName, codeModel.getTypeUsage(Base.class));

        final var matching = GenericTypeUsage.of(codeModel, collectionTypeName, codeModel.getTypeUsage(Base.class));
        final var mismatched = GenericTypeUsage.of(codeModel, collectionTypeName, codeModel.getTypeUsage(Impl.class));

        assertThat(TypeUsages.isAssignable(candidate, matching, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(candidate, mismatched, codeModel))
            .isFalse();
    }

    /**
     * A non-generic candidate whose superclass is an already-instantiated generic ({@code BaseArrayList
     * extends ArrayList<Base>}) still has its inherited argument checked: the concrete {@code Base} flows up
     * through {@code ArrayList<Base>} to {@code List<Base>}, compatible with a requested {@code List<Base>}
     * but not a requested {@code List<Impl>}.
     */
    @Test
    void shouldSubstituteThroughAnAlreadyInstantiatedGenericSuperclass() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var listTypeName = nameProvider.getTypeName(List.class);

        final var candidate = codeModel.getTypeUsage(BaseArrayList.class);

        final var matching = GenericTypeUsage.of(codeModel, listTypeName, codeModel.getTypeUsage(Base.class));
        final var mismatched = GenericTypeUsage.of(codeModel, listTypeName, codeModel.getTypeUsage(Impl.class));

        assertThat(TypeUsages.isAssignable(candidate, matching, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(candidate, mismatched, codeModel))
            .isFalse();
    }

    /**
     * Substitution walks interface-to-interface links too, not just class superclasses - {@code ArrayList<Base>}
     * reaches {@code Iterable<Base>} through {@code Collection<E>}, so it's compatible with a requested
     * {@code Iterable<Base>} and not with {@code Iterable<Impl>}.
     */
    @Test
    void shouldSubstituteThroughInterfaceInheritance() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var iterableTypeName = nameProvider.getTypeName(Iterable.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var candidate = GenericTypeUsage.of(codeModel, arrayListTypeName, codeModel.getTypeUsage(Base.class));

        final var matching = GenericTypeUsage.of(codeModel, iterableTypeName, codeModel.getTypeUsage(Base.class));
        final var mismatched = GenericTypeUsage.of(codeModel, iterableTypeName, codeModel.getTypeUsage(Impl.class));

        assertThat(TypeUsages.isAssignable(candidate, matching, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(candidate, mismatched, codeModel))
            .isFalse();
    }

    /**
     * After substitution the resulting instantiation is compared through the ordinary argument-compatibility
     * rules, so a wildcard-bearing {@code requested} still relaxes invariance: {@code ArrayList<Impl>}
     * substitutes to {@code List<Impl>}, which satisfies a requested {@code List<? extends Base>} but not
     * {@code List<? extends Impl2>}.
     */
    @Test
    void shouldApplyWildcardArgumentRulesToTheSubstitutedInstantiation() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var listTypeName = nameProvider.getTypeName(List.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var candidate = GenericTypeUsage.of(codeModel, arrayListTypeName, codeModel.getTypeUsage(Impl.class));

        final var extendsBase = GenericTypeUsage.of(codeModel, listTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))));
        final var extendsImpl2 = GenericTypeUsage.of(codeModel, listTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Impl2.class)))));

        assertThat(TypeUsages.isAssignable(candidate, extendsBase, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(candidate, extendsImpl2, codeModel))
            .isFalse();
    }

    /**
     * The full JLS 4.5.1 containment set applies to the substituted instantiation, not just {@code extends}
     * bounds. {@code ArrayList<Base>} substitutes to {@code List<Base>}: {@code Base} is contained by a
     * requested {@code ? super Impl} (via {@code Base <= ? super Base <= ? super Impl}, since
     * {@code Impl <: Base}) and by the unbounded {@code ?}, but not by {@code ? super Unrelated}, whose lower
     * bound is not a subtype of {@code Base}.
     */
    @Test
    void shouldApplySuperAndUnboundedWildcardContainmentToTheSubstitutedInstantiation() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var listTypeName = nameProvider.getTypeName(List.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var baseCandidate = GenericTypeUsage.of(codeModel, arrayListTypeName,
            codeModel.getTypeUsage(Base.class));

        final var superImpl = GenericTypeUsage.of(codeModel, listTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class))),
                Optional.empty()));
        final var unbounded = GenericTypeUsage.of(codeModel, listTypeName,
            WildcardTypeUsage.create(codeModel));
        final var superUnrelated = GenericTypeUsage.of(codeModel, listTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Unrelated.class))),
                Optional.empty()));

        assertThat(TypeUsages.isAssignable(baseCandidate, superImpl, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(baseCandidate, unbounded, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(baseCandidate, superUnrelated, codeModel))
            .isFalse();
    }

    /**
     * A concretely-parameterized candidate under a raw type that never reaches {@code requested}'s raw type at
     * all ({@code HashSet<Base>} against a requested {@code List<Base>}) is incompatible - the hierarchy walk
     * finds no instantiation to compare.
     */
    @Test
    void shouldBeIncompatibleWhenCandidateHierarchyNeverReachesRequestedRawType() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var listTypeName = nameProvider.getTypeName(List.class);
        final var hashSetTypeName = nameProvider.getTypeName(HashSet.class);

        final var requested = GenericTypeUsage.of(codeModel, listTypeName, codeModel.getTypeUsage(Base.class));
        final var candidate = GenericTypeUsage.of(codeModel, hashSetTypeName, codeModel.getTypeUsage(Base.class));

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isFalse();
    }

    /**
     * A candidate that is a subtype of {@code requested}'s raw type under a different raw type of its own, but
     * is itself a <em>raw</em> usage with no type argument, carries nothing that could conflict with
     * {@code requested}'s type argument - ordinary raw (erasure) assignability of the whole type still governs.
     */
    @Test
    void shouldFallBackToErasureAssignabilityWhenDifferentRawTypeCandidateIsRaw() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var listTypeName = nameProvider.getTypeName(List.class);
        final var arrayListTypeName = nameProvider.getTypeName(ArrayList.class);

        final var requested = GenericTypeUsage.of(codeModel, listTypeName, codeModel.getTypeUsage(Base.class));
        final var candidate = GenericTypeUsage.of(codeModel, arrayListTypeName);

        assertThat(TypeUsages.isAssignable(candidate, requested, codeModel))
            .isTrue();
    }

    /**
     * Type argument comparison is positional and requires equal arity - a candidate with a different number
     * of type arguments than requested can never be compatible, even when every one of its arguments would
     * individually satisfy the corresponding requested argument. When arity does match, every positional
     * pair must be compatible - a single mismatched pair fails the whole comparison regardless of how many
     * other pairs match.
     */
    @Test
    void shouldRequireMatchingArityAndAllPositionalParametersToBeCompatible() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var requested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))),
            codeModel.getTypeUsage(Impl.class));

        final var mismatchedArityCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Impl.class));
        final var allMatchingCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Impl.class), codeModel.getTypeUsage(Impl.class));
        final var secondParameterMismatchCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            codeModel.getTypeUsage(Impl.class), codeModel.getTypeUsage(Impl2.class));

        assertThat(TypeUsages.isAssignable(mismatchedArityCandidate, requested, codeModel))
            .isFalse();
        assertThat(TypeUsages.isAssignable(allMatchingCandidate, requested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(secondParameterMismatchCandidate, requested, codeModel))
            .isFalse();
    }

    /**
     * When neither the requested nor candidate wildcard has a {@code super} bound, containment falls back to
     * comparing each side's <i>effective upper bound</i> - {@code Object} for an unbounded or
     * {@code super}-only wildcard, since neither guarantees anything tighter. A requested
     * {@code ? extends Base} rejects a candidate with no {@code extends} bound of its own (whether fully
     * unbounded or only {@code super}-bounded), since {@code Object} isn't assignable to {@code Base}; but an
     * unbounded {@code ?} request - itself {@code ? extends Object} - accepts either, since {@code Object} is
     * assignable to {@code Object}.
     */
    @Test
    void shouldCompareEffectiveUpperBoundsWhenNeitherWildcardHasASuperBound() {
        final var codeModel = createCodeModel();
        final var nameProvider = codeModel.getNameProvider();
        final var classTypeName = nameProvider.getTypeName(Class.class);

        final var extendsBaseRequested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.empty(),
                Optional.of(Lazy.of(codeModel.getTypeUsage(Base.class)))));
        final var unboundedRequested = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.create(codeModel));

        final var unboundedCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.create(codeModel));
        final var superOnlyCandidate = GenericTypeUsage.of(codeModel, classTypeName,
            WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(codeModel.getTypeUsage(Impl.class))),
                Optional.empty()));

        assertThat(TypeUsages.isAssignable(unboundedCandidate, extendsBaseRequested, codeModel))
            .isFalse();
        assertThat(TypeUsages.isAssignable(superOnlyCandidate, extendsBaseRequested, codeModel))
            .isFalse();
        assertThat(TypeUsages.isAssignable(unboundedCandidate, unboundedRequested, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(superOnlyCandidate, unboundedRequested, codeModel))
            .isTrue();
    }

    /**
     * Identical {@link TypeName}s short-circuit to {@code true} without
     * consulting the {@link JDKCodeModel} at all.
     */
    @Test
    void shouldShortCircuitAssignabilityForIdenticalTypeNames() {
        final var codeModel = createCodeModel();

        assertThat(TypeUsages.isAssignable(
            codeModel.getTypeUsage(Base.class), codeModel.getTypeUsage(Base.class), codeModel))
            .isTrue();
    }

    /**
     * A {@link TypeUsage} that isn't a {@link NamedTypeUsage} - such as an array type - falls back to plain
     * canonical-name equality rather than {@link JDKCodeModel} hierarchy assignability.
     */
    @Test
    void shouldFallBackToCanonicalNameForNonNamedTypeUsages() {
        final var codeModel = createCodeModel();

        final var baseArray = ArrayTypeUsage.of(codeModel, Lazy.of(codeModel.getTypeUsage(Base.class)));
        final var otherBaseArray = ArrayTypeUsage.of(codeModel, Lazy.of(codeModel.getTypeUsage(Base.class)));
        final var implArray = ArrayTypeUsage.of(codeModel, Lazy.of(codeModel.getTypeUsage(Impl.class)));

        assertThat(TypeUsages.isAssignable(baseArray, otherBaseArray, codeModel))
            .isTrue();
        assertThat(TypeUsages.isAssignable(baseArray, implArray, codeModel))
            .isFalse();
    }

    /**
     * A {@link TypeName} that can't be resolved to a loadable {@link Class} - for example a purely
     * source-modeled type with no corresponding runtime class - is treated as not assignable rather than
     * failing the lookup outright.
     */
    @Test
    void shouldTreatUnresolvableTypeAsNotAssignable() {
        final var codeModel = createCodeModel();

        final var bogusNamespace = Namespace.of(IrreducibleName.of("bogus")).orElseThrow();
        final var bogusTypeName = TypeName.of(Optional.empty(), Optional.empty(), Optional.of(bogusNamespace),
            IrreducibleName.of("Nonexistent"));
        final var unresolvable = SpecificTypeUsage.of(codeModel, bogusTypeName);

        assertThat(TypeUsages.isAssignable(unresolvable, codeModel.getTypeUsage(Base.class), codeModel))
            .isFalse();
    }
}
