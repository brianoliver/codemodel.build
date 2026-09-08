package build.codemodel.jdk.descriptor;

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

import build.codemodel.expression.NumericLiteral;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.imperative.Block;
import build.codemodel.jdk.JDKCodeModel;
import build.codemodel.jdk.expression.ClassLiteral;
import build.codemodel.jdk.statement.ExpressionStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that descriptor and expression classes holding {@link build.base.mereology.Composite}
 * sub-objects correctly expose those children via {@link build.base.mereology.Composite#parts()}.
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

    // -------------------------------------------------------------------------
    // FieldInitializerDescriptor
    // -------------------------------------------------------------------------

    @Test
    void fieldInitializerDescriptorPartsContainsExpression() {
        final var expr = NumericLiteral.of(codeModel, 42);
        final var descriptor = new FieldInitializerDescriptor(expr);
        assertThat(descriptor.parts().toList()).containsExactly(expr);
    }

    // -------------------------------------------------------------------------
    // InitializerBlockDescriptor
    // -------------------------------------------------------------------------

    @Test
    void initializerBlockDescriptorPartsContainsBlock() {
        final var body = Block.of(ExpressionStatement.of(NumericLiteral.of(codeModel, 1)));
        final var descriptor = new InitializerBlockDescriptor(codeModel, true, body);
        assertThat(descriptor.parts().toList()).containsExactly(body);
    }

    // -------------------------------------------------------------------------
    // RecordComponentDescriptor
    // -------------------------------------------------------------------------

    @Test
    void recordComponentDescriptorPartsContainsType() {
        final var type = codeModel.getTypeUsage(String.class);
        final var descriptor = RecordComponentDescriptor.of(codeModel, IrreducibleName.of("value"), type);
        assertThat(descriptor.parts().toList()).containsExactly(type);
    }

    // -------------------------------------------------------------------------
    // UsesDescriptor
    // -------------------------------------------------------------------------

    @Test
    void usesDescriptorPartsContainsServiceType() {
        final var type = codeModel.getTypeUsage(Runnable.class);
        final var descriptor = UsesDescriptor.of(codeModel, type);
        assertThat(descriptor.parts().toList()).containsExactly(type);
    }

    // -------------------------------------------------------------------------
    // ProvidesDescriptor
    // -------------------------------------------------------------------------

    @Test
    void providesDescriptorPartsContainsServiceTypeAndImplementations() {
        final var serviceType = codeModel.getTypeUsage(Runnable.class);
        final var implType = codeModel.getTypeUsage(Thread.class);
        final var descriptor = ProvidesDescriptor.of(codeModel, serviceType, Stream.of(implType));
        final var parts = descriptor.parts().toList();
        assertThat(parts).contains(serviceType);
        assertThat(parts).contains(implType);
    }

    // -------------------------------------------------------------------------
    // ClassLiteral
    // -------------------------------------------------------------------------

    @Test
    void classLiteralPartsContainsReferencedType() {
        final var type = codeModel.getTypeUsage(String.class);
        final var literal = ClassLiteral.of(codeModel, type);
        assertThat(literal.parts().toList()).containsExactly(type);
    }

}
