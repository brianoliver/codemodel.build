# codemodel.build

## Codebase Overview

`codemodel.build` is a language-agnostic Java code model framework (Workday, Inc.) that provides a structured, serializable representation of software systems. A `CodeModel` can be populated from compiled classes (via reflection) or `.java` source files (via javac), then enriched, validated, and compiled through a plugin pipeline. It is the foundation for annotation processors and code generation tools.

**Stack:** Java 25, Maven multi-module (`${revision}` versioning, currently `0.27.1-SNAPSHOT`; `build.base` 0.30.1), Jakarta Inject, custom marshalling framework (`build.base:base-marshalling`), operator-precedence parser via `build.base:base-parsing`, JSR-330 DI implementation.

**Structure** (module directories are `codemodel-*`; Java packages are `build.codemodel.*` — mostly unchanged by the rename, except DI moved to `build.codemodel.dependency.injection`):
- `codemodel-foundation` — core `CodeModel` registry, `TypeDescriptor`/`TypeUsage`/`Trait` system, naming, marshalling, transport transformers
- `codemodel-expression` — expression AST nodes + operator-precedence parser hooks (parser wiring is test-only, via external `base-parsing`)
- `codemodel-hierarchical` — type hierarchy (parents, ancestors, descendants, assignability, diamond detection)
- `codemodel-imperative` — paradigm-neutral statement AST nodes (Block, If, While, Return, Assignment)
- `codemodel-objectoriented` — OOP traits (classes/interfaces, fields, methods, constructors, modifiers); `MethodDescriptor.signature()` vs `overrideKey()`; `DeclarationOrder`
- `codemodel-jdk` — **reflection-based** `JDKCodeModel` + shared descriptor/expression/statement trait vocabulary + `referencesTo()` API + `JDKModuleDescriptor` (JPMS: text scanner + ClassFile-API extraction) + `TypeUsages.isAssignable` (JLS wildcard/generic subtyping)
- `codemodel-jdk-populator` — **javac source-parsing pipeline** (extracted from `codemodel-jdk`): `JdkInitializer`, shared `TypeMirrorResolver` (also used by the annotation processor), expression/statement converters, incremental `rescan()`, `SourceLocation` at `build.codemodel.jdk.populator.descriptor`
- `codemodel-dependency-injection` — custom JSR-330 DI built on `JDKCodeModel` introspection; `TypeLiteral`, wildcard-bearing + qualified `@Provides` resolution
- `codemodel-framework` — pipeline interfaces (Initializer, Enricher, TypeChecker, Compiler, Completer)
- `codemodel-framework-builder` — concrete `FrameworkBuilder` + `InternalFramework` (4-stage pipeline; `CodeModel` re-bound per stage)
- `codemodel-jdk-annotation-discovery` — `AnnotationDiscovery` SPI + `@Discoverable`
- `codemodel-jdk-annotation-processor` — `javax.annotation.processing.Processor` driving the full pipeline (third population path; delegates `TypeMirror` resolution to `codemodel-jdk-populator`)

For detailed architecture, module-by-module analysis, data flows, and navigation guide, see [docs/CODEBASE_MAP.md](docs/CODEBASE_MAP.md).
