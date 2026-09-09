package build.codemodel.foundation.usage;

/*-
 * #%L
 * Code Model Foundation
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

import build.base.foundation.Lazy;
import build.codemodel.foundation.ConceptualCodeModel;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.naming.TypeName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TypeVariableUsage}.
 *
 * @author reed.vonredwitz
 * @since May-2026
 */
class TypeVariableUsageTests {

    private final NonCachingNameProvider naming = new NonCachingNameProvider();
    private final ConceptualCodeModel codeModel = new ConceptualCodeModel(naming);

    @Test
    void toStringShouldUseExtendsForUpperBound() {
        // T extends Number: upper bound uses "extends"
        final var tName = naming.getEmptyModuleTypeName("T");
        final var numberName = naming.getEmptyModuleTypeName("java.lang.Number");
        final var numberUsage = SpecificTypeUsage.of(codeModel, numberName);
        final var usage = TypeVariableUsage.of(codeModel, tName, Optional.empty(),
            Optional.of(Lazy.of(numberUsage)));

        assertThat(usage.toString()).isEqualTo("T extends java.lang.Number");
    }

    @Test
    void toStringShouldUseSuperForLowerBound() {
        // T super Integer: lower bound uses "super"
        final var tName = naming.getEmptyModuleTypeName("T");
        final var integerName = naming.getEmptyModuleTypeName("java.lang.Integer");
        final var integerUsage = SpecificTypeUsage.of(codeModel, integerName);
        final var usage = TypeVariableUsage.of(codeModel, tName, Optional.of(Lazy.of(integerUsage)),
            Optional.empty());

        assertThat(usage.toString()).isEqualTo("T super java.lang.Integer");
    }

    @Test
    void toStringShouldShowJustNameWhenUnbounded() {
        final var tName = naming.getEmptyModuleTypeName("T");
        final var usage = TypeVariableUsage.of(codeModel, tName, Optional.empty(), Optional.empty());

        assertThat(usage.toString()).isEqualTo("T");
    }

    @Test
    void renderUsesTheSimpleNameEvenWhenTheTypeNameIsEnclosingScoped() {
        // In the model a type variable's TypeName is scoped under its declaring type (e.g. "Holder$T")
        // so a T declared on Holder can't be confused with a T declared elsewhere. That scoping must
        // not leak into the rendered form: a declaration reads as just "T".
        final var scopedT = naming.getTypeNameFromBinary(Optional.empty(), "Holder$T");
        assertThat(scopedT.toString()).isEqualTo("Holder$T");
        assertThat(scopedT.name().toString()).isEqualTo("T");

        final var usage = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.empty());

        assertThat(usage.toString()).isEqualTo("T");
        assertThat(usage.canonicalName()).isEqualTo("T");
    }

    @Test
    void renderKeepsTheBoundButStillRendersAnEnclosingScopedVariableAsItsSimpleName() {
        final var scopedT = naming.getTypeNameFromBinary(Optional.empty(), "Holder$T");
        final var numberUsage = SpecificTypeUsage.of(codeModel, naming.getEmptyModuleTypeName("java.lang.Number"));
        final var usage = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.of(Lazy.of(numberUsage)));

        assertThat(usage.toString()).isEqualTo("T extends java.lang.Number");
        assertThat(usage.canonicalName()).isEqualTo("T extends java.lang.Number");
    }

    @Test
    void renderBoundRendersANestedSelfReferenceAsItsSimpleName() {
        // T extends Comparable<T>, with T enclosing-scoped in the model. The inner T is reached
        // through renderBound; it must read as "T", not the scoped "Holder$T"/"Holder.T".
        final var scopedT = naming.getTypeNameFromBinary(Optional.empty(), "Holder$T");
        final var comparableName = naming.getEmptyModuleTypeName("java.lang.Comparable");

        final var innerT = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.empty());
        final var bound = GenericTypeUsage.of(codeModel, comparableName, innerT);
        final var tWithBound = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.of(Lazy.of(bound)));

        assertThat(tWithBound.toString()).isEqualTo("T extends java.lang.Comparable<T>");
        assertThat(tWithBound.canonicalName()).isEqualTo("T extends java.lang.Comparable<T>");
    }

    @Test
    void renderBoundThreadsTheSimpleNameRuleThroughAnIntersectionBound() {
        // T extends Number & Comparable<T> - the self-reference is nested one level deeper, inside an
        // IntersectionTypeUsage. renderBound must still reduce it to "T".
        final var scopedT = naming.getTypeNameFromBinary(Optional.empty(), "Holder$T");
        final var numberUsage = SpecificTypeUsage.of(codeModel, naming.getEmptyModuleTypeName("java.lang.Number"));
        final var comparableOfT = GenericTypeUsage.of(codeModel, naming.getEmptyModuleTypeName("java.lang.Comparable"),
            TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.empty()));
        final var intersection = IntersectionTypeUsage.of(codeModel, numberUsage, comparableOfT);
        final var tWithBound = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(),
            Optional.of(Lazy.of(intersection)));

        assertThat(tWithBound.toString())
            .startsWith("T extends")
            .contains("java.lang.Number & java.lang.Comparable<T>")
            .doesNotContain("Holder");
        assertThat(tWithBound.canonicalName())
            .contains("java.lang.Number & java.lang.Comparable<T>")
            .doesNotContain("Holder");
    }

    @Test
    void renderDropsTheDeclaringModuleAndNamespaceFromAModuleScopedTypeVariable() {
        // The issue that motivated the enclosing-scoping work involved type variables on types in a
        // named module: their TypeName carries module + namespace + enclosing type (e.g.
        // "com.example/com.example.Holder$T"). None of that may leak into the rendered form - not even
        // toString(), which keeps modules for every other kind of usage.
        final var scopedT = moduleScopedTypeVariableName("com.example", "Holder", "T");
        assertThat(scopedT.toString()).isEqualTo("com.example/com.example.Holder$T");
        assertThat(scopedT.moduleName()).isPresent();

        final var usage = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.empty());

        assertThat(usage.toString()).isEqualTo("T");
        assertThat(usage.canonicalName()).isEqualTo("T");
    }

    @Test
    void renderBoundRendersAModuleScopedSelfReferenceAsItsSimpleName() {
        // T extends Holder<T>, Holder being a generic type in a named module and T scoped under it.
        final var scopedT = moduleScopedTypeVariableName("com.example", "Holder", "T");
        final var holderName = naming.getTypeName(naming.getModuleName("com.example"), "com.example.Holder");

        final var innerT = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.empty());
        final var bound = GenericTypeUsage.of(codeModel, holderName, innerT);
        final var tWithBound = TypeVariableUsage.of(codeModel, scopedT, Optional.empty(), Optional.of(Lazy.of(bound)));

        // toString() keeps the module on the raw type Holder, but the nested T is still just "T"
        assertThat(tWithBound.toString()).isEqualTo("T extends com.example/com.example.Holder<T>");
        assertThat(tWithBound.canonicalName()).isEqualTo("T extends com.example.Holder<T>");
    }

    /**
     * Builds the {@link TypeName} for a type variable declared on a type in a named module, exactly as
     * {@code TypeMirrorResolver}/{@code JDKCodeModel} do: module +
     * namespace + enclosing type name, with the variable's own simple name as the irreducible name.
     */
    private TypeName moduleScopedTypeVariableName(final String module,
                                                 final String enclosingSimpleName,
                                                 final String variableName) {
        final var moduleName = naming.getModuleName(module);
        final var namespace = naming.getNamespace(module);
        final var enclosing = naming.getTypeName(moduleName, namespace, Optional.empty(),
            naming.getIrreducibleName(enclosingSimpleName));
        return naming.getTypeName(moduleName, namespace, Optional.of(enclosing),
            naming.getIrreducibleName(variableName));
    }

    @Test
    void equalsShouldNotStackOverflowOnMutuallyRecursiveBounds() {
        // Comparable<T extends Comparable<T>> — upper bound references a GenericTypeUsage
        // whose parameter is the same TypeVariableUsage, creating a cycle in equals().
        final var tName = naming.getEmptyModuleTypeName("T");
        final var comparableName = naming.getEmptyModuleTypeName("java.lang.Comparable");

        final var tUsage = TypeVariableUsage.of(codeModel, tName, Optional.empty(), Optional.empty());
        final var genericBound = GenericTypeUsage.of(codeModel, comparableName, tUsage);
        final var tWithBound = TypeVariableUsage.of(codeModel, tName, Optional.empty(),
            Optional.of(Lazy.of(genericBound)));

        assertThat(tWithBound.equals(tWithBound)).isTrue();

        final var tWithBound2 = TypeVariableUsage.of(codeModel, tName, Optional.empty(),
            Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, comparableName, tUsage))));

        assertThat(tWithBound.equals(tWithBound2)).isTrue();
    }

    @Test
    void equalsDistinguishesBoundsThatReferenceSameNamedVariablesFromDifferentDeclaringTypes() {
        // Two X variables, each `X extends Comparable<T>`, but one bound's T is declared on Foo and the
        // other's on Bar. They render identically for display ("X extends java.lang.Comparable<T>"),
        // but equals must still tell them apart via the enclosing-type-scoped TypeName.
        final var xName = naming.getTypeNameFromBinary(Optional.empty(), "Holder$X");
        final var comparableName = naming.getEmptyModuleTypeName("java.lang.Comparable");

        final var fooT = naming.getTypeNameFromBinary(Optional.empty(), "Foo$T");
        final var barT = naming.getTypeNameFromBinary(Optional.empty(), "Bar$T");

        final var xBoundedByFooT = TypeVariableUsage.of(codeModel, xName, Optional.empty(),
            Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, comparableName,
                TypeVariableUsage.of(codeModel, fooT, Optional.empty(), Optional.empty())))));
        final var xBoundedByBarT = TypeVariableUsage.of(codeModel, xName, Optional.empty(),
            Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, comparableName,
                TypeVariableUsage.of(codeModel, barT, Optional.empty(), Optional.empty())))));
        final var xBoundedByFooTAgain = TypeVariableUsage.of(codeModel, xName, Optional.empty(),
            Optional.of(Lazy.of(GenericTypeUsage.of(codeModel, comparableName,
                TypeVariableUsage.of(codeModel, fooT, Optional.empty(), Optional.empty())))));

        assertThat(xBoundedByFooT.toString()).isEqualTo(xBoundedByBarT.toString());
        assertThat(xBoundedByFooT).isNotEqualTo(xBoundedByBarT);
        assertThat(xBoundedByFooT).isEqualTo(xBoundedByFooTAgain);
    }
}
