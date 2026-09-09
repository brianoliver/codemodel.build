package build.codemodel.dependency.injection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that {@link FieldInjectionPoint}, {@link MethodInjectionPoint} and {@link ConstructorInjectionPoint}
 * fail with a descriptive {@link InjectionFailedException} - one that names the member and its
 * inaccessibility - rather than letting the JDK surface a bare {@link IllegalAccessException} /
 * {@link java.lang.reflect.InaccessibleObjectException} when the module system denies deep reflection on
 * the target member.
 *
 * <p>Each case targets a {@code public} member of a {@code public} class in a non-exported, non-open
 * package of a synthesized named module (see {@link ForeignModule}); {@code trySetAccessible()} returns
 * {@code false} for all of them.
 *
 * <p>These exercise the same {@code trySetAccessible()}-and-throw contract as
 * {@link ProvidesResolverTests#shouldFailDescriptivelyWhenProvidesMethodIsInaccessible}.
 */
class InaccessibleMemberInjectionTests
    implements ContextualTesting {

    @Test
    void fieldInjectionFailsDescriptivelyWhenTargetIsInaccessible(@TempDir final Path moduleDir) throws Exception {
        final var injectable = ForeignModule.define(moduleDir).newFieldInjectable();

        // sanity: the @Inject field really is unreachable via deep reflection
        final var field = injectable.getClass().getDeclaredField("dependency");
        assertThat(field.trySetAccessible()).isFalse();

        final var context = createInjectionFramework().newContext();
        context.bind(String.class).to("value");

        assertThatThrownBy(() -> context.inject(injectable))
            .isInstanceOf(InjectionFailedException.class)
            .hasMessageContaining("dependency")
            .hasMessageContaining("inaccessible");
    }

    @Test
    void methodInjectionFailsDescriptivelyWhenTargetIsInaccessible(@TempDir final Path moduleDir) throws Exception {
        final var injectable = ForeignModule.define(moduleDir).newMethodInjectable();

        // sanity: the @Inject setter really is unreachable via deep reflection
        final var method = injectable.getClass().getDeclaredMethod("setDependency", String.class);
        assertThat(method.trySetAccessible()).isFalse();

        final var context = createInjectionFramework().newContext();
        context.bind(String.class).to("value");

        assertThatThrownBy(() -> context.inject(injectable))
            .isInstanceOf(InjectionFailedException.class)
            .hasMessageContaining("setDependency")
            .hasMessageContaining("inaccessible");
    }

    @Test
    void constructorInjectionFailsDescriptivelyWhenTargetIsInaccessible(@TempDir final Path moduleDir) throws Exception {
        final var injectableClass = ForeignModule.define(moduleDir).constructorInjectableClass();

        // sanity: the @Inject constructor really is unreachable via deep reflection
        final var constructor = injectableClass.getDeclaredConstructor(String.class);
        assertThat(constructor.trySetAccessible()).isFalse();

        final var context = createInjectionFramework().newContext();
        context.bind(String.class).to("value");

        assertThatThrownBy(() -> context.create(injectableClass))
            .isInstanceOf(InjectionFailedException.class)
            .hasMessageContaining(injectableClass.getSimpleName())
            .hasMessageContaining("inaccessible");
    }
}
