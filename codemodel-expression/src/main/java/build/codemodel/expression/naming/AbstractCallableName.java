package build.codemodel.expression.naming;

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

import build.base.marshalling.Marshal;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Out;
import build.codemodel.foundation.naming.CallableName;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.Namespace;
import build.codemodel.foundation.naming.TypeName;

import java.util.Objects;
import java.util.Optional;

/**
 * An abstract {@link CallableName} implementation.
 *
 * @author brian.oliver
 * @since Mar-2024
 */
public abstract class AbstractCallableName
    implements CallableName {

    /**
     * The {@link Optional} {@link ModuleName} in which the {@link CallableName} is defined.
     */
    private Optional<ModuleName> moduleName;

    /**
     * The {@link Optional} {@link Namespace} defining the {@link CallableName}.
     */
    private Optional<Namespace> namespace;

    /**
     * The {@link Optional} {@link TypeName} in which this {@link CallableName} is defined.
     */
    private Optional<TypeName> typeName;

    /**
     * The {@link IrreducibleName} of the {@link CallableName}.
     */
    private final IrreducibleName irreducibleName;

    /**
     * The internal {@link String} representation of the {@link CallableName}
     */
    private final String string;

    /**
     * Constructs a {@link AbstractCallableName}.
     *
     * @param moduleName      the {@link Optional} {@link ModuleName}
     * @param namespace       the {@link Optional} {@link Namespace}
     * @param typeName        the {@link Optional}  {@link TypeName}
     * @param irreducibleName the {@link IrreducibleName} for the {@link TypeName}
     */
    protected AbstractCallableName(final Optional<ModuleName> moduleName,
                                   final Optional<Namespace> namespace,
                                   final Optional<TypeName> typeName,
                                   final IrreducibleName irreducibleName) {

        this.moduleName = moduleName == null ? Optional.empty() : moduleName;
        this.namespace = namespace == null ? Optional.empty() : namespace;
        this.typeName = typeName == null ? Optional.empty() : typeName;
        this.irreducibleName =
            Objects.requireNonNull(irreducibleName, "The IrreducibleName must not be null");

        this.string =
            (this.typeName.isPresent()
                ? this.typeName
                .map(TypeName::toString)
                .map(name -> name + ".")
                .orElse("")
                : this.moduleName
                .map(name -> name.toString() + "/")
                .orElse("")
                  + this.namespace()
                .map(Namespace::toString)
                .map(name -> name + ".")
                .orElse(""))
                + this.irreducibleName;
    }

    /**
     * Un{@link Marshal} an {@link AbstractCallableName}.
     *
     * @param marshaller      the {@link Marshaller}
     * @param moduleName      the {@link Optional} {@link ModuleName}
     * @param namespace       the {@link Optional} {@link Namespace}
     * @param typeName        the {@link Optional} {@link TypeName}
     * @param irreducibleName the {@link IrreducibleName} for the {@link AbstractCallableName}
     * @param string          the {@link String} representation of the {@link AbstractCallableName}
     */
    protected AbstractCallableName(final Marshaller marshaller,
                                   final Optional<ModuleName> moduleName,
                                   final Optional<Namespace> namespace,
                                   final Optional<TypeName> typeName,
                                   final IrreducibleName irreducibleName,
                                   final String string) {

        this.moduleName = moduleName;
        this.namespace = namespace;
        this.typeName = typeName;
        this.irreducibleName = irreducibleName;
        this.string = string;
    }

    /**
     * {@link Marshal} an {@link AbstractCallableName}.
     *
     * @param marshaller      the {@link Marshaller}
     * @param moduleName      the {@link Out} parameter for {@link Optional} {@link ModuleName}
     * @param namespace       the {@link Out} parameter for {@link Optional} {@link Namespace}
     * @param typeName        the {@link Out} parameter for {@link Optional} {@link TypeName}
     * @param irreducibleName the {@link Out} parameter for {@link IrreducibleName}
     * @param string          the {@link Out} parameter for {@link String}
     */
    protected void destructor(final Marshaller marshaller,
                              final Out<Optional<ModuleName>> moduleName,
                              final Out<Optional<Namespace>> namespace,
                              final Out<Optional<TypeName>> typeName,
                              final Out<IrreducibleName> irreducibleName,
                              final Out<String> string) {

        moduleName.set(this.moduleName);
        namespace.set(this.namespace);
        typeName.set(this.typeName);
        irreducibleName.set(this.irreducibleName);
        string.set(this.string);
    }

    @Override
    public boolean matches(final String regularExpression) {
        return this.string.matches(regularExpression);
    }

    @Override
    public Optional<ModuleName> moduleName() {
        return this.moduleName;
    }

    @Override
    public Optional<Namespace> namespace() {
        return this.namespace;
    }

    @Override
    public Optional<TypeName> typeName() {
        return this.typeName;
    }

    @Override
    public IrreducibleName name() {
        return this.irreducibleName;
    }

    @Override
    public int length() {
        return this.string.length();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        return object instanceof CallableName that
            && Objects.equals(this.string, that.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.string);
    }

    @Override
    public String toString() {
        return this.string;
    }
}
