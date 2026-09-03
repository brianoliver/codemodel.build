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

import build.base.foundation.stream.Streams;
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
 * A method invocation expression: {@code [target.]method(args)}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class MethodInvocation
    extends AbstractExpression {

    /**
     * The optional receiver expression, absent for unqualified calls.
     */
    private final Optional<Expression> target;

    /**
     * The simple name of the invoked method.
     */
    private final String methodName;

    /**
     * The argument expressions.
     */
    private final ArrayList<Expression> args;

    /**
     * The resolved type of the receiver, if available.
     */
    private final Optional<TypeUsage> receiverType;

    /**
     * The explicit type witnesses supplied at the call site (e.g. {@code <String>} in
     * {@code obj.<String>method()}), empty when none were written.
     */
    private final ArrayList<TypeUsage> typeWitnesses;

    private MethodInvocation(final CodeModel codeModel,
                             final Optional<Expression> target,
                             final String methodName,
                             final Stream<Expression> args,
                             final Optional<TypeUsage> receiverType,
                             final Stream<TypeUsage> typeWitnesses) {
        super(codeModel);
        this.target = target == null ? Optional.empty() : target;
        this.methodName = Objects.requireNonNull(methodName, "methodName must not be null");
        this.args = args == null
            ? new ArrayList<>()
            : args.collect(Collectors.toCollection(ArrayList::new));
        this.receiverType = receiverType == null ? Optional.empty() : receiverType;
        this.typeWitnesses = typeWitnesses == null
            ? new ArrayList<>()
            : typeWitnesses.collect(Collectors.toCollection(ArrayList::new));
    }

    @Unmarshal
    public MethodInvocation(@Bound final CodeModel codeModel,
                            final Marshaller marshaller,
                            final Stream<Marshalled<Trait>> traits,
                            final Optional<Marshalled<Expression>> target,
                            final String methodName,
                            final Stream<Marshalled<Expression>> args,
                            final Optional<Marshalled<TypeUsage>> receiverType,
                            final Stream<Marshalled<TypeUsage>> typeWitnesses) {
        super(codeModel, marshaller, traits);
        this.target = target == null ? Optional.empty() : target.map(marshaller::unmarshal);
        this.methodName = methodName;
        this.args = args == null
            ? new ArrayList<>()
            : args.map(marshaller::unmarshal).collect(Collectors.toCollection(ArrayList::new));
        this.receiverType = receiverType == null ? Optional.empty() : receiverType.map(marshaller::unmarshal);
        this.typeWitnesses = typeWitnesses == null
            ? new ArrayList<>()
            : typeWitnesses.map(marshaller::unmarshal).collect(Collectors.toCollection(ArrayList::new));
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Optional<Marshalled<Expression>>> target,
                           final Out<String> methodName,
                           final Out<Stream<Marshalled<Expression>>> args,
                           final Out<Optional<Marshalled<TypeUsage>>> receiverType,
                           final Out<Stream<Marshalled<TypeUsage>>> typeWitnesses) {
        super.destructor(marshaller, traits);
        target.set(this.target.map(marshaller::marshal));
        methodName.set(this.methodName);
        args.set(this.args.stream().map(marshaller::marshal));
        receiverType.set(this.receiverType.map(marshaller::marshal));
        typeWitnesses.set(this.typeWitnesses.stream().map(marshaller::marshal));
    }

    /**
     * Obtains the optional receiver expression.
     *
     * @return an {@link Optional} receiver {@link Expression}
     */
    public Optional<Expression> target() {
        return this.target;
    }

    /**
     * Obtains the simple name of the invoked method.
     *
     * @return the method name
     */
    public String methodName() {
        return this.methodName;
    }

    /**
     * Obtains the argument expressions.
     *
     * @return a {@link Stream} of argument {@link Expression}s
     */
    public Stream<Expression> args() {
        return this.args.stream();
    }

    /**
     * Obtains the resolved type of the receiver, if available.
     *
     * @return an {@link Optional} {@link TypeUsage} for the receiver
     */
    public Optional<TypeUsage> receiverType() {
        return this.receiverType;
    }

    /**
     * Obtains the explicit type witnesses supplied at the call site (e.g. {@code <String>} in
     * {@code obj.<String>method()}).
     *
     * @return a {@link Stream} of type witness {@link TypeUsage}s, empty when none were written
     */
    public Stream<TypeUsage> typeWitnesses() {
        return this.typeWitnesses.stream();
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Streams.concat(
            target.stream(),
            args.stream(),
            receiverType.stream(),
            typeWitnesses.stream()
        );
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof MethodInvocation other
            && Objects.equals(this.target, other.target)
            && Objects.equals(this.methodName, other.methodName)
            && Objects.equals(this.args, other.args)
            && Objects.equals(this.receiverType, other.receiverType)
            && Objects.equals(this.typeWitnesses, other.typeWitnesses)
            && super.equals(other);
    }

    /**
     * Creates a {@link MethodInvocation} expression with no explicit type witnesses.
     *
     * @param codeModel    the {@link CodeModel}
     * @param target       the optional receiver {@link Expression}
     * @param methodName   the simple name of the invoked method
     * @param args         the argument {@link Expression}s
     * @param receiverType the resolved type of the receiver, if available
     * @return a new {@link MethodInvocation}
     */
    public static MethodInvocation of(final CodeModel codeModel,
                                      final Optional<Expression> target,
                                      final String methodName,
                                      final Stream<Expression> args,
                                      final Optional<TypeUsage> receiverType) {
        return new MethodInvocation(codeModel, target, methodName, args, receiverType, null);
    }

    /**
     * Creates a {@link MethodInvocation} expression.
     *
     * @param codeModel     the {@link CodeModel}
     * @param target        the optional receiver {@link Expression}
     * @param methodName    the simple name of the invoked method
     * @param args          the argument {@link Expression}s
     * @param receiverType  the resolved type of the receiver, if available
     * @param typeWitnesses the explicit type witnesses supplied at the call site (e.g.
     *                      {@code <String>} in {@code obj.<String>method()})
     * @return a new {@link MethodInvocation}
     */
    public static MethodInvocation of(final CodeModel codeModel,
                                      final Optional<Expression> target,
                                      final String methodName,
                                      final Stream<Expression> args,
                                      final Optional<TypeUsage> receiverType,
                                      final Stream<TypeUsage> typeWitnesses) {
        return new MethodInvocation(codeModel, target, methodName, args, receiverType, typeWitnesses);
    }

    static {
        Marshalling.register(MethodInvocation.class, MethodHandles.lookup());
    }
}
