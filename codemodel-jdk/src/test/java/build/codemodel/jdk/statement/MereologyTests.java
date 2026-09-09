package build.codemodel.jdk.statement;

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
import build.codemodel.expression.NumericLiteral;
import build.codemodel.expression.StringLiteral;
import build.codemodel.expression.VariableUsage;
import build.codemodel.expression.naming.VariableName;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.ModuleName;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.imperative.Block;
import build.codemodel.imperative.Statement;
import build.codemodel.jdk.JDKCodeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that each {@link Statement} subclass in {@code codemodel-jdk} correctly exposes
 * its structural children via {@link build.base.mereology.Composite#parts()} and
 * {@link build.base.mereology.Composite#composition()}.
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

    private VariableUsage variable(final String name) {
        return VariableUsage.of(codeModel, VariableName.of(
            ModuleName.of("java.lang", codeModel.getNameProvider()),
            Optional.empty(),
            Optional.empty(),
            IrreducibleName.of(name)));
    }

    private NumericLiteral num(final int value) {
        return NumericLiteral.of(codeModel, value);
    }

    private BooleanLiteral bool(final boolean value) {
        return BooleanLiteral.of(codeModel, value);
    }

    private Block block(final Statement... stmts) {
        return Block.of(stmts);
    }

    // -------------------------------------------------------------------------
    // Assert
    // -------------------------------------------------------------------------

    @Test
    void assertWithMessagePartsContainsConditionAndMessage() {
        final var condition = bool(true);
        final var message = StringLiteral.of(codeModel, "oops");
        final var stmt = Assert.of(condition, Optional.of(message));
        final var parts = stmt.parts().toList();
        assertEquals(2, parts.size());
        assertTrue(parts.contains(condition));
        assertTrue(parts.contains(message));
    }

    @Test
    void assertWithoutMessagePartsContainsOnlyCondition() {
        final var condition = bool(true);
        final var stmt = Assert.of(condition, Optional.empty());
        final var parts = stmt.parts().toList();
        assertEquals(1, parts.size());
        assertTrue(parts.contains(condition));
    }

    // -------------------------------------------------------------------------
    // Break / Continue — no composite parts
    // -------------------------------------------------------------------------

    @Test
    void breakPartsIsEmpty() {
        assertTrue(Break.of(codeModel, Optional.empty()).parts().toList().isEmpty());
    }

    @Test
    void continuePartsIsEmpty() {
        assertTrue(Continue.of(codeModel, Optional.empty()).parts().toList().isEmpty());
    }

    // -------------------------------------------------------------------------
    // CatchClause
    // -------------------------------------------------------------------------

    @Test
    void catchClausePartsContainsExceptionTypesAndBody() {
        final var exType = codeModel.getTypeUsage(Exception.class);
        final var body = block(Assert.of(bool(false), Optional.empty()));
        final var stmt = CatchClause.of(codeModel, false, List.of(exType), "e", body);
        final var parts = stmt.parts().toList();
        assertTrue(parts.contains(exType));
        assertTrue(parts.contains(body));
    }

    // -------------------------------------------------------------------------
    // DoWhile
    // -------------------------------------------------------------------------

    @Test
    void doWhilePartsContainsBodyAndCondition() {
        final var body = Assert.of(bool(true), Optional.empty());
        final var condition = bool(false);
        final var stmt = DoWhile.of(body, condition);
        final var parts = stmt.parts().toList();
        assertEquals(2, parts.size());
        assertTrue(parts.contains(body));
        assertTrue(parts.contains(condition));
    }

    // -------------------------------------------------------------------------
    // EnhancedFor
    // -------------------------------------------------------------------------

    @Test
    void enhancedForPartsContainsTypeIterableAndBody() {
        final var type = codeModel.getTypeUsage(String.class);
        final var iterable = variable("items");
        final var body = Assert.of(bool(true), Optional.empty());
        final var stmt = EnhancedFor.of(codeModel, false, type, "item", iterable, body);
        final var parts = stmt.parts().toList();
        assertTrue(parts.contains(type));
        assertTrue(parts.contains(iterable));
        assertTrue(parts.contains(body));
    }

    // -------------------------------------------------------------------------
    // ExpressionStatement
    // -------------------------------------------------------------------------

    @Test
    void expressionStatementPartsContainsExpression() {
        final var expr = num(7);
        final var stmt = ExpressionStatement.of(expr);
        final var parts = stmt.parts().toList();
        assertEquals(1, parts.size());
        assertTrue(parts.contains(expr));
    }

    // -------------------------------------------------------------------------
    // For
    // -------------------------------------------------------------------------

    @Test
    void forPartsContainsInitializersConditionUpdatesAndBody() {
        final var init = ExpressionStatement.of(num(0));
        final var condition = bool(true);
        final var update = num(1);
        final var body = Assert.of(bool(true), Optional.empty());
        final var stmt = For.of(codeModel, Stream.of(init), Optional.of(condition), Stream.of(update), body);
        final var parts = stmt.parts().toList();
        assertTrue(parts.contains(init));
        assertTrue(parts.contains(condition));
        assertTrue(parts.contains(update));
        assertTrue(parts.contains(body));
    }

    @Test
    void forWithoutConditionPartsOmitsCondition() {
        final var body = Assert.of(bool(true), Optional.empty());
        final var stmt = For.of(codeModel, Stream.empty(), Optional.empty(), Stream.empty(), body);
        final var parts = stmt.parts().toList();
        assertEquals(1, parts.size());
        assertTrue(parts.contains(body));
    }

    // -------------------------------------------------------------------------
    // Labeled
    // -------------------------------------------------------------------------

    @Test
    void labeledPartsContainsInnerStatement() {
        final var inner = Assert.of(bool(true), Optional.empty());
        final var stmt = Labeled.of(codeModel, "outer", inner);
        final var parts = stmt.parts().toList();
        assertEquals(1, parts.size());
        assertTrue(parts.contains(inner));
    }

    // -------------------------------------------------------------------------
    // LocalVariableDeclaration
    // -------------------------------------------------------------------------

    @Test
    void localVariableDeclarationWithInitializerPartsContainsTypeAndInitializer() {
        final var type = codeModel.getTypeUsage(int.class);
        final var init = num(42);
        final var stmt = LocalVariableDeclaration.of(codeModel, false, type, "x", Optional.of(init));
        final var parts = stmt.parts().toList();
        assertEquals(2, parts.size());
        assertTrue(parts.contains(type));
        assertTrue(parts.contains(init));
    }

    @Test
    void localVariableDeclarationWithoutInitializerPartsContainsOnlyType() {
        final var type = codeModel.getTypeUsage(int.class);
        final var stmt = LocalVariableDeclaration.of(codeModel, false, type, "x", Optional.empty());
        final var parts = stmt.parts().toList();
        assertEquals(1, parts.size());
        assertTrue(parts.contains(type));
    }

    // -------------------------------------------------------------------------
    // SwitchCase
    // -------------------------------------------------------------------------

    @Test
    void switchCasePartsContainsLabelsAndStatements() {
        final var label = num(1);
        final var body = Assert.of(bool(true), Optional.empty());
        final var stmt = SwitchCase.of(codeModel, Stream.of(label), Stream.of(body));
        final var parts = stmt.parts().toList();
        assertTrue(parts.contains(label));
        assertTrue(parts.contains(body));
    }

    // -------------------------------------------------------------------------
    // SwitchStatement
    // -------------------------------------------------------------------------

    @Test
    void switchStatementPartsContainsSelectorAndCases() {
        final var selector = num(1);
        final var switchCase = SwitchCase.of(codeModel, Stream.of(num(1)), Stream.of(Assert.of(bool(true), Optional.empty())));
        final var stmt = SwitchStatement.of(selector, Stream.of(switchCase));
        final var parts = stmt.parts().toList();
        assertTrue(parts.contains(selector));
        assertTrue(parts.contains(switchCase));
    }

    // -------------------------------------------------------------------------
    // Synchronized
    // -------------------------------------------------------------------------

    @Test
    void synchronizedPartsContainsLockAndBody() {
        final var lock = variable("monitor");
        final var body = block(Assert.of(bool(true), Optional.empty()));
        final var stmt = Synchronized.of(lock, body);
        final var parts = stmt.parts().toList();
        assertEquals(2, parts.size());
        assertTrue(parts.contains(lock));
        assertTrue(parts.contains(body));
    }

    // -------------------------------------------------------------------------
    // Throw
    // -------------------------------------------------------------------------

    @Test
    void throwPartsContainsExpression() {
        final var expr = variable("ex");
        final var stmt = Throw.of(expr);
        final var parts = stmt.parts().toList();
        assertEquals(1, parts.size());
        assertTrue(parts.contains(expr));
    }

    // -------------------------------------------------------------------------
    // Try
    // -------------------------------------------------------------------------

    @Test
    void tryPartsContainsBodyCatchesAndFinally() {
        final var body = block(Assert.of(bool(true), Optional.empty()));
        final var catchClause = CatchClause.of(codeModel, false, List.of(codeModel.getTypeUsage(Exception.class)), "e", block(Throw.of(variable("e"))));
        final var finallyBlock = block(Assert.of(bool(false), Optional.empty()));
        final var stmt = Try.of(codeModel, body, Stream.of(catchClause), Optional.of(finallyBlock));
        final var parts = stmt.parts().toList();
        assertTrue(parts.contains(body));
        assertTrue(parts.contains(catchClause));
        assertTrue(parts.contains(finallyBlock));
    }

    @Test
    void tryCompositionReachesNestedStatements() {
        final var deepAssert = Assert.of(bool(true), Optional.empty());
        final var body = block(deepAssert);
        final var stmt = Try.of(codeModel, body, Stream.empty(), Optional.empty());
        final var composition = stmt.composition().toList();
        assertTrue(composition.contains(body));
        assertTrue(composition.contains(deepAssert));
    }
}
