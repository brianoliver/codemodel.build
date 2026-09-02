package build.codemodel.hierarchical;

import build.base.foundation.stream.Streams;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.NonCachingNameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.foundation.usage.SpecificTypeUsage;
import build.codemodel.hierarchical.descriptor.HierarchicalTypeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HierarchicalCodeModel}s.
 *
 * @author brian.oliver
 * @since Mar-2025
 */
class HierarchicalCodeModelTests {

    /**
     * Creates an empty {@link HierarchicalCodeModel}.
     *
     * @return an empty {@link HierarchicalCodeModel}
     */
    protected HierarchicalCodeModel createEmptyCodeModel() {
        final var nameProvider = new NonCachingNameProvider();
        return new AbstractHierarchicalCodeModel(nameProvider) {
        };
    }

    /**
     * Creates a new {@link HierarchicalCodeModel} with the following structure.
     * <p>
     * <pre>
     *              A                B       C
     *      +-------+--------+       |
     *      |       |        |       D
     *      E    +--+--+     F
     *      |    |     |
     *      G    H     I
     *           |     |
     *        +--+     K
     *        |  |     |
     *        J  |     +--+
     *           |     |  |
     *           +--+--+  M
     *              |     |
     *              L     O
     *              |
     *              N
     * </pre>
     *
     * @return a new {@link HierarchicalCodeModel}
     */
    protected HierarchicalCodeModel createStructuredCodeModel() {

        final var codeModel = createEmptyCodeModel();
        final var nameProvider = codeModel.getNameProvider();

        // establish the TypeNames and TypeUsages
        final var typeNamesLetters = "ABCDEFGHIJKLMNO";
        final var typeNames = new LinkedHashMap<String, TypeName>();
        final var typeUsages = new LinkedHashMap<String, NamedTypeUsage>();

        typeNamesLetters.chars()
            .mapToObj(c -> String.valueOf((char) c))
            .forEach(name -> {
                final var typeName = nameProvider.getTypeName(name);

                typeNames.put(name, typeName);
                typeUsages.put(name, SpecificTypeUsage.of(codeModel, typeName));
            });

        // establish the HierarchicalTypeDescriptors
        final var typeDescriptors = new LinkedHashMap<String, HierarchicalTypeDescriptor>();

        typeNames.forEach((string, typeName) -> {
            final var typeDescriptor = codeModel
                .createTypeDescriptor(typeName, (_, name) -> new ClassTypeDescriptor(codeModel, name));

            typeDescriptors.put(string, typeDescriptor);
        });

        // establish the structure of the Code Model
        typeDescriptors.get("D").addTrait(Extends.of(typeUsages.get("B")));
        typeDescriptors.get("E").addTrait(Extends.of(typeUsages.get("A")));
        typeDescriptors.get("F").addTrait(Extends.of(typeUsages.get("A")));
        typeDescriptors.get("G").addTrait(Extends.of(typeUsages.get("E")));
        typeDescriptors.get("H").addTrait(Extends.of(typeUsages.get("A")));
        typeDescriptors.get("I").addTrait(Extends.of(typeUsages.get("A")));
        typeDescriptors.get("J").addTrait(Extends.of(typeUsages.get("H")));
        typeDescriptors.get("K").addTrait(Extends.of(typeUsages.get("I")));
        typeDescriptors.get("L").addTrait(Extends.of(typeUsages.get("H")));
        typeDescriptors.get("L").addTrait(Extends.of(typeUsages.get("K")));
        typeDescriptors.get("M").addTrait(Extends.of(typeUsages.get("K")));
        typeDescriptors.get("N").addTrait(Extends.of(typeUsages.get("L")));
        typeDescriptors.get("O").addTrait(Extends.of(typeUsages.get("M")));

        return codeModel;
    }

    /**
     * Ensure an empty {@link HierarchicalCodeModel} can be created.
     */
    @Test
    void shouldCreateEmptyCodeModel() {
        final var codeModel = createEmptyCodeModel();

        // ensure there are no TypeDescriptors in the CodeModel
        assertThat(codeModel.typeDescriptors())
            .isEmpty();

        // ensure there are no Traits on the CodeModel
        assertThat(codeModel.hasTraits())
            .isFalse();
    }

    /**
     * Ensure a structured {@link HierarchicalCodeModel} can be created.
     */
    @Test
    void shouldCreateStructuredCodeModel() {
        final var codeModel = createStructuredCodeModel();

        // ensure there are TypeDescriptors for the expected Types
        assertThat(codeModel.typeDescriptors()
            .map(TypeDescriptor::typeName)
            .map(TypeName::name)
            .map(IrreducibleName::toString))
            .contains("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O");

        // ensure there are no Traits on the CodeModel
        assertThat(codeModel.hasTraits())
            .isFalse();

        // ensure the root TypeDescriptors
        assertThat(codeModel.roots()
            .map(HierarchicalTypeDescriptor::typeName)
            .map(TypeName::name)
            .map(IrreducibleName::toString))
            .contains("A", "B", "C");

        // ensure A has the expected structure
        assertHasChildren(codeModel, "A", "E", "H", "I", "F");
        assertHasDescendants(codeModel, "A", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O");
        assertHasNoParents(codeModel, "A");
        assertHasNoAncestors(codeModel, "A");
        assertLevel(codeModel, "A", 0);
        assertIsOnlyAssignableTo(codeModel, "A", "A");
        assertFormsDiamondPattern(codeModel, "A", false);

        // ensure B has the expected structure
        assertHasChildren(codeModel, "B", "D");
        assertHasDescendants(codeModel, "B", "D");
        assertHasNoParents(codeModel, "B");
        assertHasNoAncestors(codeModel, "B");
        assertLevel(codeModel, "B", 0);
        assertIsOnlyAssignableTo(codeModel, "B", "B");
        assertFormsDiamondPattern(codeModel, "B", false);

        // ensure C has the expected structure
        assertHasNoChildren(codeModel, "C");
        assertHasNoDescendants(codeModel, "C");
        assertHasNoParents(codeModel, "C");
        assertHasNoAncestors(codeModel, "C");
        assertLevel(codeModel, "C", 0);
        assertIsOnlyAssignableTo(codeModel, "C", "C");
        assertFormsDiamondPattern(codeModel, "C", false);

        // ensure D has the expected structure
        assertHasNoChildren(codeModel, "D");
        assertHasNoDescendants(codeModel, "D");
        assertHasParents(codeModel, "D", "B");
        assertHasAncestors(codeModel, "D", "B");
        assertLevel(codeModel, "D", 1);
        assertIsOnlyAssignableTo(codeModel, "D", "D", "B");
        assertFormsDiamondPattern(codeModel, "D", false);

        // ensure E has the expected structure
        assertHasChildren(codeModel, "E", "G");
        assertHasDescendants(codeModel, "E", "G");
        assertHasParents(codeModel, "E", "A");
        assertHasAncestors(codeModel, "E", "A");
        assertLevel(codeModel, "E", 1);
        assertIsOnlyAssignableTo(codeModel, "E", "E", "A");
        assertFormsDiamondPattern(codeModel, "E", false);

        // ensure F has the expected structure
        assertHasNoChildren(codeModel, "F");
        assertHasNoDescendants(codeModel, "F");
        assertHasParents(codeModel, "F", "A");
        assertHasAncestors(codeModel, "F", "A");
        assertLevel(codeModel, "F", 1);
        assertIsOnlyAssignableTo(codeModel, "F", "F", "A");
        assertFormsDiamondPattern(codeModel, "F", false);

        // ensure G has the expected structure
        assertHasNoChildren(codeModel, "G");
        assertHasNoDescendants(codeModel, "G");
        assertHasParents(codeModel, "G", "E");
        assertHasAncestors(codeModel, "G", "E", "A");
        assertLevel(codeModel, "G", 2);
        assertIsOnlyAssignableTo(codeModel, "G", "G", "E", "A");
        assertFormsDiamondPattern(codeModel, "G", false);

        // ensure H has the expected structure
        assertHasChildren(codeModel, "H", "J", "L");
        assertHasDescendants(codeModel, "H", "J", "L", "N");
        assertHasParents(codeModel, "H", "A");
        assertHasAncestors(codeModel, "H", "A");
        assertLevel(codeModel, "H", 1);
        assertIsOnlyAssignableTo(codeModel, "H", "H", "A");
        assertFormsDiamondPattern(codeModel, "H", false);

        // ensure I has the expected structure
        assertHasChildren(codeModel, "I", "K");
        assertHasDescendants(codeModel, "I", "K", "L", "N", "M", "O");
        assertHasParents(codeModel, "I", "A");
        assertHasAncestors(codeModel, "I", "A");
        assertLevel(codeModel, "I", 1);
        assertIsOnlyAssignableTo(codeModel, "I", "I", "A");
        assertFormsDiamondPattern(codeModel, "I", false);

        // ensure J has the expected structure
        assertHasNoChildren(codeModel, "J");
        assertHasNoDescendants(codeModel, "J");
        assertHasParents(codeModel, "J", "H");
        assertHasAncestors(codeModel, "J", "H", "A");
        assertLevel(codeModel, "J", 2);
        assertIsOnlyAssignableTo(codeModel, "J", "J", "H", "A");
        assertFormsDiamondPattern(codeModel, "J", false);

        // ensure K has the expected structure
        assertHasChildren(codeModel, "K", "L", "M");
        assertHasDescendants(codeModel, "K", "L", "N", "M", "O");
        assertHasParents(codeModel, "K", "I");
        assertHasAncestors(codeModel, "K", "I", "A");
        assertLevel(codeModel, "K", 2);
        assertIsOnlyAssignableTo(codeModel, "K", "K", "I", "A");
        assertFormsDiamondPattern(codeModel, "K", false);

        // ensure L has the expected structure
        assertHasChildren(codeModel, "L", "N");
        assertHasDescendants(codeModel, "L", "N");
        assertHasParents(codeModel, "L", "H", "K");
        assertHasAncestors(codeModel, "L", "H", "A", "K", "I");
        assertLevel(codeModel, "L", 3);
        assertIsOnlyAssignableTo(codeModel, "L", "L", "H", "A", "K", "I");
        assertFormsDiamondPattern(codeModel, "L", true);

        // ensure M has the expected structure
        assertHasChildren(codeModel, "M", "O");
        assertHasDescendants(codeModel, "M", "O");
        assertHasParents(codeModel, "M", "K");
        assertHasAncestors(codeModel, "M", "K", "I", "A");
        assertLevel(codeModel, "M", 3);
        assertIsOnlyAssignableTo(codeModel, "M", "M", "K", "I", "A");
        assertFormsDiamondPattern(codeModel, "M", false);

        // ensure N has the expected structure
        assertHasNoChildren(codeModel, "N");
        assertHasNoDescendants(codeModel, "N");
        assertHasParents(codeModel, "N", "L");
        assertHasAncestors(codeModel, "N", "L", "H", "A", "K", "I");
        assertLevel(codeModel, "N", 4);
        assertIsOnlyAssignableTo(codeModel, "N", "N", "L", "H", "A", "K", "I");
        assertFormsDiamondPattern(codeModel, "N", false);

        // ensure O has the expected structure
        assertHasNoChildren(codeModel, "O");
        assertHasNoDescendants(codeModel, "O");
        assertHasParents(codeModel, "O", "M");
        assertHasAncestors(codeModel, "O", "M", "K", "I", "A");
        assertLevel(codeModel, "O", 4);
        assertIsOnlyAssignableTo(codeModel, "O", "O", "M", "K", "I", "A");
        assertFormsDiamondPattern(codeModel, "O", false);
    }

    /**
     * Ensure an {@link Extends} {@link Trait} can be replaced and the {@link HierarchicalCodeModel} correctly
     * adjusts the internal directed-acyclic-graph.
     */
    @Test
    void shouldReplaceExtends() {
        final var codeModel = createStructuredCodeModel();
        final var nameProvider = codeModel.getNameProvider();

        final var dTypeDescriptor = codeModel
            .getTypeDescriptor(nameProvider.getTypeName("D"), HierarchicalTypeDescriptor.class)
            .orElseThrow();

        final var replaced = dTypeDescriptor
            .computeIfPresent(Extends.class, (_, _) ->
                Extends.of(SpecificTypeUsage.of(codeModel, nameProvider.getTypeName("F"))));

        assertThat(replaced)
            .isPresent();

        assertThat(replaced.get()
            .parentTypeName()
            .name()
            .toString())
            .isEqualTo("F");

        // ensure A has the expected structure
        assertHasChildren(codeModel, "A", "E", "H", "I", "F");
        assertHasDescendants(codeModel, "A", "E", "F", "D", "G", "H", "I", "J", "K", "L", "M", "N", "O");
        assertHasNoParents(codeModel, "A");
        assertHasNoAncestors(codeModel, "A");
        assertLevel(codeModel, "A", 0);

        // ensure D has the expected structure
        assertHasNoChildren(codeModel, "D");
        assertHasNoDescendants(codeModel, "D");
        assertHasParents(codeModel, "D", "F");
        assertHasAncestors(codeModel, "D", "F", "A");
        assertLevel(codeModel, "D", 2);

        // ensure F has the expected structure
        assertHasChildren(codeModel, "D");
        assertHasDescendants(codeModel, "D");
        assertHasParents(codeModel, "F", "A");
        assertHasAncestors(codeModel, "F", "A");
        assertLevel(codeModel, "F", 1);
    }

    /**
     * Ensure we can determine the assignable types from a {@link HierarchicalCodeModel}.
     */
    @Test
    void shouldDetermineAssignableTypes() {
        final var codeModel = createStructuredCodeModel();

        assertThat(getAssignableTypeNames(codeModel, "A"))
            .contains("A");

        assertThat(getAssignableTypeNames(codeModel, "B"))
            .contains("B");

        assertThat(getAssignableTypeNames(codeModel, "C"))
            .contains("C");

        assertThat(getAssignableTypeNames(codeModel, "B", "D"))
            .contains("D");

        assertThat(getAssignableTypeNames(codeModel, "A", "E", "G"))
            .contains("G");

        assertThat(getAssignableTypeNames(codeModel, "H", "I"))
            .contains("L", "N");

        assertThat(getAssignableTypeNames(codeModel, "A", "E", "F"))
            .isEmpty();

        assertThat(getAssignableTypeNames(codeModel, "A", "H", "I"))
            .contains("L", "N");

        assertThat(getAssignableTypeNames(codeModel, "H", "K"))
            .contains("L", "N");
    }

    /**
     * Obtains the {@link HierarchicalTypeDescriptor}s for the specified encoded {@link TypeName}s from
     * the {@link HierarchicalCodeModel}.
     *
     * @param codeModel        the {@link HierarchicalCodeModel}
     * @param encodedTypeNames the encoded {@link TypeName}s
     * @return a {@link Stream}
     */
    private Stream<HierarchicalTypeDescriptor> getTypeDescriptors(final HierarchicalCodeModel codeModel,
                                                                  final String... encodedTypeNames) {

        final var nameProvider = codeModel.getNameProvider();

        return Streams.of(encodedTypeNames)
            .map(nameProvider::getTypeName)
            .map(typeName -> codeModel.getTypeDescriptor(typeName, HierarchicalTypeDescriptor.class)
                .orElseThrow());
    }

    /**
     * Determines the assignable encoded {@link TypeName}s of the specified encoded {@link TypeName}s from a
     * {@link HierarchicalCodeModel}.
     *
     * @param codeModel        the {@link HierarchicalCodeModel}
     * @param encodedTypeNames the encoded {@link TypeName}s
     * @return the {@link Stream} of assignable encoded {@link TypeName}s
     * @see HierarchicalCodeModel#getAssignableTypeDescriptors(Stream)
     */
    private Stream<String> getAssignableTypeNames(final HierarchicalCodeModel codeModel,
                                                  final String... encodedTypeNames) {

        return HierarchicalCodeModel.getAssignableTypeDescriptors(getTypeDescriptors(codeModel, encodedTypeNames))
            .map(TypeDescriptor::typeName)
            .map(TypeName::name)
            .map(IrreducibleName::toString);
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has no <i>children</i>.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     */
    private void assertHasNoChildren(final HierarchicalCodeModel codeModel,
                                     final String encodedTypeName) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .children())
            .isEmpty();
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has <i>children</i>
     * with the provided encoded {@link TypeName}s.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     * @param childTypeNames  the encoded <i>child</i> {@link TypeName}s
     */
    private void assertHasChildren(final HierarchicalCodeModel codeModel,
                                   final String encodedTypeName,
                                   final String... childTypeNames) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .children()
            .map(HierarchicalTypeDescriptor::typeName)
            .map(TypeName::name)
            .map(IrreducibleName::toString))
            .contains(childTypeNames);
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has no
     * <i>descendants</i>.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     */
    private void assertHasNoDescendants(final HierarchicalCodeModel codeModel,
                                        final String encodedTypeName) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .descendants())
            .isEmpty();
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has <i>descendants</i>
     * with the provided encoded {@link TypeName}s.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     * @param childTypeNames  the encoded <i>child</i> {@link TypeName}s
     */
    private void assertHasDescendants(final HierarchicalCodeModel codeModel,
                                      final String encodedTypeName,
                                      final String... childTypeNames) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .descendants()
            .map(HierarchicalTypeDescriptor::typeName)
            .map(TypeName::name)
            .map(IrreducibleName::toString))
            .contains(childTypeNames);
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has <i>parents</i>
     * with the provided encoded {@link TypeName}s.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     * @param parentTypeNames the encoded <i>parent</i> {@link TypeName}s
     */
    private void assertHasParents(final HierarchicalCodeModel codeModel,
                                  final String encodedTypeName,
                                  final String... parentTypeNames) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .parents()
            .map(HierarchicalTypeDescriptor::typeName)
            .map(TypeName::name)
            .map(IrreducibleName::toString))
            .contains(parentTypeNames);
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has no <i>parents</i>.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     */
    private void assertHasNoParents(final HierarchicalCodeModel codeModel,
                                    final String encodedTypeName) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .parents())
            .isEmpty();
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has <i>ancestors</i>
     * with the provided encoded {@link TypeName}s.
     *
     * @param codeModel         the {@link HierarchicalCodeModel}
     * @param encodedTypeName   the encoded {@link TypeName}
     * @param ancestorTypeNames the encoded <i>ancestor</i> {@link TypeName}s
     */
    private void assertHasAncestors(final HierarchicalCodeModel codeModel,
                                    final String encodedTypeName,
                                    final String... ancestorTypeNames) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .ancestors()
            .map(HierarchicalTypeDescriptor::typeName)
            .map(TypeName::name)
            .map(IrreducibleName::toString))
            .contains(ancestorTypeNames);
    }

    /**
     * Asserts that the specified encoded {@link TypeName} in the {@link HierarchicalCodeModel} has no <i>ancestors</i>.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     */
    private void assertHasNoAncestors(final HierarchicalCodeModel codeModel,
                                      final String encodedTypeName) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .ancestors())
            .isEmpty();
    }

    /**
     * Asserts that the encoded {@link TypeName} in the provided {@link HierarchicalCodeModel} is at the specified
     * {@link HierarchicalTypeDescriptor#level()}.
     *
     * @param codeModel       the {@link HierarchicalCodeModel}
     * @param encodedTypeName the encoded {@link TypeName}
     * @param level           the level
     */
    private void assertLevel(final HierarchicalCodeModel codeModel,
                             final String encodedTypeName,
                             final int level) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .level())
            .isEqualTo(level);
    }

    /**
     * Asserts that the encoded {@link TypeName} in the provided {@link HierarchicalCodeModel} possibly
     * forms a <i>Diamond Pattern</i>
     *
     * @param codeModel           the {@link HierarchicalCodeModel}
     * @param encodedTypeName     the encoded {@link TypeName}
     * @param formsDiamondPattern {@code true} if must form <i>Diamond Pattern</i>, {@code false} otherwise
     */
    private void assertFormsDiamondPattern(final HierarchicalCodeModel codeModel,
                                           final String encodedTypeName,
                                           final boolean formsDiamondPattern) {

        final var nameProvider = codeModel.getNameProvider();

        assertThat(codeModel
            .getTypeDescriptor(nameProvider.getTypeName(encodedTypeName), HierarchicalTypeDescriptor.class)
            .orElseThrow()
            .formsDiamondPattern())
            .isEqualTo(formsDiamondPattern);
    }

    /**
     * Asserts that the encoded {@link TypeName} is <strong>only</strong> assignable to each of the specified encoded
     * {@link TypeName}s within the context of the specified {@link HierarchicalCodeModel}.
     *
     * @param codeModel           the {@link HierarchicalCodeModel}
     * @param fromEncodedTypeName the encoded from {@link TypeName}
     * @param toEncodedTypeNames  the encoded to {@link TypeName}
     */
    private void assertIsOnlyAssignableTo(final HierarchicalCodeModel codeModel,
                                          final String fromEncodedTypeName,
                                          final String... toEncodedTypeNames) {

        final var nameProvider = codeModel.getNameProvider();

        final var fromTypeName = nameProvider.getTypeName(fromEncodedTypeName);

        // determine the TypeNames for comparison
        final var toTypeNames = Stream.of(toEncodedTypeNames)
            .map(nameProvider::getTypeName)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // assert using a TypeName
        toTypeNames.forEach(toTypeName ->
            assertThat(codeModel
                .getTypeDescriptor(fromTypeName, HierarchicalTypeDescriptor.class)
                .orElseThrow()
                .isAssignableTo(toTypeName))
                .withFailMessage(
                    () -> "Type: " + fromEncodedTypeName + " is not assignable to type: " + toTypeName)
                .isTrue());

        // assert using a TypeDescriptor
        toTypeNames.forEach(toTypeName ->
            assertThat(codeModel
                .getTypeDescriptor(fromTypeName, HierarchicalTypeDescriptor.class)
                .orElseThrow()
                .isAssignableTo(codeModel
                    .getTypeDescriptor(toTypeName, HierarchicalTypeDescriptor.class)
                    .orElseThrow()))
                .withFailMessage(
                    () -> "Type: " + fromEncodedTypeName + " is not assignable to type: " + toTypeName)
                .isTrue());

        // assert using TypeName with CodeModel
        toTypeNames.forEach(toTypeName ->
            assertThat(codeModel
                .isAssignable(fromTypeName, toTypeName))
                .withFailMessage(
                    () -> "Type: " + fromEncodedTypeName + " is not assignable to type: " + toTypeName)
                .isTrue());

        // assert using a TypeDescriptor with CodeModel
        toTypeNames.forEach(toTypeName ->
            assertThat(HierarchicalCodeModel
                .isAssignable(codeModel
                    .getTypeDescriptor(fromTypeName, HierarchicalTypeDescriptor.class)
                    .orElseThrow(), codeModel
                    .getTypeDescriptor(toTypeName, HierarchicalTypeDescriptor.class)
                    .orElseThrow()))
                .withFailMessage(
                    () -> "Type: " + fromEncodedTypeName + " is not assignable to type: " + toTypeName.name())
                .isTrue());

        // determine the TypeNames other than those specified
        final var otherTypeNames = codeModel
            .typeDescriptors()
            .map(TypeDescriptor::typeName)
            .filter(typeName -> !toTypeNames.contains(typeName))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // assert the type is not assignable to the other TypeNames
        otherTypeNames.forEach(toTypeName ->
            assertThat(codeModel
                .getTypeDescriptor(fromTypeName, HierarchicalTypeDescriptor.class)
                .orElseThrow()
                .isAssignableTo(toTypeName))
                .withFailMessage(
                    () -> "Type: " + fromEncodedTypeName + " is assignable to type: " + toTypeName)
                .isFalse());
    }

    /**
     * Ensure that {@link HierarchicalTypeDescriptor#level()} throws {@link IllegalStateException}
     * when a cycle exists in the type hierarchy instead of causing a {@link StackOverflowError}.
     */
    @Test
    void levelShouldThrowOnCyclicHierarchy() {
        final var codeModel = createEmptyCodeModel();
        final var nameProvider = codeModel.getNameProvider();

        final var nameA = nameProvider.getTypeName("CycleA");
        final var nameB = nameProvider.getTypeName("CycleB");

        final var usageA = SpecificTypeUsage.of(codeModel, nameA);
        final var usageB = SpecificTypeUsage.of(codeModel, nameB);

        final var descriptorA = codeModel.createTypeDescriptor(nameA, (_, n) -> new ClassTypeDescriptor(codeModel, n));
        final var descriptorB = codeModel.createTypeDescriptor(nameB, (_, n) -> new ClassTypeDescriptor(codeModel, n));

        // A extends B and B extends A — a direct cycle
        descriptorA.addTrait(Extends.of(usageB));
        descriptorB.addTrait(Extends.of(usageA));

        assertThatThrownBy(descriptorA::level)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cycle");
    }

    /**
     * Ensure that {@link HierarchicalTypeDescriptor#getAncestor} throws {@link IllegalStateException}
     * when a cycle exists in the type hierarchy instead of causing a {@link StackOverflowError}.
     */
    @Test
    void getAncestorShouldThrowOnCyclicHierarchy() {
        final var codeModel = createEmptyCodeModel();
        final var nameProvider = codeModel.getNameProvider();

        final var nameA = nameProvider.getTypeName("AncCycleA");
        final var nameB = nameProvider.getTypeName("AncCycleB");

        final var usageA = SpecificTypeUsage.of(codeModel, nameA);
        final var usageB = SpecificTypeUsage.of(codeModel, nameB);

        final var descriptorA = codeModel.createTypeDescriptor(nameA, (_, n) -> new ClassTypeDescriptor(codeModel, n));
        final var descriptorB = codeModel.createTypeDescriptor(nameB, (_, n) -> new ClassTypeDescriptor(codeModel, n));

        descriptorA.addTrait(Extends.of(usageB));
        descriptorB.addTrait(Extends.of(usageA));

        assertThatThrownBy(() -> descriptorA.getAncestor(_ -> false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cycle");
    }

    /**
     * Ensure that reading a type's {@code parents()} / {@code children()} while other threads are concurrently
     * establishing new parent-child edges (as happens when a shared {@link HierarchicalCodeModel} is populated from
     * multiple threads) does not throw {@link java.util.ConcurrentModificationException}.
     *
     * <p>Before the {@code parents}/{@code children} maps stored copy-on-write snapshots, each value was a
     * {@link LinkedHashSet} mutated in place under {@code compute}, while readers streamed that same set with no
     * synchronization - an intermittent CME thrown deep inside DI resolution's ancestor walk during concurrent
     * {@code spin} module compiles.</p>
     */
    @Test
    void shouldReadHierarchyConcurrentlyWithEdgeCreation() throws Exception {
        final var codeModel = createEmptyCodeModel();
        final var nameProvider = codeModel.getNameProvider();

        final var parentName = nameProvider.getTypeName("ConcurrentParent");
        final var parentUsage = SpecificTypeUsage.of(codeModel, parentName);
        final var parent = codeModel
            .createTypeDescriptor(parentName, (_, n) -> new ClassTypeDescriptor(codeModel, n));

        final var childCount = 500;

        // pre-create every child descriptor; the racing operation is purely the addTrait(Extends) edge creation
        final var children = IntStream.range(0, childCount)
            .mapToObj(i -> codeModel.createTypeDescriptor(
                nameProvider.getTypeName("ConcurrentChild" + i),
                (_, n) -> new ClassTypeDescriptor(codeModel, n)))
            .toList();

        final var readerCount = 4;
        final var barrier = new CyclicBarrier(readerCount + 1);
        final var writing = new AtomicBoolean(true);
        final var failures = new CopyOnWriteArrayList<Throwable>();

        final var readers = IntStream.range(0, readerCount)
            .mapToObj(_ -> Thread.ofVirtual().unstarted(() -> {
                try {
                    barrier.await();
                    while (writing.get()) {
                        parent.children().forEach(c -> c.typeName().name());
                        children.forEach(c -> c.ancestors().forEach(a -> a.typeName().name()));
                    }
                } catch (final Throwable t) {
                    failures.add(t);
                }
            }))
            .toList();

        readers.forEach(Thread::start);
        barrier.await();

        for (final var child : children) {
            child.addTrait(Extends.of(parentUsage));
        }

        writing.set(false);
        for (final var reader : readers) {
            reader.join();
        }

        assertThat(failures).isEmpty();
        assertThat(parent.children()).hasSize(childCount);
    }
}
