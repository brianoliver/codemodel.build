package build.codemodel.objectoriented.descriptor;

import build.base.foundation.Lazy;
import build.base.marshalling.Marshal;
import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshalling;
import build.base.transport.json.JsonTransport;
import build.codemodel.expression.Expression;
import build.codemodel.expression.StringLiteral;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.FormalParameterDescriptor;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.transport.IrreducibleNameTransformer;
import build.codemodel.foundation.transport.ModuleNameTransformer;
import build.codemodel.foundation.transport.NamespaceTransformer;
import build.codemodel.foundation.transport.TypeNameTransformer;
import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.TypeVariableUsage;
import build.codemodel.foundation.usage.UnknownTypeUsage;
import build.codemodel.foundation.usage.VoidTypeUsage;
import build.codemodel.objectoriented.ObjectOrientedCodeModel;
import build.codemodel.objectoriented.expression.MethodUsage;
import build.codemodel.objectoriented.expression.SuperUsage;
import build.codemodel.objectoriented.expression.ThisUsage;
import build.codemodel.objectoriented.naming.MethodName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Marshalling tests for various {@link Marshal}able classes in the codemodel-objectoriented descriptor package.
 *
 * @author tim.berston
 * @since May-2025
 */
class MarshallingTests {

    private NameProvider nameProvider;
    private CodeModel codeModel;

    @BeforeEach
    void init() {
        nameProvider = new NonCachingNameProvider();
        codeModel = new ObjectOrientedCodeModel(nameProvider);
    }

    /**
     * Ensures that {@link MethodDescriptor} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallMethodDescriptor()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestClass"));

        final var typeDescriptor = ClassTypeDescriptor.of(codeModel, typeName);

        final var methodName = MethodName.of(
            typeName.moduleName(),
            typeName.namespace(),
            Optional.of(typeName),
            IrreducibleName.of("testMethod"));

        final var returnType = VoidTypeUsage.create(codeModel);
        final var formalParameters = Stream.of(
            FormalParameterDescriptor.of(
                codeModel,
                Optional.of(IrreducibleName.of("param1")),
                VoidTypeUsage.create(codeModel)));

        typeDescriptor.addTrait(MethodDescriptor.of(
            typeDescriptor,
            methodName,
            returnType,
            formalParameters));

        marshallAndTransportAndUnMarshalAndAssert(typeDescriptor);
    }

    /**
     * Ensures that {@link FieldDescriptor} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallFieldDescriptor()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(FieldDescriptor.of(
            codeModel,
            IrreducibleName.of("testField"),
            VoidTypeUsage.create(codeModel)));
    }

    /**
     * Ensures that a {@link ConstructorDescriptor} attached to its declaring {@link TypeDescriptor} —
     * the shape every real constructor is in ({@code JdkInitializer} always does
     * {@code typeDescriptor.addTrait(ConstructorDescriptor.of(typeDescriptor, ...))}) — round-trips
     * through marshalling, transport and unmarshalling using a {@link JsonTransport}. Like
     * {@link MethodDescriptor}, {@link ConstructorDescriptor}'s {@code typeDescriptor} is
     * {@code @Bound} rather than independently marshalled, so marshalling the {@link TypeDescriptor}
     * does not recurse back into re-marshalling itself via this trait.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallConstructorDescriptor()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestClass"));

        final var typeDescriptor = ClassTypeDescriptor.of(codeModel, typeName);

        final var formalParameters = Stream.of(
            FormalParameterDescriptor.of(
                codeModel,
                Optional.of(IrreducibleName.of("param1")),
                VoidTypeUsage.create(codeModel)));

        typeDescriptor.addTrait(ConstructorDescriptor.of(typeDescriptor, formalParameters));

        marshallAndTransportAndUnMarshalAndAssert(typeDescriptor);
    }

    /**
     * Ensures that {@link ParameterizedTypeDescriptor} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallParameterizedTypeDescriptor()
        throws IOException {

        final var typeVariables = Stream.of(
            TypeVariableUsage.of(
                codeModel,
                TypeName.of(
                    ModuleName.of("com.example", this.nameProvider),
                    Optional.empty(),
                    Optional.empty(),
                    IrreducibleName.of("T")),
                // TODO: This doesn't handle Optional.empty()
                Optional.of(Lazy.of(VoidTypeUsage.create(codeModel))),
                Optional.of(Lazy.of(UnknownTypeUsage.create(codeModel)))));

        marshallAndTransportAndUnMarshalAndAssert(ParameterizedTypeDescriptor.of(
            codeModel,
            typeVariables));
    }

    /**
     * Ensures that {@link MethodUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallMethodUsage()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestClass"));

        final var expression = StringLiteral.of(codeModel, "test");

        final var methodName = MethodName.of(
            typeName.moduleName(),
            typeName.namespace(),
            Optional.of(typeName),
            IrreducibleName.of("testMethod"));

        final var arguments = Stream.<Expression>of(
            StringLiteral.of(codeModel, "arg1"));

        marshallAndTransportAndUnMarshalAndAssert(MethodUsage.of(
            expression,
            methodName,
            arguments));
    }

    /**
     * Ensures that {@link ClassTypeDescriptor} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallClassTypeDescriptor()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestClass"));

        marshallAndTransportAndUnMarshalAndAssert(ClassTypeDescriptor.of(
            codeModel,
            typeName));
    }

    /**
     * Ensures that enum {@link Trait}s (e.g. {@link AccessModifier}) attached to a {@link ClassTypeDescriptor}
     * survive a marshal/transport/unmarshal round-trip.
     * <p>
     * This test will fail until {@code Marshalling.registerEnum} is available (Workday/base.build#42)
     * and called for {@link AccessModifier} and {@link Classification}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldPreserveEnumTraitsOnClassTypeDescriptorAfterMarshalling()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestClass"));

        final var typeDescriptor = ClassTypeDescriptor.of(codeModel, typeName);
        typeDescriptor.addTrait(AccessModifier.PUBLIC);
        typeDescriptor.addTrait(Classification.FINAL);

        final var unmarshalled = marshallAndTransportAndUnMarshal(typeDescriptor);

        assertThat(unmarshalled.hasTrait(AccessModifier.class))
            .as("AccessModifier trait should survive marshalling round-trip")
            .isTrue();
        assertThat(unmarshalled.trait(AccessModifier.class))
            .as("AccessModifier value should be preserved")
            .isEqualTo(AccessModifier.PUBLIC);
        assertThat(unmarshalled.hasTrait(Classification.class))
            .as("Classification trait should survive marshalling round-trip")
            .isTrue();
        assertThat(unmarshalled.trait(Classification.class))
            .as("Classification value should be preserved")
            .isEqualTo(Classification.FINAL);
    }

    /**
     * Ensures that {@link InterfaceTypeDescriptor} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallInterfaceTypeDescriptor()
        throws IOException {

        final var typeName = TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestInterface"));

        marshallAndTransportAndUnMarshalAndAssert(InterfaceTypeDescriptor.of(
            codeModel,
            typeName));
    }

    /**
     * Ensures that {@link ThisUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallThisUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(ThisUsage.of(codeModel));
    }

    /**
     * Ensures that {@link SuperUsage} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallSuperUsage()
        throws IOException {

        marshallAndTransportAndUnMarshalAndAssert(SuperUsage.of(codeModel));
    }

    /**
     * Ensures that {@link ImplementsTypeDescriptor} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallImplementsTypeDescriptor()
        throws IOException {

        final var namedTypeUsage = GenericTypeUsage.of(codeModel, TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestInterface")));

        marshallAndTransportAndUnMarshalAndAssert(ImplementsTypeDescriptor.of(namedTypeUsage));
    }

    /**
     * Ensures that {@link ExtendsTypeDescriptor} can be marshalled, transported and unmarshalled using a {@link JsonTransport}.
     *
     * @throws IOException if an error occurs during marshalling, transport or unmarshalling
     */
    @Test
    void shouldMarshallAndTransportAndUnmarshallExtendsTypeDescriptor()
        throws IOException {

        final var namedTypeUsage = GenericTypeUsage.of(codeModel, TypeName.of(
            ModuleName.of("com.example", this.nameProvider),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of("TestInterface")));

        marshallAndTransportAndUnMarshalAndAssert(ExtendsTypeDescriptor.of(namedTypeUsage));
    }

    private <T> void marshallAndTransportAndUnMarshalAndAssert(final T original)
        throws IOException {

        final var unmarshalled = marshallAndTransportAndUnMarshal(original);

        if (original instanceof TypeDescriptor otd && unmarshalled instanceof TypeDescriptor utd) {
            // TODO: Replace this with TypeDescriptor equality when it is repaired
            assertThat(otd.typeName())
                .isEqualTo(utd.typeName());
        } else {
            assertThat(original)
                .isEqualTo(unmarshalled);
        }
    }

    private <T> T marshallAndTransportAndUnMarshal(final T original)
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

        final var otherCodeModel = new ObjectOrientedCodeModel(nameProvider);
        marshaller.bind(CodeModel.class).to(otherCodeModel);

        // establish a String-based Reader from which to read (parse) the Json
        final var reader = new StringReader(writer.toString());

        final Marshalled<T> transported = transport.read(reader, marshaller);

        return marshaller.unmarshal(transported);
    }
} 
