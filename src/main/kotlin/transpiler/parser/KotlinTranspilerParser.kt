package transpiler.parser

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTree
import transpiler.ast.KotlinFile
import transpiler.parser.generated.KotlinLexer
import transpiler.parser.generated.KotlinParser

/**
 * Thin wrapper around the ANTLR-generated parser.
 * Parses Kotlin source text and returns an internal [KotlinFile] AST.
 */
object KotlinTranspilerParser {

    fun parse(source: String): KotlinFile {
        val charStream = CharStreams.fromString(source)
        val lexer = KotlinLexer(charStream)
        lexer.removeErrorListeners()
        lexer.addErrorListener(ThrowingErrorListener)

        val tokenStream = CommonTokenStream(lexer)
        val parser = KotlinParser(tokenStream)
        parser.removeErrorListeners()
        parser.addErrorListener(ThrowingErrorListener)

        val tree: ParseTree = parser.kotlinFile()
        return AntlrToAst().convertKotlinFile(tree as KotlinParser.KotlinFileContext)
    }
}

private object ThrowingErrorListener : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        throw IllegalArgumentException("Syntax error at $line:$charPositionInLine — $msg")
    }
}
