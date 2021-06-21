package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import kotlin.io.path.*

import prog8.compiler.target.Cx16Target
import prog8.compiler.compileProgram


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCommonExamples {
    val workingDir = Path("").absolute()    // Note: Path(".") does NOT work..!
    val examplesDir = Path("..", "examples")

    @Test
    fun testCurrentAndExamplesDir() {
        assertEquals("compiler", workingDir.fileName.toString())
        assertTrue(examplesDir.isDirectory(), "sanity check; should be directory: $examplesDir")
    }

    @Test
    fun test_cxLogo() {
        val filepath = examplesDir.resolve("cxlogo.p8")

        val result = compileProgram(
            filepath,
            optimize = false,
            writeAssembly = true,
            slowCodegenWarnings = false,
            compilationTarget = Cx16Target.name,
            libdirs = listOf(),
            outputDir = workingDir
        )

        assertTrue(result.success, "compilation successful")

        //assertFailsWith<NoSuchFileException> { SourceCode.fromResources(full) }
    }
}