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

import build.base.foundation.Strings;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A default non-caching implementation of a {@link NameProvider}.
 *
 * @author brian.oliver
 * @since Jan-2024
 */
public class NonCachingNameProvider
    implements NameProvider {

    /**
     * A {@link Function} to extract the constituent parts of a {@link Namespace} from a {@link String} representation
     * of the {@link Namespace}, the first-most {@link String} being the <i>root</i> {@link Namespace}.
     */
    private final Function<? super String, Stream<String>> namespacePartExtractor;

    /**
     * Constructs a {@link NonCachingNameProvider} using the specified {@link Function} to parse/extract
     * {@link Namespace} parts from a specified {@link String} representation of a {@link Namespace},
     * the first-most {@link String} being the <i>root</i> {@link Namespace}.
     *
     * @param namespacePartExtractor the {@link Namespace} part parsing / extraction {@link Function}
     */
    public NonCachingNameProvider(final Function<? super String, Stream<String>> namespacePartExtractor) {
        this.namespacePartExtractor = namespacePartExtractor == null
            ? (string -> Strings.isEmpty(string)
                         ? Stream.empty()
                         : Arrays.stream(string.split("\\."))
            .peek(part -> {
                if (part.isEmpty()) {
                    throw new IllegalArgumentException("A Namespace must not contain empty names");
                }
            }))
            : namespacePartExtractor;
    }

    /**
     * Constructs a {@link NonCachingNameProvider} using a JDK-based (dot-separated) namespace part extraction /
     * parsing {@link Function}.
     */
    public NonCachingNameProvider() {
        // the default Namespace Part Extractor Function assumes a JDK-based packages
        this(null);
    }

    @Override
    public IrreducibleName getIrreducibleName(final String string) {
        return IrreducibleName.of(string);
    }

    @Override
    public Optional<Namespace> getNamespace(final String string) {

        if (Strings.isEmpty(string)) {
            return Optional.empty();
        }

        final var parts = this.namespacePartExtractor.apply(string)
            .map(IrreducibleName::of)
            .toList();

        if (parts.isEmpty()) {
            return Optional.empty();
        }

        Optional<Namespace> namespace = Optional.empty();

        for (IrreducibleName name : parts) {
            namespace = Namespace.of(namespace, name);
        }

        return namespace;
    }

    @Override
    public Optional<ModuleName> getModuleName(final String characters) {

        if (Objects.isNull(characters)) {
            return Optional.empty();
        }

        final var string = characters.trim();

        if (string.isEmpty()) {
            return Optional.empty();
        }

        final var parts = Arrays.stream(string.split("\\."))
            .map(String::trim)
            .peek(part -> {
                if (part.isEmpty()) {
                    throw new IllegalArgumentException(
                        "The module name must not contain empty parts [" + characters + "]");
                }
            })
            .map(IrreducibleName::of);

        return Optional.of(ModuleName.of(parts));
    }

    @Override
    public TypeName getTypeName(final Optional<ModuleName> moduleName,
                                final Optional<Namespace> namespace,
                                final Optional<TypeName> enclosingTypeName,
                                final IrreducibleName irreducibleName) {

        return TypeName.of(moduleName, namespace, enclosingTypeName, irreducibleName);
    }
}
