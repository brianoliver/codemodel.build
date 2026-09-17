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
 * The {@code null} literal expression.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class NullLiteral
    extends AbstractExpression {

    private NullLiteral(final CodeModel codeModel) {
        super(codeModel);
    }

    @Unmarshal
    public NullLiteral(@Bound final CodeModel codeModel,
                       final Marshaller marshaller,
                       final Stream<Marshalled<Trait>> traits) {
        super(codeModel, marshaller, traits);
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits) {
        super.destructor(marshaller, traits);
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof NullLiteral && super.equals(object);
    }

    @Override
    public String toString() {
        return "null";
    }

    /**
     * Creates a {@link NullLiteral} expression.
     *
     * @param codeModel the {@link CodeModel}
     * @return a new {@link NullLiteral}
     */
    public static NullLiteral of(final CodeModel codeModel) {
        return new NullLiteral(Objects.requireNonNull(codeModel, "codeModel must not be null"));
    }

    static {
        Marshalling.register(NullLiteral.class, MethodHandles.lookup());
    }
}
