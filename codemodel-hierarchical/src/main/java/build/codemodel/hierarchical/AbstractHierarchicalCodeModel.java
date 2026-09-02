package build.codemodel.hierarchical;

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

import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Unmarshal;
import build.codemodel.foundation.AbstractCodeModel;
import build.codemodel.foundation.descriptor.ModuleDescriptor;
import build.codemodel.foundation.descriptor.NamespaceDescriptor;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.Traitable;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.hierarchical.descriptor.HierarchicalTypeDescriptor;
import build.codemodel.hierarchical.descriptor.ParentTypeDescriptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

/**
 * An {@code abstract} {@link HierarchicalCodeModel}.
 *
 * @author brian.oliver
 * @since Mar-2025
 */
public abstract class AbstractHierarchicalCodeModel
    extends AbstractCodeModel
    implements HierarchicalCodeModel {

    /**
     * The <i>parent</i> {@link TypeName}s for which one or more <i>child</i> {@link HierarchicalTypeDescriptor}s
     * require them, but as yet the {@link HierarchicalTypeDescriptor} for the said <i>parent</i> is unknown.
     * <p>
     * The set values are {@link CopyOnWriteArraySet}s: a shared {@link HierarchicalCodeModel} is populated from
     * multiple threads (e.g. concurrent {@code spin} module compiles), so {@link #children(HierarchicalTypeDescriptor)}
     * / {@link #parents(HierarchicalTypeDescriptor)} can stream a set while another thread is adding an edge to it.
     * {@link CopyOnWriteArraySet} gives those readers a stable, insertion-ordered snapshot with no synchronization
     * instead of a {@link java.util.ConcurrentModificationException}.
     */
    private ConcurrentHashMap<TypeName, CopyOnWriteArraySet<HierarchicalTypeDescriptor>> orphanedChildren;

    /**
     * The <i>parent</i> {@link HierarchicalTypeDescriptor}s by <i>child</i> {@link HierarchicalTypeDescriptor}.
     * <p>
     * See {@link #orphanedChildren} for why the set values are {@link CopyOnWriteArraySet}s.
     */
    private ConcurrentHashMap<HierarchicalTypeDescriptor, CopyOnWriteArraySet<HierarchicalTypeDescriptor>> parents;

    /**
     * The <i>child</i> {@link HierarchicalTypeDescriptor}s by <i>parents</i> {@link HierarchicalTypeDescriptor}.
     * <p>
     * See {@link #orphanedChildren} for why the set values are {@link CopyOnWriteArraySet}s.
     */
    private ConcurrentHashMap<HierarchicalTypeDescriptor, CopyOnWriteArraySet<HierarchicalTypeDescriptor>> children;

    /**
     * Constructs an empty {@link AbstractHierarchicalCodeModel}.
     *
     * @param nameProvider the {@link NameProvider}
     */
    protected AbstractHierarchicalCodeModel(final NameProvider nameProvider) {
        super(nameProvider);

        this.orphanedChildren = new ConcurrentHashMap<>();
        this.parents = new ConcurrentHashMap<>();
        this.children = new ConcurrentHashMap<>();
    }

    /**
     * {@link Unmarshal}s an {@link AbstractHierarchicalCodeModel} using a {@link Marshaller}.
     *
     * @param nameProvider         the {@link NameProvider}
     * @param marshaller           the {@link Marshaller}
     * @param traits               the {@link Traitable}
     * @param typeDescriptors      the {@link Stream} of {@link Marshalled} {@link TypeDescriptor}s
     * @param moduleDescriptors    the {@link Stream} of {@link Marshalled} {@link ModuleDescriptor}s
     * @param namespaceDescriptors the {@link Stream} of {@link Marshalled} {@link NamespaceDescriptor}s
     */
    protected AbstractHierarchicalCodeModel(final NameProvider nameProvider,
                                            final Marshaller marshaller,
                                            final Stream<Marshalled<Trait>> traits,
                                            final Stream<Marshalled<TypeDescriptor>> typeDescriptors,
                                            final Stream<Marshalled<ModuleDescriptor>> moduleDescriptors,
                                            final Stream<Marshalled<NamespaceDescriptor>> namespaceDescriptors) {

        super(nameProvider, marshaller, traits, typeDescriptors, moduleDescriptors, namespaceDescriptors);
    }

    @Override
    protected void prepareForUnmarshalling() {
        super.prepareForUnmarshalling();

        this.orphanedChildren = new ConcurrentHashMap<>();
        this.parents = new ConcurrentHashMap<>();
        this.children = new ConcurrentHashMap<>();
    }

    @Override
    protected void onCreatedTypeDescriptor(final TypeDescriptor typeDescriptor) {

        if (typeDescriptor instanceof HierarchicalTypeDescriptor hierarchicalTypeDescriptor) {

            // establish child and parent references
            // (we search all Traits that are ParentTypeDescriptors as we don't know if there are subclasses)
            typeDescriptor.traits()
                .filter(ParentTypeDescriptor.class::isInstance)
                .map(ParentTypeDescriptor.class::cast)
                .map(ParentTypeDescriptor::parentTypeName)
                .forEach(parentTypeName ->
                    onCreatedParentTypeDescriptor(parentTypeName, hierarchicalTypeDescriptor));

            // handle if the TypeDescriptor being added is the parent of some orphaned children
            this.orphanedChildren.compute(typeDescriptor.typeName(), (_, existing) -> {
                if (existing == null) {
                    return null;
                }

                existing.forEach(childTypeDescriptor -> {
                    onCreatedParentTypeDescriptor(hierarchicalTypeDescriptor, childTypeDescriptor);
                });

                return null;
            });
        }

        super.onCreatedTypeDescriptor(typeDescriptor);
    }

    /**
     * Invoked when a {@link ParentTypeDescriptor} for the specified <i>parent</i> {@link TypeName} is added to the
     * specified <i>child</i> {@link HierarchicalTypeDescriptor}.
     *
     * @param parentTypeName      the <i>parent</i> {@link TypeName}
     * @param childTypeDescriptor the <i>child</i> {@link HierarchicalTypeDescriptor}
     */
    public void onCreatedParentTypeDescriptor(final TypeName parentTypeName,
                                              final HierarchicalTypeDescriptor childTypeDescriptor) {

        getTypeDescriptor(parentTypeName)
            .filter(HierarchicalTypeDescriptor.class::isInstance)
            .map(HierarchicalTypeDescriptor.class::cast)
            .ifPresentOrElse(parentTypeDescriptor ->
                    onCreatedParentTypeDescriptor(parentTypeDescriptor, childTypeDescriptor),
                () -> onOrphanedChildTypeDescriptor(parentTypeName, childTypeDescriptor));
    }

    /**
     * Invoked when a {@link HierarchicalTypeDescriptor} for the specified <i>parent</i>] is added to the
     * specified <i>child</i> {@link HierarchicalTypeDescriptor}.
     *
     * @param parentTypeDescriptor the <i>parent</i> {@link HierarchicalTypeDescriptor}
     * @param childTypeDescriptor  the <i>child</i> {@link HierarchicalTypeDescriptor}
     */
    void onCreatedParentTypeDescriptor(final HierarchicalTypeDescriptor parentTypeDescriptor,
                                       final HierarchicalTypeDescriptor childTypeDescriptor) {

        addEdge(this.children, parentTypeDescriptor, childTypeDescriptor);
        addEdge(this.parents, childTypeDescriptor, parentTypeDescriptor);
    }

    /**
     * Invoked when a {@link ParentTypeDescriptor} for the specified <i>parent</i> {@link TypeName} is unknown
     * for to the specified <i>child</i> {@link HierarchicalTypeDescriptor}.
     *
     * @param parentTypeName      the <i>parent</i> {@link TypeName}
     * @param childTypeDescriptor the <i>child</i> {@link HierarchicalTypeDescriptor}
     */
    void onOrphanedChildTypeDescriptor(final TypeName parentTypeName,
                                       final HierarchicalTypeDescriptor childTypeDescriptor) {

        addEdge(this.orphanedChildren, parentTypeName, childTypeDescriptor);
    }

    /**
     * Invoked when a {@link ParentTypeDescriptor} for the specified <i>parent</i> {@link TypeName} is removed from the
     * specified <i>child</i> {@link HierarchicalTypeDescriptor}.
     *
     * @param parentTypeName      the <i>parent</i> {@link TypeName}
     * @param childTypeDescriptor the <i>child</i> {@link HierarchicalTypeDescriptor}
     */
    public void onRemovedParentTypeDescriptor(final TypeName parentTypeName,
                                              final HierarchicalTypeDescriptor childTypeDescriptor) {

        getTypeDescriptor(parentTypeName)
            .filter(HierarchicalTypeDescriptor.class::isInstance)
            .map(HierarchicalTypeDescriptor.class::cast)
            .ifPresentOrElse(parentTypeDescriptor -> {
                    // remove the child TypeDescriptor from the Parent TypeDescriptor
                    removeEdge(this.children, parentTypeDescriptor, childTypeDescriptor);

                    // remove the parent TypeDescriptor from child TypeDescriptor (parents)
                    removeEdge(this.parents, childTypeDescriptor, parentTypeDescriptor);
                },
                () ->
                    // attempt to remove the child from orphaned
                    removeEdge(this.orphanedChildren, parentTypeName, childTypeDescriptor));
    }

    /**
     * Adds {@code element} to the set stored under {@code key}, creating the {@link CopyOnWriteArraySet} on first use.
     * The get-or-create and the {@code add} happen inside a single {@link ConcurrentHashMap#compute} so a concurrent
     * {@link #removeEdge} on the same key can't drop the entry between them.
     *
     * @param map     the map to mutate
     * @param key     the key whose set {@code element} is added to
     * @param element the element to add
     * @param <K>     the key type
     */
    private static <K> void addEdge(final ConcurrentHashMap<K, CopyOnWriteArraySet<HierarchicalTypeDescriptor>> map,
                                    final K key,
                                    final HierarchicalTypeDescriptor element) {

        map.compute(key, (_, existing) -> {
            final var set = existing == null ? new CopyOnWriteArraySet<HierarchicalTypeDescriptor>() : existing;
            set.add(element);
            return set;
        });
    }

    /**
     * Removes {@code element} from the set stored under {@code key}, dropping the map entry entirely once its set
     * becomes empty (the {@code parents}/{@code children}/{@code orphanedChildren} maps hold no empty sets - an absent
     * key and an empty set are treated identically by the readers).
     *
     * @param map     the map to mutate
     * @param key     the key whose set {@code element} is removed from
     * @param element the element to remove
     * @param <K>     the key type
     */
    private static <K> void removeEdge(final ConcurrentHashMap<K, CopyOnWriteArraySet<HierarchicalTypeDescriptor>> map,
                                       final K key,
                                       final HierarchicalTypeDescriptor element) {

        map.computeIfPresent(key, (_, set) -> {
            set.remove(element);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Obtains the <i>children</i> for the specified {@link HierarchicalTypeDescriptor}.
     *
     * @param typeDescriptor the {@link HierarchicalTypeDescriptor}
     * @return a {@link Stream} of <i>child</i> {@link HierarchicalTypeDescriptor}s
     */
    public Stream<HierarchicalTypeDescriptor> children(final HierarchicalTypeDescriptor typeDescriptor) {
        final var children = this.children.get(typeDescriptor);
        return children == null ? Stream.empty() : children.stream();
    }

    /**
     * Obtains the <i>parents</i> for the specified {@link HierarchicalTypeDescriptor}.
     *
     * @param typeDescriptor the {@link HierarchicalTypeDescriptor}
     * @return a {@link Stream} of <i>parents</i> {@link HierarchicalTypeDescriptor}s
     */
    public Stream<HierarchicalTypeDescriptor> parents(final HierarchicalTypeDescriptor typeDescriptor) {
        final var parents = this.parents.get(typeDescriptor);
        return parents == null ? Stream.empty() : parents.stream();
    }
}
