package build.codemodel.jdk.populator;

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

import build.codemodel.expression.Expression;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.imperative.Block;
import build.codemodel.imperative.If;
import build.codemodel.imperative.Return;
import build.codemodel.imperative.Statement;
import build.codemodel.imperative.While;
import build.codemodel.jdk.expression.InstanceOf;
import build.codemodel.jdk.statement.Assert;
import build.codemodel.jdk.statement.Break;
import build.codemodel.jdk.statement.CatchClause;
import build.codemodel.jdk.statement.Continue;
import build.codemodel.jdk.statement.DoWhile;
import build.codemodel.jdk.statement.EnhancedFor;
import build.codemodel.jdk.statement.ExpressionStatement;
import build.codemodel.jdk.statement.For;
import build.codemodel.jdk.statement.Labeled;
import build.codemodel.jdk.statement.LocalTypeDeclaration;
import build.codemodel.jdk.statement.LocalVariableDeclaration;
import build.codemodel.jdk.statement.SwitchCase;
import build.codemodel.jdk.statement.SwitchStatement;
import build.codemodel.jdk.statement.Synchronized;
import build.codemodel.jdk.statement.Throw;
import build.codemodel.jdk.statement.Try;
import build.codemodel.jdk.statement.Yield;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.PatternTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.SimpleTreeVisitor;

import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Modifier;

/**
 * Converts {@link StatementTree} nodes from the javac tree API to model {@link Statement} nodes.
 *
 * @author reed.vonredwitz
 * @since Mar-2026
 */
public class JdkStatementConverter
    extends SimpleTreeVisitor<Statement, Void> {

    private final CodeModel codeModel;
    private final JdkExpressionConverter exprConverter;

    /**
     * Creates a {@link JdkStatementConverter}.
     *
     * @param codeModel     the {@link CodeModel} used to construct statement nodes
     * @param exprConverter the {@link JdkExpressionConverter} used to convert sub-expressions
     */
    public JdkStatementConverter(final CodeModel codeModel,
                                 final JdkExpressionConverter exprConverter) {
        this.codeModel = codeModel;
        this.exprConverter = exprConverter;
    }

    /**
     * Converts the given {@link StatementTree} to a model {@link Statement}.
     * Returns an empty {@link Block} for a {@code null} tree.
     */
    public Statement convert(final StatementTree tree) {
        if (tree == null) {
            return Block.empty(codeModel);
        }
        final var previousPath = exprConverter.descend(tree);
        try {
            return tree.accept(this, null);
        } finally {
            exprConverter.ascend(previousPath);
        }
    }

    /**
     * Converts a {@link BlockTree} to a {@link Block}.
     */
    public Block convertBlock(final BlockTree tree) {
        if (tree == null) {
            return Block.empty(codeModel);
        }
        return convertStatements(tree.getStatements());
    }

    public Block convertStatements(final List<? extends StatementTree> statementTrees) {
        if (statementTrees == null || statementTrees.isEmpty()) {
            return Block.empty(codeModel);
        }
        final var stmts = statementTrees.stream().map(this::convert).toList();
        return stmts.isEmpty() ? Block.empty(codeModel) : Block.of(stmts.toArray(Statement[]::new));
    }

    @Override
    public Statement visitBlock(final BlockTree t, final Void v) {
        return convertBlock(t);
    }

    @Override
    public Statement visitIf(final IfTree t, final Void v) {
        return If.of(
            exprConverter.convert(t.getCondition()),
            convert(t.getThenStatement()),
            Optional.ofNullable(t.getElseStatement()).map(this::convert));
    }

    @Override
    public Statement visitWhileLoop(final WhileLoopTree t, final Void v) {
        return While.of(exprConverter.convert(t.getCondition()), convert(t.getStatement()));
    }

    @Override
    public Statement visitDoWhileLoop(final DoWhileLoopTree t, final Void v) {
        return DoWhile.of(convert(t.getStatement()), exprConverter.convert(t.getCondition()));
    }

    @Override
    public Statement visitReturn(final ReturnTree t, final Void v) {
        return t.getExpression() == null
            ? Return.of(codeModel)
            : Return.of(exprConverter.convert(t.getExpression()));
    }

    @Override
    public Statement visitThrow(final ThrowTree t, final Void v) {
        return Throw.of(exprConverter.convert(t.getExpression()));
    }

    @Override
    public Statement visitYield(final YieldTree t, final Void v) {
        return Yield.of(exprConverter.convert(t.getValue()));
    }

    @Override
    public Statement visitExpressionStatement(final ExpressionStatementTree t, final Void v) {
        return ExpressionStatement.of(exprConverter.convert(t.getExpression()));
    }

    @Override
    public Statement visitVariable(final VariableTree t, final Void v) {
        final boolean isFinal = t.getModifiers() != null
            && t.getModifiers().getFlags().contains(Modifier.FINAL);
        final TypeUsage type = exprConverter.resolveTypeUsage(t.getType());
        exprConverter.addSourceLocation(t.getType()).ifPresent(type::addTrait);
        final var decl = LocalVariableDeclaration.of(codeModel,
            isFinal,
            type,
            t.getName().toString(),
            Optional.ofNullable(t.getInitializer()).map(exprConverter::convert));
        exprConverter.addSourceLocation(t).ifPresent(decl::addTrait);
        exprConverter.convertAnnotations(t).forEach(decl::addTrait);
        exprConverter.registerLocalVariableDeclaration(t, decl);
        return decl;
    }

    @Override
    public Statement visitForLoop(final ForLoopTree t, final Void v) {
        final var inits = t.getInitializer().stream()
            .map(this::convert)
            .toList();
        final var updates = t.getUpdate().stream()
            .map(s -> exprConverter.convert(((ExpressionStatementTree) s).getExpression()))
            .toList();
        return For.of(codeModel,
            inits.stream(),
            Optional.ofNullable(t.getCondition()).map(exprConverter::convert),
            updates.stream(),
            convert(t.getStatement()));
    }

    @Override
    public Statement visitEnhancedForLoop(final EnhancedForLoopTree t, final Void v) {
        final boolean isFinal = t.getVariable().getModifiers() != null
            && t.getVariable().getModifiers().getFlags().contains(Modifier.FINAL);
        final TypeUsage type = exprConverter.resolveTypeUsage(t.getVariable().getType());
        exprConverter.addSourceLocation(t.getVariable().getType()).ifPresent(type::addTrait);
        final var enhancedFor = EnhancedFor.ofPending(codeModel,
            isFinal,
            type,
            t.getVariable().getName().toString(),
            exprConverter.convert(t.getExpression()));
        exprConverter.addSourceLocation(t.getVariable()).ifPresent(enhancedFor::addTrait);
        exprConverter.convertAnnotations(t.getVariable()).forEach(enhancedFor::addTrait);
        exprConverter.registerEnhancedForVariable(t.getVariable(), enhancedFor);
        enhancedFor.completeBody(convert(t.getStatement()));
        return enhancedFor;
    }

    @Override
    public Statement visitTry(final TryTree t, final Void v) {
        final var resources = t.getResources().stream()
            .map(r -> r instanceof StatementTree st
                ? (Statement) convert(st)
                : ExpressionStatement.of(exprConverter.convert((ExpressionTree) r)))
            .toList();
        final var catches = t.getCatches().stream()
            .map(this::convertCatch)
            .toList();
        return Try.of(codeModel,
            resources.stream(),
            convertBlock(t.getBlock()),
            catches.stream(),
            Optional.ofNullable(t.getFinallyBlock()).map(this::convertBlock));
    }

    private CatchClause convertCatch(final CatchTree c) {
        final var typeTree = c.getParameter().getType();
        final List<TypeUsage> types;
        if (typeTree instanceof UnionTypeTree unionType) {
            types = unionType.getTypeAlternatives().stream()
                .map(alt -> {
                    final var type = exprConverter.resolveTypeUsage(alt);
                    exprConverter.addSourceLocation(alt).ifPresent(type::addTrait);
                    return type;
                })
                .toList();
        } else {
            final var type = exprConverter.resolveTypeUsage(typeTree);
            exprConverter.addSourceLocation(typeTree).ifPresent(type::addTrait);
            types = List.of(type);
        }
        final boolean isFinal = c.getParameter().getModifiers() != null
            && c.getParameter().getModifiers().getFlags().contains(Modifier.FINAL);
        final var catchClause = CatchClause.ofPending(codeModel,
            isFinal,
            types,
            c.getParameter().getName().toString());
        exprConverter.addSourceLocation(c.getParameter()).ifPresent(catchClause::addTrait);
        exprConverter.convertAnnotations(c.getParameter()).forEach(catchClause::addTrait);
        exprConverter.registerCatchParameter(c.getParameter(), catchClause);
        catchClause.completeBody(convertBlock(c.getBlock()));
        return catchClause;
    }

    @Override
    public Statement visitBreak(final BreakTree t, final Void v) {
        return Break.of(codeModel, Optional.ofNullable(t.getLabel()).map(Object::toString));
    }

    @Override
    public Statement visitContinue(final ContinueTree t, final Void v) {
        return Continue.of(codeModel, Optional.ofNullable(t.getLabel()).map(Object::toString));
    }

    @Override
    public Statement visitSynchronized(final SynchronizedTree t, final Void v) {
        return Synchronized.of(exprConverter.convert(t.getExpression()), convertBlock(t.getBlock()));
    }

    @Override
    public Statement visitAssert(final AssertTree t, final Void v) {
        return Assert.of(
            exprConverter.convert(t.getCondition()),
            Optional.ofNullable(t.getDetail()).map(exprConverter::convert));
    }

    @Override
    public Statement visitLabeledStatement(final LabeledStatementTree t, final Void v) {
        return Labeled.of(codeModel, t.getLabel().toString(), convert(t.getStatement()));
    }

    @Override
    public Statement visitSwitch(final SwitchTree t, final Void v) {
        final var selector = exprConverter.convert(t.getExpression());
        final var cases = t.getCases().stream().map(c -> convertCase(c, selector)).toList();
        return SwitchStatement.of(selector, cases.stream());
    }

    /**
     * Converts a {@link CaseTree}. {@code selector} is the already-converted switch selector
     * expression, needed to build an {@link InstanceOf} label for a type-pattern case.
     */
    SwitchCase convertCase(final CaseTree c, final Expression selector) {
        final var labels = c.getLabels().stream()
            .<Expression>mapMulti((label, consumer) -> {
                if (label instanceof ConstantCaseLabelTree constant) {
                    consumer.accept(exprConverter.convert(constant.getConstantExpression()));
                } else if (label instanceof PatternCaseLabelTree pattern) {
                    convertPatternLabel(pattern.getPattern(), selector).ifPresent(consumer::accept);
                }
                // DefaultCaseLabelTree contributes no label expression; an empty labels() means default.
            })
            .toList();
        final var guard = Optional.ofNullable(c.getGuard()).map(exprConverter::convert);
        final List<Statement> stmts;
        if (c.getStatements() != null && !c.getStatements().isEmpty()) {
            stmts = c.getStatements().stream().map(this::convert).toList();
        } else if (c.getBody() instanceof ExpressionTree et) {
            stmts = List.of(ExpressionStatement.of(exprConverter.convert(et)));
        } else if (c.getBody() instanceof BlockTree bt) {
            stmts = bt.getStatements().stream().map(this::convert).toList();
        } else if (c.getBody() instanceof StatementTree st) {
            stmts = List.of(convert(st));
        } else {
            stmts = List.of();
        }
        return SwitchCase.of(codeModel, labels.stream(), stmts.stream(), guard);
    }

    /**
     * Converts a type-pattern or deconstruction-pattern case label to an {@link InstanceOf} label
     * testing the switch {@code selector}.
     */
    private Optional<Expression> convertPatternLabel(final PatternTree pattern,
                                                     final Expression selector) {
        if (pattern instanceof BindingPatternTree binding) {
            final var type = exprConverter.resolveTypeUsage(binding.getVariable().getType());
            exprConverter.addSourceLocation(binding.getVariable().getType()).ifPresent(type::addTrait);
            final var instanceOf = InstanceOf.of(selector, type, Optional.of(binding.getVariable().getName().toString()));
            exprConverter.addSourceLocation(binding.getVariable()).ifPresent(instanceOf::addTrait);
            exprConverter.registerPatternBinding(binding.getVariable(), instanceOf);
            return Optional.of(instanceOf);
        }
        if (pattern instanceof DeconstructionPatternTree deconstruction) {
            final var type = exprConverter.resolveTypeUsage(deconstruction.getDeconstructor());
            exprConverter.addSourceLocation(deconstruction.getDeconstructor()).ifPresent(type::addTrait);
            final var components = deconstruction.getNestedPatterns().stream()
                .map(exprConverter::convertPattern)
                .toList();
            final var instanceOf = InstanceOf.ofDeconstruction(selector, type, components);
            deconstruction.getNestedPatterns()
                .forEach(nested -> exprConverter.registerNestedPatternBindings(nested, instanceOf));
            return Optional.of(instanceOf);
        }
        return Optional.empty();
    }

    @Override
    public Statement visitEmptyStatement(final EmptyStatementTree t, final Void v) {
        return Block.empty(codeModel);
    }

    /**
     * Converts a local class/interface/enum/record declaration statement (e.g.
     * {@code class Foo { ... }} written inside a method body) to a
     * {@link LocalTypeDeclaration} referencing the {@link TypeName} the declared type was already
     * registered under, by {@link JdkInitializer}'s {@code visitClass}, before body conversion runs.
     */
    @Override
    public Statement visitClass(final ClassTree t, final Void v) {
        return exprConverter.resolveLocalTypeName(t)
            .<Statement>map(typeName -> {
                final var decl = LocalTypeDeclaration.of(codeModel, typeName);
                exprConverter.addSourceLocation(t).ifPresent(decl::addTrait);
                return decl;
            })
            .orElseGet(() -> Block.empty(codeModel));
    }

    @Override
    protected Statement defaultAction(final Tree node, final Void v) {
        return Block.empty(codeModel);
    }

}
