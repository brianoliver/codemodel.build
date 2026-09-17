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
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.usage.TypeUsage;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A class literal expression: {@code SomeType.class} or {@code int.class}.
 * Carries the {@link TypeUsage} of the type named before {@code .class}.
 *
 * @author reed.vonredwitz
 * @since May-2026
 */
public final class ClassLiteral
    extends AbstractExpression {

    private final TypeUsage referencedType;

    private ClassLiteral(final CodeModel codeModel, final TypeUsage referencedType) {
        super(codeModel);
        this.referencedType = Objects.requireNonNull(referencedType, "referencedType must not be null");
    }

    @Unmarshal
    public ClassLiteral(@Bound final CodeModel codeModel,
                        final Marshaller marshaller,
                        final Stream<Marshalled<Trait>> traits,
                        final Marshalled<TypeUsage> referencedType) {
        super(codeModel, marshaller, traits);
        this.referencedType = marshaller.unmarshal(referencedType);
    }

    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Marshalled<TypeUsage>> referencedType) {
        super.destructor(marshaller, traits);
        referencedType.set(marshaller.marshal(this.referencedType));
    }

    public TypeUsage referencedType() {
        return referencedType;
    }

    @Override
    public Stream<? extends Composite> compositeChildren() {
        return Stream.of(referencedType);
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof ClassLiteral other
            && Objects.equals(this.referencedType, other.referencedType)
            && super.equals(other);
    }

    @Override
    public String toString() {
        return this.referencedType.canonicalName() + ".class";
    }

    public static ClassLiteral of(final CodeModel codeModel, final TypeUsage referencedType) {
        return new ClassLiteral(codeModel, referencedType);
    }

    static {
        Marshalling.register(ClassLiteral.class, MethodHandles.lookup());
    }
}
