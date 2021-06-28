package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import kotlin.io.path.*

import prog8.ast.IFunctionCall
import prog8.ast.base.DataType
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.CharLiteral
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue

import prog8.compiler.target.Cx16Target
import prog8.compiler.compileProgram


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompilerOnCharLit {
    val workingDir = Path("").absolute()    // Note: Path(".") does NOT work..!
    val fixturesDir = workingDir.resolve("test/fixtures")
    val outputDir = workingDir.resolve("build/tmp/test")

    @Test
    fun testDirectoriesSanityCheck() {
        assertEquals("compiler", workingDir.fileName.toString())
        assertTrue(fixturesDir.isDirectory(), "sanity check; should be directory: $fixturesDir")
        assertTrue(outputDir.isDirectory(), "sanity check; should be directory: $outputDir")
    }

    @Test
    fun testCharLitAsRomsubArg() {
        val filepath = fixturesDir.resolve("charLitAsRomsubArg.p8")
        val compilationTarget = Cx16Target
        val result = compileProgram(
            filepath,
            optimize = false,
            writeAssembly = true,
            slowCodegenWarnings = false,
            compilationTarget.name,
            libdirs = listOf(),
            outputDir
        )
        assertTrue(result.success, "compilation successful")

        val program = result.programAst
        val startSub = program.entrypoint()
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        assertIs<NumericLiteralValue>(funCall.args[0],
            "char literal should have been replaced by ubyte literal")
        val arg = funCall.args[0] as NumericLiteralValue
        assertEquals(DataType.UBYTE, arg.type)
        assertEquals(compilationTarget.encodeString("\n", false)[0].toInt(), arg.number)
    }

    @Test
    fun testCharVarAsRomsubArg() {
        val filepath = fixturesDir.resolve("charVarAsRomsubArg.p8")
        val compilationTarget = Cx16Target
        val result = compileProgram(
            filepath,
            optimize = false,
            writeAssembly = true,
            slowCodegenWarnings = false,
            compilationTarget.name,
            libdirs = listOf(),
            outputDir
        )
        assertTrue(result.success, "compilation successful")
        val program = result.programAst
        val startSub = program.entrypoint()
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        assertIs<IdentifierReference>(funCall.args[0])
        val arg = funCall.args[0] as IdentifierReference
        val decl = arg.targetVarDecl(program)!!
        assertEquals(VarDeclType.VAR, decl.type)
        assertEquals(DataType.UBYTE, decl.datatype)

        // TODO: assertIs<CharLiteral>(decl.value,
        //          "char literals should be kept until code gen")
        //       val initializerValue = decl.value as CharLiteral
        //       assertEquals('\n', (initializerValue as CharLiteral).value)

        assertIs<NumericLiteralValue>(decl.value,
            "char literal should have been replaced by ubyte literal")
        val initializerValue = decl.value as NumericLiteralValue
        assertEquals(DataType.UBYTE, initializerValue.type)
        assertEquals(compilationTarget.encodeString("\n", false)[0].toInt(), initializerValue.number)
    }

    @Test
    fun testCharConstAsRomsubArg() {
        val filepath = fixturesDir.resolve("charConstAsRomsubArg.p8")
        val compilationTarget = Cx16Target
        val result = compileProgram(
            filepath,
            optimize = false,
            writeAssembly = true,
            slowCodegenWarnings = false,
            compilationTarget.name,
            libdirs = listOf(),
            outputDir
        )
        assertTrue(result.success, "compilation successful")
        val program = result.programAst
        val startSub = program.entrypoint()
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        assertIs<IdentifierReference>(funCall.args[0])
        val arg = funCall.args[0] as IdentifierReference
        val decl = arg.targetVarDecl(program)!!
        assertEquals(VarDeclType.CONST, decl.type)
        assertEquals(DataType.UBYTE, decl.datatype)
        assertEquals(compilationTarget.encodeString("\n", false)[0].toInt(),
            (decl.value as NumericLiteralValue).number)
    }
}