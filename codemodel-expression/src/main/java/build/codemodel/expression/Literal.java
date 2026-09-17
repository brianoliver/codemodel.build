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
import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Out;
import build.base.mereology.Composite;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.usage.SpecificTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A non-{@code null} <a href="https://en.wikipedia.org/wiki/Literal_(computer_programming)">Literal</a> <i>Value</i>.
 *
 * @param <T> the type of the value
 * @author brian.oliver
 * @since Sep-2024
 */
public class Literal<T>
    extends AbstractExpression {

    /**
     * The non-{@code null} value.
     */
    private final T value;

    /**
     * The {@link Optional}ly defined {@link TypeUsage}.
     */
    private final Optional<TypeUsage> type;

    /**
     * Constructs a {@link Literal}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param value      the {@code null}able value
     * @param valueClass the {@code null}able {@link Class} of value
     */
    protected Literal(final CodeModel codeModel,
                      final T value,
                      final Class<T> valueClass) {

        super(codeModel);

        this.value = Objects.requireNonNull(value, "The Value must not be null");
        this.type = Optional.of(SpecificTypeUsage.of(codeModel, codeModel.getNameProvider().getTypeName(valueClass)));
    }

    /**
     * Constructs a {@link Literal}.
     *
     * @param codeModel the {@link CodeModel}
     * @param value     the {@code null}able value
     * @param type      the {@link TypeUsage}
     */
    protected Literal(final CodeModel codeModel,
                      final T value,
                      final TypeUsage type) {

        super(codeModel);

        this.value = value;
        this.type = Optional.ofNullable(type);
    }

    /**
     * Constructs a {@link Literal}.
     *
     * @param codeModel the {@link CodeModel}
     * @param value     the {@code null}able value
     */
    @SuppressWarnings("unchecked")
    protected Literal(final CodeModel codeModel,
                      final T value) {

        this(codeModel, value, (Class<T>) (value == null ? null : value.getClass()));
    }

    protected Literal(@Bound final CodeModel codeModel,
                      final Marshaller marshaller,
                      final Stream<Marshalled<Trait>> traits,
                      final T value,
                      final Optional<TypeUsage> type) {

        super(codeModel, marshaller, traits);

        this.value = value;
        this.type = type;
    }

    protected void destructor(final Marshaller marshaller,
                              final Out<Stream<Marshalled<Trait>>> traits,
                              final Out<T> value,
                              final Out<Optional<TypeUsage>> type) {

        super.destructor(marshaller, traits);

        value.set(this.value);
        type.set(this.type);
    }

    @Override
    public Optional<TypeUsage> type() {
        return this.type;
    }

    /**
     * Obtains the <i>Literal</i> value
     *
     * @return the <i>Literal</i> value
     */
    public T value() {
        return this.value;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof final Literal<?> that)) {
            return false;
        }
        return Objects.equals(this.value, that.value) && Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value);
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return type.stream();
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }

    /**
     * Creates a {@link Literal}.
     *
     * @param <T>        the type of value
     * @param codeModel  the {@link CodeModel}
     * @param value      the {@code null}able value
     * @param valueClass the {@code null}able {@link Class}
     */
    public static <T> Literal<T> of(final CodeModel codeModel,
                                    final T value,
                                    final Class<T> valueClass) {

        return new Literal<>(codeModel, value, valueClass);
    }

    /**
     * Creates a {@link Literal}.
     *
     * @param <T>       the type of value
     * @param codeModel the {@link CodeModel}
     * @param value     the {@code null}able value
     * @param type      the {@link TypeUsage}
     */
    public static <T> Literal<T> of(final CodeModel codeModel,
                                    final T value,
                                    final TypeUsage type) {

        return new Literal<>(codeModel, value, type);
    }

    /**
     * Creates a {@link Literal}.
     *
     * @param <T>       the type of value
     * @param codeModel the {@link CodeModel}
     * @param value     the {@code null}able value
     */
    public static <T> Literal<T> of(final CodeModel codeModel,
                                    final T value) {

        return new Literal<>(codeModel, value);
    }

//    static {
//        Marshalling.register(Literal.class, MethodHandles.lookup());
//    }
}
