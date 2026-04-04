# Questions about the Transpiler Spec

## Architecture

1. **Parsing strategy**: Should we use an existing Kotlin grammar/parser (e.g., ANTLR grammar for Kotlin, or the Kotlin compiler's own PSI/compiler API), or write a parser from scratch? The Kotlin compiler's embedded parser would be the most robust option but is a heavyweight dependency; ANTLR with a community Kotlin grammar is a middle ground.

Answer: Let's go with ANTLR. If I need to take some action to install it then you should give me instructions on how to do this.

2. **C++ standard**: What C++ standard should the output target? C++17 is the safest for most competitive programming judges, but C++20/23 offer useful features (e.g., `std::ranges`). Should we use `#include <bits/stdc++.h>` as the sole include (common in CP), or explicit headers?

Answer: Let's target C++20. It's fine to just include `bits/stdc++`.

3. **Type inference**: Kotlin infers types heavily (e.g., `val x = listOf(1, 2, 3)`). We'll need to resolve the inferred type to emit correct C++ declarations. Should we build a type inference pass, or is it acceptable to require explicit type annotations in input programs when inference would be ambiguous?

Answer: This is a good question. I am hoping that use of `auto` can handle most of this, but if not we should discuss this more.

## Kotlin Language Features

4. **String templates**: Should `"Hello $name"` and `"Hello ${expr}"` be supported? These are very common in Kotlin and would need transpilation to something like `"Hello " + name` or a formatted string in C++.

5. **`if`/`when` as expressions**: In Kotlin, `if` and `when` can be used as expressions (e.g., `val x = if (cond) a else b`). Should these be supported, and if so, should they be transpiled to the ternary operator `? :` or to something else?

6. **Null safety operators**: Should `?.` (safe call), `?:` (Elvis operator), and `!!` (non-null assertion) be supported? These are idiomatic Kotlin but imply nullable types.

7. **Extension functions**: Are extension functions (e.g., `fun List<Int>.mySum() = ...`) used in your CP code? These are very common in Kotlin and require special handling (transpile to free functions taking the receiver as the first parameter, or similar).

8. **Scope functions**: Should `let`, `run`, `apply`, `also`, and `with` be supported? These are common idioms, especially `let` for null-safety chains and `apply` for object initialization.

9. **`repeat`**: Should `repeat(n) { ... }` be supported? This is very commonly used in CP Kotlin as a substitute for `for (i in 0 until n)`.

10. **Destructuring declarations**: Should `val (a, b) = pair` and destructuring in `for` loops (e.g., `for ((k, v) in map)`) be supported?

11. **Labeled returns and breaks**: Kotlin allows `return@label` from lambdas and labeled `break`/`continue` in loops. Should these be handled?

12. **`object` declarations and companion objects**: Should top-level `object` singletons and `companion object` blocks inside classes be supported?

13. **Operator overloading**: Should custom `operator fun` definitions (e.g., `operator fun plus(other: T)`) be supported?

14. **Default parameter values and named arguments**: Should functions with default parameter values (e.g., `fun f(x: Int = 0)`) and call sites using named arguments (e.g., `f(x = 5)`) be supported?

15. **`Array` and primitive arrays**: Should `Array<T>`, `IntArray`, `LongArray`, `BooleanArray`, etc. be supported in addition to `List`? These are more common in performance-sensitive CP code.

16. **`StringBuilder`**: Should `StringBuilder` be supported (transpiled to `std::string` with appends, or similar)?

17. **`vararg`**: Should variadic function parameters be supported?

Answer:

The following should definitely be supported: 4, 5, 6, 9, 10, 11, 13, 15, 16

Others may be if possible but it's not a priority.

## Standard Library / Collections

18. **`mutableMapOf`**: Should this map to `std::unordered_map` (O(1) avg) or `std::map` (O(log n), ordered)? In CP the choice matters for correctness and performance. (Note: `TreeMap` is already listed as mapping to `std::map`.)

Answer: Maybe you can make this configurable. By default I would say `map` since `unordered_map` can be slow in the worst case.

19. **`BitSet`**: Java's `BitSet` is dynamically sized, while `std::bitset` requires a compile-time size. Should we map to `std::vector<bool>` instead, or require the user to annotate the size somewhere?

Answer: BitSet needs to be able to handle bitwise operations (and, or, flip all bits) etc. If vector<bool> can handle all of these then that's fine; otherwise we need another option. Let's discuss this more, but one option is to just write our own implementation of `dynamic_bitset` that handles everything that Java `BitSet` does.

20. **`Comparator` and `compareBy`**: `compareBy` and `compareValuesBy` produce `Comparator` objects used with sort and ordered collections. How deeply should comparator composition be supported (e.g., chained `thenBy`, `reversed`)?

Answer: We don't need to support any of `Comparator`'s methods such as `thenBy` or `reversed`.

## Output / C++ Specifics

21. **Top-level declarations**: Kotlin programs often have top-level functions and `val`/`var` declarations outside `main`. Should these be emitted as C++ free functions and global variables?

Answer: Yes

22. **`Long` vs `long long`**: Should Kotlin `Int` map to `int` and `Long` to `long long`? What about `Double` (to `double`) and `Float` (to `float`)?

Answer: Yes

23. **Sealed classes**: Should `sealed class` hierarchies be transpiled to a C++ class hierarchy with virtual dispatch, or to `std::variant`? The latter is more efficient but more complex to generate.

Answer: Whatever is easier

24. **Inheritance and interfaces**: How deep should class/interface support go? Specifically, should `interface` definitions (with or without default implementations) be supported?

Answer: Inheritance is really only needed for sealed classes. Interfaces do not need to be supported, but sealed classes themselves may have abstract functions so ideally you should account for this (sealed classes themselves are not a high priority, so if this ends up being difficult we can skip it for the initial implementation)

## Testing

25. **Examples directory**: The spec mentions `examples/` but it doesn't exist yet. Should I create the examples directory and populate it with sample programs as part of this first iteration, or will you provide them?

Answer: I will create it. You should create your own examples (likely many more than I do) in a different directory.

26. **Test harness**: Should the automated test harness be part of the Kotlin transpiler project itself (e.g., a Gradle test task), or a separate script? And should it require both `kotlinc` and a C++ compiler (e.g., `g++`) to be available on the PATH?

Answer: It doesn't matter, and probably both yes. I'm not sure if those are actually true right now so I will have to check.

27. **Test input generation**: For programs that read from stdin, how should test inputs be provided — hand-written input files, or a generated input alongside each example program?

## Build

28. **Build system**: Should the transpiler be built with Gradle (standard for Kotlin projects), and if so, is there a preference for Kotlin DSL (`build.gradle.kts`) vs Groovy DSL?

Answer: Gradle sounds good, and we should prefer the Kotlin DSL.


Additional notes from me:
- Kotlin's `Random` class should also be supported.
- I have added an auxiliary Kotlin file that provides the custom `nextInt` etc. input functions described in the instructions.
- Kotlin's `readln` function does not need to be supported. If this appears in any example program (other than in a comment), this is a mistake and you should let me know.