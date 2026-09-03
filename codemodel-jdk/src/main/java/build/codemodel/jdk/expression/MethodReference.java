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
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A method reference expression: {@code qualifier::method}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class MethodReference
    extends AbstractExpression {

    /**
     * The qualifier expression (the left-hand side of {@code ::}).
     */
    private final Expression qualifier;

    /**
     * The simple name of the referenced method.
     */
    private final String methodName;

    /**
     * The resolved type of the qualifier, if available.
     */
    private final Optional<TypeUsage> qualifierType;

    /**
     * The explicit type witnesses supplied before the method name (e.g. {@code <String>} in
     * {@code Qualifier::<String>method}), empty when none were written.
     */
    private final ArrayList<TypeUsage> typeWitnesses;

    private MethodReference(final Expression qualifier,
                            final String methodName,
                            final Optional<TypeUsage> qualifierType,
                            final Stream<TypeUsage> typeWitnesses) {
        super(Objects.requireNonNull(qualifier, "qualifier must not be null").codeModel());
        this.qualifier = qualifier;
        this.methodName = Objects.requireNonNull(methodName, "methodName must not be null");
        this.qualifierType = qualifierType == null ? Optional.empty() : qualifierType;
        this.typeWitnesses = typeWitnesses == null
            ? new ArrayList<>()
            : typeWitnesses.collect(Collectors.toCollection(ArrayList::new));
    }

    @Unmarshal
    public MethodReference(@Bound final CodeModel codeModel,
                           final Marshaller marshaller,
                           final Stream<Marshalled<Trait>> traits,
                           final Marshalled<Expression> qualifier,
                           final String methodName,
                           final Optional<Marshalled<TypeUsage>> qualifierType,
                           final Stream<Marshalled<TypeUsage>> typeWitnesses) {
        super(codeModel, marshaller, traits);
        this.qualifier = marshaller.unmarshal(qualifier);
        this.methodName = methodName;
        this.qualifierType = qualifierType == null ? Optional.empty() : qualifierType.map(marshaller::unmarshal);
        this.typeWitnesses = typeWitnesses == null
            ? new ArrayList<>()
            : typeWitnesses.map(marshaller::unmarshal).collect(Collectors.toCollection(ArrayList::new));
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Marshalled<Expression>> qualifier,
                           final Out<String> methodName,
                           final Out<Optional<Marshalled<TypeUsage>>> qualifierType,
                           final Out<Stream<Marshalled<TypeUsage>>> typeWitnesses) {
        super.destructor(marshaller, traits);
        qualifier.set(marshaller.marshal(this.qualifier));
        methodName.set(this.methodName);
        qualifierType.set(this.qualifierType.map(marshaller::marshal));
        typeWitnesses.set(this.typeWitnesses.stream().map(marshaller::marshal));
    }

    /**
     * Obtains the qualifier expression.
     *
     * @return the qualifier {@link Expression}
     */
    public Expression qualifier() {
        return this.qualifier;
    }

    /**
     * Obtains the simple name of the referenced method.
     *
     * @return the method name
     */
    public String methodName() {
        return this.methodName;
    }

    /**
     * Obtains the resolved type of the qualifier, if available.
     *
     * @return an {@link Optional} {@link TypeUsage} for the qualifier
     */
    public Optional<TypeUsage> qualifierType() {
        return this.qualifierType;
    }

    /**
     * Obtains the explicit type witnesses supplied before the method name (e.g. {@code <String>} in
     * {@code Qualifier::<String>method}).
     *
     * @return a {@link Stream} of type witness {@link TypeUsage}s, empty when none were written
     */
    public Stream<TypeUsage> typeWitnesses() {
        return this.typeWitnesses.stream();
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Stream.concat(
            Stream.concat(Stream.of(qualifier), qualifierType.stream()),
            typeWitnesses.stream());
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof MethodReference other
            && Objects.equals(this.qualifier, other.qualifier)
            && Objects.equals(this.methodName, other.methodName)
            && Objects.equals(this.qualifierType, other.qualifierType)
            && Objects.equals(this.typeWitnesses, other.typeWitnesses)
            && super.equals(other);
    }

    /**
     * Creates a {@link MethodReference} expression with no explicit type witnesses.
     *
     * @param qualifier     the qualifier {@link Expression}
     * @param methodName    the simple name of the referenced method
     * @param qualifierType the resolved type of the qualifier, if available
     * @return a new {@link MethodReference}
     */
    public static MethodReference of(final Expression qualifier,
                                     final String methodName,
                                     final Optional<TypeUsage> qualifierType) {
        return new MethodReference(qualifier, methodName, qualifierType, null);
    }

    /**
     * Creates a {@link MethodReference} expression.
     *
     * @param qualifier     the qualifier {@link Expression}
     * @param methodName    the simple name of the referenced method
     * @param qualifierType the resolved type of the qualifier, if available
     * @param typeWitnesses the explicit type witnesses supplied before the method name (e.g.
     *                      {@code <String>} in {@code Qualifier::<String>method})
     * @return a new {@link MethodReference}
     */
    public static MethodReference of(final Expression qualifier,
                                     final String methodName,
                                     final Optional<TypeUsage> qualifierType,
                                     final Stream<TypeUsage> typeWitnesses) {
        return new MethodReference(qualifier, methodName, qualifierType, typeWitnesses);
    }

    static {
        Marshalling.register(MethodReference.class, MethodHandles.lookup());
    }
}
