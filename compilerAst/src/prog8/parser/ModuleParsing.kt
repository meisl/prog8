package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.IStringEncoding
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.base.SyntaxError
import prog8.ast.statements.Directive
import prog8.ast.statements.DirectiveArg
import java.io.File
import kotlin.io.FileSystemException
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Path   // TODO: use kotlin.io.paths.Path instead
import java.nio.file.Paths  // TODO: use kotlin.io.paths.Path instead


fun moduleName(fileName: Path) = fileName.toString().substringBeforeLast('.')

internal fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


class ModuleImporter(private val program: Program,
                     private val encoder: IStringEncoding,
                     private val compilationTargetName: String,
                     private val libdirs: List<String>) {

    fun importModule(filePath: Path): Module {
        print("importing '${moduleName(filePath.fileName)}'")
        if (filePath.parent != null) { // TODO: use Path.relativize
            var importloc = filePath.toString()
            val curdir = Paths.get("").toAbsolutePath().toString()
            if(importloc.startsWith(curdir))
                importloc = "." + importloc.substring(curdir.length)
            println(" (from '$importloc')")
        }
        else
            println("")

        val module = Prog8Parser.parseModule(SourceCode.fromPath(filePath))
        //moduleAst.isLibraryModule = isLibrary
        //moduleAst.source = modulePath

        module.program = program
        module.linkParents(program.namespace)
        program.modules.add(module)

        // accept additional imports
        val lines = module.statements.toMutableList()
        lines.asSequence()
            .mapIndexed { i, it -> i to it }
            .filter { (it.second as? Directive)?.directive == "%import" }
            .forEach { executeImportDirective(it.second as Directive, filePath) }

        module.statements = lines
        return module
    }

    fun importLibraryModule(name: String): Module? {
        val import = Directive("%import", listOf(
                DirectiveArg("", name, 42, position = Position("<<<implicit-import>>>", 0, 0, 0))
        ), Position("<<<implicit-import>>>", 0, 0, 0))
        return executeImportDirective(import, Paths.get(""))
    }

    private fun importModule(stream: CharStream, modulePath: Path, isLibrary: Boolean): Module {
        val parser = Prog8Parser
        val sourceText = stream.toString()
        val moduleAst = parser.parseModule(SourceCode.of(sourceText))

        //moduleAst.isLibraryModule = isLibrary
        //moduleAst.source = modulePath

        moduleAst.program = program
        moduleAst.linkParents(program.namespace)
        program.modules.add(moduleAst)

        // accept additional imports
        val lines = moduleAst.statements.toMutableList()
        lines.asSequence()
                .mapIndexed { i, it -> i to it }
                .filter { (it.second as? Directive)?.directive == "%import" }
                .forEach { executeImportDirective(it.second as Directive, modulePath) }

        moduleAst.statements = lines
        return moduleAst
    }

    private fun executeImportDirective(import: Directive, importingModule: Path): Module? {
        if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
            throw SyntaxError("invalid import directive", import.position)
        val moduleName = import.args[0].name!!
        if("$moduleName.p8" == import.position.file)
            throw SyntaxError("cannot import self", import.position)

        val existing = program.modules.singleOrNull { it.name == moduleName }
        if(existing!=null)
            return null

        var srcCode = tryGetModuleFromResource("$moduleName.p8", compilationTargetName)
        val importedModule =
            if (srcCode != null) {
                println("importing '$moduleName' (library): ${srcCode.origin}")
                val path = Path.of(URL(srcCode.origin).file) // FIXME: doesn't work with our made-up protocoll "res:"
                importModule(srcCode.getCharStream(), path, true)
            } else {
                srcCode = tryGetModuleFromFile(moduleName, importingModule)
                if (srcCode == null)
                    throw NoSuchFileException(File("$moduleName.p8"))
                importModule(srcCode.getCharStream(), kotlin.io.path.Path(srcCode.origin), false)
            }

        removeDirectivesFromImportedModule(importedModule)
        return importedModule
    }

    private fun removeDirectivesFromImportedModule(importedModule: Module) {
        // Most global directives don't apply for imported modules, so remove them
        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%target")
        var directives = importedModule.statements.filterIsInstance<Directive>()
        importedModule.statements.removeAll(directives)
        directives = directives.filter{ it.directive !in moduleLevelDirectives }
        importedModule.statements.addAll(0, directives)
    }

    private fun tryGetModuleFromResource(name: String, compilationTargetName: String): SourceCode? {
        // try target speficic first
        try {
            return SourceCode.fromResources("/prog8lib/$compilationTargetName/$name")
        } catch (e: FileSystemException) {
        }
        try {
            return SourceCode.fromResources("/prog8lib/$name")
        } catch (e: FileSystemException) {
        }
        return null
    }

    private fun tryGetModuleFromFile(name: String, importingModule: Path): SourceCode? {
        val fileName = "$name.p8"
        val libpaths = libdirs.map { Path.of(it) }
        val locations =
            if (importingModule.toString().isEmpty()) { // <=> imported from library module
                libpaths
            } else {
                libpaths.drop(1) +  // TODO: why drop the first?!
                listOf(importingModule.parent ?: Path.of(".")) +
                listOf( // also look in ./prog8lib
                    Paths.get(
                        Paths.get("").toAbsolutePath().toString(),
                        "prog8lib"
                    )
                )
            }

        locations.forEach {
            try {
                return SourceCode.fromPath(it.resolve(fileName))
            } catch (e: NoSuchFileException) {
            }
        }

        //throw ParsingFailedError("$position Import: no module source file '$fileName' found  (I've looked in: embedded libs and $locations)")
        return null
    }
}
