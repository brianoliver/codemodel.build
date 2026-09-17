package build.codemodel.hierarchical.descriptor;

/*-
 * #%L
 * Hierarchical Code Model
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

import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.hierarchical.HierarchicalCodeModel;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link TypeDescriptor} capturing information concerning the <i>definition</i> of a
 * <a href="https://en.wikipedia.org/wiki/Class_hierarchy">Hierarchical</a> type.
 *
 * @author brian.oliver
 * @see ParentTypeDescriptor
 * @since Feb-2025
 */
public interface HierarchicalTypeDescriptor
    extends TypeDescriptor {

    /**
     * Determines if the {@link HierarchicalTypeDescriptor} has one or more {@link #parents()}.
     *
     * @return {@code true} if the {@link HierarchicalTypeDescriptor} as {@link #parents()}, {@code false} otherwise
     * @see #isRoot()
     */
    default boolean hasParents() {
        return parentTypeUsages()
            .findAny()
            .isPresent();
    }

    /**
     * Determines if the {@link HierarchicalTypeDescriptor} has no {@link #parents()}.
     *
     * @return {@code true} if the {@link HierarchicalTypeDescriptor} as no {@link #parents()}, {@code false} otherwise
     * @see #hasParents()
     */
    default boolean isRoot() {
        return !hasParents();
    }

    /**
     * Obtains the {@link NamedTypeUsage}s defining the <strong>immediate</strong> <i>parent</i> types of this
     * {@link HierarchicalTypeDescriptor}.
     *
     * @return a {@link Stream} of the <strong>immediate</strong> <i>parent</i> {@link NamedTypeUsage}s.
     */
    default Stream<NamedTypeUsage> parentTypeUsages() {
        return traits(ParentTypeDescriptor.class)
            .map(ParentTypeDescriptor::parentTypeUsage);
    }

    /**
     * Obtains the {@link TypeName}s of the <strong>immediate</strong> <i>parent</i> types of this
     * {@link HierarchicalTypeDescriptor}.
     *
     * @return a {@link Stream} of the <strong>immediate</strong> <i>parent</i> {@link TypeName}s.
     */
    default Stream<TypeName> parentTypeNames() {
        return parentTypeUsages()
            .map(NamedTypeUsage::typeName);
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s of the <strong>immediate</strong> <i>parent</i> types of this
     * {@link HierarchicalTypeDescriptor} that may be obtained from the {@link CodeModel}.
     *
     * @return a {@link Stream} of <strong>immediate</strong> <i>parent</i> {@link HierarchicalTypeDescriptor}s
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for a <i>parent</i> can't be obtained
     */
    default Stream<HierarchicalTypeDescriptor> parents() {
        final var codeModel = codeModel();

        return parentTypeNames()
            .map(typeName -> codeModel
                .getTypeDescriptor(typeName, HierarchicalTypeDescriptor.class)
                .orElseThrow(() -> new IllegalStateException(
                    "The parent TypeDescriptor for [" + typeName + "] is not defined in the CodeModel for the type ["
                        + typeName() + "]")));
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s of the <strong>immediate</strong> <i>parent</i> types of this
     * {@link HierarchicalTypeDescriptor} that may be obtained from the {@link CodeModel}, which are assignable to the
     * specified {@link Class}.
     *
     * @param <T>                 the type of the {@link Class}
     * @param typeDescriptorClass the required {@link Class}
     * @return a {@link Stream} of <strong>immediate</strong> <i>parent</i> {@link HierarchicalTypeDescriptor}s
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for a <i>parent</i> can't be obtained
     * @see #parents()
     */
    default <T> Stream<T> parents(final Class<T> typeDescriptorClass) {
        return parents()
            .filter(typeDescriptorClass::isInstance)
            .map(typeDescriptorClass::cast);
    }

    /**
     * Obtains the maximal level of the {@link HierarchicalTypeDescriptor} in the {@link HierarchicalCodeModel},
     * a <i>root</i> {@link HierarchicalTypeDescriptor} being level {@code 0}.
     *
     * @return the level of the {@link HierarchicalTypeDescriptor}
     */
    default int level() {
        return level(new HashSet<>());
    }

    private int level(final Set<TypeName> visited) {
        if (!visited.add(typeName())) {
            throw new IllegalStateException("Cycle detected in type hierarchy at: " + typeName());
        }
        try {
            return parents()
                .map(p -> p.level(visited))
                .max(Integer::compareTo)
                .map(level -> level + 1)
                .orElse(0);
        } finally {
            visited.remove(typeName());
        }
    }

    /**
     * Obtains the set of {@link TypeName}s of the <i>ancestor</i> types.
     *
     * @return a {@link Stream} of the {@link TypeName}s of the <i>ancestor</i> types
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for an <i>ancestor</i> can't be obtained
     */
    default Stream<TypeName> ancestorTypeNames() {
        return ancestors()
            .map(TypeDescriptor::typeName);
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s of the <i>ancestor</i> types of this
     * {@link HierarchicalTypeDescriptor} that may be obtained from the {@link CodeModel}.
     *
     * @return a {@link Stream} of <i>ancestor</i> {@link HierarchicalTypeDescriptor}s
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for an <i>ancestor</i> can't be obtained
     */
    default Stream<HierarchicalTypeDescriptor> ancestors() {
        final var ancestors = new LinkedHashSet<HierarchicalTypeDescriptor>();

        final var parents = parents()
            .collect(Collectors.toCollection(LinkedHashSet::new));

        while (!parents.isEmpty()) {
            final var parent = parents.removeFirst();
            ancestors.add(parent);

            parent.parents()
                .filter(ancestor ->
                    !ancestors.contains(ancestor) && !parents.contains(ancestor))
                .forEach(parents::add);
        }

        return ancestors.stream();
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s of the <i>ancestor</i> types of this
     * {@link HierarchicalTypeDescriptor} that may be obtained from the {@link CodeModel}, which are assignable to the
     * specified {@link Class}.
     *
     * <p><strong>WARNING:</strong> {@link HierarchicalTypeDescriptor}s that can't be obtained through the use of
     * {@link CodeModel#getTypeDescriptor(TypeName)} will not be returned.</p>
     *
     * @param <T>                 the type of the {@link Class}
     * @param typeDescriptorClass the required {@link Class}
     * @return a {@link Stream} of <i>ancestor</i> {@link HierarchicalTypeDescriptor}s
     * @see #ancestors()
     */
    default <T> Stream<T> ancestors(final Class<T> typeDescriptorClass) {
        return ancestors()
            .filter(typeDescriptorClass::isInstance)
            .map(typeDescriptorClass::cast);
    }

    /**
     * Obtains the <i>first</i> <i>ancestor</i> {@link HierarchicalTypeDescriptor} that satisfies the {@link Predicate}.
     *
     * @param predicate the <i>ancestor</i> {@link Predicate}
     * @return the {@link Optional}ly found <i>first</i> <i>ancestor</i>, otherwise {@link Optional#empty()}
     */
    default Optional<HierarchicalTypeDescriptor> getAncestor(final Predicate<? super HierarchicalTypeDescriptor> predicate) {
        return getAncestor(predicate, new HashSet<>());
    }

    private Optional<HierarchicalTypeDescriptor> getAncestor(final Predicate<? super HierarchicalTypeDescriptor> predicate,
                                                             final Set<TypeName> visited) {
        if (!hasParents()) {
            return Optional.empty();
        }

        if (!visited.add(typeName())) {
            throw new IllegalStateException("Cycle detected in type hierarchy at: " + typeName());
        }
        try {
            // first attempt to locate a parent that satisfies the Predicate
            final var optional = parents()
                .filter(predicate)
                .findFirst();

            if (optional.isPresent()) {
                return optional;
            }

            // now attempt to locate a parent of the parent that satisfies the Predicate
            return parents()
                .map(parent -> parent.getAncestor(predicate, visited))
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .findFirst();
        } finally {
            visited.remove(typeName());
        }
    }

    /**
     * Determines if the {@link HierarchicalTypeDescriptor} is responsible for forming a
     * <a href="https://en.wikipedia.org/wiki/Multiple_inheritance">Diamond Pattern</a>.
     *
     * @param exclusionPredicate a {@link Predicate} to <strong>exclude</strong> <i>ancestors</i> from consideration,
     *                           or {@code null} to include all <i>ancestors</i>
     * @return {@code true} if the {@link HierarchicalTypeDescriptor} forms a <i>Diamond Pattern</i>,
     * otherwise {@code false}
     */
    default boolean formsDiamondPattern(final Predicate<? super HierarchicalTypeDescriptor> exclusionPredicate) {

        final var filteredParents = parents()
            .filter(parent -> exclusionPredicate == null || !exclusionPredicate.test(parent))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (filteredParents.size() < 2) {
            // only when there are multiple parent types may a diamond be formed
            return false;
        }

        final var iter = filteredParents.iterator();

        // initially use the first parent's ancestors
        final var ancestors = iter.next()
            .ancestors()
            .filter(ancestor -> exclusionPredicate == null || !exclusionPredicate.test(ancestor))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // determine the intersection of the ancestors of remaining parents
        while (iter.hasNext()) {
            ancestors.retainAll(iter.next()
                .ancestors()
                .filter(ancestor -> exclusionPredicate == null || !exclusionPredicate.test(ancestor))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        return !ancestors.isEmpty();
    }

    /**
     * Determines if the {@link HierarchicalTypeDescriptor} is responsible for forming a
     * <a href="https://en.wikipedia.org/wiki/Multiple_inheritance">Diamond Pattern</a>.
     *
     * @return {@code true} if the {@link HierarchicalTypeDescriptor} forms a <i>Diamond Pattern</i>,
     * otherwise {@code false}
     */
    default boolean formsDiamondPattern() {
        return formsDiamondPattern(null);
    }

    /**
     * Determines if this {@link HierarchicalTypeDescriptor} is an <strong>immediate</strong> <i>parent</i> of the
     * specified <i>child</i> {@link TypeName}.
     *
     * @param childTypeName the {@link TypeName}
     * @return {@code true} if the specified {@link TypeName} is an <strong>immediate</strong> <i>child</i>,
     * otherwise {@code false}
     */
    default boolean isChild(final TypeName childTypeName) {
        final var codeModel = codeModel();

        return childTypeName != null
            && codeModel.getTypeDescriptor(childTypeName, HierarchicalTypeDescriptor.class)
            .filter(this::isChild)
            .isPresent();
    }

    /**
     * Determines if this {@link HierarchicalTypeDescriptor} is an <strong>immediate</strong> <i>parent</i> of the
     * specified <i>child</i> {@link HierarchicalTypeDescriptor}.
     *
     * @param childTypeDescriptor the proposed <i>child</i> {@link HierarchicalTypeDescriptor}
     * @return {@code true} if the specified {@link HierarchicalTypeDescriptor} is an <strong>immediate</strong>
     * <i>child</i>, otherwise {@code false}
     */
    default boolean isChild(final HierarchicalTypeDescriptor childTypeDescriptor) {
        return childTypeDescriptor != null
            && childTypeDescriptor
            .traits(ParentTypeDescriptor.class, ParentTypeDescriptor::parentTypeName, typeName())
            .findAny()
            .isPresent();
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s that may be obtained from the {@link CodeModel} of the
     * <strong>immediate</strong> <i>child</i> types for which this {@link HierarchicalTypeDescriptor} is an
     * <strong>immediate</strong> <i>parent</i>.
     *
     * @return a {@link Stream} of <strong>immediate</strong> <i>child</i> {@link HierarchicalTypeDescriptor}s
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for a <i>child</i> can't be obtained
     */
    default Stream<HierarchicalTypeDescriptor> children() {
        return codeModel()
            .typeDescriptors(HierarchicalTypeDescriptor.class)
            .filter(typeDescriptor -> isChild(typeDescriptor.typeName()));
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s that may be obtained from the {@link CodeModel} of the
     * <strong>immediate</strong> <i>child</i> types, which are assignable to the specified {@link Class}, for which
     * this {@link HierarchicalTypeDescriptor} is an <strong>immediate</strong> <i>parent</i>.
     *
     * @param <T>                 the type of the {@link Class}
     * @param typeDescriptorClass the required {@link Class} for which
     * @return a {@link Stream} of <strong>immediate</strong> <i>child</i> {@link HierarchicalTypeDescriptor}s
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for a <i>child</i> can't be obtained
     * @see #children()
     */
    default <T> Stream<T> children(final Class<T> typeDescriptorClass) {
        return children()
            .filter(typeDescriptorClass::isInstance)
            .map(typeDescriptorClass::cast);
    }

    /**
     * Obtains the set of {@link TypeName}s of the <i>descendant</i> types.
     *
     * @return a {@link Stream} of the {@link TypeName}s of the <i>descendant</i> types
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for a <i>descendant</i> can't be obtained
     */
    default Stream<TypeName> descendantTypeNames() {
        return descendants()
            .map(TypeDescriptor::typeName);
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s of the <i>descendant</i> types of this
     * {@link HierarchicalTypeDescriptor} that may be obtained from the {@link CodeModel}.
     *
     * @return a {@link Stream} of <i>descendant</i> {@link HierarchicalTypeDescriptor}s
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for a <i>descendant</i> can't be obtained
     */
    default Stream<HierarchicalTypeDescriptor> descendants() {
        final var descendants = new LinkedHashSet<HierarchicalTypeDescriptor>();

        final var children = children()
            .collect(Collectors.toCollection(LinkedHashSet::new));

        while (!children.isEmpty()) {
            final var child = children.removeFirst();
            descendants.add(child);

            child.children()
                .filter(descendant ->
                    !descendants.contains(descendant) && !children.contains(descendant))
                .forEach(children::add);
        }

        return descendants.stream();

    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s of the <i>descendant</i> types of this
     * {@link HierarchicalTypeDescriptor} that may be obtained from the {@link CodeModel}, which are assignable to the
     * specified {@link Class}.
     *
     * @return a {@link Stream} of <i>descendant</i> {@link HierarchicalTypeDescriptor}s
     * @throws IllegalStateException when a {@link HierarchicalTypeDescriptor} for a <i>descendant</i> can't be obtained
     * @see #descendants()
     */
    default <T> Stream<T> descendants(final Class<T> typeDescriptorClass) {
        return descendants()
            .filter(typeDescriptorClass::isInstance)
            .map(typeDescriptorClass::cast);
    }

    /**
     * Determines if the {@link HierarchicalTypeDescriptor} is <i>assignable</i> to the specified {@link TypeName} or
     * one of its <i>ancestors</i>.
     *
     * @param typeName the {@link TypeName}
     * @return {@code true} if the {@link HierarchicalTypeDescriptor} is assignable, {@code false} otherwise
     */
    default boolean isAssignableTo(final TypeName typeName) {
        return typeName != null
            && (typeName().equals(typeName)
            || getAncestor(ancestor -> ancestor.typeName().equals(typeName)).isPresent());
    }

    /**
     * Determines if the {@link HierarchicalTypeDescriptor} is <i>assignable</i> to the specified
     * {@link HierarchicalTypeDescriptor} or one of its <i>ancestors</i>.
     *
     * @param typeDescriptor the {@link HierarchicalTypeDescriptor}
     * @return {@code true} if the {@link HierarchicalTypeDescriptor} is assignable, {@code false} otherwise
     */
    default boolean isAssignableTo(final HierarchicalTypeDescriptor typeDescriptor) {
        return typeDescriptor != null && isAssignableTo(typeDescriptor.typeName());
    }
}
