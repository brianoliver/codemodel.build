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
import build.base.mereology.Composite;
import build.codemodel.expression.AbstractExpression;
import build.codemodel.expression.Expression;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.usage.TypeUsage;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A field access expression: {@code target.field}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class FieldAccess
    extends AbstractExpression {

    /**
     * The receiver expression on which the field is accessed.
     */
    private final Expression target;

    /**
     * The name of the accessed field.
     */
    private final String fieldName;

    /**
     * The resolved type of the receiver, if available.
     */
    private final Optional<TypeUsage> receiverType;

    private FieldAccess(final Expression target,
                        final String fieldName,
                        final Optional<TypeUsage> receiverType) {
        super(Objects.requireNonNull(target, "target must not be null").codeModel());
        this.target = target;
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName must not be null");
        this.receiverType = receiverType == null ? Optional.empty() : receiverType;
    }

    @Unmarshal
    public FieldAccess(@Bound final CodeModel codeModel,
                       final Marshaller marshaller,
                       final Stream<Marshalled<Trait>> traits,
                       final Marshalled<Expression> target,
                       final String fieldName,
                       final Optional<Marshalled<TypeUsage>> receiverType) {
        super(codeModel, marshaller, traits);
        this.target = marshaller.unmarshal(target);
        this.fieldName = fieldName;
        this.receiverType = receiverType == null ? Optional.empty() : receiverType.map(marshaller::unmarshal);
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Marshalled<Expression>> target,
                           final Out<String> fieldName,
                           final Out<Optional<Marshalled<TypeUsage>>> receiverType) {
        super.destructor(marshaller, traits);
        target.set(marshaller.marshal(this.target));
        fieldName.set(this.fieldName);
        receiverType.set(this.receiverType.map(marshaller::marshal));
    }

    /**
     * Obtains the receiver expression.
     *
     * @return the receiver {@link Expression}
     */
    public Expression target() {
        return this.target;
    }

    /**
     * Obtains the name of the accessed field.
     *
     * @return the field name
     */
    public String fieldName() {
        return this.fieldName;
    }

    /**
     * Obtains the resolved type of the receiver, if available.
     *
     * @return an {@link Optional} {@link TypeUsage} for the receiver
     */
    public Optional<TypeUsage> receiverType() {
        return this.receiverType;
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Stream.concat(Stream.of(target), receiverType.stream());
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof FieldAccess other
            && Objects.equals(this.target, other.target)
            && Objects.equals(this.fieldName, other.fieldName)
            && Objects.equals(this.receiverType, other.receiverType)
            && super.equals(other);
    }

    @Override
    public String toString() {
        return this.target + "." + this.fieldName;
    }

    /**
     * Creates a {@link FieldAccess} expression.
     *
     * @param target       the receiver {@link Expression}
     * @param fieldName    the name of the accessed field
     * @param receiverType the resolved type of the receiver, if available
     * @return a new {@link FieldAccess}
     */
    public static FieldAccess of(final Expression target,
                                 final String fieldName,
                                 final Optional<TypeUsage> receiverType) {
        return new FieldAccess(target, fieldName, receiverType);
    }

    static {
        Marshalling.register(FieldAccess.class, MethodHandles.lookup());
    }
}
