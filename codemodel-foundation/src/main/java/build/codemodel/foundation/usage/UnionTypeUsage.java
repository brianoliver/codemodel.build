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
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.TypeName;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides type information concerning the <i>usage</i> of multiple types.
 *
 * @author brian.oliver
 * @see TypeDescriptor
 * @since Feb-2024
 */
public class UnionTypeUsage
    extends AbstractTypeUsage {

    /**
     * The {@link Lazy} {@link TypeUsage}s.
     */
    private final ArrayList<Lazy<TypeUsage>> types;

    /**
     * Constructs a {@link UnionTypeUsage}.
     *
     * @param codeModel the {@link CodeModel}
     * @param types     the {@link Lazy} {@link TypeUsage} in the {@link UnionTypeUsage}
     */
    private UnionTypeUsage(final CodeModel codeModel,
                           final Stream<Lazy<TypeUsage>> types) {

        super(codeModel);

        this.types = types == null
            ? new ArrayList<>()
            : types.collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * {@link Unmarshal} an {@link UnionTypeUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param types      the {@link Lazy} {@link TypeUsage}s
     */
    @Unmarshal
    public UnionTypeUsage(@Bound final CodeModel codeModel,
                          final Marshaller marshaller,
                          final Stream<Marshalled<Trait>> traits,
                          final Stream<Lazy<TypeUsage>> types) {

        super(codeModel, marshaller, traits);

        this.types = types
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * {@link Marshal} an {@link UnionTypeUsage}.
     *
     * @param marshaller the {@link Marshaller}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param types      the {@link TypeUsage}s
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Stream<TypeUsage>> types) {

        super.destructor(marshaller, traits);

        types.set(this.types.stream()
            .map(Lazy::get));
    }

    /**
     * Obtains the types in the union.
     *
     * @return the {@link Stream} of types in the union
     */
    public Stream<TypeUsage> types() {
        return this.types.stream().map(Lazy::get);
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return types();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        return object instanceof UnionTypeUsage other
            && Streams.equals(types(), other.types())
            && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Streams.hashCode(types());
    }

    @Override
    protected String render(final Function<TypeName, String> nameRenderer,
                            final Function<TypeUsage, String> usageRenderer) {
        return types().map(usageRenderer).collect(Collectors.joining(" | ", " ", ""));
    }

    /**
     * Creates a {@link UnionTypeUsage}.
     *
     * @param codeModel the {@link CodeModel}
     * @param types     the {@link Lazy} {@link TypeUsage} in the {@link UnionTypeUsage}
     * @return a new {@link UnionTypeUsage}
     */
    public static UnionTypeUsage of(final CodeModel codeModel,
                                    final Stream<Lazy<TypeUsage>> types) {

        return new UnionTypeUsage(codeModel, types);
    }

    /**
     * Creates a {@link UnionTypeUsage} with the specified parameters.
     *
     * @param codeModel the {@link CodeModel}
     * @param types     the {@link TypeUsage}s
     * @return a new {@link GenericTypeUsage}
     */
    public static UnionTypeUsage of(final CodeModel codeModel,
                                    final TypeUsage... types) {

        return new UnionTypeUsage(codeModel, Streams.of(types).map(Lazy::of));
    }

    static {
        Marshalling.register(UnionTypeUsage.class, MethodHandles.lookup());
    }
}
