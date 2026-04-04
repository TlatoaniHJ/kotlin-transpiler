package transpiler.typesystem

import transpiler.ast.KotlinType
import transpiler.Config

/**
 * Converts a [KotlinType] to its C++ representation.
 */
object TypeResolver {

    fun toCpp(type: KotlinType, config: Config = Config.default): String = when (type) {
        is KotlinType.Unit    -> "void"
        is KotlinType.Auto    -> "auto"
        is KotlinType.Dynamic -> "auto"
        is KotlinType.Simple  -> simpleTypeToCpp(type.name)
        is KotlinType.Generic -> genericTypeToCpp(type.name, type.typeArgs, config)
        is KotlinType.Nullable -> nullableTypeToCpp(type.inner, config)
        is KotlinType.Function -> functionTypeToCpp(type.paramTypes, type.returnType, config)
        is KotlinType.Array   -> arrayTypeToCpp(type.elementType, config)
    }

    // Returns the C++ type string for use in variable declarations, etc.
    // For pointer types we use raw pointers (memory leaks acceptable in CP).
    private fun nullableTypeToCpp(inner: KotlinType, config: Config): String {
        val base = toCpp(inner, config)
        return when (inner) {
            // value types become optional, everything else stays as pointer
            is KotlinType.Simple -> when (inner.name) {
                "Int", "Long", "Double", "Float", "Boolean", "Char" ->
                    "std::optional<$base>"
                else -> "$base*"
            }
            else -> "$base*"
        }
    }

    private fun functionTypeToCpp(params: List<KotlinType>, ret: KotlinType, config: Config): String {
        val paramsCpp = params.joinToString(", ") { toCpp(it, config) }
        val retCpp = toCpp(ret, config)
        return "std::function<$retCpp($paramsCpp)>"
    }

    private fun arrayTypeToCpp(element: KotlinType, config: Config): String =
        "std::vector<${toCpp(element, config)}>"

    private fun simpleTypeToCpp(name: String): String = when (name) {
        "Int"          -> "int"
        "Long"         -> "long long"
        "Double"       -> "double"
        "Float"        -> "float"
        "Boolean"      -> "bool"
        "Char"         -> "char"
        "String"       -> "std::string"
        "Unit"         -> "void"
        "Any"          -> "void*"
        "Nothing"      -> "void"
        "IntArray"     -> "std::vector<int>"
        "LongArray"    -> "std::vector<long long>"
        "DoubleArray"  -> "std::vector<double>"
        "FloatArray"   -> "std::vector<float>"
        "BooleanArray" -> "std::vector<bool>"
        "CharArray"    -> "std::vector<char>"
        "ByteArray"    -> "std::vector<uint8_t>"
        "ShortArray"   -> "std::vector<short>"
        "StringBuilder"-> "std::ostringstream"
        "BitSet"       -> "DynamicBitSet"
        "Random"       -> "std::mt19937"
        // Collections without type params (raw use) – best effort
        "List"         -> "std::vector<int>"
        "MutableList"  -> "std::vector<int>"
        "ArrayList"    -> "std::vector<int>"
        "Set"          -> "std::set<int>"
        "MutableSet"   -> "std::set<int>"
        "Map"          -> "std::map<int, int>"
        "MutableMap"   -> "std::map<int, int>"
        "HashMap"      -> "std::unordered_map<int, int>"
        "HashSet"      -> "std::unordered_set<int>"
        "TreeSet"      -> "std::set<int>"
        "TreeMap"      -> "std::map<int, int>"
        "ArrayDeque"   -> "std::deque<int>"
        "Stack"        -> "std::stack<int>"
        "PriorityQueue"-> "std::priority_queue<int>"
        "Pair"         -> "std::pair<int, int>"
        "Triple"       -> "std::tuple<int, int, int>"
        else           -> name   // user-defined class; keep as-is
    }

    private fun genericTypeToCpp(name: String, args: List<KotlinType>, config: Config): String {
        fun arg(i: Int) = toCpp(args.getOrElse(i) { KotlinType.Auto }, config)
        return when (name) {
            "List", "MutableList", "ArrayList" ->
                "std::vector<${arg(0)}>"
            "Array" ->
                "std::vector<${arg(0)}>"
            "Set", "MutableSet" ->
                "std::set<${arg(0)}>"
            "HashSet", "LinkedHashSet" ->
                "std::unordered_set<${arg(0)}>"
            "TreeSet" ->
                "std::set<${arg(0)}>"
            "Map", "MutableMap", "LinkedHashMap" ->
                if (config.useUnorderedMapForMutableMapOf)
                    "std::unordered_map<${arg(0)}, ${arg(1)}>"
                else
                    "std::map<${arg(0)}, ${arg(1)}>"
            "HashMap" ->
                "std::unordered_map<${arg(0)}, ${arg(1)}>"
            "TreeMap" ->
                "std::map<${arg(0)}, ${arg(1)}>"
            "ArrayDeque" ->
                "std::deque<${arg(0)}>"
            "Stack" ->
                "std::stack<${arg(0)}>"
            "PriorityQueue" ->
                if (args.size >= 2)
                    "std::priority_queue<${arg(0)}, std::vector<${arg(0)}>, ${arg(1)}>"
                else
                    "std::priority_queue<${arg(0)}>"
            "Pair" ->
                "std::pair<${arg(0)}, ${arg(1)}>"
            "Triple" ->
                "std::tuple<${arg(0)}, ${arg(1)}, ${toCpp(args.getOrElse(2) { KotlinType.Auto }, config)}>"
            "Comparable" ->
                arg(0)   // erase to element type; comparisons become operator<
            "Iterable", "Collection", "Sequence" ->
                "std::vector<${arg(0)}>"
            "Function0", "Function1", "Function2" -> {
                val params = args.dropLast(1).joinToString(", ") { toCpp(it, config) }
                val ret = toCpp(args.last(), config)
                "std::function<$ret($params)>"
            }
            else -> {
                val argsCpp = args.joinToString(", ") { toCpp(it, config) }
                "$name<$argsCpp>"
            }
        }
    }

    /**
     * Parses a simple Kotlin type string (as it appears in annotations) into a [KotlinType].
     * Handles: primitive names, nullable suffix `?`, and one level of generic `<...>`.
     */
    fun parseKotlinType(text: String): KotlinType {
        val trimmed = text.trim()

        // Nullable
        if (trimmed.endsWith("?")) {
            return KotlinType.Nullable(parseKotlinType(trimmed.dropLast(1)))
        }

        // Function type  (A, B) -> C
        if (trimmed.startsWith("(")) {
            val arrow = trimmed.lastIndexOf("->")
            if (arrow >= 0) {
                val paramsPart = trimmed.substring(1, trimmed.lastIndexOf(')'))
                val retPart = trimmed.substring(arrow + 2).trim()
                val params = if (paramsPart.isBlank()) emptyList()
                             else splitTypeArgs(paramsPart).map { parseKotlinType(it) }
                return KotlinType.Function(params, parseKotlinType(retPart))
            }
        }

        // Generic  Name<A, B>
        val lt = trimmed.indexOf('<')
        if (lt >= 0 && trimmed.endsWith('>')) {
            val name = trimmed.substring(0, lt).trim()
            val inner = trimmed.substring(lt + 1, trimmed.length - 1)
            val typeArgs = splitTypeArgs(inner).map { parseKotlinType(it) }
            return when (name) {
                "Array" -> KotlinType.Array(typeArgs.firstOrNull() ?: KotlinType.Auto)
                else    -> KotlinType.Generic(name, typeArgs)
            }
        }

        return when (trimmed) {
            "Unit"         -> KotlinType.Unit
            "IntArray"     -> KotlinType.Array(KotlinType.Simple("Int"))
            "LongArray"    -> KotlinType.Array(KotlinType.Simple("Long"))
            "DoubleArray"  -> KotlinType.Array(KotlinType.Simple("Double"))
            "FloatArray"   -> KotlinType.Array(KotlinType.Simple("Float"))
            "BooleanArray" -> KotlinType.Array(KotlinType.Simple("Boolean"))
            "CharArray"    -> KotlinType.Array(KotlinType.Simple("Char"))
            else           -> KotlinType.Simple(trimmed)
        }
    }

    /** Splits a comma-separated list of type arguments, respecting nested `<>` brackets. */
    private fun splitTypeArgs(s: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in s.indices) {
            when (s[i]) {
                '<' -> depth++
                '>' -> depth--
                ',' -> if (depth == 0) {
                    parts.add(s.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        val last = s.substring(start).trim()
        if (last.isNotEmpty()) parts.add(last)
        return parts
    }
}
