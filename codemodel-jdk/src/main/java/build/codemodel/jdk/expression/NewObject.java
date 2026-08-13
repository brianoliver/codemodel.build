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
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.TypeUsage;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An object creation expression: {@code new Type(args)}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class NewObject
    extends AbstractExpression {

    /**
     * The resolved type of the class to instantiate.
     */
    private final TypeUsage type;

    /**
     * The constructor argument expressions.
     */
    private final ArrayList<Expression> args;

    /**
     * The resolved type arguments (e.g. {@code <String>} in {@code new ArrayList<String>()}).
     */
    private final ArrayList<TypeUsage> typeArguments;

    /**
     * The outer-instance qualifier, e.g. {@code outer} in {@code outer.new Inner(args)}.
     */
    private final Optional<Expression> outerInstance;

    /**
     * The {@link TypeName} of the anonymous type compiled from this expression's class body (e.g.
     * {@code new Runnable() { ... }}), when it has one.
     */
    private final Optional<TypeName> anonymousBodyType;

    private NewObject(final CodeModel codeModel,
                      final TypeUsage type,
                      final Stream<Expression> args,
                      final Stream<TypeUsage> typeArguments,
                      final Optional<Expression> outerInstance,
                      final Optional<TypeName> anonymousBodyType) {
        super(codeModel);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.args = args == null
            ? new ArrayList<>()
            : args.collect(Collectors.toCollection(ArrayList::new));
        this.typeArguments = typeArguments == null
            ? new ArrayList<>()
            : typeArguments.collect(Collectors.toCollection(ArrayList::new));
        this.outerInstance = outerInstance == null ? Optional.empty() : outerInstance;
        this.anonymousBodyType = anonymousBodyType == null ? Optional.empty() : anonymousBodyType;
    }

    @Unmarshal
    public NewObject(@Bound final CodeModel codeModel,
                     final Marshaller marshaller,
                     final Stream<Marshalled<Trait>> traits,
                     final Marshalled<TypeUsage> type,
                     final Stream<Marshalled<Expression>> args,
                     final Stream<Marshalled<TypeUsage>> typeArguments,
                     final Optional<Marshalled<Expression>> outerInstance,
                     final Optional<TypeName> anonymousBodyType) {
        super(codeModel, marshaller, traits);
        this.type = marshaller.unmarshal(type);
        this.args = args == null
            ? new ArrayList<>()
            : args.map(marshaller::unmarshal).collect(Collectors.toCollection(ArrayList::new));
        this.typeArguments = typeArguments == null
            ? new ArrayList<>()
            : typeArguments.map(marshaller::unmarshal).collect(Collectors.toCollection(ArrayList::new));
        this.outerInstance = outerInstance == null
            ? Optional.empty()
            : outerInstance.map(marshaller::unmarshal);
        this.anonymousBodyType = anonymousBodyType == null ? Optional.empty() : anonymousBodyType;
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Marshalled<TypeUsage>> type,
                           final Out<Stream<Marshalled<Expression>>> args,
                           final Out<Stream<Marshalled<TypeUsage>>> typeArguments,
                           final Out<Optional<Marshalled<Expression>>> outerInstance,
                           final Out<Optional<TypeName>> anonymousBodyType) {
        super.destructor(marshaller, traits);
        type.set(marshaller.marshal(this.type));
        args.set(this.args.stream().map(marshaller::marshal));
        typeArguments.set(this.typeArguments.stream().map(marshaller::marshal));
        outerInstance.set(this.outerInstance.map(marshaller::marshal));
        anonymousBodyType.set(this.anonymousBodyType);
    }

    /**
     * Obtains the resolved type of the class to instantiate.
     *
     * @return the {@link TypeUsage}
     */
    public TypeUsage instantiatedType() {
        return this.type;
    }

    /**
     * Obtains the constructor argument expressions.
     *
     * @return a {@link Stream} of argument {@link Expression}s
     */
    public Stream<Expression> args() {
        return this.args.stream();
    }

    /**
     * Obtains the resolved type arguments (e.g. {@code <String>} in {@code new ArrayList<String>()}).
     *
     * @return a {@link Stream} of type argument {@link TypeUsage}s
     */
    public Stream<TypeUsage> typeArguments() {
        return this.typeArguments.stream();
    }

    /**
     * Obtains the outer-instance qualifier, e.g. {@code outer} in {@code outer.new Inner(args)}.
     *
     * @return an {@link Optional} outer-instance {@link Expression}
     */
    public Optional<Expression> outerInstance() {
        return this.outerInstance;
    }

    /**
     * Obtains the {@link TypeName} of the anonymous type compiled from this expression's class body
     * (e.g. {@code new Runnable() { ... }}), when it has one.
     *
     * @return an {@link Optional} {@link TypeName}
     */
    public Optional<TypeName> anonymousBodyType() {
        return this.anonymousBodyType;
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Streams.concat(
            Stream.of(type),
            args.stream(),
            typeArguments.stream(),
            outerInstance.stream()
        );
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof NewObject other
            && Objects.equals(this.type, other.type)
            && Objects.equals(this.args, other.args)
            && Objects.equals(this.typeArguments, other.typeArguments)
            && Objects.equals(this.outerInstance, other.outerInstance)
            && Objects.equals(this.anonymousBodyType, other.anonymousBodyType)
            && super.equals(other);
    }

    /**
     * Creates a {@link NewObject} expression with type arguments, an outer-instance qualifier, and an
     * anonymous class body type.
     *
     * @param codeModel         the {@link CodeModel}
     * @param type              the resolved {@link TypeUsage} of the class to instantiate
     * @param args              the constructor argument {@link Expression}s
     * @param typeArguments     the resolved type argument {@link TypeUsage}s
     * @param outerInstance     the outer-instance qualifier {@link Expression}, e.g. {@code outer} in
     *                          {@code outer.new Inner(args)}
     * @param anonymousBodyType the {@link TypeName} of the anonymous type compiled from this
     *                          expression's class body, e.g. {@code new Runnable() { ... }}
     * @return a new {@link NewObject}
     */
    public static NewObject of(final CodeModel codeModel,
                               final TypeUsage type,
                               final Stream<Expression> args,
                               final Stream<TypeUsage> typeArguments,
                               final Optional<Expression> outerInstance,
                               final Optional<TypeName> anonymousBodyType) {
        return new NewObject(codeModel, type, args, typeArguments, outerInstance, anonymousBodyType);
    }

    /**
     * Creates a {@link NewObject} expression with type arguments.
     *
     * @param codeModel     the {@link CodeModel}
     * @param type          the resolved {@link TypeUsage} of the class to instantiate
     * @param args          the constructor argument {@link Expression}s
     * @param typeArguments the resolved type argument {@link TypeUsage}s
     * @return a new {@link NewObject}
     */
    public static NewObject of(final CodeModel codeModel,
                               final TypeUsage type,
                               final Stream<Expression> args,
                               final Stream<TypeUsage> typeArguments) {
        return new NewObject(codeModel, type, args, typeArguments, null, null);
    }

    static {
        Marshalling.register(NewObject.class, MethodHandles.lookup());
    }
}
