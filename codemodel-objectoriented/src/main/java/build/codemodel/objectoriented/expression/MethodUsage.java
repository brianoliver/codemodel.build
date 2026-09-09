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
import build.base.mereology.Composite;
import build.codemodel.expression.AbstractExpression;
import build.codemodel.expression.Expression;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.objectoriented.naming.MethodName;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a <a href="https://en.wikipedia.org/wiki/Method_(computer_programming)">Method</a> invocation.
 *
 * @author brian.oliver
 * @since Sep-2024
 */
public class MethodUsage
    extends AbstractExpression {

    /**
     * The {@link Expression} that produces an <i>Object</i> on which the invoke the <i>Method</i>.
     */
    private final Expression expression;

    /**
     * The {@link MethodName} to call.
     */
    private final MethodName methodName;

    /**
     * The actual-parameters for the {@link MethodUsage}.
     */
    private ArrayList<Expression> arguments;

    /**
     * Constructs a {@link MethodUsage}.
     *
     * @param expression the {@link Expression} to obtain the <i>Object</i> on which to invoke the method
     * @param methodName the {@link MethodName} for <i>Method</i> to invoke
     * @param arguments  the arguments for the function
     */
    private MethodUsage(final Expression expression,
                        final MethodName methodName,
                        final Stream<Expression> arguments) {

        super(Objects.requireNonNull(expression, "The Expression must not be null").codeModel());
        this.expression = expression;
        this.methodName = Objects.requireNonNull(methodName, "The MethodName must not be null");
        this.arguments = arguments == null
            ? new ArrayList<>()
            : arguments.collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * {@link Unmarshal} a {@link MethodUsage}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param expression the {@link Marshalled} {@link Expression} to obtain the <i>Object</i> on which to invoke the method
     * @param methodName the {@link MethodName} for <i>Method</i> to invoke
     * @param arguments  the {@link Stream} of {@link Marshalled} {@link Expression} arguments for the function
     */
    @Unmarshal
    public MethodUsage(@Bound final CodeModel codeModel,
                       final Marshaller marshaller,
                       final Stream<Marshalled<Trait>> traits,
                       final Marshalled<Expression> expression,
                       final MethodName methodName,
                       final Stream<Marshalled<Expression>> arguments) {

        super(codeModel, marshaller, traits);

        this.expression = marshaller.unmarshal(expression);
        this.methodName = methodName;
        this.arguments = arguments == null
            ? new ArrayList<>()
            : arguments.map(marshaller::unmarshal).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * {@link Marshal} a {@link MethodUsage}.
     *
     * @param marshaller the {@link Marshaller}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param expression the {@link Marshalled} {@link Expression} to obtain the <i>Object</i> on which to invoke the method
     * @param methodName the {@link MethodName} for <i>Method</i> to invoke
     * @param arguments  the {@link Stream} of {@link Marshalled} {@link Expression} arguments for the function
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Marshalled<Expression>> expression,
                           final Out<MethodName> methodName,
                           final Out<Stream<Marshalled<Expression>>> arguments) {

        super.destructor(marshaller, traits);

        expression.set(marshaller.marshal(this.expression));
        methodName.set(this.methodName);
        arguments.set(this.arguments.stream()
            .filter(argument -> marshaller.isMarshallable(argument.getClass()))
            .map(marshaller::marshal));
    }

    @Override
    public Optional<TypeUsage> type() {
        // the TypeUsage for the FunctionUsage is inferred from the definition of the Function
        return Optional.empty();
    }

    /**
     * Obtains the {@link MethodName}.
     *
     * @return the {@link MethodName}
     */
    public MethodName methodName() {
        return this.methodName;
    }

    /**
     * Obtains the actual-parameter {@link Expression} arguments.
     *
     * @return the actual-parameter {@link Expression} arguments
     */
    public Stream<Expression> arguments() {
        return this.arguments.stream();
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Stream.concat(Stream.of(expression), arguments.stream());
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        return object instanceof MethodUsage other
            && Objects.equals(this.expression, other.expression)
            && Objects.equals(this.methodName, other.methodName)
            && Objects.equals(this.arguments, other.arguments)
            && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.expression, this.methodName, this.arguments, super.hashCode());
    }

    /**
     * Creates a {@link MethodUsage}.
     *
     * @param expression the {@link Expression} to obtain the <i>Object</i> on which to invoke the method
     * @param methodName the {@link MethodName} for <i>Method</i> to invoke
     * @param arguments  the arguments for the function
     */
    public static MethodUsage of(final Expression expression,
                                 final MethodName methodName,
                                 final Stream<Expression> arguments) {

        return new MethodUsage(expression, methodName, arguments);
    }

    /**
     * Creates a {@link MethodUsage}.
     *
     * @param expression the {@link Expression} to obtain the <i>Object</i> on which to invoke the method
     * @param methodName the {@link MethodName} for <i>Method</i> to invoke
     * @param arguments  the arguments for the function
     */
    public static MethodUsage of(final Expression expression,
                                 final MethodName methodName,
                                 final Expression... arguments) {

        return new MethodUsage(expression, methodName, arguments == null ? null : Stream.of(arguments));
    }

    static {
        // register this type to be usable for marshalling
        Marshalling.register(MethodUsage.class, MethodHandles.lookup());
    }
}
