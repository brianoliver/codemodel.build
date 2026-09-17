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
 * A bare name reference — local variable, type name, {@code this}, or {@code super}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class Identifier
    extends AbstractExpression {

    /**
     * The identifier name.
     */
    private final String name;

    private Identifier(final CodeModel codeModel, final String name) {
        super(codeModel);
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    @Unmarshal
    public Identifier(@Bound final CodeModel codeModel,
                      final Marshaller marshaller,
                      final Stream<Marshalled<Trait>> traits,
                      final String name) {
        super(codeModel, marshaller, traits);
        this.name = name;
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<String> name) {
        super.destructor(marshaller, traits);
        name.set(this.name);
    }

    /**
     * Obtains the identifier name.
     *
     * @return the identifier name
     */
    public String name() {
        return this.name;
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof Identifier other
            && Objects.equals(this.name, other.name)
            && super.equals(other);
    }

    @Override
    public String toString() {
        return this.name;
    }

    /**
     * Creates an {@link Identifier} expression.
     *
     * @param codeModel the {@link CodeModel}
     * @param name      the identifier name
     * @return a new {@link Identifier}
     */
    public static Identifier of(final CodeModel codeModel, final String name) {
        return new Identifier(codeModel, name);
    }

    static {
        Marshalling.register(Identifier.class, MethodHandles.lookup());
    }
}
