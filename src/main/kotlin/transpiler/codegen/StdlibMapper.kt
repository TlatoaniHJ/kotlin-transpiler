package transpiler.codegen

import transpiler.ast.*

/**
 * Maps Kotlin standard-library method / function calls to their C++ equivalents.
 *
 * The [CodeGenerator] delegates here whenever it encounters a [CallExpression] or
 * [MethodCallExpression] whose callee is a known stdlib symbol.
 *
 * Returns null when no mapping is known (the caller should fall back to a
 * verbatim translation).
 */
object StdlibMapper {

    // ─── Top-level function calls ─────────────────────────────────────────────

    /**
     * Try to map a top-level call like `max(a,b)`, `listOf(...)`, `compareBy {...}`.
     * [gen] is used to recursively emit sub-expressions.
     * Returns the C++ string, or null if no mapping known.
     */
    fun mapTopLevelCall(
        name: String,
        typeArgs: List<KotlinType>,
        args: List<CallArgument>,
        trailingLambda: LambdaExpression?,
        gen: CodeGenerator
    ): String? {
        fun arg(i: Int) = gen.genExpr(args[i].value)
        fun allArgs() = args.map { gen.genExpr(it.value) }

        return when (name) {
            // Math
            "max", "maxOf" -> if (args.size == 2) "max(${arg(0)}, ${arg(1)})" else null
            "min", "minOf" -> if (args.size == 2) "min(${arg(0)}, ${arg(1)})" else null
            "abs"          -> if (args.size == 1) "abs(${arg(0)})" else null
            "sqrt"         -> if (args.size == 1) "sqrt(${arg(0)})" else null
            "pow"          -> if (args.size == 2) "pow(${arg(0)}, ${arg(1)})" else null
            "floor"        -> if (args.size == 1) "floor(${arg(0)})" else null
            "ceil"         -> if (args.size == 1) "ceil(${arg(0)})" else null
            "round"        -> if (args.size == 1) "round(${arg(0)})" else null
            "ln"           -> if (args.size == 1) "log(${arg(0)})" else null
            "log2"         -> if (args.size == 1) "log2(${arg(0)})" else null
            "log10"        -> if (args.size == 1) "log10(${arg(0)})" else null
            "sign"         -> if (args.size == 1) "(${arg(0)} > 0 ? 1 : ${arg(0)} < 0 ? -1 : 0)" else null
            "gcd"          -> if (args.size == 2) "[](auto _a, auto _b) -> decltype(_a) { _a = _a < 0 ? -_a : _a; _b = _b < 0 ? -_b : _b; while (_b) { auto _t = _b; _b = _a % _b; _a = _t; } return _a; }(${arg(0)}, ${arg(1)})" else null

            // Collections
            "listOf"        -> buildListOf(typeArgs, args, gen)
            "mutableListOf" -> buildListOf(typeArgs, args, gen)
            "arrayListOf"   -> buildListOf(typeArgs, args, gen)
            "emptyList"     -> buildEmptyList(typeArgs, gen)
            "mutableSetOf", "hashSetOf" -> buildSetOf(typeArgs, args, gen, ordered = false)
            "setOf"         -> buildSetOf(typeArgs, args, gen, ordered = true)
            "sortedSetOf"   -> buildSetOf(typeArgs, args, gen, ordered = true)
            "mutableMapOf"  -> if (args.isEmpty()) buildEmptyMap(typeArgs, gen, ordered = true) else null
            "hashMapOf"     -> if (args.isEmpty()) buildEmptyMap(typeArgs, gen, ordered = false) else null
            "mapOf"         -> if (args.isEmpty()) buildEmptyMap(typeArgs, gen, ordered = true) else null
            "emptyMap"      -> buildEmptyMap(typeArgs, gen, ordered = true)
            "arrayOf"       -> buildListOf(typeArgs, args, gen)
            "intArrayOf", "longArrayOf", "doubleArrayOf", "floatArrayOf",
            "booleanArrayOf", "charArrayOf" -> buildPrimArrayOf(name, args, gen)

            // Array/list constructors
            "IntArray", "LongArray", "DoubleArray", "FloatArray",
            "BooleanArray", "CharArray" -> {
                if (args.size == 1) {
                    val elemType = name.removeSuffix("Array").lowercase()
                    if (trailingLambda != null) {
                        val lambdaCpp = gen.genLambdaAsLoopInit(trailingLambda, arg(0), elemType)
                        lambdaCpp
                    } else {
                        "vector<$elemType>(${arg(0)})"
                    }
                } else null
            }
            "Array" -> {
                if (args.size == 1 && trailingLambda != null) {
                    gen.genLambdaAsLoopInit(trailingLambda, arg(0), "auto")
                } else null
            }
            "MutableList" -> {
                if (args.size == 1 && trailingLambda != null) {
                    gen.genLambdaAsLoopInit(trailingLambda, arg(0), "auto")
                } else null
            }

            // Pairs / tuples
            "Pair"         -> if (args.size == 2) "make_pair(${arg(0)}, ${arg(1)})" else null
            "Triple"       -> if (args.size == 3) "make_tuple(${arg(0)}, ${arg(1)}, ${arg(2)})" else null
            "to"           -> if (args.size == 2) "make_pair(${arg(0)}, ${arg(1)})" else null

            // Comparators (produce lambdas)
            "compareBy" -> {
                // Collect all selector lambdas: from args and trailing lambda
                val selectors = mutableListOf<String>()
                for (a in args) {
                    if (a.value is LambdaExpression) {
                        selectors.add(gen.genLambdaBody(a.value as LambdaExpression))
                    }
                }
                if (trailingLambda != null) {
                    selectors.add(gen.genLambdaBody(trailingLambda))
                }
                if (selectors.isEmpty()) return null
                if (selectors.size == 1) {
                    val key = selectors[0]
                    "[&](const auto& _a, const auto& _b) { return $key(_a) < $key(_b); }"
                } else {
                    // Multiple selectors: compare lexicographically
                    val comparisons = selectors.mapIndexed { i, key ->
                        val kA = "_k${i}a"
                        val kB = "_k${i}b"
                        "auto $kA = $key(_a); auto $kB = $key(_b);"
                    }.joinToString(" ")
                    val checks = selectors.indices.map { i ->
                        val kA = "_k${i}a"
                        val kB = "_k${i}b"
                        if (i < selectors.size - 1) {
                            "if ($kA != $kB) return $kA < $kB;"
                        } else {
                            "return $kA < $kB;"
                        }
                    }.joinToString(" ")
                    "[&](const auto& _a, const auto& _b) { $comparisons $checks }"
                }
            }
            "compareByDescending" -> {
                val selectors = mutableListOf<String>()
                for (a in args) {
                    if (a.value is LambdaExpression) {
                        selectors.add(gen.genLambdaBody(a.value as LambdaExpression))
                    }
                }
                if (trailingLambda != null) {
                    selectors.add(gen.genLambdaBody(trailingLambda))
                }
                if (selectors.isEmpty()) return null
                if (selectors.size == 1) {
                    val key = selectors[0]
                    "[&](const auto& _a, const auto& _b) { return $key(_a) > $key(_b); }"
                } else {
                    val comparisons = selectors.mapIndexed { i, key ->
                        val kA = "_k${i}a"
                        val kB = "_k${i}b"
                        "auto $kA = $key(_a); auto $kB = $key(_b);"
                    }.joinToString(" ")
                    val checks = selectors.indices.map { i ->
                        val kA = "_k${i}a"
                        val kB = "_k${i}b"
                        if (i < selectors.size - 1) {
                            "if ($kA != $kB) return $kA > $kB;"
                        } else {
                            "return $kA > $kB;"
                        }
                    }.joinToString(" ")
                    "[&](const auto& _a, const auto& _b) { $comparisons $checks }"
                }
            }
            "naturalOrder"        -> "[](const auto& _a, const auto& _b) { return _a < _b; }"
            "reverseOrder"        -> "[](const auto& _a, const auto& _b) { return _a > _b; }"

            // I/O
            "print"   -> mapPrint(args, trailingLambda, gen, newline = false)
            "println" -> mapPrint(args, trailingLambda, gen, newline = true)

            // String
            "buildString" -> {
                if (trailingLambda != null) {
                    gen.genBuildString(trailingLambda)
                } else null
            }

            // repeat
            "repeat" -> {
                if (args.size == 1 && trailingLambda != null) {
                    gen.genRepeat(arg(0), trailingLambda)
                } else null
            }

            // check/require/assert - no-ops or assert
            "check", "require" -> if (args.size == 1) "assert(${arg(0)})" else null
            "error" -> if (args.size == 1) "throw runtime_error(${arg(0)})" else null

            // with scope function
            "with" -> {
                if (args.size == 1 && trailingLambda != null) {
                    gen.genWithScope(arg(0), trailingLambda)
                } else null
            }

            // run (no receiver)
            "run" -> {
                if (trailingLambda != null) {
                    "[&]() { ${gen.genLambdaBodyStatements(trailingLambda)} }()"
                } else null
            }

            else -> null
        }
    }

    // ─── Method calls on receiver ─────────────────────────────────────────────

    /**
     * Try to map a method call like `xs.add(x)`, `x.toString()`, `xs.sortBy { it.x }`.
     * Returns the C++ string, or null if no mapping known.
     */
    fun mapMethodCall(
        receiver: String,
        method: String,
        typeArgs: List<KotlinType>,
        args: List<CallArgument>,
        trailingLambda: LambdaExpression?,
        isSafe: Boolean,
        gen: CodeGenerator
    ): String? {
        val safePrefix = if (isSafe) "($receiver ? " else ""
        val safeSuffix = if (isSafe) " : nullptr)" else ""
        fun wrap(s: String) = if (isSafe) "($receiver ? ($s) : /* null */{})" else s

        fun arg(i: Int) = gen.genExpr(args[i].value)
        fun allArgs() = args.map { gen.genExpr(it.value) }

        return when (method) {
            // ── Conversion ──────────────────────────────────────────────────
            "toInt"    -> "(int)($receiver)"
            "toLong"   -> "(long long)($receiver)"
            "toDouble" -> "(double)($receiver)"
            "toFloat"  -> "(float)($receiver)"
            "toChar"   -> "(char)($receiver)"
            "toByte"   -> "(uint8_t)($receiver)"
            "toShort"  -> "(short)($receiver)"
            "toString" -> "to_string($receiver)"
            "toBoolean"-> "($receiver == \"true\")"
            "toLongOrNull", "toIntOrNull" -> null  // skip for now

            // ── String methods ───────────────────────────────────────────────
            "length"       -> "$receiver.size()"
            "isEmpty"      -> "$receiver.empty()"
            "isNotEmpty"   -> "(!$receiver.empty())"
            "trim"         -> null // skip
            "trimStart", "trimEnd" -> null
            "lowercase", "toLowercase" -> null
            "uppercase", "toUppercase" -> null
            "contains"     -> if (args.size == 1) "$receiver.find(${arg(0)}) != string::npos" else null
            "startsWith"   -> if (args.size == 1) "($receiver.rfind(${arg(0)}, 0) == 0)" else null
            "endsWith"     -> if (args.size == 1)
                                  "($receiver.size() >= ${arg(0)}.size() && $receiver.substr($receiver.size()-${arg(0)}.size()) == ${arg(0)})"
                              else null
            "substring"    -> if (args.size == 1) "$receiver.substr(${arg(0)})"
                              else if (args.size == 2) "$receiver.substr(${arg(0)}, ${arg(1)}-${arg(0)})"
                              else null
            "replace"      -> if (args.size == 2)
                                  "[&]() { string _s = $receiver; size_t _p; while ((_p = _s.find(${arg(0)})) != string::npos) _s.replace(_p, ${arg(0)}.size(), ${arg(1)}); return _s; }()"
                              else null
            "split"        -> if (args.size == 1) null else null  // complex, skip
            "toCharArray"  -> "vector<char>($receiver.begin(), $receiver.end())"
            "reversed"     -> if (args.isEmpty()) "string($receiver.rbegin(), $receiver.rend())" else null

            // ── Collection size / access ──────────────────────────────────────
            "size"       -> "$receiver.size()"
            "count"      -> if (args.isEmpty()) "(int)$receiver.size()"
                            else if (trailingLambda != null) gen.genCountIf(receiver, trailingLambda)
                            else null
            "indices"    -> "(0 until (int)$receiver.size())"  // will be used in for loops
            "lastIndex"  -> "((int)$receiver.size() - 1)"
            "first"      -> if (args.isEmpty()) "$receiver.front()"
                            else if (trailingLambda != null) gen.genFirstIf(receiver, trailingLambda)
                            else null
            "last"       -> if (args.isEmpty()) "$receiver.back()"
                            else if (trailingLambda != null) gen.genLastIf(receiver, trailingLambda)
                            else null
            "firstOrNull" -> null
            "lastOrNull"  -> null
            "get"        -> if (args.size == 1) "$receiver[${arg(0)}]" else null
            "set"        -> if (args.size == 2) "$receiver[${arg(0)}] = ${arg(1)}" else null
            "getOrElse"  -> if (args.size == 1 && trailingLambda != null)
                                "(${arg(0)} < (int)$receiver.size() ? $receiver[${arg(0)}] : ${gen.genLambdaBody(trailingLambda)}(${arg(0)}))"
                            else null
            "getOrDefault" -> if (args.size == 2) "($receiver.count(${arg(0)}) ? $receiver.at(${arg(0)}) : ${arg(1)})" else null

            // ── List mutation ─────────────────────────────────────────────────
            "add"        -> when (args.size) {
                1 -> { gen.markDirty(); "$receiver.push_back(${arg(0)})" }
                2 -> { gen.markDirty(); "$receiver.insert($receiver.begin() + ${arg(0)}, ${arg(1)})" }
                else -> null
            }
            "addAll"     -> if (args.size == 1)
                                "$receiver.insert($receiver.end(), ${arg(0)}.begin(), ${arg(0)}.end())"
                            else null
            "removeAt"   -> if (args.size == 1) "$receiver.erase($receiver.begin() + ${arg(0)})" else null
            "remove"     -> if (args.size == 1)
                                "{ auto _it = find($receiver.begin(), $receiver.end(), ${arg(0)}); if (_it != $receiver.end()) $receiver.erase(_it); }"
                            else null
            "removeAll"  -> null
            "removeLast" -> "([&]() { auto _v = $receiver.back(); $receiver.pop_back(); return _v; }())"
            "removeFirst"-> "([&]() { auto _v = $receiver.front(); $receiver.erase($receiver.begin()); return _v; }())"

            // ── Stack-like (deque / ArrayDeque) ───────────────────────────────
            "addFirst", "push", "addLast" -> when (method) {
                "addFirst", "push" -> "$receiver.push_front(${arg(0)})"
                else               -> "$receiver.push_back(${arg(0)})"
            }
            "removeFirst", "pollFirst", "pop" -> "$receiver.pop_front(); $receiver.front()"
            "removeLast", "pollLast"           -> "$receiver.pop_back(); $receiver.back()"
            "peekFirst"  -> "$receiver.front()"
            "peekLast"   -> "$receiver.back()"
            "peek"       -> "$receiver.top()"   // stack / priority_queue
            "poll"       -> "($receiver.top(); $receiver.pop())"
            "offer"      -> "$receiver.push(${arg(0)})"

            // ── Common collection ops ─────────────────────────────────────────
            "isEmpty"    -> "$receiver.empty()"
            "isNotEmpty" -> "(!$receiver.empty())"
            "clear"      -> "$receiver.clear()"
            "contains"   -> if (args.size == 1) "($receiver.count(${arg(0)}) > 0)" else null
            "containsKey"-> if (args.size == 1) "($receiver.count(${arg(0)}) > 0)" else null
            "containsValue" -> null
            "subList"    -> if (args.size == 2)
                                "vector<decay_t<decltype($receiver.front())>>($receiver.begin() + ${arg(0)}, $receiver.begin() + ${arg(1)})"
                            else null
            "toList", "toMutableList" -> "vector<decay_t<decltype($receiver.front())>>($receiver.begin(), $receiver.end())"
            "toSet"      -> "set<decay_t<decltype($receiver.front())>>($receiver.begin(), $receiver.end())"
            "toIntArray" -> "vector<int>($receiver.begin(), $receiver.end())"
            "toLongArray"-> "vector<long long>($receiver.begin(), $receiver.end())"
            "reversed"   -> "vector<decay_t<decltype($receiver.front())>>($receiver.rbegin(), $receiver.rend())"
            "asReversed" -> "vector<decay_t<decltype($receiver.front())>>($receiver.rbegin(), $receiver.rend())"

            // ── Map ops ───────────────────────────────────────────────────────
            "put"        -> if (args.size == 2) "$receiver[${arg(0)}] = ${arg(1)}" else null
            "putAll"     -> if (args.size == 1) "$receiver.insert(${arg(0)}.begin(), ${arg(0)}.end())" else null
            "getOrPut"   -> if (args.size == 1 && trailingLambda != null)
                                "([&]() -> auto& { auto _k = ${arg(0)}; if (!$receiver.count(_k)) $receiver[_k] = ${gen.genLambdaBody(trailingLambda)}(); return $receiver[_k]; }())"
                            else null
            "keys"       -> null  // complex
            "values"     -> null
            "entries"    -> "vector<pair<decay_t<decltype($receiver.begin()->first)>, decay_t<decltype($receiver.begin()->second)>>>($receiver.begin(), $receiver.end())"

            // ── TreeSet navigation ────────────────────────────────────────────
            "lower"      -> "([&]() { auto _it = $receiver.lower_bound(${arg(0)}); return _it == $receiver.begin() ? /* no lower */ *_it : *prev(_it); }())"
            "floor"      -> "([&]() { auto _it = $receiver.upper_bound(${arg(0)}); return _it == $receiver.begin() ? /* no floor */ *_it : *prev(_it); }())"
            "higher"     -> "(*$receiver.upper_bound(${arg(0)}))"
            "ceiling"    -> "(*$receiver.lower_bound(${arg(0)}))"
            "lowerKey"   -> "([&]() { auto _it = $receiver.lower_bound(${arg(0)}); return _it == $receiver.begin() ? /* no lower */ _it->first : prev(_it)->first; }())"
            "floorKey"   -> "([&]() { auto _it = $receiver.upper_bound(${arg(0)}); return _it == $receiver.begin() ? /* no floor */ _it->first : prev(_it)->first; }())"
            "higherKey"  -> "($receiver.upper_bound(${arg(0)})->first)"
            "ceilingKey" -> "($receiver.lower_bound(${arg(0)})->first)"
            "lowerEntry" -> "([&]() { auto _it = $receiver.lower_bound(${arg(0)}); return _it == $receiver.begin() ? /* no lower */ *_it : *prev(_it); }())"
            "floorEntry" -> "([&]() { auto _it = $receiver.upper_bound(${arg(0)}); return _it == $receiver.begin() ? /* no floor */ *_it : *prev(_it); }())"
            "higherEntry"-> "(*$receiver.upper_bound(${arg(0)}))"
            "ceilingEntry"-> "(*$receiver.lower_bound(${arg(0)}))"
            "headSet", "tailSet", "subSet" -> null

            // ── Sort (12 variants) ────────────────────────────────────────────
            "sort"            -> "$receiver.sort($receiver.begin(), $receiver.end())".also {
                // actually for vector: std::sort
                return "sort($receiver.begin(), $receiver.end())"
            }
            "sortDescending"  -> "sort($receiver.begin(), $receiver.end(), greater<decay_t<decltype($receiver.front())>>())"
            "sortBy"          -> if (trailingLambda != null) {
                val key = gen.genLambdaBody(trailingLambda)
                "sort($receiver.begin(), $receiver.end(), [&](const auto& _a, const auto& _b) { return $key(_a) < $key(_b); })"
            } else null
            "sortByDescending"-> if (trailingLambda != null) {
                val key = gen.genLambdaBody(trailingLambda)
                "sort($receiver.begin(), $receiver.end(), [&](const auto& _a, const auto& _b) { return $key(_a) > $key(_b); })"
            } else null
            "sortWith"        -> if (args.size == 1) "sort($receiver.begin(), $receiver.end(), ${arg(0)})" else null
            "sorted"          -> "[&]() { auto _v = $receiver; sort(_v.begin(), _v.end()); return _v; }()"
            "sortedDescending"-> "[&]() { auto _v = $receiver; sort(_v.begin(), _v.end(), greater<decay_t<decltype(_v.front())>>()); return _v; }()"
            "sortedBy"        -> if (trailingLambda != null) {
                val key = gen.genLambdaBody(trailingLambda)
                "[&]() { auto _v = $receiver; sort(_v.begin(), _v.end(), [&](const auto& _a, const auto& _b) { return $key(_a) < $key(_b); }); return _v; }()"
            } else null
            "sortedByDescending" -> if (trailingLambda != null) {
                val key = gen.genLambdaBody(trailingLambda)
                "[&]() { auto _v = $receiver; sort(_v.begin(), _v.end(), [&](const auto& _a, const auto& _b) { return $key(_a) > $key(_b); }); return _v; }()"
            } else null
            "sortedWith"      -> if (args.size == 1) "[&]() { auto _v = $receiver; sort(_v.begin(), _v.end(), ${arg(0)}); return _v; }()" else null
            "reversed"        -> "[&]() { auto _v = $receiver; reverse(_v.begin(), _v.end()); return _v; }()"

            // ── Functional ────────────────────────────────────────────────────
            "map", "mapIndexed" -> if (trailingLambda != null)
                gen.genMap(receiver, trailingLambda, indexed = method == "mapIndexed")
                else null
            "filter", "filterIndexed" -> if (trailingLambda != null)
                gen.genFilter(receiver, trailingLambda, indexed = method == "filterIndexed")
                else null
            "forEach", "forEachIndexed" -> if (trailingLambda != null)
                gen.genForEach(receiver, trailingLambda, indexed = method == "forEachIndexed")
                else null
            "any"       -> if (trailingLambda != null) gen.genAny(receiver, trailingLambda) else null
            "all"       -> if (trailingLambda != null) gen.genAll(receiver, trailingLambda) else null
            "none"      -> if (trailingLambda != null) gen.genNone(receiver, trailingLambda) else null
            "reduce"    -> if (trailingLambda != null) gen.genReduce(receiver, trailingLambda) else null
            "fold"      -> if (args.size == 1 && trailingLambda != null) gen.genFold(receiver, arg(0), trailingLambda) else null
            "flatMap"   -> if (trailingLambda != null) gen.genFlatMap(receiver, trailingLambda) else null
            "mapNotNull"-> if (trailingLambda != null) gen.genMapNotNull(receiver, trailingLambda) else null
            "filterNot" -> if (trailingLambda != null)
                gen.genFilter(receiver, trailingLambda, negated = true)
                else null
            "partition" -> null  // complex
            "groupBy"   -> null  // complex
            "distinct"  -> "[&]() { auto _v = $receiver; sort(_v.begin(), _v.end()); _v.erase(unique(_v.begin(), _v.end()), _v.end()); return _v; }()"
            "distinctBy"-> null
            "zip"       -> if (args.size == 1)
                "[&]() { auto& _a = $receiver; auto& _b = ${arg(0)}; auto _n = min(_a.size(), _b.size()); vector<pair<decltype(_a[0]), decltype(_b[0])>> _r(_n); for (int _i = 0; _i < (int)_n; _i++) _r[_i] = {_a[_i], _b[_i]}; return _r; }()"
                else null
            "take"      -> if (args.size == 1) "vector<decay_t<decltype($receiver.front())>>($receiver.begin(), $receiver.begin() + min((int)${arg(0)}, (int)$receiver.size()))" else null
            "drop"      -> if (args.size == 1) "vector<decay_t<decltype($receiver.front())>>($receiver.begin() + min((int)${arg(0)}, (int)$receiver.size()), $receiver.end())" else null
            "sum"       -> if (args.isEmpty()) "accumulate($receiver.begin(), $receiver.end(), decltype($receiver.front())(0))" else null
            "sumOf"     -> if (trailingLambda != null) {
                val lam = gen.genLambdaAsCppLambda(trailingLambda)
                "accumulate($receiver.begin(), $receiver.end(), 0LL, [&](auto _s, auto& _x) { return _s + $lam(_x); })"
            } else null
            "maxOrNull", "max" -> "(*max_element($receiver.begin(), $receiver.end()))"
            "minOrNull", "min" -> "(*min_element($receiver.begin(), $receiver.end()))"
            "maxByOrNull", "maxBy" -> if (trailingLambda != null) {
                val key = gen.genLambdaBody(trailingLambda)
                "(*max_element($receiver.begin(), $receiver.end(), [&](const auto& _a, const auto& _b) { return $key(_a) < $key(_b); }))"
            } else null
            "minByOrNull", "minBy" -> if (trailingLambda != null) {
                val key = gen.genLambdaBody(trailingLambda)
                "(*min_element($receiver.begin(), $receiver.end(), [&](const auto& _a, const auto& _b) { return $key(_a) < $key(_b); }))"
            } else null
            "joinToString" -> {
                val sep = if (args.isNotEmpty()) arg(0) else "\"\""
                gen.genJoinToString(receiver, sep, trailingLambda)
            }

            // ── Scope functions ───────────────────────────────────────────────
            "let"    -> if (trailingLambda != null) gen.genLet(receiver, trailingLambda) else null
            "also"   -> if (trailingLambda != null) gen.genAlso(receiver, trailingLambda) else null
            "apply"  -> if (trailingLambda != null) gen.genApply(receiver, trailingLambda) else null
            "run"    -> if (trailingLambda != null) gen.genRunReceiver(receiver, trailingLambda) else null
            "takeIf" -> if (trailingLambda != null)
                "([&]() -> auto* { auto& _it = $receiver; return ${gen.genLambdaBody(trailingLambda)}(_it) ? &_it : nullptr; }())"
                else null

            // ── Random ────────────────────────────────────────────────────────
            "nextInt"    -> {
                gen.markNeedsRandom()
                when (args.size) {
                    0 -> "rng()"
                    1 -> "uniform_int_distribution<int>(0, ${arg(0)}-1)(rng)"
                    2 -> "uniform_int_distribution<int>(${arg(0)}, ${arg(1)}-1)(rng)"
                    else -> null
                }
            }
            "nextLong"   -> {
                gen.markNeedsRandom()
                "uniform_int_distribution<long long>(0, LLONG_MAX)(rng64)"
            }
            "nextDouble" -> {
                gen.markNeedsRandom()
                "uniform_real_distribution<double>(0.0, 1.0)(rng)"
            }
            "nextBoolean" -> {
                gen.markNeedsRandom()
                "(rng() & 1)"
            }

            else -> null
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildListOf(typeArgs: List<KotlinType>, args: List<CallArgument>, gen: CodeGenerator): String {
        val elemsCpp = args.joinToString(", ") { gen.genExpr(it.value) }
        val typeStr = if (typeArgs.isNotEmpty()) gen.typeToCpp(typeArgs[0]) else if (args.isNotEmpty()) "decltype(${gen.genExpr(args[0].value)})" else "int"
        return if (args.isEmpty()) "vector<$typeStr>()" else "vector<$typeStr>{$elemsCpp}"
    }

    private fun buildEmptyList(typeArgs: List<KotlinType>, gen: CodeGenerator): String {
        val typeStr = if (typeArgs.isNotEmpty()) gen.typeToCpp(typeArgs[0]) else "int"
        return "vector<$typeStr>()"
    }

    private fun buildSetOf(typeArgs: List<KotlinType>, args: List<CallArgument>, gen: CodeGenerator, ordered: Boolean): String {
        val elemsCpp = args.joinToString(", ") { gen.genExpr(it.value) }
        val typeStr = if (typeArgs.isNotEmpty()) gen.typeToCpp(typeArgs[0]) else if (args.isNotEmpty()) "decltype(${gen.genExpr(args[0].value)})" else "int"
        val container = if (ordered) "set" else "unordered_set"
        return if (args.isEmpty()) "$container<$typeStr>()" else "$container<$typeStr>{$elemsCpp}"
    }

    private fun buildEmptyMap(typeArgs: List<KotlinType>, gen: CodeGenerator, ordered: Boolean): String {
        val k = if (typeArgs.size >= 1) gen.typeToCpp(typeArgs[0]) else "int"
        val v = if (typeArgs.size >= 2) gen.typeToCpp(typeArgs[1]) else "int"
        val container = if (ordered) "map" else "unordered_map"
        return "$container<$k, $v>()"
    }

    private fun buildPrimArrayOf(name: String, args: List<CallArgument>, gen: CodeGenerator): String {
        val elemsCpp = args.joinToString(", ") { gen.genExpr(it.value) }
        val type = when (name) {
            "intArrayOf"     -> "int"
            "longArrayOf"    -> "long long"
            "doubleArrayOf"  -> "double"
            "floatArrayOf"   -> "float"
            "booleanArrayOf" -> "bool"
            "charArrayOf"    -> "char"
            else             -> "auto"
        }
        return "vector<$type>{$elemsCpp}"
    }

    private fun mapPrint(
        args: List<CallArgument>,
        trailingLambda: LambdaExpression?,
        gen: CodeGenerator,
        newline: Boolean
    ): String {
        val suffix = if (newline) " << '\\n'" else ""
        if (args.isEmpty()) return "cout << '\\n'"
        val value = args[0].value

        // Special-case: println(xs.joinToString(...))
        if (value is CallExpression) {
            val callee = value.callee
            if (callee is PropertyAccessExpr && callee.name == "joinToString") {
                val sep = if (value.args.isNotEmpty()) gen.genExpr(value.args[0].value) else "\"\""
                return gen.genJoinToCout(gen.genExpr(callee.receiver), sep, value.trailingLambda, newline)
            }
        }
        if (value is MethodCallExpression && value.method == "joinToString") {
            val sep = if (value.args.isNotEmpty()) gen.genExpr(value.args[0].value) else "\"\""
            return gen.genJoinToCout(gen.genExpr(value.receiver), sep, value.trailingLambda, newline)
        }

        return "cout << ${gen.genExpr(value)}$suffix"
    }
}
