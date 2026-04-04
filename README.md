# Kotlin-to-C++ Transpiler

A transpiler that converts Kotlin source files into equivalent C++ code, designed for competitive programming. The generated C++ targets C++20 and uses `#include <bits/stdc++.h>`.

## Prerequisites

- **JDK 17+** (for building and running the transpiler)
- **g++** or **clang++** with C++20 support (for compiling the generated C++)
- **kotlinc** (optional, for running tests that compare Kotlin and C++ output)

## Build

```bash
./gradlew build
```

## Usage

```bash
# Transpile a Kotlin file (output defaults to input name with .cpp extension)
./gradlew run --args="input.kt"

# Specify output file
./gradlew run --args="input.kt -o output.cpp"

# Output to stdout
./gradlew run --args="input.kt -o -"
```

### Options

| Flag | Description |
|------|-------------|
| `-o`, `--output <file>` | Output file (default: input with `.cpp` extension) |
| `--unordered-map` | Use `std::unordered_map` for `mutableMapOf()` (default: `std::map`) |
| `-h`, `--help` | Show help |

### Compiling the generated C++

If you're using Apple Clang (macOS default), you'll need the included `bits/stdc++.h` shim:

```bash
g++ -O2 -std=c++20 -I include output.cpp -o output
```

If you have GCC installed, you can compile without the `-I` flag.

## Running Tests

```bash
./gradlew test
```

Tests live in `test-programs/`. Each test directory contains:
- `test.kt` — the Kotlin source
- `test.in` — optional stdin input
- `test.out` — optional expected output (if absent, Kotlin output is used as ground truth)

The test harness transpiles each program, compiles both the Kotlin and C++ versions, runs them with the same input, and asserts identical output.

## Supported Features

### I/O
- `nextInt()`, `nextLong()`, `nextDouble()`, `nextToken()`, `nextLine()`
- `print()`, `println()`
- `println(xs.joinToString(" "))` is special-cased to an efficient loop

### Language Features
- Functions (top-level, nested, operator overloading)
- Lambdas (`map`, `filter`, `sortBy`, implicit `it` parameter)
- Classes, data classes, sealed classes
- `if`/`when` as both statements and expressions
- `for` loops with ranges (`..`, `until`, `downTo`, `step`)
- `while`/`do-while` loops, `repeat`
- Labeled `break`/`continue`
- Destructuring declarations
- String templates
- Null safety operators (`?.`, `?:`, `!!`)
- Type casts (`as`, `as?`)

### Standard Library
- `List`, `MutableList`, `listOf`, `mutableListOf`
- `Map`, `MutableMap`, `mutableMapOf`
- `TreeSet`, `TreeMap` (with `lower`, `higher`, `floor`, `ceiling`)
- `ArrayDeque`, `Stack`, `PriorityQueue`
- `Pair`
- `StringBuilder`
- `BitSet` (via custom `DynamicBitSet` implementation)
- `Random`
- All 12 sort variants (`sort`, `sorted`, `sortBy`, `sortedBy`, `sortDescending`, etc.)
- Math functions (`max`, `min`, `abs`, `gcd`, etc.)
- Casting functions (`toInt`, `toLong`, `toString`, etc.)
- `compareBy`, `compareByDescending`

## Configuration

The transpiler uses `std::map` for `mutableMapOf()` by default. Pass `--unordered-map` to use `std::unordered_map` instead (faster average case, but vulnerable to worst-case input in competitive programming).
