package build.codemodel.jdk.statement;

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
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.imperative.AbstractStatement;
import build.codemodel.imperative.Block;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@code catch} clause: {@code catch (ExType param) body}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public final class CatchClause
    extends AbstractStatement {

    /**
     * Whether the exception parameter is declared {@code final}.
     */
    private final boolean isFinal;

    /**
     * The resolved types of the caught exception(s).
     * Has more than one element for multi-catch clauses (e.g. {@code catch (IOException | RuntimeException e)}).
     */
    private final List<TypeUsage> exceptionTypes;

    /**
     * The name of the exception parameter.
     */
    private final String paramName;

    /**
     * The catch body block.
     *
     * <p>Not {@code final}: {@link #ofPending} constructs a {@link CatchClause} before its body is
     * converted, so that the exception parameter's {@code Element} can be registered for
     * {@code Symbol} resolution before body conversion visits identifiers referencing it — the
     * body transitively references this very node, so it cannot be supplied as a constructor
     * argument. {@link #completeBody} sets it exactly once; from any external caller's perspective
     * the node is immutable once returned.
     */
    private Block body;

    private CatchClause(final CodeModel codeModel,
                        final boolean isFinal,
                        final List<TypeUsage> exceptionTypes,
                        final String paramName,
                        final Block body) {
        super(codeModel);
        this.isFinal = isFinal;
        this.exceptionTypes = Objects.requireNonNull(exceptionTypes, "exceptionTypes must not be null");
        this.paramName = Objects.requireNonNull(paramName, "paramName must not be null");
        this.body = body;
    }

    @Unmarshal
    public CatchClause(@Bound final CodeModel codeModel,
                       final Marshaller marshaller,
                       final Stream<Marshalled<Trait>> traits,
                       final Boolean isFinal,
                       final Stream<Marshalled<TypeUsage>> exceptionTypes,
                       final String paramName,
                       final Marshalled<Block> body) {
        super(codeModel, marshaller, traits);
        this.isFinal = isFinal != null && isFinal;
        this.exceptionTypes = exceptionTypes == null ? List.of() : exceptionTypes.map(marshaller::unmarshal).toList();
        this.paramName = paramName;
        this.body = marshaller.unmarshal(body);
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Boolean> isFinal,
                           final Out<Stream<Marshalled<TypeUsage>>> exceptionTypes,
                           final Out<String> paramName,
                           final Out<Marshalled<Block>> body) {
        super.destructor(marshaller, traits);
        isFinal.set(this.isFinal);
        exceptionTypes.set(this.exceptionTypes.stream().map(marshaller::marshal));
        paramName.set(this.paramName);
        body.set(marshaller.marshal(this.body));
    }

    /**
     * Returns {@code true} if the exception parameter is declared {@code final}.
     *
     * @return {@code true} if the parameter is {@code final}
     */
    public boolean isFinal() {
        return this.isFinal;
    }

    /**
     * Obtains the resolved types of the caught exception(s).
     * Returns more than one element for multi-catch clauses.
     *
     * @return a {@link Stream} of exception {@link TypeUsage}s
     */
    public Stream<TypeUsage> exceptionTypes() {
        return this.exceptionTypes.stream();
    }

    /**
     * Obtains the name of the exception parameter.
     *
     * @return the parameter name
     */
    public String paramName() {
        return this.paramName;
    }

    /**
     * Obtains the catch body block.
     *
     * @return the body {@link Block}
     */
    public Block body() {
        return this.body;
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Stream.concat(exceptionTypes.stream(), Stream.of(body));
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof CatchClause other
            && this.isFinal == other.isFinal
            && Objects.equals(this.exceptionTypes, other.exceptionTypes)
            && Objects.equals(this.paramName, other.paramName)
            && Objects.equals(this.body, other.body)
            && super.equals(other);
    }

    /**
     * Creates a {@link CatchClause}.
     *
     * @param codeModel      the {@link CodeModel}
     * @param isFinal        whether the exception parameter is {@code final}
     * @param exceptionTypes the resolved {@link TypeUsage}s of the caught exception(s)
     * @param paramName      the name of the exception parameter
     * @param body           the catch body {@link Block}
     * @return a new {@link CatchClause}
     */
    public static CatchClause of(final CodeModel codeModel,
                                 final boolean isFinal,
                                 final List<TypeUsage> exceptionTypes,
                                 final String paramName,
                                 final Block body) {
        return new CatchClause(codeModel, isFinal, exceptionTypes, paramName,
            Objects.requireNonNull(body, "body must not be null"));
    }

    /**
     * Creates a {@link CatchClause} without its body, for callers that need the node's identity
     * (e.g. to register the exception parameter's declaring construct for {@code Symbol}
     * resolution) before the body can be converted. {@link #completeBody} must be called exactly
     * once before any other code observes this node.
     *
     * @param codeModel      the {@link CodeModel}
     * @param isFinal        whether the exception parameter is {@code final}
     * @param exceptionTypes the resolved {@link TypeUsage}s of the caught exception(s)
     * @param paramName      the name of the exception parameter
     * @return a new bodyless {@link CatchClause}, to be finished with {@link #completeBody}
     */
    public static CatchClause ofPending(final CodeModel codeModel,
                                        final boolean isFinal,
                                        final List<TypeUsage> exceptionTypes,
                                        final String paramName) {
        return new CatchClause(codeModel, isFinal, exceptionTypes, paramName, null);
    }

    /**
     * Sets the body of a {@link CatchClause} created via {@link #ofPending}. May be called exactly
     * once.
     *
     * @param body the catch body {@link Block}
     * @throws IllegalStateException if the body was already set
     */
    public void completeBody(final Block body) {
        if (this.body != null) {
            throw new IllegalStateException("CatchClause body is already set");
        }
        this.body = Objects.requireNonNull(body, "body must not be null");
    }

    static {
        Marshalling.register(CatchClause.class, MethodHandles.lookup());
    }
}
