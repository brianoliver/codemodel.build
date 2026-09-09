package build.codemodel.dependency.injection;

/*-
 * #%L
 * Dependency Injection
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

import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

/**
 * A throwaway named module, {@code foreign.provider}, synthesized with the {@link ClassFile} API and
 * defined into a child {@link ModuleLayer} for tests that need genuinely inaccessible members.
 *
 * <p>The module exports only {@code foreign.provider.api}; everything interesting lives in the
 * non-exported, non-open package {@code foreign.provider.internal}, so this (unnamed) test module
 * cannot enable deep reflection on it and {@link java.lang.reflect.AccessibleObject#trySetAccessible()}
 * returns {@code false} even for {@code public} members. The exported {@code foreign.provider.api.Factory}
 * hands back instances whose classes the test cannot construct directly.
 *
 * <p>The {@code @Provides} / {@code @Inject} annotation types deliberately stay on the classpath rather
 * than in {@code foreign.provider}: reflective annotation lookup resolves them through the class loader
 * without a module-readability check, so the framework still recognizes the members even though
 * {@code foreign.provider} does not read the classpath.
 */
final class ForeignModule {

    private static final String MODULE_NAME = "foreign.provider";
    private static final String API_PACKAGE = "foreign.provider.api";
    private static final String INTERNAL_PACKAGE = "foreign.provider.internal";

    private static final String FACTORY = API_PACKAGE + ".Factory";
    private static final String PROVIDER = INTERNAL_PACKAGE + ".InaccessibleProvider";
    private static final String FIELD_INJECTABLE = INTERNAL_PACKAGE + ".InaccessibleFieldInjectable";
    private static final String METHOD_INJECTABLE = INTERNAL_PACKAGE + ".InaccessibleMethodInjectable";
    private static final String CONSTRUCTOR_INJECTABLE = INTERNAL_PACKAGE + ".InaccessibleConstructorInjectable";

    private final ModuleLayer layer;

    private ForeignModule(final ModuleLayer layer) {
        this.layer = layer;
    }

    /**
     * Synthesizes the module under {@code moduleDir} and defines it into a child layer of the boot layer,
     * using this class's class loader as the parent.
     *
     * @param moduleDir a {@link org.junit.jupiter.api.io.TempDir}-managed directory to write the module into
     * @return the loaded {@link ForeignModule}
     */
    static ForeignModule define(final Path moduleDir) throws IOException {
        final var classFile = ClassFile.of();
        final var providesCd = ClassDesc.of(Provides.class.getName());
        final var injectCd = ClassDesc.of("jakarta.inject.Inject");
        final var providerCd = ClassDesc.of(PROVIDER);
        final var fieldInjectableCd = ClassDesc.of(FIELD_INJECTABLE);
        final var methodInjectableCd = ClassDesc.of(METHOD_INJECTABLE);
        final var constructorInjectableCd = ClassDesc.of(CONSTRUCTOR_INJECTABLE);

        final var moduleInfo = classFile.buildModule(
            ModuleAttribute.of(ModuleDesc.of(MODULE_NAME), builder -> {
                builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
                builder.exports(PackageDesc.of(API_PACKAGE), 0);
            }));

        final var provider = classFile.build(providerCd, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            defaultConstructor(builder);
            builder.withMethod("greeting", MethodTypeDesc.of(CD_String), ClassFile.ACC_PUBLIC, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(providesCd)));
                method.withCode(code -> code
                    .ldc("Hello from an inaccessible provider")
                    .areturn());
            });
        });

        final var fieldInjectable = classFile.build(fieldInjectableCd, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            defaultConstructor(builder);
            builder.withField("dependency", CD_String, field -> field
                .withFlags(ClassFile.ACC_PUBLIC)
                .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(injectCd))));
        });

        final var methodInjectable = classFile.build(methodInjectableCd, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            defaultConstructor(builder);
            builder.withMethod("setDependency", MethodTypeDesc.of(CD_void, CD_String), ClassFile.ACC_PUBLIC, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(injectCd)));
                method.withCode(code -> code.return_());
            });
        });

        final var constructorInjectable = classFile.build(constructorInjectableCd, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            builder.withMethod(INIT_NAME, MethodTypeDesc.of(CD_void, CD_String), ClassFile.ACC_PUBLIC, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(injectCd)));
                method.withCode(code -> code
                    .aload(0)
                    .invokespecial(CD_Object, INIT_NAME, MTD_void)
                    .return_());
            });
        });

        final var factory = classFile.build(ClassDesc.of(FACTORY), builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            defaultConstructor(builder);
            factoryMethod(builder, "newProvider", providerCd);
            factoryMethod(builder, "newFieldInjectable", fieldInjectableCd);
            factoryMethod(builder, "newMethodInjectable", methodInjectableCd);
        });

        writeClass(moduleDir, "module-info", moduleInfo);
        writeClass(moduleDir, "foreign/provider/api/Factory", factory);
        writeClass(moduleDir, "foreign/provider/internal/InaccessibleProvider", provider);
        writeClass(moduleDir, "foreign/provider/internal/InaccessibleFieldInjectable", fieldInjectable);
        writeClass(moduleDir, "foreign/provider/internal/InaccessibleMethodInjectable", methodInjectable);
        writeClass(moduleDir, "foreign/provider/internal/InaccessibleConstructorInjectable", constructorInjectable);

        final var bootLayer = ModuleLayer.boot();
        final var configuration = bootLayer.configuration()
            .resolve(ModuleFinder.of(moduleDir), ModuleFinder.of(), Set.of(MODULE_NAME));
        final var layer = bootLayer.defineModulesWithOneLoader(configuration, ForeignModule.class.getClassLoader());

        return new ForeignModule(layer);
    }

    /**
     * A fresh instance of the class carrying a public no-arg {@code @Provides String greeting()} method
     * in the non-exported package.
     *
     * @return the provider instance
     */
    Object newProvider() {
        return fromFactory("newProvider");
    }

    /**
     * A fresh instance of the class carrying a public {@code @Inject String dependency} field in the
     * non-exported package.
     *
     * @return the field-injectable instance
     */
    Object newFieldInjectable() {
        return fromFactory("newFieldInjectable");
    }

    /**
     * A fresh instance of the class carrying a public {@code @Inject void setDependency(String)} method
     * in the non-exported package.
     *
     * @return the method-injectable instance
     */
    Object newMethodInjectable() {
        return fromFactory("newMethodInjectable");
    }

    /**
     * The class in the non-exported package whose only constructor is a public {@code @Inject (String)}
     * constructor.
     *
     * <p>Unlike the other injectables there is no {@code Factory} method for this one: the whole point is
     * that it has no no-arg constructor, so the test hands the {@link Class} to {@code context.create(...)}
     * and lets the framework attempt the inaccessible {@code @Inject} constructor itself.
     *
     * @return the constructor-injectable class
     */
    Class<?> constructorInjectableClass() {
        return load(CONSTRUCTOR_INJECTABLE);
    }

    private Object fromFactory(final String method) {
        try {
            return load(FACTORY).getMethod(method).invoke(null);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Could not invoke " + FACTORY + "." + method + "()", e);
        }
    }

    private Class<?> load(final String binaryName) {
        try {
            return this.layer.findLoader(MODULE_NAME).loadClass(binaryName);
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Could not load " + binaryName + " from " + MODULE_NAME, e);
        }
    }

    private static void defaultConstructor(final ClassBuilder builder) {
        builder.withMethodBody(INIT_NAME, MTD_void, ClassFile.ACC_PUBLIC, code -> code
            .aload(0)
            .invokespecial(CD_Object, INIT_NAME, MTD_void)
            .return_());
    }

    private static void factoryMethod(final ClassBuilder builder, final String name, final ClassDesc target) {
        builder.withMethodBody(name, MethodTypeDesc.of(CD_Object),
            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code
                .new_(target)
                .dup()
                .invokespecial(target, INIT_NAME, MTD_void)
                .areturn());
    }

    private static void writeClass(final Path moduleDir, final String binaryPath, final byte[] bytecode)
        throws IOException {

        final var target = moduleDir.resolve(binaryPath + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytecode);
    }
}
