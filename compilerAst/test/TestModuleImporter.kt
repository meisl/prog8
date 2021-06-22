package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import kotlin.io.path.*

import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.parser.ModuleImporter
import prog8.parser.ParseError


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestModuleImporter {

    object DummyFunctions: IBuiltinFunctions {
        override val names: Set<String> = emptySet()
        override val purefunctionNames: Set<String> = emptySet()
        override fun constValue(name: String, args: List<Expression>, position: Position, memsizer: IMemSizer): NumericLiteralValue? = null
        override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
    }

    object DummyMemsizer: IMemSizer {
        override fun memorySize(dt: DataType): Int = 0
    }


    @Test
    fun testImportModuleWithNonExistingPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, "blah", listOf("./test/fixtures"))

        val srcPath = Path("test", "fixtures", "i_do_not_exist")

        assertFalse(srcPath.exists(), "sanity check: file should not exist")
        assertFailsWith<NoSuchFileException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportModuleWithDirectoryPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, "blah", listOf("./test/fixtures"))

        val srcPath = Path("test", "fixtures")

        assertTrue(srcPath.isDirectory(), "sanity check: should be a directory")

        // fn importModule(Path) used to check *.isReadable()*, but NOT .isRegularFile():
        assertTrue(srcPath.isReadable(), "sanity check: should still be readable")

        assertFailsWith<AccessDeniedException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, "blah", listOf("./test/fixtures"))

        val filename = "file_with_syntax_error.p8"
        val path = Path("test", "fixtures", filename)
        val act = { importer.importModule(path) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            assertEquals(path.absolutePathString(), e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testImportModuleWithImportingModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, "blah", listOf("./test/fixtures"))

        val importing = Path("test", "fixtures", "import_file_with_syntax_error.p8")
        val imported = Path("test", "fixtures", "file_with_syntax_error.p8")

        val act = { importer.importModule(importing) }

        assertTrue(importing.isRegularFile(), "sanity check: should be regular file")
        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            val expectedProvenance = imported.absolutePathString()
            assertEquals(expectedProvenance, e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testImportLibraryModuleWithNonExistingName() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, "blah", listOf("./test/fixtures"))
        val filenameNoExt = "i_do_not_exist"
        val filenameWithExt = filenameNoExt + ".p8"
        val srcPathNoExt = Path("test", "fixtures", filenameNoExt)
        val srcPathWithExt = Path("test", "fixtures", filenameWithExt)

        assertFalse(srcPathNoExt.exists(), "sanity check: file should not exist")
        assertFalse(srcPathWithExt.exists(), "sanity check: file should not exist")
        assertFailsWith<NoSuchFileException> { importer.importLibraryModule(filenameNoExt) }
        assertFailsWith<NoSuchFileException> { importer.importLibraryModule(filenameWithExt) }
    }

    @Test
    fun testImportLibraryModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, "blah", listOf("./test/fixtures"))

        val filename = "file_with_syntax_error"

        val act = { importer.importLibraryModule(filename) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            val expectedProvenance = Path("test", "fixtures", filename + ".p8")
                .absolutePathString()
            assertEquals(expectedProvenance, e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based")
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testImportLibraryModuleWithImportingBadModule() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val libdirs = listOf("./test/fixtures")
        val importer = ModuleImporter(program, "blah", libdirs)

        val importing = "import_file_with_syntax_error"
        val imported = "file_with_syntax_error"
        val act = { importer.importLibraryModule(importing) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            val expectedProvenance = Path(libdirs[0], imported + ".p8")
                .normalize()
                .absolutePathString()
            assertEquals(expectedProvenance, e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based")
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

}
