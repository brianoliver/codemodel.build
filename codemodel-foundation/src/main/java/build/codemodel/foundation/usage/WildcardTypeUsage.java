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
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.naming.TypeName;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides type information concerning the <i>usage</i> of a generic <i>wildcard variable</i>.
 *
 * @author brian.oliver
 * @since Feb-2024
 */

public class WildcardTypeUsage
    extends TypeVariableUsage {

    /**
     * Constructs a {@link WildcardTypeUsage}.
     *
     * @param codeModel the {@link CodeModel}
     */
    private WildcardTypeUsage(final CodeModel codeModel) {

        super(codeModel,
            codeModel.getNameProvider().getTypeName(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                codeModel.getNameProvider().getIrreducibleName("wildcard")),
            Optional.empty(),
            Optional.empty());
    }

    /**
     * Constructs a bounded {@link WildcardTypeUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param lowerBound the {@link Optional} {@link Lazy} lower-bound {@link TypeUsage} (from {@code ? super T})
     * @param upperBound the {@link Optional} {@link Lazy} upper-bound {@link TypeUsage} (from {@code ? extends T})
     */
    private WildcardTypeUsage(final CodeModel codeModel,
                              final Optional<Lazy<TypeUsage>> lowerBound,
                              final Optional<Lazy<TypeUsage>> upperBound) {

        super(codeModel,
            codeModel.getNameProvider().getTypeName(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                codeModel.getNameProvider().getIrreducibleName("wildcard")),
            lowerBound,
            upperBound);
    }

    /**
     * {@link Unmarshal} a {@link WildcardTypeUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param typeName   the {@link TypeName}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param lowerBound the {@link Optional} {@link Lazy} lower-bound {@link TypeUsage} (from {@code ? super T})
     * @param upperBound the {@link Optional} {@link Lazy} upper-bound {@link TypeUsage} (from {@code ? extends T})
     */
    @Unmarshal
    public WildcardTypeUsage(@Bound final CodeModel codeModel,
                             final Marshaller marshaller,
                             final TypeName typeName,
                             final Stream<Marshalled<Trait>> traits,
                             final Optional<Lazy<TypeUsage>> lowerBound,
                             final Optional<Lazy<TypeUsage>> upperBound) {

        super(codeModel, marshaller, typeName, traits, lowerBound, upperBound);
    }

    /**
     * {@link Marshal} a {@link WildcardTypeUsage}.
     *
     * @param marshaller the {@link Marshaller}
     * @param typeName   the {@link TypeName}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param lowerBound the {@link Optional} lower-bound {@link TypeUsage} (from {@code ? super T})
     * @param upperBound the {@link Optional} upper-bound {@link TypeUsage} (from {@code ? extends T})
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<TypeName> typeName,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Optional<TypeUsage>> lowerBound,
                           final Out<Optional<TypeUsage>> upperBound) {

        super.destructor(marshaller, typeName, traits, lowerBound, upperBound);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof WildcardTypeUsage other
            && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerBound(), upperBound());
    }

    /**
     * Creates a {@link WildcardTypeUsage}.
     *
     * @param codeModel the {@link CodeModel}
     * @return a {@link WildcardTypeUsage}
     */
    public static WildcardTypeUsage create(final CodeModel codeModel) {
        return new WildcardTypeUsage(codeModel);
    }

    /**
     * Creates a bounded {@link WildcardTypeUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param lowerBound the {@link Optional} {@link Lazy} lower-bound {@link TypeUsage} (from {@code ? super T})
     * @param upperBound the {@link Optional} {@link Lazy} upper-bound {@link TypeUsage} (from {@code ? extends T})
     * @return a {@link WildcardTypeUsage}
     */
    public static WildcardTypeUsage of(final CodeModel codeModel,
                                       final Optional<Lazy<TypeUsage>> lowerBound,
                                       final Optional<Lazy<TypeUsage>> upperBound) {
        return new WildcardTypeUsage(codeModel, lowerBound, upperBound);
    }

    static {
        Marshalling.register(WildcardTypeUsage.class, MethodHandles.lookup());
    }
}
