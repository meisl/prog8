package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.ParentSentinel
import prog8.ast.expressions.FunctionCall
import prog8.ast.expressions.IdentifierReference
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.*
import prog8.compiler.loadAsmIncludeFile

private val alwaysKeepSubroutines = setOf(
        Pair("main", "start"),
        Pair("irq", "irq")
)

private val asmJumpRx = Regex("""[\-+a-zA-Z0-9_ \t]+(jmp|jsr)[ \t]+(\S+).*""", RegexOption.IGNORE_CASE)
private val asmRefRx = Regex("""[\-+a-zA-Z0-9_ \t]+(...)[ \t]+(\S+).*""", RegexOption.IGNORE_CASE)


class CallGraph(private val program: Program) : IAstVisitor {

    val imports = mutableMapOf<Module, List<Module>>().withDefault { mutableListOf() }
    val importedBy = mutableMapOf<Module, List<Module>>().withDefault { mutableListOf() }
    val calls = mutableMapOf<INameScope, List<Subroutine>>().withDefault { mutableListOf() }
    val calledBy = mutableMapOf<Subroutine, List<Node>>().withDefault { mutableListOf() }

    // TODO  add dataflow graph: what statements use what variables - can be used to eliminate unused vars
    val usedSymbols = mutableSetOf<Statement>()

    init {
        visit(program)
    }

    fun forAllSubroutines(scope: INameScope, sub: (s: Subroutine) -> Unit) {
        fun findSubs(scope: INameScope) {
            scope.statements.forEach {
                if (it is Subroutine)
                    sub(it)
                if (it is INameScope)
                    findSubs(it)
            }
        }
        findSubs(scope)
    }

    override fun visit(program: Program) {
        super.visit(program)

        program.modules.forEach {
            it.importedBy.clear()
            it.imports.clear()

            it.importedBy.addAll(importedBy.getValue(it))
            it.imports.addAll(imports.getValue(it))
        }

        val rootmodule = program.modules.first()
        rootmodule.importedBy.add(rootmodule)       // don't discard root module
    }

    override fun visit(block: Block) {
        if (block.definingModule().isLibraryModule) {
            // make sure the block is not removed
            addNodeAndParentScopes(block)
        }

        super.visit(block)
    }

    override fun visit(directive: Directive) {
        val thisModule = directive.definingModule()
        if (directive.directive == "%import") {
            val importedModule: Module = program.modules.single { it.name == directive.args[0].name }
            imports[thisModule] = imports.getValue(thisModule).plus(importedModule)
            importedBy[importedModule] = importedBy.getValue(importedModule).plus(thisModule)
        } else if (directive.directive == "%asminclude") {
            val asm = loadAsmIncludeFile(directive.args[0].str!!, thisModule.source)
            val scope = directive.definingScope()
            scanAssemblyCode(asm, directive, scope)
        }

        super.visit(directive)
    }

    override fun visit(identifier: IdentifierReference) {
        // track symbol usage
        val target = identifier.targetStatement(this.program.namespace)
        if (target != null) {
            addNodeAndParentScopes(target)
        }
        super.visit(identifier)
    }

    private fun addNodeAndParentScopes(stmt: Statement) {
        usedSymbols.add(stmt)
        var node: Node = stmt
        do {
            if (node is INameScope && node is Statement) {
                usedSymbols.add(node)
            }
            node = node.parent
        } while (node !is Module && node !is ParentSentinel)
    }

    override fun visit(subroutine: Subroutine) {
        if (Pair(subroutine.definingScope().name, subroutine.name) in alwaysKeepSubroutines
                || subroutine.definingModule().isLibraryModule) {
            // make sure the entrypoint is mentioned in the used symbols
            addNodeAndParentScopes(subroutine)
        }
        super.visit(subroutine)
    }

    override fun visit(decl: VarDecl) {
        if (decl.autogeneratedDontRemove || decl.definingModule().isLibraryModule) {
            // make sure autogenerated vardecls are in the used symbols and are never removed as 'unused'
            addNodeAndParentScopes(decl)
        }

        if (decl.datatype == DataType.STRUCT)
            addNodeAndParentScopes(decl)

        super.visit(decl)
    }

    override fun visit(functionCall: FunctionCall) {
        val otherSub = functionCall.target.targetSubroutine(program.namespace)
        if (otherSub != null) {
            functionCall.definingSubroutine()?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub).plus(otherSub)
                calledBy[otherSub] = calledBy.getValue(otherSub).plus(functionCall)
            }
        }
        super.visit(functionCall)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val otherSub = functionCallStatement.target.targetSubroutine(program.namespace)
        if (otherSub != null) {
            functionCallStatement.definingSubroutine()?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub).plus(otherSub)
                calledBy[otherSub] = calledBy.getValue(otherSub).plus(functionCallStatement)
            }
        }
        super.visit(functionCallStatement)
    }

    override fun visit(jump: Jump) {
        val otherSub = jump.identifier?.targetSubroutine(program.namespace)
        if (otherSub != null) {
            jump.definingSubroutine()?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub).plus(otherSub)
                calledBy[otherSub] = calledBy.getValue(otherSub).plus(jump)
            }
        }
        super.visit(jump)
    }

    override fun visit(structDecl: StructDecl) {
        usedSymbols.add(structDecl)
        usedSymbols.addAll(structDecl.statements)
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        // parse inline asm for subroutine calls (jmp, jsr)
        val scope = inlineAssembly.definingScope()
        scanAssemblyCode(inlineAssembly.assembly, inlineAssembly, scope)
        super.visit(inlineAssembly)
    }

    private fun scanAssemblyCode(asm: String, context: Statement, scope: INameScope) {
        asm.lines().forEach { line ->
            val matches = asmJumpRx.matchEntire(line)
            if (matches != null) {
                val jumptarget = matches.groups[2]?.value
                if (jumptarget != null && (jumptarget[0].isLetter() || jumptarget[0] == '_')) {
                    val node = program.namespace.lookup(jumptarget.split('.'), context)
                    if (node is Subroutine) {
                        calls[scope] = calls.getValue(scope).plus(node)
                        calledBy[node] = calledBy.getValue(node).plus(context)
                    } else if (jumptarget.contains('.')) {
                        // maybe only the first part already refers to a subroutine
                        val node2 = program.namespace.lookup(listOf(jumptarget.substringBefore('.')), context)
                        if (node2 is Subroutine) {
                            calls[scope] = calls.getValue(scope).plus(node2)
                            calledBy[node2] = calledBy.getValue(node2).plus(context)
                        }
                    }
                }
            } else {
                val matches2 = asmRefRx.matchEntire(line)
                if (matches2 != null) {
                    val target = matches2.groups[2]?.value
                    if (target != null && (target[0].isLetter() || target[0] == '_')) {
                        if (target.contains('.')) {
                            val node = program.namespace.lookup(listOf(target.substringBefore('.')), context)
                            if (node is Subroutine) {
                                calls[scope] = calls.getValue(scope).plus(node)
                                calledBy[node] = calledBy.getValue(node).plus(context)
                            }
                        }
                    }
                }
            }
        }
    }
}
