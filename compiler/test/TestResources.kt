package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import java.nio.file.Path   // TODO: use kotlin.io.path.Path instead

import prog8.parser.SourceCode


/**
 * TODO: factor out access to resources (`object{}.javaClass.getResource`)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestResources {

    @Test
    fun shouldMirrorProductionResources() {
        // TODO: maybe use ICompilationTarget for platform specific libs?

        val url = object{}.javaClass.getResource("/prog8lib/math.p8")
        assertNotNull(url, "resource url")
    }

    /**
     * TODO: forced to test [SourceCode.fromResources] from here as long as prog8libs resources are port of module compiler
     */
    @Test
    fun testFromResourcesWithExistingGeneralLib() {
        val filename = "math.p8"
        val full = "/prog8lib/$filename"
        val src = SourceCode.fromResources(full)

        // FIXME: hack to get at compiler/res/..
        val expectedText = Path.of("../compiler/res/prog8lib/$filename").toFile().readText();
        val actualText = src.asString()

        assertEquals("<res:$full>", src.origin, )
        assertEquals(expectedText, actualText)
    }

    /**
     * TODO: forced to test [SourceCode.fromResources] from here as long as prog8libs resources are port of module compiler
     */
    @Test
    fun testFromResourcesWithNonExistingGeneralLib() {
        val filename = "i_do_not_exist"
        val full = "/prog8lib/$filename"

        assertFailsWith<NoSuchFileException> { SourceCode.fromResources(full) }
    }
}