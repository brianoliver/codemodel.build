package build.codemodel.dependency.injection;

/*-
 * #%L
 * Dependency Injection
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

import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.TypeUsages;
import jakarta.inject.Qualifier;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Defines a {@link TypeUsage} for <i>Dependency Injection</i> with an {@link Injector}.
 *
 * @author brian.oliver
 * @since Jan-2025
 */
public interface Dependency {

    /**
     * Obtains the {@link TypeUsage} for the {@link Dependency}.
     *
     * @return the {@link TypeUsage}
     */
    TypeUsage typeUsage();

    /**
     * Obtains the unique signature for the {@link Dependency}, consisting of the {@link TypeName} and any
     * {@link AnnotationTypeUsage}s that have been annotated with the {@link Qualifier} meta-annotation.
     *
     * @return the <i>Signature</i> for the {@link Dependency}
     */
    String signature();

    /**
     * Computes the canonical signature for a {@link TypeUsage} and a set of qualifier {@link AnnotationTypeUsage}s,
     * consisting of the {@link TypeUsage}'s {@link TypeUsage#canonicalName()} (recursively including any generic
     * parameters) followed by the qualifier annotations, sorted and rendered so that the signature is stable
     * regardless of the order in which the qualifiers were declared.
     *
     * <p>Anywhere two things must be recognized as the "same qualified type" (an {@link IndependentDependency}
     * and a {@link Provides} method it may resolve to, for example) must derive their signature from this method,
     * so the two stay comparable.
     *
     * @param typeUsage            the {@link TypeUsage}
     * @param qualifierAnnotations the {@link Qualifier} {@link AnnotationTypeUsage}s
     * @return the canonical signature
     * @throws DuplicateQualifierException if more than one {@link AnnotationTypeUsage} of the same qualifier
     *                                     annotation type is present, making the signature ambiguous
     */
    static String signatureOf(final TypeUsage typeUsage,
                              final Stream<? extends AnnotationTypeUsage> qualifierAnnotations) {

        final var sortedQualifiers = qualifierAnnotations.sorted().toList();

        // reject more than one qualifier annotation of the same type (e.g. two differently-valued @Named
        // annotations), as that would make the qualifier ambiguous
        sortedQualifiers.stream()
            .collect(Collectors.groupingBy(AnnotationTypeUsage::typeName))
            .values()
            .stream()
            .filter(duplicates -> duplicates.size() > 1)
            .findFirst()
            .ifPresent(duplicates -> {
                throw new DuplicateQualifierException(
                    "The TypeUsage [" + typeUsage.canonicalName() + "] defines more than one ["
                        + duplicates.getFirst().typeName() + "] qualifier: " + duplicates);
            });

        return sortedQualifiers.isEmpty()
            ? typeUsage.canonicalName()
            : typeUsage.canonicalName()
              + sortedQualifiers.stream()
            .map(Object::toString)
            .collect(Collectors.joining(" ", " ", ""));
    }

    /**
     * Looks up the value registered for {@code requested} in {@code candidates}, keyed by {@link Dependency}.
     * An exact key match (same {@link #signature()}) always wins outright. On a miss, {@code candidates} is
     * searched for the {@link Dependency} whose qualifiers match {@code requested}'s exactly and whose
     * {@link #typeUsage()} is a
     * {@link TypeUsages#isAssignable(TypeUsage, TypeUsage, JDKCodeModel) subtype} of {@code requested}'s -
     * this is what lets a wildcard-bearing request (e.g. {@code Class<? extends Base>} or
     * {@code Set<? extends Base>}) be satisfied by a registered candidate that doesn't match it exactly (e.g.
     * {@code Class<Impl>}), without ever letting a wildcard-fallback candidate override an exact match, and
     * without letting a differently-qualified (or unqualified) candidate silently satisfy a qualified request
     * (e.g. an unqualified {@code JDKVersion} must never satisfy a request for {@code @System JDKVersion}).
     *
     * @param requested  the requested {@link Dependency}
     * @param candidates the candidates, keyed by {@link Dependency}
     * @param codeModel  the {@link JDKCodeModel} used to check bound assignability
     * @param <V>        the type of value
     * @return the resolved value, or {@link Optional#empty()} if no candidate is compatible
     * @throws InjectionException if more than one candidate is wildcard-compatible with {@code requested}
     */
    static <V> Optional<V> resolve(final Dependency requested,
                                   final Map<Dependency, V> candidates,
                                   final JDKCodeModel codeModel) {

        final var exact = candidates.get(requested);
        if (exact != null) {
            return Optional.of(exact);
        }

        final var requestedQualifierSignature = qualifierSignature(requested);

        final var compatible = candidates.entrySet().stream()
            .filter(entry -> qualifierSignature(entry.getKey()).equals(requestedQualifierSignature))
            .filter(entry -> TypeUsages.isAssignable(entry.getKey().typeUsage(), requested.typeUsage(), codeModel))
            .toList();

        if (compatible.size() > 1) {
            throw new InjectionException("Ambiguous wildcard-compatible candidates for [" + requested + "]: "
                + compatible.stream().map(entry -> entry.getKey().toString()).toList());
        }

        return compatible.stream().findFirst().map(Map.Entry::getValue);
    }

    /**
     * Extracts the qualifier portion of {@code dependency}'s {@link #signature()} - everything after the
     * {@link #typeUsage()}'s {@link TypeUsage#canonicalName()} prefix that {@link #signatureOf} always builds
     * the signature from - so two {@link Dependency}s can be compared for "same qualifiers" independently of
     * whether their {@link TypeUsage}s are themselves an exact match (e.g. a wildcard-bearing request versus a
     * concrete candidate).
     *
     * @param dependency the {@link Dependency}
     * @return the qualifier suffix of the {@link Dependency}'s signature
     * @throws IllegalStateException if {@code dependency}'s {@link #signature()} does not start with its own
     *                               {@link #typeUsage()}'s {@link TypeUsage#canonicalName()}, meaning it was not
     *                               built via {@link #signatureOf} and the qualifier suffix can't be trusted
     */
    private static String qualifierSignature(final Dependency dependency) {
        final var typePrefix = dependency.typeUsage().canonicalName();
        final var signature = dependency.signature();
        if (!signature.startsWith(typePrefix)) {
            throw new IllegalStateException("Dependency [" + dependency + "]'s signature [" + signature
                + "] does not start with its own TypeUsage's canonicalName [" + typePrefix + "]");
        }
        return signature.substring(typePrefix.length());
    }
}
