package build.codemodel.jdk.populator;

import build.base.compile.testing.JavaFileObjects;
import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.IntersectionTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.foundation.usage.TypeVariableUsage;
import build.codemodel.foundation.usage.WildcardTypeUsage;
import build.codemodel.objectoriented.descriptor.FieldDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for generic type discovery via {@link JdkInitializer}.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public class GenericsDiscoveryTests {

    /**
     * Ensure that a generic class has a {@link ParameterizedTypeDescriptor} trait
     * and that fields with generic types are represented as {@link GenericTypeUsage}.
     */
    @Test
    void shouldDiscoverGenericTypeParameter() {
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Container", """
            package build.codemodel.jdk.example;
            import java.util.List;
            public class Container<T> {
                public List<T> elements;
            }
            """);
        final var initializer = new JdkInitializer(List.of(), List.of(), List.of(source));
        final var codeModel = JdkInitializerTests.runInternal(initializer);

        final var naming = codeModel.getNameProvider();
        final var typeName = naming.getEmptyModuleTypeName("build.codemodel.jdk.example.Container");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        // Container<T> has a type parameter, so it must carry a ParameterizedTypeDescriptor trait
        assertThat(descriptor.getTrait(ParameterizedTypeDescriptor.class)).isPresent();

        // One field: elements
        assertThat(descriptor.traits(FieldDescriptor.class)
            .map(f -> f.fieldName().toString())
            .toList())
            .containsExactly("elements");

        // Field type is generic (List<T>)
        final var elementsField = descriptor.traits(FieldDescriptor.class)
            .findFirst()
            .orElseThrow();
        assertThat(elementsField.type()).isInstanceOf(GenericTypeUsage.class);

        // The generic type name includes java.util.List
        final var genericUsage = (GenericTypeUsage) elementsField.type();
        assertThat(genericUsage.typeName().toString()).contains("java.util.List");
    }

    @Test
    void shouldDiscoverWildcardBounds() {
        final var source = JavaFileObjects.forSourceString("Discoverable", """
            import java.util.List;
            public class Discoverable {
                public List<? extends Number> upper;
                public List<? super Integer> lower;
            }
            """);
        final var initializer = new JdkInitializer(List.of(), List.of(), List.of(source));
        final var codeModel = JdkInitializerTests.runInternal(initializer);

        final var naming = codeModel.getNameProvider();
        final var typeName = naming.getEmptyModuleTypeName("Discoverable");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        // upper field: List<? extends Number> → wildcard has upper bound, no lower bound
        final var upperField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("upper"))
            .findFirst()
            .orElseThrow();
        assertThat(upperField.type()).isInstanceOf(GenericTypeUsage.class);
        final var upperParam = ((GenericTypeUsage) upperField.type()).parameters().findFirst().orElseThrow();
        assertThat(upperParam).isInstanceOf(WildcardTypeUsage.class);
        final var upperWildcard = (WildcardTypeUsage) upperParam;
        assertThat(upperWildcard.upperBound()).isPresent();
        assertThat(upperWildcard.lowerBound()).isEmpty();
        assertThat(((NamedTypeUsage) upperWildcard.upperBound().orElseThrow()).typeName().toString())
            .contains("Number");

        // lower field: List<? super Integer> → wildcard has lower bound, no upper bound
        final var lowerField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("lower"))
            .findFirst()
            .orElseThrow();
        assertThat(lowerField.type()).isInstanceOf(GenericTypeUsage.class);
        final var lowerParam = ((GenericTypeUsage) lowerField.type()).parameters().findFirst().orElseThrow();
        assertThat(lowerParam).isInstanceOf(WildcardTypeUsage.class);
        final var lowerWildcard = (WildcardTypeUsage) lowerParam;
        assertThat(lowerWildcard.lowerBound()).isPresent();
        assertThat(lowerWildcard.upperBound()).isEmpty();
        assertThat(((NamedTypeUsage) lowerWildcard.lowerBound().orElseThrow()).typeName().toString())
            .contains("Integer");
    }

    @Test
    void shouldDiscoverBoundedTypeParameter() {
        final var source = JavaFileObjects.forSourceString("Discoverable", """
            public class Discoverable<T extends Number> {
                public <U extends Comparable<U>> U max(U a, U b) { return a; }
            }
            """);
        final var initializer = new JdkInitializer(List.of(), List.of(), List.of(source));
        final var codeModel = JdkInitializerTests.runInternal(initializer);

        final var naming = codeModel.getNameProvider();
        final var typeName = naming.getEmptyModuleTypeName("Discoverable");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        // Class-level: <T extends Number> — upper bound must be present and resolve to Number
        final var classTypeVar = descriptor.getTrait(ParameterizedTypeDescriptor.class).orElseThrow()
            .typeVariables().findFirst().orElseThrow();
        assertThat(classTypeVar).isInstanceOf(TypeVariableUsage.class);
        final var classUpperBound = ((TypeVariableUsage) classTypeVar).upperBound();
        assertThat(classUpperBound).isPresent();
        assertThat(((NamedTypeUsage) classUpperBound.get()).typeName().toString()).contains("Number");

        // Method-level: <U extends Comparable<U>> — upper bound must be present and resolve to Comparable
        final var maxMethod = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("max"))
            .findFirst()
            .orElseThrow();
        final var methodTypeVar = maxMethod.getTrait(ParameterizedTypeDescriptor.class).orElseThrow()
            .typeVariables().findFirst().orElseThrow();
        assertThat(methodTypeVar).isInstanceOf(TypeVariableUsage.class);
        final var methodUpperBound = ((TypeVariableUsage) methodTypeVar).upperBound();
        assertThat(methodUpperBound).isPresent();
        assertThat(((NamedTypeUsage) methodUpperBound.get()).typeName().toString()).contains("Comparable");
    }

    @Test
    void shouldDiscoverIntersectionTypeBound() {
        final var source = JavaFileObjects.forSourceString("Discoverable", """
            public class Discoverable<T extends Number & Comparable<T>> {
            }
            """);
        final var initializer = new JdkInitializer(List.of(), List.of(), List.of(source));
        final var codeModel = JdkInitializerTests.runInternal(initializer);

        final var naming = codeModel.getNameProvider();
        final var typeName = naming.getEmptyModuleTypeName("Discoverable");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var classTypeVar = descriptor.getTrait(ParameterizedTypeDescriptor.class).orElseThrow()
            .typeVariables().findFirst().orElseThrow();
        assertThat(classTypeVar).isInstanceOf(TypeVariableUsage.class);

        final var upperBound = ((TypeVariableUsage) classTypeVar).upperBound();
        assertThat(upperBound).isPresent();
        assertThat(upperBound.get()).isInstanceOf(IntersectionTypeUsage.class);

        final var boundNames = ((IntersectionTypeUsage) upperBound.get()).types()
            .map(t -> ((NamedTypeUsage) t).typeName().toString())
            .toList();
        assertThat(boundNames).anyMatch(name -> name.contains("Number"));
        assertThat(boundNames).anyMatch(name -> name.contains("Comparable"));
    }

    @Test
    void genericTypeNameShouldIncludeModule() {
        // Regression: visitDeclared was constructing TypeNames with Optional.empty() module,
        // causing generic raw types to not match descriptors registered with a JPMS module.
        final var source = JavaFileObjects.forSourceString("build.codemodel.jdk.example.Wrapper", """
            package build.codemodel.jdk.example;
            import java.util.List;
            public class Wrapper {
                public List<String> items;
            }
            """);
        final var codeModel = JdkInitializerTests.runInternal(
            new JdkInitializer(List.of(), List.of(), List.of(source)));

        final var naming = codeModel.getNameProvider();
        final var typeName = naming.getEmptyModuleTypeName("build.codemodel.jdk.example.Wrapper");
        final var field = codeModel.getTypeDescriptor(typeName).orElseThrow()
            .traits(FieldDescriptor.class).findFirst().orElseThrow();

        final var generic = (GenericTypeUsage) field.type();

        // The raw type must carry the java.base module
        final var javaBase = naming.getModuleName("java.base");
        final var expectedListName = naming.getTypeName(javaBase, "java.util.List");
        assertThat(generic.typeName()).isEqualTo(expectedListName);

        // The type argument (String) must also carry java.base
        final var stringArg = (NamedTypeUsage) generic.parameters().findFirst().orElseThrow();
        final var expectedStringName = naming.getTypeName(javaBase, "java.lang.String");
        assertThat(stringArg.typeName()).isEqualTo(expectedStringName);
    }

    @Test
    void shouldDiscoverGenericMethodTypeParameter() {
        final var source = JavaFileObjects.forSourceString("Discoverable", """
            public class Discoverable {
                public <T> T convert(Class<T> clazz) { return null; }
            }
            """);
        final var initializer = new JdkInitializer(List.of(), List.of(), List.of(source));
        final var codeModel = JdkInitializerTests.runInternal(initializer);

        final var naming = codeModel.getNameProvider();
        final var typeName = naming.getEmptyModuleTypeName("Discoverable");
        final var descriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var convertMethod = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("convert"))
            .findFirst()
            .orElseThrow();

        assertThat(convertMethod.getTrait(ParameterizedTypeDescriptor.class)).isPresent();
        final var parameterized = convertMethod.getTrait(ParameterizedTypeDescriptor.class).orElseThrow();
        // The method type parameter <T> is scoped under its enclosing class (empty module, no
        // namespace) so it can't collide with a T declared elsewhere.
        assertThat(parameterized.typeVariables()
            .map(tv -> tv.typeName().toString())
            .toList())
            .containsExactly("Discoverable$T");
        assertThat(parameterized.typeVariables()
            .map(tv -> tv.typeName().name().toString())
            .toList())
            .containsExactly("T");
    }
}
