package build.codemodel.dependency.injection;

import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.objectoriented.descriptor.Classification;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProvidesResolver}.
 *
 * @author reed.vonredwitz
 */
class ProvidesResolverTests
    implements ContextualTesting {

    /**
     * Verifies that a value produced by a {@link Provides}-annotated method is resolvable and injected.
     */
    @Test
    void shouldResolveValueFromProvidesMethod() {
        final var framework = createInjectionFramework();

        final var context = framework.newContext(ProvidesResolver.of(new GreetingProvider(), framework));
        context.bind(GreetingService.class).to(GreetingService.class);

        final var service = context.create(GreetingService.class);

        assertThat(service.greeting).isEqualTo("Hello from @Provides");
    }

    /**
     * Verifies that a superclass {@link Provides} method is discovered when scanning the hierarchy.
     */
    @Test
    void shouldResolveValueFromSuperclassProvidesMethod() {
        final var framework = createInjectionFramework();

        final var context = framework.newContext(ProvidesResolver.of(new ExtendedProvider(), framework));
        context.bind(GreetingService.class).to(GreetingService.class);

        final var service = context.create(GreetingService.class);

        assertThat(service.greeting).isEqualTo("Hello from @Provides");
    }

    /**
     * Verifies that a {@link ProvidesResolver} with no matching method returns empty.
     */
    @Test
    void shouldReturnEmptyWhenNoProvidesMethodMatchesDependency() {
        final var framework = createInjectionFramework();

        final var resolver = ProvidesResolver.of(new EmptyProvider(), framework);

        // EmptyProvider has no @Provides for String
        final var dependency = IndependentDependency.of(
            framework.codeModel().getTypeUsage(String.class),
            _ -> Stream.empty());

        final Optional<?> result = resolver.resolve(dependency);
        assertThat(result).isEmpty();
    }

    /**
     * Verifies that a {@link Provides}-annotated method with a {@code void} return type is silently
     * ignored during construction and does not cause an error or a spurious registration.
     */
    @Test
    void shouldIgnoreVoidProvidesMethod() {
        final var framework = createInjectionFramework();

        // construction must not throw even though @Provides is on a void method
        final var resolver = ProvidesResolver.of(new VoidProvider(), framework);

        // nothing should be registered, so any dependency comes back empty
        final var dependency = IndependentDependency.of(
            framework.codeModel().getTypeUsage(String.class),
            _ -> Stream.empty());

        assertThat(resolver.resolve(dependency)).isEmpty();
    }

    /**
     * Verifies that two {@link Provides} methods returning the same type but distinguished by different
     * {@code @Named} qualifiers are resolved independently, rather than the second registration being
     * dropped or the first method's value being returned for both qualifiers.
     */
    @Test
    void shouldResolveDistinctValuesForDifferentlyQualifiedProvidesMethods() {
        final var framework = createInjectionFramework();

        final var context = framework.newContext(ProvidesResolver.of(new QualifiedGreetingProvider(), framework));
        context.bind(QualifiedGreetingService.class).to(QualifiedGreetingService.class);

        final var service = context.create(QualifiedGreetingService.class);

        assertThat(service.englishGreeting).isEqualTo("Hello");
        assertThat(service.frenchGreeting).isEqualTo("Bonjour");
    }

    /**
     * A qualified request must never be satisfied by a {@link Provides} method whose qualifiers don't match,
     * even though the two {@link build.codemodel.foundation.usage.TypeUsage}s are themselves compatible (here,
     * identical). The wildcard-compatibility fallback in {@link Dependency#resolve} exists to let a
     * wildcard-bearing request match a structurally different candidate - it must not let an unqualified
     * {@code @Provides} method silently stand in for a differently-qualified request (e.g. an injection point
     * requiring {@code @Named("French") String} must never resolve to a {@code @Provides} method returning a
     * bare, unqualified {@code String}).
     */
    @Test
    void shouldNotResolveQualifiedRequestFromUnqualifiedProvidesMethod() {
        final var framework = createInjectionFramework();

        final var context = framework.newContext(ProvidesResolver.of(new GreetingProvider(), framework));
        context.bind(NamedGreetingService.class).to(NamedGreetingService.class);

        assertThatThrownBy(() -> context.create(NamedGreetingService.class))
            .isInstanceOf(UnsatisfiedDependencyException.class);
    }

    /**
     * Verifies that a concrete method overriding an {@code abstract} {@link Provides}-annotated method is
     * itself treated as {@link Provides}, even though it does not repeat the annotation. An {@code abstract}
     * method can't be "opted out of" the way an {@code @Inject} method can - every concrete subclass must
     * supply an override - so the {@link Provides} contract carries through to whichever override is
     * actually invoked.
     */
    @Test
    void shouldResolveValueFromConcreteOverrideOfAbstractProvidesMethod() {
        final var framework = createInjectionFramework();

        final var context = framework.newContext(ProvidesResolver.of(new ConcreteGreetingProvider(), framework));
        context.bind(GreetingService.class).to(GreetingService.class);

        final var service = context.create(GreetingService.class);

        assertThat(service.greeting).isEqualTo("Hello from concrete override");
    }

    /**
     * {@link InjectionFramework#isProvides} and {@link InjectionFramework#resolveEffectivelyProvides} must
     * agree, method for method, on what counts as {@link Provides}. A {@code void}-returning
     * {@code @Provides} method is a provider by neither, even though it carries the annotation.
     */
    @Test
    void shouldExcludeVoidProvidesMethodFromBothProvidesPredicates() {
        final var framework = createInjectionFramework();
        final var methods = methodsInHierarchy(framework, VoidProvider.class);

        // the annotated void method is present, so the assertions below are not vacuous
        assertThat(methods).anyMatch(ProvidesResolverTests::carriesProvidesAnnotation);

        assertProvidesPredicatesAgree(framework, methods);
        assertThat(methods).noneMatch(md -> framework.isProvides(md, methods));
        assertThat(framework.resolveEffectivelyProvides(methods)).isEmpty();
    }

    /**
     * For an ordinary {@link Provides} method the two predicates agree: {@link InjectionFramework#isProvides}
     * is {@code true} and the method appears in {@link InjectionFramework#resolveEffectivelyProvides}, while
     * a plain non-annotated method satisfies neither.
     */
    @Test
    void shouldAgreeOnAnnotatedAndUnannotatedProvidesMethods() {
        final var framework = createInjectionFramework();
        final var methods = methodsInHierarchy(framework, GreetingProvider.class);

        final var effectivelyProvides = framework.resolveEffectivelyProvides(methods).toList();

        final var greeting = namedMethod(methods, "greeting");
        final var notProvides = namedMethod(methods, "notProvides");

        assertThat(framework.isProvides(greeting, methods)).isTrue();
        assertThat(effectivelyProvides).contains(greeting);

        assertThat(framework.isProvides(notProvides, methods)).isFalse();
        assertThat(effectivelyProvides).doesNotContain(notProvides);

        assertProvidesPredicatesAgree(framework, methods);
    }

    /**
     * A concrete override of an {@code abstract} {@link Provides} method carries no annotation of its own,
     * yet both {@link InjectionFramework#isProvides} and {@link InjectionFramework#resolveEffectivelyProvides}
     * must recognize it - consistently.
     */
    @Test
    void shouldRecognizeConcreteOverrideOfAbstractProvidesConsistently() {
        final var framework = createInjectionFramework();
        final var methods = methodsInHierarchy(framework, ConcreteGreetingProvider.class);

        final var concreteOverride = methods.stream()
            .filter(md -> md.methodName().name().toString().equals("greeting"))
            .filter(md -> md.getTrait(Classification.class)
                .map(classification -> classification != Classification.ABSTRACT)
                .orElse(true))
            .findFirst()
            .orElseThrow();

        assertThat(carriesProvidesAnnotation(concreteOverride)).isFalse();
        assertThat(framework.isProvides(concreteOverride, methods)).isTrue();
        assertThat(framework.resolveEffectivelyProvides(methods)).contains(concreteOverride);

        assertProvidesPredicatesAgree(framework, methods);
    }

    /**
     * Asserts that {@link InjectionFramework#isProvides} classifies each method exactly as {@link
     * InjectionFramework#resolveEffectivelyProvides} does.
     */
    private static void assertProvidesPredicatesAgree(
        final InjectionFramework framework, final List<MethodDescriptor> methods) {

        final var effectivelyProvides = framework.resolveEffectivelyProvides(methods).toList();

        assertThat(methods.stream().filter(md -> framework.isProvides(md, methods)).toList())
            .containsExactlyInAnyOrderElementsOf(effectivelyProvides);
    }

    private static List<MethodDescriptor> methodsInHierarchy(final InjectionFramework framework, final Class<?> type) {
        final var codeModel = framework.codeModel();
        return codeModel.getJDKTypeDescriptor(type)
            .map(typeDescriptor -> codeModel.getTraitsInHierarchy(typeDescriptor, MethodDescriptor.class).toList())
            .orElseThrow();
    }

    private static MethodDescriptor namedMethod(final List<MethodDescriptor> methods, final String name) {
        return methods.stream()
            .filter(md -> md.methodName().name().toString().equals(name))
            .findFirst()
            .orElseThrow();
    }

    private static boolean carriesProvidesAnnotation(final MethodDescriptor descriptor) {
        return descriptor.traits(AnnotationTypeUsage.class)
            .anyMatch(annotation -> annotation.typeName().canonicalName().equals(Provides.class.getCanonicalName()));
    }

    // --- fixtures ---

    static class GreetingService {
        @Inject
        String greeting;
    }

    static class GreetingProvider {
        @Provides
        public String greeting() {
            return "Hello from @Provides";
        }

        public String notProvides() {
            return "not annotated";
        }
    }

    static class BaseProvider {
        @Provides
        public String greeting() {
            return "Hello from @Provides";
        }
    }

    static class ExtendedProvider extends BaseProvider {
        // inherits the @Provides method
    }

    static class EmptyProvider {
        public String notAnnotated() {
            return "no @Provides here";
        }
    }

    static class VoidProvider {
        @Provides
        public void doNothing() {
            // void return type — must be silently ignored by ProvidesResolver
        }
    }

    abstract static class AbstractGreetingProvider {
        @Provides
        public abstract String greeting();
    }

    static class ConcreteGreetingProvider extends AbstractGreetingProvider {
        @Override
        public String greeting() {
            return "Hello from concrete override";
        }
    }

    static class NamedGreetingService {
        @Inject
        @Named("French")
        String greeting;
    }

    static class QualifiedGreetingService {
        @Inject
        @Named("English")
        String englishGreeting;

        @Inject
        @Named("French")
        String frenchGreeting;
    }

    static class QualifiedGreetingProvider {
        @Provides
        @Named("English")
        public String english() {
            return "Hello";
        }

        @Provides
        @Named("French")
        public String french() {
            return "Bonjour";
        }
    }
}
