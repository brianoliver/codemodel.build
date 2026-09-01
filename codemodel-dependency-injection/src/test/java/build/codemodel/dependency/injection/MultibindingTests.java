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

import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the multibinding system: {@link Binder#bindSet}, {@link MultiBinder}, and the five
 * resolvable collection types ({@link Set}, {@link Collection}, {@link Iterable}, {@link Stream},
 * {@link List}).
 *
 * @author reed.vonredwitz
 * @since Apr-2026
 */
class MultibindingTests
    implements ContextualTesting {

    // ---- holder classes for injection-point-based tests ----

    static class SetHolder {
        @Inject
        Set<String> values;
    }

    static class CollectionHolder {
        @Inject
        Collection<String> values;
    }

    static class IterableHolder {
        @Inject
        Iterable<String> values;
    }

    static class StreamHolder {
        @Inject
        Stream<String> values;
    }

    static class ListHolder {
        @Inject
        List<String> values;
    }

    // ---- Set<T> ----

    /**
     * Ensures bound values are injectable as a {@link Set}.
     */
    @Test
    void shouldInjectAsSet() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(String.class).add("a").add("b").add("c");

        final var holder = context.inject(new SetHolder());

        assertThat(holder.values).containsExactlyInAnyOrder("a", "b", "c");
    }

    // ---- Collection<T> ----

    /**
     * Ensures bound values are injectable as a {@link Collection}.
     */
    @Test
    void shouldInjectAsCollection() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(String.class).add("a").add("b");

        final var holder = context.inject(new CollectionHolder());

        assertThat(holder.values).containsExactlyInAnyOrder("a", "b");
    }

    // ---- Iterable<T> ----

    /**
     * Ensures bound values are injectable as an {@link Iterable}.
     */
    @Test
    void shouldInjectAsIterable() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(String.class).add("a").add("b");

        final var holder = context.inject(new IterableHolder());

        assertThat(holder.values).containsExactlyInAnyOrder("a", "b");
    }

    // ---- Stream<T> ----

    /**
     * Ensures bound values are injectable as a {@link Stream}, and a fresh stream is produced on each
     * injection so the stream can be consumed without affecting subsequent injections.
     */
    @Test
    void shouldInjectAsStream() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(String.class).add("a").add("b");

        final var holder1 = context.inject(new StreamHolder());
        assertThat(holder1.values).containsExactlyInAnyOrder("a", "b");

        // second injection must get a fresh, unconsumed stream
        final var holder2 = context.inject(new StreamHolder());
        assertThat(holder2.values).containsExactlyInAnyOrder("a", "b");
    }

    // ---- List<T> ----

    /**
     * Ensures bound values are injectable as an immutable {@link List}.
     */
    @Test
    void shouldInjectAsList() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(String.class).add("a").add("b").add("c");

        final var holder = context.inject(new ListHolder());

        assertThat(holder.values).containsExactlyInAnyOrder("a", "b", "c");
    }

    // ---- unregistered element type ----

    static class UnregisteredHolder {
        @Inject
        Set<Integer> values;
    }

    static class UnregisteredCollectionHolder {
        @Inject
        Collection<Integer> values;
    }

    static class UnregisteredIterableHolder {
        @Inject
        Iterable<Integer> values;
    }

    static class UnregisteredListHolder {
        @Inject
        List<Integer> values;
    }

    static class UnregisteredStreamHolder {
        @Inject
        Stream<Integer> values;
    }

    /**
     * A {@code Set<T>} injection point whose element type was never registered via
     * {@link Binder#bindSet} must resolve to an empty set rather than failing to resolve the
     * dependency: an unpopulated multibinding yields an empty collection.
     */
    @Test
    void shouldInjectEmptySetWhenElementTypeNeverBound() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(String.class).add("a");

        final var holder = context.inject(new UnregisteredHolder());

        assertThat(holder.values).isEmpty();
    }

    /**
     * The empty-collection fallback applies to every supported collection shape, not just
     * {@code Set}: an unregistered element type yields an empty {@code Collection}, {@code Iterable},
     * {@code List}, and {@code Stream} too.
     */
    @Test
    void shouldInjectEmptyCollectionsWhenElementTypeNeverBound() {
        final var context = createInjectionFramework().newContext();

        assertThat(context.inject(new UnregisteredCollectionHolder()).values).isEmpty();
        assertThat(context.inject(new UnregisteredIterableHolder()).values).isEmpty();
        assertThat(context.inject(new UnregisteredListHolder()).values).isEmpty();
        assertThat(context.inject(new UnregisteredStreamHolder()).values).isEmpty();
    }

    /**
     * Multibinding contributions registered on a parent context must remain visible to a child
     * context created via {@link Context#newContext()}: the child's own (empty) multibinding
     * registry must not shadow the parent's contributions with an empty collection.
     */
    @Test
    void shouldInheritMultibindingsFromParentContext() {
        final var parent = createInjectionFramework().newContext();
        parent.bindSet(String.class).add("a").add("b");

        final var child = parent.newContext();
        final var holder = child.inject(new SetHolder());

        assertThat(holder.values).containsExactlyInAnyOrder("a", "b");
    }

    /**
     * A user-added {@link Resolver} that supplies a collection type must win over the empty-collection
     * fallback: the fallback is the last link in the resolver chain and only applies once every other
     * resolver has declined.
     */
    @Test
    void shouldPreferCustomResolverOverEmptyMultibindingFallback() {
        final var context = createInjectionFramework().newContext();
        context.addResolver(Resolver.of(dependency -> isSetOf(dependency, Integer.class)
            ? Optional.<Binding<Object>>of(ValueBinding.of(dependency, (Object) Set.of(1, 2, 3)))
            : Optional.empty()));

        final var holder = context.inject(new UnregisteredHolder());

        assertThat(holder.values).containsExactlyInAnyOrder(1, 2, 3);
    }

    /**
     * A {@link Resolver} added to a <em>child</em> context must win over the empty-collection fallback
     * inherited from its parent: the parent is chained ahead of the child's own resolvers, so its
     * fallback must not be consulted before them. Only the child's single trailing fallback applies,
     * once every resolver — parent and child — has declined.
     */
    @Test
    void shouldPreferChildResolverOverParentEmptyMultibindingFallback() {
        final var parent = createInjectionFramework().newContext();
        final var child = parent.newContext();
        child.addResolver(Resolver.of(dependency -> isSetOf(dependency, Integer.class)
            ? Optional.<Binding<Object>>of(ValueBinding.of(dependency, (Object) Set.of(1, 2, 3)))
            : Optional.empty()));

        final var holder = child.inject(new UnregisteredHolder());

        assertThat(holder.values).containsExactlyInAnyOrder(1, 2, 3);
    }

    private static boolean isSetOf(final Dependency dependency, final Class<?> elementType) {
        return dependency.typeUsage() instanceof GenericTypeUsage generic
            && Set.class.getCanonicalName().equals(generic.typeName().canonicalName())
            && generic.parameters().findFirst()
                .filter(NamedTypeUsage.class::isInstance)
                .map(NamedTypeUsage.class::cast)
                .map(named -> named.typeName().canonicalName())
                .filter(elementType.getCanonicalName()::equals)
                .isPresent();
    }

    // ---- cross-module merging ----

    /**
     * Ensures that calling {@link Binder#bindSet} from two different modules merges into one set.
     */
    @Test
    void shouldMergeMultibindingsAcrossModules() {
        final Module moduleA = binder -> binder.bindSet(String.class).add("a");
        final Module moduleB = binder -> binder.bindSet(String.class).add("b");

        final var context = createInjectionFramework().newContext(moduleA, moduleB);
        final var holder = context.inject(new SetHolder());

        assertThat(holder.values).containsExactlyInAnyOrder("a", "b");
    }

    // ---- class and supplier variants ----

    /**
     * Ensures {@link MultiBinder#add(Class)} binds by class, resolving via the context.
     */
    @Test
    void shouldBindByClass() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(CharSequence.class).add(String.class);

        final var holder = context.inject(new CharSeqSetHolder());

        assertThat(holder.values).hasSize(1);
        assertThat(holder.values.iterator().next()).isInstanceOf(String.class);
    }

    /**
     * Ensures {@link MultiBinder#add(java.util.function.Supplier)} binds via supplier.
     */
    @Test
    void shouldBindBySupplier() {
        final var context = createInjectionFramework().newContext();
        context.bindSet(String.class).add(() -> "supplied");

        final var holder = context.inject(new SetHolder());

        assertThat(holder.values).containsExactly("supplied");
    }

    static class CharSeqSetHolder {
        @Inject
        Set<CharSequence> values;
    }

    // ---- wildcard element types ----

    static class Base {
    }

    static class Impl
        extends Base {
    }

    static class Sibling
        extends Base {
    }

    static class WildcardSetHolder {
        @Inject
        Set<? extends Base> values;
    }

    static class ConcreteSetHolder {
        @Inject
        Set<Impl> values;
    }

    /**
     * {@code multiBinder(Impl.class)} contributions satisfy a wildcard-equivalent {@code Set<? extends Base>}
     * injection point exactly as they already satisfy the concrete {@code Set<Impl>} form, since every
     * {@code Impl} is a {@code Base}. {@link InjectionContext}'s multibinding resolver can't extract a
     * {@link Class} from a wildcard type argument directly (a
     * {@link build.codemodel.foundation.usage.WildcardTypeUsage} has no loadable class of its own via
     * {@code TypeUsages.getFirstTypeParameterClass}), so it instead searches the registered element classes
     * for one compatible with the wildcard's bound.
     */
    @Test
    void shouldSatisfyWildcardCollectionFromMultiBinderContributions() {
        final var context = createInjectionFramework().newContext();

        context.bindSet(Impl.class).add(new Impl()).add(new Impl());

        final var concreteHolder = context.inject(new ConcreteSetHolder());
        assertThat(concreteHolder.values)
            .hasSize(2);

        final var wildcardHolder = context.inject(new WildcardSetHolder());
        assertThat(wildcardHolder.values)
            .hasSize(2);
    }

    /**
     * When more than one registered {@code multiBinder} element type is compatible with a requested wildcard
     * bound (here, both {@code Impl} and {@code Sibling} satisfy {@code Set<? extends Base>}), resolution
     * must not silently pick one - there's no principled way to choose between two equally-compatible, but
     * structurally unrelated, contributed collections.
     */
    @Test
    void shouldThrowWhenMultipleMultiBinderElementTypesAreWildcardCompatible() {
        final var context = createInjectionFramework().newContext();

        context.bindSet(Impl.class).add(new Impl());
        context.bindSet(Sibling.class).add(new Sibling());

        assertThatThrownBy(() -> context.inject(new WildcardSetHolder()))
            .isInstanceOf(InjectionException.class);
    }

    // ---- known gap: multibinding does not support qualifiers ----

    static class NamedProdHolder {
        @Inject
        @Named("prod")
        Set<String> values;
    }

    static class NamedDevHolder {
        @Inject
        @Named("dev")
        Set<String> values;
    }

    /**
     * KNOWN GAP: {@link Binder#bindSet} has no qualifier parameter - {@link MultiBinder} exposes no
     * {@code with}/{@code as} to attach one - and {@link InjectionContext}'s multibinding resolution keys its
     * element registry purely by element type, so the qualifiers on the requesting {@code Set<T>} injection
     * point are never consulted. As a result, two differently-qualified injection points for the same element
     * type (here {@code @Named("prod")} and {@code @Named("dev")}) both silently resolve to the very same
     * registered set, rather than being kept independent or failing.
     *
     * <p>This asserts what a correct, qualifier-aware {@code bindSet} would need to support - two
     * independently-qualified sets of {@code String} resolving separately - which the current API can't even
     * express (there is no qualified {@code bindSet}), let alone honor. It's disabled rather than asserted as
     * a positive expectation of today's behavior: it documents the missing capability so it isn't
     * rediscovered by surprise, without treating either the missing API or the collision as a passing
     * contract. Un-skip and adapt once qualified multibinding is supported.
     */
    @Test
    @Disabled("multibinding does not yet support qualifiers - see Javadoc")
    void shouldKeepDifferentlyQualifiedMultibindingsIndependent() {
        final var context = createInjectionFramework().newContext();

        // bindSet has no qualifier-attaching overload today; both calls collapse into the one
        // unqualified String multibinding, which is exactly the gap this test documents.
        context.bindSet(String.class).add("prod-value");
        context.bindSet(String.class).add("dev-value");

        final var prodHolder = context.inject(new NamedProdHolder());
        final var devHolder = context.inject(new NamedDevHolder());

        assertThat(prodHolder.values).containsExactly("prod-value");
        assertThat(devHolder.values).containsExactly("dev-value");
    }
}
