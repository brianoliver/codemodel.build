package build.codemodel.expression;

/*-
 * #%L
 * Expression Code Model
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
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.usage.TypeUsage;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines a {@link String}-based {@link Literal}.
 *
 * @author brian.oliver
 * @since Feb-2025
 */
public class StringLiteral
    extends Literal<String> {

    /**
     * Constructs a {@link StringLiteral}.
     *
     * @param codeModel the {@link CodeModel}
     * @param value     the {@link String} value
     */
    private StringLiteral(final CodeModel codeModel,
                          final String value) {

        super(codeModel, value);
    }

    /**
     * Unmarshalling constructor for {@link StringLiteral}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param value      the {@link String} value
     * @param type       the {@link Optional} {@link TypeUsage} for the {@link StringLiteral}
     */
    @Unmarshal
    public StringLiteral(@Bound final CodeModel codeModel,
                         final Marshaller marshaller,
                         final Stream<Marshalled<Trait>> traits,
                         final String value,
                         final Optional<TypeUsage> type) {

        super(codeModel, marshaller, traits, value, type);
    }

    /**
     * {@link Marshal} a {@link StringLiteral}.
     *
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param value      the {@link String} value
     * @param type       the {@link Optional} {@link TypeUsage} for the {@link StringLiteral}
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<String> value,
                           final Out<Optional<TypeUsage>> type) {

        super.destructor(marshaller, traits, value, type);
    }

    /**
     * Creates a {@link StringLiteral}.
     *
     * @param codeModel the {@link CodeModel}
     * @param value     the {@link String} value
     */
    public static StringLiteral of(final CodeModel codeModel,
                                   final String value) {

        return new StringLiteral(codeModel, value);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof final StringLiteral stringLiteral)) {
            return false;
        }
        return Objects.equals(this.value(), stringLiteral.value()) && Objects.equals(this.type(), stringLiteral.type());
    }

    @Override
    public String toString() {
        return "\"" + this.value() + "\"";
    }

    static {
        Marshalling.register(StringLiteral.class, MethodHandles.lookup());
    }
}
