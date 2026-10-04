package build.codemodel.foundation.usage;

import build.base.foundation.Lazy;
import build.base.marshalling.Marshal;
import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshalling;
import build.base.transport.json.JsonTransport;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.ConceptualCodeModel;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.transport.IrreducibleNameTransformer;
import build.codemodel.foundation.transport.ModuleNameTransformer;
import build.codemodel.foundation.transport.NamespaceTransformer;
import build.codemodel.foundation.transport.TypeNameTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Marshalling tests for various {@link Marshal}able classes.
 *
 * @author tim.berston
 * @since Apr-2025
 */
class MarshallingTests {

    private NameProvider nameProvider;
    private CodeModel codeModel;

    @BeforeEach
    void init() {
        nameProvider = new NonCachingNameProvider();
        codeModel = new ConceptualCodeModel(nameProvider);
    }

    /**
     * Ensures that {@link VoidTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallVoidTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(VoidTypeUsage.create(codeModel));
    }

    /**
     * Ensures that {@link UnknownTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallUnknownTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(UnknownTypeUsage.create(codeModel));
    }

    /**
     * Ensures that {@link AnnotationTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallAnnotationTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(AnnotationTypeUsage.of(codeModel,
            TypeName.of(
                ModuleName.of("some.module", this.nameProvider),
                Optional.empty(),
                Optional.empty(),
                IrreducibleName.of("MyType")),
            AnnotationValue.of(codeModel, "Annotation1", "Value1"),
            AnnotationValue.of(codeModel, "Annotation2", "Value2")));
    }

    /**
     * Ensures that an {@link AnnotationValue.Value.ClassRef} survives a marshal → transport →
     * unmarshal round-trip. Regression test: the raw-{@link Object} marshalling this replaced
     * serialized a {@link AnnotationValue.Value.ClassRef} to a bare {@link TypeName} with no
     * matching deserialize case, so it silently came back as a {@link AnnotationValue.Value.Literal}
     * instead.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallAnnotationValueClassRef()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("some.module", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("MyType"));

        marshallAndTransportAndUnMarshalAndAssert(
            AnnotationValue.of(codeModel, "classValue", new AnnotationValue.Value.ClassRef(typeName)));
    }

    /**
     * Ensures that an {@link AnnotationValue.Value.EnumConstant} survives a marshal → transport →
     * unmarshal round-trip. Regression test: the raw-{@link Object} marshalling this replaced
     * serialized an {@link AnnotationValue.Value.EnumConstant} to a formatted {@code "Type.CONSTANT"}
     * string with no matching deserialize case, so it silently came back as a
     * {@link AnnotationValue.Value.Literal} instead, losing the type name and constant name as
     * separate fields.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallAnnotationValueEnumConstant()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("some.module", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("MyEnum"));

        marshallAndTransportAndUnMarshalAndAssert(
            AnnotationValue.of(codeModel, "enumValue", new AnnotationValue.Value.EnumConstant(typeName, "CONSTANT")));
    }

    /**
     * Ensures that an {@link AnnotationValue.Value.Nested} annotation value survives a marshal →
     * transport → unmarshal round-trip.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallAnnotationValueNested()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("some.module", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("NestedAnnotation"));
        final var nested = AnnotationTypeUsage.of(codeModel, typeName,
            AnnotationValue.of(codeModel, "inner", "innerValue"));

        marshallAndTransportAndUnMarshalAndAssert(
            AnnotationValue.of(codeModel, "nestedValue", new AnnotationValue.Value.Nested(nested)));
    }

    /**
     * Ensures that an {@link AnnotationValue.Value.Array} survives a marshal → transport →
     * unmarshal round-trip.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallAnnotationValueArray()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(
            AnnotationValue.of(codeModel, "arrayValue", new AnnotationValue.Value.Array(List.of(
                new AnnotationValue.Value.Literal("a"),
                new AnnotationValue.Value.Literal("b")))));
    }

    /**
     * Ensures that each JLS-legal {@link AnnotationValue.Value.Literal} payload type survives a
     * marshal → transport → unmarshal round-trip with its runtime type intact.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallAnnotationValueLiteral()
        throws IOException {

        final Object[] payloads = {
            Boolean.TRUE,
            (byte) 7,
            (short) 9,
            42,
            123456789012L,
            3.14f,
            2.718281828d,
            'z',
            "hello",
        };

        for (final var payload : payloads) {
            final var context = "payload [" + payload + "] of type " + payload.getClass().getName();

            final var unmarshalled = marshallAndTransportAndUnMarshalAndAssert(
                AnnotationValue.of(codeModel, "literalValue", new AnnotationValue.Value.Literal(payload)),
                context);

            final var literal = (AnnotationValue.Value.Literal) unmarshalled.value();
            assertEquals(payload.getClass(), literal.value().getClass(),
                "runtime type must survive the round-trip for " + context);
        }
    }

    /**
     * The {@link AnnotationValue.Value.Literal} canonical constructor rejects a payload that is
     * not one of the JLS-legal element types (a primitive wrapper or {@link String}).
     */
    @Test
    void shouldRejectANonJlsLiteralPayload() {
        assertThrows(IllegalArgumentException.class,
            () -> new AnnotationValue.Value.Literal(new Date()));
    }

    /**
     * The {@link AnnotationValue.Value.Literal} canonical constructor rejects a {@code null} payload.
     */
    @Test
    void shouldRejectANullLiteralPayload() {
        assertThrows(NullPointerException.class,
            () -> new AnnotationValue.Value.Literal(null));
    }

    /**
     * Ensures that {@link ArrayTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallArrayTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(
            ArrayTypeUsage.of(codeModel, Lazy.of(VoidTypeUsage.create(codeModel))));
    }

    /**
     * Ensures that {@link GenericTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallGenericTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(GenericTypeUsage.of(codeModel,
            TypeName.of(
                ModuleName.of("some.module", this.nameProvider),
                Optional.empty(),
                Optional.empty(),
                IrreducibleName.of("MyType")),
            VoidTypeUsage.create(codeModel),
            UnknownTypeUsage.create(codeModel)));
    }

    /**
     * Ensures that {@link IntersectionTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalIntersectionTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(IntersectionTypeUsage.of(codeModel,
            VoidTypeUsage.create(codeModel),
            UnknownTypeUsage.create(codeModel)));
    }

    /**
     * Ensures that {@link SpecificTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalSpecificTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(SpecificTypeUsage.of(codeModel,
            TypeName.of(
                ModuleName.of("some.module", this.nameProvider),
                Optional.empty(),
                Optional.empty(),
                IrreducibleName.of("MyType"))));
    }

    /**
     * Ensures that {@link TypeVariableUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalTypeVariableUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(TypeVariableUsage.of(codeModel,
            TypeName.of(
                ModuleName.of("some.module", this.nameProvider),
                Optional.empty(),
                Optional.empty(),
                IrreducibleName.of("MyType")),
            Optional.of(Lazy.of(VoidTypeUsage.create(codeModel))),
            Optional.of(Lazy.of(UnknownTypeUsage.create(codeModel)))));
    }

    /**
     * Ensures that a self-referential {@link TypeVariableUsage} (as in {@code T extends Comparable<T>})
     * survives a marshal → transport → unmarshal round-trip.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalRecursiveTypeVariableUsage()
        throws IOException {

        assertRecursiveRoundTrip(
            recursiveTypeVariable(variable -> generic("Comparable", variable)),
            variable -> ((GenericTypeUsage) variable.upperBound().orElseThrow())
                .parameters().findFirst().orElseThrow());
    }

    /**
     * Ensures that a {@link TypeVariableUsage} that refers back to itself through a bounded
     * {@link WildcardTypeUsage} (as in {@code T extends Foo<? extends T>}) survives a marshal →
     * transport → unmarshal round-trip.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalRecursiveTypeVariableUsageThroughWildcard()
        throws IOException {

        assertRecursiveRoundTrip(
            recursiveTypeVariable(variable -> generic("Foo",
                WildcardTypeUsage.of(codeModel, Optional.empty(), Optional.of(Lazy.of(variable))))),
            variable -> ((WildcardTypeUsage) ((GenericTypeUsage) variable.upperBound().orElseThrow())
                .parameters().findFirst().orElseThrow()).upperBound().orElseThrow());
    }

    /**
     * Ensures that a {@link TypeVariableUsage} that refers back to itself through an
     * {@link IntersectionTypeUsage} bound (as in {@code T extends Number & Comparable<T>}) survives a
     * marshal → transport → unmarshal round-trip.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalRecursiveTypeVariableUsageThroughIntersection()
        throws IOException {

        assertRecursiveRoundTrip(
            recursiveTypeVariable(variable -> IntersectionTypeUsage.of(codeModel,
                SpecificTypeUsage.of(codeModel, typeName("Number")),
                generic("Comparable", variable))),
            variable -> ((GenericTypeUsage) ((IntersectionTypeUsage) variable.upperBound().orElseThrow())
                .types().skip(1).findFirst().orElseThrow()).parameters().findFirst().orElseThrow());
    }

    /**
     * Ensures that a {@link TypeVariableUsage} that refers back to itself through the <i>lower-bound</i> of a
     * {@link WildcardTypeUsage} (as in {@code T extends Foo<? super T>}) survives a marshal → transport →
     * unmarshal round-trip.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalRecursiveTypeVariableUsageThroughWildcardLowerBound()
        throws IOException {

        assertRecursiveRoundTrip(
            recursiveTypeVariable(variable -> generic("Foo",
                WildcardTypeUsage.of(codeModel, Optional.of(Lazy.of(variable)), Optional.empty()))),
            variable -> ((WildcardTypeUsage) ((GenericTypeUsage) variable.upperBound().orElseThrow())
                .parameters().findFirst().orElseThrow()).lowerBound().orElseThrow());
    }

    /**
     * Ensures that a {@link TypeVariableUsage} that refers back to itself through a
     * {@link UnionTypeUsage} survives a marshal → transport → unmarshal round-trip.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalRecursiveTypeVariableUsageThroughUnion()
        throws IOException {

        assertRecursiveRoundTrip(
            recursiveTypeVariable(variable -> UnionTypeUsage.of(codeModel,
                SpecificTypeUsage.of(codeModel, typeName("Number")),
                generic("Comparable", variable))),
            variable -> ((GenericTypeUsage) ((UnionTypeUsage) variable.upperBound().orElseThrow())
                .types().skip(1).findFirst().orElseThrow()).parameters().findFirst().orElseThrow());
    }

    /**
     * Ensures that {@link UnionTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallUnionTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(UnionTypeUsage.of(codeModel,
            VoidTypeUsage.create(codeModel),
            UnknownTypeUsage.create(codeModel)));
    }

    /**
     * Ensures that an unbounded {@link WildcardTypeUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalWildcardTypeUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(WildcardTypeUsage.create(codeModel));
    }

    /**
     * Ensures that a bounded {@link WildcardTypeUsage} (with lower and upper bounds) survives a
     * marshal → transport → unmarshal round-trip with bounds preserved.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshalBoundedWildcardTypeUsage()
        throws IOException {

        final var original = WildcardTypeUsage.of(codeModel,
            Optional.of(Lazy.of(VoidTypeUsage.create(codeModel))),
            Optional.of(Lazy.of(UnknownTypeUsage.create(codeModel))));

        final var marshaller = Marshalling.newMarshaller();
        final var marshalled = marshaller.marshal(original);

        final var transport = new JsonTransport();
        transport.register(new IrreducibleNameTransformer(nameProvider));
        transport.register(new ModuleNameTransformer(nameProvider));
        transport.register(new NamespaceTransformer(nameProvider));
        transport.register(new TypeNameTransformer(nameProvider));

        final var writer = new StringWriter();
        transport.write(marshalled, writer);

        final var otherCodeModel = new ConceptualCodeModel(nameProvider);
        marshaller.bind(CodeModel.class).to(otherCodeModel);

        final var reader = new StringReader(writer.toString());
        final Marshalled<WildcardTypeUsage> transported = transport.read(reader, marshaller);
        final var unmarshalled = marshaller.unmarshal(transported);

        assertEquals(original, unmarshalled);
        // Explicitly verify bounds survive the round-trip — the real regression the
        // prior @Unmarshal/@Marshal fix was intended to catch
        assertTrue(unmarshalled.lowerBound().isPresent(), "lower bound must be preserved");
        assertTrue(unmarshalled.upperBound().isPresent(), "upper bound must be preserved");
    }

    /**
     * Creates a {@link TypeName} in {@code some.module} with the specified simple name.
     *
     * @param name the simple name
     * @return a new {@link TypeName}
     */
    private TypeName typeName(final String name) {
        return TypeName.of(
            ModuleName.of("some.module", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of(name));
    }

    /**
     * Creates a {@link GenericTypeUsage} of the named type with the specified parameters.
     *
     * @param name       the simple name of the generic type
     * @param parameters the parameters
     * @return a new {@link GenericTypeUsage}
     */
    private GenericTypeUsage generic(final String name, final TypeUsage... parameters) {
        return GenericTypeUsage.of(codeModel, typeName(name), parameters);
    }

    /**
     * Creates a {@link TypeVariableUsage} named {@code T} whose <i>upper-bound</i> may refer back to the
     * variable itself.
     *
     * @param upperBound a {@link Function} that, given the variable, produces its upper-bound
     * @return a new, self-referential {@link TypeVariableUsage}
     */
    private TypeVariableUsage recursiveTypeVariable(final Function<TypeVariableUsage, TypeUsage> upperBound) {
        final var bound = Lazy.<TypeUsage>empty();
        final var typeVariable = TypeVariableUsage.of(codeModel,
            typeName("T"),
            Optional.empty(),
            Optional.of(bound));
        bound.set(upperBound.apply(typeVariable));
        return typeVariable;
    }

    /**
     * Round-trips the specified self-referential {@link TypeVariableUsage} and obtains the result, after
     * asserting that {@code backReference} (given the unmarshalled variable) is that very variable and not a copy.
     *
     * @param typeVariable  the {@link TypeVariableUsage} to round-trip
     * @param backReference locates the back-reference to the variable within the unmarshalled variable
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    private void assertRecursiveRoundTrip(final TypeVariableUsage typeVariable,
                                          final Function<TypeVariableUsage, TypeUsage> backReference)
        throws IOException {

        final var unmarshalled = marshallAndTransportAndUnMarshalAndAssert(typeVariable, null);
        assertSame(unmarshalled, backReference.apply(unmarshalled));
    }

    private <T> void marshallAndTransportAndUnMarshalAndAssert(final T original)
        throws IOException {
        marshallAndTransportAndUnMarshalAndAssert(original, null);
    }

    @SuppressWarnings("UnusedReturnValue")
    private <T> T marshallAndTransportAndUnMarshalAndAssert(final T original, final String message)
        throws IOException {
        final var marshaller = Marshalling.newMarshaller();
        final var marshalled = marshaller.marshal(original);

        final var transport = new JsonTransport();

        // include the CodeModel specific Transformers
        // (encoding/decoding some CodeModel types as primitive types)
        transport.register(new IrreducibleNameTransformer(nameProvider));
        transport.register(new ModuleNameTransformer(nameProvider));
        transport.register(new NamespaceTransformer(nameProvider));
        transport.register(new TypeNameTransformer(nameProvider));

        // establish a String-based Writer into which to write the Json
        final var writer = new StringWriter();

        // write the Marshalled using the Transport, and the very Marshaller that produced it, so a cycle
        // back to the outermost object is recognized as that same Marshalled
        transport.write(marshalled, writer, marshaller);

        final var otherCodeModel = new ConceptualCodeModel(nameProvider);
        marshaller.bind(CodeModel.class).to(otherCodeModel);

        // establish a String-based Reader from which to read (parse) the Json
        final var reader = new StringReader(writer.toString());

        final Marshalled<T> transported = transport.read(reader, marshaller);

        final var unmarshalled = marshaller.unmarshal(transported);

        assertEquals(original, unmarshalled, message);

        return unmarshalled;
    }
}
