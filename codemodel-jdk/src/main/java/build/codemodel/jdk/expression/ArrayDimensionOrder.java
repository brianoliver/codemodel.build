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

import build.codemodel.foundation.descriptor.Singular;
import build.codemodel.foundation.descriptor.Trait;

/**
 * A {@link Trait} on an {@link build.codemodel.foundation.usage.AnnotationTypeUsage} that a
 * {@link NewArray} node carries for a type-use annotation written on one of its dimension brackets,
 * recording which bracket: {@code 0} for the first {@code []}, {@code 1} for the second, and so on,
 * left-to-right as written in source. For example, in {@code new int[3] @Foo [4]} the {@code @Foo}
 * usage on the {@code NewArray} carries {@code ArrayDimensionOrder(1)}.
 *
 * <p>Trait storage does not preserve insertion order, so a consumer that needs the annotations in
 * bracket order (round-trip rendering, an LSP outline) must sort on this trait. Base-type
 * annotations ({@code new @Foo int[3]}) sit on {@link NewArray#elementType()} and carry no
 * {@code ArrayDimensionOrder}.
 *
 * @param dimension the zero-based dimension bracket the annotation applies to
 * @author reed.vonredwitz
 * @since Sep-2026
 */
@Singular
public record ArrayDimensionOrder(int dimension) implements Trait {
}
