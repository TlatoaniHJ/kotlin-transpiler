package transpiler.codegen

import transpiler.Config
import transpiler.ast.*
import transpiler.typesystem.TypeResolver

/**
 * Walks a [KotlinFile] AST and emits equivalent C++ source code.
 *
 * Usage:
 *   val cpp = CodeGenerator(config).generate(kotlinFile)
 */
class CodeGenerator(val config: Config = Config.default) {

    // ─── Output buffer ────────────────────────────────────────────────────────

    private val out = StringBuilder()
    private var indentLevel = 0

    private fun emit(line: String) {
        if (line.isEmpty()) { out.appendLine(); return }
        out.append(INDENT.repeat(indentLevel))
        out.appendLine(line)
    }

    private fun emitRaw(s: String) { out.append(s) }

    private fun indent(block: () -> Unit) {
        indentLevel++
        block()
        indentLevel--
    }

    // ─── Feature flags ────────────────────────────────────────────────────────

    var needsBitSet  = false
    var needsRandom  = false

    fun markDirty() {}          // no-op – reserved for future mutation tracking
    fun markNeedsRandom() { needsRandom = true }

    // ─── Transpilation errors ─────────────────────────────────────────────────

    private val errors = mutableListOf<String>()

    /** Record a transpilation error and emit a C++ #error directive so compilation fails clearly. */
    private fun emitError(msg: String): String {
        errors.add(msg)
        System.err.println("Transpiler warning: $msg")
        return "/* TRANSPILER ERROR: $msg */ static_assert(false, \"$msg\")"
    }

    // ─── Labeled-break support ────────────────────────────────────────────────

    // Maps Kotlin label name → C++ goto label name
    private val pendingGotoLabels = mutableSetOf<String>()

    // ─── Lambda context (for return@label → continue) ─────────────────────────

    // Stack of currently open "inline lambda" expansion contexts.
    // Each entry is the Kotlin label of the enclosing lambda call (e.g. "forEach").
    private val inlineLambdaLabels = ArrayDeque<String>()

    // ─── Variable type tracking ──────────────────────────────────────────────

    // Tracks variable names whose initializer is a StringBuilder (ostringstream)
    private val stringBuilderVars = mutableSetOf<String>()
    /** Check if a variable name refers to a StringBuilder (ostringstream) */
    fun isStringBuilderVar(name: String): Boolean = name in stringBuilderVars

    // Tracks variable names whose initializer is a set-like type (TreeSet, HashSet, etc.)
    private val setVars = mutableSetOf<String>()

    // Tracks variable names whose initializer is a PQ or java.util.Stack
    private val pqVars = mutableSetOf<String>()
    private val stackVars = mutableSetOf<String>()

    // Tracks locally defined function names (to avoid stdlib mapping conflicts)
    private val localFunctionNames = mutableSetOf<String>()

    // Tracks locally declared variable names (to distinguish from class members in pointer checks)
    private val localVarNames = mutableSetOf<String>()

    // Tracks user-defined class names (to avoid constructor/method mapping conflicts)
    private val userDefinedClassNames = mutableSetOf<String>()

    // Tracks variable names whose type is a user-defined class (skip stdlib method mapping)
    private val userClassVars = mutableSetOf<String>()

    // Tracks variable/member names whose type is a collection of user-defined class elements
    // (e.g. MutableList<Edge>, Array<Node>). Indexing these yields a user class instance.
    private val userClassCollectionVars = mutableSetOf<String>()

    // Deferred out-of-line method definitions (emitted after all class structs)
    private val deferredMethodDefs = mutableListOf<String>()

    // Tracks variable/member names that are pointers to user-defined classes
    // (lateinit members, nullable class-typed members). Access uses -> instead of .
    private val pointerMembers = mutableSetOf<String>()

    // Tracks which constructor parameter indices need pointer semantics (user-defined class types).
    // Key: class name, Value: set of parameter indices that are pointers.
    private val classPointerCtorParams = mutableMapOf<String, Set<Int>>()

    // Tracks C++ types of class members: "ClassName.memberName" → "C++ type"
    private val classMemberTypes = mutableMapOf<String, String>()

    /** Copy variable tracking state from parent generator to this sub-generator. */
    fun inheritTrackingFrom(parent: CodeGenerator) {
        stringBuilderVars.addAll(parent.stringBuilderVars)
        setVars.addAll(parent.setVars)
        pqVars.addAll(parent.pqVars)
        stackVars.addAll(parent.stackVars)
        localFunctionNames.addAll(parent.localFunctionNames)
        userDefinedClassNames.addAll(parent.userDefinedClassNames)
        userClassVars.addAll(parent.userClassVars)
        userClassCollectionVars.addAll(parent.userClassCollectionVars)
        pointerMembers.addAll(parent.pointerMembers)
        classPointerCtorParams.putAll(parent.classPointerCtorParams)
        classMemberTypes.putAll(parent.classMemberTypes)
    }

    private fun createSubGenerator(): CodeGenerator {
        val sub = CodeGenerator(config)
        sub.inheritTrackingFrom(this)
        return sub
    }

    // ─── Main entry point ─────────────────────────────────────────────────────

    fun generate(file: KotlinFile, originalSource: String? = null): String {
        val header = StringBuilder()

        // Emit source comment header
        if (originalSource != null) {
            header.appendLine("// Transpiled from Kotlin to C++ using https://github.com/TlatoaniHJ/kotlin-transpiler")
            header.appendLine("//")
            for (line in originalSource.trimEnd().lines()) {
                if (line.isEmpty()) header.appendLine("//")
                else header.appendLine("// $line")
            }
            header.appendLine()
        }

        val body = StringBuilder()

        // Detect features before emitting
        detectFeatures(file)

        // Generate file body into `out`
        genFile(file)
        body.append(out)

        return header.toString() + Boilerplate.build(
            needsIo     = true,
            needsBitSet = needsBitSet,
            needsRandom = needsRandom
        ) + body.toString()
    }

    // ─── Feature detection ────────────────────────────────────────────────────

    private fun detectFeatures(file: KotlinFile) {
        val src = file.toString()   // rough scan; precise detection done during gen
        if (src.contains("BitSet"))    needsBitSet = true
        if (src.contains("Random") || src.contains("nextInt") || src.contains("nextLong")
            || src.contains("nextDouble")) needsRandom = true
    }

    // ─── File / top-level ────────────────────────────────────────────────────

    /** Topologically sort classes so that by-value member dependencies come first.
     *  Pointer/lateinit members only need forward declarations, so they don't create ordering constraints. */
    private fun topoSortClasses(classes: List<ClassDecl>): List<ClassDecl> {
        val classMap = classes.associateBy { it.name }
        val classNames = classMap.keys

        // Collect by-value dependencies: member types that are user-defined classes and NOT pointers
        fun byValueDeps(cls: ClassDecl): Set<String> {
            val deps = mutableSetOf<String>()
            // Constructor params: user-defined class types are always stored as pointers,
            // so they don't create by-value dependencies. Only non-class types would,
            // but those can't be user-defined class names anyway. Skip all class-typed params.
            // (Previously this added them as deps, but genClassDecl always emits them as pointers.)
            // Member properties
            for (m in cls.members.filterIsInstance<MemberProperty>()) {
                val typeName = when (m.decl.type) {
                    is KotlinType.Simple -> (m.decl.type as KotlinType.Simple).name
                    is KotlinType.Generic -> (m.decl.type as KotlinType.Generic).name
                    else -> null
                }
                if (typeName != null && typeName in classNames
                    && m.decl.type !is KotlinType.Nullable
                    && Modifier.Lateinit !in m.decl.modifiers) {
                    deps.add(typeName)
                }
                // Also check generic type args (e.g., MutableList<Edge> needs Edge)
                if (m.decl.type is KotlinType.Generic) {
                    for (arg in (m.decl.type as KotlinType.Generic).typeArgs) {
                        val argName = when (arg) {
                            is KotlinType.Simple -> arg.name
                            is KotlinType.Generic -> arg.name
                            else -> null
                        }
                        if (argName != null && argName in classNames) deps.add(argName)
                    }
                }
                // Check initializer for class references (e.g., Array(n) { IDENTITY })
                if (m.decl.initializer != null) {
                    fun checkTypeArgs(typeArgs: List<KotlinType>) {
                        for (ta in typeArgs) {
                            val n = when (ta) { is KotlinType.Simple -> ta.name; is KotlinType.Generic -> ta.name; else -> null }
                            if (n != null && n in classNames) deps.add(n)
                            if (ta is KotlinType.Generic) checkTypeArgs(ta.typeArgs)
                        }
                    }
                    fun checkExpr(e: Expression) {
                        when (e) {
                            is CallExpression -> {
                                if (e.callee is NameReference && (e.callee as NameReference).name in classNames) deps.add((e.callee as NameReference).name)
                                checkTypeArgs(e.typeArgs)
                            }
                            is ObjectCreation -> {
                                val n = when (e.type) { is KotlinType.Simple -> (e.type as KotlinType.Simple).name; is KotlinType.Generic -> (e.type as KotlinType.Generic).name; else -> null }
                                if (n != null && n in classNames) deps.add(n)
                            }
                            else -> {}
                        }
                    }
                    checkExpr(m.decl.initializer)
                }
            }
            // Super types
            for (s in cls.superTypes) {
                val sn = when (s.type) { is KotlinType.Simple -> (s.type as KotlinType.Simple).name; is KotlinType.Generic -> (s.type as KotlinType.Generic).name; else -> null }
                if (sn != null && sn in classNames) deps.add(sn)
            }
            deps.remove(cls.name) // no self-dependency
            return deps
        }

        // Kahn's algorithm
        val deps = classes.associate { it.name to byValueDeps(it).toMutableSet() }
        val result = mutableListOf<ClassDecl>()
        val emitted = mutableSetOf<String>()
        val ready = ArrayDeque(classes.filter { deps[it.name]!!.isEmpty() })
        for (c in ready) emitted.add(c.name)

        while (ready.isNotEmpty()) {
            val cls = ready.removeFirst()
            result.add(cls)
            for (other in classes) {
                if (other.name !in emitted && deps[other.name]!!.remove(cls.name) && deps[other.name]!!.isEmpty()) {
                    ready.add(other)
                    emitted.add(other.name)
                }
            }
        }
        // Add any remaining (circular by-value deps — shouldn't happen with proper pointer usage)
        for (cls in classes) {
            if (cls.name !in emitted) result.add(cls)
        }
        return result
    }

    /** Check if a property's initializer references a user-defined class (e.g. Horval(0,0,0)). */
    private fun propReferencesUserClass(prop: PropertyDecl, classNames: Set<String>): Boolean {
        fun exprRefs(expr: Expression): Boolean = when (expr) {
            is CallExpression -> (expr.callee is NameReference && (expr.callee as NameReference).name in classNames) || expr.args.any { exprRefs(it.value) }
            is ObjectCreation -> {
                val tn = when (expr.type) { is KotlinType.Simple -> (expr.type as KotlinType.Simple).name; is KotlinType.Generic -> (expr.type as KotlinType.Generic).name; else -> "" }
                tn in classNames || expr.args.any { exprRefs(it.value) }
            }
            is NameReference -> expr.name in classNames
            else -> false
        }
        return prop.initializer?.let { exprRefs(it) } ?: false
    }

    private fun genFile(file: KotlinFile) {
        // Forward-declare all top-level function signatures so order doesn't matter
        val functions   = file.declarations.filterIsInstance<TopLevelFunction>().map { it.decl }
        val classes     = file.declarations.filterIsInstance<TopLevelClass>().map { it.decl }
        val objects     = file.declarations.filterIsInstance<TopLevelObject>().map { it.decl }
        val properties  = file.declarations.filterIsInstance<TopLevelProperty>().map { it.decl }

        // Register user-defined class names so we skip stdlib mapping for them
        for (cls in classes) userDefinedClassNames.add(cls.name)

        // Forward-declare all user-defined classes (handles circular references)
        if (classes.isNotEmpty()) {
            for (cls in classes) {
                if (cls.typeParams.isNotEmpty()) {
                    val templateParams = cls.typeParams.joinToString(", ") { "typename $it" }
                    emit("template<$templateParams> struct ${cls.name};")
                } else {
                    emit("struct ${cls.name};")
                }
            }
            emit("")
        }

        // Split properties: primitive constants go before classes (e.g. const val MOD),
        // properties referencing user-defined types go after classes.
        val (earlyProps, lateProps) = properties.partition { prop ->
            prop.initializer == null || !propReferencesUserClass(prop, userDefinedClassNames)
        }

        // Primitive constants first (must be visible to classes)
        for (prop in earlyProps) genTopLevelProperty(prop)
        if (earlyProps.isNotEmpty()) emit("")

        // Classes — topologically sorted so by-value member types are defined first
        // Interleave late props: after each class, emit any late props whose dependencies are now satisfied
        val sortedClasses = topoSortClasses(classes)
        val emittedClasses = mutableSetOf<String>()
        val emittedLateProps = mutableSetOf<Int>()

        // Build dependency map: for each late prop, which user-defined classes does it reference?
        val latePropDeps = lateProps.mapIndexed { idx, prop ->
            val deps = mutableSetOf<String>()
            fun collectClassRefs(expr: Expression) {
                when (expr) {
                    is CallExpression -> {
                        if (expr.callee is NameReference && (expr.callee as NameReference).name in userDefinedClassNames)
                            deps.add((expr.callee as NameReference).name)
                        expr.args.forEach { collectClassRefs(it.value) }
                    }
                    is ObjectCreation -> {
                        val tn = when (expr.type) { is KotlinType.Simple -> (expr.type as KotlinType.Simple).name; is KotlinType.Generic -> (expr.type as KotlinType.Generic).name; else -> "" }
                        if (tn in userDefinedClassNames) deps.add(tn)
                        expr.args.forEach { collectClassRefs(it.value) }
                    }
                    is NameReference -> if (expr.name in userDefinedClassNames) deps.add(expr.name)
                    else -> {}
                }
            }
            prop.initializer?.let { collectClassRefs(it) }
            idx to deps
        }

        for (cls in sortedClasses) {
            genClassDecl(cls)
            emittedClasses.add(cls.name)
            // Emit any late props whose class dependencies are now all satisfied
            for ((idx, deps) in latePropDeps) {
                if (idx !in emittedLateProps && deps.all { it in emittedClasses }) {
                    genTopLevelProperty(lateProps[idx])
                    emit("")
                    emittedLateProps.add(idx)
                }
            }
        }
        for (obj in objects) genObjectDecl(obj)

        // Emit deferred out-of-line method definitions (after all class structs are complete)
        if (deferredMethodDefs.isNotEmpty()) {
            emit("")
            for (def in deferredMethodDefs) {
                emitRaw(def)
                emit("")
            }
        }

        // Emit any remaining late props (shouldn't happen, but safety net)
        val remainingLateProps = lateProps.indices.filter { it !in emittedLateProps }
        for (idx in remainingLateProps) genTopLevelProperty(lateProps[idx])
        if (remainingLateProps.isNotEmpty()) emit("")

        // Non-main functions
        val nonMain = functions.filter { it.name != "main" }
        val mainFn  = functions.firstOrNull { it.name == "main" }

        // Emit forward declarations for all non-main functions (C++ needs these for forward references)
        if (nonMain.size > 1) {
            for (fn in nonMain) { genFunctionPrototype(fn) }
            emit("")
        }

        for (fn in nonMain) { genFunctionDecl(fn); emit("") }

        // main function
        if (mainFn != null) {
            genMainFunction(mainFn)
        }
    }

    // ─── Declarations ────────────────────────────────────────────────────────

    private fun genMainFunction(fn: FunctionDecl) {
        emit("int main() {")
        indent {
            emit("ios::sync_with_stdio(false);")
            emit("cin.tie(nullptr);")
            emit("cout << boolalpha;")
            when (val body = fn.body) {
                is BlockBody      -> body.statements.forEach { genStatement(it) }
                is ExpressionBody -> { emit("return ${genExpr(body.expr)};") }
                null              -> {}
            }
            emit("return 0;")
        }
        emit("}")
    }

    /** Map Kotlin operator function names to C++ operator syntax. */
    private fun kotlinOperatorToCpp(name: String, paramCount: Int): String? = when (name) {
        "plus" -> "operator+"
        "minus" -> "operator-"
        "times" -> "operator*"
        "div" -> "operator/"
        "rem" -> "operator%"
        "unaryMinus" -> "operator-"
        "unaryPlus" -> "operator+"
        "get" -> if (paramCount == 1) "operator[]" else "get"  // C++ operator[] only takes 1 arg
        "invoke" -> "operator()"
        "equals" -> "operator=="
        "compareTo" -> "compareTo"
        "contains" -> "contains"
        "rangeTo" -> "rangeTo"
        "set" -> "set"
        else -> null
    }

    private fun genFunctionDecl(fn: FunctionDecl) {
        // Determine the emitted function name (operator mapping if applicable)
        val fnName = if (Modifier.Operator in fn.modifiers) {
            kotlinOperatorToCpp(fn.name, fn.params.size) ?: fn.name
        } else fn.name

        if (Modifier.Abstract in fn.modifiers) {
            // Abstract → pure virtual (only makes sense inside a class)
            val retType = retTypeToCpp(fn)
            val params = fn.params.joinToString(", ") { paramToCpp(it) }
            emit("virtual $retType $fnName($params) = 0;")
            return
        }

        val retType = retTypeToCpp(fn)
        val params = fn.params.joinToString(", ") { paramToCpp(it) }
        val isVirtual = Modifier.Override in fn.modifiers || Modifier.Open in fn.modifiers

        // Track function parameters that are user-defined class types
        for (p in fn.params) {
            if (p.type != null && isUserDefinedClassType(p.type)) {
                userClassVars.add(p.name)
            }
        }

        val prefix = buildString {
            if (isVirtual) append("virtual ")
            if (Modifier.Override in fn.modifiers) { /* override goes after params */ }
        }

        when (val body = fn.body) {
            null -> {
                val overrideSuffix = if (Modifier.Override in fn.modifiers) " override" else ""
                emit("${prefix}virtual $retType $fnName($params)$overrideSuffix = 0;")
            }
            is ExpressionBody -> {
                val overrideSuffix = if (Modifier.Override in fn.modifiers) " override" else ""
                emit("$retType $fnName($params)$overrideSuffix {")
                indent { emit("return ${genExpr(body.expr)};") }
                emit("}")
            }
            is BlockBody -> {
                val overrideSuffix = if (Modifier.Override in fn.modifiers) " override" else ""
                emit("$retType $fnName($params)$overrideSuffix {")
                indent { body.statements.forEach { genStatement(it) } }
                emit("}")
            }
        }
    }

    /** Emit a forward declaration (prototype) for a top-level function. */
    private fun genFunctionPrototype(fn: FunctionDecl) {
        val retType = retTypeToCpp(fn)
        val params = fn.params.joinToString(", ") { paramToCpp(it) }
        emit("$retType ${fn.name}($params);")
    }

    /** Inner function (nested fun) → local lambda assigned to auto variable. */
    private fun genLocalFunction(fn: FunctionDecl) {
        localFunctionNames.add(fn.name)
        val params = fn.params.joinToString(", ") { p ->
            val t = if (p.type != null) typeToCpp(p.type) else "auto"
            "$t ${p.name}"
        }

        // Check if function is recursive (body references its own name)
        val isRecursive = fn.body != null && functionBodyReferencesName(fn.body, fn.name)

        // For recursive nested functions, use std::function instead of auto
        val varDecl = if (isRecursive) {
            val retType = if (fn.returnType == null || fn.returnType == KotlinType.Unit) "void" else typeToCpp(fn.returnType)
            val paramTypes = fn.params.joinToString(", ") { p ->
                if (p.type != null) typeToCpp(p.type) else "auto"
            }
            "function<$retType($paramTypes)> ${fn.name}"
        } else {
            "auto ${fn.name}"
        }

        when (val body = fn.body) {
            is ExpressionBody -> emit("$varDecl = [&]($params) { return ${genExpr(body.expr)}; };")
            is BlockBody -> {
                emit("$varDecl = [&]($params) {")
                indent { body.statements.forEach { genStatement(it) } }
                emit("};")
            }
            null -> emit("// abstract nested function ${fn.name} skipped")
        }
    }

    /** Checks if a function body references a given name (for detecting recursive calls). */
    private fun functionBodyReferencesName(body: FunctionBody, name: String): Boolean {
        return when (body) {
            is ExpressionBody -> exprReferencesName(body.expr, name)
            is BlockBody -> body.statements.any { stmtReferencesName(it, name) }
        }
    }

    private fun exprReferencesName(expr: Expression, name: String): Boolean = when (expr) {
        is NameReference -> expr.name == name
        is BinaryExpression -> exprReferencesName(expr.left, name) || exprReferencesName(expr.right, name)
        is PrefixExpression -> exprReferencesName(expr.expr, name)
        is PostfixExpression -> exprReferencesName(expr.expr, name)
        is CallExpression -> exprReferencesName(expr.callee, name) || expr.args.any { exprReferencesName(it.value, name) }
            || (expr.trailingLambda?.body?.any { stmtReferencesName(it, name) } ?: false)
        is MethodCallExpression -> exprReferencesName(expr.receiver, name) || expr.args.any { exprReferencesName(it.value, name) }
            || (expr.trailingLambda?.body?.any { stmtReferencesName(it, name) } ?: false)
        is PropertyAccessExpr -> exprReferencesName(expr.receiver, name)
        is IndexAccess -> exprReferencesName(expr.receiver, name) || exprReferencesName(expr.index, name) || expr.additionalIndices.any { exprReferencesName(it, name) }
        is IfExpression -> exprReferencesName(expr.condition, name) || ifBranchReferencesName(expr.thenBranch, name) || ifBranchReferencesName(expr.elseBranch, name)
        is ElvisExpression -> exprReferencesName(expr.left, name) || exprReferencesName(expr.right, name)
        is TypeCheckExpression -> exprReferencesName(expr.expr, name)
        is TypeCastExpression -> exprReferencesName(expr.expr, name)
        is ReturnJumpExpr -> expr.value?.let { exprReferencesName(it, name) } ?: false
        is ThrowJumpExpr -> exprReferencesName(expr.expr, name)
        is ObjectCreation -> expr.args.any { exprReferencesName(it.value, name) }
        is StringTemplate -> expr.parts.any { it is ExpressionStringPart && exprReferencesName(it.expr, name) }
        is LambdaExpression -> expr.body.any { stmtReferencesName(it, name) }
        is WhenExpression -> (expr.subject?.expr?.let { exprReferencesName(it, name) } ?: false) ||
            expr.entries.any { entry ->
                when (val b = entry.body) {
                    is ExpressionEntryBody -> exprReferencesName(b.expr, name)
                    is BlockEntryBody -> b.statements.any { stmtReferencesName(it, name) }
                }
            }
        else -> false
    }

    private fun ifBranchReferencesName(branch: IfBranch, name: String): Boolean = when (branch) {
        is ExprBranch -> exprReferencesName(branch.expr, name)
        is BlockBranch -> branch.statements.any { stmtReferencesName(it, name) }
    }

    private fun stmtReferencesName(stmt: Statement, name: String): Boolean = when (stmt) {
        is ExpressionStatement -> exprReferencesName(stmt.expr, name)
        is ReturnStatement -> stmt.value?.let { exprReferencesName(it, name) } ?: false
        is LocalProperty -> stmt.initializer?.let { exprReferencesName(it, name) } ?: false
        is Assignment -> exprReferencesName(stmt.target, name) || exprReferencesName(stmt.value, name)
        is IfStatement -> exprReferencesName(stmt.condition, name) || stmt.thenBranch.any { stmtReferencesName(it, name) } || (stmt.elseBranch?.any { stmtReferencesName(it, name) } ?: false)
        is ForStatement -> stmt.body.any { stmtReferencesName(it, name) }
        is WhileStatement -> exprReferencesName(stmt.condition, name) || stmt.body.any { stmtReferencesName(it, name) }
        is ThrowStatement -> exprReferencesName(stmt.expr, name)
        is NestedFunctionStatement -> stmt.decl.body?.let { functionBodyReferencesName(it, name) } ?: false
        else -> false
    }

    /** Get the base type name (unwrapping Nullable if needed). */
    private fun getBaseTypeName(type: KotlinType?): String? = when (type) {
        is KotlinType.Simple -> type.name
        is KotlinType.Nullable -> getBaseTypeName(type.inner)
        is KotlinType.Generic -> type.name
        else -> null
    }

    /** Check if a KotlinType refers to a user-defined class (not a primitive/stdlib type). */
    private fun isUserDefinedClassType(type: KotlinType?): Boolean {
        if (type == null) return false
        return when (type) {
            is KotlinType.Simple -> type.name in userDefinedClassNames
            is KotlinType.Nullable -> isUserDefinedClassType(type.inner)
            is KotlinType.Generic -> type.name in userDefinedClassNames
            else -> false
        }
    }

    /** Check if a receiver expression possibly refers to a user-defined class instance.
     *  Used to avoid applying Pair/Map.Entry property mappings (value→second, key→first)
     *  to user-defined class instances that have their own 'value'/'key' properties. */
    private fun isReceiverPossibleUserClass(expr: Expression): Boolean {
        // Direct variable reference that's a known user-class instance
        if (expr is NameReference && expr.name in userClassVars) return true
        // Direct variable reference that's a pointer member (pointing to a user-defined class)
        // but NOT if shadowed by a local variable of a different type
        if (expr is NameReference && expr.name in pointerMembers && expr.name !in localVarNames) return true
        // Property access whose result is a pointer member (e.g. node.left)
        if (expr is PropertyAccessExpr && expr.name in pointerMembers) return true
        // !! on a user-class variable or pointer member
        if (expr is PostfixExpression && expr.op == PostfixOp.NotNull) return isReceiverPossibleUserClass(expr.expr)
        // this expression inside a class
        if (expr is ThisExpression) return true
        return false
    }

    /** Check if an expression refers to a pointer member (for arrow access detection). */
    private fun isExprPointerMember(expr: Expression): Boolean = when (expr) {
        is NameReference -> expr.name in pointerMembers && expr.name !in localVarNames
        is PropertyAccessExpr -> expr.name in pointerMembers
        is PostfixExpression -> if (expr.op == PostfixOp.NotNull) isExprPointerMember(expr.expr) else false
        else -> false
    }

    /** Check if a member property should be emitted as a pointer (lateinit or nullable class type). */
    private fun shouldBePointer(decl: PropertyDecl): Boolean {
        // lateinit members are pointers only when the type is a user-defined class
        // (primitive types like String, Int etc. have default constructors and don't need pointers)
        if (Modifier.Lateinit in decl.modifiers && isUserDefinedClassType(decl.type)) return true
        // Nullable user-defined class types are pointers
        val type = decl.type
        if (type is KotlinType.Nullable && isUserDefinedClassType(type.inner)) return true
        return false
    }

    /** Get the base type name for a pointer member (strips Nullable wrapper). */
    private fun pointerBaseType(type: KotlinType?): String {
        if (type == null) return "auto"
        return when (type) {
            is KotlinType.Nullable -> {
                val inner = type.inner
                if (isUserDefinedClassType(inner)) typeToCpp(inner) else typeToCpp(type)
            }
            else -> typeToCpp(type)
        }
    }

    private fun genClassDecl(cls: ClassDecl) {
        val baseClause = if (cls.superTypes.isNotEmpty()) {
            " : " + cls.superTypes.joinToString(", ") { entry ->
                "public ${typeToCpp(entry.type)}"
            }
        } else ""

        // Emit template declaration if the class has type parameters
        if (cls.typeParams.isNotEmpty()) {
            val templateParams = cls.typeParams.joinToString(", ") { "typename $it" }
            emit("template<$templateParams>")
        }

        // Collect names of members assigned in init blocks (for Bug 5: val without initializer)
        val initBlocks = cls.members.filterIsInstance<InitBlock>()
        val initAssignedNames = mutableSetOf<String>()
        for (ib in initBlocks) {
            for (stmt in ib.body) {
                if (stmt is Assignment && stmt.target is NameReference) {
                    initAssignedNames.add((stmt.target as NameReference).name)
                }
            }
        }

        emit("struct ${cls.name}$baseClause {")
        indent {
            // Constructor params that are val/var become fields
            val fields = cls.primaryConstructor.filter { it.isVal || it.isVar }

            // Determine which constructor params should be pointers (user-defined class types)
            val pointerParamIndices = mutableSetOf<Int>()
            for ((i, p) in cls.primaryConstructor.withIndex()) {
                if (isUserDefinedClassType(p.type)) {
                    pointerParamIndices.add(i)
                }
            }
            classPointerCtorParams[cls.name] = pointerParamIndices

            for (f in fields) {
                val isPointerField = isUserDefinedClassType(f.type)
                if (isPointerField) {
                    emit("${typeToCpp(f.type)}* ${sanitizeName(f.name)};")
                    pointerMembers.add(sanitizeName(f.name))
                    classMemberTypes["${cls.name}.${sanitizeName(f.name)}"] = "${typeToCpp(f.type)}*"
                } else {
                    // Don't use const for val fields — it deletes copy/move assignment
                    // operators, breaking storage in vectors/collections.
                    emit("${typeToCpp(f.type)} ${sanitizeName(f.name)};")
                    classMemberTypes["${cls.name}.${sanitizeName(f.name)}"] = typeToCpp(f.type)
                }
            }
            // Non-field constructor params: NOT emitted as members, only available in constructor
            val nonFieldCtorParams = cls.primaryConstructor.filter { !it.isVal && !it.isVar }
            val nonFieldParamNames = nonFieldCtorParams.map { it.name }.toSet()

            // Member properties — split into those that depend on non-field ctor params
            // (must be initialized in the constructor body) vs those that can use default init
            val memberProps = cls.members.filterIsInstance<MemberProperty>()
            val ctorInitProps = mutableListOf<MemberProperty>()
            val defaultInitProps = mutableListOf<MemberProperty>()
            for (mp in memberProps) {
                val dependsOnCtorParam = mp.decl.initializer != null &&
                    nonFieldParamNames.any { exprReferencesName(mp.decl.initializer, it) }
                if (dependsOnCtorParam) ctorInitProps.add(mp) else defaultInitProps.add(mp)
            }

            // Track member properties that are collections of user classes
            for (mp in memberProps) {
                val decl = mp.decl
                // Check type annotation: List<Edge>, MutableList<Node>, etc.
                if (decl.type is KotlinType.Generic && (decl.type as KotlinType.Generic).typeArgs.any { isUserDefinedClassType(it) }) {
                    userClassCollectionVars.add(sanitizeName(decl.name))
                }
                // Check initializer: mutableListOf<Edge>()
                if (decl.initializer != null && isUserClassCollectionInit(decl.initializer)) {
                    userClassCollectionVars.add(sanitizeName(decl.name))
                }
            }

            // Emit member properties with default initialization
            for (mp in defaultInitProps) {
                if (shouldBePointer(mp.decl)) {
                    // lateinit or nullable class-typed member → emit as pointer
                    val baseType = pointerBaseType(mp.decl.type)
                    emit("$baseType* ${sanitizeName(mp.decl.name)} = nullptr;")
                    pointerMembers.add(sanitizeName(mp.decl.name))
                    classMemberTypes["${cls.name}.${sanitizeName(mp.decl.name)}"] = "$baseType*"
                } else {
                    val init = if (mp.decl.initializer != null) " = ${genExpr(mp.decl.initializer)}" else ""
                    // Don't use const for val members that have no initializer and are assigned in init block
                    val assignedInInit = mp.decl.initializer == null && mp.decl.name in initAssignedNames
                    val useConst = !mp.decl.isMutable && !assignedInInit &&
                        (mp.decl.initializer == null || !isMutableContainerInit(mp.decl.initializer))
                    val constQ = if (useConst) "const " else ""
                    val typeStr = if (mp.decl.type != null) typeToCpp(mp.decl.type)
                                  else inferTypeFromInitializer(mp.decl.initializer)
                    emit("$constQ$typeStr ${sanitizeName(mp.decl.name)}$init;")
                    classMemberTypes["${cls.name}.${sanitizeName(mp.decl.name)}"] = typeStr
                }
            }

            // Emit member properties that depend on ctor params (declaration only, init in constructor)
            for (mp in ctorInitProps) {
                if (shouldBePointer(mp.decl)) {
                    val baseType = pointerBaseType(mp.decl.type)
                    emit("$baseType* ${sanitizeName(mp.decl.name)} = nullptr;")
                    pointerMembers.add(sanitizeName(mp.decl.name))
                } else {
                    val typeStr = if (mp.decl.type != null) typeToCpp(mp.decl.type)
                                  else inferTypeFromInitializer(mp.decl.initializer)
                    emit("$typeStr ${sanitizeName(mp.decl.name)};")
                }
            }

            emit("")

            // Constructor
            if (cls.primaryConstructor.isNotEmpty() || cls.members.any { it is InitBlock }) {
                val ctorParams = cls.primaryConstructor.joinToString(", ") { p ->
                    val isPointerParam = isUserDefinedClassType(p.type)
                    val typeStr = if (isPointerParam) "${typeToCpp(p.type)}*" else typeToCpp(p.type)
                    // val/var fields use name_ suffix to distinguish from the member
                    // Non-field params keep their original name (usable in ctor body)
                    if (p.isVal || p.isVar) "$typeStr ${sanitizeName(p.name)}_"
                    else "$typeStr ${sanitizeName(p.name)}"
                }
                val fieldInits = fields.joinToString(", ") { "${sanitizeName(it.name)}(${sanitizeName(it.name)}_)" }
                emit("${cls.name}($ctorParams)${if (fieldInits.isNotEmpty()) " : $fieldInits" else ""} {")
                indent {
                    // Initialize member properties that depend on ctor params
                    for (mp in ctorInitProps) {
                        emit("${sanitizeName(mp.decl.name)} = ${genExpr(mp.decl.initializer!!)};")
                    }
                    // init blocks
                    for (m in cls.members) {
                        if (m is InitBlock) m.body.forEach { genStatement(it) }
                    }
                }
                emit("}")
            }

            emit("")

            // Member functions — emit declarations only; full definitions are deferred
            for (m in cls.members) {
                when (m) {
                    is MemberFunction -> {
                        genMemberFunctionDeclaration(m.decl, cls.name)
                        deferMemberFunctionDefinition(cls, m.decl)
                    }
                    is CompanionObject -> {
                        emit("// companion object members:")
                        for (cm in m.members) {
                            if (cm is MemberFunction) {
                                emit("static ${genMemberFunctionSignature(cm.decl)};")
                                deferStaticMemberFunctionDefinition(cls, cm.decl)
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Data class: ==, < operators
            if (cls.kind == ClassKind.Data && fields.isNotEmpty()) {
                val eqBody = fields.joinToString(" && ") { "${it.name} == o.${it.name}" }
                emit("bool operator==(const ${cls.name}& o) const { return $eqBody; }")
                val ltBody = genLexicographicLt(fields.map { it.name }, "o")
                emit("bool operator<(const ${cls.name}& o) const { return $ltBody; }")
            }
        }
        emit("};")
        emit("")
    }

    /** Generate just the signature string for a member function (no body, no semicolon). */
    private fun genMemberFunctionSignature(fn: FunctionDecl): String {
        val fnName = if (Modifier.Operator in fn.modifiers) {
            kotlinOperatorToCpp(fn.name, fn.params.size) ?: fn.name
        } else fn.name
        val retType = retTypeToCpp(fn)
        val params = fn.params.joinToString(", ") { paramToCpp(it) }
        val isVirtual = Modifier.Override in fn.modifiers || Modifier.Open in fn.modifiers
        val overrideSuffix = if (Modifier.Override in fn.modifiers) " override" else ""
        val prefix = if (isVirtual) "virtual " else ""
        return if (Modifier.Abstract in fn.modifiers) {
            "virtual $retType $fnName($params) = 0"
        } else {
            "$prefix$retType $fnName($params)$overrideSuffix"
        }
    }

    /** Emit a member function declaration (signature + semicolon) inside the struct.
     *  For expression-body functions with no explicit return type, tries to infer a concrete
     *  return type so the function can be called before its out-of-line definition. */
    private fun genMemberFunctionDeclaration(fn: FunctionDecl, className: String? = null) {
        if (fn.returnType == null && fn.body is ExpressionBody) {
            val inferredType = inferExprType((fn.body as ExpressionBody).expr, className)
            if (inferredType != null) {
                val fnName = if (Modifier.Operator in fn.modifiers) {
                    kotlinOperatorToCpp(fn.name, fn.params.size) ?: fn.name
                } else fn.name
                val params = fn.params.joinToString(", ") { paramToCpp(it) }
                emit("$inferredType $fnName($params);")
                return
            }
        }
        emit("${genMemberFunctionSignature(fn)};")
    }

    /** Try to infer the C++ type of an expression from its structure. Returns null if unknown.
     *  @param currentClass The name of the class this expression appears in (for member lookups). */
    private fun inferExprType(expr: Expression, currentClass: String? = null): String? = when (expr) {
        // Constructor call: Vec2(...) → Vec2
        is CallExpression -> {
            val calleeName = (expr.callee as? NameReference)?.name
            if (calleeName != null && calleeName in userDefinedClassNames) calleeName
            // max/min/maxOf/minOf: return type matches first argument
            else if (calleeName in setOf("max", "min", "maxOf", "minOf") && expr.args.isNotEmpty())
                inferExprType(expr.args[0].value, currentClass)
            else null
        }
        // Property access: look up the member type if we know the receiver's class
        is PropertyAccessExpr -> {
            val receiverClass = inferExprClassName(expr.receiver, currentClass)
            if (receiverClass != null) classMemberTypes["$receiverClass.${expr.name}"]
            else null
        }
        // Binary expressions: result type matches operand types
        is BinaryExpression -> inferExprType(expr.left, currentClass) ?: inferExprType(expr.right, currentClass)
        // String template → string
        is StringTemplate -> "string"
        // Literals
        is IntLiteral -> "int"
        is LongLiteral -> "long long"
        is DoubleLiteral -> "double"
        is BooleanLiteral -> "bool"
        is CharLiteral -> "char"
        is StringLiteral -> "string"
        else -> null
    }

    /** Try to infer the user-defined class name of an expression, given the current class context. */
    private fun inferExprClassName(expr: Expression, currentClass: String? = null): String? = when (expr) {
        is IndexAccess -> {
            // array[i] — check if the member is a collection whose element type is a known class
            val recv = expr.receiver
            if (recv is NameReference && currentClass != null) {
                val memberType = classMemberTypes["$currentClass.${recv.name}"]
                if (memberType != null) {
                    // Extract element type from vector<X>, std::vector<X>, etc.
                    val match = Regex("""(?:vector|std::vector)<(\w+)>""").find(memberType)
                    match?.groupValues?.get(1)
                } else null
            } else null
        }
        else -> null
    }

    /** Defer the out-of-line definition of a member function. */
    private fun deferMemberFunctionDefinition(cls: ClassDecl, fn: FunctionDecl) {
        // Abstract methods have no body to defer
        if (Modifier.Abstract in fn.modifiers || fn.body == null) return

        val fnName = if (Modifier.Operator in fn.modifiers) {
            kotlinOperatorToCpp(fn.name, fn.params.size) ?: fn.name
        } else fn.name
        // Use inferred type if available (must match the declaration)
        val retType = if (fn.returnType == null && fn.body is ExpressionBody) {
            inferExprType((fn.body as ExpressionBody).expr, cls.name) ?: retTypeToCpp(fn)
        } else retTypeToCpp(fn)
        val params = fn.params.joinToString(", ") { paramToCpp(it) }

        // Build template prefix and class qualifier
        val templatePrefix = if (cls.typeParams.isNotEmpty()) {
            val tParams = cls.typeParams.joinToString(", ") { "typename $it" }
            "template<$tParams>\n"
        } else ""
        val classQualifier = if (cls.typeParams.isNotEmpty()) {
            "${cls.name}<${cls.typeParams.joinToString(", ")}>"
        } else cls.name

        // Use a sub-generator to capture the body output
        val sub = createSubGenerator()

        // Track function parameters that are user-defined class types (same as genFunctionDecl)
        for (p in fn.params) {
            if (p.type != null && isUserDefinedClassType(p.type)) {
                sub.userClassVars.add(p.name)
            }
        }

        when (val body = fn.body) {
            is ExpressionBody -> {
                sub.emit("$templatePrefix$retType $classQualifier::$fnName($params) {")
                sub.indent { sub.emit("return ${sub.genExpr(body.expr)};") }
                sub.emit("}")
            }
            is BlockBody -> {
                sub.emit("$templatePrefix$retType $classQualifier::$fnName($params) {")
                sub.indent { body.statements.forEach { sub.genStatement(it) } }
                sub.emit("}")
            }
            null -> {} // should not happen given the guard above
        }

        deferredMethodDefs.add(sub.out.toString())
    }

    /** Defer the out-of-line definition of a static (companion object) member function. */
    private fun deferStaticMemberFunctionDefinition(cls: ClassDecl, fn: FunctionDecl) {
        if (fn.body == null) return

        val fnName = if (Modifier.Operator in fn.modifiers) {
            kotlinOperatorToCpp(fn.name, fn.params.size) ?: fn.name
        } else fn.name
        val retType = retTypeToCpp(fn)
        val params = fn.params.joinToString(", ") { paramToCpp(it) }

        val templatePrefix = if (cls.typeParams.isNotEmpty()) {
            val tParams = cls.typeParams.joinToString(", ") { "typename $it" }
            "template<$tParams>\n"
        } else ""
        val classQualifier = if (cls.typeParams.isNotEmpty()) {
            "${cls.name}<${cls.typeParams.joinToString(", ")}>"
        } else cls.name

        val sub = createSubGenerator()
        for (p in fn.params) {
            if (p.type != null && isUserDefinedClassType(p.type)) {
                sub.userClassVars.add(p.name)
            }
        }

        when (val body = fn.body) {
            is ExpressionBody -> {
                sub.emit("$templatePrefix$retType $classQualifier::$fnName($params) {")
                sub.indent { sub.emit("return ${sub.genExpr(body.expr)};") }
                sub.emit("}")
            }
            is BlockBody -> {
                sub.emit("$templatePrefix$retType $classQualifier::$fnName($params) {")
                sub.indent { body.statements.forEach { sub.genStatement(it) } }
                sub.emit("}")
            }
            null -> {}
        }

        deferredMethodDefs.add(sub.out.toString())
    }

    private fun genLexicographicLt(fields: List<String>, other: String): String {
        if (fields.isEmpty()) return "false"
        if (fields.size == 1) return "${fields[0]} < $other.${fields[0]}"
        val head = fields[0]
        val rest = genLexicographicLt(fields.drop(1), other)
        return "$head < $other.$head || ($head == $other.$head && ($rest))"
    }

    private fun genObjectDecl(obj: ObjectDecl) {
        emit("struct ${obj.name} {")
        indent {
            for (m in obj.members) {
                when (m) {
                    is MemberFunction -> genFunctionDecl(m.decl)
                    is MemberProperty -> {
                        val typeStr = if (m.decl.type != null) typeToCpp(m.decl.type) else "auto"
                        val init = if (m.decl.initializer != null) " = ${genExpr(m.decl.initializer)}" else ""
                        emit("static $typeStr ${m.decl.name}$init;")
                    }
                    else -> {}
                }
            }
        }
        emit("} ${obj.name.replaceFirstChar { it.lowercaseChar() }};")
    }

    private fun genTopLevelProperty(prop: PropertyDecl) {
        val typeStr = if (prop.type != null) typeToCpp(prop.type) else "auto"
        val constQ  = if (!prop.isMutable) "const " else ""
        val init    = if (prop.initializer != null) " = ${genExpr(prop.initializer)}" else ""
        emit("$constQ$typeStr ${prop.name}$init;")
    }

    // ─── Statements ──────────────────────────────────────────────────────────

    fun genStatement(stmt: Statement) {
        when (stmt) {
            is ExpressionStatement    -> {
                when (val e = stmt.expr) {
                    is ReturnJumpExpr  -> genReturnStatement(ReturnStatement(e.value, e.label))
                    is ThrowJumpExpr   -> emit("throw ${genExpr(e.expr)};")
                    is BreakJumpExpr   -> if (e.label != null) { pendingGotoLabels.add(e.label); emit("goto _lbl_${e.label};") } else emit("break;")
                    is ContinueJumpExpr -> if (e.label != null) { pendingGotoLabels.add("${e.label}_cont"); emit("goto _lbl_${e.label}_cont;") } else emit("continue;")
                    else -> emit("${genExpr(e)};")
                }
            }
            is LocalProperty          -> genLocalProperty(stmt)
            is DestructuringDeclaration -> genDestructuringDecl(stmt)
            is Assignment             -> genAssignment(stmt)
            is IfStatement            -> genIfStatement(stmt)
            is WhenStatement          -> genWhenStatement(stmt)
            is ForStatement           -> genForStatement(stmt)
            is WhileStatement         -> genWhileStatement(stmt)
            is ReturnStatement        -> genReturnStatement(stmt)
            is BreakStatement         -> emit("break;")
            is LabeledBreak           -> {
                pendingGotoLabels.add(stmt.label)
                emit("goto _lbl_${stmt.label};")
            }
            is ContinueStatement      -> emit("continue;")
            is LabeledContinue        -> {
                pendingGotoLabels.add("${stmt.label}_cont")
                emit("goto _lbl_${stmt.label}_cont;")
            }
            is ThrowStatement         -> emit("throw ${genExpr(stmt.expr)};")
            is LabeledStatement       -> genLabeledStatement(stmt)
            is NestedFunctionStatement -> genLocalFunction(stmt.decl)
        }
    }

    private fun genLocalProperty(p: LocalProperty) {
        // Track this as a local variable (to distinguish from class members in pointer checks)
        localVarNames.add(p.name)
        // Track StringBuilder variables for .toString() → .str() mapping
        if (p.initializer != null && isStringBuilderInit(p.initializer)) {
            stringBuilderVars.add(p.name)
        }
        // Track set variables for .add() → .insert() mapping
        if (p.initializer != null && isSetInit(p.initializer)) {
            setVars.add(p.name)
        }
        // Track PQ and Stack variables
        if (p.initializer != null && isPQInit(p.initializer)) {
            pqVars.add(p.name)
        }
        if (p.initializer != null && isJavaStackInit(p.initializer)) {
            stackVars.add(p.name)
        }
        // Track variables initialized with user-defined class constructors
        if (p.initializer != null && isUserClassInit(p.initializer)) {
            userClassVars.add(p.name)
        }
        // Track variables whose explicit type is a user-defined class
        if (p.type != null && isUserDefinedClassType(p.type)) {
            userClassVars.add(p.name)
        }
        // Track collection variables whose elements are user-defined classes
        if (p.initializer != null && isUserClassCollectionInit(p.initializer)) {
            userClassCollectionVars.add(p.name)
        }
        // Track collections by explicit type: List<Edge>, MutableList<Node>, etc.
        if (p.type is KotlinType.Generic && (p.type as KotlinType.Generic).typeArgs.any { isUserDefinedClassType(it) }) {
            userClassCollectionVars.add(p.name)
        }
        // Detect variable shadowing: `var n = n` in Kotlin is valid but `auto n = n;` in C++
        // is self-referential. Skip the declaration — in C++ the outer variable (e.g. for-loop
        // variable) is already mutable, so the shadowing copy is unnecessary.
        val isSelfShadow = p.initializer is NameReference
            && (p.initializer as NameReference).name == p.name
        if (isSelfShadow) {
            emit("// var ${p.name} = ${p.name} (self-shadow, skipped)")
            return
        }
        // Check if this is a nullable user-defined class type → pointer semantics
        val isNullableUserClass = p.type is KotlinType.Nullable &&
            isUserDefinedClassType((p.type as KotlinType.Nullable).inner)
        if (isNullableUserClass) {
            pointerMembers.add(p.name)
            val innerType = typeToCpp((p.type as KotlinType.Nullable).inner)
            val init = if (p.initializer == null) ""
            else if (p.initializer is NullLiteral) " = nullptr"
            else if (isObjectCreationExpr(p.initializer)) " = new ${genExpr(p.initializer)}"
            else if (isAlreadyPointerExpr(p.initializer)) " = ${genExpr(p.initializer)}"
            else " = &${genExpr(p.initializer)}"
            val varName = sanitizeName(p.name)
            emit("$innerType* $varName$init;")
            return
        }
        val init = if (p.initializer != null) " = ${genExpr(p.initializer)}" else ""
        // Kotlin val = immutable reference, but the object may be mutable (e.g. mutableListOf).
        // In C++ const prevents mutation of the object itself, so skip const for mutable containers.
        val useConst = !p.isMutable && p.initializer != null && !isMutableContainerInit(p.initializer)
        val varName = sanitizeName(p.name)
        if (p.type != null) {
            val constQ = if (useConst) "const " else ""
            emit("$constQ${typeToCpp(p.type)} $varName$init;")
        } else {
            val constQ = if (useConst) "const " else ""
            emit("${constQ}auto $varName$init;")
        }
    }

    private fun isStringBuilderInit(expr: Expression): Boolean = when (expr) {
        is CallExpression -> expr.callee is NameReference && (expr.callee as NameReference).name == "StringBuilder"
        is ObjectCreation -> {
            val typeName = when (expr.type) {
                is KotlinType.Simple -> (expr.type as KotlinType.Simple).name
                is KotlinType.Generic -> (expr.type as KotlinType.Generic).name
                else -> ""
            }
            typeName == "StringBuilder"
        }
        else -> false
    }

    private val setTypeNames = setOf(
        "TreeSet", "HashSet", "LinkedHashSet",
        "mutableSetOf", "hashSetOf", "sortedSetOf", "setOf", "linkedSetOf"
    )

    private fun isSetInit(expr: Expression): Boolean = when (expr) {
        is CallExpression -> expr.callee is NameReference && (expr.callee as NameReference).name in setTypeNames
        is ObjectCreation -> {
            val typeName = when (expr.type) {
                is KotlinType.Simple -> (expr.type as KotlinType.Simple).name
                is KotlinType.Generic -> (expr.type as KotlinType.Generic).name
                else -> ""
            }
            typeName in setTypeNames
        }
        else -> false
    }

    private fun isPQInit(expr: Expression): Boolean = when (expr) {
        is CallExpression -> expr.callee is NameReference && (expr.callee as NameReference).name == "PriorityQueue"
        else -> false
    }

    private fun isJavaStackInit(expr: Expression): Boolean = when (expr) {
        is CallExpression -> expr.callee is NameReference && (expr.callee as NameReference).name == "Stack"
                && (expr.callee as NameReference).name !in userDefinedClassNames
        else -> false
    }

    private fun isUserClassInit(expr: Expression): Boolean = when (expr) {
        is CallExpression -> expr.callee is NameReference && (expr.callee as NameReference).name in userDefinedClassNames
        is ObjectCreation -> {
            val typeName = when (expr.type) {
                is KotlinType.Simple -> (expr.type as KotlinType.Simple).name
                is KotlinType.Generic -> (expr.type as KotlinType.Generic).name
                else -> ""
            }
            typeName in userDefinedClassNames
        }
        // Index access on a user class collection yields a user class instance
        is IndexAccess -> isExprUserClassCollection(expr.receiver)
        // Property access on a user class that returns a pointer member (another user class)
        is PropertyAccessExpr -> expr.name in pointerMembers
        else -> false
    }

    /** Check if an expression refers to a collection of user-defined class elements. */
    private fun isExprUserClassCollection(expr: Expression): Boolean {
        if (expr is NameReference && expr.name in userClassCollectionVars) return true
        // Property access whose name is a known user class collection member
        if (expr is PropertyAccessExpr && expr.name in userClassCollectionVars) return true
        // Index access on a user class collection (nested indexing)
        if (expr is IndexAccess && isExprUserClassCollection(expr.receiver)) return true
        return false
    }

    /** Check if a collection initializer contains user-defined class elements. */
    private fun isUserClassCollectionInit(expr: Expression): Boolean {
        // mutableListOf<Edge>(), listOf<Edge>(), arrayListOf<Edge>(), etc.
        if (expr is CallExpression && expr.typeArgs.any { isUserDefinedClassType(it) }) return true
        // Array(n) { ... } or Array<Edge>(n) { ... }
        if (expr is CallExpression && expr.callee is NameReference) {
            val name = (expr.callee as NameReference).name
            if (name == "Array" || name == "arrayOf" || name == "arrayOfNulls") {
                if (expr.typeArgs.any { isUserDefinedClassType(it) }) return true
            }
        }
        return false
    }

    /**
     * Returns true if the expression initializes a mutable container or a user-defined
     * class instance.  For `val` declarations, `const` should only be applied to primitive
     * types, strings, and simple immutable values — never to class instances whose internal
     * state may be mutated through methods.
     */
    /** Returns true if the expression produces a list/vector (for detecting list concatenation with +). */
    private fun isListExpression(expr: Expression): Boolean {
        val listCallNames = setOf(
            "listOf", "mutableListOf", "arrayListOf", "emptyList", "arrayOf",
            "List", "MutableList", "Array",
            "IntArray", "LongArray", "DoubleArray", "FloatArray", "BooleanArray", "CharArray",
            "intArrayOf", "longArrayOf", "doubleArrayOf", "floatArrayOf", "booleanArrayOf", "charArrayOf"
        )
        return when (expr) {
            is CallExpression -> expr.callee is NameReference && (expr.callee as NameReference).name in listCallNames
            is MethodCallExpression -> expr.method in setOf(
                "map", "filter", "sorted", "sortedBy", "sortedDescending", "sortedByDescending",
                "sortedWith", "reversed", "toList", "toMutableList", "subList", "take", "drop",
                "flatMap", "mapNotNull", "distinct", "zip"
            )
            is BinaryExpression -> expr.op == BinaryOp.Plus && (isListExpression(expr.left) || isListExpression(expr.right))
            else -> false
        }
    }

    private fun isMutableContainerInit(expr: Expression): Boolean {
        val mutableCallNames = setOf(
            "mutableListOf", "mutableSetOf", "mutableMapOf", "arrayListOf",
            "hashMapOf", "hashSetOf", "linkedMapOf", "linkedSetOf",
            "ArrayDeque", "PriorityQueue", "Stack", "TreeSet", "TreeMap",
            "ArrayList", "LinkedList", "HashMap", "LinkedHashMap",
            "MutableList", "StringBuilder"
        )
        // Primitive / immutable types whose val declarations can safely be const
        val immutableTypes = setOf(
            "Int", "Long", "Double", "Float", "Boolean", "Char", "Byte", "Short",
            "String", "UInt", "ULong", "UByte", "UShort"
        )
        return when (expr) {
            is CallExpression -> {
                val callee = expr.callee
                if (callee is NameReference) {
                    // Known mutable containers
                    if (callee.name in mutableCallNames) return true
                    // If the name starts with an uppercase letter and is not a known
                    // immutable type or stdlib factory, treat it as a class constructor
                    // whose instance could have mutable state.
                    if (callee.name[0].isUpperCase() && callee.name !in immutableTypes
                        && callee.name !in setOf("Pair", "Triple")) return true
                }
                false
            }
            is ObjectCreation -> {
                val typeName = when (expr.type) {
                    is KotlinType.Simple -> (expr.type as KotlinType.Simple).name
                    is KotlinType.Generic -> (expr.type as KotlinType.Generic).name
                    else -> ""
                }
                // Any ObjectCreation is a class instance; skip const
                true
            }
            else -> false
        }
    }

    private fun genDestructuringDecl(d: DestructuringDeclaration) {
        val typeStr = if (d.type != null) typeToCpp(d.type) else "auto"
        val names = d.names.joinToString(", ") { it ?: "_" }
        emit("auto [$names] = ${genExpr(d.initializer)};")
    }

    private fun genAssignment(a: Assignment) {
        val op = when (a.op) {
            AssignOp.Assign      -> "="
            AssignOp.PlusAssign  -> "+="
            AssignOp.MinusAssign -> "-="
            AssignOp.TimesAssign -> "*="
            AssignOp.DivAssign   -> "/="
            AssignOp.ModAssign   -> "%="
        }
        val target = genExpr(a.target)
        // Check if the assignment target is a pointer member (lateinit or nullable class-typed)
        val targetIsPointer = isAssignmentTargetPointer(a.target)
        val value = if (targetIsPointer && a.op == AssignOp.Assign) {
            when {
                // Heap-allocate: obj.ptr = new Type(args)
                isObjectCreationExpr(a.value) -> "new ${genExpr(a.value)}"
                // Null literal: obj.ptr = nullptr
                a.value is NullLiteral -> "nullptr"
                // Already a pointer (another pointer member): obj.ptr = other.ptr
                isAssignmentValuePointer(a.value) -> genExpr(a.value)
                // Value-type variable assigned to pointer: obj.ptr = &varName
                else -> "&${genExpr(a.value)}"
            }
        } else {
            genExpr(a.value)
        }
        emit("$target $op $value;")
    }

    /** Check if an assignment target refers to a pointer member. */
    private fun isAssignmentTargetPointer(target: Expression): Boolean {
        return when (target) {
            // Bare name: only treat as pointer if it's a known pointer member AND not shadowed by a local variable
            is NameReference -> target.name in pointerMembers && target.name !in localVarNames
            is PropertyAccessExpr -> target.name in pointerMembers
            else -> false
        }
    }

    /** Check if an assignment RHS expression is already a pointer value (no & needed). */
    private fun isAssignmentValuePointer(expr: Expression): Boolean {
        // Another pointer member variable (but not if shadowed by a local variable)
        if (expr is NameReference && expr.name in pointerMembers && expr.name !in localVarNames) return true
        // Property access to a pointer member (e.g. other.neighbor)
        if (expr is PropertyAccessExpr && expr.name in pointerMembers) return true
        // !! on a pointer (just strips null check)
        if (expr is PostfixExpression && expr.op == PostfixOp.NotNull) return isAssignmentValuePointer(expr.expr)
        return false
    }

    /** Check if an expression is an object creation (constructor call) for a user-defined class. */
    private fun isObjectCreationExpr(expr: Expression): Boolean {
        return when (expr) {
            is ObjectCreation -> isUserDefinedClassType(expr.type)
            is CallExpression -> expr.callee is NameReference &&
                (expr.callee as NameReference).name in userDefinedClassNames
            else -> false
        }
    }

    private fun genIfStatement(s: IfStatement) {
        emit("if (${genExpr(s.condition)}) {")
        indent { s.thenBranch.forEach { genStatement(it) } }
        if (s.elseBranch != null) {
            if (s.elseBranch.size == 1 && s.elseBranch[0] is IfStatement) {
                emitRaw("${INDENT.repeat(indentLevel)}}")
                out.append(" else ")
                val inner = s.elseBranch[0] as IfStatement
                // inline else-if
                out.appendLine("if (${genExpr(inner.condition)}) {")
                indent { inner.thenBranch.forEach { genStatement(it) } }
                if (inner.elseBranch != null) {
                    emit("} else {")
                    indent { inner.elseBranch.forEach { genStatement(it) } }
                }
                emit("}")
            } else {
                emit("} else {")
                indent { s.elseBranch.forEach { genStatement(it) } }
                emit("}")
            }
        } else {
            emit("}")
        }
    }

    private fun genWhenStatement(s: WhenStatement) {
        genWhenEntries(s.subject, s.entries, asExpr = false)
    }

    private fun genWhenEntries(subject: WhenSubject?, entries: List<WhenEntry>, asExpr: Boolean): String? {
        val subjectExpr = subject?.expr?.let { genExpr(it) }
        val binding = subject?.binding

        var first = true
        for (entry in entries) {
            val isElse = entry.conditions.isEmpty() || entry.conditions.any { it is ElseCondition }
            val cond = if (isElse) null else buildWhenCondition(entry.conditions, subjectExpr)

            if (first) {
                if (cond != null) emit("if ($cond) {") else emit("{")
                first = false
            } else {
                if (cond != null) emit("} else if ($cond) {") else emit("} else {")
            }

            indent {
                if (binding != null && subjectExpr != null) {
                    // Subject binding – hoist it
                }
                when (val body = entry.body) {
                    is BlockEntryBody -> body.statements.forEach { genStatement(it) }
                    is ExpressionEntryBody -> {
                        if (asExpr) emit("return ${genExpr(body.expr)};")
                        else emit("${genExpr(body.expr)};")
                    }
                }
            }
        }
        if (!first) emit("}")
        return null
    }

    private fun buildWhenCondition(conditions: List<WhenCondition>, subject: String?): String {
        return conditions.joinToString(" || ") { cond ->
            when (cond) {
                is ExpressionCondition ->
                    if (subject != null) "$subject == ${genExpr(cond.expr)}" else genExpr(cond.expr)
                is RangeCondition -> {
                    val rangeExpr = cond.range
                    val check = if (subject != null && rangeExpr is RangeExpression) {
                        val lo = genExpr(rangeExpr.start)
                        val hi = genExpr(rangeExpr.end)
                        when (rangeExpr.kind) {
                            RangeKind.Inclusive -> "($subject >= $lo && $subject <= $hi)"
                            RangeKind.Until    -> "($subject >= $lo && $subject < $hi)"
                            RangeKind.DownTo   -> "($subject <= $lo && $subject >= $hi)"
                        }
                    } else {
                        val rangeCpp = genExpr(rangeExpr)
                        if (subject != null) "/* in range */ $rangeCpp" else rangeCpp
                    }
                    if (cond.negated) "!($check)" else check
                }
                is TypeCondition -> {
                    val typeCpp = typeToCpp(cond.type)
                    val check = if (subject != null) "dynamic_cast<$typeCpp*>(&($subject)) != nullptr" else "false"
                    if (cond.negated) "!($check)" else check
                }
                is ElseCondition -> "true"
            }
        }
    }

    private fun genForStatement(s: ForStatement) {
        when (val iter = s.iterable) {
            is RangeExpression -> genForRange(s.variable, iter, s.body)
            is CallExpression  -> {
                // Special case: indices
                val callee = iter.callee
                if (callee is PropertyAccessExpr && callee.name == "indices") {
                    val recv = genExpr(callee.receiver)
                    val varName = (s.variable as? SimpleVar)?.name ?: "_i"
                    emit("for (int $varName = 0; $varName < (int)$recv.size(); $varName++) {")
                    indent {
                        genGotoContLabel(s)
                        s.body.forEach { genStatement(it) }
                    }
                    emit("}")
                    genGotoBreakLabel(s)
                    return
                }
                genForCollection(s.variable, iter, s.body)
            }
            else -> genForCollection(s.variable, iter, s.body)
        }
    }

    private fun genForRange(variable: ForLoopVariable, range: RangeExpression, body: List<Statement>) {
        val varName = (variable as? SimpleVar)?.name ?: "_i"
        val start = genExpr(range.start)
        val end   = genExpr(range.end)
        val step  = range.step?.let { genExpr(it) }

        when (range.kind) {
            RangeKind.Inclusive -> {
                val inc = if (step != null) "$varName += $step" else "$varName++"
                emit("for (auto $varName = $start; $varName <= $end; $inc) {")
            }
            RangeKind.Until -> {
                val inc = if (step != null) "$varName += $step" else "$varName++"
                emit("for (auto $varName = $start; $varName < $end; $inc) {")
            }
            RangeKind.DownTo -> {
                val dec = if (step != null) "$varName -= $step" else "$varName--"
                emit("for (auto $varName = $start; $varName >= $end; $dec) {")
            }
        }
        indent { body.forEach { genStatement(it) } }
        emit("}")
    }

    private fun genForCollection(variable: ForLoopVariable, iterable: Expression, body: List<Statement>) {
        val iterCpp = genExpr(iterable)
        val varDecl = when (variable) {
            is SimpleVar       -> "auto& ${variable.name}"
            is DestructuredVar -> {
                val names = variable.names.joinToString(", ") { it ?: "_" }
                "auto& [$names]"
            }
        }
        emit("for ($varDecl : $iterCpp) {")
        indent { body.forEach { genStatement(it) } }
        emit("}")
    }

    // Emit a continue-goto label if needed
    private fun genGotoContLabel(s: ForStatement) {
        // Only emit if there's a labeled continue targeting this loop
        // (Handled lazily: the label is emitted at the END of the loop body)
    }
    private fun genGotoBreakLabel(s: ForStatement) { /* similarly handled in LabeledStatement */ }

    private fun genWhileStatement(s: WhileStatement) {
        if (s.isDoWhile) {
            emit("do {")
            indent { s.body.forEach { genStatement(it) } }
            emit("} while (${genExpr(s.condition)});")
        } else {
            emit("while (${genExpr(s.condition)}) {")
            indent { s.body.forEach { genStatement(it) } }
            emit("}")
        }
    }

    private fun genReturnStatement(s: ReturnStatement) {
        if (s.label != null) {
            // return@forEach (or other inline lambda label) → continue (skip to next iteration)
            if (s.label in inlineLambdaLabels) {
                emit("continue;")
                return
            }
            // return@outerLabel → goto to labeled break target
            pendingGotoLabels.add(s.label)
            emit("goto _lbl_${s.label};")
            return
        }
        if (s.value != null) emit("return ${genExpr(s.value)};")
        else emit("return;")
    }

    private fun genLabeledStatement(s: LabeledStatement) {
        // The label applies to the inner statement.  For loops, we place goto targets
        // immediately before (break) and at end of body (continue).
        val label = s.label
        val inner = s.stmt

        if (inner is ForStatement || inner is WhileStatement) {
            // Emit the loop (it will be followed by the break label below)
            genStatement(inner)
            // Break goto label after the loop
            if (pendingGotoLabels.contains(label)) emit("_lbl_$label:;")
            // Continue goto label inside loop body is handled separately
        } else {
            genStatement(inner)
            if (pendingGotoLabels.contains(label)) emit("_lbl_$label:;")
        }
    }

    // ─── Expression generation ────────────────────────────────────────────────

    fun genExpr(expr: Expression): String = when (expr) {
        is IntLiteral       -> expr.value.toString()
        is LongLiteral      -> "${expr.value}LL"
        is DoubleLiteral    -> expr.value.toString().let { if ('.' in it || 'e' in it) it else "$it.0" }
        is FloatLiteral     -> "${expr.value}f"
        is BooleanLiteral   -> if (expr.value) "true" else "false"
        is CharLiteral      -> "'${escapeChar(expr.value)}'"
        is StringLiteral    -> "\"${escapeString(expr.value)}\""
        is NullLiteral      -> "nullptr"

        is StringTemplate   -> genStringTemplate(expr)

        is NameReference    -> sanitizeName(expr.name)
        is PropertyAccessExpr -> genPropertyAccess(expr)
        is IndexAccess      -> if (expr.additionalIndices.isNotEmpty()) {
            val allArgs = (listOf(expr.index) + expr.additionalIndices).joinToString(", ") { genExpr(it) }
            "${genExpr(expr.receiver)}.get($allArgs)"
        } else {
            "${genExpr(expr.receiver)}[${genExpr(expr.index)}]"
        }
        is ThisExpression   -> "this"
        is SuperExpression  -> "/* super */"

        is BinaryExpression -> genBinaryExpr(expr)
        is PrefixExpression -> genPrefixExpr(expr)
        is PostfixExpression -> genPostfixExpr(expr)
        is TypeCheckExpression -> genTypeCheck(expr)
        is TypeCastExpression  -> genTypeCast(expr)
        is ElvisExpression     -> genElvisExpr(expr)
        is RangeExpression     -> genRangeAsValue(expr)

        is CallExpression         -> genCallExpr(expr)
        is MethodCallExpression   -> genMethodCallExpr(expr)
        is LambdaExpression       -> genLambdaAsCppLambda(expr)
        is IfExpression           -> genIfExpression(expr)
        is ReturnJumpExpr         -> { if (expr.value != null) "/* return ${genExpr(expr.value)} */" else "/* return */" }
        is ThrowJumpExpr          -> "throw ${genExpr(expr.expr)}"
        is BreakJumpExpr          -> { if (expr.label != null) { pendingGotoLabels.add(expr.label); "goto _lbl_${expr.label}" } else "break" }
        is ContinueJumpExpr       -> { if (expr.label != null) { pendingGotoLabels.add("${expr.label}_cont"); "goto _lbl_${expr.label}_cont" } else "continue" }
        is WhenExpression      -> genWhenExpression(expr)
        is ObjectCreation      -> genObjectCreation(expr)
        is AnonymousObject     -> "/* anon object */"
    }

    private fun genPropertyAccess(expr: PropertyAccessExpr): String {
        // Numeric type companion constants: Int.MAX_VALUE, Long.MIN_VALUE, etc.
        if (expr.receiver is NameReference) {
            val typeName = (expr.receiver as NameReference).name
            val constMapping = numericConstant(typeName, expr.name)
            if (constMapping != null) return constMapping
        }

        val recv = genExpr(expr.receiver)
        val safe = expr.isSafe

        // Check if receiver is a user-defined class variable — skip stdlib property mappings
        val isUserClassReceiver = (expr.receiver is NameReference && (expr.receiver as NameReference).name in userClassVars)
            || (expr.receiver is ThisExpression)
            || (expr.receiver is PostfixExpression && (expr.receiver as PostfixExpression).op == PostfixOp.NotNull
                && (expr.receiver as PostfixExpression).expr is NameReference
                && ((expr.receiver as PostfixExpression).expr as NameReference).name in userClassVars)
            // Also treat receiver as user-class if it's a property access whose final property
            // is a pointer member (user-defined class field), e.g. node.left.value
            || (expr.receiver is PropertyAccessExpr && (expr.receiver as PropertyAccessExpr).name in pointerMembers)
            // Or receiver is !! on a property access to a pointer member
            || (expr.receiver is PostfixExpression && (expr.receiver as PostfixExpression).op == PostfixOp.NotNull
                && (expr.receiver as PostfixExpression).expr is PropertyAccessExpr
                && ((expr.receiver as PostfixExpression).expr as PropertyAccessExpr).name in pointerMembers)
        // Check if receiver is a pointer member (lateinit or nullable class type)
        val isPointerReceiver = expr.receiver is NameReference && (expr.receiver as NameReference).name in pointerMembers
        // Check if the receiver is a property access whose final name is a pointer member
        val isNestedPointerAccess = expr.receiver is PropertyAccessExpr &&
            (expr.receiver as PropertyAccessExpr).name in pointerMembers
        // Check if receiver is a !! (not-null assertion) on a pointer member: e.g. root.left!!.value
        val isNotNullOnPointer = expr.receiver is PostfixExpression &&
            (expr.receiver as PostfixExpression).op == PostfixOp.NotNull &&
            isExprPointerMember((expr.receiver as PostfixExpression).expr)
        val useArrow = isPointerReceiver || isNestedPointerAccess || isNotNullOnPointer

        // For user-defined class instances, don't apply stdlib property mappings
        if (isUserClassReceiver || useArrow || isReceiverPossibleUserClass(expr.receiver)) {
            val op = if (useArrow || safe) "->" else "."
            return if (safe) "($recv != nullptr ? $recv->${ expr.name} : /* null */{})"
                   else "$recv$op${expr.name}"
        }

        // Convert known property accesses
        return when (expr.name) {
            "size"      -> if (safe) "([&]() { auto&& _c = $recv; return _c ? (int)_c->size() : 0; }())" else "(int)$recv.size()"
            "lastIndex" -> if (safe) "([&]() { auto&& _c = $recv; return _c ? (int)_c->size()-1 : -1; }())" else "((int)$recv.size()-1)"
            "first"     -> if (safe) "([&]() { auto&& _c = $recv; return _c ? _c->front() : /* null */{}; }())" else "$recv.front()"
            "last"      -> if (safe) "([&]() { auto&& _c = $recv; return _c ? _c->back()  : /* null */{}; }())" else "$recv.back()"
            "indices"   -> "(0..(int)$recv.size()-1)"  // used in for loops
            "length"    -> "(int)$recv.size()"
            "isEmpty"   -> "$recv.empty()"
            "isNotEmpty"-> "(!$recv.empty())"
            "entries"   -> "([&]() { auto&& _c = $recv; return vector<pair<decay_t<decltype(_c.begin()->first)>, decay_t<decltype(_c.begin()->second)>>>(_c.begin(), _c.end()); }())"
            // Pair/Map.Entry: .key→.first, .value→.second
            // Only apply when we're confident it's a pair/map entry, not a user-defined class.
            // Since we can't always tell, just pass through — Kotlin Pair already has .first/.second,
            // and Map entries are converted to pair where .first/.second work directly.
            "key"       -> "$recv.first"
            "value"     -> "$recv.value"
            else        -> if (safe) "([&]() { auto&& _c = $recv; return _c != nullptr ? _c->${expr.name} : /* null */{}; }())"
                           else "$recv.${expr.name}"
        }
    }

    /** Wraps the expression in parentheses if it's a binary expression with different precedence. */
    private fun parenIfBinary(child: Expression, parentOp: BinaryOp, isRight: Boolean = false): String {
        val raw = genExpr(child)
        if (child !is BinaryExpression) return raw
        val childPrec = opPrecedence(child.op)
        val parentPrec = opPrecedence(parentOp)
        // Parenthesize if child has strictly lower precedence
        if (childPrec < parentPrec) return "($raw)"
        // For right operand of non-commutative operators (-, /, %), also parenthesize
        // at same precedence: a - (b + c) != a - b + c
        if (isRight && childPrec == parentPrec &&
            parentOp in setOf(BinaryOp.Minus, BinaryOp.Div, BinaryOp.Mod))
            return "($raw)"
        return raw
    }

    private fun opPrecedence(op: BinaryOp): Int = when (op) {
        BinaryOp.Or -> 1
        BinaryOp.And -> 2
        BinaryOp.Eq, BinaryOp.NotEq, BinaryOp.RefEq, BinaryOp.RefNotEq -> 3
        BinaryOp.Lt, BinaryOp.Gt, BinaryOp.LtEq, BinaryOp.GtEq -> 4
        BinaryOp.RangeTo, BinaryOp.RangeUntil -> 5
        BinaryOp.Plus, BinaryOp.Minus -> 6
        BinaryOp.Times, BinaryOp.Div, BinaryOp.Mod -> 7
        BinaryOp.Shl, BinaryOp.Shr, BinaryOp.Ushr -> 5
        BinaryOp.BitAnd -> 5
        BinaryOp.BitOr -> 5
        BinaryOp.BitXor -> 5
        BinaryOp.Elvis -> 0
    }

    private fun genBinaryExpr(expr: BinaryExpression): String {
        val l = parenIfBinary(expr.left, expr.op, isRight = false)
        val r = parenIfBinary(expr.right, expr.op, isRight = true)
        return when (expr.op) {
            BinaryOp.Plus    -> {
                // Detect list concatenation: if either side looks like a list-producing expression
                if (isListExpression(expr.left) || isListExpression(expr.right))
                    "[&]() { auto _a = $l; auto _b = $r; _a.insert(_a.end(), _b.begin(), _b.end()); return _a; }()"
                else "$l + $r"
            }
            BinaryOp.Minus   -> "$l - $r"
            BinaryOp.Times   -> "$l * $r"
            BinaryOp.Div     -> "$l / $r"
            BinaryOp.Mod     -> "$l % $r"
            BinaryOp.And     -> "$l && $r"
            BinaryOp.Or      -> "$l || $r"
            BinaryOp.Eq      -> "$l == $r"
            BinaryOp.NotEq   -> "$l != $r"
            BinaryOp.RefEq   -> "$l == $r"
            BinaryOp.RefNotEq -> "$l != $r"
            BinaryOp.Lt      -> "$l < $r"
            BinaryOp.Gt      -> "$l > $r"
            BinaryOp.LtEq    -> "$l <= $r"
            BinaryOp.GtEq    -> "$l >= $r"
            BinaryOp.Elvis   -> {
                // Map index access: map[key] ?: default → count-based check
                if (expr.left is IndexAccess) {
                    val receiver = genExpr((expr.left as IndexAccess).receiver)
                    val index = genExpr((expr.left as IndexAccess).index)
                    "([&]() { auto&& _c = $receiver; auto&& _k = $index; return _c.count(_k) ? _c[_k] : $r; }())"
                } else {
                    "($l != nullptr ? $l : $r)"
                }
            }
            BinaryOp.RangeTo -> "/* range $l..$r */"
            BinaryOp.RangeUntil -> "/* range $l..<$r */"
            BinaryOp.Shl     -> "($l << $r)"
            BinaryOp.Shr     -> "($l >> $r)"
            BinaryOp.Ushr    -> "((unsigned)$l >> $r)"
            BinaryOp.BitAnd  -> "($l & $r)"
            BinaryOp.BitOr   -> "($l | $r)"
            BinaryOp.BitXor  -> "($l ^ $r)"
        }
    }

    private fun genPrefixExpr(expr: PrefixExpression): String {
        val e = genExpr(expr.expr)
        return when (expr.op) {
            PrefixOp.UnaryMinus -> "-($e)"
            PrefixOp.UnaryPlus  -> "+($e)"
            PrefixOp.Not        -> "!($e)"
            PrefixOp.PreInc     -> "++$e"
            PrefixOp.PreDec     -> "--$e"
        }
    }

    private fun genPostfixExpr(expr: PostfixExpression): String {
        val e = genExpr(expr.expr)
        return when (expr.op) {
            PostfixOp.PostInc -> "$e++"
            PostfixOp.PostDec -> "$e--"
            PostfixOp.NotNull -> e  // !! = no-op (memory leaks acceptable)
        }
    }

    private fun genTypeCheck(expr: TypeCheckExpression): String {
        val e = genExpr(expr.expr)
        val t = typeToCpp(expr.type)
        val check = "dynamic_cast<$t*>(&($e)) != nullptr"
        return if (expr.negated) "!($check)" else check
    }

    private fun genTypeCast(expr: TypeCastExpression): String {
        val e = genExpr(expr.expr)
        val t = typeToCpp(expr.type)
        // Safe cast (as?) → dynamic_cast returning pointer
        return if (expr.isSafe) "dynamic_cast<$t*>(&($e))"
               else "static_cast<$t>($e)"
    }

    private fun genElvisExpr(expr: ElvisExpression): String {
        val left = expr.left
        val right = genExpr(expr.right)
        // Map index access: map[key] ?: default → (map.count(key) ? map[key] : default)
        if (left is IndexAccess) {
            val receiver = genExpr(left.receiver)
            val index = genExpr(left.index)
            return "([&]() { auto&& _c = $receiver; auto&& _k = $index; return _c.count(_k) ? _c[_k] : $right; }())"
        }
        // General case: keep nullptr check for nullable types
        val leftCpp = genExpr(left)
        return "($leftCpp != nullptr ? *$leftCpp : $right)"
    }

    private fun genRangeAsValue(range: RangeExpression): String {
        // Ranges used as values (not in for loops) — materialize as a vector
        val s = genExpr(range.start)
        val e = genExpr(range.end)
        val step = range.step?.let { genExpr(it) }
        return when (range.kind) {
            RangeKind.Inclusive -> {
                val inc = if (step != null) "_i += $step" else "_i++"
                "[&]() { vector<int> _r; for (auto _i = $s; _i <= $e; $inc) _r.push_back(_i); return _r; }()"
            }
            RangeKind.Until -> {
                val inc = if (step != null) "_i += $step" else "_i++"
                "[&]() { vector<int> _r; for (auto _i = $s; _i < $e; $inc) _r.push_back(_i); return _r; }()"
            }
            RangeKind.DownTo -> {
                val dec = if (step != null) "_i -= $step" else "_i--"
                "[&]() { vector<int> _r; for (auto _i = $s; _i >= $e; $dec) _r.push_back(_i); return _r; }()"
            }
        }
    }

    private fun genCallExpr(expr: CallExpression): String {
        val lambda = expr.trailingLambda

        // Callee is a simple name → check stdlib (skip if locally defined)
        if (expr.callee is NameReference) {
            val name = (expr.callee as NameReference).name
            if (name !in localFunctionNames) {
                val mapped = StdlibMapper.mapTopLevelCall(name, expr.typeArgs, expr.args, lambda, this)
                if (mapped != null) return mapped
            }

            // Known Kotlin type constructors that are parsed as CallExpression
            // (not ObjectCreation), e.g. TreeSet(), StringBuilder(), etc.
            val constructorMapping = mapConstructorCall(name, expr.typeArgs, expr.args)
            if (constructorMapping != null) return constructorMapping
        }

        // Callee is a property access (e.g. Math.abs, receiver.method)
        if (expr.callee is PropertyAccessExpr) {
            val pa = expr.callee as PropertyAccessExpr
            // Intercept Math.xxx calls → translate to C++ stdlib math functions
            if (pa.receiver is NameReference && (pa.receiver as NameReference).name == "Math") {
                val mathMapped = StdlibMapper.mapTopLevelCall(pa.name, expr.typeArgs, expr.args, lambda, this)
                if (mathMapped != null) return mathMapped
            }
            // StringBuilder method overrides
            if (pa.receiver is NameReference && (pa.receiver as NameReference).name in stringBuilderVars) {
                val sbRecv = genExpr(pa.receiver)
                when (pa.name) {
                    "toString" -> if (expr.args.isEmpty()) return "$sbRecv.str()"
                    "append" -> if (expr.args.size == 1) return "{ $sbRecv << ${genExpr(expr.args[0].value)}; }"
                    "appendLine", "appendln" -> return if (expr.args.size == 1) "{ $sbRecv << ${genExpr(expr.args[0].value)} << '\\n'; }" else "{ $sbRecv << '\\n'; }"
                    "clear" -> if (expr.args.isEmpty()) return "$sbRecv.str(\"\")"
                }
            }
            val isUserClassRecv = pa.receiver is NameReference && (pa.receiver as NameReference).name in userClassVars
            val isPointerRecv = isExprPointerMember(pa.receiver)
            // Set-specific method overrides
            if (!isUserClassRecv && !isPointerRecv && pa.receiver is NameReference && (pa.receiver as NameReference).name in setVars) {
                val recv = genExpr(pa.receiver)
                when (pa.name) {
                    "add" -> if (expr.args.size == 1) return "$recv.insert(${genExpr(expr.args[0].value)})"
                    "first" -> if (expr.args.isEmpty()) return "(*$recv.begin())"
                    "last" -> if (expr.args.isEmpty()) return "(*$recv.rbegin())"
                    "remove" -> if (expr.args.size == 1) return "$recv.erase(${genExpr(expr.args[0].value)})"
                    "contains" -> if (expr.args.size == 1) return "($recv.count(${genExpr(expr.args[0].value)}) > 0)"
                }
            }
            // `x in collection` / `x !in collection` is parsed as collection.contains(x)
            // Use .count() which works for maps, sets, and associative containers
            // (skip for user-defined class instances — they have their own contains method)
            if (!isUserClassRecv && !isPointerRecv && pa.name == "contains" && expr.args.size == 1 && expr.typeArgs.isEmpty()) {
                val recv = genExpr(pa.receiver)
                val arg = genExpr(expr.args[0].value)
                return "($recv.count($arg) > 0)"
            }
            // Skip stdlib method mapping for user-defined class instances or pointer members
            if (isUserClassRecv || isPointerRecv) {
                val recv = genExpr(pa.receiver)
                val argsList = buildArgList(expr.args, lambda)
                val op = if (isPointerRecv || pa.isSafe) "->" else "."
                return "$recv$op${pa.name}($argsList)"
            }
            val recv = genExpr(pa.receiver)
            val mapped = StdlibMapper.mapMethodCall(
                recv, pa.name, expr.typeArgs, expr.args, lambda, pa.isSafe, this)
            if (mapped != null) return mapped
        }

        // Generic call
        val callee = genExpr(expr.callee)
        val typeArgStr = if (expr.typeArgs.isNotEmpty())
            "<${expr.typeArgs.joinToString(", ") { typeToCpp(it) }}>" else ""

        // If this is a user-defined class constructor call, handle pointer params
        val calleeName = if (expr.callee is NameReference) (expr.callee as NameReference).name else null
        val pointerIndices = if (calleeName != null) classPointerCtorParams[calleeName] else null
        if (pointerIndices != null && pointerIndices.isNotEmpty()) {
            val argParts = mutableListOf<String>()
            for ((i, arg) in expr.args.withIndex()) {
                val argExpr = genExpr(arg.value)
                if (i in pointerIndices && !isAlreadyPointerExpr(arg.value)) {
                    argParts.add("&$argExpr")
                } else {
                    argParts.add(argExpr)
                }
            }
            if (lambda != null) argParts.add(genLambdaAsCppLambda(lambda))
            return "$callee$typeArgStr(${argParts.joinToString(", ")})"
        }

        val args = buildArgList(expr.args, lambda)
        return "$callee$typeArgStr($args)"
    }

    /** Maps known Kotlin type constructor calls (parsed as CallExpression) to C++ equivalents. */
    private fun mapConstructorCall(name: String, typeArgs: List<KotlinType>, args: List<CallArgument>): String? {
        // Skip mapping for user-defined classes
        if (name in userDefinedClassNames) return null

        fun typeArg(i: Int): String = if (typeArgs.size > i) typeToCpp(typeArgs[i]) else "int"
        val argsCpp = args.joinToString(", ") { genExpr(it.value) }

        return when (name) {
            "TreeSet"       -> "set<${typeArg(0)}>($argsCpp)"
            "TreeMap"       -> "map<${typeArg(0)}, ${typeArg(1)}>($argsCpp)"
            "HashMap"       -> "unordered_map<${typeArg(0)}, ${typeArg(1)}>($argsCpp)"
            "HashSet"       -> "unordered_set<${typeArg(0)}>($argsCpp)"
            "LinkedHashMap" -> "map<${typeArg(0)}, ${typeArg(1)}>($argsCpp)"
            "LinkedHashSet" -> "set<${typeArg(0)}>($argsCpp)"
            "ArrayDeque"    -> if (args.size == 1 && typeArgs.isEmpty()) {
                val a = genExpr(args[0].value)
                "[&]() { auto _v = $a; return deque<decay_t<decltype(_v.front())>>(_v.begin(), _v.end()); }()"
            } else "deque<${typeArg(0)}>($argsCpp)"
            "PriorityQueue" -> {
                val elem = typeArg(0)
                if (args.isNotEmpty()) {
                    // PriorityQueue with comparator — Kotlin PQ is a min-heap but C++ priority_queue
                    // is a max-heap, so we invert the comparator: comp(a,b) → comp(b,a)
                    val cmp = genExpr(args[0].value)
                    "[&]() { auto _kcmp = $cmp; auto _cmp = [_kcmp](const auto& a, const auto& b) { return _kcmp(b, a); }; return priority_queue<$elem, vector<$elem>, decltype(_cmp)>(_cmp); }()"
                } else "priority_queue<$elem>()"
            }
            "ArrayList"     -> "vector<${typeArg(0)}>($argsCpp)"
            "Stack"         -> "deque<${typeArg(0)}>($argsCpp)"
            "StringBuilder" -> "ostringstream($argsCpp)"
            else            -> null
        }
    }

    private fun buildArgList(args: List<CallArgument>, lambda: LambdaExpression?): String {
        val parts = args.map { genExpr(it.value) }.toMutableList()
        if (lambda != null) parts.add(genLambdaAsCppLambda(lambda))
        return parts.joinToString(", ")
    }

    /** Generates a MethodCallExpression (receiver.method(args)). */
    private fun genMethodCallExpr(expr: MethodCallExpression): String {
        val isUserClassReceiver = expr.receiver is NameReference
            && (expr.receiver as NameReference).name in userClassVars
        // Check if receiver is a pointer member (for -> access)
        val isPointerReceiver = isExprPointerMember(expr.receiver)

        // Intercept Math.xxx calls → translate to C++ stdlib math functions
        if (expr.receiver is NameReference && (expr.receiver as NameReference).name == "Math") {
            val mathMapped = StdlibMapper.mapTopLevelCall(expr.method, expr.typeArgs, expr.args, expr.trailingLambda, this)
            if (mathMapped != null) return mathMapped
        }

        // StringBuilder method overrides: toString→str, append→<<, clear→str("")
        if (expr.receiver is NameReference && (expr.receiver as NameReference).name in stringBuilderVars) {
            val recv = genExpr(expr.receiver)
            when (expr.method) {
                "toString" -> if (expr.args.isEmpty()) return "$recv.str()"
                "append" -> if (expr.args.size == 1) return "{ $recv << ${genExpr(expr.args[0].value)}; }"
                "appendLine", "appendln" -> return if (expr.args.size == 1) "{ $recv << ${genExpr(expr.args[0].value)} << '\\n'; }" else "{ $recv << '\\n'; }"
                "clear" -> if (expr.args.isEmpty()) return "$recv.str(\"\")"
            }
        }

        // PriorityQueue method overrides
        if (expr.receiver is NameReference && (expr.receiver as NameReference).name in pqVars) {
            val recv = genExpr(expr.receiver)
            when (expr.method) {
                "add", "offer" -> if (expr.args.size == 1) return "$recv.push(${genExpr(expr.args[0].value)})"
                "poll", "remove" -> if (expr.args.isEmpty()) return "([&]() { auto _v = $recv.top(); $recv.pop(); return _v; }())"
                "peek" -> if (expr.args.isEmpty()) return "$recv.top()"
            }
        }

        // java.util.Stack method overrides (Stack maps to std::deque)
        if (expr.receiver is NameReference && (expr.receiver as NameReference).name in stackVars) {
            val recv = genExpr(expr.receiver)
            when (expr.method) {
                "push" -> if (expr.args.size == 1) return "$recv.push_back(${genExpr(expr.args[0].value)})"
                "pop" -> if (expr.args.isEmpty()) return "([&]() { auto _v = $recv.back(); $recv.pop_back(); return _v; }())"
                "peek" -> if (expr.args.isEmpty()) return "$recv.back()"
                "isEmpty" -> if (expr.args.isEmpty()) return "$recv.empty()"
                "size" -> if (expr.args.isEmpty()) return "(int)$recv.size()"
                "max" -> if (expr.args.isEmpty()) return "(*max_element($recv.begin(), $recv.end()))"
            }
        }

        // Set-specific method overrides: add→insert, first→*begin, last→*rbegin
        if (!isUserClassReceiver && expr.receiver is NameReference && (expr.receiver as NameReference).name in setVars) {
            val recv = genExpr(expr.receiver)
            when (expr.method) {
                "add" -> if (expr.args.size == 1) return "$recv.insert(${genExpr(expr.args[0].value)})"
                "first" -> if (expr.args.isEmpty()) return "(*$recv.begin())"
                "last" -> if (expr.args.isEmpty()) return "(*$recv.rbegin())"
                "remove" -> if (expr.args.size == 1) return "$recv.erase(${genExpr(expr.args[0].value)})"
                "contains" -> if (expr.args.size == 1) return "($recv.count(${genExpr(expr.args[0].value)}) > 0)"
            }
        }

        // Skip stdlib method mapping for user-defined class instances or pointer members
        if (isUserClassReceiver || isPointerReceiver) {
            val recv = genExpr(expr.receiver)
            val args = buildArgList(expr.args, expr.trailingLambda)
            val op = if (expr.isSafeCall || isPointerReceiver) "->" else "."
            return "$recv$op${expr.method}($args)"
        }

        val recv = genExpr(expr.receiver)
        val mapped = StdlibMapper.mapMethodCall(
            recv, expr.method, expr.typeArgs, expr.args, expr.trailingLambda, expr.isSafeCall, this)
        if (mapped != null) return mapped

        val args = buildArgList(expr.args, expr.trailingLambda)
        val op = if (expr.isSafeCall) "->" else "."
        // Emit error for Kotlin stdlib methods that weren't mapped
        if (!isUserClassReceiver && isLikelyKotlinStdlibMethod(expr.method)) {
            return emitError("Unsupported method: .${expr.method}()")
        }
        return "$recv$op${sanitizeName(expr.method)}($args)"
    }

    /** Heuristic: returns true if this method name looks like a Kotlin stdlib method
     *  that should have been mapped but wasn't. */
    private fun isLikelyKotlinStdlibMethod(method: String): Boolean {
        val knownKotlinMethods = setOf(
            "sumOf", "count", "map", "filter", "forEach", "forEachIndexed",
            "mapIndexed", "filterIndexed", "flatMap", "mapNotNull", "filterNot",
            "any", "all", "none", "reduce", "fold", "groupBy", "partition",
            "associateBy", "associateWith", "associate",
            "sortBy", "sortByDescending", "sortWith",
            "sortedBy", "sortedByDescending", "sortedWith",
            "maxByOrNull", "minByOrNull", "maxBy", "minBy",
            "joinToString", "withIndex",
            "toInt", "toLong", "toDouble", "toFloat", "toString", "toChar",
            "toByte", "toShort", "toBigInteger", "toBigDecimal",
            "toList", "toMutableList", "toSet", "toMutableSet",
            "toIntArray", "toLongArray", "toDoubleArray",
            "removeLast", "removeFirst", "removeAll", "retainAll",
            "addAll", "addFirst", "addLast",
            "subList", "substring", "replace", "split", "trim",
            "startsWith", "endsWith", "padStart", "padEnd",
            "lowercase", "uppercase", "capitalize", "decapitalize",
            "repeat", "chunked", "windowed", "zip", "unzip",
            "flatten", "flatMapIndexed",
            "take", "drop", "takeWhile", "dropWhile",
            "indexOf", "lastIndexOf", "indexOfFirst", "indexOfLast",
            "find", "findLast", "first", "last", "firstOrNull", "lastOrNull",
            "single", "singleOrNull",
            "reversed", "asReversed", "shuffled",
            "distinct", "distinctBy",
            "sum", "average", "count",
            "appendLine", "appendln", "append",
            "let", "also", "apply", "run", "with", "takeIf", "takeUnless",
            "compareTo", "coerceIn", "coerceAtLeast", "coerceAtMost",
            "component1", "component2", "component3",
            "getOrDefault", "getOrElse", "getOrPut",
            "getValue", "setValue",
            "containsKey", "containsValue",
            "keys", "values", "entries",
            "floorEntry", "ceilingEntry", "lowerEntry", "higherEntry",
            "floorKey", "ceilingKey", "lowerKey", "higherKey",
            "floor", "ceiling", "lower", "higher",
            "peek", "poll", "offer",
            "push", "pop",
            "removeAt", "set", "get",
            "isEmpty", "isNotEmpty", "isNullOrEmpty", "isNullOrBlank",
            "isBlank", "isNotBlank",
            "toCharArray", "toByteArray",
            "length", "size", "lastIndex", "indices",
            "clear", "add", "remove", "contains",
            "first", "last",
            "max", "min", "maxOrNull", "minOrNull",
            "sort", "sorted", "sortDescending", "sortedDescending",
            "reverse", "reversed",
            "withIndex", "asSequence", "asIterable",
            "buildString", "buildList"
        )
        return method in knownKotlinMethods
    }

    private fun genIfExpression(expr: IfExpression): String {
        val cond = genExpr(expr.condition)
        val thenIsSimple = expr.thenBranch is ExprBranch
        val elseIsSimple = expr.elseBranch is ExprBranch

        // Simple ternary
        if (thenIsSimple && elseIsSimple) {
            val t = genExpr((expr.thenBranch as ExprBranch).expr)
            val e = genExpr((expr.elseBranch as ExprBranch).expr)
            return "($cond ? $t : $e)"
        }

        // Block → immediately-invoked lambda
        return buildString {
            append("[&]() -> auto {\n")
            val thenStmts = when (expr.thenBranch) {
                is ExprBranch  -> listOf(ReturnStatement(expr.thenBranch.expr))
                is BlockBranch -> {
                    val stmts = expr.thenBranch.statements.toMutableList()
                    if (stmts.isNotEmpty()) {
                        val last = stmts.last()
                        if (last is ExpressionStatement)
                            stmts[stmts.lastIndex] = ReturnStatement(last.expr)
                    }
                    stmts
                }
            }
            val elseStmts = when (expr.elseBranch) {
                is ExprBranch  -> listOf(ReturnStatement(expr.elseBranch.expr))
                is BlockBranch -> {
                    val stmts = expr.elseBranch.statements.toMutableList()
                    if (stmts.isNotEmpty()) {
                        val last = stmts.last()
                        if (last is ExpressionStatement)
                            stmts[stmts.lastIndex] = ReturnStatement(last.expr)
                    }
                    stmts
                }
            }

            // We have to build the if inline using a sub-generator
            val sub = createSubGenerator()
            sub.emit("if ($cond) {")
            sub.indent { thenStmts.forEach { sub.genStatement(it) } }
            sub.emit("} else {")
            sub.indent { elseStmts.forEach { sub.genStatement(it) } }
            sub.emit("}")
            append(sub.out.toString().trimEnd())
            append("\n}()")
        }
    }

    private fun genWhenExpression(expr: WhenExpression): String {
        // Emit as immediately-invoked lambda
        val sub = createSubGenerator()
        val subject = expr.subject
        val subjectExpr = subject?.expr?.let { genExpr(it) }

        var first = true
        for (entry in expr.entries) {
            val isElse = entry.conditions.isEmpty() || entry.conditions.any { it is ElseCondition }
            val cond = if (isElse) null else buildWhenCondition(entry.conditions, subjectExpr)

            if (first) {
                if (cond != null) sub.emit("if ($cond) {") else sub.emit("{")
                first = false
            } else {
                if (cond != null) sub.emit("} else if ($cond) {") else sub.emit("} else {")
            }

            sub.indent {
                when (val body = entry.body) {
                    is BlockEntryBody -> {
                        val stmts = body.statements.toMutableList()
                        if (stmts.isNotEmpty()) {
                            val last = stmts.last()
                            if (last is ExpressionStatement)
                                stmts[stmts.lastIndex] = ReturnStatement(last.expr)
                        }
                        stmts.forEach { sub.genStatement(it) }
                    }
                    is ExpressionEntryBody -> sub.emit("return ${genExpr(body.expr)};")
                }
            }
        }
        if (!first) sub.emit("}")

        return "[&]() -> auto {\n${sub.out.toString().trimEnd()}\n}()"
    }

    private fun genObjectCreation(expr: ObjectCreation): String {
        val typeName = typeToCpp(expr.type)
        val className = getBaseTypeName(expr.type)
        val pointerIndices = if (className != null) classPointerCtorParams[className] else null

        if (pointerIndices != null && pointerIndices.isNotEmpty()) {
            // Some constructor params are pointers — add & to non-pointer lvalue args at those positions
            val argParts = mutableListOf<String>()
            for ((i, arg) in expr.args.withIndex()) {
                val argExpr = genExpr(arg.value)
                if (i in pointerIndices && !isAlreadyPointerExpr(arg.value)) {
                    argParts.add("&$argExpr")
                } else {
                    argParts.add(argExpr)
                }
            }
            if (expr.trailingLambda != null) argParts.add(genLambdaAsCppLambda(expr.trailingLambda))
            return "$typeName(${argParts.joinToString(", ")})"
        }

        val args = buildArgList(expr.args, expr.trailingLambda)
        return "$typeName($args)"
    }

    /** Check if an expression is already a pointer (no need for &). */
    private fun isAlreadyPointerExpr(expr: Expression): Boolean {
        if (expr is NullLiteral) return true
        if (expr is NameReference && expr.name in pointerMembers) return true
        if (expr is PropertyAccessExpr && expr.name in pointerMembers) return true
        if (expr is PostfixExpression && expr.op == PostfixOp.NotNull) return isAlreadyPointerExpr(expr.expr)
        // ObjectCreation with `new` is handled separately (not through genObjectCreation for ctor args)
        return false
    }

    private fun genStringTemplate(expr: StringTemplate): String {
        if (expr.parts.size == 1 && expr.parts[0] is LiteralStringPart)
            return "\"${escapeString((expr.parts[0] as LiteralStringPart).value)}\""

        // Build via ostringstream in a lambda
        val sub = StringBuilder()
        sub.append("[&]() -> std::string { ostringstream _ss; ")
        for (part in expr.parts) {
            when (part) {
                is LiteralStringPart   -> sub.append("_ss << \"${escapeString(part.value)}\"; ")
                is ExpressionStringPart -> sub.append("_ss << ${genExpr(part.expr)}; ")
            }
        }
        sub.append("return _ss.str(); }()")
        return sub.toString()
    }

    // ─── Lambda helpers ──────────────────────────────────────────────────────

    /**
     * Generates a C++ lambda expression for [lambda].
     * Single-statement lambdas become `[&](...) { return expr; }`.
     */
    fun genLambdaAsCppLambda(lambda: LambdaExpression): String {
        val params = lambdaParams(lambda)
        val body = lambda.body
        return buildString {
            append("[&]($params)")
            if (body.size == 1 && body[0] is ExpressionStatement) {
                val innerExpr = (body[0] as ExpressionStatement).expr
                if (innerExpr is BreakJumpExpr || innerExpr is ContinueJumpExpr) {
                    // goto statements should not be wrapped with return
                    append(" { ${genExpr(innerExpr)}; }")
                } else {
                    val e = genExpr(innerExpr)
                    append(" { return $e; }")
                }
            } else {
                append(" {")
                val sub = createSubGenerator()
                body.forEach { sub.genStatement(it) }
                append("\n")
                append(sub.out.toString().trimEnd())
                append("\n}")
            }
        }
    }

    /**
     * Returns a callable expression from [lambda] — either the full lambda or,
     * for a single-expression body, just the expression text wrapped in a lambda.
     */
    fun genLambdaBody(lambda: LambdaExpression): String = genLambdaAsCppLambda(lambda)

    /**
     * Generates the *statements* inside a lambda body as inline C++ text (for
     * use inside `[&]() { ... }()` blocks).
     */
    fun genLambdaBodyStatements(lambda: LambdaExpression): String {
        val sub = createSubGenerator()
        lambda.body.forEach { sub.genStatement(it) }
        return sub.out.toString().trimEnd()
    }

    private fun lambdaParams(lambda: LambdaExpression): String {
        if (lambda.params.isEmpty()) {
            // If the body references implicit `it`, add _it as parameter
            if (lambdaBodyUsesIt(lambda.body)) return "auto _it"
            return ""
        }
        return lambda.params.joinToString(", ") { p ->
            val t = if (p.type != null) typeToCpp(p.type) else "auto"
            "$t ${if (p.name == "it") "_it" else p.name}"
        }
    }

    /** Checks whether a list of statements references `it` (the implicit lambda parameter). */
    private fun lambdaBodyUsesIt(stmts: List<Statement>): Boolean {
        fun exprUsesIt(expr: Expression): Boolean = when (expr) {
            is NameReference -> expr.name == "it"
            is BinaryExpression -> exprUsesIt(expr.left) || exprUsesIt(expr.right)
            is PrefixExpression -> exprUsesIt(expr.expr)
            is PostfixExpression -> exprUsesIt(expr.expr)
            is CallExpression -> exprUsesIt(expr.callee) || expr.args.any { exprUsesIt(it.value) }
            is MethodCallExpression -> exprUsesIt(expr.receiver) || expr.args.any { exprUsesIt(it.value) }
            is PropertyAccessExpr -> exprUsesIt(expr.receiver)
            is IndexAccess -> exprUsesIt(expr.receiver) || exprUsesIt(expr.index) || expr.additionalIndices.any { exprUsesIt(it) }
            is IfExpression -> exprUsesIt(expr.condition)
            is ElvisExpression -> exprUsesIt(expr.left) || exprUsesIt(expr.right)
            is TypeCheckExpression -> exprUsesIt(expr.expr)
            is TypeCastExpression -> exprUsesIt(expr.expr)
            is ReturnJumpExpr -> expr.value?.let { exprUsesIt(it) } ?: false
            is ThrowJumpExpr -> exprUsesIt(expr.expr)
            is ObjectCreation -> expr.args.any { exprUsesIt(it.value) }
            is StringTemplate -> expr.parts.any { it is ExpressionStringPart && exprUsesIt(it.expr) }
            else -> false
        }
        fun stmtUsesIt(stmt: Statement): Boolean = when (stmt) {
            is ExpressionStatement -> exprUsesIt(stmt.expr)
            is ReturnStatement -> stmt.value?.let { exprUsesIt(it) } ?: false
            is LocalProperty -> stmt.initializer?.let { exprUsesIt(it) } ?: false
            is Assignment -> exprUsesIt(stmt.target) || exprUsesIt(stmt.value)
            is IfStatement -> exprUsesIt(stmt.condition) || stmt.thenBranch.any { stmtUsesIt(it) } || (stmt.elseBranch?.any { stmtUsesIt(it) } ?: false)
            is ForStatement -> stmt.body.any { stmtUsesIt(it) }
            is WhileStatement -> exprUsesIt(stmt.condition) || stmt.body.any { stmtUsesIt(it) }
            else -> false
        }
        return stmts.any { stmtUsesIt(it) }
    }

    // resolve `it` → `_it` inside lambda body text
    // (handled via param renaming above)

    // ─── Stdlib generation methods (called from StdlibMapper) ────────────────

    fun genRepeat(n: String, lambda: LambdaExpression): String {
        val indexVar = lambda.params.firstOrNull()?.name?.let {
            if (it == "it") "_it" else it
        } ?: "_i"
        val sub = createSubGenerator()
        // Evaluate n once to avoid re-evaluating expressions with side effects (e.g. nextInt())
        sub.emit("auto _n = $n;")
        sub.emit("for (int $indexVar = 0; $indexVar < _n; $indexVar++) {")
        sub.indent { lambda.body.forEach { sub.genStatement(it) } }
        sub.emit("}")
        return "[&]() { ${sub.out.toString().trimEnd()} }()"
    }

    fun genForEach(receiver: String, lambda: LambdaExpression, indexed: Boolean): String {
        val elemVar = lambda.params.getOrNull(0)?.name?.let { if (it == "it") "_it" else it } ?: "_it"
        val idxVar  = if (indexed) lambda.params.getOrNull(1)?.name ?: "_idx" else null
        val sub = createSubGenerator()
        // Push forEach label so return@forEach → continue
        sub.inlineLambdaLabels.addLast("forEach")
        if (indexed) sub.inlineLambdaLabels.addLast("forEachIndexed")
        // Also copy outer pending goto labels context
        sub.pendingGotoLabels.addAll(pendingGotoLabels)
        if (indexed && idxVar != null) {
            sub.emit("{ int $idxVar = 0; for (auto& $elemVar : $receiver) {")
            sub.indent {
                lambda.body.forEach { sub.genStatement(it) }
                sub.emit("$idxVar++;")
            }
            sub.emit("} }")
        } else {
            sub.emit("for (auto& $elemVar : $receiver) {")
            sub.indent { lambda.body.forEach { sub.genStatement(it) } }
            sub.emit("}")
        }
        // Merge back any goto labels the sub-generator registered
        pendingGotoLabels.addAll(sub.pendingGotoLabels)
        return "[&]() { ${sub.out.toString().trimEnd()} }()"
    }

    fun genMap(receiver: String, lambda: LambdaExpression, indexed: Boolean = false): String {
        val elemVar = lambda.params.getOrNull(0)?.name?.let { if (it == "it") "_it" else it } ?: "_it"
        val transform = genLambdaAsCppLambda(lambda)
        return "[&]() { auto&& _c = $receiver; auto _r = vector<decay_t<decltype($transform(_c.front()))>>(); " +
               "_r.reserve(_c.size()); " +
               "for (auto& $elemVar : _c) _r.push_back($transform($elemVar)); " +
               "return _r; }()"
    }

    fun genFilter(receiver: String, lambda: LambdaExpression, indexed: Boolean = false, negated: Boolean = false): String {
        val elemVar = lambda.params.getOrNull(0)?.name?.let { if (it == "it") "_it" else it } ?: "_it"
        val pred = genLambdaAsCppLambda(lambda)
        val check = if (negated) "!$pred($elemVar)" else "$pred($elemVar)"
        return "[&]() { auto&& _c = $receiver; auto _r = vector<decay_t<decltype(_c.front())>>(); " +
               "for (auto& $elemVar : _c) if ($check) _r.push_back($elemVar); " +
               "return _r; }()"
    }

    fun genAny(receiver: String, lambda: LambdaExpression): String {
        val pred = genLambdaAsCppLambda(lambda)
        return "([&]() { auto&& _c = $receiver; return any_of(_c.begin(), _c.end(), $pred); }())"
    }

    fun genAll(receiver: String, lambda: LambdaExpression): String {
        val pred = genLambdaAsCppLambda(lambda)
        return "([&]() { auto&& _c = $receiver; return all_of(_c.begin(), _c.end(), $pred); }())"
    }

    fun genNone(receiver: String, lambda: LambdaExpression): String {
        val pred = genLambdaAsCppLambda(lambda)
        return "([&]() { auto&& _c = $receiver; return none_of(_c.begin(), _c.end(), $pred); }())"
    }

    fun genCountIf(receiver: String, lambda: LambdaExpression): String {
        val pred = genLambdaAsCppLambda(lambda)
        return "([&]() { auto&& _c = $receiver; return (int)count_if(_c.begin(), _c.end(), $pred); }())"
    }

    fun genFirstIf(receiver: String, lambda: LambdaExpression): String {
        val pred = genLambdaAsCppLambda(lambda)
        return "([&]() { auto&& _c = $receiver; return (*find_if(_c.begin(), _c.end(), $pred)); }())"
    }

    fun genLastIf(receiver: String, lambda: LambdaExpression): String {
        val pred = genLambdaAsCppLambda(lambda)
        return "([&]() { auto&& _c = $receiver; return (*find_if(_c.rbegin(), _c.rend(), $pred)); }())"
    }

    fun genReduce(receiver: String, lambda: LambdaExpression): String {
        val f = genLambdaAsCppLambda(lambda)
        return "[&]() { auto&& _c = $receiver; auto _r = _c.front(); " +
               "for (int _i = 1; _i < (int)_c.size(); _i++) _r = $f(_r, _c[_i]); " +
               "return _r; }()"
    }

    fun genFold(receiver: String, initial: String, lambda: LambdaExpression): String {
        val f = genLambdaAsCppLambda(lambda)
        return "[&]() { auto _r = $initial; " +
               "for (auto& _x : $receiver) _r = $f(_r, _x); " +
               "return _r; }()"
    }

    fun genFlatMap(receiver: String, lambda: LambdaExpression): String {
        val f = genLambdaAsCppLambda(lambda)
        return "[&]() { auto&& _c = $receiver; auto _r = vector<decay_t<decltype($f(_c.front()).front())>>(); " +
               "for (auto& _x : _c) { auto _sub = $f(_x); _r.insert(_r.end(), _sub.begin(), _sub.end()); } " +
               "return _r; }()"
    }

    fun genMapNotNull(receiver: String, lambda: LambdaExpression): String {
        val f = genLambdaAsCppLambda(lambda)
        return "[&]() { auto&& _c = $receiver; auto _r = vector<remove_pointer_t<decltype($f(_c.front()))>>(); " +
               "for (auto& _x : _c) { auto _v = $f(_x); if (_v != nullptr) _r.push_back(*_v); } " +
               "return _r; }()"
    }

    fun genJoinToString(receiver: String, sep: String, transform: LambdaExpression?): String {
        // Cache receiver in a local to avoid re-evaluating complex expressions (e.g. map results)
        val transformPart = if (transform != null) {
            val f = genLambdaAsCppLambda(transform)
            "auto&& _c = $receiver; for (int _i = 0; _i < (int)_c.size(); _i++) { if (_i) _ss << $sep; _ss << $f(_c[_i]); }"
        } else {
            "auto&& _c = $receiver; for (int _i = 0; _i < (int)_c.size(); _i++) { if (_i) _ss << $sep; _ss << _c[_i]; }"
        }
        return "[&]() -> string { ostringstream _ss; $transformPart return _ss.str(); }()"
    }

    fun genJoinToCout(receiver: String, sep: String, transform: LambdaExpression?, newline: Boolean): String {
        val suffix = if (newline) " cout << '\\n';" else ""
        // Cache receiver in a local to avoid re-evaluating complex expressions
        val elemPart = if (transform != null) {
            val f = genLambdaAsCppLambda(transform)
            "cout << $f(_c[_i]);"
        } else "cout << _c[_i];"
        return "[&]() { auto&& _c = $receiver; for (int _i = 0; _i < (int)_c.size(); _i++) { if (_i) cout << $sep; $elemPart }$suffix }()"
    }

    fun genLambdaAsLoopInit(lambda: LambdaExpression, size: String, elemType: String): String {
        val idxVar = lambda.params.getOrNull(0)?.name
            ?: if (lambdaBodyUsesIt(lambda.body)) "_it" else "_i"
        val bodyExpr = if (lambda.body.size == 1 && lambda.body[0] is ExpressionStatement)
            genExpr((lambda.body[0] as ExpressionStatement).expr)
        else "[&]() { ${genLambdaBodyStatements(lambda)} }()"
        val resolvedType = if (elemType != "auto") {
            elemType
        } else {
            // Try to infer the type directly from the body expression to avoid decltype scoping issues
            // (the body expression may reference the loop variable which isn't in scope at decltype point)
            val inferredType = inferLambdaBodyType(lambda)
            if (inferredType != null) inferredType
            else "decay_t<decltype([&](int $idxVar) { return $bodyExpr; }(0))>"
        }
        return "[&]() { vector<$resolvedType> _v; _v.reserve($size); for (int $idxVar = 0; $idxVar < $size; $idxVar++) _v.push_back($bodyExpr); return _v; }()"
    }

    /** Try to infer the element type from a lambda body expression without decltype.
     *  Returns the C++ type string if determinable, or null. */
    private fun inferLambdaBodyType(lambda: LambdaExpression): String? {
        if (lambda.body.size != 1 || lambda.body[0] !is ExpressionStatement) return null
        val expr = (lambda.body[0] as ExpressionStatement).expr
        return when (expr) {
            // Constructor call for a user-defined class: use the class name directly
            is ObjectCreation -> typeToCpp(expr.type)
            is CallExpression -> {
                val callee = expr.callee
                if (callee is NameReference && callee.name in userDefinedClassNames) callee.name
                else null
            }
            is IntLiteral -> "int"
            is LongLiteral -> "long long"
            is DoubleLiteral -> "double"
            is BooleanLiteral -> "bool"
            is StringLiteral -> "string"
            else -> null
        }
    }

    fun genBuildString(lambda: LambdaExpression): String {
        // The lambda body uses `this` or `it` as a StringBuilder.
        // We'll rename to a local ostringstream.
        val sub = createSubGenerator()
        sub.emit("ostringstream _sb;")
        // Replace any reference to the implicit builder
        lambda.body.forEach { sub.genStatement(it) }
        sub.emit("return _sb.str();")
        return "[&]() -> string { ${sub.out.toString().trimEnd()} }()"
    }

    fun genWithScope(receiver: String, lambda: LambdaExpression): String {
        // `with(obj) { ... }` – the lambda body can refer to obj's members without receiver
        // We generate: [&]() { auto& _recv = receiver; ... }()
        val sub = createSubGenerator()
        sub.emit("auto& _recv = $receiver;")
        lambda.body.forEach { sub.genStatement(it) }
        return "[&]() -> auto { ${sub.out.toString().trimEnd()} }()"
    }

    fun genLet(receiver: String, lambda: LambdaExpression): String {
        val paramName = lambda.params.getOrNull(0)?.name?.let { if (it == "it") "_it" else it } ?: "_it"
        val sub = createSubGenerator()
        sub.emit("auto& $paramName = $receiver;")
        lambda.body.forEach { sub.genStatement(it) }
        return "[&]() -> auto { ${sub.out.toString().trimEnd()} }()"
    }

    fun genAlso(receiver: String, lambda: LambdaExpression): String {
        val paramName = lambda.params.getOrNull(0)?.name?.let { if (it == "it") "_it" else it } ?: "_it"
        val sub = createSubGenerator()
        sub.emit("auto& $paramName = $receiver;")
        lambda.body.forEach { sub.genStatement(it) }
        sub.emit("return $paramName;")
        return "[&]() -> auto { ${sub.out.toString().trimEnd()} }()"
    }

    fun genApply(receiver: String, lambda: LambdaExpression): String {
        val sub = createSubGenerator()
        lambda.body.forEach { sub.genStatement(it) }
        return "[&]() -> auto { auto& _self = $receiver; ${sub.out.toString().trimEnd()} return _self; }()"
    }

    fun genRunReceiver(receiver: String, lambda: LambdaExpression): String {
        val sub = createSubGenerator()
        lambda.body.forEach { sub.genStatement(it) }
        return "[&]() -> auto { auto& _self = $receiver; ${sub.out.toString().trimEnd()} }()"
    }

    // ─── Type helpers ─────────────────────────────────────────────────────────

    /**
     * Infers the C++ type from an initializer expression.
     * Used for struct members where `auto` is not allowed in C++.
     * Falls back to `decltype(init)` when the type cannot be determined statically.
     */
    private fun inferTypeFromInitializer(init: Expression?): String {
        if (init == null) return "auto"  // shouldn't happen for member properties, but fallback
        return when (init) {
            is IntLiteral       -> "int"
            is LongLiteral      -> "long long"
            is DoubleLiteral    -> "double"
            is FloatLiteral     -> "float"
            is BooleanLiteral   -> "bool"
            is CharLiteral      -> "char"
            is StringLiteral    -> "string"
            is StringTemplate   -> "string"
            is CallExpression   -> {
                val callee = init.callee
                if (callee is NameReference) {
                    val name = callee.name
                    // Try to infer from known stdlib calls
                    val typeArgs = init.typeArgs
                    when (name) {
                        "mutableListOf", "listOf", "arrayListOf", "arrayOf" -> {
                            val elem = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"
                            "vector<$elem>"
                        }
                        "mutableSetOf", "setOf", "hashSetOf", "sortedSetOf" -> {
                            val elem = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"
                            "set<$elem>"
                        }
                        "mutableMapOf", "mapOf", "hashMapOf" -> {
                            val k = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"
                            val v = if (typeArgs.size > 1) typeToCpp(typeArgs[1]) else "int"
                            "map<$k, $v>"
                        }
                        "TreeSet"       -> { val e = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"; "set<$e>" }
                        "TreeMap"       -> { val k = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"; val v = if (typeArgs.size > 1) typeToCpp(typeArgs[1]) else "int"; "map<$k, $v>" }
                        "HashMap"       -> { val k = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"; val v = if (typeArgs.size > 1) typeToCpp(typeArgs[1]) else "int"; "unordered_map<$k, $v>" }
                        "HashSet"       -> { val e = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"; "unordered_set<$e>" }
                        "ArrayDeque"    -> { val e = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"; "deque<$e>" }
                        "PriorityQueue" -> { val e = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"; "priority_queue<$e>" }
                        "StringBuilder" -> "ostringstream"
                        "IntArray" -> "vector<int>"
                        "LongArray" -> "vector<long long>"
                        "DoubleArray" -> "vector<double>"
                        "FloatArray" -> "vector<float>"
                        "BooleanArray" -> "vector<bool>"
                        "CharArray" -> "vector<char>"
                        "Array" -> {
                            val e = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"
                            "vector<$e>"
                        }
                        "List", "MutableList" -> {
                            val e = if (typeArgs.isNotEmpty()) typeToCpp(typeArgs[0]) else "int"
                            "vector<$e>"
                        }
                        else -> "decltype(${genExpr(init)})"
                    }
                } else "decltype(${genExpr(init)})"
            }
            is ObjectCreation -> typeToCpp(init.type)
            else -> "decltype(${genExpr(init)})"
        }
    }

    fun typeToCpp(type: KotlinType): String = TypeResolver.toCpp(type, config)

    private fun retTypeToCpp(fn: FunctionDecl): String {
        return when {
            fn.returnType == null         -> inferReturnType(fn)
            fn.returnType == KotlinType.Unit -> "void"
            else                          -> typeToCpp(fn.returnType)
        }
    }

    /** Infer the C++ return type when the Kotlin function has no explicit return type.
     *  Block bodies with no return-with-value → void.
     *  Expression bodies or block bodies with return values → auto. */
    private fun inferReturnType(fn: FunctionDecl): String {
        return when (val body = fn.body) {
            is BlockBody -> if (blockHasReturnWithValue(body.statements)) "auto" else "void"
            is ExpressionBody -> "auto"
            null -> "void"
        }
    }

    /** Check if a list of statements contains any return statement with a value. */
    private fun blockHasReturnWithValue(stmts: List<Statement>): Boolean {
        for (stmt in stmts) {
            if (stmt is ReturnStatement && stmt.value != null) return true
            // Check nested blocks
            if (stmt is IfStatement) {
                if (blockHasReturnWithValue(stmt.thenBranch)) return true
                if (stmt.elseBranch != null && blockHasReturnWithValue(stmt.elseBranch)) return true
            }
            if (stmt is ForStatement && blockHasReturnWithValue(stmt.body)) return true
            if (stmt is WhileStatement && blockHasReturnWithValue(stmt.body)) return true
        }
        return false
    }

    private fun paramToCpp(p: Parameter): String {
        val type = if (p.type != null) typeToCpp(p.type) else "auto"
        val vararg = if (p.isVararg) "..." else ""
        return "$type$vararg ${p.name}"
    }

    // ─── String escaping ─────────────────────────────────────────────────────

    private fun escapeString(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun escapeChar(c: Char): String = when (c) {
        '\'' -> "\\'"
        '\\' -> "\\\\"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> c.toString()
    }

    /** Map Kotlin numeric companion constants (Int.MAX_VALUE, Long.MIN_VALUE, etc.) to C++ equivalents. */
    private fun numericConstant(typeName: String, propName: String): String? = when (typeName) {
        "Int" -> when (propName) {
            "MAX_VALUE" -> "INT_MAX"
            "MIN_VALUE" -> "INT_MIN"
            else -> null
        }
        "Long" -> when (propName) {
            "MAX_VALUE" -> "LLONG_MAX"
            "MIN_VALUE" -> "LLONG_MIN"
            else -> null
        }
        "Short" -> when (propName) {
            "MAX_VALUE" -> "SHRT_MAX"
            "MIN_VALUE" -> "SHRT_MIN"
            else -> null
        }
        "Byte" -> when (propName) {
            "MAX_VALUE" -> "SCHAR_MAX"
            "MIN_VALUE" -> "SCHAR_MIN"
            else -> null
        }
        "Double" -> when (propName) {
            "MAX_VALUE" -> "DBL_MAX"
            "MIN_VALUE" -> "DBL_MIN"
            "POSITIVE_INFINITY" -> "numeric_limits<double>::infinity()"
            "NEGATIVE_INFINITY" -> "(-numeric_limits<double>::infinity())"
            "NaN" -> "numeric_limits<double>::quiet_NaN()"
            else -> null
        }
        "Float" -> when (propName) {
            "MAX_VALUE" -> "FLT_MAX"
            "MIN_VALUE" -> "FLT_MIN"
            "POSITIVE_INFINITY" -> "numeric_limits<float>::infinity()"
            "NEGATIVE_INFINITY" -> "(-numeric_limits<float>::infinity())"
            "NaN" -> "numeric_limits<float>::quiet_NaN()"
            else -> null
        }
        "Char" -> when (propName) {
            "MAX_VALUE" -> "CHAR_MAX"
            "MIN_VALUE" -> "CHAR_MIN"
            else -> null
        }
        else -> null
    }

    // ─── Name sanitization ────────────────────────────────────────────────────

    private val cppReservedTypeNames = setOf(
        "stack", "set", "map", "string", "vector", "queue", "deque",
        "list", "array", "pair", "tuple", "bitset", "multiset", "multimap",
        "priority_queue", "unordered_set", "unordered_map"
    )

    private fun sanitizeName(name: String): String = when (name) {
        "it"        -> "_it"
        "new"       -> "_new"
        "delete"    -> "_delete"
        "class"     -> "_class"
        "template"  -> "_template"
        "namespace" -> "_namespace"
        "register"  -> "_register"
        "volatile"  -> "_volatile"
        "nullptr"   -> "_nullptr"
        "short"     -> "_short"
        "long"      -> "_long"
        "int"       -> "_int"
        "float"     -> "_float"
        "double"    -> "_double"
        "char"      -> "_char"
        "bool"      -> "_bool"
        "unsigned"  -> "_unsigned"
        "signed"    -> "_signed"
        "auto"      -> "_auto"
        "const"     -> "_const"
        "static"    -> "_static"
        "extern"    -> "_extern"
        "struct"    -> "_struct"
        "union"     -> "_union"
        "enum"      -> "_enum"
        "virtual"   -> "_virtual"
        "friend"    -> "_friend"
        "operator"  -> "_operator"
        in cppReservedTypeNames -> "_$name"
        else        -> name
    }

    companion object {
        private const val INDENT = "    "
    }
}

