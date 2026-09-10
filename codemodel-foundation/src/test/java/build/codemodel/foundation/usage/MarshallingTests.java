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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        // write the Marshalled<CodeModel> using the Transport
        transport.write(marshalled, writer);

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
