# Transpiler Development Journal

## Session 1 — 2026-04-04

### Goals for this session
Implement the first complete iteration of the Kotlin→C++ transpiler per `plan.md`.

### Setup
- Installed `gradle 9.4.1` via Homebrew (gradle was not already present).
- Used `gradle wrapper --gradle-version 8.8` to pin the project to Gradle 8.8, which is
  well-tested with the Kotlin JVM plugin.
- Confirmed `kotlinc 2.3.20` and `g++ (Apple Clang 16)` are both available.

### Architecture decisions made
- Used ANTLR4 (`4.13.2`) with the official Kotlin grammar from
  `github.com/Kotlin/kotlin-grammar-antlr4`.
- ANTLR generates Java source into `build/generated-src/antlr/main/transpiler/parser/generated/`;
  this directory is added to the `java` source set so it compiles alongside Kotlin.
- `MethodCallExpression` initially went into `CodeGenerator.kt` but had to be moved to
  `ast/AST.kt` because sealed class subclasses must be in the same Kotlin package.
- Chose immediately-invoked lambdas (`[&]() { ... }()`) as the translation target for
  Kotlin expressions that require multiple C++ statements (if-expression with blocks,
  when-expression, map/filter, etc.). This is idiomatic modern C++ and avoids needing
  a separate "lift to statement" pass.
- `auto` is used for inferred local variable types and function return types; explicit
  Kotlin type annotations are used for class members and function parameters.
- `DynamicBitSet` is a custom C++ struct backed by `vector<uint64_t>` that provides the
  full Java `BitSet` API. It is emitted as boilerplate only when `BitSet` is used.

### Files created
```
src/main/kotlin/transpiler/
  ast/AST.kt               — full sealed-class AST hierarchy
  typesystem/TypeResolver.kt — Kotlin→C++ type mapping
  codegen/Boilerplate.kt   — I/O helpers, DynamicBitSet, Random boilerplate
  codegen/StdlibMapper.kt  — stdlib method/function call mappings
  codegen/CodeGenerator.kt — main AST walker / C++ emitter
  parser/KotlinTranspilerParser.kt — ANTLR wrapper
  Config.kt                — configuration options
  Main.kt                  — CLI entry point
```

### Key design notes

#### Type mapping
`Int`→`int`, `Long`→`long long`, `Double`→`double`, `String`→`std::string`.
Nullable types use `std::optional<T>` for primitives and `T*` for reference types.
`auto` is used wherever Kotlin infers the type from a right-hand-side expression.

#### I/O
`print`/`println` → `cout <<`.  The `println(xs.joinToString(" "))` pattern is
detected and emitted as a loop directly writing to `cout`, avoiding building an
intermediate string.

#### Labeled break/continue
Kotlin's labeled `break@label` and `continue@label` have no direct equivalent in C++.
The translation uses `goto` to a label placed immediately after (for break) or at the
end of the body (for continue) of the target loop.

#### Lambdas
All lambdas use `[&]` capture-by-reference since CP programs are typically
single-threaded and don't return lambdas beyond the local scope.  The implicit Kotlin
`it` parameter is renamed to `_it` in the emitted C++ to avoid any accidental collision.

#### Sealed classes
Emitted as C++ abstract base classes with `virtual` methods and concrete subclasses that
use `public` inheritance. `dynamic_cast` is used for type checks (`is`/`!is` and `when`
type conditions).

### Difficulties / open issues
- The ANTLR visitor (`AntlrToAst.kt`) is being written after the grammar files were
  downloaded.  The official Kotlin grammar is very large (~3000 lines across three files).
  The visitor only needs to handle the constructs described in `plan.md`; unrecognized
  rules throw `UnsupportedOperationException` with the rule name so gaps are easy to spot
  during testing.
- `genWhenEntries` currently has an unused return type (`String?`) left over from an
  earlier design where it could be called as an expression generator. This will be cleaned
  up once the expression path is confirmed working.
- Some edge cases in `StdlibMapper` (e.g. `TreeSet.lower`/`floor` with empty sets) emit
  a `/* no lower */` comment; these will panic at runtime if hit. Proper handling requires
  either `optional` return types or contract-based preconditions.  For CP usage the caller
  is expected to check before calling.

### Next steps
1. Write `AntlrToAst.kt` (ANTLR parse-tree visitor → internal AST).
2. Write the test harness (`TranspilerTest.kt`).
3. Write test programs covering all features.
4. Attempt a first build and fix compile errors.
5. Run tests and iterate.
