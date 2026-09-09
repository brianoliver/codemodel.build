package build.codemodel.foundation.usage;

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
import build.base.marshalling.Bound;
import build.base.marshalling.Marshal;
import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Marshalling;
import build.base.marshalling.Out;
import build.base.marshalling.Unmarshal;
import build.base.mereology.Composite;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.TypeName;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Provides type information concerning the <i>usage</i> of a generic <i>type variable</i>, for example the
 * <i>generic type</i> for an attribute, the <i>generic type</i> returned by a method or the <i>generic type</i> for a
 * parameter.
 *
 * @author brian.oliver
 * @see TypeDescriptor
 * @since Feb-2024
 */

public class TypeVariableUsage
    extends AbstractNamedTypeUsage {

    /**
     * The {@link Lazy} {@link TypeUsage} of the <i>lower-bound</i>.
     */
    private final Optional<Lazy<TypeUsage>> lowerBound;

    /**
     * The {@link Lazy} {@link TypeUsage} of the <i>upper-bound</i>.
     */
    private final Optional<Lazy<TypeUsage>> upperBound;

    /**
     * Constructs a {@link TypeVariableUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param typeName   the {@link TypeName}
     * @param lowerBound the {@link Optional} {@link Lazy} {@link TypeUsage} of the <i>lower-bound</i>
     * @param upperBound the {@link Optional} {@link Lazy} {@link TypeUsage} of the <i>upper-bound</i>
     */
    protected TypeVariableUsage(final CodeModel codeModel,
                                final TypeName typeName,
                                final Optional<Lazy<TypeUsage>> lowerBound,
                                final Optional<Lazy<TypeUsage>> upperBound) {

        super(codeModel, typeName);
        this.lowerBound = Objects.requireNonNull(lowerBound, "The lower-bound TypeUsage must not be null");
        this.upperBound = Objects.requireNonNull(upperBound, "The upper-bound TypeUsage must not be null");
    }

    /**
     * {@link Unmarshal} a {@link TypeVariableUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param typeName   the {@link TypeName}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param lowerBound the {@link Optional} {@link TypeUsage} of the <i>lower-bound</i>
     * @param upperBound the {@link Optional} {@link TypeUsage} of the <i>upper-bound</i>
     */
    @Unmarshal
    public TypeVariableUsage(@Bound final CodeModel codeModel,
                             final Marshaller marshaller,
                             final TypeName typeName,
                             final Stream<Marshalled<Trait>> traits,
                             final Optional<TypeUsage> lowerBound,
                             final Optional<TypeUsage> upperBound) {

        super(codeModel, marshaller, typeName, traits);

        this.lowerBound = lowerBound.map(Lazy::of);
        this.upperBound = upperBound.map(Lazy::of);
    }

    /**
     * {@link Marshal} an {@link TypeVariableUsage}.
     *
     * @param marshaller the {@link Marshaller}
     * @param typeName   the {@link TypeName}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param lowerBound the {@link Optional} {@link TypeUsage} of the <i>lower-bound</i>
     * @param upperBound the {@link Optional} {@link TypeUsage} of the <i>upper-bound</i>
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<TypeName> typeName,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Optional<TypeUsage>> lowerBound,
                           final Out<Optional<TypeUsage>> upperBound) {

        super.destructor(marshaller, typeName, traits);

        lowerBound.set(this.lowerBound.map(Lazy::get));
        upperBound.set(this.upperBound.map(Lazy::get));
    }

    /**
     * Obtains the {@link Optional} <i>lower-bound</i> of the type.
     *
     * @return the {@link Optional} {@link TypeUsage} representing the <i>lower-bound</i>
     */
    public Optional<TypeUsage> lowerBound() {
        return this.lowerBound.map(Lazy::get);
    }

    /**
     * Obtains the {@link Optional} <i>upper-bound</i> of the type.
     *
     * @return the {@link Optional} {@link TypeUsage} representing the <i>upper-bound</i>
     */
    public Optional<TypeUsage> upperBound() {
        return this.upperBound.map(Lazy::get);
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Stream.of(lowerBound(), upperBound()).flatMap(Optional::stream);
    }

    @Override
    public int hashCode() {
        return super.typeName().hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof TypeVariableUsage other
            && Objects.equals(lowerBound().map(TypeVariableUsage::boundIdentity),
                              other.lowerBound().map(TypeVariableUsage::boundIdentity))
            && Objects.equals(upperBound().map(TypeVariableUsage::boundIdentity),
                              other.upperBound().map(TypeVariableUsage::boundIdentity))
            && super.equals(other);
    }

    @Override
    protected String render(final Function<TypeName, String> nameRenderer,
                            final Function<TypeUsage, String> usageRenderer) {
        // Render the type variable as its own simple name (e.g. "T"), never the enclosing-type-scoped
        // model form (toString "Foo$T", canonicalName "Foo.T") that distinguishes it from a same-named
        // variable declared elsewhere: a declaration and every back-reference to it from within a bound
        // must read as the same source identifier. renderBound applies the same rule to any
        // TypeVariableUsage nested inside a bound; boundIdentity deliberately does not, since equals
        // still needs to tell those same-named variables apart.
        return typeName().name().toString()
            + upperBound().map(b -> " extends " + renderBound(b, nameRenderer, usageRenderer)).orElse("")
            + lowerBound().map(b -> " super " + renderBound(b, nameRenderer, usageRenderer)).orElse("");
    }

    /**
     * Renders a type variable's bound. Two concerns are handled here:
     * <ul>
     *   <li>A nested {@link TypeVariableUsage} renders as its bare simple name - matching
     *       {@link #render}, not its enclosing-type-scoped {@link TypeName} - so a self-reference such
     *       as the inner {@code T} in {@code T extends Comparable<T>} reads as {@code T}.</li>
     *   <li>That nested {@link TypeVariableUsage} is rendered <em>without</em> descending into its own
     *       bound. Otherwise a self-referential bound would recurse forever: {@code T}'s bound contains
     *       {@code T} again, however deeply nested inside other {@link AbstractTypeUsage} containers
     *       (e.g. the {@link UnionTypeUsage}/{@link IntersectionTypeUsage} of {@code T extends Number &
     *       Comparable<T>}). The guard has to be threaded through every level of that nesting - not
     *       just the immediate bound - so every recursive step re-enters {@code renderBound} rather
     *       than falling back to the plain {@code usageRenderer}.</li>
     * </ul>
     */
    private static String renderBound(final TypeUsage bound,
                                      final Function<TypeName, String> nameRenderer,
                                      final Function<TypeUsage, String> usageRenderer) {
        return switch (bound) {
            case TypeVariableUsage typeVariableUsage -> typeVariableUsage.typeName().name().toString();
            case AbstractTypeUsage abstractTypeUsage ->
                abstractTypeUsage.render(nameRenderer, usage -> renderBound(usage, nameRenderer, usageRenderer));
            default -> usageRenderer.apply(bound);
        };
    }

    /**
     * A recursion-safe structural key for a bound, used only by {@link #equals}. It mirrors
     * {@link #renderBound}'s traversal - including its refusal to descend into a nested
     * {@link TypeVariableUsage}'s own bound, which is what stops a self-referential bound from
     * recursing forever - but, unlike {@link #renderBound}, keeps a nested type variable's
     * enclosing-type-scoped {@link TypeName#canonicalName()} instead of reducing it to a bare simple
     * name. Two bounds that reference same-named type variables declared on different types (e.g.
     * {@code Comparable<T>} where one {@code T} is declared on {@code Foo} and the other on {@code
     * Bar}) must not compare equal merely because both render for display as {@code Comparable<T>}.
     */
    private static String boundIdentity(final TypeUsage bound) {
        return switch (bound) {
            case TypeVariableUsage typeVariableUsage -> typeVariableUsage.typeName().canonicalName();
            case AbstractTypeUsage abstractTypeUsage ->
                abstractTypeUsage.render(TypeName::canonicalName, TypeVariableUsage::boundIdentity);
            default -> bound.canonicalName();
        };
    }

    /**
     * Creates a {@link TypeVariableUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param typeName   the {@link TypeName}
     * @param lowerBound the {@link Optional} {@link Lazy} {@link TypeUsage} of the <i>lower-bound</i>
     * @param upperBound the {@link Optional} {@link Lazy} {@link TypeUsage} of the <i>upper-bound</i>
     * @return a {@link TypeVariableUsage}
     */
    public static TypeVariableUsage of(final CodeModel codeModel,
                                       final TypeName typeName,
                                       final Optional<Lazy<TypeUsage>> lowerBound,
                                       final Optional<Lazy<TypeUsage>> upperBound) {

        return new TypeVariableUsage(codeModel, typeName, lowerBound, upperBound);
    }

    static {
        Marshalling.register(TypeVariableUsage.class, MethodHandles.lookup());
    }
}
