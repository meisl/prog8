package prog8.ast.antlr

import prog8.ast.base.Position
import prog8.ast.base.SyntaxError

/**
  * TODO: move utility functions escape(String) and unescape(String) OUT OF prog8.ast.antlr!
  */
fun escape(str: String): String {
    val es = str.map {
        when(it) {
            '\t' -> "\\t"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '"' -> "\\\""
            in '\u8000'..'\u80ff' -> "\\x" + (it.code - 0x8000).toString(16).padStart(2, '0')
            in '\u0000'..'\u00ff' -> it.toString()
            else -> "\\u" + it.code.toString(16).padStart(4, '0')
        }
    }
    return es.joinToString("")
}

/**
 * TODO: move utility functions escape(String) and unescape(String) OUT OF prog8.ast.antlr!
 */
fun unescape(str: String, position: Position): String {
    val result = mutableListOf<Char>()
    val iter = str.iterator()
    while(iter.hasNext()) {
        val c = iter.nextChar()
        if(c=='\\') {
            val ec = iter.nextChar()
            result.add(when(ec) {
                '\\' -> '\\'
                'n' -> '\n'
                'r' -> '\r'
                '"' -> '"'
                '\'' -> '\''
                'u' -> {
                    "${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}".toInt(16).toChar()
                }
                'x' -> {
                    // special hack 0x8000..0x80ff  will be outputted verbatim without encoding
                    val hex = ("" + iter.nextChar() + iter.nextChar()).toInt(16)
                    (0x8000 + hex).toChar()
                }
                else -> throw SyntaxError("invalid escape char in string: \\$ec", position)
            })
        } else {
            result.add(c)
        }
    }
    return result.joinToString("")
}
