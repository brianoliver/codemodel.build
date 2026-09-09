package build.codemodel.jdk.annotation.processor;

/*-
 * #%L
 * JDK Annotation Processor
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

import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.jdk.descriptor.EnumConstantDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link AnnotationProcessor} models user-written enum constants as
 * {@link EnumConstantDescriptor} traits. The member-processing switch in
 * {@code createTypeDescriptor} only handled {@code FIELD}, {@code CONSTRUCTOR}, and {@code
 * METHOD} element kinds; {@code ENUM_CONSTANT} fell through the no-op default, so a
 * {@code TypeDescriptor} for an enum built via the annotation-processing path had zero
 * {@link EnumConstantDescriptor} traits.
 */
class EnumConstantDiscoveryTests extends AnnotationProcessorTests {

    @Test
    void shouldCreateEnumConstantDescriptors() {
        final var source =
            """
                    import build.codemodel.jdk.annotation.discovery.Discoverable;
                
                    @Discoverable
                    public enum Color {
                        RED, GREEN, BLUE
                    }
                """;
        final var annotationProcessor = new AnnotationProcessor();
        compile(annotationProcessor, "Color", source);

        final var codeModel = annotationProcessor.getCodeModel().orElseThrow();
        final var naming = codeModel.getNameProvider();
        final var typeName = naming.getEmptyModuleTypeName("Color");
        final var typeDescriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        // Element encounter order for enum constants is not guaranteed to match source
        // declaration order (see AnnotationProcessorDeclarationOrderTests, which asserts the
        // analogous field/constructor/method orders only up to permutation) -- so this pairs
        // each name with its order (rather than checking names and orders as separate sets,
        // which would miss a mismatched pairing) and checks the pairs against the language's
        // fixed enum ordinal assignment, which is independent of Element encounter order.
        final var orderByName = typeDescriptor.traits(EnumConstantDescriptor.class)
            .collect(Collectors.toMap(constant -> constant.name().toString(), EnumConstantDescriptor::order));

        assertThat(orderByName).containsExactlyInAnyOrderEntriesOf(Map.of("RED", 0, "GREEN", 1, "BLUE", 2));

        // the implicit values()/valueOf(String) methods must still be modeled alongside the
        // constants (see ImplicitMethodDiscoveryTests) -- adding the ENUM_CONSTANT switch case
        // must not disturb the existing METHOD handling.
        assertThat(typeDescriptor.traits(MethodDescriptor.class)
            .map(m -> m.methodName().name().toString()))
            .contains("values", "valueOf");
    }

    @Test
    void shouldCaptureEnumConstantAnnotations() {
        final var source =
            """
                    import build.codemodel.jdk.annotation.discovery.Discoverable;

                    @Discoverable
                    public enum Color {
                        @Deprecated RED,
                        GREEN
                    }
                """;
        final var annotationProcessor = new AnnotationProcessor();
        compile(annotationProcessor, "Color", source);

        final var codeModel = annotationProcessor.getCodeModel().orElseThrow();
        final var typeName = codeModel.getNameProvider().getEmptyModuleTypeName("Color");
        final var typeDescriptor = codeModel.getTypeDescriptor(typeName).orElseThrow();

        final var constants = typeDescriptor.traits(EnumConstantDescriptor.class).toList();
        final var red = constants.stream()
            .filter(c -> c.name().toString().equals("RED"))
            .findFirst().orElseThrow();
        final var green = constants.stream()
            .filter(c -> c.name().toString().equals("GREEN"))
            .findFirst().orElseThrow();

        assertThat(red.traits(AnnotationTypeUsage.class)
            .map(a -> a.typeName().name().toString())
            .toList())
            .containsExactly("Deprecated");
        assertThat(green.traits(AnnotationTypeUsage.class).toList()).isEmpty();
    }
}
