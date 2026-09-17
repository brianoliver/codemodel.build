package build.codemodel.objectoriented.naming;

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

import build.base.marshalling.Marshal;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Marshalling;
import build.base.marshalling.Out;
import build.base.marshalling.Unmarshal;
import build.codemodel.expression.naming.AbstractCallableName;
import build.codemodel.expression.naming.FunctionName;
import build.codemodel.foundation.naming.CallableName;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.Namespace;
import build.codemodel.foundation.naming.TypeName;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

/**
 * A {@link CallableName} for a <i>Method</i>.
 *
 * @author brian.oliver
 * @since Sep-2024
 */
public class MethodName
    extends AbstractCallableName {

    /**
     * Constructs a {@link MethodName}.
     *
     * @param moduleName      the {@link Optional} {@link ModuleName}
     * @param namespace       the {@link Optional} {@link Namespace}
     * @param typeName        the {@link Optional}  {@link TypeName}
     * @param irreducibleName the {@link IrreducibleName} for the {@link TypeName}
     */
    private MethodName(final Optional<ModuleName> moduleName,
                       final Optional<Namespace> namespace,
                       final Optional<TypeName> typeName,
                       final IrreducibleName irreducibleName) {

        super(moduleName, namespace, typeName, irreducibleName);
    }

    /**
     * {@link Unmarshal} a {@link MethodName}.
     *
     * @param marshaller      the {@link Marshaller}
     * @param moduleName      the {@link Optional} {@link ModuleName}
     * @param namespace       the {@link Optional} {@link Namespace}
     * @param typeName        the {@link Optional} {@link TypeName}
     * @param irreducibleName the {@link IrreducibleName} for the {@link MethodName}
     * @param string          the {@link String} representation of the {@link MethodName}
     */
    @Unmarshal
    public MethodName(final Marshaller marshaller,
                      final Optional<ModuleName> moduleName,
                      final Optional<Namespace> namespace,
                      final Optional<TypeName> typeName,
                      final IrreducibleName irreducibleName,
                      final String string) {

        super(marshaller, moduleName, namespace, typeName, irreducibleName, string);
    }

    /**
     * {@link Marshal} a {@link MethodName}.
     *
     * @param marshaller      the {@link Marshaller}
     * @param moduleName      the {@link Out} parameter for {@link Optional} {@link ModuleName}
     * @param namespace       the {@link Out} parameter for {@link Optional} {@link Namespace}
     * @param typeName        the {@link Out} parameter for {@link Optional} {@link TypeName}
     * @param irreducibleName the {@link Out} parameter for {@link IrreducibleName}
     * @param string          the {@link Out} parameter for {@link String}
     */
    @Marshal
    public void destructor(final Marshaller marshaller,
                           final Out<Optional<ModuleName>> moduleName,
                           final Out<Optional<Namespace>> namespace,
                           final Out<Optional<TypeName>> typeName,
                           final Out<IrreducibleName> irreducibleName,
                           final Out<String> string) {

        super.destructor(marshaller, moduleName, namespace, typeName, irreducibleName, string);
    }

    /**
     * Creates a {@link MethodName}.
     *
     * @param moduleName      the {@link Optional} {@link ModuleName}
     * @param namespace       the {@link Optional} {@link Namespace}
     * @param typeName        the {@link Optional} enclosing {@link TypeName}
     * @param irreducibleName the {@link IrreducibleName} for the {@link FunctionName}
     * @return a {@link MethodName}
     */
    public static MethodName of(final Optional<ModuleName> moduleName,
                                final Optional<Namespace> namespace,
                                final Optional<TypeName> typeName,
                                final IrreducibleName irreducibleName) {

        return new MethodName(moduleName, namespace, typeName, irreducibleName);
    }

    static {
        // register this type to be usable for marshalling
        Marshalling.register(MethodName.class, MethodHandles.lookup());
    }
}
