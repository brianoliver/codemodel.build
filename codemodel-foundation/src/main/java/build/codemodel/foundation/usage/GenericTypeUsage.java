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
import build.base.foundation.stream.Streams;
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
import build.codemodel.foundation.naming.TypeName;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides type information concerning the <i>usage</i> of a generic type in a parameterized manner.
 *
 * @author brian.oliver
 * @since Jan-2024
 */
public class GenericTypeUsage
    extends AbstractNamedTypeUsage {

    /**
     * The parameters for the {@link GenericTypeUsage} as {@link Lazy} {@link TypeUsage}s.
     */
    private final ArrayList<Lazy<TypeUsage>> parameters;

    /**
     * Constructs a {@link GenericTypeUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param typeName   the {@link TypeName}
     * @param parameters the {@link Lazy} {@link TypeUsage}s
     */
    private GenericTypeUsage(final CodeModel codeModel,
                             final TypeName typeName,
                             final Stream<Lazy<TypeUsage>> parameters) {

        super(codeModel, typeName);

        this.parameters = (parameters == null
            ? Stream.<Lazy<TypeUsage>>empty()
            : parameters)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * {@link Unmarshal} an {@link AbstractNamedTypeUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param typeName   the {@link TypeName}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param parameters the {@link Lazy} {@link TypeUsage}s
     */
    @Unmarshal
    public GenericTypeUsage(@Bound final CodeModel codeModel,
                            final Marshaller marshaller,
                            final TypeName typeName,
                            final Stream<Marshalled<Trait>> traits,
                            final Stream<Lazy<TypeUsage>> parameters) {

        super(codeModel, marshaller, typeName, traits);

        this.parameters = parameters
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * {@link Marshal} an {@link AbstractNamedTypeUsage}.
     *
     * @param marshaller the {@link Marshaller}
     * @param typeName   the {@link TypeName}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param parameters the {@link TypeUsage}s for parameters
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<TypeName> typeName,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Stream<TypeUsage>> parameters) {

        super.destructor(marshaller, typeName, traits);

        parameters.set(this.parameters.stream()
            .map(Lazy::get));
    }

    /**
     * Obtains the generic parameter as {@link TypeUsage}s.
     *
     * @return the {@link TypeUsage}s defining the generic parameters
     */
    public Stream<TypeUsage> parameters() {
        return this.parameters.stream()
            .map(Lazy::get);
    }

    /**
     * Determines if a {@link GenericTypeUsage} is for the {@link Optional} type.
     *
     * @return {@code true} when the {@link GenericTypeUsage} is for the {@link Optional} type,
     * {@code false} otherwise
     */
    public boolean isOptional() {
        return typeName()
            .equals(codeModel().getNameProvider().getTypeName(Optional.class))
            && parameters().count() < 2L;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof GenericTypeUsage other) {
            return Objects.equals(typeName(), other.typeName())
                && super.equals(other)
                && Streams.equals(parameters(), other.parameters());
        }

        return false;
    }

    @Override
    protected String render(final Function<TypeName, String> nameRenderer,
                            final Function<TypeUsage, String> usageRenderer) {
        final var separator = ",";
        return nameRenderer.apply(typeName())
            + parameters()
            .map(usageRenderer)
            .collect(Collectors.joining(separator, "<", ">"));
    }

    /**
     * Creates a {@link GenericTypeUsage} with no specified parameters, the assumption being
     * that the specified {@link TypeName} is defined as a generic type.
     *
     * @param codeModel the {@link CodeModel}
     * @param typeName  the {@link TypeName}
     * @return a new {@link GenericTypeUsage}
     */
    public static GenericTypeUsage of(final CodeModel codeModel,
                                      final TypeName typeName) {

        return new GenericTypeUsage(codeModel, typeName, Stream.empty());
    }

    /**
     * Creates a {@link GenericTypeUsage} with the specified parameters.
     *
     * @param codeModel  the {@link CodeModel}
     * @param typeName   the {@link TypeName}
     * @param parameters the {@link Lazy} {@link TypeUsage} parameters
     * @return a new {@link GenericTypeUsage}
     */
    public static GenericTypeUsage of(final CodeModel codeModel,
                                      final TypeName typeName,
                                      final Stream<Lazy<TypeUsage>> parameters) {

        return new GenericTypeUsage(codeModel, typeName, parameters);
    }

    /**
     * Creates a {@link GenericTypeUsage} with the specified parameters.
     *
     * @param codeModel  the {@link CodeModel}
     * @param typeName   the {@link TypeName}
     * @param parameters the {@link TypeUsage}s
     * @return a new {@link GenericTypeUsage}
     */
    public static GenericTypeUsage of(final CodeModel codeModel,
                                      final TypeName typeName,
                                      final TypeUsage... parameters) {

        return new GenericTypeUsage(codeModel, typeName, Streams.of(parameters).map(Lazy::of));
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return parameters();
    }

    static {
        Marshalling.register(GenericTypeUsage.class, MethodHandles.lookup());
    }
}
