# Transpiler Implementation Plan

## Overview

A Kotlin-to-C++ transpiler implemented in Kotlin, using ANTLR4 to parse the input Kotlin source. The pipeline is:

```
Kotlin source → ANTLR parse tree → Internal AST → Type resolution → C++ code generation
```

---

## Project Structure

```
kotlin-transpiler/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/
├── gradlew / gradlew.bat
├── src/
│   ├── main/kotlin/transpiler/
│   │   ├── Main.kt                  -- CLI entry point
│   │   ├── Config.kt                -- configurable options (e.g. mutableMapOf target type)
│   │   ├── ast/
│   │   │   └── AST.kt               -- sealed class hierarchy for all AST nodes
│   │   ├── parser/
│   │   │   ├── AntlrToAst.kt        -- ANTLR visitor → internal AST
│   │   │   └── KotlinParser.kt      -- thin wrapper around ANTLR
│   │   ├── typesystem/
│   │   │   ├── Types.kt             -- Kotlin type representations
│   │   │   └── TypeResolver.kt      -- type annotation extraction + auto inference
│   │   └── codegen/
│   │       ├── CodeGenerator.kt     -- main AST walker, emits C++
│   │       ├── StdlibMapper.kt      -- stdlib method/function mappings
│   │       └── Boilerplate.kt       -- emits DynamicBitSet, I/O helpers, Random helpers
│   └── test/kotlin/transpiler/
│       └── TranspilerTest.kt        -- automated test harness
├── grammar/                         -- ANTLR4 Kotlin grammar files (.g4)
├── test-programs/                   -- transpiler-written test .kt programs (+ .in files)
├── examples/                        -- user-provided example programs
├── instructions.md
├── plan.md
└── questions.md
```

---

## Phase 1: Project Setup

- Initialize Gradle project with Kotlin DSL (`build.gradle.kts`)
- Add dependencies:
  - ANTLR4 runtime (`org.antlr:antlr4-runtime`)
  - ANTLR4 tool via the ANTLR Gradle plugin (for grammar code generation)
  - JUnit for tests
- Download the official Kotlin ANTLR4 grammar files (`KotlinLexer.g4`, `KotlinParser.g4`, `UnicodeClasses.g4`) from `https://github.com/Kotlin/kotlin-grammar-antlr4` and place them in `grammar/`
- Configure the ANTLR plugin to generate the lexer/parser into `build/generated-src/antlr/`
- Write a smoke test that parses a trivial Kotlin file and confirms no errors

---

## Phase 2: AST Definition

Define a sealed class hierarchy in `ast/AST.kt` covering all constructs. Key nodes:

**Top-level**
- `Program(declarations: List<TopLevelDecl>)`
- `FunctionDecl(name, params, returnType, body, isOperator, ...)`
- `ClassDecl(name, kind: ClassKind, primaryConstructor, members, superTypes, ...)`
  - `ClassKind`: `Regular`, `Data`, `Sealed`
- `PropertyDecl(name, type, initializer, isMutable)`
- `ObjectDecl(name, members)` *(low priority)*

**Statements**
- `Block(stmts)`
- `LocalVarDecl(name, type?, initializer, isMutable)`
- `DestructuringDecl(names, type?, initializer)`
- `AssignStmt(target, value, op)`
- `IfStmt(condition, thenBranch, elseBranch?)`
- `WhenStmt(subject?, entries)`
- `ForStmt(variable, iterable, body)` — iterable is either a range or a collection
- `WhileStmt(condition, body, isDoWhile)`
- `ReturnStmt(value?, label?)`
- `BreakStmt(label?)` / `ContinueStmt(label?)`
- `ExprStmt(expr)`
- `LabeledStmt(label, stmt)`

**Expressions**
- `LiteralExpr(value, kind: Int/Long/Double/Float/Bool/String/Null/Char)`
- `VarRef(name)`
- `StringTemplate(parts: List<StringPart>)` — parts are either raw strings or expressions
- `BinaryExpr(left, op, right)`
- `UnaryExpr(op, expr, isPrefix)`
- `CallExpr(callee, typeArgs, args, lambdaArg?)`
- `MethodCallExpr(receiver, method, typeArgs, args, lambdaArg?, isSafeCall)`
- `IndexExpr(receiver, index)`
- `LambdaExpr(params, body)`
- `IfExpr(condition, thenBranch, elseBranch)` — when used as expression
- `WhenExpr(subject?, entries)`
- `RangeExpr(start, end, kind: Inclusive/Until/DownTo, step?)`
- `TypeCastExpr(expr, type, isSafe)`
- `NullCheckExpr(expr)` — `!!`
- `ElvisExpr(left, right)` — `?:`
- `IsExpr(expr, type, negated)` — `is` / `!is`
- `PropertyAccess(receiver, name, isSafeCall)`
- `ThisExpr` / `SuperExpr`
- `AnonymousObjectExpr` *(low priority)*

---

## Phase 3: ANTLR → AST

Implement an ANTLR visitor in `AntlrToAst.kt` that walks the generated parse tree and builds the internal AST.

Key considerations:
- Handle the distinction between statement-position and expression-position `if`/`when`
- Normalize lambda trailing syntax (Kotlin allows `f(x) { body }` — merge lambda into args)
- Resolve `it` as the implicit lambda parameter name
- Propagate labels to adjacent statements

This phase is the most labor-intensive; build it incrementally, feature by feature.

---

## Phase 4: Type System

**Type representation** (`typesystem/Types.kt`):
```
KotlinType
  PrimitiveType(name)        -- Int, Long, Double, Float, Boolean, String, Char
  UnitType
  NullableType(inner)
  GenericType(name, args)    -- List<T>, Map<K,V>, etc.
  FunctionType(params, ret)
  ArrayType(element)         -- IntArray, LongArray, Array<T>
  AutoType                   -- inferred, will emit `auto`
```

**C++ type mapping** (`TypeResolver.kt`):

| Kotlin | C++ |
|--------|-----|
| `Int` | `int` |
| `Long` | `long long` |
| `Double` | `double` |
| `Float` | `float` |
| `Boolean` | `bool` |
| `Char` | `char` |
| `String` | `string` |
| `Unit` | `void` |
| `List<T>` / `MutableList<T>` / `Array<T>` | `vector<T>` |
| `IntArray` / `LongArray` / etc. | `vector<int>` / `vector<long long>` / etc. |
| `ArrayDeque<T>` | `deque<T>` |
| `TreeSet<T>` | `set<T>` |
| `TreeMap<K,V>` | `map<K,V>` |
| `mutableMapOf` result | `map<K,V>` (configurable → `unordered_map`) |
| `Stack<T>` | `stack<T>` |
| `PriorityQueue<T>` | `priority_queue<T>` |
| `BitSet` | `DynamicBitSet` (custom) |
| `Pair<A,B>` | `pair<A,B>` |
| `StringBuilder` | `ostringstream` |
| `NullableType(T)` | `T*` (or `optional<T>` for value types) |

**Inference strategy**: emit `auto` for local variable declarations where the Kotlin type is not annotated. For uninitialized declarations, class members, and function parameters, the Kotlin type annotation is always present and is used directly.

---

## Phase 5: Code Generation — Core

Implement `CodeGenerator.kt` as a recursive AST walker. Emit C++ to a `StringBuilder`/output stream with indentation tracking.

**File structure emitted**:
```cpp
#include <bits/stdc++.h>
using namespace std;
// [DynamicBitSet and other boilerplate if needed]
// [top-level declarations / global variables]
// [top-level functions]
int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    // [main body]
}
```

**Core constructs**:

- `val`/`var` → `const auto`/`auto` (or typed if annotation present), with structured bindings for destructuring: `auto [a, b] = ...`
- `if` statement → `if (...) { } else { }`
- `if` expression → ternary `? :` for simple cases; immediately-invoked lambda `[&]() { ... }()` for block bodies
- `when` statement/expression → chain of `if/else if` (or `switch` when subject is an integer with constant branches)
- `for (x in range)` → `for (int x = start; x <= end; x++)` (special-cased for `..`, `until`, `downTo`, `step`)
- `for (x in collection)` → `for (auto& x : collection)`
- `for ((k, v) in map)` → `for (auto& [k, v] : map)`
- `while` / `do-while` → direct translation
- `return` → `return`
- `break`/`continue` with labels → `goto` to a label placed after the target loop
- `repeat(n) { i -> ... }` → `for (int i = 0; i < n; i++) { ... }`
- Functions → free C++ functions; nested functions → local lambdas assigned to `auto`
- Operator overloading → `operator+` etc. as member functions
- Classes → C++ classes with constructor, members, methods
- Data classes → C++ structs with a generated `operator<` and `operator==`
- Sealed classes → abstract base class with virtual methods + subclasses

---

## Phase 6: Code Generation — Expressions

**String templates**: `"Hello $name, age ${age+1}"` → emit as a series of `+` concatenations or via an immediately-invoked ostringstream lambda for complex cases.

**Null safety**:
- `x?.foo()` → `(x == nullptr ? nullptr : x->foo())`
- `x ?: y` → `(x != nullptr ? x : y)`
- `x!!` → `x` (no-op; memory safety not required)

**Lambdas**: `{ x -> expr }` → `[&](auto x) { return expr; }`. For lambdas used as comparators or higher-order function arguments, capture by reference (`[&]`).

**`it`**: the implicit lambda parameter; rename to `_it` internally to avoid any C++ keyword conflicts.

**Labeled `return@label`**: for lambdas passed to `forEach`/`map`/`filter`/etc., translate `return@label` to `continue` within the emitted loop.

**Ranges as values** (not in `for`): emit a small `range_iterator` helper or handle by expanding to a `vector<int>` at the call site (only needed if the range is iterated or converted, not indexed).

---

## Phase 7: Code Generation — Standard Library

Implement `StdlibMapper.kt` to translate method calls on known types.

### Collections (vector)
| Kotlin | C++ |
|--------|-----|
| `xs.size` | `(int)xs.size()` |
| `xs[i]` | `xs[i]` |
| `xs.add(x)` | `xs.push_back(x)` |
| `xs.add(i, x)` | `xs.insert(xs.begin() + i, x)` |
| `xs.removeAt(i)` | `xs.erase(xs.begin() + i)` |
| `xs.remove(x)` | `xs.erase(find(xs.begin(), xs.end(), x))` |
| `xs.first()` | `xs.front()` |
| `xs.last()` | `xs.back()` |
| `xs.lastIndex` | `(int)xs.size() - 1` |
| `xs.clear()` | `xs.clear()` |
| `xs.addAll(ys)` | `xs.insert(xs.end(), ys.begin(), ys.end())` |
| `xs.isEmpty()` | `xs.empty()` |
| `xs.subList(a, b)` | `vector<T>(xs.begin()+a, xs.begin()+b)` |
| `xs.map { f }` | emit loop building a new vector |
| `xs.filter { f }` | emit loop with conditional push_back |
| `xs.joinToString(sep)` | emit loop with `cout << sep` between elements (special-cased for print context); otherwise build a string |
| `listOf(...)` | `vector<T>{...}` |
| `mutableListOf(...)` | `vector<T>{...}` |
| `MutableList(n) { f }` | `vector<T>` built with a loop |

### Sort (12 variants)
| Kotlin | C++ |
|--------|-----|
| `xs.sort()` | `sort(xs.begin(), xs.end())` |
| `xs.sortDescending()` | `sort(xs.begin(), xs.end(), greater<T>())` |
| `xs.sortBy { it.x }` | `sort(xs.begin(), xs.end(), [](const T& a, const T& b){ return a.x < b.x; })` |
| `xs.sortByDescending { it.x }` | same but `>` |
| `xs.sortWith(cmp)` | `sort(xs.begin(), xs.end(), cmp)` |
| `xs.sorted()` | copy + sort, return |
| `xs.sortedDescending()` | copy + sort descending, return |
| `xs.sortedBy { it.x }` | copy + sortBy |
| `xs.sortedByDescending { it.x }` | copy + sortByDescending |
| `xs.sortedWith(cmp)` | copy + sortWith |
| `compareBy { it.x }` | emit a lambda comparator |
| `compareValuesBy(a, b) { it.x }` | emit inline comparison expression |

### TreeSet (`set<T>`)
| Kotlin | C++ |
|--------|-----|
| `add(x)` | `insert(x)` |
| `remove(x)` | `erase(x)` |
| `contains(x)` | `count(x) > 0` |
| `size` | `size()` |
| `first()` | `*begin()` |
| `last()` | `*rbegin()` |
| `lower(x)` | `*prev(lower_bound(x))` |
| `floor(x)` | `*(iterator to x or prev(lower_bound(x)))` |
| `higher(x)` | `*upper_bound(x)` |
| `ceiling(x)` | `*lower_bound(x)` |

### TreeMap (`map<K,V>`)
| Kotlin | C++ |
|--------|-----|
| `put(k, v)` / `set` | `[k] = v` |
| `get(k)` / `[]` | `at(k)` or `[k]` |
| `remove(k)` | `erase(k)` |
| `contains(k)` / `containsKey` | `count(k) > 0` |
| `size` | `size()` |
| `lowerEntry(k)` | `*prev(lower_bound(k))` → returns `pair<K,V>` |
| `floorEntry(k)` | similar |
| `higherEntry(k)` | `*upper_bound(k)` |
| `ceilingEntry(k)` | `*lower_bound(k)` |

### Other collections
- `ArrayDeque`: `push_back`/`push_front`/`pop_back`/`pop_front`/`first()`/`last()`/`[]`
- `Stack`: `push`/`pop`/`peek` → `top()`/`pop()`
- `PriorityQueue`: `add` → `push`, `poll` → `pop`/`top`, `peek` → `top`
- `Pair`: `.first`/`.second` directly (same names in C++)

### Math & conversions
- `max(a, b)` / `min(a, b)` / `maxOf` / `minOf` → `max` / `min` (already in `<algorithm>` via `bits/stdc++.h`)
- `abs(x)` → `abs(x)`
- `x.toInt()` / `x.toLong()` / `x.toDouble()` / `x.toFloat()` → C-style cast `(int)x` etc.
- `x.toString()` → `to_string(x)`
- `x.toChar()` → `(char)x`

### I/O
- `nextToken()` → `({ string _t; cin >> _t; _t; })`  — or define as a function in boilerplate
- `nextInt()` / `nextLong()` / `nextDouble()` → `({ T _t; cin >> _t; _t; })` — defined in boilerplate
- `nextLine()` → `({ string _t; getline(cin, _t); _t; })` — defined in boilerplate
- `print(x)` → `cout << x`
- `println(x)` → `cout << x << '\n'`
- `println(xs.joinToString(" "))` → special-cased to a loop: `for (int i = 0; i < xs.size(); i++) { if (i) cout << ' '; cout << xs[i]; } cout << '\n';`

### StringBuilder
```kotlin
val sb = StringBuilder()
sb.append(x)
sb.toString()
```
→
```cpp
ostringstream sb;
sb << x;
sb.str()
```

### Random
- `Random.nextInt(n)` → define a global `mt19937 rng(chrono::steady_clock::now().time_since_epoch().count())` in boilerplate; emit `uniform_int_distribution<int>(0, n-1)(rng)`
- `Random.nextDouble()` → `uniform_real_distribution<double>(0.0, 1.0)(rng)`

---

## Phase 8: Boilerplate / Runtime Helpers

`Boilerplate.kt` generates C++ code that is prepended to the output file when needed. Components:

### I/O helpers
```cpp
int nextInt() { int x; cin >> x; return x; }
long long nextLong() { long long x; cin >> x; return x; }
double nextDouble() { double x; cin >> x; return x; }
string nextToken() { string x; cin >> x; return x; }
string nextLine() { string x; getline(cin, x); return x; }
```

### DynamicBitSet
A C++ class backed by `vector<uint64_t>` implementing the Java `BitSet` API:
- `set(i)`, `clear(i)`, `get(i)`, `flip(i)`, `flip()`
- `and_(other)`, `or_(other)`, `xor_(other)`, `andNot(other)`
- `cardinality()` using `__builtin_popcountll`
- `nextSetBit(from)`, `nextClearBit(from)`
- `size()`, `length()`, `isEmpty()`
- Bitwise operators `&=`, `|=`, `^=`

### Random
```cpp
mt19937 rng(chrono::steady_clock::now().time_since_epoch().count());
```

Each boilerplate component is only emitted when the transpiler detects it is needed (e.g., `DynamicBitSet` only if `BitSet` is used).

---

## Phase 9: Testing Infrastructure

### Test program conventions
Each test lives in `test-programs/`:
```
test-programs/
  basic_arithmetic/
    test.kt
    test.in      (optional, if program reads stdin)
    test.out     (optional, expected output — if absent, Kotlin output is used as ground truth)
```

### Test harness (`TranspilerTest.kt`)
For each test directory:
1. Run the transpiler on `test.kt` → `test.cpp`
2. Compile `test.cpp` with `g++ -O2 -std=c++20 -o test_cpp`
3. Compile `test.kt` with `kotlinc test.kt -include-runtime -d test.jar`
4. Run both with `test.in` (if present) on stdin
5. Assert stdout matches between the two runs
6. Report pass/fail per test

The harness is a JUnit test class; `./gradlew test` runs it.

### Test coverage plan
Write test programs covering:
- Basic arithmetic and I/O
- String templates and StringBuilder
- If/when as expressions
- For/while loops, ranges, repeat
- Lambdas (map, filter, sortBy, etc.)
- All collection types and their methods
- All 12 sort variants
- TreeSet/TreeMap with lower/floor/higher/ceiling
- Null safety operators
- Destructuring (pairs, map entries)
- Labeled break/continue
- Classes (regular, data, sealed)
- Operator overloading
- Nested functions
- Global variables and top-level functions
- DynamicBitSet operations
- PriorityQueue with comparator
- Random usage

---

## Phase 10: Documentation

`README.md` covering:
- Prerequisites (kotlinc, g++)
- Build instructions: `./gradlew build`
- Usage: `./gradlew run --args="input.kt output.cpp"` (or equivalent)
- Running tests: `./gradlew test`
- Configuration options (e.g., `mutableMapOf` target type)

---

## Implementation Order

1. Project setup + ANTLR grammar integration (Phase 1)
2. AST definition (Phase 2)
3. ANTLR → AST, starting with simple programs (Phase 3)
4. Type system + mapping (Phase 4)
5. Code generation — core statements and expressions (Phase 5 + 6)
6. Standard library mappings (Phase 7)
7. Boilerplate helpers (Phase 8)
8. Expand ANTLR → AST to cover all features as code generation is added
9. Testing infrastructure + test programs (Phase 9)
10. Documentation (Phase 10)

Phases 3, 5, 6, 7, and 8 proceed in tandem — each new language/library feature requires changes to the parser (Phase 3), type system (Phase 4), and code generator (Phases 5–8) simultaneously.
