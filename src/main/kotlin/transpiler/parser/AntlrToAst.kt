package transpiler.parser

import transpiler.ast.*
import transpiler.parser.generated.KotlinParser
import transpiler.parser.generated.KotlinParserBaseVisitor
import transpiler.typesystem.TypeResolver

/**
 * Converts the ANTLR parse tree produced by [KotlinParser] into the internal
 * [KotlinFile] AST.
 *
 * Unrecognised constructs that would block compilation throw
 * [UnsupportedOperationException]; minor unsupported features may return a
 * fallback node and print a warning to stderr.
 */
class AntlrToAst : KotlinParserBaseVisitor<Any?>() {

    // ─── File ────────────────────────────────────────────────────────────────

    fun convertKotlinFile(ctx: KotlinParser.KotlinFileContext): KotlinFile {
        val decls = ctx.topLevelObject().mapNotNull { convertTopLevelObject(it) }
        return KotlinFile(decls)
    }

    private fun convertTopLevelObject(ctx: KotlinParser.TopLevelObjectContext): TopLevelDeclaration? =
        visitDeclarationAsTop(ctx.declaration())

    private fun visitDeclarationAsTop(ctx: KotlinParser.DeclarationContext?): TopLevelDeclaration? {
        ctx ?: return null
        return when {
            ctx.functionDeclaration() != null  -> TopLevelFunction(visitFunctionDecl(ctx.functionDeclaration()))
            ctx.classDeclaration() != null     -> TopLevelClass(visitClassDecl(ctx.classDeclaration()))
            ctx.objectDeclaration() != null    -> TopLevelObject(visitObjectDecl(ctx.objectDeclaration()))
            ctx.propertyDeclaration() != null  -> TopLevelProperty(visitPropertyDecl(ctx.propertyDeclaration()))
            ctx.typeAlias() != null            -> null   // ignore type aliases
            else -> null
        }
    }

    // ─── Function declaration ────────────────────────────────────────────────

    private fun visitFunctionDecl(ctx: KotlinParser.FunctionDeclarationContext): FunctionDecl {
        val name = ctx.simpleIdentifier().text
        val modifiers = parseModifiers(ctx.modifiers())
        val typeParams = ctx.typeParameters()?.typeParameter()?.map { it.simpleIdentifier().text } ?: emptyList()
        val params = ctx.functionValueParameters().functionValueParameter().map { visitFunctionParam(it) }
        val returnType = ctx.type()?.let { convertType(it) }
        val body = ctx.functionBody()?.let { convertFunctionBody(it) }
        return FunctionDecl(name, typeParams, params, returnType, body, modifiers)
    }

    private fun visitFunctionParam(ctx: KotlinParser.FunctionValueParameterContext): Parameter {
        val param = ctx.parameter()
        val name = param.simpleIdentifier().text
        val type = param.type()?.let { convertType(it) }
        val default = ctx.expression()?.let { visitExpr(it) }
        val isVararg = ctx.parameterModifiers()?.parameterModifier()
            ?.any { it.VARARG() != null } == true
        return Parameter(name, type, default, isVararg)
    }

    private fun convertFunctionBody(ctx: KotlinParser.FunctionBodyContext): FunctionBody =
        if (ctx.block() != null) BlockBody(convertBlock(ctx.block()))
        else ExpressionBody(visitExpr(ctx.expression()))

    // ─── Class declaration ───────────────────────────────────────────────────

    private fun visitClassDecl(ctx: KotlinParser.ClassDeclarationContext): ClassDecl {
        val name = ctx.simpleIdentifier().text
        val modifiers = parseModifiers(ctx.modifiers())
        val kind = when {
            ctx.modifiers()?.modifier()?.any { it.classModifier()?.SEALED() != null } == true -> ClassKind.Sealed
            ctx.modifiers()?.modifier()?.any { it.classModifier()?.DATA() != null } == true   -> ClassKind.Data
            ctx.modifiers()?.modifier()?.any { it.inheritanceModifier()?.ABSTRACT() != null } == true -> ClassKind.Abstract
            ctx.INTERFACE() != null -> ClassKind.Interface
            else -> ClassKind.Regular
        }
        val typeParams = ctx.typeParameters()?.typeParameter()?.map { it.simpleIdentifier().text } ?: emptyList()
        val primaryCtor = ctx.primaryConstructor()?.classParameters()?.classParameter()
            ?.map { visitConstructorParam(it) } ?: emptyList()
        val superTypes = ctx.delegationSpecifiers()?.annotatedDelegationSpecifier()
            ?.map { convertDelegationSpecifier(it.delegationSpecifier()) } ?: emptyList()
        val members = ctx.classBody()?.classMemberDeclarations()?.classMemberDeclaration()
            ?.mapNotNull { visitClassMember(it) } ?: emptyList()
        return ClassDecl(name, kind, typeParams, primaryCtor, superTypes, members)
    }

    private fun visitConstructorParam(ctx: KotlinParser.ClassParameterContext): ConstructorParam {
        val name = ctx.simpleIdentifier().text
        val type = convertType(ctx.type())
        val isVal = ctx.VAL() != null
        val isVar = ctx.VAR() != null
        val default = ctx.expression()?.let { visitExpr(it) }
        return ConstructorParam(name, type, isVal, isVar, default)
    }

    private fun convertDelegationSpecifier(ctx: KotlinParser.DelegationSpecifierContext): SuperTypeEntry =
        when {
            ctx.constructorInvocation() != null -> {
                val ci = ctx.constructorInvocation()
                val type = convertUserType(ci.userType())
                val args = ci.valueArguments()?.valueArgument()?.map { visitCallArg(it).value } ?: emptyList()
                SuperTypeEntry(type, args)
            }
            ctx.userType() != null -> SuperTypeEntry(convertUserType(ctx.userType()))
            else -> SuperTypeEntry(KotlinType.Simple("Any"))
        }

    private fun visitClassMember(ctx: KotlinParser.ClassMemberDeclarationContext): ClassMember? =
        when {
            ctx.declaration() != null -> {
                val d = ctx.declaration()
                when {
                    d.functionDeclaration() != null -> MemberFunction(visitFunctionDecl(d.functionDeclaration()))
                    d.propertyDeclaration() != null -> MemberProperty(visitPropertyDecl(d.propertyDeclaration()))
                    d.classDeclaration() != null    -> null  // nested class: skip for now
                    d.objectDeclaration() != null   -> null
                    else -> null
                }
            }
            ctx.anonymousInitializer() != null ->
                InitBlock(convertBlock(ctx.anonymousInitializer().block()))
            ctx.companionObject() != null -> {
                val co = ctx.companionObject()
                val members = co.classBody()?.classMemberDeclarations()?.classMemberDeclaration()
                    ?.mapNotNull { visitClassMember(it) } ?: emptyList()
                CompanionObject(members)
            }
            ctx.secondaryConstructor() != null -> {
                val sc = ctx.secondaryConstructor()
                val params = sc.functionValueParameters().functionValueParameter().map { visitFunctionParam(it) }
                val delegArgs = sc.constructorDelegationCall()?.valueArguments()?.valueArgument()
                    ?.map { visitCallArg(it).value } ?: emptyList()
                val body = sc.block()?.let { convertBlock(it) } ?: emptyList()
                SecondaryConstructor(params, delegArgs, body)
            }
            else -> null
        }

    // ─── Object declaration ──────────────────────────────────────────────────

    private fun visitObjectDecl(ctx: KotlinParser.ObjectDeclarationContext): ObjectDecl {
        val name = ctx.simpleIdentifier().text
        val superTypes = ctx.delegationSpecifiers()?.annotatedDelegationSpecifier()
            ?.map { convertDelegationSpecifier(it.delegationSpecifier()) } ?: emptyList()
        val members = ctx.classBody()?.classMemberDeclarations()?.classMemberDeclaration()
            ?.mapNotNull { visitClassMember(it) } ?: emptyList()
        return ObjectDecl(name, superTypes, members)
    }

    // ─── Property declaration ────────────────────────────────────────────────

    private fun visitPropertyDecl(ctx: KotlinParser.PropertyDeclarationContext): PropertyDecl {
        val isMutable = ctx.VAR() != null
        val modifiers = parseModifiers(ctx.modifiers())

        // Handle multi-variable destructuring at top level (rare, skip for now)
        val varDecl = ctx.variableDeclaration()
            ?: return PropertyDecl("_destruct", null, null, isMutable, modifiers)

        val name = varDecl.simpleIdentifier().text
        val type = varDecl.type()?.let { convertType(it) }
        val init = ctx.expression()?.let { visitExpr(it) }
        return PropertyDecl(name, type, init, isMutable, modifiers)
    }

    // ─── Statements ──────────────────────────────────────────────────────────

    private fun convertBlock(ctx: KotlinParser.BlockContext): List<Statement> =
        ctx.statements()?.statement()?.mapNotNull { convertStatement(it) } ?: emptyList()

    private fun convertStatement(ctx: KotlinParser.StatementContext): Statement? {
        // Labels (e.g. "outer@")
        val labels = ctx.label().map { it.simpleIdentifier().text }

        val innerStmt: Statement? = when {
            ctx.declaration() != null -> {
                val d = ctx.declaration()
                when {
                    d.functionDeclaration() != null -> {
                        // Nested function → treated as a special statement
                        NestedFunctionStatement(visitFunctionDecl(d.functionDeclaration()))
                    }
                    d.propertyDeclaration() != null -> visitLocalProperty(d.propertyDeclaration())
                    d.classDeclaration() != null    -> null  // nested class: skip
                    else -> null
                }
            }
            ctx.assignment() != null     -> convertAssignment(ctx.assignment())
            ctx.loopStatement() != null  -> convertLoopStatement(ctx.loopStatement())
            ctx.expression() != null     -> {
                val expr = visitExpr(ctx.expression())
                // Unwrap IfExpression at statement position into IfStatement
                if (expr is IfExpression) {
                    ifExprToStatement(expr)
                }
                // Unwrap WhenExpression at statement position into WhenStatement
                else if (expr is WhenExpression) {
                    WhenStatement(expr.subject, expr.entries)
                } else {
                    ExpressionStatement(expr)
                }
            }
            else -> null
        }

        if (innerStmt == null) return null

        // Wrap with labels
        return labels.foldRight(innerStmt) { label, stmt -> LabeledStatement(label, stmt) }
    }

    /** Converts an [IfExpression] to an [IfStatement], recursively handling else-if chains. */
    private fun ifExprToStatement(expr: IfExpression): IfStatement {
        fun branchToStmts(branch: IfBranch): List<Statement> = when (branch) {
            is BlockBranch -> branch.statements.map { stmt ->
                // Recursively convert nested IfExpression inside ExpressionStatement
                if (stmt is ExpressionStatement && stmt.expr is IfExpression)
                    ifExprToStatement(stmt.expr as IfExpression)
                else stmt
            }
            is ExprBranch  -> if (branch.expr is NullLiteral) emptyList()
                              else if (branch.expr is IfExpression) listOf(ifExprToStatement(branch.expr as IfExpression))
                              else listOf(ExpressionStatement(branch.expr))
        }
        val thenStmts = branchToStmts(expr.thenBranch)
        val elseRaw   = branchToStmts(expr.elseBranch)
        val elseStmts = if (elseRaw.isEmpty()) null else elseRaw
        return IfStatement(expr.condition, thenStmts, elseStmts)
    }

    private fun visitLocalProperty(ctx: KotlinParser.PropertyDeclarationContext): Statement {
        val isMutable = ctx.VAR() != null
        // Destructuring: val (a, b) = expr
        if (ctx.multiVariableDeclaration() != null) {
            val mvd = ctx.multiVariableDeclaration()
            val names = mvd.variableDeclaration().map { it.simpleIdentifier().text as String? }
            val type = null
            val init = ctx.expression()?.let { visitExpr(it) }
                ?: throw UnsupportedOperationException("Destructuring without initializer")
            return DestructuringDeclaration(names, type, init, isMutable)
        }
        val varDecl = ctx.variableDeclaration()
        val name = varDecl.simpleIdentifier().text
        val type = varDecl.type()?.let { convertType(it) }
        val init = ctx.expression()?.let { visitExpr(it) }
        return LocalProperty(name, type, init, isMutable)
    }

    private fun convertAssignment(ctx: KotlinParser.AssignmentContext): Statement {
        val op = when {
            ctx.assignmentAndOperator() != null -> when (ctx.assignmentAndOperator().text) {
                "+=" -> AssignOp.PlusAssign
                "-=" -> AssignOp.MinusAssign
                "*=" -> AssignOp.TimesAssign
                "/=" -> AssignOp.DivAssign
                "%=" -> AssignOp.ModAssign
                else -> AssignOp.Assign
            }
            else -> AssignOp.Assign
        }
        val target = visitAssignableTarget(ctx)
        val value = visitExpr(ctx.expression())
        return Assignment(target, value, op)
    }

    private fun visitAssignableTarget(ctx: KotlinParser.AssignmentContext): Expression {
        // The grammar: (directlyAssignableExpression ASSIGNMENT | assignableExpression assignmentAndOperator)
        if (ctx.directlyAssignableExpression() != null) {
            return visitDirectlyAssignableExpr(ctx.directlyAssignableExpression())
        }
        if (ctx.assignableExpression() != null) {
            return visitAssignableExpr(ctx.assignableExpression())
        }
        throw UnsupportedOperationException("Unknown assignment target: ${ctx.text.take(40)}")
    }

    private fun visitDirectlyAssignableExpr(ctx: KotlinParser.DirectlyAssignableExpressionContext): Expression {
        if (ctx.simpleIdentifier() != null) return NameReference(ctx.simpleIdentifier().text)
        if (ctx.postfixUnaryExpression() != null) {
            val base = convertPostfixUnaryExpression(ctx.postfixUnaryExpression())
            // Apply assignable suffix
            if (ctx.assignableSuffix() != null) {
                val suffix = ctx.assignableSuffix()
                return when {
                    suffix.indexingSuffix() != null -> {
                        val exprs = suffix.indexingSuffix().expression()
                        val additional = if (exprs.size > 1) exprs.drop(1).map { visitExpr(it) } else emptyList()
                        IndexAccess(base, visitExpr(exprs[0]), additional)
                    }
                    suffix.navigationSuffix() != null -> {
                        val nav = suffix.navigationSuffix()
                        val name = nav.simpleIdentifier()?.text ?: "?"
                        PropertyAccessExpr(base, name, nav.memberAccessOperator()?.safeNav() != null)
                    }
                    else -> base
                }
            }
            return base
        }
        if (ctx.parenthesizedDirectlyAssignableExpression() != null)
            return visitDirectlyAssignableExpr(ctx.parenthesizedDirectlyAssignableExpression().directlyAssignableExpression())
        return NameReference(ctx.text)
    }

    private fun visitAssignableExpr(ctx: KotlinParser.AssignableExpressionContext): Expression {
        if (ctx.prefixUnaryExpression() != null) return visitPrefixUnaryExpr(ctx.prefixUnaryExpression())
        if (ctx.parenthesizedAssignableExpression() != null)
            return visitAssignableExpr(ctx.parenthesizedAssignableExpression().assignableExpression())
        return NameReference(ctx.text)
    }

    private fun convertLoopStatement(ctx: KotlinParser.LoopStatementContext): Statement =
        when {
            ctx.forStatement() != null      -> convertForStatement(ctx.forStatement())
            ctx.whileStatement() != null    -> convertWhileStatement(ctx.whileStatement())
            ctx.doWhileStatement() != null  -> convertDoWhileStatement(ctx.doWhileStatement())
            else -> throw UnsupportedOperationException("Unknown loop")
        }

    private fun convertForStatement(ctx: KotlinParser.ForStatementContext): ForStatement {
        val variable: ForLoopVariable = when {
            ctx.variableDeclaration() != null -> SimpleVar(ctx.variableDeclaration().simpleIdentifier().text)
            ctx.multiVariableDeclaration() != null ->
                DestructuredVar(ctx.multiVariableDeclaration().variableDeclaration()
                    .map { it.simpleIdentifier().text as String? })
            else -> throw UnsupportedOperationException("Unknown for variable")
        }
        val iterable = visitExpr(ctx.expression())
        val body = ctx.controlStructureBody()?.let { visitControlBody(it) } ?: emptyList()
        return ForStatement(variable, iterable, body)
    }

    private fun convertWhileStatement(ctx: KotlinParser.WhileStatementContext): WhileStatement {
        val cond = visitExpr(ctx.expression())
        val body = ctx.controlStructureBody()?.let { visitControlBody(it) } ?: emptyList()
        return WhileStatement(cond, body)
    }

    private fun convertDoWhileStatement(ctx: KotlinParser.DoWhileStatementContext): WhileStatement {
        val cond = visitExpr(ctx.expression())
        val body = ctx.controlStructureBody()?.let { visitControlBody(it) } ?: emptyList()
        return WhileStatement(cond, body, isDoWhile = true)
    }

    private fun visitControlBody(ctx: KotlinParser.ControlStructureBodyContext): List<Statement> =
        when {
            ctx.block() != null    -> convertBlock(ctx.block())
            ctx.statement() != null -> listOfNotNull(convertStatement(ctx.statement()))
            else -> emptyList()
        }

    // ─── Expressions ─────────────────────────────────────────────────────────

    private fun visitExpr(ctx: KotlinParser.ExpressionContext): Expression =
        convertDisjunction(ctx.disjunction())

    private fun convertDisjunction(ctx: KotlinParser.DisjunctionContext): Expression {
        val parts = ctx.conjunction().map { convertConjunction(it) }
        return parts.reduce { acc, e -> BinaryExpression(acc, BinaryOp.Or, e) }
    }

    private fun convertConjunction(ctx: KotlinParser.ConjunctionContext): Expression {
        val parts = ctx.equality().map { convertEquality(it) }
        return parts.reduce { acc, e -> BinaryExpression(acc, BinaryOp.And, e) }
    }

    private fun convertEquality(ctx: KotlinParser.EqualityContext): Expression {
        val comparisons = ctx.comparison().map { convertComparison(it) }
        val ops = ctx.equalityOperator().map { parseEqualityOp(it) }
        return foldBinary(comparisons, ops)
    }

    private fun parseEqualityOp(ctx: KotlinParser.EqualityOperatorContext): BinaryOp = when {
        ctx.EQEQ() != null     -> BinaryOp.Eq
        ctx.EXCL_EQ() != null  -> BinaryOp.NotEq
        ctx.EQEQEQ() != null   -> BinaryOp.RefEq
        ctx.EXCL_EQEQ() != null -> BinaryOp.RefNotEq
        else -> BinaryOp.Eq
    }

    private fun convertComparison(ctx: KotlinParser.ComparisonContext): Expression {
        val parts = ctx.genericCallLikeComparison().map { visitGenericCallLike(it) }
        val ops = ctx.comparisonOperator().map { parseCmpOp(it) }
        return foldBinary(parts, ops)
    }

    private fun parseCmpOp(ctx: KotlinParser.ComparisonOperatorContext): BinaryOp = when {
        ctx.LANGLE() != null  -> BinaryOp.Lt
        ctx.RANGLE() != null  -> BinaryOp.Gt
        ctx.LE() != null      -> BinaryOp.LtEq
        ctx.GE() != null      -> BinaryOp.GtEq
        else -> BinaryOp.Lt
    }

    private fun visitGenericCallLike(ctx: KotlinParser.GenericCallLikeComparisonContext): Expression {
        var result = convertInfixOperation(ctx.infixOperation())
        // Extra call suffixes after an infix operation (rare, handles `f<T>(x)` parsing ambiguity)
        for (cs in ctx.callSuffix()) {
            result = applyCallSuffix(result, cs)
        }
        return result
    }

    private fun convertInfixOperation(ctx: KotlinParser.InfixOperationContext): Expression {
        var result = visitElvis(ctx.elvisExpression(0))
        var i = 0
        val children = ctx.children ?: return result
        // Alternate between operators and operands after the first
        var exprIdx = 1
        for (child in children.drop(1)) {
            val text = child.text
            result = when {
                child is KotlinParser.InOperatorContext -> {
                    val rhs = visitElvis(ctx.elvisExpression(exprIdx++))
                    val negated = child.NOT_IN() != null
                    // x in collection → call contains
                    if (negated)
                        PrefixExpression(PrefixOp.Not, CallExpression(
                            PropertyAccessExpr(rhs, "contains"), emptyList(),
                            listOf(CallArgument(null, result))))
                    else
                        CallExpression(PropertyAccessExpr(rhs, "contains"), emptyList(),
                            listOf(CallArgument(null, result)))
                }
                child is KotlinParser.IsOperatorContext -> {
                    val type = convertType(ctx.type(i++))
                    val negated = child.NOT_IS() != null
                    TypeCheckExpression(result, type, negated)
                }
                else -> result
            }
        }
        return result
    }

    private fun visitElvis(ctx: KotlinParser.ElvisExpressionContext): Expression {
        val parts = ctx.infixFunctionCall().map { convertInfixFunctionCall(it) }
        return parts.reduce { acc, e -> ElvisExpression(acc, e) }
    }

    private fun convertInfixFunctionCall(ctx: KotlinParser.InfixFunctionCallContext): Expression {
        val parts = ctx.rangeExpression().map { visitRangeExpr(it) }
        val funcs = ctx.simpleIdentifier().map { it.text }
        if (parts.size == 1) return parts[0]
        var result = parts[0]
        for ((i, func) in funcs.withIndex()) {
            val rhs = parts[i + 1]
            result = when (func) {
                "shl"    -> BinaryExpression(result, BinaryOp.Shl, rhs)
                "shr"    -> BinaryExpression(result, BinaryOp.Shr, rhs)
                "ushr"   -> BinaryExpression(result, BinaryOp.Ushr, rhs)
                "and"    -> BinaryExpression(result, BinaryOp.BitAnd, rhs)
                "or"     -> BinaryExpression(result, BinaryOp.BitOr, rhs)
                "xor"    -> BinaryExpression(result, BinaryOp.BitXor, rhs)
                "until"  -> RangeExpression(result, rhs, RangeKind.Until)
                "downTo" -> RangeExpression(result, rhs, RangeKind.DownTo)
                "step"   -> {
                    // `range step n` — add step to existing range
                    if (result is RangeExpression) result.copy(step = rhs)
                    else result  // fallback
                }
                "to"     -> CallExpression(NameReference("Pair"), emptyList(),
                                listOf(CallArgument(null, result), CallArgument(null, rhs)))
                else     -> {
                    // Generic infix function call: f(a, b) style
                    CallExpression(NameReference(func), emptyList(),
                        listOf(CallArgument(null, result), CallArgument(null, rhs)))
                }
            }
        }
        return result
    }

    private fun visitRangeExpr(ctx: KotlinParser.RangeExpressionContext): Expression {
        val parts = ctx.additiveExpression().map { visitAdditive(it) }
        if (parts.size == 1) return parts[0]
        return parts.zipWithNext { a, b ->
            // Check which range operator was used
            val hasRangeUntil = ctx.RANGE_UNTIL().isNotEmpty()
            if (hasRangeUntil) RangeExpression(a, b, RangeKind.Until)
            else RangeExpression(a, b, RangeKind.Inclusive)
        }.first()
    }

    private fun visitAdditive(ctx: KotlinParser.AdditiveExpressionContext): Expression {
        val parts = ctx.multiplicativeExpression().map { visitMultiplicative(it) }
        val ops = ctx.additiveOperator().map { op ->
            if (op.ADD() != null) BinaryOp.Plus else BinaryOp.Minus
        }
        return foldBinary(parts, ops)
    }

    private fun visitMultiplicative(ctx: KotlinParser.MultiplicativeExpressionContext): Expression {
        val parts = ctx.asExpression().map { visitAsExpr(it) }
        val ops = ctx.multiplicativeOperator().map { op ->
            when {
                op.MULT() != null -> BinaryOp.Times
                op.DIV() != null  -> BinaryOp.Div
                else              -> BinaryOp.Mod
            }
        }
        return foldBinary(parts, ops)
    }

    private fun visitAsExpr(ctx: KotlinParser.AsExpressionContext): Expression {
        var result = visitPrefixUnaryExpr(ctx.prefixUnaryExpression())
        for (i in ctx.asOperator().indices) {
            val op = ctx.asOperator(i)
            val type = convertType(ctx.type(i))
            val isSafe = op.AS_SAFE() != null
            result = TypeCastExpression(result, type, isSafe)
        }
        return result
    }

    private fun visitPrefixUnaryExpr(ctx: KotlinParser.PrefixUnaryExpressionContext): Expression {
        var result = convertPostfixUnaryExpression(ctx.postfixUnaryExpression())
        // Apply prefixes in reverse order (they're listed left-to-right but applied right-to-left)
        for (prefix in ctx.unaryPrefix().asReversed()) {
            val op = prefix.prefixUnaryOperator() ?: continue
            val prefixOp = when {
                op.ADD() != null     -> PrefixOp.UnaryPlus
                op.SUB() != null     -> PrefixOp.UnaryMinus
                op.excl() != null -> PrefixOp.Not
                op.INCR() != null    -> PrefixOp.PreInc
                op.DECR() != null    -> PrefixOp.PreDec
                else -> continue
            }
            result = PrefixExpression(prefixOp, result)
        }
        return result
    }

    private fun convertPostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext): Expression {
        var result: Expression = visitPrimary(ctx.primaryExpression())
        val suffixes = ctx.postfixUnarySuffix()
        var i = 0
        // Pending type arguments from a typeArguments suffix, to be forwarded to next call suffix
        var pendingTypeArgs: List<KotlinType>? = null
        while (i < suffixes.size) {
            val suffix = suffixes[i]
            result = when {
                suffix.postfixUnaryOperator() != null -> {
                    val op = when {
                        suffix.postfixUnaryOperator().INCR() != null -> PostfixOp.PostInc
                        suffix.postfixUnaryOperator().DECR() != null -> PostfixOp.PostDec
                        suffix.postfixUnaryOperator().excl() != null -> PostfixOp.NotNull
                        else -> PostfixOp.NotNull
                    }
                    i++
                    pendingTypeArgs = null
                    PostfixExpression(result, op)
                }
                suffix.callSuffix() != null -> {
                    i++
                    val callExpr = applyCallSuffix(result, suffix.callSuffix())
                    // If we had pending type args from a previous typeArguments suffix,
                    // merge them into this call expression (if the call itself has none)
                    val savedTypeArgs = pendingTypeArgs
                    pendingTypeArgs = null
                    if (savedTypeArgs != null && callExpr is CallExpression && callExpr.typeArgs.isEmpty()) {
                        callExpr.copy(typeArgs = savedTypeArgs)
                    } else {
                        callExpr
                    }
                }
                suffix.indexingSuffix() != null -> {
                    i++
                    pendingTypeArgs = null
                    val exprs = suffix.indexingSuffix().expression()
                    val additional = if (exprs.size > 1) exprs.drop(1).map { visitExpr(it) } else emptyList()
                    IndexAccess(result, visitExpr(exprs[0]), additional)
                }
                suffix.navigationSuffix() != null -> {
                    val nav = suffix.navigationSuffix()
                    val isSafe = nav.memberAccessOperator()?.safeNav() != null
                    val isScope = nav.memberAccessOperator()?.COLONCOLON() != null

                    val memberName = nav.simpleIdentifier()?.text
                    if (memberName == null) { i++; pendingTypeArgs = null; result }
                    else if (isScope) {
                        // Method reference: receiver::method → lambda that calls method on receiver
                        i++
                        pendingTypeArgs = null
                        // Generate: { _it -> receiver.method(_it) } as a lambda
                        LambdaExpression(listOf(LambdaParam("_it")), listOf(
                            ExpressionStatement(MethodCallExpression(result, memberName, emptyList(),
                                listOf(CallArgument(null, NameReference("_it")))))))
                    } else {
                        // Peek: if next suffix is a callSuffix, it's a method call
                        if (i + 1 < suffixes.size && suffixes[i + 1].callSuffix() != null) {
                            val callCtx = suffixes[i + 1].callSuffix()
                            i += 2
                            pendingTypeArgs = null
                            buildMethodCall(result, memberName, callCtx, isSafe)
                        } else {
                            i++
                            pendingTypeArgs = null
                            // Check if this is a known property that should become a call with ()
                            PropertyAccessExpr(result, memberName, isSafe)
                        }
                    }
                }
                suffix.typeArguments() != null -> {
                    // Save type arguments to forward to the next call suffix
                    pendingTypeArgs = suffix.typeArguments().typeProjection().mapNotNull {
                        it.type()?.let { t -> convertType(t) }
                    }
                    i++
                    result
                }
                else -> { i++; pendingTypeArgs = null; result }
            }
        }
        return result
    }

    private fun applyCallSuffix(callee: Expression, ctx: KotlinParser.CallSuffixContext): Expression {
        val typeArgs = ctx.typeArguments()?.typeProjection()?.mapNotNull {
            it.type()?.let { t -> convertType(t) }
        } ?: emptyList()
        val args = ctx.valueArguments()?.valueArgument()?.map { visitCallArg(it) } ?: emptyList()
        val lambda = ctx.annotatedLambda()?.lambdaLiteral()?.let { visitLambda(it) }
        return CallExpression(callee, typeArgs, args, lambda)
    }

    private fun buildMethodCall(
        receiver: Expression,
        method: String,
        ctx: KotlinParser.CallSuffixContext,
        isSafe: Boolean
    ): Expression {
        val typeArgs = ctx.typeArguments()?.typeProjection()?.mapNotNull {
            it.type()?.let { t -> convertType(t) }
        } ?: emptyList()
        val args = ctx.valueArguments()?.valueArgument()?.map { visitCallArg(it) } ?: emptyList()
        val lambda = ctx.annotatedLambda()?.lambdaLiteral()?.let { visitLambda(it) }
        return MethodCallExpression(receiver, method, typeArgs, args, lambda, isSafe)
    }

    private fun visitCallArg(ctx: KotlinParser.ValueArgumentContext): CallArgument {
        val name = ctx.simpleIdentifier()?.text
        val value = visitExpr(ctx.expression())
        return CallArgument(name, value)
    }

    // ─── Primary expressions ─────────────────────────────────────────────────

    private fun visitPrimary(ctx: KotlinParser.PrimaryExpressionContext): Expression = when {
        ctx.parenthesizedExpression() != null ->
            visitExpr(ctx.parenthesizedExpression().expression())
        ctx.simpleIdentifier() != null ->
            NameReference(ctx.simpleIdentifier().text)
        ctx.literalConstant() != null ->
            convertLiteralConstant(ctx.literalConstant())
        ctx.stringLiteral() != null ->
            convertStringLiteral(ctx.stringLiteral())
        ctx.functionLiteral() != null -> {
            val fl = ctx.functionLiteral()
            if (fl.lambdaLiteral() != null) visitLambda(fl.lambdaLiteral())
            else visitExpr(ctx.text)   // anonymous function: approximate
        }
        ctx.ifExpression() != null ->
            visitIfExpr(ctx.ifExpression())
        ctx.whenExpression() != null ->
            visitWhenExpr(ctx.whenExpression())
        ctx.thisExpression() != null ->
            ThisExpression()
        ctx.superExpression() != null ->
            SuperExpression()
        ctx.jumpExpression() != null ->
            visitJump(ctx.jumpExpression())
        ctx.collectionLiteral() != null ->
            convertCollectionLiteral(ctx.collectionLiteral())
        ctx.objectLiteral() != null ->
            AnonymousObject(emptyList(), emptyList())  // simplified
        ctx.callableReference() != null -> {
            // e.g. ::functionName or Type::member – approximate as lambda
            val ref = ctx.callableReference()
            val name = ref.simpleIdentifier()?.text ?: "?"
            LambdaExpression(listOf(LambdaParam("_it")), listOf(
                ExpressionStatement(CallExpression(NameReference(name), emptyList(),
                    listOf(CallArgument(null, NameReference("_it")))))))
        }
        ctx.tryExpression() != null ->
            visitTryExpr(ctx.tryExpression())
        else -> throw UnsupportedOperationException("Unknown primary: ${ctx.text.take(40)}")
    }

    private fun convertLiteralConstant(ctx: KotlinParser.LiteralConstantContext): Expression = when {
        ctx.BooleanLiteral() != null -> BooleanLiteral(ctx.text.trim() == "true")
        ctx.NullLiteral() != null    -> NullLiteral
        ctx.IntegerLiteral() != null -> {
            val text = ctx.text.replace("_", "")
            IntLiteral(text.toIntOrNull() ?: text.toLong().toInt())
        }
        ctx.LongLiteral() != null -> {
            val text = ctx.text.removeSuffix("L").removeSuffix("l").replace("_", "")
            LongLiteral(text.toLong())
        }
        ctx.HexLiteral() != null -> {
            val text = ctx.text.removePrefix("0x").removePrefix("0X").replace("_", "")
            IntLiteral(text.toLong(16).toInt())
        }
        ctx.BinLiteral() != null -> {
            val text = ctx.text.removePrefix("0b").removePrefix("0B").replace("_", "")
            IntLiteral(text.toLong(2).toInt())
        }
        ctx.RealLiteral() != null -> {
            val text = ctx.text.replace("_", "")
            if (text.endsWith("f") || text.endsWith("F"))
                FloatLiteral(text.dropLast(1).toFloat())
            else
                DoubleLiteral(text.toDouble())
        }
        ctx.CharacterLiteral() != null -> {
            val s = ctx.text
            val inner = s.substring(1, s.length - 1)
            val c = when (inner) {
                "\\n" -> '\n'; "\\t" -> '\t'; "\\r" -> '\r';
                "\\'" -> '\''; "\\\\" -> '\\'; "\\0" -> '\u0000'
                else -> if (inner.startsWith("\\u"))
                    inner.substring(2).toInt(16).toChar()
                else inner[0]
            }
            CharLiteral(c)
        }
        ctx.UnsignedLiteral() != null -> {
            val text = ctx.text.replace("_", "").trimEnd('u','U','L','l')
            IntLiteral(text.toLongOrNull()?.toInt() ?: 0)
        }
        else -> IntLiteral(0)
    }

    private fun convertStringLiteral(ctx: KotlinParser.StringLiteralContext): Expression {
        val line = ctx.lineStringLiteral()
        val multi = ctx.multiLineStringLiteral()
        return when {
            line != null -> visitLineString(line)
            multi != null -> visitMultiLineString(multi)
            else -> StringLiteral("")
        }
    }

    private fun visitLineString(ctx: KotlinParser.LineStringLiteralContext): Expression {
        val parts = mutableListOf<StringPart>()
        for (child in ctx.children ?: emptyList()) {
            when (child) {
                is KotlinParser.LineStringContentContext -> {
                    val text = when {
                        child.LineStrText() != null       -> child.LineStrText().text
                        child.LineStrEscapedChar() != null -> unescapeKotlinStringChar(child.LineStrEscapedChar().text)
                        child.LineStrRef() != null        -> {
                            // $variable reference
                            val varName = child.LineStrRef().text.removePrefix("\$")
                            parts.add(ExpressionStringPart(NameReference(varName)))
                            continue
                        }
                        else -> ""
                    }
                    if (text.isNotEmpty()) parts.add(LiteralStringPart(text))
                }
                is KotlinParser.LineStringExpressionContext -> {
                    parts.add(ExpressionStringPart(visitExpr(child.expression())))
                }
            }
        }
        return when {
            parts.isEmpty() -> StringLiteral("")
            parts.size == 1 && parts[0] is LiteralStringPart -> StringLiteral((parts[0] as LiteralStringPart).value)
            else -> StringTemplate(parts)
        }
    }

    private fun visitMultiLineString(ctx: KotlinParser.MultiLineStringLiteralContext): Expression {
        val sb = StringBuilder()
        for (child in ctx.children ?: emptyList()) {
            when (child) {
                is KotlinParser.MultiLineStringContentContext -> {
                    if (child.MultiLineStrText() != null) sb.append(child.MultiLineStrText().text)
                    else if (child.MultiLineStringQuote() != null) sb.append('"')
                    // $var references in multiline strings
                    else if (child.MultiLineStrRef() != null) {
                        val varName = child.MultiLineStrRef().text.removePrefix("\$")
                        // For simplicity, convert to template
                        val parts = mutableListOf<StringPart>()
                        if (sb.isNotEmpty()) { parts.add(LiteralStringPart(sb.toString())); sb.clear() }
                        parts.add(ExpressionStringPart(NameReference(varName)))
                        return StringTemplate(parts)
                    }
                }
                is KotlinParser.MultiLineStringExpressionContext -> {
                    val beforeStr = sb.toString(); sb.clear()
                    val parts = mutableListOf<StringPart>()
                    if (beforeStr.isNotEmpty()) parts.add(LiteralStringPart(beforeStr))
                    parts.add(ExpressionStringPart(visitExpr(child.expression())))
                    // Note: there may be more content after; this is simplified
                    return StringTemplate(parts)
                }
            }
        }
        return StringLiteral(sb.toString())
    }

    private fun unescapeKotlinStringChar(s: String): String = when (s) {
        "\\n"  -> "\n"; "\\t" -> "\t"; "\\r" -> "\r"
        "\\\"" -> "\""; "\\\\" -> "\\"
        "\\'"  -> "'"; "\\$" -> "\$"
        else -> if (s.startsWith("\\u"))
            s.substring(2).toInt(16).toChar().toString()
        else s
    }

    private fun visitLambda(ctx: KotlinParser.LambdaLiteralContext): LambdaExpression {
        val params = ctx.lambdaParameters()?.lambdaParameter()?.map { lp ->
            val vd = lp.variableDeclaration()
            val mvd = lp.multiVariableDeclaration()
            when {
                vd != null  -> LambdaParam(vd.simpleIdentifier().text, vd.type()?.let { convertType(it) })
                mvd != null -> LambdaParam(mvd.variableDeclaration().joinToString(",") { it.simpleIdentifier().text })
                else        -> LambdaParam("it")
            }
        } ?: emptyList()
        val stmts = ctx.statements()?.statement()?.mapNotNull { convertStatement(it) } ?: emptyList()
        return LambdaExpression(params, stmts)
    }

    private fun visitIfExpr(ctx: KotlinParser.IfExpressionContext): Expression {
        val cond = visitExpr(ctx.expression())
        val bodies = ctx.controlStructureBody()
        val thenCtx = bodies.getOrNull(0)
        val elseCtx  = bodies.getOrNull(1)

        fun bodyToBranch(ctrl: KotlinParser.ControlStructureBodyContext?): IfBranch {
            if (ctrl == null) return BlockBranch(emptyList())
            val stmts = visitControlBody(ctrl)
            if (stmts.size == 1 && stmts[0] is ExpressionStatement)
                return ExprBranch((stmts[0] as ExpressionStatement).expr)
            return BlockBranch(stmts)
        }

        val thenBranch = bodyToBranch(thenCtx)
        val elseBranch = bodyToBranch(elseCtx)

        // If both branches are expressions, it's an if-expression; otherwise statement
        return if (elseBranch is BlockBranch && elseBranch.statements.isEmpty()) {
            // No else → this is an if statement used as expression (rarely), treat as expression
            IfExpression(cond, thenBranch, ExprBranch(NullLiteral))
        } else {
            IfExpression(cond, thenBranch, elseBranch)
        }
    }

    private fun visitWhenExpr(ctx: KotlinParser.WhenExpressionContext): Expression {
        val subject = ctx.whenSubject()?.let { ws ->
            val binding = ws.variableDeclaration()?.simpleIdentifier()?.text
            WhenSubject(visitExpr(ws.expression()), binding)
        }
        val entries = ctx.whenEntry().map { convertWhenEntry(it) }
        return WhenExpression(subject, entries)
    }

    private fun convertWhenEntry(ctx: KotlinParser.WhenEntryContext): WhenEntry {
        val isElse = ctx.ELSE() != null
        val conditions: List<WhenCondition> = if (isElse) emptyList()
        else ctx.whenCondition().map { convertWhenCondition(it) }
        val body = visitControlBody(ctx.controlStructureBody())
        val entryBody: WhenEntryBody = if (body.size == 1 && body[0] is ExpressionStatement)
            ExpressionEntryBody((body[0] as ExpressionStatement).expr)
        else BlockEntryBody(body)
        return WhenEntry(conditions, entryBody)
    }

    private fun convertWhenCondition(ctx: KotlinParser.WhenConditionContext): WhenCondition = when {
        ctx.expression() != null -> ExpressionCondition(visitExpr(ctx.expression()))
        ctx.rangeTest() != null  -> {
            val negated = ctx.rangeTest().inOperator().NOT_IN() != null
            RangeCondition(visitExpr(ctx.rangeTest().expression()), negated)
        }
        ctx.typeTest() != null -> {
            val negated = ctx.typeTest().isOperator().NOT_IS() != null
            TypeCondition(convertType(ctx.typeTest().type()), negated)
        }
        else -> ExpressionCondition(BooleanLiteral(true))
    }

    private fun visitJump(ctx: KotlinParser.JumpExpressionContext): Expression {
        return when {
            ctx.RETURN() != null || ctx.RETURN_AT() != null -> {
                val label = ctx.RETURN_AT()?.text?.removePrefix("return@")
                val value = ctx.expression()?.let { visitExpr(it) }
                // Return is a jump expression in Kotlin — wrap as a statement
                ReturnJumpExpr(value, label)
            }
            ctx.THROW() != null -> ThrowJumpExpr(visitExpr(ctx.expression()))
            ctx.BREAK() != null -> BreakJumpExpr(null)
            ctx.BREAK_AT() != null -> BreakJumpExpr(ctx.BREAK_AT().text.removePrefix("break@"))
            ctx.CONTINUE() != null -> ContinueJumpExpr(null)
            ctx.CONTINUE_AT() != null -> ContinueJumpExpr(ctx.CONTINUE_AT().text.removePrefix("continue@"))
            else -> NullLiteral
        }
    }

    private fun convertCollectionLiteral(ctx: KotlinParser.CollectionLiteralContext): Expression {
        val elems = ctx.expression().map { visitExpr(it) }
        return CallExpression(NameReference("listOf"), emptyList(),
            elems.map { CallArgument(null, it) })
    }

    private fun visitTryExpr(ctx: KotlinParser.TryExpressionContext): Expression {
        // For CP, try-catch is rare; just emit the try body
        val stmts = convertBlock(ctx.block())
        return if (stmts.size == 1 && stmts[0] is ExpressionStatement)
            (stmts[0] as ExpressionStatement).expr
        else NullLiteral
    }

    private fun visitExpr(text: String): Expression = NameReference("/* $text */")

    // ─── Types ───────────────────────────────────────────────────────────────

    private fun convertType(ctx: KotlinParser.TypeContext): KotlinType {
        val nullable = ctx.typeModifiers()?.typeModifier()?.any { it.SUSPEND() != null } == false
        val inner = when {
            ctx.typeReference() != null -> visitTypeRef(ctx.typeReference())
            ctx.nullableType() != null  -> convertNullableType(ctx.nullableType())
            ctx.functionType() != null  -> convertFunctionType(ctx.functionType())
            else -> KotlinType.Auto
        }
        return inner
    }

    private fun visitTypeRef(ctx: KotlinParser.TypeReferenceContext): KotlinType = when {
        ctx.userType() != null      -> convertUserType(ctx.userType())
        ctx.DYNAMIC() != null       -> KotlinType.Dynamic
        else -> KotlinType.Auto
    }

    private fun convertNullableType(ctx: KotlinParser.NullableTypeContext): KotlinType {
        val inner = when {
            ctx.typeReference() != null -> visitTypeRef(ctx.typeReference())
            ctx.parenthesizedType() != null -> convertType(ctx.parenthesizedType().type())
            else -> KotlinType.Auto
        }
        return KotlinType.Nullable(inner)
    }

    private fun convertUserType(ctx: KotlinParser.UserTypeContext): KotlinType {
        val parts = ctx.simpleUserType()
        val lastName = parts.last()
        val name = lastName.simpleIdentifier().text
        val typeArgs = lastName.typeArguments()?.typeProjection()?.mapNotNull {
            if (it.MULT() != null) KotlinType.Auto
            else it.type()?.let { t -> convertType(t) }
        } ?: emptyList()
        return if (typeArgs.isEmpty()) TypeResolver.parseKotlinType(name)
               else KotlinType.Generic(name, typeArgs)
    }

    private fun convertFunctionType(ctx: KotlinParser.FunctionTypeContext): KotlinType {
        val params = ctx.functionTypeParameters()?.let { ftp ->
            val paramTypes = ftp.parameter().map { convertType(it.type()) }
            val typeList = ftp.type().map { convertType(it) }
            paramTypes + typeList
        } ?: emptyList()
        val ret = ctx.type()?.let { convertType(it) } ?: KotlinType.Unit
        return KotlinType.Function(params, ret)
    }

    // ─── Modifier parsing ────────────────────────────────────────────────────

    private fun parseModifiers(ctx: KotlinParser.ModifiersContext?): Set<Modifier> {
        ctx ?: return emptySet()
        val result = mutableSetOf<Modifier>()
        for (mod in ctx.modifier()) {
            when {
                mod.memberModifier()?.OVERRIDE() != null     -> result.add(Modifier.Override)
                mod.inheritanceModifier()?.ABSTRACT() != null -> result.add(Modifier.Abstract)
                mod.inheritanceModifier()?.OPEN() != null    -> result.add(Modifier.Open)
                mod.visibilityModifier()?.PRIVATE() != null  -> result.add(Modifier.Private)
                mod.visibilityModifier()?.PROTECTED() != null -> result.add(Modifier.Protected)
                mod.functionModifier()?.OPERATOR() != null   -> result.add(Modifier.Operator)
                mod.functionModifier()?.INLINE() != null     -> result.add(Modifier.Inline)
            }
        }
        return result
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun foldBinary(parts: List<Expression>, ops: List<BinaryOp>): Expression {
        if (parts.size == 1) return parts[0]
        var result = parts[0]
        for ((i, op) in ops.withIndex()) {
            result = BinaryExpression(result, op, parts[i + 1])
        }
        return result
    }
}

