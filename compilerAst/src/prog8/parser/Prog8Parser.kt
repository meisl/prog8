package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.IStringEncoding
import prog8.ast.Module
import prog8.ast.statements.Statement
import prog8.ast.antlr.toAst
import prog8.ast.base.Position
import kotlin.io.path.*


open class ParsingFailedError(override var message: String) : Exception(message)

class ParseError(override var message: String, val position: Position, cause: RuntimeException)
    : ParsingFailedError("${position.toClickableStr()}$message") {
    init {
        initCause(cause)
    }
}

private fun RecognitionException.getPosition(provenance: String) : Position {
    val offending = this.offendingToken
    val line = offending.line
    val beginCol = offending.charPositionInLine
    val endCol = beginCol + offending.stopIndex - offending.startIndex  // TODO: point to col *after* token?
    val pos = Position(provenance, line, beginCol, endCol)
    return pos
}

object Prog8Parser {

    fun parseModule(src: SourceCode): Module {
        val antlrErrorListener = AntlrErrorListener(src.origin)
        val lexer = Prog8ANTLRLexer(src.getCharStream())
        lexer.removeErrorListeners()
        lexer.addErrorListener(antlrErrorListener)
        val tokens = CommonTokenStream(lexer)
        val parser = Prog8ANTLRParser(tokens)
        parser.errorHandler = Prog8ErrorStrategy
        parser.removeErrorListeners()
        parser.addErrorListener(antlrErrorListener)

        val parseTree = parser.module()

        // FIXME: hacking together a name for the module:
        val moduleName = when {
            src.origin.startsWith("<") -> when {
                src.origin.startsWith("<res:") -> Path(src.origin.substring(5, src.origin.length - 1)).nameWithoutExtension
                else -> src.origin
            }
            else -> Path(src.origin).nameWithoutExtension
        }

        val module = parseTree.toAst(moduleName, false, source = Path(""))
        for (statement in module.statements) {
            statement.linkParents(module)
        }
        // TODO: use Module ctor directly
        // val module = Module(moduleName, statements = listOf<Statement>().toMutableList(), position = null, isLibraryModule = false, source = null)

        return module
    }

    private object Prog8ErrorStrategy: BailErrorStrategy() {
        private fun fillIn(e: RecognitionException?, ctx: ParserRuleContext?) {
            var context = ctx
            while (context != null) {
                context.exception = e
                context = context.getParent()
            }
        }

        override fun reportInputMismatch(recognizer: Parser?, e: InputMismatchException?) {
            super.reportInputMismatch(recognizer, e)
        }

        override fun recover(recognizer: Parser?, e: RecognitionException?) {
            fillIn(e, recognizer!!.context)
            reportError(recognizer, e)
        }

        override fun recoverInline(recognizer: Parser?): Token {
            val e = InputMismatchException(recognizer)
            fillIn(e, recognizer!!.context)
            reportError(recognizer, e)
            throw e
        }
    }

    private class AntlrErrorListener(val sourceCodeProvenance: String): BaseErrorListener() {
        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
            if (e == null) {
                TODO("no RecognitionException - create your own ParseError")
                //throw ParseError()
            } else {
                throw ParseError(msg, e.getPosition(sourceCodeProvenance), e)
            }
        }
    }

}
