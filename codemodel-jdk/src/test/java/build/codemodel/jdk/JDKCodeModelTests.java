package build.codemodel.jdk;

import build.codemodel.foundation.descriptor.ThrowableDescriptor;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.IntersectionTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.foundation.usage.TypeVariableUsage;
import build.codemodel.foundation.usage.WildcardTypeUsage;
import build.codemodel.hierarchical.descriptor.HierarchicalTypeDescriptor;
import build.codemodel.jdk.descriptor.AnnotationType;
import build.codemodel.jdk.descriptor.EnclosingTypeDescriptor;
import build.codemodel.jdk.descriptor.EnumConstantDescriptor;
import build.codemodel.jdk.descriptor.EnumType;
import build.codemodel.jdk.descriptor.Final;
import build.codemodel.jdk.descriptor.JDKTypeDescriptor;
import build.codemodel.jdk.descriptor.MemberTypeDescriptor;
import build.codemodel.jdk.descriptor.NonSealed;
import build.codemodel.jdk.descriptor.PermitsTypeDescriptor;
import build.codemodel.jdk.descriptor.ReceiverAnnotation;
import build.codemodel.jdk.descriptor.RecordComponentDescriptor;
import build.codemodel.jdk.descriptor.RecordType;
import build.codemodel.jdk.descriptor.Sealed;
import build.codemodel.jdk.descriptor.Varargs;
import build.codemodel.jdk.example.AbstractPerson;
import build.codemodel.jdk.example.AnnotatedGenericContainer;
import build.codemodel.jdk.example.AnnotatedReceiverExample;
import build.codemodel.jdk.example.AnnotatedTypeParameterExample;
import build.codemodel.jdk.example.BoundedContainer;
import build.codemodel.jdk.example.ColorExample;
import build.codemodel.jdk.example.Container;
import build.codemodel.jdk.example.Description;
import build.codemodel.jdk.example.FinalParamExample;
import build.codemodel.jdk.example.MultiBoundContainer;
import build.codemodel.jdk.example.NonAbstractPerson;
import build.codemodel.jdk.example.OuterExample;
import build.codemodel.jdk.example.PointExample;
import build.codemodel.jdk.example.RawFieldContainer;
import build.codemodel.jdk.example.SealedCircle;
import build.codemodel.jdk.example.SealedPolygon;
import build.codemodel.jdk.example.SealedShape;
import build.codemodel.jdk.example.ThrowingExample;
import build.codemodel.jdk.example.VarargsExample;
import build.codemodel.jdk.example.WildcardContainer;
import build.codemodel.objectoriented.descriptor.AccessModifier;
import build.codemodel.objectoriented.descriptor.Classification;
import build.codemodel.objectoriented.descriptor.ConstructorDescriptor;
import build.codemodel.objectoriented.descriptor.ExtendsTypeDescriptor;
import build.codemodel.objectoriented.descriptor.FieldDescriptor;
import build.codemodel.objectoriented.descriptor.ImplementsTypeDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JDKCodeModel}.
 *
 * @author brian.oliver
 * @since Sep-2024
 */
class JDKCodeModelTests {

    /**
     * Creates a new {@link JDKCodeModel}.
     *
     * @return a new {@link JDKCodeModel}
     */
    protected JDKCodeModel createCodeModel() {
        final var nameProvider = new NonCachingNameProvider();
        return new JDKCodeModel(nameProvider);
    }

    /**
     * Ensure a new {@link JDKCodeModel} can be created.
     */
    @Test
    void shouldCreateCodeModel() {
        final var codeModel = createCodeModel();

        assertThat(codeModel)
            .isNotNull();

        assertThat(codeModel.typeDescriptors())
            .isNotEmpty();

        final var objectTypeDescriptor = codeModel.getJDKTypeDescriptor(Object.class)
            .orElseThrow();

        assertThat(objectTypeDescriptor.typeName().canonicalName())
            .isEqualTo("java.lang.Object");
    }

    /**
     * Ensure a {@link TypeDescriptor} can be created from an {@link AbstractPerson} using the {@link JDKCodeModel}.
     */
    @Test
    void shouldCreateTypeDescriptorForAbstractPersonClass() {
        final var codeModel = createCodeModel();

        final var typeDescriptor = codeModel.getJDKTypeDescriptor(AbstractPerson.class)
            .orElseThrow();

        assertThat(typeDescriptor.typeName().canonicalName())
            .isEqualTo(AbstractPerson.class.getCanonicalName());

        assertThat(typeDescriptor.getTrait(AccessModifier.class))
            .contains(AccessModifier.PUBLIC);

        assertThat(typeDescriptor.getTrait(Classification.class))
            .contains(Classification.ABSTRACT);

        assertThat(typeDescriptor.getTrait(ExtendsTypeDescriptor.class)
            .orElseThrow()
            .parentTypeUsage()
            .typeName()
            .canonicalName())
            .isEqualTo(Object.class.getCanonicalName());

        assertThat(typeDescriptor.traits(ImplementsTypeDescriptor.class))
            .isEmpty();

        assertThat(typeDescriptor.traits(ConstructorDescriptor.class))
            .hasSize(1);

        final var constructorDescriptor = typeDescriptor.getTrait(ConstructorDescriptor.class)
            .orElseThrow();

        assertThat(constructorDescriptor.getTrait(AccessModifier.class))
            .contains(AccessModifier.PROTECTED);

        assertThat(constructorDescriptor.callableName()
            .typeName()
            .orElseThrow())
            .isEqualTo(typeDescriptor.typeName());

        assertThat(constructorDescriptor.formalParameters())
            .hasSize(2);

        assertThat(typeDescriptor.traits(MethodDescriptor.class))
            .hasSize(5);

        assertThat(typeDescriptor.traits(FieldDescriptor.class))
            .hasSize(4);
    }

    /**
     * Ensure a {@link TypeDescriptor} can be created from a {@link NonAbstractPerson} using the {@link JDKCodeModel}.
     */
    @Test
    void shouldCreateTypeDescriptorForNonPersonClass() {
        final var codeModel = createCodeModel();

        final var typeDescriptor = codeModel.getJDKTypeDescriptor(NonAbstractPerson.class)
            .orElseThrow();

        assertThat(typeDescriptor.typeName().canonicalName())
            .isEqualTo(NonAbstractPerson.class.getCanonicalName());

        assertThat(typeDescriptor.getTrait(AccessModifier.class))
            .contains(AccessModifier.PUBLIC);

        assertThat(typeDescriptor.getTrait(Classification.class))
            .contains(Classification.CONCRETE);

        assertThat(typeDescriptor.getTrait(ExtendsTypeDescriptor.class)
            .orElseThrow()
            .parentTypeUsage()
            .typeName()
            .canonicalName())
            .isEqualTo(AbstractPerson.class.getCanonicalName());

        assertThat(typeDescriptor.traits(ImplementsTypeDescriptor.class))
            .isEmpty();

        assertThat(typeDescriptor.traits(ConstructorDescriptor.class))
            .hasSize(1);

        final var constructorDescriptor = typeDescriptor.getTrait(ConstructorDescriptor.class)
            .orElseThrow();

        assertThat(constructorDescriptor.getTrait(AccessModifier.class))
            .contains(AccessModifier.PUBLIC);

        assertThat(constructorDescriptor.callableName()
            .typeName()
            .orElseThrow())
            .isEqualTo(typeDescriptor.typeName());

        assertThat(constructorDescriptor.formalParameters())
            .hasSize(2);

        final var deprecatedMethodDescriptor = typeDescriptor.traits(MethodDescriptor.class)
            .findFirst()
            .orElseThrow();

        assertThat(deprecatedMethodDescriptor.signature())
            .isEqualTo("java.lang.String fullName()");

        assertThat(deprecatedMethodDescriptor.traits(AnnotationTypeUsage.class))
            .hasSize(2);

        assertThat(deprecatedMethodDescriptor.traits(AnnotationTypeUsage.class)
            .filter(annotationTypeUsage -> annotationTypeUsage
                .typeName()
                .canonicalName()
                .equals(Deprecated.class.getCanonicalName()))
            .findFirst())
            .isPresent();

        assertThat(deprecatedMethodDescriptor.traits(AnnotationTypeUsage.class)
            .filter(annotationTypeUsage -> annotationTypeUsage
                .typeName()
                .canonicalName()
                .equals(Description.class.getCanonicalName()))
            .findFirst()
            .orElseThrow()
            .values()
            .findFirst()
            .orElseThrow()
            .toString())
            .contains("Calculates the full name");

        assertThat(typeDescriptor.traits(FieldDescriptor.class))
            .isEmpty();

        // ensure we can find the MethodDescriptors
        final var methodDescriptors = codeModel.getTraitsInHierarchy(typeDescriptor, MethodDescriptor.class)
            .toList();

        assertThat(methodDescriptors)
            .hasSize(6);
    }

    /**
     * Ensure a {@link TypeDescriptor} can be created from a generically declared {@link Container} using the
     * {@link JDKCodeModel}.
     */
    @Test
    void shouldCreateTypeDescriptorForContainer() {
        final var codeModel = createCodeModel();

        final var typeDescriptor = codeModel.getJDKTypeDescriptor(Container.class)
            .orElseThrow();

        assertThat(typeDescriptor.typeName().canonicalName())
            .isEqualTo(Container.class.getCanonicalName());

        assertThat(typeDescriptor.getTrait(AccessModifier.class))
            .contains(AccessModifier.PUBLIC);

        assertThat(typeDescriptor.getTrait(Classification.class))
            .contains(Classification.CONCRETE);

        assertThat(typeDescriptor.getTrait(ExtendsTypeDescriptor.class)
            .orElseThrow()
            .parentTypeUsage()
            .typeName()
            .canonicalName())
            .isEqualTo(Object.class.getCanonicalName());

        assertThat(typeDescriptor.traits(ImplementsTypeDescriptor.class))
            .isEmpty();

        assertThat(typeDescriptor.traits(ConstructorDescriptor.class))
            .hasSize(1);

        final var constructorDescriptor = typeDescriptor.getTrait(ConstructorDescriptor.class)
            .orElseThrow();

        assertThat(constructorDescriptor.getTrait(AccessModifier.class))
            .contains(AccessModifier.PUBLIC);

        assertThat(constructorDescriptor.callableName()
            .typeName()
            .orElseThrow())
            .isEqualTo(typeDescriptor.typeName());

        assertThat(constructorDescriptor.formalParameters())
            .hasSize(0);

        assertThat(typeDescriptor.traits(MethodDescriptor.class))
            .hasSize(0);

        assertThat(typeDescriptor.traits(FieldDescriptor.class))
            .hasSize(1);

        final var fieldDescriptor = typeDescriptor.getTrait(FieldDescriptor.class)
            .orElseThrow();

        assertThat(fieldDescriptor.type())
            .isInstanceOf(GenericTypeUsage.class);
    }

    /**
     * Ensure {@link HierarchicalTypeDescriptor} navigation is possible.
     */
    @Test
    void shouldNavigateHierarchicalTypeDescriptors() {
        final var codeModel = createCodeModel();

        final var typeDescriptor = codeModel.getJDKTypeDescriptor(NonAbstractPerson.class)
            .orElseThrow();

        final var parentTypeDescriptor = typeDescriptor.parent()
            .orElseThrow();

        final var objectTypeDescriptor = parentTypeDescriptor.parent()
            .orElseThrow();

        // ensure isChild is as expected
        assertThat(parentTypeDescriptor.isChild(typeDescriptor))
            .isTrue();

        assertThat(parentTypeDescriptor.isChild(objectTypeDescriptor))
            .isFalse();

        assertThat(objectTypeDescriptor.isChild(parentTypeDescriptor))
            .isTrue();

        // ensure isRootType is as expected
        assertThat(typeDescriptor.isRoot())
            .isFalse();

        assertThat(parentTypeDescriptor.isRoot())
            .isFalse();

        assertThat(objectTypeDescriptor.isRoot())
            .isTrue();

        // ensure parents(...) is as expected
        assertThat(typeDescriptor.parents())
            .containsExactly(parentTypeDescriptor);

        assertThat(typeDescriptor.parents(JDKTypeDescriptor.class))
            .containsExactly(parentTypeDescriptor);

        // ensure ancestors(...) is as expected
        assertThat(typeDescriptor.ancestors())
            .containsExactly(parentTypeDescriptor, objectTypeDescriptor);

        assertThat(typeDescriptor.ancestors(JDKTypeDescriptor.class))
            .containsExactly(parentTypeDescriptor, objectTypeDescriptor);

        // ensure children(...) is as expected
        assertThat(objectTypeDescriptor.children()
            .filter(childTypeDescriptor -> childTypeDescriptor.typeName().moduleName()
                .equals(parentTypeDescriptor.typeName().moduleName())))
            .containsExactly(parentTypeDescriptor);

        assertThat(objectTypeDescriptor.children(JDKTypeDescriptor.class)
            .filter(childTypeDescriptor -> childTypeDescriptor.typeName().moduleName()
                .equals(parentTypeDescriptor.typeName().moduleName())))
            .containsExactly(parentTypeDescriptor);

        assertThat(parentTypeDescriptor.children())
            .containsExactly(typeDescriptor);

        assertThat(parentTypeDescriptor.children(JDKTypeDescriptor.class))
            .containsExactly(typeDescriptor);

        assertThat(typeDescriptor.children())
            .isEmpty();

        // ensure descendants(...) is as expected
        assertThat(parentTypeDescriptor.descendants())
            .containsExactly(typeDescriptor);

        assertThat(parentTypeDescriptor.descendants(JDKTypeDescriptor.class))
            .containsExactly(typeDescriptor);

        assertThat(objectTypeDescriptor.descendants()
            .filter(descendantTypeDescriptor -> descendantTypeDescriptor.typeName().moduleName()
                .equals(parentTypeDescriptor.typeName().moduleName())))
            .containsExactly(parentTypeDescriptor, typeDescriptor);

        assertThat(objectTypeDescriptor.descendants(JDKTypeDescriptor.class)
            .filter(descendantTypeDescriptor -> descendantTypeDescriptor.typeName().moduleName()
                .equals(parentTypeDescriptor.typeName().moduleName())))
            .containsExactly(parentTypeDescriptor, typeDescriptor);

        assertThat(codeModel.roots())
            .hasSize(11);
    }

    /**
     * Ensure that resolving a {@link TypeDescriptor} for a class with a self-referential type variable bound
     * (e.g. {@code E extends Enum<E>}) does not cause infinite recursion.
     */
    @Test
    void shouldResolveTypeDescriptorForSelfReferentialTypeVariable() {
        final var codeModel = createCodeModel();

        // Enum<E extends Enum<E>> is the canonical self-referential type variable;
        // if the recursion guard is absent this call will throw StackOverflowError
        final var typeDescriptor = codeModel.getJDKTypeDescriptor(Enum.class);

        assertThat(typeDescriptor)
            .isPresent();

        assertThat(typeDescriptor.orElseThrow().typeName().canonicalName())
            .isEqualTo("java.lang.Enum");
    }

    @Test
    void shouldDiscoverWildcardBoundsViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(WildcardContainer.class).orElseThrow();

        final var upperField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("upper"))
            .findFirst().orElseThrow();
        final var upperParam = ((GenericTypeUsage) upperField.type()).parameters().findFirst().orElseThrow();
        assertThat(upperParam).isInstanceOf(WildcardTypeUsage.class);
        final var upperWildcard = (WildcardTypeUsage) upperParam;
        assertThat(upperWildcard.upperBound()).isPresent();
        assertThat(upperWildcard.lowerBound()).isEmpty();
        assertThat(((NamedTypeUsage) upperWildcard.upperBound().orElseThrow()).typeName().toString())
            .contains("Number");

        final var lowerField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("lower"))
            .findFirst().orElseThrow();
        final var lowerParam = ((GenericTypeUsage) lowerField.type()).parameters().findFirst().orElseThrow();
        assertThat(lowerParam).isInstanceOf(WildcardTypeUsage.class);
        final var lowerWildcard = (WildcardTypeUsage) lowerParam;
        assertThat(lowerWildcard.lowerBound()).isPresent();
        assertThat(lowerWildcard.upperBound()).isEmpty();
        assertThat(((NamedTypeUsage) lowerWildcard.lowerBound().orElseThrow()).typeName().toString())
            .contains("Integer");

        final var unboundedField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("unbounded"))
            .findFirst().orElseThrow();
        final var unboundedParam = ((GenericTypeUsage) unboundedField.type()).parameters().findFirst().orElseThrow();
        assertThat(unboundedParam).isInstanceOf(WildcardTypeUsage.class);
        final var unboundedWildcard = (WildcardTypeUsage) unboundedParam;
        assertThat(unboundedWildcard.upperBound()).isEmpty();
        assertThat(unboundedWildcard.lowerBound()).isEmpty();
    }

    @Test
    void shouldDiscoverTypeParameterDeclarationViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(Container.class).orElseThrow();

        assertThat(descriptor.getTrait(ParameterizedTypeDescriptor.class)).isPresent();

        final var typeVars = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow()
            .typeVariables()
            .toList();
        assertThat(typeVars).hasSize(1);
        assertThat(typeVars.getFirst().typeName().name().toString()).isEqualTo("T");
    }

    @Test
    void shouldAttachThrowableDescriptorToMethodDescriptorViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(ThrowingExample.class).orElseThrow();

        final var readMethod = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("read"))
            .findFirst().orElseThrow();

        assertThat(readMethod.traits(ThrowableDescriptor.class)).hasSize(1);
        assertThat(((NamedTypeUsage) readMethod.traits(ThrowableDescriptor.class)
            .findFirst().orElseThrow().throwable()).typeName().canonicalName())
            .isEqualTo("java.io.IOException");

        // The ThrowableDescriptor must NOT appear on the type itself
        assertThat(descriptor.traits(ThrowableDescriptor.class)).isEmpty();
    }

    @Test
    void shouldAttachThrowableDescriptorToConstructorDescriptorViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(ThrowingExample.class).orElseThrow();

        final var ctor = descriptor.getTrait(ConstructorDescriptor.class).orElseThrow();

        assertThat(ctor.traits(ThrowableDescriptor.class)).hasSize(1);
        assertThat(((NamedTypeUsage) ctor.traits(ThrowableDescriptor.class)
            .findFirst().orElseThrow().throwable()).typeName().canonicalName())
            .isEqualTo("java.io.IOException");

        // The ThrowableDescriptor must NOT appear on the type itself
        assertThat(descriptor.traits(ThrowableDescriptor.class)).isEmpty();
    }

    @Test
    void shouldMarkVarargsParameterWithVarargsTraitViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(VarargsExample.class).orElseThrow();

        // Method: format(String prefix, Object... args) — last param is varargs
        final var formatMethod = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("format"))
            .findFirst().orElseThrow();
        final var formatParams = formatMethod.formalParameters().toList();
        assertThat(formatParams.get(0).hasTrait(Varargs.class)).as("prefix is not varargs").isFalse();
        assertThat(formatParams.get(1).hasTrait(Varargs.class)).as("args is varargs").isTrue();

        // Method: fixed(String a, String b) — no varargs
        final var fixedMethod = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("fixed"))
            .findFirst().orElseThrow();
        assertThat(fixedMethod.formalParameters().noneMatch(p -> p.hasTrait(Varargs.class)))
            .as("fixed() has no varargs params").isTrue();

        // Constructor: VarargsExample(String first, String... rest) — last param is varargs
        final var ctor = descriptor.getTrait(ConstructorDescriptor.class).orElseThrow();
        final var ctorParams = ctor.formalParameters().toList();
        assertThat(ctorParams.get(0).hasTrait(Varargs.class)).as("first is not varargs").isFalse();
        assertThat(ctorParams.get(1).hasTrait(Varargs.class)).as("rest is varargs").isTrue();
    }

    @Test
    void shouldMarkFinalParameterWithFinalTraitViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(FinalParamExample.class).orElseThrow();

        // Constructor: FinalParamExample(final String key, String value)
        final var ctor = descriptor.getTrait(ConstructorDescriptor.class).orElseThrow();
        final var ctorParams = ctor.formalParameters().toList();
        assertThat(ctorParams.get(0).hasTrait(Final.class)).as("key is final").isTrue();
        assertThat(ctorParams.get(1).hasTrait(Final.class)).as("value is not final").isFalse();

        // Method: store(final String key, String value)
        final var storeMethod = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("store"))
            .findFirst().orElseThrow();
        final var storeParams = storeMethod.formalParameters().toList();
        assertThat(storeParams.get(0).hasTrait(Final.class)).as("key is final").isTrue();
        assertThat(storeParams.get(1).hasTrait(Final.class)).as("value is not final").isFalse();
    }

    @Test
    void shouldDiscoverBoundedTypeParameterDeclarationViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(BoundedContainer.class).orElseThrow();

        assertThat(descriptor.getTrait(ParameterizedTypeDescriptor.class)).isPresent();

        final var typeVar = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow()
            .typeVariables()
            .findFirst()
            .orElseThrow();
        assertThat(typeVar).isInstanceOf(TypeVariableUsage.class);
        assertThat(typeVar.typeName().name().toString()).isEqualTo("T");
        assertThat(typeVar.upperBound()).isPresent();
        assertThat(((NamedTypeUsage) typeVar.upperBound().get()).typeName().toString()).contains("Number");
    }

    /**
     * Demonstrates: an unbounded type variable ({@code <T>}) has no upper bound trait, matching the
     * source-parsing path. Reflection reports an implicit {@code java.lang.Object} bound for every type
     * variable, so the reflection path must elide it rather than surfacing it as an explicit upper bound.
     */
    @Test
    void shouldDiscoverUnboundedTypeParameterWithNoUpperBoundViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(Container.class).orElseThrow();

        final var typeVar = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow()
            .typeVariables()
            .findFirst()
            .orElseThrow();
        assertThat(typeVar).isInstanceOf(TypeVariableUsage.class);
        assertThat(typeVar.upperBound()).isEmpty();
    }

    /**
     * Demonstrates: a multi-bound type variable ({@code T extends Number & Comparable<T>}) must resolve
     * to an {@link IntersectionTypeUsage} on the reflection path, just as it does on the source-parsing
     * path (see {@code GenericsDiscoveryTests.shouldDiscoverIntersectionTypeBound}).
     */
    @Test
    void shouldDiscoverIntersectionTypeBoundViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(MultiBoundContainer.class).orElseThrow();

        final var typeVar = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow()
            .typeVariables()
            .findFirst()
            .orElseThrow();
        assertThat(typeVar).isInstanceOf(TypeVariableUsage.class);

        final var upperBound = typeVar.upperBound();
        assertThat(upperBound).isPresent();
        assertThat(upperBound.get()).isInstanceOf(IntersectionTypeUsage.class);

        final var boundNames = ((IntersectionTypeUsage) upperBound.get()).types()
            .map(t -> ((NamedTypeUsage) t).typeName().toString())
            .toList();
        assertThat(boundNames).anyMatch(name -> name.contains("Number"));
        assertThat(boundNames).anyMatch(name -> name.contains("Comparable"));
    }

    /**
     * Guards against a regression of a {@link StackOverflowError} in {@code TypeVariableUsage.render()}.
     * For {@code T extends Number & Comparable<T>}, T's upper bound is
     * {@code IntersectionTypeUsage[Number, Comparable<T>]} — two levels of nesting between T and its own
     * self-reference. {@code TypeVariableUsage.render()}'s cycle guard must be threaded through every level
     * of that nesting, not just the immediate bound, or rendering recurses forever.
     */
    @Test
    void shouldNotStackOverflowRenderingIntersectionTypeBoundViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(MultiBoundContainer.class).orElseThrow();

        final var typeVar = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow()
            .typeVariables()
            .findFirst()
            .orElseThrow();

        assertThat(typeVar.canonicalName()).isNotBlank();
    }

    /**
     * Demonstrates: a raw usage of a generic type ({@code List raw;}, no type argument) should resolve
     * to a {@link GenericTypeUsage} with zero parameters, distinguishing it from usage of a genuinely
     * non-generic type. Reflection currently cannot tell "raw usage of a generic class" apart from
     * "non-generic class" and always produces a {@code SpecificTypeUsage} instead.
     */
    @Test
    void shouldDiscoverRawGenericUsageAsGenericTypeUsageViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(RawFieldContainer.class).orElseThrow();

        final var rawField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("raw"))
            .findFirst().orElseThrow();

        assertThat(rawField.type()).isInstanceOf(GenericTypeUsage.class);
        assertThat(((GenericTypeUsage) rawField.type()).parameters()).isEmpty();
    }

    /**
     * Demonstrates: a {@code TYPE_USE} annotation nested inside a generic type argument
     * ({@code List<@NonNull String>}) must be preserved on the reflection path, just as it is on the
     * source-parsing path (see {@code TypeAnnotationDiscoveryTests.shouldPreserveAnnotationOnGenericTypeArgument}).
     * Reflection currently only reads the outermost {@code AnnotatedType}'s annotations and drops
     * annotations nested inside {@code AnnotatedParameterizedType.getAnnotatedActualTypeArguments()}.
     */
    @Test
    void shouldPreserveAnnotationOnGenericTypeArgumentViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(AnnotatedGenericContainer.class).orElseThrow();

        final var itemsField = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("items"))
            .findFirst().orElseThrow();

        assertThat(itemsField.type()).isInstanceOf(GenericTypeUsage.class);
        final var arg = ((GenericTypeUsage) itemsField.type()).parameters().findFirst().orElseThrow();
        assertThat(arg.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList())
            .contains("NonNull");
    }

    /**
     * Ensure a {@code sealed} type discovered via reflection carries the {@link Sealed} trait along
     * with {@link PermitsTypeDescriptor} traits for each permitted subtype.
     */
    @Test
    void shouldCaptureSealedAndPermitsOnTypeViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(SealedShape.class).orElseThrow();

        assertThat(descriptor.getTrait(Sealed.class))
            .as("sealed type SealedShape should carry the Sealed trait")
            .contains(Sealed.SEALED);

        final var permittedTypeNames = descriptor.traits(PermitsTypeDescriptor.class)
            .map(permits -> permits.parentTypeUsage().typeName().name().toString())
            .toList();

        assertThat(permittedTypeNames)
            .as("sealed type SealedShape should record its permitted subtypes")
            .containsExactlyInAnyOrder("SealedCircle", "SealedPolygon");
    }

    /**
     * {@code java.lang.reflect.Modifier} has no bit for {@code non-sealed}, so the reflection path
     * (see {@code JDKCodeModel#isDirectSubtypeOfSealed}) must infer the {@link NonSealed} trait by
     * walking the class hierarchy: a non-final direct subtype of a {@code sealed} superclass or
     * superinterface must itself be {@code non-sealed}.
     */
    @Test
    void shouldCaptureNonSealedOnTypeViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(SealedPolygon.class).orElseThrow();

        assertThat(descriptor.getTrait(NonSealed.class))
            .as("non-sealed type SealedPolygon should carry the NonSealed trait")
            .contains(NonSealed.NON_SEALED);
    }

    /**
     * A {@code final} permitted subtype of a {@code sealed} type must not be inferred as
     * {@code non-sealed} — {@code isDirectSubtypeOfSealed} only applies once {@code final} has
     * already been ruled out.
     */
    @Test
    void shouldNotCaptureNonSealedOnFinalSubtypeOfSealedTypeViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(SealedCircle.class).orElseThrow();

        assertThat(descriptor.getTrait(NonSealed.class))
            .as("final type SealedCircle should not carry the NonSealed trait")
            .isEmpty();
    }

    /**
     * A type unrelated to any {@code sealed} hierarchy must not be inferred as {@code non-sealed}.
     */
    @Test
    void shouldNotCaptureNonSealedOnUnrelatedTypeViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(Container.class).orElseThrow();

        assertThat(descriptor.getTrait(NonSealed.class))
            .as("Container is not a subtype of any sealed type and should not carry the NonSealed trait")
            .isEmpty();
    }

    /**
     * Demonstrates a known, unfixable limitation: an explicitly bounded {@code ? extends Object}
     * wildcard cannot be distinguished from an unbounded {@code ?} wildcard via reflection. Both
     * {@link java.lang.reflect.WildcardType#getUpperBounds()} and
     * {@link java.lang.reflect.AnnotatedWildcardType#getAnnotatedUpperBounds()} synthesize
     * {@code [Object.class]} for both cases; the distinction only survives in the raw bytecode
     * {@code Signature} attribute ({@code *} vs {@code +Ljava/lang/Object;}), which {@code java.lang.reflect}
     * does not expose. Fixing this would require hand-parsing generic signature bytecode. The
     * source-parsing path does not have this limitation since {@code javax.lang.model.type.WildcardType}
     * preserves the distinction.
     */
    @Test
    @Disabled("java.lang.reflect cannot distinguish `? extends Object` from unbounded `?` -- "
        + "see method Javadoc")
    void shouldDistinguishExplicitObjectBoundFromUnboundedWildcardViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(WildcardContainer.class).orElseThrow();

        final var field = descriptor.traits(FieldDescriptor.class)
            .filter(f -> f.fieldName().toString().equals("explicitObjectBound"))
            .findFirst().orElseThrow();
        final var param = ((GenericTypeUsage) field.type()).parameters().findFirst().orElseThrow();
        assertThat(param).isInstanceOf(WildcardTypeUsage.class);

        final var wildcard = (WildcardTypeUsage) param;
        assertThat(wildcard.upperBound()).isPresent();
        assertThat(((NamedTypeUsage) wildcard.upperBound().orElseThrow()).typeName().toString())
            .contains("Object");
    }

    @Test
    void shouldModelEnumConstantsViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(ColorExample.class).orElseThrow();

        assertThat(descriptor.hasTrait(EnumType.class))
            .as("an enum should carry the EnumType kind marker, matching the source-parsing path")
            .isTrue();

        // traits() does not guarantee insertion order (see JDKCodeModelDeclarationOrderTests), so
        // sort by the EnumConstantDescriptor's own order() -- backed by Enum#ordinal() -- rather than
        // relying on stream order
        final var constantNames = descriptor.traits(EnumConstantDescriptor.class)
            .sorted(Comparator.comparingInt(EnumConstantDescriptor::order))
            .map(c -> c.name().toString())
            .toList();

        assertThat(constantNames)
            .as("enum constants should be modeled as EnumConstantDescriptors, in ordinal order")
            .containsExactly("RED", "GREEN", "BLUE");

        // enum constants must not also appear as FieldDescriptors
        assertThat(descriptor.traits(FieldDescriptor.class)
            .map(f -> f.fieldName().toString()))
            .as("enum constants should not be double-modeled as fields")
            .containsExactly("label");
    }

    @Test
    void shouldModelRecordComponentsViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(PointExample.class).orElseThrow();

        assertThat(descriptor.hasTrait(RecordType.class))
            .as("a record should carry the RecordType kind marker, matching the source-parsing path")
            .isTrue();

        final var components = descriptor.traits(RecordComponentDescriptor.class).toList();

        assertThat(components.stream().map(c -> c.name().toString()))
            .as("record components should be modeled as RecordComponentDescriptors")
            .containsExactlyInAnyOrder("x", "y");

        final var xComponent = components.stream()
            .filter(c -> c.name().toString().equals("x"))
            .findFirst().orElseThrow();
        assertThat(((NamedTypeUsage) xComponent.type()).typeName().canonicalName())
            .isEqualTo("int");
    }

    @Test
    void shouldModelNestedTypesViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(OuterExample.class).orElseThrow();

        final var memberTypeNames = descriptor.traits(MemberTypeDescriptor.class)
            .map(m -> m.memberTypeName().canonicalName())
            .toList();

        assertThat(memberTypeNames)
            .as("declared member types should be modeled as MemberTypeDescriptors")
            .containsExactlyInAnyOrder(
                OuterExample.NestedClass.class.getCanonicalName(),
                OuterExample.NestedInterface.class.getCanonicalName(),
                OuterExample.NestedEnum.class.getCanonicalName(),
                OuterExample.NestedRecord.class.getCanonicalName());

        // and the nested types themselves should be resolvable as full JDKTypeDescriptors
        final var nestedClass = codeModel.getJDKTypeDescriptor(OuterExample.NestedClass.class).orElseThrow();
        final var nestedInterface = codeModel.getJDKTypeDescriptor(OuterExample.NestedInterface.class).orElseThrow();

        // each member type should carry the EnclosingTypeDescriptor back-pointer, matching the
        // source-parsing path
        assertThat(nestedClass.hasTrait(EnclosingTypeDescriptor.class)).isTrue();
        assertThat(nestedInterface.hasTrait(EnclosingTypeDescriptor.class)).isTrue();
    }

    @Test
    void shouldModelAnnotationTypeKindViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(Description.class).orElseThrow();

        assertThat(descriptor.hasTrait(AnnotationType.class))
            .as("an @interface should carry the AnnotationType kind marker, matching the source-parsing path")
            .isTrue();
    }

    @Test
    void shouldModelKindMarkerAndEnclosingTypeForNestedEnumAndRecordViaReflection() {
        final var codeModel = createCodeModel();

        final var nestedEnum = codeModel.getJDKTypeDescriptor(OuterExample.NestedEnum.class).orElseThrow();
        assertThat(nestedEnum.hasTrait(EnumType.class))
            .as("a nested enum should carry the EnumType kind marker")
            .isTrue();
        assertThat(nestedEnum.hasTrait(EnclosingTypeDescriptor.class))
            .as("a nested enum should also carry the EnclosingTypeDescriptor back-pointer")
            .isTrue();

        final var nestedRecord = codeModel.getJDKTypeDescriptor(OuterExample.NestedRecord.class).orElseThrow();
        assertThat(nestedRecord.hasTrait(RecordType.class))
            .as("a nested record should carry the RecordType kind marker")
            .isTrue();
        assertThat(nestedRecord.hasTrait(EnclosingTypeDescriptor.class))
            .as("a nested record should also carry the EnclosingTypeDescriptor back-pointer")
            .isTrue();
    }

    @Test
    void shouldNotModelEnclosingTypeOrKindMarkersForPlainTopLevelClassViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(OuterExample.class).orElseThrow();

        assertThat(descriptor.hasTrait(EnclosingTypeDescriptor.class))
            .as("a top-level type should not carry an EnclosingTypeDescriptor")
            .isFalse();
        assertThat(descriptor.hasTrait(EnumType.class))
            .as("a plain class should not carry the EnumType kind marker")
            .isFalse();
        assertThat(descriptor.hasTrait(RecordType.class))
            .as("a plain class should not carry the RecordType kind marker")
            .isFalse();
        assertThat(descriptor.hasTrait(AnnotationType.class))
            .as("a plain class should not carry the AnnotationType kind marker")
            .isFalse();
    }

    /**
     * Demonstrates: an annotation written directly on a type parameter declaration
     * ({@code <@NonNull T>}) must be preserved on the reflection path, matching the source-parsing
     * path (see {@code AnnotationCaptureGapsTests.shouldCaptureAnnotationOnTypeParameterDeclaration}).
     * {@code TypeVariable} is itself an {@code AnnotatedElement}, so this is distinct from the
     * annotations on the type variable's bounds.
     */
    @Test
    void shouldCaptureAnnotationOnTypeParameterDeclarationViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(AnnotatedTypeParameterExample.class).orElseThrow();

        final var typeVar = descriptor.getTrait(ParameterizedTypeDescriptor.class)
            .orElseThrow()
            .typeVariables()
            .findFirst()
            .orElseThrow();

        assertThat(typeVar.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactly("NonNull");
    }

    /**
     * Demonstrates: an annotation written directly on a method's receiver parameter
     * ({@code @NonNull AnnotatedReceiverExample this}) must be captured on the reflection path,
     * matching the source-parsing path (see
     * {@code AnnotationCaptureGapsTests.shouldCaptureReceiverParameterAnnotationOnMethod}).
     * {@code Method.getParameters()} excludes the receiver, so without explicitly reading
     * {@code getAnnotatedReceiverType()} this annotation would be silently dropped.
     */
    @Test
    void shouldCaptureReceiverParameterAnnotationOnMethodViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(AnnotatedReceiverExample.class).orElseThrow();

        final var runMethod = descriptor.traits(MethodDescriptor.class)
            .filter(m -> m.methodName().name().toString().equals("run"))
            .findFirst().orElseThrow();

        assertThat(runMethod.traits(ReceiverAnnotation.class)
            .map(ReceiverAnnotation::annotation)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactly("NonNull");
    }

    /**
     * Demonstrates: an annotation written on an inner class constructor's outer-instance receiver
     * ({@code @NonNull AnnotatedReceiverExample AnnotatedReceiverExample.this}) must be captured on
     * the reflection path, matching the source-parsing path (see
     * {@code AnnotationCaptureGapsTests.shouldCaptureReceiverParameterAnnotationOnInnerClassConstructor}).
     */
    @Test
    void shouldCaptureReceiverParameterAnnotationOnInnerClassConstructorViaReflection() {
        final var codeModel = createCodeModel();
        final var descriptor = codeModel.getJDKTypeDescriptor(AnnotatedReceiverExample.Inner.class).orElseThrow();

        final var constructor = descriptor.traits(ConstructorDescriptor.class)
            .findFirst().orElseThrow();

        assertThat(constructor.traits(ReceiverAnnotation.class)
            .map(ReceiverAnnotation::annotation)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactly("NonNull");
    }
}
