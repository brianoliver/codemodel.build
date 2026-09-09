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

import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.descriptor.MethodType;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link Resolver} of values produced by {@link Provides}-annotated methods on a provider object.
 *
 * <p>At construction, the provider object's type descriptor hierarchy is scanned via the
 * {@link InjectionFramework} for non-static, non-abstract, non-void, no-arg methods annotated with
 * {@link Provides} - including a concrete method that overrides an {@code abstract} {@link Provides}
 * declaration without repeating the annotation itself (see
 * {@link InjectionFramework#resolveEffectivelyProvides}).  Each such method is registered as resolvable by
 * its return type together with any qualifier annotations (e.g. {@code @Named}) declared on the method
 * itself, so differently-qualified {@link Provides} methods returning the same type do not collide.
 * At resolve time, the matching method is invoked and its return value is wrapped in a {@link ValueBinding}.
 *
 * <p>Each call to {@link #resolve} invokes the {@link Provides} method afresh, giving factory-per-resolution
 * semantics (analogous to an unscoped binding in Dagger/Guice).  If singleton semantics are required, the
 * provider object should memoize the value itself.
 *
 * @author reed.vonredwitz
 * @see Provides
 */
public class ProvidesResolver
    implements Resolver<Object> {

    /**
     * The provider object whose {@link Provides} methods are invoked on resolution.
     */
    private final Object providerObject;

    /**
     * Map from a {@link Dependency} representing a {@link Provides} method's return type and qualifier
     * annotations to the corresponding {@link Provides} {@link MethodDescriptor}.
     */
    private final Map<Dependency, MethodDescriptor> methodsByDependency;

    /**
     * The {@link JDKCodeModel} used to check wildcard bound assignability on resolution.
     */
    private final JDKCodeModel codeModel;

    /**
     * Constructs a {@link ProvidesResolver}.
     *
     * @param providerObject the provider object
     * @param framework      the {@link InjectionFramework} used to scan for {@link Provides} methods
     */
    private ProvidesResolver(final Object providerObject, final InjectionFramework framework) {
        this.providerObject = Objects.requireNonNull(providerObject, "The provider object must not be null");
        Objects.requireNonNull(framework, "The InjectionFramework must not be null");

        this.methodsByDependency = new LinkedHashMap<>();
        this.codeModel = framework.codeModel();

        this.codeModel.getJDKTypeDescriptor(providerObject.getClass())
            .ifPresent(typeDescriptor -> {
                final var allMethods = codeModel.getTraitsInHierarchy(typeDescriptor, MethodDescriptor.class)
                    .toList();

                framework.resolveEffectivelyProvides(allMethods)
                    .filter(md -> md.formalParameters().findAny().isEmpty())
                    .filter(md -> md.returnType() instanceof NamedTypeUsage)
                    .forEach(md -> {
                        final var dependency = IndependentDependency.of(md.returnType(), _ -> framework.getQualifierAnnotationTypes(md));
                        this.methodsByDependency.putIfAbsent(dependency, md);
                    });
            });
    }

    @Override
    public Optional<? extends Binding<Object>> resolve(final Dependency dependency) {

        if (!(dependency.typeUsage() instanceof NamedTypeUsage)) {
            return Optional.empty();
        }

        final var methodDescriptor = Dependency.resolve(dependency, this.methodsByDependency, this.codeModel)
            .orElse(null);

        if (methodDescriptor == null) {
            return Optional.empty();
        }

        final var method = methodDescriptor.getTrait(MethodType.class)
            .map(MethodType::method)
            .orElseThrow(() -> new InjectionException(
                "No MethodType trait for @Provides method " + methodDescriptor));

        if (!method.trySetAccessible()) {
            throw new InjectionException(
                "Can't invoke @Provides method " + method + " as it's inaccessible");
        }

        try {
            final var value = method.invoke(this.providerObject);
            return Optional.of(new ValueBinding<Object>() {
                @Override
                public Object value() {
                    return value;
                }

                @Override
                public Dependency dependency() {
                    return dependency;
                }
            });
        } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new InjectionException("@Provides method " + method + " failed", e);
        }
    }

    /**
     * Creates a {@link ProvidesResolver} for the specified provider object.
     *
     * @param providerObject the provider object
     * @param framework      the {@link InjectionFramework}
     * @return a new {@link ProvidesResolver}
     */
    public static ProvidesResolver of(final Object providerObject, final InjectionFramework framework) {
        return new ProvidesResolver(providerObject, framework);
    }
}
