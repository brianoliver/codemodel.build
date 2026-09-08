package build.codemodel.jdk.descriptor;

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
import build.codemodel.foundation.descriptor.AbstractTraitable;
import build.codemodel.foundation.descriptor.NonSingular;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.Traitable;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.usage.TypeUsage;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@link Trait} representing a record component on a record type descriptor.
 *
 * <p>Being a {@link Traitable}, a {@link RecordComponentDescriptor} can carry its own traits -
 * notably the {@code AnnotationTypeUsage}s for annotations whose {@code @Target} is exactly
 * {@code RECORD_COMPONENT}, which per JLS 9.7.4 propagate only to the record component and not to
 * the backing field, constructor parameter, or accessor method.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
@NonSingular
public final class RecordComponentDescriptor
    extends AbstractTraitable
    implements Trait, Traitable {

    /**
     * The {@link IrreducibleName} of the record component.
     */
    private final IrreducibleName name;

    /**
     * The declared {@link TypeUsage} of the record component.
     */
    private final TypeUsage type;

    private RecordComponentDescriptor(final CodeModel codeModel, final IrreducibleName name, final TypeUsage type) {
        super(codeModel);
        this.name = Objects.requireNonNull(name, "The name must not be null");
        this.type = Objects.requireNonNull(type, "The type must not be null");
    }

    /**
     * {@link Unmarshal} a {@link RecordComponentDescriptor}.
     *
     * @param codeModel  the {@link CodeModel}
     * @param marshaller the {@link Marshaller} for unmarshalling the {@link Marshalled} {@link Trait}s
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param name       the {@link IrreducibleName} of the record component
     * @param type       the declared {@link TypeUsage} of the record component
     */
    @Unmarshal
    public RecordComponentDescriptor(@Bound final CodeModel codeModel,
                                     final Marshaller marshaller,
                                     final Stream<Marshalled<Trait>> traits,
                                     final IrreducibleName name,
                                     final TypeUsage type) {

        super(codeModel, marshaller, traits);
        this.name = name;
        this.type = type;
    }

    /**
     * {@link Marshal} a {@link RecordComponentDescriptor}.
     *
     * @param marshaller the {@link Marshaller}
     * @param traits     the {@link Marshalled} {@link Trait}s
     * @param name       the {@link IrreducibleName} of the record component
     * @param type       the declared {@link TypeUsage} of the record component
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<IrreducibleName> name,
                           final Out<TypeUsage> type) {

        super.destructor(marshaller, traits);
        name.set(this.name);
        type.set(this.type);
    }

    /**
     * Obtains the {@link IrreducibleName} of the record component.
     *
     * @return the {@link IrreducibleName} of the record component
     */
    public IrreducibleName name() {
        return this.name;
    }

    /**
     * Obtains the declared {@link TypeUsage} of the record component.
     *
     * @return the {@link TypeUsage} of the record component
     */
    public TypeUsage type() {
        return this.type;
    }

    @Override
    protected Stream<? extends Composite> compositeChildren() {
        return Stream.of(this.type);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        return object instanceof RecordComponentDescriptor other
            && Objects.equals(this.name, other.name)
            && Objects.equals(this.type, other.type)
            && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.type, super.hashCode());
    }

    /**
     * Creates a {@link RecordComponentDescriptor}.
     *
     * @param codeModel the {@link CodeModel}
     * @param name      the {@link IrreducibleName} of the record component
     * @param type      the declared {@link TypeUsage} of the record component
     * @return a new {@link RecordComponentDescriptor}
     */
    public static RecordComponentDescriptor of(final CodeModel codeModel,
                                               final IrreducibleName name,
                                               final TypeUsage type) {

        return new RecordComponentDescriptor(codeModel, name, type);
    }

    static {
        Marshalling.register(RecordComponentDescriptor.class, MethodHandles.lookup());
    }
}
