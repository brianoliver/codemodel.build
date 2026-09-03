package build.codemodel.jdk.expression;

/*-
 * #%L
 * JDK Code Model
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

import build.codemodel.expression.BooleanLiteral;
import build.codemodel.expression.Expression;
import build.codemodel.expression.NumericLiteral;
import build.codemodel.foundation.descriptor.FormalParameterDescriptor;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.imperative.Block;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.statement.Assert;
import build.codemodel.jdk.statement.ExpressionStatement;
import build.codemodel.jdk.statement.SwitchCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that codemodel-jdk {@link Expression} subclasses correctly expose their structural
 * children via {@link build.base.mereology.Composite#parts()}.
 *
 * @author reed.vonredwitz
 * @since May-2026
 */
class MereologyTests {

    private JDKCodeModel codeModel;

    @BeforeEach
    void init() {
        codeModel = new JDKCodeModel(new NonCachingNameProvider());
    }

    private NumericLiteral num(final int value) {
        return NumericLiteral.of(codeModel, value);
    }

    private TypeUsage stringType() {
        return codeModel.getTypeUsage(String.class);
    }

    // -------------------------------------------------------------------------
    // MethodInvocation
    // -------------------------------------------------------------------------

    @Test
    void methodInvocationWithTargetAndArgsPartsContainsAll() {
        final var target = num(1);
        final var arg = num(2);
        final var receiverType = stringType();
        final var expr = MethodInvocation.of(codeModel, Optional.of(target), "toString",
            Stream.of(arg), Optional.of(receiverType));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(target, arg, receiverType);
    }

    @Test
    void methodInvocationTypeWitnessesAppearInParts() {
        final var target = num(1);
        final var arg = num(2);
        final var receiverType = stringType();
        final var witness = codeModel.getTypeUsage(Integer.class);
        final var expr = MethodInvocation.of(codeModel, Optional.of(target), "toString",
            Stream.of(arg), Optional.of(receiverType), Stream.of(witness));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(target, arg, receiverType, witness);
    }

    @Test
    void methodInvocationWithoutTargetOrReceiverPartsContainsOnlyArgs() {
        final var arg = num(42);
        final var expr = MethodInvocation.of(codeModel, Optional.empty(), "foo",
            Stream.of(arg), Optional.empty());
        assertThat(expr.parts().toList()).containsExactly(arg);
    }

    // -------------------------------------------------------------------------
    // FieldAccess
    // -------------------------------------------------------------------------

    @Test
    void fieldAccessPartsContainsTargetAndReceiverType() {
        final var target = num(1);
        final var receiverType = stringType();
        final var expr = FieldAccess.of(target, "length", Optional.of(receiverType));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(target, receiverType);
    }

    @Test
    void fieldAccessWithoutReceiverTypePartsContainsOnlyTarget() {
        final var target = num(1);
        final var expr = FieldAccess.of(target, "length", Optional.empty());
        assertThat(expr.parts().toList()).containsExactly(target);
    }

    // -------------------------------------------------------------------------
    // MethodReference
    // -------------------------------------------------------------------------

    @Test
    void methodReferencePartsContainsQualifierAndQualifierType() {
        final var qualifier = num(1);
        final var qualifierType = stringType();
        final var expr = MethodReference.of(qualifier, "toString", Optional.of(qualifierType));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(qualifier, qualifierType);
    }

    @Test
    void methodReferenceTypeWitnessesAppearInParts() {
        final var qualifier = num(1);
        final var qualifierType = stringType();
        final var witness = codeModel.getTypeUsage(Integer.class);
        final var expr = MethodReference.of(qualifier, "toString", Optional.of(qualifierType),
            Stream.of(witness));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(qualifier, qualifierType, witness);
    }

    // -------------------------------------------------------------------------
    // InstanceOf
    // -------------------------------------------------------------------------

    @Test
    void instanceOfPartsContainsExpressionAndType() {
        final var expression = num(1);
        final var type = stringType();
        final var expr = InstanceOf.of(expression, type);
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(expression, type);
    }

    // -------------------------------------------------------------------------
    // PostfixUnary / PrefixUnary
    // -------------------------------------------------------------------------

    @Test
    void postfixUnaryPartsContainsOperand() {
        final var operand = num(1);
        final var expr = PostfixUnary.of(codeModel, PostfixOperator.INCREMENT, operand);
        assertThat(expr.parts().toList()).containsExactly(operand);
    }

    @Test
    void prefixUnaryPartsContainsOperand() {
        final var operand = num(1);
        final var expr = PrefixUnary.of(codeModel, PrefixOperator.INCREMENT, operand);
        assertThat(expr.parts().toList()).containsExactly(operand);
    }

    // -------------------------------------------------------------------------
    // Parenthesized
    // -------------------------------------------------------------------------

    @Test
    void parenthesizedPartsContainsInner() {
        final var inner = num(5);
        final var expr = Parenthesized.of(inner);
        assertThat(expr.parts().toList()).containsExactly(inner);
    }

    // -------------------------------------------------------------------------
    // BitwiseBinary
    // -------------------------------------------------------------------------

    @Test
    void bitwiseBinaryPartsContainsLeftAndRight() {
        final var left = num(1);
        final var right = num(2);
        final var expr = BitwiseBinary.of(codeModel, BitwiseOperator.AND, left, right);
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(left, right);
    }

    // -------------------------------------------------------------------------
    // CompoundAssignment
    // -------------------------------------------------------------------------

    @Test
    void compoundAssignmentPartsContainsVariableAndValue() {
        final var variable = num(1);
        final var value = num(2);
        final var expr = CompoundAssignment.of(codeModel, AssignmentOperator.PLUS, variable, value);
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(variable, value);
    }

    // -------------------------------------------------------------------------
    // Ternary
    // -------------------------------------------------------------------------

    @Test
    void ternaryPartsContainsConditionThenAndElse() {
        final var condition = num(1);
        final var thenExpr = num(2);
        final var elseExpr = num(3);
        final var expr = Ternary.of(condition, thenExpr, elseExpr);
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(condition, thenExpr, elseExpr);
    }

    // -------------------------------------------------------------------------
    // ArrayAccess
    // -------------------------------------------------------------------------

    @Test
    void arrayAccessPartsContainsArrayIndexAndType() {
        final var array = num(1);
        final var index = num(0);
        final var arrayType = stringType();
        final var expr = ArrayAccess.of(array, index, Optional.of(arrayType));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(array, index, arrayType);
    }

    // -------------------------------------------------------------------------
    // NewObject
    // -------------------------------------------------------------------------

    @Test
    void newObjectPartsContainsTypeArgsAndTypeArguments() {
        final var type = stringType();
        final var arg = num(1);
        final var typeArg = codeModel.getTypeUsage(Integer.class);
        final var expr = NewObject.of(codeModel, type, Stream.of(arg), Stream.of(typeArg));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(type, arg, typeArg);
    }

    @Test
    void newObjectTypeWitnessesAppearInPartsAndAreDistinctFromTypeArguments() {
        final var type = stringType();
        final var arg = num(1);
        final var typeArg = codeModel.getTypeUsage(Integer.class);
        final var witness = codeModel.getTypeUsage(Long.class);
        final var expr = NewObject.of(codeModel, type, Stream.of(arg), Stream.of(typeArg),
            Optional.empty(), Optional.empty(), Stream.of(witness));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(type, arg, typeArg, witness);
    }

    // -------------------------------------------------------------------------
    // NewArray
    // -------------------------------------------------------------------------

    @Test
    void newArrayPartsContainsElementTypeAndDimensions() {
        final var elementType = stringType();
        final var dim = num(5);
        final var expr = NewArray.of(codeModel, elementType, Stream.of(dim));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(elementType, dim);
    }

    // -------------------------------------------------------------------------
    // SwitchExpression
    // -------------------------------------------------------------------------

    @Test
    void switchExpressionPartsContainsSelectorAndCases() {
        final var selector = num(1);
        final var switchCase = SwitchCase.of(codeModel, Stream.of(num(1)),
            Stream.of(ExpressionStatement.of(num(2))));
        final var expr = SwitchExpression.of(selector, Stream.of(switchCase));
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(selector, switchCase);
    }

    // -------------------------------------------------------------------------
    // Lambda
    // -------------------------------------------------------------------------

    @Test
    void lambdaPartsContainsParametersAndBody() {
        final var param = new LambdaParameter(codeModel, Optional.of(stringType()), "s");
        final var body = Block.of(Assert.of(BooleanLiteral.of(codeModel, true), Optional.empty()));
        final var expr = Lambda.of(codeModel, List.of(param), body);
        assertThat(expr.parts().toList()).containsExactlyInAnyOrder(param, body);
    }

    @Test
    void lambdaParameterWithType_partsContainsType() {
        final var type = stringType();
        final var param = new LambdaParameter(codeModel, Optional.of(type), "s");
        assertThat(param.parts().toList()).containsExactly(type);
    }

    @Test
    void lambdaParameterWithoutType_partsIsEmpty() {
        final var param = new LambdaParameter(codeModel, Optional.empty(), "s");
        assertThat(param.parts().toList()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Symbol (Identifier trait)
    // -------------------------------------------------------------------------

    @Test
    void symbolLocalVariable_partsContainsDeclaredType() {
        final var type = stringType();
        assertThat(new Symbol.LocalVariable(type).parts().toList()).containsExactly(type);
    }

    @Test
    void symbolParameter_partsContainsDeclaredType() {
        final var type = stringType();
        final var descriptor = FormalParameterDescriptor.of(codeModel, Optional.of(IrreducibleName.of("value")), type);
        assertThat(new Symbol.Parameter(descriptor).parts().toList()).containsExactly(type);
    }

    @Test
    void symbolField_partsContainsDeclaredType() {
        // Symbol.Field.descriptor() re-resolves the live FieldDescriptor on every call (so it
        // tracks a type across rescans instead of pinning to a stale descriptor), but parts()
        // must never route through it: JDKCodeModel#compositeContains walks method/constructor
        // bodies looking for a TypeName, and if a field access exposed its resolved descriptor's
        // structure as a part, an access like `Other.field` could spuriously "contain" types that
        // only appear on the declaring field elsewhere (declaringType.parameterTypes,
        // annotations, generic bounds, ...) and never at the access site itself - the same class
        // of leak that ResolvedMethod needed an explicit exclude() for in compositeContains.
        // declaringType below deliberately doesn't resolve to a real type (descriptor() is empty),
        // proving parts() is computed from declaredType alone, independent of resolution.
        final var type = stringType();
        final var declaringType = codeModel.getEmptyModuleTypeName("com.example.Foo");
        final var field = new Symbol.Field(type, codeModel, declaringType, "value");
        assertThat(field.descriptor()).isEmpty();
        assertThat(field.parts().toList()).containsExactly(type);
    }

    @Test
    void symbolTypeReference_partsContainsType() {
        final var type = stringType();
        assertThat(new Symbol.TypeReference(type).parts().toList()).containsExactly(type);
    }

    @Test
    void symbolThisReference_partsContainsDeclaredType() {
        final var type = stringType();
        assertThat(new Symbol.ThisReference(type).parts().toList()).containsExactly(type);
    }

    @Test
    void symbolSuperReference_partsContainsDeclaredType() {
        final var type = stringType();
        assertThat(new Symbol.SuperReference(type).parts().toList()).containsExactly(type);
    }

    // -------------------------------------------------------------------------
    // ClassLiteral
    // -------------------------------------------------------------------------

    @Test
    void classLiteralPartsContainsReferencedType() {
        final var type = stringType();
        final var expr = ClassLiteral.of(codeModel, type);
        assertThat(expr.parts().toList()).containsExactly(type);
    }
}
