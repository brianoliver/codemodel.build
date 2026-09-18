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

import build.base.query.Index;
import build.base.query.Match;
import build.codemodel.foundation.descriptor.AbstractTraitable;
import build.codemodel.foundation.descriptor.Singular;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.TraitAware;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CodeModelTraitable}, specifically the {@link TraitAware} notification and index book-keeping
 * around {@link CodeModelTraitable#createTrait}, {@link CodeModelTraitable#computeIfAbsent} and
 * {@link CodeModelTraitable#computeIfPresent}, for both {@link Singular} and non-{@link Singular} {@link Trait}s.
 *
 * @author reed.vonredwitz
 * @since Sep-2026
 */
class CodeModelTraitableTests {

    /**
     * Creates a new empty {@link CodeModel} for testing.
     *
     * @return a new {@link CodeModel}
     */
    private static CodeModel createCodeModel() {
        return new ConceptualCodeModel(new NonCachingNameProvider());
    }

    /**
     * Ensure that re-adding the same non-{@link build.codemodel.foundation.descriptor.Singular} {@link Trait}
     * instance is a no-op and does not re-trigger {@link TraitAware#onAddedTrait(Trait)} or re-index the
     * {@link Trait}.
     */
    @Test
    void shouldOnlyNotifyOnceWhenAddingSameNonSingularTraitTwice() {
        final var traitable = new CountingTraitAware(createCodeModel());

        final var widget = new Widget();
        traitable.addTrait(widget);
        traitable.addTrait(widget);

        assertThat(traitable.traits())
            .containsExactly(widget);

        assertThat(traitable.addedCount.get())
            .isEqualTo(1);

        assertThat(traitable.removedCount.get())
            .isZero();
    }

    /**
     * Ensure that {@link build.codemodel.foundation.descriptor.Traitable#computeIfPresent} removing a
     * non-{@link build.codemodel.foundation.descriptor.Singular} {@link Trait} without a replacement leaves no
     * trace of the {@link Trait}'s registration class behind, so {@code hasTrait}/{@code hasTraits} correctly
     * report its absence.
     */
    @Test
    void shouldHaveNoTraitAfterComputeIfPresentRemovesNonSingularTraitWithoutReplacement() {
        final var traitable = new CountingTraitAware(createCodeModel());

        final var widget = new Widget();
        traitable.addTrait(widget);

        final var result = traitable.computeIfPresent(Widget.class, (_, _) -> null);

        assertThat(result)
            .isEmpty();

        assertThat(traitable.hasTrait(Widget.class))
            .isFalse();

        assertThat(traitable.hasTraits())
            .isFalse();

        assertThat(traitable.traits())
            .isEmpty();

        assertThat(traitable.removedCount.get())
            .isEqualTo(1);

        assertThat(traitable.addedCount.get())
            .isEqualTo(1);
    }

    /**
     * Ensure that {@link build.codemodel.foundation.descriptor.Traitable#computeIfPresent} replacing a
     * {@link Singular} {@link Trait} with a new instance notifies removal of the old {@link Trait} and addition of
     * the new one, in that order, exactly once each.
     */
    @Test
    void shouldNotifyRemovedThenAddedWhenComputeIfPresentReplacesSingularTrait() {
        final var traitable = new CountingTraitAware(createCodeModel());

        final var gadget = new Gadget();
        traitable.addTrait(gadget);

        final var replacement = new Gadget();
        final var result = traitable.computeIfPresent(Gadget.class, (_, _) -> replacement);

        assertThat(result)
            .contains(replacement);

        assertThat(traitable.trait(Gadget.class))
            .isSameAs(replacement);

        assertThat(traitable.addedCount.get())
            .isEqualTo(2);

        assertThat(traitable.removedCount.get())
            .isEqualTo(1);
    }

    /**
     * Ensure that {@link build.codemodel.foundation.descriptor.Traitable#computeIfPresent} replacing a
     * non-{@link Singular} {@link Trait} with a new instance notifies removal of the old {@link Trait} and addition
     * of the new one, and that the registration class now maps to only the replacement.
     */
    @Test
    void shouldNotifyRemovedThenAddedWhenComputeIfPresentReplacesNonSingularTrait() {
        final var traitable = new CountingTraitAware(createCodeModel());

        final var widget = new Widget();
        traitable.addTrait(widget);

        final var replacement = new Widget();
        final var result = traitable.computeIfPresent(Widget.class, (_, _) -> replacement);

        assertThat(result)
            .contains(replacement);

        assertThat(traitable.traits())
            .containsExactly(replacement);

        assertThat(traitable.addedCount.get())
            .isEqualTo(2);

        assertThat(traitable.removedCount.get())
            .isEqualTo(1);
    }

    /**
     * Ensure that {@link build.codemodel.foundation.descriptor.Traitable#computeIfAbsent} creating a new
     * {@link Singular} {@link Trait} notifies addition exactly once, and that calling it again while the
     * {@link Trait} is present does not re-notify.
     */
    @Test
    void shouldOnlyNotifyOnceWhenComputeIfAbsentCreatesSingularTrait() {
        final var traitable = new CountingTraitAware(createCodeModel());

        final var gadget = new Gadget();
        final var created = traitable.computeIfAbsent(Gadget.class, _ -> gadget);
        final var second = traitable.computeIfAbsent(Gadget.class, _ -> new Gadget());

        assertThat(created)
            .contains(gadget);

        assertThat(second)
            .contains(gadget);

        assertThat(traitable.addedCount.get())
            .isEqualTo(1);

        assertThat(traitable.removedCount.get())
            .isZero();
    }

    /**
     * Ensure that a {@link Trait} reentering the {@link Traitable} from within {@link Index#index(Object)} — eg: a
     * {@link CodeModel} that indexes eagerly and eagerly triggers further model changes — does not fail, because
     * {@link CodeModelTraitable} calls {@link Index#index(Object)} (and notifies {@link TraitAware}) strictly
     * outside of any {@link java.util.concurrent.ConcurrentHashMap#compute} remapping function for the affected
     * registration class. Previously, this notification happened from within the remapping function, so a
     * reentrant {@link Traitable#removeTrait} for the same {@link Singular} registration class would trigger a
     * nested {@link java.util.concurrent.ConcurrentHashMap#compute} call on the same key, which
     * {@link java.util.concurrent.ConcurrentHashMap} rejects with an {@link IllegalStateException}.
     */
    @Test
    void shouldSupportReentrantTraitRemovalFromWithinIndex() {
        final var codeModel = new ReentrantIndexCodeModel(new NonCachingNameProvider());
        final var traitable = new CountingTraitAware(codeModel);

        final var gadget = new Gadget();

        codeModel.runReentrantOnNextIndex(() -> traitable.removeTrait(gadget));

        traitable.addTrait(gadget);

        assertThat(traitable.hasTrait(Gadget.class))
            .isFalse();

        assertThat(traitable.addedCount.get())
            .isEqualTo(1);

        assertThat(traitable.removedCount.get())
            .isEqualTo(1);
    }

    /**
     * A simple concrete, un-annotated (and therefore non-{@link Singular}) {@link Trait} used to exercise the
     * {@code traitsByClass} code paths of {@link CodeModelTraitable}.
     */
    static final class Widget
        implements Trait {

    }

    /**
     * A {@link Singular} {@link Trait} used to exercise the {@code singularTraitsByClass} code paths of
     * {@link CodeModelTraitable}.
     */
    @Singular
    static final class Gadget
        implements Trait {

    }

    /**
     * A {@link TraitAware} {@link AbstractTraitable} that counts the number of times it is notified of an added or
     * removed {@link Trait}.
     */
    static final class CountingTraitAware
        extends AbstractTraitable
        implements TraitAware {

        final AtomicInteger addedCount = new AtomicInteger();
        final AtomicInteger removedCount = new AtomicInteger();

        CountingTraitAware(final CodeModel codeModel) {
            super(codeModel);
        }

        @Override
        public void onAddedTrait(final Trait trait) {
            this.addedCount.incrementAndGet();
        }

        @Override
        public void onRemovedTrait(final Trait trait) {
            this.removedCount.incrementAndGet();
        }
    }

    /**
     * A {@link ConceptualCodeModel} whose {@link Index} runs a one-shot {@link Runnable} immediately after
     * {@link Index#index(Object)} is called, allowing a test to reenter the {@link Traitable} from within
     * indexing.
     */
    static final class ReentrantIndexCodeModel
        extends ConceptualCodeModel {

        private final ReentrantHookIndex hookIndex;

        ReentrantIndexCodeModel(final NameProvider nameProvider) {
            super(nameProvider);
            this.hookIndex = new ReentrantHookIndex(super.index());
        }

        @Override
        protected Index index() {
            return this.hookIndex;
        }

        void runReentrantOnNextIndex(final Runnable reentry) {
            this.hookIndex.pendingReentry.set(reentry);
        }
    }

    /**
     * An {@link Index} that delegates to another {@link Index}, but runs a pending one-shot {@link Runnable}
     * immediately after {@link #index(Object)} is delegated, then clears it.
     */
    static final class ReentrantHookIndex
        implements Index {

        private final Index delegate;
        private final AtomicReference<Runnable> pendingReentry = new AtomicReference<>();

        ReentrantHookIndex(final Index delegate) {
            this.delegate = delegate;
        }

        @Override
        public void index(final Object object) {
            this.delegate.index(object);

            final var reentry = this.pendingReentry.getAndSet(null);
            if (reentry != null) {
                reentry.run();
            }
        }

        @Override
        public void unindex(final Object object) {
            this.delegate.unindex(object);
        }

        @Override
        public <T> void add(final Class<T> valueClass, final T value) {
            this.delegate.add(valueClass, value);
        }

        @Override
        public <T> void remove(final Class<T> valueClass, final T value) {
            this.delegate.remove(valueClass, value);
        }

        @Override
        public <M> Match<M> match(final Class<M> matchableClass) {
            return this.delegate.match(matchableClass);
        }
    }
}
