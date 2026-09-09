package build.codemodel.objectoriented.expression;

/*-
 * #%L
 * Object-Oriented Code Model
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

import build.base.marshalling.Bound;
import build.base.marshalling.Marshal;
import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Marshalling;
import build.base.marshalling.Out;
import build.base.marshalling.Unmarshal;
import build.codemodel.expression.AbstractExpression;
import build.codemodel.expression.Expression;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.usage.TypeUsage;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An {@link Expression} representing access to {@code this}.
 *
 * @author brian.oliver
 * @since Sep-2024
 */
public class ThisUsage
    extends AbstractExpression {

    /**
     * Constructs a {@link ThisUsage}.
     *
     * @param codeModel the {@link CodeModel} in which {@code this} occurs
     */
    private ThisUsage(final CodeModel codeModel) {
        super(codeModel);
    }

    /**
     * {@link Unmarshal} a {@link ThisUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param traits     the {@link Marshalled} {@link Trait}s
     */
    @Unmarshal
    public ThisUsage(@Bound final CodeModel codeModel,
                     final Marshaller marshaller,
                     final Stream<Marshalled<Trait>> traits) {
        super(codeModel, marshaller, traits);
    }

    /**
     * {@link Marshal} a {@link ThisUsage}.
     *
     * @param marshaller the {@link Marshaller}
     * @param traits     the {@link Marshalled} {@link Trait}s
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits) {
        super.destructor(marshaller, traits);
    }

    @Override
    public Optional<TypeUsage> type() {
        // the TypeUsage for the ThisUsage is inferred from the definition of the Method
        return Optional.empty();
    }

    /**
     * Creates a {@link ThisUsage}.
     *
     * @param codeModel the {@link CodeModel} in which {@code this} occurs
     */
    public static ThisUsage of(final CodeModel codeModel) {
        return new ThisUsage(codeModel);
    }

    static {
        // register this type to be usable for marshalling
        Marshalling.register(ThisUsage.class, MethodHandles.lookup());
    }
}
