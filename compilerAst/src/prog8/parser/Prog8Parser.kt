package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.IStringEncoding
import prog8.ast.Module
import prog8.ast.antlr.toAst
import prog8.ast.base.Position
import java.nio.file.Path


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

    fun parseModule(srcPath: Path): Module {
        return parseModule(CharStreams.fromPath(srcPath), srcPath.fileName.toString())
    }

    fun parseModule(srcText: String): Module {
        return parseModule(CharStreams.fromString(srcText), "<String@${System.identityHashCode(srcText).toString(16)}>")
    }

    private fun parseModule(chars: CharStream, provenance: String): Module {
        val antlrErrorListener = AntlrErrorListener(provenance)
        val lexer = Prog8ANTLRLexer(chars)
        lexer.removeErrorListeners()
        lexer.addErrorListener(antlrErrorListener)
        val tokens = CommonTokenStream(lexer)
        val parser = Prog8ANTLRParser(tokens)
        parser.errorHandler = Prog8ErrorStrategy
        parser.removeErrorListeners()
        parser.addErrorListener(antlrErrorListener)

        val parseTree = parser.module()

        // TODO: use Module ctor directly
        val moduleAst = parseTree.toAst("anonymous", false, pathFrom(""), DummyEncoding)

        return moduleAst
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

    private object DummyEncoding: IStringEncoding {
        override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
            TODO("move StringEncoding out of compilerAst")
        }

        override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
            TODO("move StringEncoding out of compilerAst")
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
