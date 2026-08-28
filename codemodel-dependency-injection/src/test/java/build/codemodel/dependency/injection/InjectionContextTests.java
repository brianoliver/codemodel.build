package build.codemodel.dependency.injection;

import build.codemodel.dependency.injection.example.ConstructorInjectablePerson;
import build.codemodel.dependency.injection.example.FieldInjectablePerson;
import build.codemodel.dependency.injection.example.FieldInjectablePersonWithDefaultConstructor;
import build.codemodel.dependency.injection.example.FieldInjectablePersonWithInjectAnnotatedDefaultConstructor;
import build.codemodel.dependency.injection.example.MultipleConstructorPerson;
import build.codemodel.dependency.injection.example.NonAbstractPerson;
import build.codemodel.dependency.injection.example.SetterInjectablePerson;
import build.codemodel.foundation.usage.NamedTypeUsage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link InjectionContext}s.
 *
 * @author brian.oliver
 * @since Mar-2018
 */
class InjectionContextTests
    implements ContextualTesting {

    /**
     * Ensure a {@link Context} can inject values into a class using public, private and package-protected
     * {@link Inject} annotated fields.
     */
    @Test
    void shouldInjectIntoPublicPrivateAndPackageProtectedFields() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);
        context.bind(boolean.class).to(true);

        final var person = context.inject(new FieldInjectablePerson());

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);

        assertThat(person.isGraduated())
            .isTrue();
    }

    /**
     * Ensure a {@link Context} can inject values into a class using public, private and package-protected
     * {@link Inject} annotated setters.
     */
    @Test
    void shouldInjectIntoPublicPrivateAndPackageProtectedSetterMethods() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);

        final var person = context.inject(new SetterInjectablePerson());

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure repeated {@link BindingBuilder#as(String)} calls on the same binding are rejected with a
     * {@link DuplicateQualifierException} when the {@link Binding} is created, since the resulting
     * {@code @Named} qualifier would otherwise be ambiguous.
     */
    @Test
    void shouldRejectRepeatedAsCallsAsDuplicateQualifier() {
        final var context = createInjectionFramework().newContext();

        assertThrows(DuplicateQualifierException.class, () ->
            context.bind(String.class).as("first").as("second").to("value"));
    }

    /**
     * Ensure a {@link Context} can inject values from a custom {@link Resolver}.
     */
    @Test
    void shouldInjectUsingCustomResolver() {
        final var resolver = Resolver.of(
            dependency -> dependency.typeUsage() instanceof NamedTypeUsage namedTypeUsage
                && namedTypeUsage.typeName().canonicalName().equals(String.class.getCanonicalName())
                ? Optional.of(SingletonValueBinding.of(dependency, "Brian"))
                : Optional.empty());

        final var context = createInjectionFramework()
            .newContext()
            .addResolver(resolver);

        context.bind(int.class).as("Age").to(42);
        context.bind(boolean.class).to(true);

        final var person = context.inject(new FieldInjectablePerson());

        assertThat(person.getFirstName())
            .isEqualTo("Brian");

        assertThat(person.getLastName())
            .isEqualTo("Brian");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure a {@link Context} fails to inject with an {@link InjectionException}.
     */
    @Test
    void shouldFailToInjectIntoPrivateFields() {
        final var context = createInjectionFramework().newContext();

        assertThrows(UnsatisfiedDependencyException.class, () -> {
            context.bind(String.class).as("FirstName").to("Harry");
            context.bind(String.class).as("LastName").to("Potter");

            context.inject(new FieldInjectablePerson());
        });
    }

    /**
     * Ensure a {@link Context} can create instances of a {@link String}.
     */
    @Test
    void shouldCreateAString() {
        final var context = createInjectionFramework().newContext();

        final var string = context.create(String.class);

        assertThat(string)
            .isNotNull();

        assertThat(string)
            .isEmpty();
    }

    /**
     * Ensure a {@link Context} can create an inject values into a class that has a default constructor
     * (not annotated with {@link Inject}), but with {@link Inject} annotated fields.
     */
    @Test
    void shouldCreateAndInjectIntoFieldsWithDefaultConstructor() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);
        context.bind(boolean.class).to(true);

        final var person = context.create(FieldInjectablePersonWithDefaultConstructor.class);

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure a {@link Context} can create an inject values into a class that has a default constructor
     * (not annotated with {@link Inject}), but with {@link Inject} annotated fields.
     */
    @Test
    void shouldCreateAndInjectIntoFieldsWithInjectAnnotatedDefaultConstructor() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);
        context.bind(boolean.class).to(true);

        final var person = context.create(FieldInjectablePersonWithInjectAnnotatedDefaultConstructor.class);

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure a {@link Context} can create an inject values into a class that has no default constructor and only
     * private {@link Inject} annotated fields.
     */
    @Test
    void shouldCreateAndInjectIntoFieldsWithoutDefaultConstructor() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);
        context.bind(boolean.class).to(true);

        final var person = context.create(FieldInjectablePerson.class);

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure a {@link Context} can create an inject values into a class that has multiple constructors
     * (not annotated with {@link Inject}), but with {@link Inject} annotated fields.
     */
    @Test
    void shouldCreateAndInjectIntoFieldsWithMultipleConstructors() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);
        context.bind(boolean.class).to(true);

        final var person = context.create(MultipleConstructorPerson.class);

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure a {@link Context} can create and inject values into a class that has no default constructor and only
     * {@link Inject} annotated setter methods.
     */
    @Test
    void shouldCreateAndInjectIntoSetterMethodsWithoutDefaultConstructor() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);

        final var person = context.create(SetterInjectablePerson.class);

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure a {@link Context} can create and inject values into a class using an {@link Inject} annotated constructor.
     */
    @Test
    void shouldCreateAndInjectIntoConstructor() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).to("Cool");
        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);

        final var person = context.create(ConstructorInjectablePerson.class);

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);

        assertThat(person.isPostInjectInvoked())
            .isTrue();
    }

    /**
     * Ensure a {@link Context} fails to create and inject with an {@link InjectionException}.
     */
    @Test
    void shouldFailToCreateAndInjectIntoConstructor() {
        assertThrows(UnsatisfiedDependencyException.class, () -> {
            final var context = createInjectionFramework().newContext();

            context.bind(String.class).as("FirstName").to("Harry");
            context.bind(String.class).as("LastName").to("Potter");

            // attempt to inject (this should fail)
            context.create(ConstructorInjectablePerson.class);
        });
    }

    /**
     * Ensure a {@link Context} can create and inject values into an abstract / inherited class using an {@link Inject}
     * annotated constructor.
     */
    @Test
    void shouldCreateAndInjectIntoAnAbstractClass() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to("Harry");
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);
        context.bind(boolean.class).to(true);

        final var person = context.create(NonAbstractPerson.class);

        assertThat(person.getFirstName())
            .isEqualTo("Harry");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);

        assertThat(person.isTall())
            .isTrue();

        assertThat(person.isAbstractPostInjectInvoked())
            .isTrue();

        assertThat(person.isNonAbstractPostInjectInvoked())
            .isTrue();

    }

    /**
     * Ensure a {@link Context} can create and inject values into a class using an {@link Inject} annotated constructor
     * non-value ({@link Class} and {@link Supplier}) based bindings.
     */
    @Test
    void shouldCreateAndInjectIntoConstructorUsingNonValueBasedBinding() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("FirstName").to(String.class);
        context.bind(String.class).as("LastName").to("Potter");
        context.bind(int.class).as("Age").to(42);

        final var person = context.create(ConstructorInjectablePerson.class);

        assertThat(person.getFirstName())
            .isEqualTo("");

        assertThat(person.getLastName())
            .isEqualTo("Potter");

        assertThat(person.getAge())
            .isEqualTo(42);
    }

    /**
     * Ensure a {@link Context} fails to inject into a constructor that has no bindings for {@link String}s
     * (ie: no default constructor classes)
     */
    @Test
    void shouldFailToInjectIntoConstructorWithNoStringBindings() {
        final var context = createInjectionFramework().newContext();

        // we supply the bindings for the int, but not the Strings
        context.bind(int.class).as("Age").to(42);

        assertThrows(UnsatisfiedDependencyException.class, () -> {
            context.create(ConstructorInjectablePerson.class);
        });
    }

    /**
     * Ensure the {@link UnsatisfiedDependencyException} message includes the full "required by" chain so that
     * developers can trace which top-level component pulled in a missing dependency.
     */
    @Test
    void shouldIncludeRequiredByChainInUnsatisfiedDependencyMessage() {
        final var context = createInjectionFramework().newContext();

        final var exception = assertThrows(UnsatisfiedDependencyException.class,
            () -> context.create(ChainRootService.class));

        assertThat(exception.getMessage())
            .contains("MissingLeafService")
            .contains("required by")
            .contains("ChainMiddleService")
            .contains("ChainRootService");
    }

    /**
     * A leaf dependency that is never bound, causing a resolution failure when a service chain requests it.
     */
    interface MissingLeafService {
    }

    /**
     * A singleton mid-chain service that requires the unbound {@link MissingLeafService}.
     */
    @Singleton
    static class ChainMiddleService {
        @Inject
        ChainMiddleService(final MissingLeafService leaf) {
        }
    }

    /**
     * The root service that pulls in {@link ChainMiddleService}, triggering the full chain failure.
     */
    static class ChainRootService {
        @Inject
        ChainRootService(final ChainMiddleService middle) {
        }
    }

    /**
     * Ensure {@code create(Class)} can instantiate a class that is <em>not</em> resolvable by name on the
     * dependency-injection module's own classloader (e.g. a build-script or plugin class loaded by a
     * foreign loader). {@code getValue} must not abort resolution when {@code Class.forName} misses — the
     * top-level {@code create(Class)} still holds the caller's real {@link Class} reference and its
     * fallback should take over.
     */
    @Test
    void shouldCreateClassNotResolvableByNameOnTheModuleLoader() {
        final var foreignClass = loadForeignInjectableClass();

        final var context = createInjectionFramework().newContext();

        final var instance = context.create(foreignClass);

        assertThat(instance)
            .isNotNull();
        assertThat(instance.getClass())
            .isSameAs(foreignClass);
    }

    /**
     * Ensure the non-{@code Class} entry points still fail loudly when a top-level {@link Dependency}
     * names a type that {@code Class.forName} cannot resolve on this module's loader. Unlike
     * {@code create(Class)}, {@code create(TypeUsage)} / {@code create(Dependency)} hold no real
     * {@link Class} reference to fall back on, so {@code getValue} returning empty must surface as an
     * {@link UnsatisfiedDependencyException} rather than a {@code null} result.
     */
    @Test
    void shouldThrowWhenTopLevelTypeUsageNamesAnUnresolvableClass() {
        final var framework = createInjectionFramework();
        final var foreignClass = loadForeignInjectableClass();

        final var typeUsage = framework.codeModel().getTypeUsage(foreignClass);
        final var context = framework.newContext();

        assertThrows(UnsatisfiedDependencyException.class, () -> context.create(typeUsage));
    }

    /**
     * Synthesizes a class with a single public no-arg constructor into a throwaway {@link ClassLoader},
     * so that it exists only in that loader and is invisible to {@link Class#forName} on the
     * dependency-injection module's own loader (e.g. a build-script or plugin class loaded by a foreign
     * loader).
     *
     * @return the synthesized {@link Class}, loaded by a foreign loader
     */
    private Class<?> loadForeignInjectableClass() {
        final var className = "ForeignInjectable";
        final var bytecode = ClassFile.of().build(ClassDesc.of(className), classBuilder ->
            classBuilder.withMethodBody(
                ConstantDescs.INIT_NAME,
                ConstantDescs.MTD_void,
                ClassFile.ACC_PUBLIC,
                codeBuilder -> codeBuilder
                    .aload(0)
                    .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                    .return_()));

        final var foreignLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(name, bytecode, 0, bytecode.length);
                }
                throw new ClassNotFoundException(name);
            }
        };

        final var foreignClass = assertDoesNotThrow(() -> Class.forName(className, true, foreignLoader));

        // sanity: the DI module's loader genuinely cannot see it by name
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));

        return foreignClass;
    }

    /**
     * Ensure a {@link Context} will use a {@link Supplier} to resolve values for injection.
     */
    @Test
    void shouldResolveValuesWithSuppliers() {
        final var injection = createInjectionFramework();
        final var context = injection.newContext();

        final var counter = new AtomicInteger(0);

        final Supplier<Integer> supplier = counter::incrementAndGet;

        context.bind(Integer.class).to(supplier);

        final var resolver = context.resolver();

        final var typeUsage = injection.codeModel().getTypeUsage(Integer.class);
        final var dependency = IndependentDependency.of(typeUsage, _ -> Stream.empty());

        final var binding1 = resolver.resolve(dependency)
            .map(ValueBinding.class::cast)
            .orElseThrow();

        assertThat(binding1.value())
            .isEqualTo(1);

        assertThat(counter.get())
            .isEqualTo(1);

        final var binding2 = resolver.resolve(dependency)
            .map(ValueBinding.class::cast)
            .orElseThrow();

        assertThat(binding2.value())
            .isEqualTo(2);

        assertThat(counter.get())
            .isEqualTo(2);
    }

    /**
     * Ensure a {@link Context} can use public static constructor-based injection when creating a binding.
     */
    @Test
    void shouldCreateBindingUsingPublicStaticConstructorInjection() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("name").to("Barney");

        final var person = context.create(PublicStaticConstructorInjectablePerson.class);

        assertThat(person.getName())
            .isEqualTo("Barney");
    }

    /**
     * Ensure a {@link Context} can use non-public static constructor-based injection when creating a binding.
     */
    @Test
    void shouldCreateBindingUsingStaticConstructorInjection() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("name").to("Barney");

        final var person = context.create(StaticConstructorInjectablePerson.class);

        assertThat(person.getName())
            .isEqualTo("Barney");
    }

    /**
     * Ensure a {@link Context} can use private static constructor-based injection when creating a binding.
     */
    @Test
    void shouldCreateBindingUsingPrivateStaticConstructorInjection() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("name").to("Barney");

        final var person = context.create(PrivateStaticConstructorInjectablePerson.class);

        assertThat(person.getName())
            .isEqualTo("Barney");
    }

    /**
     * Ensure a {@link Context} will instantiate singletons as required.
     */
    @Test
    void shouldCreateSingletons() {
        final var context = createInjectionFramework().newContext();

        context.bind(String.class).as("name").to("Barney");
        context.bind(Highlander.class).to(Highlander.class);

        final var highlander1 = context.create(Highlander.class);
        final var highlander2 = context.create(Highlander.class);

        assertThat(highlander1).isSameAs(highlander2);
    }

    /**
     * Ensure a {@link Context} will instantiate non-singletons as required.
     */
    @Test
    void shouldCreateNonSingletons() {
        final var context = createInjectionFramework().newContext();

        final String name = "Barney";

        context.bind(String.class).as("name").to(name);
        context.bind(NonSingleton.class).to(NonSingleton.class);
        context.bind(Highlander.class).to(Highlander.class);

        final InjectableContainer container = context.create(InjectableContainer.class);

        assertThat(container.firstNonSingleton)
            .isNotEqualTo(container.secondNonSingleton);

        assertThat(container.firstNonSingleton.name)
            .isEqualTo(name);

        assertThat(container.secondNonSingleton.name)
            .isEqualTo(name);

        assertThat(container.firstHighlander)
            .isEqualTo(container.secondHighlander);

        assertThat(container.firstHighlander.name)
            .isEqualTo(name);
    }

    /**
     * A simple person class defined as a public static inner class, using constructor-based {@link Inject}ion.
     */
    public static class PublicStaticConstructorInjectablePerson {

        /**
         * The name of the person.
         */
        private final String name;

        /**
         * Constructs a person.
         *
         * @param name the name of the person
         */
        @Inject
        PublicStaticConstructorInjectablePerson(@Named("name") final String name) {
            this.name = name;
        }

        /**
         * Obtains the first name of the person.
         *
         * @return the first name
         */
        public String getName() {
            return this.name;
        }
    }

    /**
     * A simple person class defined as a non-public static inner class, using constructor-based {@link Inject}ion.
     */
    static class StaticConstructorInjectablePerson {

        /**
         * The name of the person.
         */
        private final String name;

        /**
         * Constructs a person.
         *
         * @param name the name of the person
         */
        @Inject
        StaticConstructorInjectablePerson(@Named("name") final String name) {
            this.name = name;
        }

        /**
         * Obtains the first name of the person.
         *
         * @return the first name
         */
        public String getName() {
            return this.name;
        }
    }

    /**
     * A simple person class defined as a private static inner class, using constructor-based {@link Inject}ion.
     */
    private static class PrivateStaticConstructorInjectablePerson {

        /**
         * The name of the person.
         */
        private final String name;

        /**
         * Constructs a person.
         *
         * @param name the name of the person
         */
        @Inject
        PrivateStaticConstructorInjectablePerson(@Named("name") final String name) {
            this.name = name;
        }

        /**
         * Obtains the first name of the person.
         *
         * @return the first name
         */
        public String getName() {
            return this.name;
        }
    }

    /**
     * A simple {@link Class} containing multiple non-singletons.
     */
    private static class InjectableContainer {

        @Inject
        private NonSingleton firstNonSingleton;

        @Inject
        private NonSingleton secondNonSingleton;

        @Inject
        private Highlander firstHighlander;

        @Inject
        private Highlander secondHighlander;
    }

    /**
     * A simple {@link Class} to be used as a non-singleton.
     */
    private static class NonSingleton {

        @Inject
        @Named("name")
        private String name;
    }

    /**
     * A simple {@code Singleton}.
     */
    @Singleton
    private static class Highlander {

        @Inject
        @Named("name")
        private String name;
    }
}
