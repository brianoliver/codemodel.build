package build.codemodel.jdk;

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

import build.base.foundation.Lazy;
import build.base.foundation.stream.Streams;
import build.base.marshalling.Bound;
import build.base.marshalling.Marshal;
import build.base.marshalling.Marshalled;
import build.base.marshalling.Marshaller;
import build.base.marshalling.Marshalling;
import build.base.marshalling.Out;
import build.base.marshalling.Unmarshal;
import build.base.mereology.Composite;
import build.base.mereology.Strategy;
import build.codemodel.foundation.CodeModel;
import build.codemodel.foundation.descriptor.FormalParameterDescriptor;
import build.codemodel.foundation.descriptor.ModuleDescriptor;
import build.codemodel.foundation.descriptor.NamespaceDescriptor;
import build.codemodel.foundation.descriptor.ThrowableDescriptor;
import build.codemodel.foundation.descriptor.Trait;
import build.codemodel.foundation.descriptor.Traitable;
import build.codemodel.foundation.descriptor.TypeDescriptor;
import build.codemodel.foundation.naming.IrreducibleName;
import build.codemodel.foundation.naming.NameProvider;
import build.codemodel.foundation.naming.TypeName;
import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.AnnotationValue;
import build.codemodel.foundation.usage.ArrayTypeUsage;
import build.codemodel.foundation.usage.GenericTypeUsage;
import build.codemodel.foundation.usage.IntersectionTypeUsage;
import build.codemodel.foundation.usage.NamedTypeUsage;
import build.codemodel.foundation.usage.SpecificTypeUsage;
import build.codemodel.foundation.usage.TypeUsage;
import build.codemodel.foundation.usage.TypeVariableUsage;
import build.codemodel.foundation.usage.UnknownTypeUsage;
import build.codemodel.foundation.usage.WildcardTypeUsage;
import build.codemodel.jdk.descriptor.AnnotationType;
import build.codemodel.jdk.descriptor.ConstructorType;
import build.codemodel.jdk.descriptor.Default;
import build.codemodel.jdk.descriptor.EnclosingTypeDescriptor;
import build.codemodel.jdk.descriptor.EnumConstantDescriptor;
import build.codemodel.jdk.descriptor.EnumType;
import build.codemodel.jdk.descriptor.FieldType;
import build.codemodel.jdk.descriptor.Final;
import build.codemodel.jdk.descriptor.InitializerBlockDescriptor;
import build.codemodel.jdk.descriptor.JDKType;
import build.codemodel.jdk.descriptor.JDKTypeDescriptor;
import build.codemodel.jdk.descriptor.MemberTypeDescriptor;
import build.codemodel.jdk.descriptor.MethodBodyDescriptor;
import build.codemodel.jdk.descriptor.MethodType;
import build.codemodel.jdk.descriptor.Native;
import build.codemodel.jdk.descriptor.NonSealed;
import build.codemodel.jdk.descriptor.PermitsTypeDescriptor;
import build.codemodel.jdk.descriptor.ReceiverAnnotation;
import build.codemodel.jdk.descriptor.RecordComponentDescriptor;
import build.codemodel.jdk.descriptor.RecordType;
import build.codemodel.jdk.descriptor.Sealed;
import build.codemodel.jdk.descriptor.Static;
import build.codemodel.jdk.descriptor.Strictfp;
import build.codemodel.jdk.descriptor.Synchronized;
import build.codemodel.jdk.descriptor.Transient;
import build.codemodel.jdk.descriptor.Varargs;
import build.codemodel.jdk.descriptor.Volatile;
import build.codemodel.jdk.expression.ResolvedMethod;
import build.codemodel.objectoriented.ObjectOrientedCodeModel;
import build.codemodel.objectoriented.descriptor.AccessModifier;
import build.codemodel.objectoriented.descriptor.Classification;
import build.codemodel.objectoriented.descriptor.ConstructorDescriptor;
import build.codemodel.objectoriented.descriptor.DeclarationOrder;
import build.codemodel.objectoriented.descriptor.ExtendsTypeDescriptor;
import build.codemodel.objectoriented.descriptor.FieldDescriptor;
import build.codemodel.objectoriented.descriptor.ImplementsTypeDescriptor;
import build.codemodel.objectoriented.descriptor.MethodDescriptor;
import build.codemodel.objectoriented.descriptor.ParameterizedTypeDescriptor;
import build.codemodel.objectoriented.naming.MethodName;
import jakarta.inject.Inject;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A <i>JDK-based</i> {@link ObjectOrientedCodeModel} allowing the runtime discovery, access and representation of
 * {@link TypeDescriptor}s based using <a href="https://en.wikipedia.org/wiki/Reflective_programming">Reflection</a>.
 *
 * @author brian.oliver
 * @since Mar-2025
 */
public class JDKCodeModel
    extends ObjectOrientedCodeModel {

    /**
     * Constructs an empty {@link JDKCodeModel}.
     *
     * @param nameProvider the {@link NameProvider}
     */
    @Inject
    public JDKCodeModel(final NameProvider nameProvider) {
        super(nameProvider);

        // establish the foundation types for the CodeModel
        initialize();
    }

    /**
     * Constructs an {@link JDKCodeModel} using a {@link Marshaller}.
     *
     * @param nameProvider         the {@link NameProvider}
     * @param marshaller           the {@link Marshaller}
     * @param traits               the {@link Traitable}
     * @param typeDescriptors      the {@link Stream} of {@link Marshalled} {@link TypeDescriptor}s
     * @param moduleDescriptors    the {@link Stream} of {@link Marshalled} {@link ModuleDescriptor}s
     * @param namespaceDescriptors the {@link Stream} of {@link Marshalled} {@link NamespaceDescriptor}s
     */
    @Unmarshal
    public JDKCodeModel(@Bound final NameProvider nameProvider,
                        @Bound final Marshaller marshaller,
                        final Stream<Marshalled<Trait>> traits,
                        final Stream<Marshalled<TypeDescriptor>> typeDescriptors,
                        final Stream<Marshalled<ModuleDescriptor>> moduleDescriptors,
                        final Stream<Marshalled<NamespaceDescriptor>> namespaceDescriptors) {

        super(nameProvider, marshaller, traits, typeDescriptors, moduleDescriptors, namespaceDescriptors);
    }

    /**
     * Destructs an {@link ObjectOrientedCodeModel} so it can be {@link Marshal}led.
     *
     * @param marshaller           the {@link Marshaller} to use for marshalling
     * @param traits               the {@link Marshalled} {@link Trait}s of the {@link CodeModel} itself
     * @param typeDescriptors      the {@link Stream} of marshallable {@link TypeDescriptor}s
     * @param moduleDescriptors    the {@link Stream} of marshallable {@link ModuleDescriptor}s
     * @param namespaceDescriptors the {@link Stream} of marshallable {@link NamespaceDescriptor}s
     */
    @Marshal
    public void destructor(@Bound final Marshaller marshaller,
                           final Out<Stream<Marshalled<Trait>>> traits,
                           final Out<Stream<Marshalled<TypeDescriptor>>> typeDescriptors,
                           final Out<Stream<Marshalled<ModuleDescriptor>>> moduleDescriptors,
                           final Out<Stream<Marshalled<NamespaceDescriptor>>> namespaceDescriptors) {

        super.destructor(marshaller, traits, typeDescriptors, moduleDescriptors, namespaceDescriptors);
    }

    /**
     * Obtains the {@link TypeUsage} for the specified {@link AnnotatedType} using reflection.
     *
     * @param annotatedType the {@link AnnotatedType}
     * @return the {@link TypeUsage}
     */
    public TypeUsage getTypeUsage(final AnnotatedType annotatedType) {
        Objects.requireNonNull(annotatedType, "The AnnotatedType must not be null");

        final var typeUsage = getStructuralTypeUsage(annotatedType);

        // include the annotations on the TypeUsage
        getAnnotations(annotatedType)
            .forEach(typeUsage::addTrait);

        return typeUsage;
    }

    /**
     * Builds the {@link TypeUsage} structure for the specified {@link AnnotatedType} using reflection,
     * descending into nested {@code TYPE_USE} annotated positions (generic type arguments, wildcard
     * bounds, and array components) so annotations at any depth are preserved. Unlike
     * {@link #getTypeUsage(AnnotatedType)}, this does <strong>not</strong> apply the annotations declared
     * directly on the outermost {@link AnnotatedType}, allowing callers (such as field resolution) to
     * supply outer-level annotations from a different source without duplicating them.
     *
     * @param annotatedType the {@link AnnotatedType}
     * @return the structural {@link TypeUsage}, without the outermost {@link AnnotatedType}'s own annotations
     */
    private TypeUsage getStructuralTypeUsage(final AnnotatedType annotatedType) {
        if (annotatedType instanceof AnnotatedParameterizedType annotatedParameterizedType) {
            final var parameterizedType = (ParameterizedType) annotatedType.getType();
            final var rawTypeUsage = getNamedTypeUsage(parameterizedType.getRawType())
                .orElseThrow(() -> new IllegalStateException(
                    "RawType of ParameterizedType is not named:" + annotatedType.getType()));
            final var parameters = Arrays.stream(annotatedParameterizedType.getAnnotatedActualTypeArguments())
                .map(this::getTypeUsage)
                .toArray(TypeUsage[]::new);

            return GenericTypeUsage.of(this, rawTypeUsage.typeName(), parameters);
        } else if (annotatedType instanceof AnnotatedWildcardType annotatedWildcardType) {
            final var lowers = annotatedWildcardType.getAnnotatedLowerBounds();
            final var uppers = annotatedWildcardType.getAnnotatedUpperBounds();

            final Optional<Lazy<TypeUsage>> optLower = lowers.length > 0
                ? Optional.of(Lazy.of(getTypeUsage(lowers[0])))
                : Optional.empty();

            // getAnnotatedUpperBounds() returns [Object] for unbounded `?` — treat that as no explicit upper bound
            final Optional<Lazy<TypeUsage>> optUpper = uppers.length > 0 && !uppers[0].getType().equals(Object.class)
                ? Optional.of(Lazy.of(getTypeUsage(uppers[0])))
                : Optional.empty();

            return WildcardTypeUsage.of(this, optLower, optUpper);
        } else if (annotatedType instanceof AnnotatedArrayType annotatedArrayType) {
            return ArrayTypeUsage.of(
                this,
                Lazy.of(getTypeUsage(annotatedArrayType.getAnnotatedGenericComponentType())));
        }

        return getTypeUsage(annotatedType.getType());
    }

    /**
     * Obtains the {@link TypeUsage} for the specified {@link Parameter} using reflection.
     *
     * @param parameter the {@link Parameter}
     * @return the {@link TypeUsage}
     */
    public TypeUsage getTypeUsage(final Parameter parameter) {
        Objects.requireNonNull(parameter, "The Parameter must not be null");

        final var typeUsage = getTypeUsage(parameter.getParameterizedType());

        // include the annotations on the TypeUsage
        getAnnotations(parameter)
            .forEach(typeUsage::addTrait);

        return typeUsage;
    }

    /**
     * Obtains the {@link TypeUsage} for the specified {@link Type} with the
     * provided {@link Annotation}s using reflection.
     *
     * @param type        the {@link Type}
     * @param annotations the {@link Annotation}s
     * @return the {@link TypeUsage}
     */
    public TypeUsage getTypeUsage(final Type type,
                                  final Annotation... annotations) {

        final TypeUsage typeUsage;

        final var nameProvider = getNameProvider();

        if (type instanceof Class<?> classType) {
            final var typeName = nameProvider.getTypeName(classType);

            // a bare Class with declared type parameters is a raw usage of a generic type
            // (e.g. `List list;`), distinct from a genuinely non-generic type
            typeUsage = classType.getTypeParameters().length > 0
                ? GenericTypeUsage.of(this, typeName)
                : SpecificTypeUsage.of(this, typeName);
        } else if (type instanceof ParameterizedType parameterizedType) {
            final var rawTypeUsage = getNamedTypeUsage(parameterizedType.getRawType())
                .orElseThrow(() -> new IllegalStateException("RawType of ParameterizedType is not named:" + type));
            final var parameters = Arrays.stream(parameterizedType.getActualTypeArguments())
                .map(this::getTypeUsage)
                .toArray(TypeUsage[]::new);

            typeUsage = GenericTypeUsage.of(this, rawTypeUsage.typeName(), parameters);
        } else if (type instanceof TypeVariable<?> typeVariable) {
            typeUsage = resolveTypeVariableUsage(typeVariable);
        } else if (type instanceof WildcardType wildcardType) {
            final Type[] lowers = wildcardType.getLowerBounds();
            final Type[] uppers = wildcardType.getUpperBounds();

            final Optional<Lazy<TypeUsage>> optLower = lowers.length > 0
                ? Optional.of(Lazy.of(getTypeUsage(lowers[0])))
                : Optional.empty();

            // getUpperBounds() returns [Object] for unbounded `?` — treat that as no explicit upper bound
            final Optional<Lazy<TypeUsage>> optUpper = uppers.length > 0 && !uppers[0].equals(Object.class)
                ? Optional.of(Lazy.of(getTypeUsage(uppers[0])))
                : Optional.empty();

            typeUsage = WildcardTypeUsage.of(this, optLower, optUpper);
        } else if (type instanceof GenericArrayType genericArrayType) {
            typeUsage = ArrayTypeUsage.of(
                this,
                Lazy.of(getTypeUsage(genericArrayType.getGenericComponentType())));
        } else {
            typeUsage = UnknownTypeUsage.create(this);
        }

        // include the provided Annotations
        if (annotations != null && annotations.length > 0) {
            Arrays.stream(annotations)
                .map(this::getAnnotation)
                .forEach(typeUsage::addTrait);
        }

        return typeUsage;
    }

    /**
     * Resolves the {@link TypeVariableUsage} for a reflective {@link TypeVariable}, guarding against
     * self-referential bounds (e.g. {@code T extends Comparable<T>} or {@code E extends Enum<E>}).
     * <p>
     * A skeleton {@link TypeVariableUsage} with an as-yet-unset {@link Lazy} upper bound is created and
     * registered under the {@link TypeVariable}'s identity <i>before</i> its bound is resolved. If
     * resolving that bound recurses back to the same {@link TypeVariable} (directly or transitively),
     * the in-flight skeleton is returned instead of re-entering resolution, and the recursive reference
     * shares the same instance whose {@link Lazy} is filled in exactly once when the outer resolution
     * completes. This allows resolution to any depth without truncating information, unlike a guard that
     * simply skips re-resolution and leaves the bound empty.
     *
     * @param typeVariable the reflective {@link TypeVariable}
     * @return the {@link TypeVariableUsage}
     */
    private TypeVariableUsage resolveTypeVariableUsage(final TypeVariable<?> typeVariable) {
        final var inProgress = IN_PROGRESS_TYPE_VARIABLES.get();
        final var existing = inProgress.get(typeVariable);
        if (existing != null) {
            return existing;
        }

        final var nameProvider = getNameProvider();
        final var variableName = nameProvider.getIrreducibleName(typeVariable.getName());
        final var typeName = resolveTypeVariableTypeName(typeVariable, variableName);
        final var annotatedBounds = typeVariable.getAnnotatedBounds();

        // an unbounded type variable still reports an implicit java.lang.Object bound; elide it
        // to match the source-parsing path, where an unbounded `<T>` has no upper bound trait at all
        final var isImplicitObjectBound = annotatedBounds.length == 1
            && annotatedBounds[0].getType() == Object.class;

        if (annotatedBounds.length == 0 || isImplicitObjectBound) {
            final var unboundedTypeVariableUsage = TypeVariableUsage.of(this, typeName, Optional.empty(), Optional.empty());
            getAnnotations(typeVariable).forEach(unboundedTypeVariableUsage::addTrait);
            return unboundedTypeVariableUsage;
        }

        final Lazy<TypeUsage> upperBound = Lazy.empty();
        final var typeVariableUsage =
            TypeVariableUsage.of(this, typeName, Optional.empty(), Optional.of(upperBound));
        getAnnotations(typeVariable).forEach(typeVariableUsage::addTrait);

        inProgress.put(typeVariable, typeVariableUsage);
        try {
            final List<TypeUsage> upperBoundTypeUsages = Streams.of(annotatedBounds)
                .map(this::getTypeUsage)
                .toList();

            final var resolvedUpperBound = upperBoundTypeUsages.size() == 1
                ? upperBoundTypeUsages.getFirst()
                : IntersectionTypeUsage.of(this, upperBoundTypeUsages.stream().map(Lazy::of));

            upperBound.set(resolvedUpperBound);
        } finally {
            inProgress.remove(typeVariable);
        }

        return typeVariableUsage;
    }

    /**
     * Resolves the {@link TypeName} for a type variable (e.g. {@code T} in {@code class Foo<T>}).
     * A bare simple name alone (e.g. just {@code "T"}) can't distinguish one type variable from
     * another of the same name declared elsewhere (e.g. {@code List<E>} vs {@code Set<E>}), so this
     * scopes the name under the class that declares it - directly for a class type parameter, or
     * via {@link Member#getDeclaringClass()} for a method/constructor type parameter.
     */
    private TypeName resolveTypeVariableTypeName(final TypeVariable<?> typeVariable,
                                                 final IrreducibleName variableName) {
        final var nameProvider = getNameProvider();
        final var declaringClass = switch (typeVariable.getGenericDeclaration()) {
            case Class<?> clazz -> clazz;
            case Member member -> member.getDeclaringClass();
            default -> null;
        };

        if (declaringClass == null) {
            return nameProvider.getTypeName(Optional.empty(), Optional.empty(), Optional.empty(), variableName);
        }

        final var enclosingTypeName = nameProvider.getTypeName(declaringClass);
        return nameProvider.getTypeName(enclosingTypeName.moduleName(), enclosingTypeName.namespace(),
            Optional.of(enclosingTypeName), variableName);
    }

    /**
     * Attempts to obtain the {@link NamedTypeUsage} for the specified {@link AnnotatedType} using reflection.
     *
     * @param type the {@link AnnotatedType}
     * @return the {@link Optional} {@link NamedTypeUsage} or {@link Optional#empty()} if the {@link AnnotatedType} is not named
     */
    public Optional<NamedTypeUsage> getNamedTypeUsage(final AnnotatedType type) {

        return getTypeUsage(type) instanceof NamedTypeUsage namedTypeUsage
            ? Optional.of(namedTypeUsage)
            : Optional.empty();
    }

    /**
     * Attempts to obtain the {@link NamedTypeUsage} for the specified {@link build.base.marshalling.Parameter} using reflection.
     *
     * @param parameter the {@link Parameter}
     * @return the {@link Optional} {@link NamedTypeUsage} or {@link Optional#empty()} if the {@link Parameter} is not named
     */
    public Optional<NamedTypeUsage> getNamedTypeUsage(final Parameter parameter) {

        return getTypeUsage(parameter) instanceof NamedTypeUsage namedTypeUsage
            ? Optional.of(namedTypeUsage)
            : Optional.empty();
    }

    /**
     * Attempts to obtain the {@link NamedTypeUsage} for the specified {@link Type} using reflection.
     *
     * @param type the {@link Type}
     * @return the {@link Optional} {@link NamedTypeUsage} or {@link Optional#empty()} if the {@link Type} is not named
     */
    public Optional<NamedTypeUsage> getNamedTypeUsage(final Type type) {

        return getTypeUsage(type) instanceof NamedTypeUsage namedTypeUsage
            ? Optional.of(namedTypeUsage)
            : Optional.empty();
    }

    /**
     * Attempts to obtain the {@link JDKTypeDescriptor} for the specified {@link Type} using reflection.
     * <p>
     * Some {@link Type}s can't, don't or won't produce {@link TypeDescriptor}s. For example; arrays, intersections,
     * wildcards, type variables.  These are examples of uses or declarations of types, and thus don't have
     * {@link TypeDescriptor}s.  The type information for these {@link Type}s are adequately described by
     * {@link TypeUsage}s.
     *
     * @param type the {@link Type}
     * @return the {@link Optional} {@link TypeDescriptor}
     */
    public Optional<JDKTypeDescriptor> getJDKTypeDescriptor(final Type type) {

        if (type instanceof Class<?> classType) {
            // resolve the Class, including any related (super and interface) classes
            final var queue = new ArrayList<Class<?>>(10);
            queue.add(classType);

            final var resolved = new LinkedHashMap<Class<?>, JDKTypeDescriptor>();

            while (!queue.isEmpty()) {
                final Class<?> current = queue.removeFirst();

                getJDKTypeDescriptor(current, clazz -> {
                    if (!resolved.containsKey(clazz)) {
                        queue.add(clazz);
                    }
                }).ifPresent(typeDescriptor -> resolved.put(current, typeDescriptor));
            }

            return Optional.ofNullable(resolved.get(classType));
        } else if (type instanceof ParameterizedType parameterizedType) {
            // for ParameterizedTypes we return the TypeDescriptor of the Raw Type
            return getJDKTypeDescriptor(parameterizedType.getRawType());
        }

        // unsupported type of types (like Arrays etc) don't get TypeDescriptors
        return Optional.empty();
    }

    /**
     * Attempts to resolve a {@link TypeDescriptor} for the specified {@link Class} and that {@link Class} only.  Any
     * super or interface {@link Class}es will be provided to the {@link Consumer} to track potential other resolutions,
     * but they won't occur here.
     *
     * @param classType the {@link Class} to resolve
     * @param consumer  the {@link Consumer} of other potential {@link Class}es to resolve
     * @return the {@link Optional}ly resolved {@link TypeDescriptor}, or {@link Optional#empty()} if it could not be
     * resolved
     */
    private Optional<JDKTypeDescriptor> getJDKTypeDescriptor(final Class<?> classType,
                                                             final Consumer<? super Class<?>> consumer) {

        final var typeName = getNameProvider().getTypeName(classType);

        // The populate runs atomically inside computeIfAbsent so no other thread can observe a
        // partially-built descriptor.
        final var typeDescriptor = createTypeDescriptor(typeName, JDKTypeDescriptor.supplier(classType), descriptor -> populateJDKTypeDescriptor(descriptor, classType, consumer));

        return Optional.of(typeDescriptor);
    }

    private void populateJDKTypeDescriptor(final JDKTypeDescriptor typeDescriptor,
                                           final Class<?> classType,
                                           final Consumer<? super Class<?>> consumer) {

        final var typeName = typeDescriptor.typeName();

        // include the JDKType in the TypeDescriptor
        typeDescriptor.addTrait(new JDKType(classType));

        // include the type-kind marker (annotation/enum/record), mirroring the source-parsing path,
        // which derives it from the ElementKind
        if (classType.isAnnotation()) {
            typeDescriptor.addTrait(AnnotationType.ANNOTATION_TYPE);
        } else if (classType.isEnum()) {
            typeDescriptor.addTrait(EnumType.ENUM);
        } else if (classType.isRecord()) {
            typeDescriptor.addTrait(RecordType.RECORD);
        }

        // include the EnclosingTypeDescriptor back-pointer for a member type, mirroring the
        // source-parsing path (which adds it whenever the enclosing element is a type). Local and
        // anonymous classes are excluded, matching both that path and getDeclaredClasses() below.
        if (classType.isMemberClass()) {
            typeDescriptor.addTrait(new EnclosingTypeDescriptor(
                getNameProvider().getTypeName(classType.getEnclosingClass())));
        }

        final var classModifier = classType.getModifiers();

        // include the Static trait (if necessary)
        if (Modifier.isStatic(classModifier)) {
            typeDescriptor.addTrait(Static.STATIC);
        }

        // include the AccessModifier
        getAccessModifier(classModifier)
            .ifPresent(typeDescriptor::addTrait);

        // include the Classification
        typeDescriptor.addTrait(getClassification(classModifier));

        // include the Sealed/NonSealed traits and permitted subtypes
        if (classType.isSealed()) {
            typeDescriptor.addTrait(Sealed.SEALED);
            Arrays.stream(classType.getPermittedSubclasses())
                .forEach(permitted -> getNamedTypeUsage((Type) permitted)
                    .ifPresent(named -> typeDescriptor.addTrait(PermitsTypeDescriptor.of(named))));
        } else if (!Modifier.isFinal(classModifier) && isDirectSubtypeOfSealed(classType)) {
            typeDescriptor.addTrait(NonSealed.NON_SEALED);
        }

        // include the generic parameter declarations on the type itself
        final TypeVariable<?>[] typeParameters = classType.getTypeParameters();
        if (typeParameters.length > 0) {
            final var typeVars = Arrays.stream(typeParameters)
                .map(tp -> (TypeVariableUsage) getTypeUsage(tp))
                .toList();
            typeDescriptor.addTrait(ParameterizedTypeDescriptor.of(this, typeVars.stream()));
        }

        // include the ExtendsTypeDescriptor (should a super-class be defined)
        final var superType = classType.getAnnotatedSuperclass();
        if (superType != null) {
            final var extendsTypeDescriptor = ExtendsTypeDescriptor.of(getNamedTypeUsage(superType)
                .orElseThrow(() -> new IllegalStateException(
                    "Super class " + superType + " of " + typeName + " is not named!")));

            typeDescriptor.addTrait(extendsTypeDescriptor);

            if (superType.getType() instanceof Class<?> superClass) {
                consumer.accept(superClass);
            }
        }

        // include the ImplementsTypeDescriptors for implemented Interfaces
        Streams.of(classType.getAnnotatedInterfaces())
            .forEach(interfaceType -> {
                final var interfaceTypeUsage = getNamedTypeUsage(interfaceType)
                    .orElseThrow(() -> new IllegalStateException(
                        "The interface type " + interfaceType + " is not named!"));

                final var implementsTypeDescriptor = ImplementsTypeDescriptor.of(interfaceTypeUsage);
                typeDescriptor.addTrait(implementsTypeDescriptor);

                if (interfaceType.getType() instanceof Class<?> interfaceClass) {
                    consumer.accept(interfaceClass);
                }
            });

        // include EnumConstantDescriptors for the declared enum constants, ordered separately from
        // the other members (mirroring the source-parsing path's enumConstantOrder). getEnumConstants()
        // (backed by Enum's ordinal) is used for ordering rather than getDeclaredFields(), since the
        // latter's order is not guaranteed by the JVM spec (see JDKCodeModelDeclarationOrderTests).
        if (classType.isEnum()) {
            Streams.of(classType.getEnumConstants())
                .forEach(constant -> {
                    final var enumConstant = (Enum<?>) constant;
                    final var name = getNameProvider().getIrreducibleName(enumConstant.name());
                    typeDescriptor.addTrait(EnumConstantDescriptor.of(this, name, enumConstant.ordinal()));
                });
        }

        // include RecordComponentDescriptors for the declared record components (in declaration order)
        if (classType.isRecord()) {
            Streams.of(classType.getRecordComponents())
                .forEach(component -> {
                    final var name = getNameProvider().getIrreducibleName(component.getName());
                    final var componentType = getStructuralTypeUsage(component.getAnnotatedType());
                    typeDescriptor.addTrait(RecordComponentDescriptor.of(name, componentType));
                });
        }

        // include MemberTypeDescriptors for the declared member types, and resolve each as its own
        // JDKTypeDescriptor (mirroring how superclasses and interfaces are enqueued for resolution)
        Streams.of(classType.getDeclaredClasses())
            .forEach(memberType -> {
                final var memberTypeName = getNameProvider().getTypeName(memberType);
                typeDescriptor.addTrait(MemberTypeDescriptor.of(memberTypeName));
                consumer.accept(memberType);
            });

        // include ConstructorDescriptor, MethodDescriptor, and FieldDescriptor for the declared members,
        // sharing a single DeclarationOrder counter across all three kinds so the trait reflects each
        // member's position among all members, not just among its own kind
        final var memberOrder = new AtomicInteger();

        // include ConstructorDescriptor for the declared Constructors
        Streams.of(classType.getDeclaredConstructors())
            .forEach(constructor -> populateConstructor(typeDescriptor, constructor, memberOrder.getAndIncrement()));

        // include MethodDescriptors for the declared Methods
        Streams.of(classType.getDeclaredMethods())
            .forEach(method -> populateMethod(typeDescriptor, typeName, method, memberOrder.getAndIncrement()));

        // include FieldDescriptors for the declared Fields, excluding enum constants (already
        // modeled above as EnumConstantDescriptors) and compiler-synthesized fields (e.g. an enum's
        // $VALUES array), which have no counterpart in the source-parsing path since it only ever
        // walks explicitly written members
        Streams.of(classType.getDeclaredFields())
            .filter(field -> !field.isEnumConstant() && !field.isSynthetic())
            .forEach(field -> populateField(typeDescriptor, field, memberOrder.getAndIncrement()));

        // include the annotations on the TypeDescriptor (from the Class Type)
        getAnnotations(classType)
            .forEach(typeDescriptor::addTrait);
    }

    private void populateConstructor(final JDKTypeDescriptor typeDescriptor, final Constructor<?> constructor,
                                     final int order) {
        final var formalParameters = getFormalParameters(constructor.getParameters());
        final var constructorDescriptor = ConstructorDescriptor.of(typeDescriptor, formalParameters);
        constructorDescriptor.addTrait(new DeclarationOrder(order));

        // include the annotations on the ConstructorDescriptor
        getAnnotations(constructor)
            .forEach(constructorDescriptor::addTrait);

        // include the annotations on the receiver parameter (if any)
        addReceiverAnnotations(constructorDescriptor, constructor);

        // include the AccessModifier
        getAccessModifier(constructor.getModifiers())
            .ifPresent(constructorDescriptor::addTrait);

        // include the ThrowableDescriptors
        Streams.of(constructor.getAnnotatedExceptionTypes())
            .forEach(exceptionType -> {
                final var exceptionTypeUsage = getNamedTypeUsage(exceptionType)
                    .orElseThrow(() -> new IllegalStateException(
                        "The exception type " + exceptionType + " is not named!"));
                final var throwableDescriptor = ThrowableDescriptor.of(exceptionTypeUsage);
                constructorDescriptor.addTrait(throwableDescriptor);
            });

        // include the ConstructorType
        constructorDescriptor.addTrait(new ConstructorType(constructor));

        typeDescriptor.addTrait(constructorDescriptor);
    }

    private void populateMethod(final JDKTypeDescriptor typeDescriptor, final TypeName typeName,
                                final Method method, final int order) {
        final var nameProvider = getNameProvider();
        final var methodName = MethodName.of(
            typeName.moduleName(),
            typeName.namespace(),
            Optional.of(typeName),
            nameProvider.getIrreducibleName(method.getName()));

        final var returnType = getTypeUsage(method.getAnnotatedReturnType());
        final var formalParameters = getFormalParameters(method.getParameters());

        final var methodDescriptor = MethodDescriptor
            .of(typeDescriptor, methodName, returnType, formalParameters);

        final var methodModifiers = method.getModifiers();

        // include the Static trait (if necessary)
        if (Modifier.isStatic(methodModifiers)) {
            methodDescriptor.addTrait(Static.STATIC);
        }

        // include the Synchronized/Native/Strictfp traits (if necessary)
        if (Modifier.isSynchronized(methodModifiers)) {
            methodDescriptor.addTrait(Synchronized.SYNCHRONIZED);
        }
        if (Modifier.isNative(methodModifiers)) {
            methodDescriptor.addTrait(Native.NATIVE);
        }
        if (Modifier.isStrict(methodModifiers)) {
            methodDescriptor.addTrait(Strictfp.STRICTFP);
        }

        // include the Default trait for interface methods declared with a body via `default`
        if (method.isDefault()) {
            methodDescriptor.addTrait(Default.DEFAULT);
        }

        // include the AccessModifier
        getAccessModifier(methodModifiers)
            .ifPresent(methodDescriptor::addTrait);

        // include the Classification
        methodDescriptor.addTrait(getClassification(methodModifiers));

        // include the annotations on the MethodDescriptor
        getAnnotations(method)
            .forEach(methodDescriptor::addTrait);

        // include the annotations on the receiver parameter (if any)
        addReceiverAnnotations(methodDescriptor, method);

        // include the ThrowableDescriptors
        Streams.of(method.getAnnotatedExceptionTypes())
            .forEach(exceptionType -> {
                final var exceptionTypeUsage = getNamedTypeUsage(exceptionType)
                    .orElseThrow(() -> new IllegalStateException(
                        "The exception type " + exceptionType + " is not named!"));
                final var throwableDescriptor = ThrowableDescriptor.of(exceptionTypeUsage);
                methodDescriptor.addTrait(throwableDescriptor);
            });

        // include the MethodType
        methodDescriptor.addTrait(new MethodType(method));

        // include the DeclarationOrder
        methodDescriptor.addTrait(new DeclarationOrder(order));

        typeDescriptor.addTrait(methodDescriptor);
    }

    private void populateField(final JDKTypeDescriptor typeDescriptor, final Field field, final int order) {
        final var nameProvider = getNameProvider();
        final var fieldName = nameProvider.getIrreducibleName(field.getName());

        // Field.getAnnotatedType().getAnnotations() omits declaration-only annotations
        // (i.e. those not targeting TYPE_USE), so the outer-level annotations must come from
        // Field.getAnnotations() rather than the AnnotatedType, to avoid missing or duplicating
        // them; getStructuralTypeUsage still descends into the AnnotatedType for nested
        // TYPE_USE annotations (generic arguments, wildcard bounds, array components).
        final var fieldType = getStructuralTypeUsage(field.getAnnotatedType());

        // include the annotations on the Field TypeUsage
        getAnnotations(field)
            .forEach(fieldType::addTrait);

        final var fieldDescriptor = FieldDescriptor.of(this, fieldName, fieldType);
        final var fieldModifiers = field.getModifiers();

        // include the Static trait (if necessary)
        if (Modifier.isStatic(fieldModifiers)) {
            fieldDescriptor.addTrait(Static.STATIC);
        }

        // include the Transient/Volatile traits (if necessary)
        if (Modifier.isTransient(fieldModifiers)) {
            fieldDescriptor.addTrait(Transient.TRANSIENT);
        }
        if (Modifier.isVolatile(fieldModifiers)) {
            fieldDescriptor.addTrait(Volatile.VOLATILE);
        }

        // include the AccessModifier
        getAccessModifier(fieldModifiers)
            .ifPresent(fieldDescriptor::addTrait);

        // include the Classification
        fieldDescriptor.addTrait(getClassification(fieldModifiers));

        // include the FieldType
        fieldDescriptor.addTrait(new FieldType(field));

        // include the DeclarationOrder
        fieldDescriptor.addTrait(new DeclarationOrder(order));

        typeDescriptor.addTrait(fieldDescriptor);
    }

    /**
     * Obtains a {@link Stream} of {@link Trait}s of the specified {@link Class} from the provided
     * {@link JDKTypeDescriptor}, including those that are transitively defined in the
     * <a href="https://en.wikipedia.org/wiki/Class_hierarchy">Class Hierarchy</a> through the use of
     * {@link ExtendsTypeDescriptor}s  {@link ImplementsTypeDescriptor}s, returned in-order of
     * discovery from the {@link JDKTypeDescriptor} and <i>up</i>.
     *
     * @param <T>                the type of {@link Trait}
     * @param javaTypeDescriptor the {@link JDKTypeDescriptor}
     * @param traitClass         the {@link Class} of {@link Trait}
     * @return a {@link Stream} of {@link Trait}s
     */
    public <T extends Trait> Stream<T> getTraitsInHierarchy(final JDKTypeDescriptor javaTypeDescriptor,
                                                            final Class<T> traitClass) {

        if (javaTypeDescriptor == null || traitClass == null) {
            return Stream.empty();
        }

        // assume no results (but we want to keep them ordered)
        final LinkedHashSet<T> traits = new LinkedHashSet<>();

        // we use a queue to avoid recursion
        final Queue<JDKTypeDescriptor> queue = new LinkedList<>();
        queue.offer(javaTypeDescriptor);

        // we don't want to re-process previously seen TypeDescriptors
        final HashSet<JDKTypeDescriptor> processed = new HashSet<>();

        while (!queue.isEmpty()) {

            final var current = queue.poll();

            // include the current TypeDescriptor as being processed
            processed.add(current);

            // include the Traits for this Traitable
            current.traits(traitClass)
                .forEach(traits::add);

            // include processing of the interfaces to discover Traits (if not already processed)
            current.interfaceTypeUsages()
                .map(typeUsage -> getJDKTypeDescriptor(typeUsage).orElse(null))
                .filter(Objects::nonNull)
                .filter(descriptor -> !processed.contains(descriptor))
                .forEach(queue::offer);

            // include processing of the super class to discover Traits (if not already processed)
            current.parentTypeUsage()
                .flatMap(this::getJDKTypeDescriptor)
                .filter(descriptor -> !processed.contains(descriptor))
                .ifPresent(queue::offer);
        }

        return traits.stream();
    }

    /**
     * Attempts to obtain the {@link JDKTypeDescriptor} for the specified {@link TypeName}.
     *
     * @param typeName the {@link TypeName}
     * @return the {@link Optional} {@link JDKTypeDescriptor} or {@link Optional#empty()} if unavailable
     */
    public Optional<JDKTypeDescriptor> getJDKTypeDescriptor(final TypeName typeName) {

        if (typeName == null) {
            return Optional.empty();
        }

        final var existing = getTypeDescriptor(typeName);
        if (existing.isPresent()) {
            return existing.map(JDKTypeDescriptor.class::cast);
        }

        try {
            final var typeUsageClass = Thread.currentThread().getContextClassLoader()
                .loadClass(typeName.binaryName());

            return getJDKTypeDescriptor(typeUsageClass);
        } catch (final ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to obtain the {@link JDKTypeDescriptor} for the specified {@link TypeUsage}.
     *
     * @param typeUsage the {@link TypeUsage}
     * @return the {@link Optional} {@link JDKTypeDescriptor} or {@link Optional#empty()} if unavailable
     */
    public Optional<JDKTypeDescriptor> getJDKTypeDescriptor(final TypeUsage typeUsage) {

        return typeUsage instanceof NamedTypeUsage namedTypeUsage
            ? getJDKTypeDescriptor(namedTypeUsage.typeName())
            : Optional.empty();
    }

    /**
     * Obtains the {@link AnnotationTypeUsage}s defined by the specified {@link AnnotatedElement}.
     *
     * @param element the {@link AnnotatedElement}
     * @return a {@link Stream} of {@link AnnotationTypeUsage}s
     */
    public Stream<AnnotationTypeUsage> getAnnotations(final AnnotatedElement element) {

        return element == null
            ? Stream.empty()
            : Streams.of(element.getDeclaredAnnotations())
            .map(this::getAnnotation);
    }

    /**
     * Captures annotations written directly on {@code executable}'s receiver parameter
     * (e.g. {@code @Anno} in {@code void m(@Anno MyClass this)}), as a {@link ReceiverAnnotation}
     * trait per annotation. {@code Executable.getAnnotatedReceiverType()} never returns
     * {@code null} - for a static method, or a constructor of a top-level class, it instead
     * returns an unannotated {@link AnnotatedType}, so this is a no-op in those cases.
     */
    private void addReceiverAnnotations(final Traitable traitable, final Executable executable) {
        getAnnotations(executable.getAnnotatedReceiverType())
            .map(annotation -> ReceiverAnnotation.of(this, annotation))
            .forEach(traitable::addTrait);
    }

    /**
     * Obtains the {@link AnnotationTypeUsage} represented by the specified {@link Annotation}.
     *
     * @param annotation the {@link Annotation}
     * @return the corresponding {@link AnnotationTypeUsage}
     */
    public AnnotationTypeUsage getAnnotation(final Annotation annotation) {
        Objects.requireNonNull(annotation, "The Annotation must not be null");

        final var nameProvider = getNameProvider();

        final var annotationType = annotation.annotationType();
        final var typeName = nameProvider.getTypeName(annotationType);

        final var annotationValues = Streams.of(annotationType.getDeclaredMethods())
            .filter(method -> !method.isDefault() && method.getParameters().length == 0)
            .map(method -> {
                try {
                    final var name = nameProvider.getIrreducibleName(method.getName());
                    final var raw = method.invoke(annotation);
                    final AnnotationValue.Value value = switch (raw) {
                        case Annotation nested -> new AnnotationValue.Value.Nested(getAnnotation(nested));
                        case Class<?> clazz -> new AnnotationValue.Value.ClassRef(nameProvider.getTypeName(clazz));
                        case Enum<?> e -> new AnnotationValue.Value.EnumConstant(
                            nameProvider.getTypeName(e.getDeclaringClass()), e.name());
                        case Object[] arr -> new AnnotationValue.Value.Array(
                            Arrays.stream(arr)
                                .map(item -> (AnnotationValue.Value) switch (item) {
                                    case Annotation nested -> new AnnotationValue.Value.Nested(getAnnotation(nested));
                                    case Class<?> clazz ->
                                        new AnnotationValue.Value.ClassRef(nameProvider.getTypeName(clazz));
                                    case Enum<?> e -> new AnnotationValue.Value.EnumConstant(
                                        nameProvider.getTypeName(e.getDeclaringClass()), e.name());
                                    default -> new AnnotationValue.Value.Literal(item);
                                })
                                .toList());
                        default -> new AnnotationValue.Value.Literal(raw);
                    };
                    return AnnotationValue.of(this, name, value);
                } catch (final IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            });

        return AnnotationTypeUsage.of(this, typeName, annotationValues);
    }

    /**
     * Attempts to obtain the {@link JDKTypeDescriptor} for the specified fully-qualified type name.
     * <p>
     * Tries the unnamed-module lookup first, then retries against each known module — so callers
     * don't need to know which module the type belongs to.
     *
     * @param binaryName the JVM binary type name (e.g. {@code com.example.Outer$Inner})
     * @return the {@link Optional} {@link JDKTypeDescriptor}, or {@link Optional#empty()} if not found
     */
    public Optional<JDKTypeDescriptor> getJDKTypeDescriptor(final String binaryName) {
        final var np = getNameProvider();

        final var unqualified = getTypeDescriptor(np.getTypeNameFromBinary(Optional.empty(), binaryName));
        if (unqualified.isPresent()) {
            return unqualified.map(JDKTypeDescriptor.class::cast);
        }

        return moduleDescriptors()
            .map(md -> getTypeDescriptor(np.getTypeNameFromBinary(Optional.of(md.moduleName()), binaryName)))
            .filter(Optional::isPresent)
            .map(opt -> (JDKTypeDescriptor) opt.get())
            .findFirst();
    }

    /**
     * Returns all {@link TypeReference}s to the given {@link TypeName}.
     *
     * @param typeName the target {@link TypeName}
     * @return a {@link Stream} of {@link TypeReference}s
     */
    public Stream<TypeReference> referencesTo(final TypeName typeName) {
        return typeDescriptors()
            .filter(JDKTypeDescriptor.class::isInstance)
            .map(JDKTypeDescriptor.class::cast)
            .flatMap(td -> referencesIn(td, typeName))
            .distinct();
    }

    /**
     * Returns all {@link TypeReference}s to the given {@link TypeName} with the given {@link ReferenceKind}.
     *
     * @param typeName the target {@link TypeName}
     * @param kind     the {@link ReferenceKind} to match
     * @return a {@link Stream} of {@link TypeReference}s
     */
    public Stream<TypeReference> referencesTo(final TypeName typeName, final ReferenceKind kind) {
        Objects.requireNonNull(kind, "kind");
        return referencesTo(typeName).filter(ref -> ref.kind() == kind);
    }

    private static Stream<TypeReference> referencesIn(final JDKTypeDescriptor td, final TypeName typeName) {
        return Streams.concat(
            extendsRefs(td, typeName),
            implementsRefs(td, typeName),
            fieldRefs(td, typeName),
            td.traits(MethodDescriptor.class).flatMap(md -> methodRefsFor(td, md, typeName)),
            td.traits(ConstructorDescriptor.class).flatMap(cd -> constructorRefsFor(td, cd, typeName)),
            initializerRefs(td, typeName)
        );
    }

    private static Stream<TypeReference> extendsRefs(final JDKTypeDescriptor td, final TypeName typeName) {
        return td.traits(ExtendsTypeDescriptor.class)
            .filter(ext -> typeUsageContains(ext.parentTypeUsage(), typeName))
            .map(_ -> TypeReference.of(td, ReferenceKind.EXTENDS));
    }

    private static Stream<TypeReference> implementsRefs(final JDKTypeDescriptor td, final TypeName typeName) {
        return td.traits(ImplementsTypeDescriptor.class)
            .filter(impl -> typeUsageContains(impl.parentTypeUsage(), typeName))
            .map(_ -> TypeReference.of(td, ReferenceKind.IMPLEMENTS));
    }

    private static Stream<TypeReference> fieldRefs(final JDKTypeDescriptor td, final TypeName typeName) {
        return td.traits(FieldDescriptor.class)
            .filter(fd -> typeUsageContains(fd.type(), typeName))
            .map(fd -> TypeReference.of(td, ReferenceKind.FIELD_TYPE, fd));
    }

    private static Stream<TypeReference> methodRefsFor(final JDKTypeDescriptor td,
                                                       final MethodDescriptor md,
                                                       final TypeName typeName) {
        return Streams.concat(
            Stream.of(md)
                .filter(m -> typeUsageContains(m.returnType(), typeName))
                .map(m -> TypeReference.of(td, ReferenceKind.RETURN_TYPE, m)),
            Stream.of(md)
                .filter(m -> m.formalParameters().anyMatch(p -> typeUsageContains(p.type(), typeName)))
                .map(m -> TypeReference.of(td, ReferenceKind.PARAMETER_TYPE, m)),
            md.getTrait(MethodBodyDescriptor.class)
                .filter(body -> compositeContains(body, typeName))
                .map(_ -> TypeReference.of(td, ReferenceKind.METHOD_BODY, md))
                .stream()
        );
    }

    private static Stream<TypeReference> constructorRefsFor(final JDKTypeDescriptor td,
                                                            final ConstructorDescriptor cd,
                                                            final TypeName typeName) {
        return Streams.concat(
            Stream.of(cd)
                .filter(c -> c.formalParameters().anyMatch(p -> typeUsageContains(p.type(), typeName)))
                .map(c -> TypeReference.of(td, ReferenceKind.PARAMETER_TYPE, c)),
            cd.getTrait(MethodBodyDescriptor.class)
                .filter(body -> compositeContains(body, typeName))
                .map(_ -> TypeReference.of(td, ReferenceKind.METHOD_BODY, cd))
                .stream()
        );
    }

    private static Stream<TypeReference> initializerRefs(final JDKTypeDescriptor td, final TypeName typeName) {
        return td.traits(InitializerBlockDescriptor.class)
            .filter(ib -> compositeContains(ib, typeName))
            .map(_ -> TypeReference.of(td, ReferenceKind.METHOD_BODY));
    }

    private static boolean typeUsageContains(final TypeUsage typeUsage, final TypeName typeName) {
        if (typeUsage instanceof NamedTypeUsage ntu && ntu.typeName().equals(typeName)) {
            return true;
        }
        return typeUsage.traverse(NamedTypeUsage.class)
            .strategy(Strategy.DepthFirst)
            .stream()
            .anyMatch(ntu -> ntu.typeName().equals(typeName));
    }

    private static boolean compositeContains(final Composite composite, final TypeName typeName) {
        // ResolvedMethod.iterator() descends into the live-resolved MethodDescriptor (declaring
        // type, formal parameter types, ...), none of which appear literally at the invocation
        // site. Without this exclusion, e.g. `Other.take(null)` would spuriously "reference"
        // take's formal parameter type even though it never appears in the caller's source.
        // Symbol.Field needs no equivalent exclusion: its iterator() only ever yields the
        // access expression's own declaredType (its javac-resolved static type), never anything
        // from the live FieldDescriptor - see Symbol.Field's descriptor()/parts() split, locked
        // down by MereologyTests#symbolField_partsContainsDeclaredType.
        return composite.traverse(NamedTypeUsage.class)
            .strategy(Strategy.DepthFirst)
            .exclude(ResolvedMethod.class::isInstance)
            .stream()
            .anyMatch(ntu -> ntu.typeName().equals(typeName));
    }

    /**
     * Determines the {@link AccessModifier} based on the specified modifiers.
     *
     * @param modifiers the modifiers
     * @return the {@link Optional} {@link AccessModifier}, {@link Optional#empty()} if none specified
     */
    private Optional<AccessModifier> getAccessModifier(final int modifiers) {
        return Modifier.isPublic(modifiers)
            ? Optional.of(AccessModifier.PUBLIC)
            : (Modifier.isProtected(modifiers)
               ? Optional.of(AccessModifier.PROTECTED)
               : (Modifier.isPrivate(modifiers) ? Optional.of(AccessModifier.PRIVATE) : Optional.empty()));
    }

    /**
     * Determines the {@link Classification} based on the specified modifiers.
     *
     * @param modifiers the modifiers
     * @return the {@link Classification}
     */
    private Classification getClassification(final int modifiers) {
        // include the Classification
        if (Modifier.isAbstract(modifiers)
            || Modifier.isInterface(modifiers)) {

            return Classification.ABSTRACT;
        } else if (Modifier.isFinal(modifiers)) {
            return Classification.FINAL;
        } else {
            return Classification.CONCRETE;
        }
    }

    /**
     * Determines whether {@code classType} is a direct subtype of a {@code sealed} superclass or
     * superinterface, meaning it must itself be declared {@code non-sealed} (having already ruled out
     * {@code final} and {@code sealed}).
     *
     * @param classType the {@link Class} to check
     * @return {@code true} if {@code classType} directly extends or implements a {@code sealed} type
     */
    private static boolean isDirectSubtypeOfSealed(final Class<?> classType) {
        final var superclass = classType.getSuperclass();
        if (superclass != null && superclass.isSealed()) {
            return true;
        }
        return Arrays.stream(classType.getInterfaces()).anyMatch(Class::isSealed);
    }

    /**
     * Determines the {@link FormalParameterDescriptor}s based on the specified {@link Parameter}s.
     *
     * @param parameters the {@link Parameter}s
     * @return a {@link Stream} of {@link FormalParameterDescriptor}s
     */
    private Stream<FormalParameterDescriptor> getFormalParameters(final Parameter... parameters) {

        final var nameProvider = getNameProvider();

        return Streams.of(parameters)
            .map(parameter -> {
                final var parameterName = parameter.isNamePresent()
                    ? Optional.of(nameProvider.getIrreducibleName(parameter.getName()))
                    : Optional.<IrreducibleName>empty();

                final var parameterType = getTypeUsage(parameter);

                final var pd = FormalParameterDescriptor.of(this, parameterName, parameterType);
                if (parameter.isVarArgs()) {
                    pd.addTrait(Varargs.VARARGS);
                }
                if (Modifier.isFinal(parameter.getModifiers())) {
                    pd.addTrait(Final.FINAL);
                }
                return pd;
            });
    }

    /**
     * Initializes the {@link JDKCodeModel} with the foundation types through reflection.
     */
    private void initialize() {

        Stream.of(
                // primitive types
                byte.class,
                short.class,
                int.class,
                long.class,
                float.class,
                double.class,
                boolean.class,
                char.class,

                // wrapper types
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class,
                Boolean.class,
                Character.class,

                // other classes
                String.class,
                Number.class,

                // classes for which we don't require discovery
                Annotation.class,
                Comparable.class,
                Class.class,
                Enum.class,
                ClassLoader.class,
                Object.class,
                Optional.class,
                Record.class,
                Stream.class,
                Throwable.class,
                Exception.class)
            .forEach(javaClass -> {

                final var nameProvider = getNameProvider();

                final var typeName = nameProvider.getTypeName(javaClass);

                final var javaTypeDescriptor = createTypeDescriptor(typeName,
                    JDKTypeDescriptor.supplier(javaClass));

                // include the corresponding Type from which the TypeDescriptor was established
                javaTypeDescriptor.addTrait(new JDKType(javaClass));

                // include the ExtendsTypeDescriptor trait for the super class
                final var superClass = javaClass.getSuperclass();
                if (superClass != null) {
                    javaTypeDescriptor.addTrait(
                        ExtendsTypeDescriptor.of(
                            SpecificTypeUsage.of(this, nameProvider.getTypeName(superClass))));
                }

                // include the ImplementsTypeDescriptor traits for the interfaces
                Streams.of(javaClass.getInterfaces())
                    .forEach(javaInterface -> {
                        javaTypeDescriptor.addTrait(
                            ImplementsTypeDescriptor.of(
                                SpecificTypeUsage.of(this, nameProvider.getTypeName(javaInterface))));
                    });
            });
    }

    private static final ThreadLocal<Map<TypeVariable<?>, TypeVariableUsage>> IN_PROGRESS_TYPE_VARIABLES =
        ThreadLocal.withInitial(HashMap::new);

    static {
        // register this type to be usable for marshalling
        Marshalling.register(JDKCodeModel.class, MethodHandles.lookup());
    }
}
