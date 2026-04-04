package transpiler.ast

// ─── Top-level ──────────────────────────────────────────────────────────────

data class KotlinFile(
    val declarations: List<TopLevelDeclaration>
)

sealed class TopLevelDeclaration

data class TopLevelFunction(val decl: FunctionDecl) : TopLevelDeclaration()
data class TopLevelClass(val decl: ClassDecl) : TopLevelDeclaration()
data class TopLevelObject(val decl: ObjectDecl) : TopLevelDeclaration()
data class TopLevelProperty(val decl: PropertyDecl) : TopLevelDeclaration()

// ─── Declarations ────────────────────────────────────────────────────────────

data class FunctionDecl(
    val name: String,
    val typeParams: List<String>,            // generic type parameter names
    val params: List<Parameter>,
    val returnType: KotlinType?,             // null → infer / Unit
    val body: FunctionBody?,                 // null → abstract
    val modifiers: Set<Modifier> = emptySet(),
    val label: String? = null
)

sealed class FunctionBody
data class BlockBody(val statements: List<Statement>) : FunctionBody()
data class ExpressionBody(val expr: Expression) : FunctionBody()

data class Parameter(
    val name: String,
    val type: KotlinType?,
    val defaultValue: Expression? = null,
    val isVararg: Boolean = false
)

data class ClassDecl(
    val name: String,
    val kind: ClassKind,
    val typeParams: List<String>,
    val primaryConstructor: List<ConstructorParam>,
    val superTypes: List<SuperTypeEntry>,
    val members: List<ClassMember>
)

enum class ClassKind { Regular, Data, Sealed, Abstract, Interface }

data class ConstructorParam(
    val name: String,
    val type: KotlinType,
    val isVal: Boolean = false,
    val isVar: Boolean = false,
    val defaultValue: Expression? = null
)

data class SuperTypeEntry(
    val type: KotlinType,
    val constructorArgs: List<Expression> = emptyList()   // non-empty if calling super constructor
)

sealed class ClassMember
data class MemberProperty(val decl: PropertyDecl) : ClassMember()
data class MemberFunction(val decl: FunctionDecl) : ClassMember()
data class InitBlock(val body: List<Statement>) : ClassMember()
data class CompanionObject(val members: List<ClassMember>) : ClassMember()
data class SecondaryConstructor(val params: List<Parameter>, val delegationArgs: List<Expression>, val body: List<Statement>) : ClassMember()

data class ObjectDecl(
    val name: String,
    val superTypes: List<SuperTypeEntry>,
    val members: List<ClassMember>
)

data class PropertyDecl(
    val name: String,
    val type: KotlinType?,
    val initializer: Expression?,
    val isMutable: Boolean,                 // var vs val
    val modifiers: Set<Modifier> = emptySet()
)

enum class Modifier {
    Override, Abstract, Open, Private, Protected, Public, Internal,
    Operator, Inline, Suspend, Companion, Static, Final
}

// ─── Statements ──────────────────────────────────────────────────────────────

sealed class Statement

data class ExpressionStatement(val expr: Expression) : Statement()

data class LocalProperty(
    val name: String,
    val type: KotlinType?,
    val initializer: Expression?,
    val isMutable: Boolean
) : Statement()

data class DestructuringDeclaration(
    val names: List<String?>,               // null = underscore wildcard
    val type: KotlinType?,
    val initializer: Expression,
    val isMutable: Boolean
) : Statement()

data class Assignment(
    val target: Expression,
    val value: Expression,
    val op: AssignOp
) : Statement()

enum class AssignOp { Assign, PlusAssign, MinusAssign, TimesAssign, DivAssign, ModAssign }

data class IfStatement(
    val condition: Expression,
    val thenBranch: List<Statement>,
    val elseBranch: List<Statement>?
) : Statement()

data class WhenStatement(
    val subject: WhenSubject?,
    val entries: List<WhenEntry>
) : Statement()

data class ForStatement(
    val variable: ForLoopVariable,
    val iterable: Expression,
    val body: List<Statement>
) : Statement()

data class WhileStatement(
    val condition: Expression,
    val body: List<Statement>,
    val isDoWhile: Boolean = false
) : Statement()

data class ReturnStatement(
    val value: Expression?,
    val label: String? = null
) : Statement()

object BreakStatement : Statement()
data class LabeledBreak(val label: String) : Statement()
object ContinueStatement : Statement()
data class LabeledContinue(val label: String) : Statement()

data class ThrowStatement(val expr: Expression) : Statement()

data class LabeledStatement(val label: String, val stmt: Statement) : Statement()

// Nested function declaration used as a statement (fun inside fun)
data class NestedFunctionStatement(val decl: FunctionDecl) : Statement()

// ─── For-loop variable ───────────────────────────────────────────────────────

sealed class ForLoopVariable
data class SimpleVar(val name: String) : ForLoopVariable()
data class DestructuredVar(val names: List<String?>) : ForLoopVariable()

// ─── When ────────────────────────────────────────────────────────────────────

data class WhenSubject(
    val expr: Expression,
    val binding: String? = null             // "val x = <expr>" subject binding
)

data class WhenEntry(
    val conditions: List<WhenCondition>,    // empty list = else branch
    val body: WhenEntryBody
)

sealed class WhenCondition
data class ExpressionCondition(val expr: Expression) : WhenCondition()
data class RangeCondition(val range: Expression, val negated: Boolean) : WhenCondition()
data class TypeCondition(val type: KotlinType, val negated: Boolean) : WhenCondition()
object ElseCondition : WhenCondition()

sealed class WhenEntryBody
data class BlockEntryBody(val statements: List<Statement>) : WhenEntryBody()
data class ExpressionEntryBody(val expr: Expression) : WhenEntryBody()

// ─── Expressions ─────────────────────────────────────────────────────────────

sealed class Expression

// Literals
data class IntLiteral(val value: Int) : Expression()
data class LongLiteral(val value: Long) : Expression()
data class DoubleLiteral(val value: Double) : Expression()
data class FloatLiteral(val value: Float) : Expression()
data class BooleanLiteral(val value: Boolean) : Expression()
data class CharLiteral(val value: Char) : Expression()
data class StringLiteral(val value: String) : Expression()
object NullLiteral : Expression()

// String with interpolation: "Hello $name, you are ${age+1}"
data class StringTemplate(val parts: List<StringPart>) : Expression()

sealed class StringPart
data class LiteralStringPart(val value: String) : StringPart()
data class ExpressionStringPart(val expr: Expression) : StringPart()

// Variables and members
data class NameReference(val name: String) : Expression()
data class PropertyAccessExpr(val receiver: Expression, val name: String, val isSafe: Boolean = false) : Expression()
data class IndexAccess(val receiver: Expression, val index: Expression, val additionalIndices: List<Expression> = emptyList()) : Expression()
data class ThisExpression(val qualifier: String? = null) : Expression()
data class SuperExpression(val qualifier: String? = null) : Expression()

// Operations
data class BinaryExpression(val left: Expression, val op: BinaryOp, val right: Expression) : Expression()
data class PrefixExpression(val op: PrefixOp, val expr: Expression) : Expression()
data class PostfixExpression(val expr: Expression, val op: PostfixOp) : Expression()
data class TypeCheckExpression(val expr: Expression, val type: KotlinType, val negated: Boolean) : Expression()
data class TypeCastExpression(val expr: Expression, val type: KotlinType, val isSafe: Boolean) : Expression()
data class ElvisExpression(val left: Expression, val right: Expression) : Expression()
data class RangeExpression(val start: Expression, val end: Expression, val kind: RangeKind, val step: Expression? = null) : Expression()

enum class RangeKind { Inclusive, Until, DownTo }

// Calls
data class CallExpression(
    val callee: Expression,
    val typeArgs: List<KotlinType>,
    val args: List<CallArgument>,
    val trailingLambda: LambdaExpression? = null
) : Expression()

data class CallArgument(
    val name: String?,       // named argument
    val value: Expression
)

// Lambda
data class LambdaExpression(
    val params: List<LambdaParam>,
    val body: List<Statement>
) : Expression()

data class LambdaParam(
    val name: String,
    val type: KotlinType? = null
)

// If and when as expressions
data class IfExpression(
    val condition: Expression,
    val thenBranch: IfBranch,
    val elseBranch: IfBranch
) : Expression()

sealed class IfBranch
data class BlockBranch(val statements: List<Statement>) : IfBranch()
data class ExprBranch(val expr: Expression) : IfBranch()

data class WhenExpression(
    val subject: WhenSubject?,
    val entries: List<WhenEntry>
) : Expression()

// Method call:  receiver.method(args) or receiver?.method(args)
data class MethodCallExpression(
    val receiver: Expression,
    val method: String,
    val typeArgs: List<KotlinType>,
    val args: List<CallArgument>,
    val trailingLambda: LambdaExpression? = null,
    val isSafeCall: Boolean = false
) : Expression()

// Object creation
data class ObjectCreation(
    val type: KotlinType,
    val args: List<CallArgument>,
    val trailingLambda: LambdaExpression? = null
) : Expression()

// Anonymous object
data class AnonymousObject(
    val superTypes: List<SuperTypeEntry>,
    val members: List<ClassMember>
) : Expression()

// Jump expressions (return/break/continue/throw can appear as expressions in Kotlin)
data class ReturnJumpExpr(val value: Expression?, val label: String?) : Expression()
data class ThrowJumpExpr(val expr: Expression) : Expression()
data class BreakJumpExpr(val label: String?) : Expression()
data class ContinueJumpExpr(val label: String?) : Expression()

// ─── Operators ───────────────────────────────────────────────────────────────

enum class BinaryOp(val token: String) {
    Plus("+"), Minus("-"), Times("*"), Div("/"), Mod("%"),
    And("&&"), Or("||"),
    Eq("=="), NotEq("!="), RefEq("==="), RefNotEq("!=="),
    Lt("<"), Gt(">"), LtEq("<="), GtEq(">="),
    Elvis("?:"),
    RangeTo(".."), RangeUntil("..<"),
    Shl("shl"), Shr("shr"), Ushr("ushr"), BitAnd("and"), BitOr("or"), BitXor("xor")
}

enum class PrefixOp(val token: String) {
    UnaryMinus("-"), UnaryPlus("+"), Not("!"), PreInc("++"), PreDec("--")
}

enum class PostfixOp(val token: String) {
    PostInc("++"), PostDec("--"), NotNull("!!")
}

// ─── Types ───────────────────────────────────────────────────────────────────

sealed class KotlinType {
    object Unit : KotlinType()
    object Auto : KotlinType()      // type to be inferred; emits `auto`
    object Dynamic : KotlinType()   // Kotlin dynamic type
    data class Simple(val name: String) : KotlinType()
    data class Generic(val name: String, val typeArgs: List<KotlinType>) : KotlinType()
    data class Nullable(val inner: KotlinType) : KotlinType()
    data class Function(val paramTypes: List<KotlinType>, val returnType: KotlinType) : KotlinType()
    data class Array(val elementType: KotlinType) : KotlinType()

    companion object {
        val Int = Simple("Int")
        val Long = Simple("Long")
        val Double = Simple("Double")
        val Float = Simple("Float")
        val Boolean = Simple("Boolean")
        val String = Simple("String")
        val Char = Simple("Char")
        val Any = Simple("Any")
        val Nothing = Simple("Nothing")
    }
}
