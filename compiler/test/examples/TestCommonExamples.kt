package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import kotlin.io.path.*

import prog8.compiler.target.Cx16Target
import prog8.compiler.compileProgram
import prog8.compiler.target.ICompilationTarget


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCommonExamples {
    val workingDir = Path("").absolute()    // Note: Path(".") does NOT work..!
    val examplesDir = workingDir.resolve("../examples")
    val outputDir = workingDir.resolve("build/tmp/test")

    @Test
    fun testDirectoriesSanityCheck() {
        assertEquals("compiler", workingDir.fileName.toString())
        assertTrue(examplesDir.isDirectory(), "sanity check; should be directory: $examplesDir")
        assertTrue(outputDir.isDirectory(), "sanity check; should be directory: $outputDir")
    }


    fun testExample(nameWithoutExt: String, platform: ICompilationTarget, optimize: Boolean) {
        val filepath = examplesDir.resolve("$nameWithoutExt.p8")
        val result = compileProgram(
            filepath,
            optimize,
            writeAssembly = true,
            slowCodegenWarnings = false,
            compilationTarget = platform.name,
            libdirs = listOf(),
            outputDir
        )
        assertTrue(result.success, "${platform.name}, optimize=$optimize: \"$filepath\"")
    }


    @Test
    fun test_cxLogo_noopt() {
        testExample("cxlogo", Cx16Target, false)
    }
    @Test
    fun test_cxLogo_opt() {
        TODO("make assembly stage testable - currently assembling this with 64tass fails and Process.exit s")
        testExample("cxlogo", Cx16Target, true)
    }

    @Test
    fun test_swirl_noopt() {
        testExample("swirl", Cx16Target, false)
    }
    @Test
    fun test_swirl_opt() {
        TODO("make assembly stage testable - currently assembling this with 64tass fails and Process.exit s")
        testExample("swirl", Cx16Target, true)
    }

    @Test
    fun test_animals_noopt() {
        testExample("animals", Cx16Target, false)
    }
    @Test
    fun test_animals_opt() {
        TODO("make assembly stage testable - currently assembling this with 64tass fails and Process.exit s")
        testExample("animals", Cx16Target, true)
    }

}