package il65.ast

import il65.ParsingFailedError

/**
 * Checks that are specific for imported modules.
 */

fun Module.checkImportedValid() {
    val checker = ImportedAstChecker()
    this.process(checker)
    val result = checker.result()
    result.forEach {
        it.printError()
    }
    if(result.isNotEmpty())
        throw ParsingFailedError("There are ${result.size} errors in imported module '$name'.")
}


class ImportedAstChecker : IAstProcessor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()

    fun result(): List<SyntaxError> {
        return checkResult
    }

    /**
     * Module check: most global directives don't apply for imported modules
     */
    override fun process(module: Module) {
        super.process(module)
        val newStatements : MutableList<IStatement> = mutableListOf()
        module.statements.forEach {
            val stmt = it.process(this)
            if(stmt is Directive) {
                if(stmt.parent is Module) {
                    when(stmt.directive) {
                        "%output", "%launcher", "%zp", "%address" ->
                            println("${stmt.position} Warning: ignoring module directive because it was imported: ${stmt.directive}")
                        else ->
                            newStatements.add(stmt)
                    }
                }
            }
            else newStatements.add(stmt)
        }
        module.statements = newStatements
    }
}
