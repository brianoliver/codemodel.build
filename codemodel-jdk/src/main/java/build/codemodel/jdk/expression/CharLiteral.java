package build.codemodel.jdk.expression;

/*-
 * #%L
 * JDK Code Model
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
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A character literal expression: {@code 'a'}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class CharLiteral
    extends AbstractExpression {

    /**
     * The character value of the literal.
     */
    private final char value;

    private CharLiteral(final CodeModel codeModel, final char value) {
        super(codeModel);
        this.value = value;
    }

    @Unmarshal
    public CharLiteral(@Bound final CodeModel codeModel,
                       final Marshaller marshaller,
                       final Stream<Marshalled<Trait>> traits,
                       final Character value) {
        super(codeModel, marshaller, traits);
        this.value = value;
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Character> value) {
        super.destructor(marshaller, traits);
        value.set(this.value);
    }

    /**
     * Obtains the character value of the literal.
     *
     * @return the {@code char} value
     */
    public char value() {
        return this.value;
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof CharLiteral other
            && this.value == other.value
            && super.equals(other);
    }

    @Override
    public String toString() {
        return "'" + this.value + "'";
    }

    /**
     * Creates a {@link CharLiteral} expression.
     *
     * @param codeModel the {@link CodeModel}
     * @param value     the {@code char} value
     * @return a new {@link CharLiteral}
     */
    public static CharLiteral of(final CodeModel codeModel, final char value) {
        return new CharLiteral(Objects.requireNonNull(codeModel, "codeModel must not be null"), value);
    }

    static {
        Marshalling.register(CharLiteral.class, MethodHandles.lookup());
    }
}
