package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*
import kotlin.io.path.*

import prog8.ast.base.DataType
import prog8.ast.base.RegisterOrPair
import prog8.ast.base.Statusflag
import prog8.ast.expressions.BinaryExpression
import prog8.ast.statements.*

import prog8.parser.ParseError
import prog8.parser.Prog8Parser
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode


class TestProg8Parser {

    @Test
    fun testModuleSourceNeedNotEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val src = SourceCode.of("foo {" + nl + "}")   // source ends with '}' (= NO newline, issue #40)

        // #45: Prog8ANTLRParser would report (throw) "missing <EOL> at '<EOF>'"
        val module = parseModule(src)
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testModuleSourceMayEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val srcText = "foo {" + nl + "}" + nl  // source does end with a newline (issue #40)
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testAllBlocksButLastMustEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)

        // BAD: 2nd block `bar` does NOT start on new line; however, there's is a nl at the very end
        val srcBad = "foo {" + nl + "}" + " bar {" + nl + "}" + nl

        // GOOD: 2nd block `bar` does start on a new line; however, a nl at the very end ain't needed
        val srcGood = "foo {" + nl + "}" + nl + "bar {" + nl + "}"

        assertFailsWith<ParseError> { parseModule(SourceCode.of(srcBad)) }
        val module = parseModule(SourceCode.of(srcGood))
        assertEquals(2, module.statements.size)
    }

    @Test
    fun testWindowsAndMacNewlinesAreAlsoFine() {
        val nlWin = "\r\n"
        val nlUnix = "\n"
        val nlMac = "\r"

        // a good mix of all kinds of newlines:
        val srcText =
            "foo {" +
            nlMac +
            nlWin +
            "}" +
            nlMac +     // <-- do test a single \r (!) where an EOL is expected
            "bar {" +
            nlUnix +
            "}" +
            nlUnix + nlMac   // both should be "eaten up" by just one EOL token
            "combi {" +
            nlMac + nlWin + nlUnix   // all three should be "eaten up" by just one EOL token
            "}" +
            nlUnix      // end with newline (see testModuleSourceNeedNotEndWithNewline)

        val module = parseModule(SourceCode.of(srcText))
        assertEquals(2, module.statements.size)
    }

    @Test
    fun testInterleavedEolAndCommentBeforeFirstBlock() {
        // issue: #47
        val srcText = """
            ; comment
            
            ; comment
            
            blockA {            
            }
"""
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testInterleavedEolAndCommentBetweenBlocks() {
        // issue: #47
        val srcText = """
            blockA {
            }
            ; comment
            
            ; comment
            
            blockB {            
            }
"""
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(2, module.statements.size)
    }

    @Test
    fun testInterleavedEolAndCommentAfterLastBlock() {
        // issue: #47
        val srcText = """
            blockA {            
            }
            ; comment
            
            ; comment
            
"""
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testNewlineBetweenTwoBlocksOrDirectivesStillRequired() {
        // issue: #47

        // block and block
        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            blockA {
            } blockB {            
            }            
        """)) }

        // block and directive
        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            blockB {            
            } %import textio            
        """)) }

        // The following two are bogus due to directive *args* expected to follow the directive name.
        // Leaving them in anyways.

        // dir and block
        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            %import textio blockB {            
            }            
        """)) }

        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            %import textio %import syslib            
        """)) }
    }

    @Test
    fun parseModuleShouldNotLookAtImports() {
        val imported = "i_do_not_exist"
        val pathNoExt = Path(imported).absolute()
        val pathWithExt = Path("${pathNoExt}.p8")
        val text = "%import $imported"

        assertFalse(pathNoExt.exists(), "sanity check: file should not exist: $pathNoExt")
        assertFalse(pathWithExt.exists(), "sanity check: file should not exist: $pathWithExt")

        val module = parseModule(SourceCode.of(text))
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testErrorLocationForSourceFromString() {
        val srcText = "bad * { }\n"

        assertFailsWith<ParseError> { parseModule(SourceCode.of(srcText)) }
        try {
            parseModule(SourceCode.of(srcText))
        } catch (e: ParseError) {
            // Note: assertContains expects *actual* value first
            assertContains(e.position.file, Regex("^<String@[0-9a-f]+>$"))
            assertEquals(1, e.position.line, "line; should be 1-based")
            assertEquals(4, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(4, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testErrorLocationForSourceFromPath() {
        val filename = "file_with_syntax_error.p8"
        val path = Path("test", "fixtures", filename)

        assertFailsWith<ParseError> { parseModule(SourceCode.fromPath(path)) }
        try {
            parseModule(SourceCode.fromPath(path))
        } catch (e: ParseError) {
            assertEquals(path.absolutePathString(), e.position.file, "provenance; should be the path's filename, incl. extension '.p8'")
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }


    @Test
    fun testParentNodesInitialized() {
        val src = SourceCode.of(
            """
            %zeropage basicsafe
            main {
                ubyte x = 23
                sub start() -> ubyte {
                    return x * x
                }
            }
        """.trimIndent()
        )
        val module = parseModule(src)
        val directive = module.statements.filterIsInstance<Directive>()[0]
        val directiveArg = directive.args[0]
        val mainBlock = module.statements.filterIsInstance<Block>()[0]
        val declX = mainBlock.statements.filterIsInstance<VarDecl>()[0]
        val declXrhs = declX.value
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
        val returnStmt = startSub.statements.filterIsInstance<Return>()[0]
        val retValue = returnStmt.value as BinaryExpression
        val left = retValue.left
        val right = retValue.right

        assertThrows<UninitializedPropertyAccessException> {
            assertNull(
                module.parent,
                "most top-level nodes from parser are Modules - with not-yet-initialzed parent"
            )
        }
        assertSame(expected = module, directive.parent)
        assertSame(expected = directive, directiveArg.parent)
        assertSame(expected = module, mainBlock.parent)
        assertSame(expected = mainBlock, declX.parent)
        assertSame(expected = declX, declXrhs!!.parent)
        assertSame(expected = mainBlock, startSub.parent)
        assertSame(expected = startSub, returnStmt.parent)
        assertSame(expected = returnStmt, retValue.parent)
        assertSame(expected = retValue, left.parent)
        assertSame(expected = retValue, right.parent)
    }

    @Test
    fun testVarDeclWithCharLiteral() {
        val src = SourceCode.of("""
            main {
                ubyte c = 'x'   ; TODO: introduce type char, do not necessitate an implicit (!) encoding just yet (=fixed at *parsing*)
            }
        """.trimIndent())
        val module = parseModule(src)

        assertEquals(1, module.statements.size, "nr of stmts in module")
        val block = module.statements.first() as Block // TODO: we really should NOT have to cast to Block..!
        assertEquals(1, block.statements.size, "nr of stmts in block")
        val decl = block.statements.first() as VarDecl
        assertEquals(block, decl.parent, "parent of decl")
    }


    @Test
    fun testAsmSub() {
        val src = SourceCode.of("""
            main {
                asmsub foo() {
                }
                asmsub bar(ubyte isIt @Pz) clobbers(A) -> ubyte @ Pc {
                }
                inline asmsub qmbl(byte one @A, ; let's just split it up over two lines
                    uword two @XY) -> uword @XY {
                    %asm {{
                        txy
                        tax
                    }}
                }
            }
        """.trimIndent())
        val module = parseModule(src)
        val blocks = module.statements.filterIsInstance<Block>()
        assertEquals(1, blocks.size, "nr of blocks in module")
        val subs = blocks[0].statements.filterIsInstance<Subroutine>()
        assertEquals(3, subs.size, "nr of subs in block")

        with (subs[0]) {
            assertTrue(isAsmSubroutine)
            assertFalse(inline)
            assertEquals("foo", name, "name of sub")
            assertEquals("main.foo", scopedname, "scopedname of sub $name")
            assertEquals(0, parameters.size, "nr of params of sub $name")
            assertEquals(0, returntypes.size, "nr of returntypes of sub $name")
            assertEquals(0, asmReturnvaluesRegisters.size, "nr of returnregs of sub $name")
            assertEquals(0, statements.size, "nr of stmts in sub $name")
        }
        with (subs[1]) {
            assertTrue(isAsmSubroutine)
            assertFalse(inline)
            assertEquals("bar", name, "name of sub")
            assertEquals("main.bar", scopedname, "scopedname of sub $name")
            assertEquals(1, parameters.size, "nr of params of sub $name")
            assertEquals("isIt", parameters[0].name, "name of 1st param of sub $name")
            assertEquals(DataType.UBYTE, parameters[0].type, "type of 1st param of sub $name")
            assertEquals(Statusflag.Pz, asmParameterRegisters[0].statusflag, "flag/reg of 1st param of sub $name")
            assertEquals(1, returntypes.size, "nr of returntypes of sub $name")
            assertEquals(DataType.UBYTE, returntypes[0], "type of 1st return value")
            assertEquals(1, asmReturnvaluesRegisters.size, "nr of returnregs of sub $name")
            assertEquals(Statusflag.Pc, asmReturnvaluesRegisters[0].statusflag, "reg/flag of 1st return value")
            assertEquals(0, statements.size, "nr of stmts in sub $name")
        }
        with (subs[2]) {
            assertTrue(isAsmSubroutine)
            assertTrue(inline)
            assertEquals("qmbl", name, "name of sub")
            assertEquals("main.qmbl", scopedname, "scopedname of sub $name")
            assertEquals(2, parameters.size, "nr of params of sub $name")
            assertEquals("one", parameters[0].name, "name of 1st param of sub $name")
            assertEquals(DataType.BYTE, parameters[0].type, "type of 1st param of sub $name")
            assertEquals(RegisterOrPair.A, asmParameterRegisters[0].registerOrPair, "flag/reg of 1st param of sub $name")
            assertEquals("two", parameters[1].name, "name of 2nd param of sub $name")
            assertEquals(DataType.UWORD, parameters[1].type, "type of 2nd param of sub $name")
            assertEquals(RegisterOrPair.XY, asmParameterRegisters[1].registerOrPair, "flag/reg of 2nd param of sub $name")
            assertEquals(1, returntypes.size, "nr of returntypes of sub $name")
            assertEquals(DataType.UWORD, returntypes[0], "type of 1st return value")
            assertEquals(1, asmReturnvaluesRegisters.size, "nr of returnregs of sub $name")
            assertEquals(RegisterOrPair.XY, asmReturnvaluesRegisters[0].registerOrPair, "reg/flag of 1st return value")
            assertEquals(1, statements.size, "nr of stmts in sub $name")
        }
    }

    @Test
    fun testExample_Swirl() {
        val src = SourceCode.of("""
            %import textio
            %zeropage basicsafe
    
            ; Note: this program is compatible with C64 and CX16.
    
            main {
                const uword SCREEN_W = txt.DEFAULT_WIDTH
                const uword SCREEN_H = txt.DEFAULT_HEIGHT
                uword anglex
                        uword angley
                        ubyte ball_color
                        const ubyte ball_char = 81
    
                sub start() {
                    repeat {
                        ubyte x = msb(sin8u(msb(anglex)) * SCREEN_W)
                        ubyte y = msb(cos8u(msb(angley)) * SCREEN_H)
                        txt.setcc(x, y, ball_char, ball_color)
                        anglex += 366
                        angley += 291
                        ball_color++
                    }
                }
            }
            """.trimIndent()
        )
        val module = parseModule(src)

        assertEquals(3, module.statements.size)
        val blocks = module.statements.filterIsInstance<Block>()
        assertEquals(1, blocks.size, "nr of blocks in module")
        assertEquals(7, blocks[0].statements.size, "nr of stmts in main block")
        val subs = blocks[0].statements.filterIsInstance<Subroutine>()
        assertEquals(1, subs.size, "nr of subs in main block")
        assertEquals(1, subs[0].statements.size, "nr of stmts in sub")
        assertIs<RepeatLoop>(subs[0].statements[0], "1st stmt in sub")
    }

}
