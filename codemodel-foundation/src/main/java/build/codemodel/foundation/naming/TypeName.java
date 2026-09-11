package build.codemodel.foundation.naming;

/*-
 * #%L
 * Code Model Foundation
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

import build.base.foundation.Introspection;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A representation for the name of a <i>Type</i>, optionally scoped by its {@link ModuleName} and {@link Namespace}.
 *
 * @author brian.oliver
 * @since Jan-2024
 */
public final class TypeName
    implements Name, Comparable<TypeName> {

    /**
     * A constant representing an empty {@link TypeName}.
     */
    private static final TypeName EMPTY = new TypeName(Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        IrreducibleName.of(""));

    /**
     * The {@link Optional} {@link ModuleName} that defines the {@link TypeName}.
     */
    private Optional<ModuleName> moduleName;

    /**
     * The {@link Optional} {@link Namespace} defining the {@link TypeName}.
     */
    private Optional<Namespace> namespace;

    /**
     * The {@link Optional} {@link TypeName} in which this {@link TypeName} is defined.
     */
    private Optional<TypeName> enclosingTypeName;

    /**
     * The {@link IrreducibleName} of the {@link TypeName}.
     */
    private final IrreducibleName irreducibleName;

    /**
     * The internal {@link String} representation of the {@link TypeName}
     */
    private final String string;

    /**
     * Constructs a {@link TypeName}.
     *
     * @param moduleName        the {@link Optional} {@link ModuleName}
     * @param namespace         the {@link Optional} {@link Namespace}
     * @param enclosingTypeName the {@link Optional} enclosing {@link TypeName}
     * @param irreducibleName   the {@link IrreducibleName} for the {@link TypeName}
     */
    private TypeName(final Optional<ModuleName> moduleName,
                     final Optional<Namespace> namespace,
                     final Optional<TypeName> enclosingTypeName,
                     final IrreducibleName irreducibleName) {

        this.moduleName = moduleName == null ? Optional.empty() : moduleName;
        this.namespace = namespace == null ? Optional.empty() : namespace;
        this.enclosingTypeName = enclosingTypeName == null ? Optional.empty() : enclosingTypeName;
        this.irreducibleName =
            Objects.requireNonNull(irreducibleName, "The name of the type must not be null");

        this.string = this.moduleName.map(m -> m + "/").orElse("") + binaryName(this.namespace, this.enclosingTypeName, this.irreducibleName);
    }

    /**
     * The {@link Optional} {@link ModuleName} in which the {@link TypeName} is defined.
     *
     * @return the {@link Optional} {@link ModuleName}
     */
    public Optional<ModuleName> moduleName() {
        return this.moduleName;
    }

    /**
     * The {@link Optional} {@link Namespace} in which the {@link TypeName} is defined.
     *
     * @return the {@link Optional} {@link Namespace}
     */
    public Optional<Namespace> namespace() {
        return this.namespace;
    }

    /**
     * Obtains the {@link Optional} {@link TypeName} in which this {@link TypeName} is defined (the
     * enclosing type).
     *
     * @return the {@link Optional} {@link TypeName}
     */
    public Optional<TypeName> enclosingTypeName() {
        return this.enclosingTypeName;
    }

    /**
     * The {@link IrreducibleName} name of the {@link TypeName}.
     *
     * @return the {@link IrreducibleName}
     */
    public IrreducibleName name() {
        return this.irreducibleName;
    }

    /**
     * The names of the JDK primitive types, used to detect the synthetic {@code java.lang} namespace primitives
     * are internally represented with despite not actually residing there. Derived from
     * {@link Introspection#primitives()} rather than hardcoded, so it can't drift from the JDK's actual set of
     * primitive types.
     */
    private static final Set<String> PRIMITIVE_NAMES = Introspection.primitives()
        .map(Class::getName)
        .collect(Collectors.toUnmodifiableSet());

    /**
     * Obtains the <i>canonical-name</i> (dot-separated, Java source form), stripping the synthetic
     * {@code java.lang} namespace primitives are internally represented with.
     *
     * @return the canonical name
     */
    public String canonicalName() {
        return switch (enclosingTypeName().orElse(null)) {
            case TypeName enclosing -> enclosing.canonicalName() + "." + name();
            case null -> switch (namespace().orElse(null)) {
                case Namespace _ when isPrimitive() -> name().toString();
                case Namespace ns -> ns + "." + name().toString();
                case null -> name().toString();
            };
        };
    }

    /**
     * Determines whether this {@link TypeName} represents one of the JDK primitive types, modeled with a synthetic
     * {@code java.lang} namespace despite not actually residing there. Kept private: this is JDK-specific knowledge
     * needed only to make {@link #canonicalName()} correct, not something other modules should couple to. Callers
     * outside this class that need the same determination should use {@code TypeUsages.isPrimitive(TypeName)} in
     * {@code codemodel-jdk}, which is the appropriate layer for JDK-specific concerns.
     */
    private boolean isPrimitive() {
        return namespace().map(ns -> "java.lang".equals(ns.toString())).orElse(false)
            && PRIMITIVE_NAMES.contains(name().toString());
    }

    private static String binaryName(final Optional<Namespace> namespace,
                                     final Optional<TypeName> enclosingTypeName,
                                     final IrreducibleName irreducibleName) {
        final var prefix = enclosingTypeName
            .map(e -> e.binaryName() + "$")
            .orElseGet(() -> namespace.map(ns -> ns + ".").orElse(""));
        return prefix + irreducibleName;
    }

    /**
     * Obtains the <i>binary-name</i> (dot-separated package, {@code $}-separated nested types),
     * suitable for use with Class#forName.
     *
     * @return the binary name
     */
    public String binaryName() {
        return binaryName(this.namespace, this.enclosingTypeName, this.irreducibleName);
    }

    @Override
    public boolean matches(final String regularExpression) {
        return this.string.matches(regularExpression);
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

        return object instanceof TypeName that
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

    @Override
    public int compareTo(final TypeName other) {
        return this.string.compareTo(other.toString());
    }

    /**
     * Creates a {@link TypeName}.
     *
     * @param moduleName        the {@link Optional} {@link ModuleName}
     * @param namespace         the {@link Optional} {@link Namespace}
     * @param enclosingTypeName the {@link Optional} enclosing {@link TypeName}
     * @param irreducibleName   the {@link IrreducibleName} for the {@link TypeName}
     * @return an {@link TypeName}
     */
    public static TypeName of(final Optional<ModuleName> moduleName,
                              final Optional<Namespace> namespace,
                              final Optional<TypeName> enclosingTypeName,
                              final IrreducibleName irreducibleName) {

        return new TypeName(moduleName, namespace, enclosingTypeName, irreducibleName);
    }

    /**
     * Obtains an empty {@link TypeName}.
     *
     * @return an empty {@link TypeName}
     */
    public static TypeName empty() {
        return EMPTY;
    }
}
