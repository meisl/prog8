package prog8.compiler.target.c64.codegen2

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.base.initvarsSubName
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.*
import prog8.compiler.target.c64.AssemblyProgram
import prog8.compiler.target.c64.MachineDefinition
import prog8.compiler.target.c64.codegen.optimizeAssembly
import java.io.File
import java.util.*


internal class AssemblyError(msg: String) : RuntimeException(msg)


internal class AsmGen2(val program: Program,
                       val options: CompilationOptions,
                       val zeropage: Zeropage) {

    private val assemblyLines = mutableListOf<String>()
    private val globalFloatConsts = mutableMapOf<Double, String>()     // all float values in the entire program (value -> varname)
    private val allocatedZeropageVariables = mutableMapOf<String, Pair<Int, DataType>>()
    private val breakpointLabels = mutableListOf<String>()

    internal fun compileToAssembly(optimize: Boolean): AssemblyProgram {
        assemblyLines.clear()
        println("Generating assembly code... ")

        header()
        val allBlocks = program.allBlocks()
        if(allBlocks.first().name != "main")
            throw AssemblyError("first block should be 'main'")
        for(b in program.allBlocks())
            block2asm(b)
        footer()

        if(optimize) {
            var optimizationsDone = 1
            while (optimizationsDone > 0) {
                optimizationsDone = optimizeAssembly(assemblyLines)
            }
        }

        File("${program.name}.asm").printWriter().use {
            for (line in assemblyLines) { it.println(line) }
        }

        return AssemblyProgram(program.name)
    }

    private fun header() {
        val ourName = this.javaClass.name
        out("; 6502 assembly code for '${program.name}'")
        out("; generated by $ourName on ${Date()}")
        out("; assembler syntax is for the 64tasm cross-assembler")
        out("; output options: output=${options.output} launcher=${options.launcher} zp=${options.zeropage}")
        out("\n.cpu  '6502'\n.enc  'none'\n")

        program.actualLoadAddress = program.definedLoadAddress
        if (program.actualLoadAddress == 0)   // fix load address
            program.actualLoadAddress = if (options.launcher == LauncherType.BASIC)
                MachineDefinition.BASIC_LOAD_ADDRESS else MachineDefinition.RAW_LOAD_ADDRESS

        when {
            options.launcher == LauncherType.BASIC -> {
                if (program.actualLoadAddress != 0x0801)
                    throw AssemblyError("BASIC output must have load address $0801")
                out("; ---- basic program with sys call ----")
                out("* = ${program.actualLoadAddress.toHex()}")
                val year = Calendar.getInstance().get(Calendar.YEAR)
                out("  .word  (+), $year")
                out("  .null  $9e, format(' %d ', _prog8_entrypoint), $3a, $8f, ' prog8 by idj'")
                out("+\t.word  0")
                out("_prog8_entrypoint\t; assembly code starts here\n")
                out("  jsr  prog8_lib.init_system")
            }
            options.output == OutputType.PRG -> {
                out("; ---- program without basic sys call ----")
                out("* = ${program.actualLoadAddress.toHex()}\n")
                out("  jsr  prog8_lib.init_system")
            }
            options.output == OutputType.RAW -> {
                out("; ---- raw assembler program ----")
                out("* = ${program.actualLoadAddress.toHex()}\n")
            }
        }

        if (zeropage.exitProgramStrategy != Zeropage.ExitProgramStrategy.CLEAN_EXIT) {
            // disable shift-commodore charset switching and run/stop key
            out("  lda  #$80")
            out("  lda  #$80")
            out("  sta  657\t; disable charset switching")
            out("  lda  #239")
            out("  sta  808\t; disable run/stop key")
        }

        out("  ldx  #\$ff\t; init estack pointer")

        out("  ; initialize the variables in each block")
        for (block in program.allBlocks()) {
            val initVarsSub = block.statements.singleOrNull { it is Subroutine && it.name == initvarsSubName }
            if(initVarsSub!=null)
                out("  jsr  ${block.name}.$initvarsSubName")
        }

        out("  clc")
        when (zeropage.exitProgramStrategy) {
            Zeropage.ExitProgramStrategy.CLEAN_EXIT -> {
                out("  jmp  main.start\t; jump to program entrypoint")
            }
            Zeropage.ExitProgramStrategy.SYSTEM_RESET -> {
                out("  jsr  main.start\t; call program entrypoint")
                out("  jmp  (c64.RESET_VEC)\t; cold reset")
            }
        }
        out("")
    }

    private fun footer() {
        // the global list of all floating point constants for the whole program
        out("; global float constants")
        for (flt in globalFloatConsts) {
            val floatFill = makeFloatFill(MachineDefinition.Mflpt5.fromNumber(flt.key))
            out("${flt.value}\t.byte  $floatFill  ; float ${flt.key}")
        }
    }

    private fun block2asm(block: Block) {
        out("\n; ---- block: '${block.name}' ----")
        out("${block.name}\t.proc\n")           // TODO not if force_output?
        if(block.address!=null) {
            out(".cerror * > ${block.address.toHex()}, 'block address overlaps by ', *-${block.address.toHex()},' bytes'")
            out("* = ${block.address.toHex()}")
        }

        zeropagevars2asm(block.statements)
        memdefs2asm(block.statements)
        vardecls2asm(block.statements)
        out("")

        // first translate regular statements, and then put the subroutines at the end.
        val (subroutine, stmts) = block.statements.partition { it is Subroutine }
        stmts.forEach { translate(it) }
        subroutine.forEach { translate(it as Subroutine) }

        out("\n\t.pend\n")              // TODO not if force_output?
    }

    private fun out(str: String, splitlines: Boolean = true) {
        if (splitlines) {
            for (line in str.split('\n')) {
                val trimmed = if (line.startsWith(' ')) "\t" + line.trim() else line.trim()
                // trimmed = trimmed.replace(Regex("^\\+\\s+"), "+\t")  // sanitize local label indentation
                assemblyLines.add(trimmed)
            }
        } else assemblyLines.add(str)
    }

    private fun makeFloatFill(flt: MachineDefinition.Mflpt5): String {
        val b0 = "$" + flt.b0.toString(16).padStart(2, '0')
        val b1 = "$" + flt.b1.toString(16).padStart(2, '0')
        val b2 = "$" + flt.b2.toString(16).padStart(2, '0')
        val b3 = "$" + flt.b3.toString(16).padStart(2, '0')
        val b4 = "$" + flt.b4.toString(16).padStart(2, '0')
        return "$b0, $b1, $b2, $b3, $b4"
    }

    private fun zeropagevars2asm(statements: List<Statement>) {
        out("; vars allocated on zeropage")
        val variables = statements.filterIsInstance<VarDecl>().filter { it.type==VarDeclType.VAR }
        for(variable in variables) {
            val fullName = variable.scopedname
            val zpVar = allocatedZeropageVariables[fullName]
            if(zpVar==null) {
                // This var is not on the ZP yet. Attempt to move it there (if it's not a float, those take up too much space)
                if(variable.zeropage != ZeropageWish.NOT_IN_ZEROPAGE &&
                        variable.datatype in zeropage.allowedDatatypes
                        && variable.datatype != DataType.FLOAT) {
                    try {
                        val address = zeropage.allocate(fullName, variable.datatype, null)
                        out("${variable.name} = $address\t; auto zp ${variable.datatype}")
                        // make sure we add the var to the set of zpvars for this block
                        allocatedZeropageVariables[fullName] = Pair(address, variable.datatype)
                    } catch (x: ZeropageDepletedError) {
                        // leave it as it is.
                    }
                }
            }
            else {
                TODO("already allocated on zp?? $zpVar")
                // it was already allocated on the zp, what to do?
                // out("${variable.name} = ${zpVar.first}\t; zp ${zpVar.second}")
            }
        }
    }

    private fun vardecl2asm(decl: VarDecl) {
        when (decl.datatype) {
            DataType.UBYTE -> out("${decl.name}\t.byte  0")
            DataType.BYTE -> out("${decl.name}\t.char  0")
            DataType.UWORD -> out("${decl.name}\t.word  0")
            DataType.WORD -> out("${decl.name}\t.sint  0")
            DataType.FLOAT -> out("${decl.name}\t.byte  0,0,0,0,0  ; float")
            DataType.STRUCT -> {}       // is flattened
            DataType.STR -> TODO()
            DataType.STR_S -> TODO()
            DataType.ARRAY_UB -> {
                // unsigned integer byte arraysize
                val data = makeArrayFillDataUnsigned(decl)
                if (data.size <= 16)
                    out("${decl.name}\t.byte  ${data.joinToString()}")
                else {
                    out(decl.name)
                    for (chunk in data.chunked(16))
                        out("  .byte  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_B -> TODO()
            DataType.ARRAY_UW -> TODO()
            DataType.ARRAY_W -> TODO()
            DataType.ARRAY_F -> TODO()
        }
    }

    private fun memdefs2asm(statements: List<Statement>) {
        out("\n; memdefs and kernel subroutines")
        val memvars = statements.filterIsInstance<VarDecl>().filter { it.type==VarDeclType.MEMORY || it.type==VarDeclType.CONST }
        for(m in memvars) {
            out("  ${m.name} = ${(m.value as NumericLiteralValue).number.toHex()}")
        }
        val asmSubs = statements.filterIsInstance<Subroutine>().filter { it.isAsmSubroutine }
        for(sub in asmSubs) {
            if(sub.asmAddress!=null) {
                if(sub.statements.isNotEmpty())
                    throw AssemblyError("kernel subroutine cannot have statements")
                out("  ${sub.name} = ${sub.asmAddress.toHex()}")
            }
        }
    }

    private fun vardecls2asm(statements: List<Statement>) {
        out("\n; non-zeropage variables")
        val vars = statements.filterIsInstance<VarDecl>().filter { it.type==VarDeclType.VAR }

        // first output the flattened struct member variables *in order*
        // after that, the other variables sorted by their datatype

        val (structMembers, normalVars) = vars.partition { it.struct!=null }
        structMembers.forEach { vardecl2asm(it) }

        normalVars.sortedBy { it.datatype }.forEach {
            if(it.scopedname !in allocatedZeropageVariables)
                vardecl2asm(it)
        }
    }

    private fun makeArrayFillDataUnsigned(decl: VarDecl): List<String> {
        val array = (decl.value as ReferenceLiteralValue).array!!
        return array.map { (it as NumericLiteralValue).number.toString() }
    }

    private fun getFloatConst(number: Double): String {
        val name = globalFloatConsts[number]
        if(name!=null)
            return name
        val newName = "prog8_float_const_${globalFloatConsts.size}"
        globalFloatConsts[number] = newName
        return newName
    }

    private fun asmIdentifierName(identifier: IdentifierReference): String {
        val name = if(identifier.memberOfStruct(program.namespace)!=null) {
            identifier.targetVarDecl(program.namespace)!!.name
        } else {
            identifier.nameInSource.joinToString(".")
        }
        return fixNameSymbols(name)
    }

    private fun fixNameSymbols(name: String) = name.replace("<", "prog8_").replace(">", "")     // take care of the autogenerated invalid (anon) label names

    private fun branchInstruction(condition: BranchCondition, complement: Boolean) =
            if(complement) {
                when (condition) {
                    BranchCondition.CS -> "bcc"
                    BranchCondition.CC -> "bcs"
                    BranchCondition.EQ, BranchCondition.Z -> "beq"
                    BranchCondition.NE, BranchCondition.NZ -> "bne"
                    BranchCondition.VS -> "bvc"
                    BranchCondition.VC -> "bvs"
                    BranchCondition.MI, BranchCondition.NEG -> "bmi"
                    BranchCondition.PL, BranchCondition.POS -> "bpl"
                }
            } else {
                when (condition) {
                    BranchCondition.CS -> "bcs"
                    BranchCondition.CC -> "bcc"
                    BranchCondition.EQ, BranchCondition.Z -> "beq"
                    BranchCondition.NE, BranchCondition.NZ -> "bne"
                    BranchCondition.VS -> "bvs"
                    BranchCondition.VC -> "bvc"
                    BranchCondition.MI, BranchCondition.NEG -> "bmi"
                    BranchCondition.PL, BranchCondition.POS -> "bpl"
                }
            }

    private fun translate(stmt: Statement) {
        when(stmt) {
            is VarDecl, is StructDecl, is NopStatement -> {}
            is Directive -> translate(stmt)
            is Return -> translate(stmt)
            is Subroutine -> translate(stmt)
            is InlineAssembly -> translate(stmt)
            is FunctionCallStatement -> translate(stmt)
            is Assignment -> translate(stmt)
            is Jump -> translate(stmt)
            is PostIncrDecr -> translate(stmt)
            is Label -> translate(stmt)
            is BranchStatement -> translate(stmt)
            is ForLoop -> translate(stmt)
            is Continue -> TODO()
            is Break -> TODO()
            is IfStatement -> TODO()
            is WhileLoop -> TODO()
            is RepeatLoop -> TODO()
            is WhenStatement -> TODO()
            is BuiltinFunctionStatementPlaceholder -> throw AssemblyError("builtin function should not have placeholder anymore?")
            is AnonymousScope -> throw AssemblyError("anonscope should have been flattened")
            is Block -> throw AssemblyError("block should have been handled elsewhere")
        }
    }

    private fun translate(stmt: Label) {
        out("${stmt.name}")
    }

    private fun translate(stmt: BranchStatement) {
        if(stmt.truepart.containsNoCodeNorVars() && stmt.elsepart.containsCodeOrVars())
            throw AssemblyError("only else part contains code, shoud have been switched already")

        val jump = stmt.truepart.statements.first() as? Jump
        if(jump!=null) {
            // branch with only a jump
            val instruction = branchInstruction(stmt.condition, false)
            out("  $instruction  ${getJumpTarget(jump)}")
        } else {
            TODO("$stmt")
        }
    }

    private fun translate(stmt: Directive) {
        when(stmt.directive) {
            "%asminclude" -> {
                val sourcecode = loadAsmIncludeFile(stmt.args[0].str!!, stmt.definingModule().source)
                val scopeprefix = stmt.args[1].str ?: ""
                if(!scopeprefix.isBlank())
                    out("$scopeprefix\t.proc")
                assemblyLines.add(sourcecode.trimEnd().trimStart('\n'))
                if(!scopeprefix.isBlank())
                    out("  .pend\n")
            }
            "%asmbinary" -> {
                val offset = if(stmt.args.size>1) ", ${stmt.args[1].int}" else ""
                val length = if(stmt.args.size>2) ", ${stmt.args[2].int}" else ""
                out("  .binary \"${stmt.args[0].str}\" $offset $length")
            }
            "%breakpoint" -> {
                val label = "_prog8_breakpoint_${breakpointLabels.size+1}"
                breakpointLabels.add(label)
                out("$label\tnop")
            }
        }
    }

    private fun translate(stmt: ForLoop) {
        out("; for $stmt")
    }

    private fun translate(stmt: PostIncrDecr) {
        when {
            stmt.target.register!=null -> {
                when(stmt.target.register!!) {
                    Register.A -> out("""
                        clc
                        adc  #1
                    """)
                    Register.X -> out("  inx")
                    Register.Y -> out("  iny")
                }
            }
            stmt.target.identifier!=null -> {
                val targetName = asmIdentifierName(stmt.target.identifier!!)
                out("  inc  $targetName")
            }
            else -> TODO("postincrdecr $stmt")
        }
    }

    private fun translate(jmp: Jump) {
        out("  jmp  ${getJumpTarget(jmp)}")
    }

    private fun getJumpTarget(jmp: Jump): String {
        return when {
            jmp.identifier!=null -> asmIdentifierName(jmp.identifier)
            jmp.generatedLabel!=null -> jmp.generatedLabel
            jmp.address!=null -> jmp.address.toHex()
            else -> "????"
        }
    }

    private fun translate(ret: Return) {
        if(ret.value!=null) {
            TODO("$ret value")
        }
        out("  rts")
    }

    private fun translate(sub: Subroutine) {
        if(sub.isAsmSubroutine) {
            if(sub.asmAddress!=null)
                return  // already done at the memvars section

            // asmsub with most likely just an inline asm in it
            out("${sub.name}\t.proc")
            sub.statements.forEach{ translate(it) }
            out("  .pend\n")
        } else {
            // regular subroutine
            out("${sub.name}\t.proc")
            zeropagevars2asm(sub.statements)
            memdefs2asm(sub.statements)
            sub.statements.forEach{ translate(it) }
            vardecls2asm(sub.statements)
            out("  .pend\n")
        }
    }

    private fun translate(asm: InlineAssembly) {
        val assembly = asm.assembly.trimEnd().trimStart('\n')
        assemblyLines.add(assembly)
    }

    private fun translate(call: FunctionCallStatement) {
        if(call.arglist.isEmpty()) {
            out("  jsr  ${call.target.nameInSource.joinToString(".")}")
        } else {
            TODO("call $call")
        }
    }

    private fun translate(assign: Assignment) {
        if(assign.aug_op!=null)
            throw AssemblyError("aug-op assignments should have been transformed to normal ones")

        when(assign.value) {
            is NumericLiteralValue -> {
                val numVal = assign.value as NumericLiteralValue
                when(numVal.type) {
                    DataType.UBYTE, DataType.BYTE -> assignByteConstant(assign.target, numVal.number.toInt())
                    DataType.UWORD, DataType.WORD -> assignWordConstant(assign.target, numVal.number.toInt())
                    DataType.FLOAT -> assignFloatConstant(assign.target, numVal.number.toDouble())
                    DataType.STR -> TODO()
                    DataType.STR_S -> TODO()
                    DataType.ARRAY_UB -> TODO()
                    DataType.ARRAY_B -> TODO()
                    DataType.ARRAY_UW -> TODO()
                    DataType.ARRAY_W -> TODO()
                    DataType.ARRAY_F -> TODO()
                    DataType.STRUCT -> TODO()
                }
            }
            is RegisterExpr -> {
                assignRegister(assign.target, (assign.value as RegisterExpr).register)
            }
            is IdentifierReference -> {
                val type = assign.target.inferType(program, assign)!!
                when(type) {
                    DataType.UBYTE, DataType.BYTE -> assignByteVariable(assign.target, assign.value as IdentifierReference)
                    DataType.UWORD, DataType.WORD -> assignWordVariable(assign.target, assign.value as IdentifierReference)
                    DataType.FLOAT -> TODO()
                    DataType.STR -> TODO()
                    DataType.STR_S -> TODO()
                    DataType.ARRAY_UB -> TODO()
                    DataType.ARRAY_B -> TODO()
                    DataType.ARRAY_UW -> TODO()
                    DataType.ARRAY_W -> TODO()
                    DataType.ARRAY_F -> TODO()
                    DataType.STRUCT -> TODO()
                }
            }
            is AddressOf -> {
                val identifier = (assign.value as AddressOf).identifier
                val scopedname = (assign.value as AddressOf).scopedname!!
                assignAddressOf(assign.target, identifier, scopedname)
            }
            is DirectMemoryRead -> {
                val read = (assign.value as DirectMemoryRead)
                when(read.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (read.addressExpression as NumericLiteralValue).number.toInt()
                        assignMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignMemoryByte(assign.target, null, read.addressExpression as IdentifierReference)
                    }
                    else -> {
                        translateExpression(read.addressExpression)
                        out("; read memory byte from result and put that in ${assign.target}")      // TODO
                    }
                }
            }
            is PrefixExpression -> {
                translateExpression(assign.value as PrefixExpression)
                assignEvalResult(assign.target)
            }
            is BinaryExpression -> {
                translateExpression(assign.value as BinaryExpression)
                assignEvalResult(assign.target)
            }
            is ArrayIndexedExpression -> {
                translateExpression(assign.value as ArrayIndexedExpression)
                assignEvalResult(assign.target)
            }
            is TypecastExpression -> {
                val cast = assign.value as TypecastExpression
                val sourceType = cast.expression.inferType(program)
                val targetType = assign.target.inferType(program, assign)
                if((sourceType in ByteDatatypes && targetType in ByteDatatypes) ||
                        (sourceType in WordDatatypes && targetType in WordDatatypes)) {
                    // no need for a type cast
                    assign.value = cast.expression
                    translate(assign)
                } else {
                    translateExpression(assign.value as TypecastExpression)
                    assignEvalResult(assign.target)
                }
            }
            is FunctionCall -> {
                translateExpression(assign.value as FunctionCall)
                assignEvalResult(assign.target)
            }
            is ReferenceLiteralValue -> TODO("string/array/struct assignment?")
            is StructLiteralValue -> throw AssemblyError("struct literal value assignment should have been flattened")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values")
        }
    }

    private fun translateExpression(expr: ArrayIndexedExpression) {
        out("; evaluate arrayindexed ${expr}")
    }

    private fun translateExpression(expr: FunctionCall) {
        out("; evaluate funccall ${expr}")
    }

    private fun translateExpression(expr: TypecastExpression) {
        translateExpression(expr.expression)
        out("; typecast to ${expr.type}")
    }

    private fun translateExpression(expression: Expression) {
        when(expression) {
            is PrefixExpression -> translateExpression(expression)
            is BinaryExpression -> translateExpression(expression)
            is ArrayIndexedExpression -> translateExpression(expression)
            is TypecastExpression -> translateExpression(expression)
            is AddressOf -> translateExpression(expression)
            is DirectMemoryRead -> translateExpression(expression)
            is NumericLiteralValue -> translateExpression(expression)
            is RegisterExpr -> translateExpression(expression)
            is IdentifierReference -> translateExpression(expression)
            is FunctionCall -> translateExpression(expression)
            is ReferenceLiteralValue -> TODO("string/array/struct assignment?")
            is StructLiteralValue -> throw AssemblyError("struct literal value assignment should have been flattened")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values")
        }
    }

    private fun translateExpression(expr: AddressOf) {
        out("; take address of ${expr}")
    }

    private fun translateExpression(expr: DirectMemoryRead) {
        out("; memread ${expr}")
    }

    private fun translateExpression(expr: NumericLiteralValue) {
        out("; literalvalue ${expr}")
    }

    private fun translateExpression(expr: RegisterExpr) {
        out("; register value ${expr}")
    }

    private fun translateExpression(expr: IdentifierReference) {
        out("; identifier value ${expr}")
    }

    private fun translateExpression(expr: BinaryExpression) {
        translateExpression(expr.left)
        translateExpression(expr.right)
        out("; evaluate binary ${expr.operator}")
    }

    private fun translateExpression(expr: PrefixExpression) {
        translateExpression(expr.expression)
        out("; evaluate prefix ${expr.operator}")
    }

    private fun assignEvalResult(target: AssignTarget) {
        out("; put result in $target")
    }

    private fun assignAddressOf(target: AssignTarget, name: IdentifierReference, scopedname: String) {
        when {
            target.identifier!=null -> {
                val targetName = asmIdentifierName(target.identifier)
                val struct = name.memberOfStruct(program.namespace)
                if(struct!=null) {
                    // take the address of the first struct member instead
                    val decl = name.targetVarDecl(program.namespace)!!
                    val firstStructMember = struct.nameOfFirstMember()
                    // find the flattened var that belongs to this first struct member
                    val firstVarName = listOf(decl.name, firstStructMember)
                    val firstVar = name.definingScope().lookup(firstVarName, name) as VarDecl
                    val sourceName = firstVar.name
                    out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  $targetName
                        sty  $targetName+1
                    """)
                } else {
                    val sourceName = fixNameSymbols(scopedname)
                    out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  $targetName
                        sty  $targetName+1
                    """)
                }
            }
            else -> TODO("assign address to $target")
        }
    }

    private fun assignWordVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = variable.nameInSource.joinToString(".")
        when {
            target.identifier!=null -> {
                val targetName = asmIdentifierName(target.identifier)
                out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  $targetName
                    sty  $targetName+1
                """)
            }
            else -> TODO("assign word to $target")
        }
    }

    private fun assignByteVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = variable.nameInSource.joinToString(".")
        when {
            target.register!=null -> {
                out("  ld${target.register.name.toLowerCase()}  $sourceName")
            }
            target.identifier!=null -> {
                val targetName = asmIdentifierName(target.identifier)
                out("""
                    lda  $sourceName
                    sta  $targetName
                    """)
            }
            else -> TODO("assign byte to $target")
        }
    }

    private fun assignRegister(target: AssignTarget, register: Register) {
        when {
            target.identifier!=null -> {
                val targetName = asmIdentifierName(target.identifier)
                out("  st${register.name.toLowerCase()}  $targetName")
            }
            target.register!=null -> {
                when(register) {
                    Register.A -> when(target.register) {
                        Register.A -> {}
                        Register.X -> out("  tax")
                        Register.Y -> out("  tay")
                    }
                    Register.X -> when(target.register) {
                        Register.A -> out("  txa")
                        Register.X -> {}
                        Register.Y -> out("  txy")
                    }
                    Register.Y -> when(target.register) {
                        Register.A -> out("  tya")
                        Register.X -> out("  tyx")
                        Register.Y -> {}
                    }
                }
            }
            else -> out("; assign register $register to $target")
        }
    }

    private fun assignWordConstant(target: AssignTarget, word: Int) {
        if(target.identifier!=null) {
            val targetName = asmIdentifierName(target.identifier)
            // TODO optimize case where lsb = msb
            out("""
                lda  #<${word.toHex()}
                ldy  #>${word.toHex()}
                sta  $targetName
                sty  $targetName+1
            """)
        } else {
            out("; assign byte $word to $target")
        }
    }

    private fun assignByteConstant(target: AssignTarget, byte: Int) {
        when {
            target.register!=null -> {
                out("  ld${target.register.name.toLowerCase()}  #${byte.toHex()}")
            }
            target.identifier!=null -> {
                val targetName = asmIdentifierName(target.identifier)
                out("""
                lda  #${byte.toHex()}
                sta  $targetName
            """)
            }
            else -> out("; assign byte $byte to $target")
        }
    }

    private fun assignFloatConstant(target: AssignTarget, float: Double) {
        if(float==0.0) {
            // optimized case for float zero
            if (target.identifier != null) {
                val targetName = asmIdentifierName(target.identifier)
                out("""
                        lda  #0
                        sta  $targetName
                        sta  $targetName+1
                        sta  $targetName+2
                        sta  $targetName+3
                        sta  $targetName+4
                    """)
            } else {
                out("; assign float 0.0 to $target")
            }
        } else {
            // non-zero value
            val constFloat = getFloatConst(float)
            if (target.identifier != null) {
                val targetName = asmIdentifierName(target.identifier)
                out("""
                        lda  $constFloat
                        sta  $targetName
                        lda  $constFloat+1
                        sta  $targetName+1
                        lda  $constFloat+2
                        sta  $targetName+2
                        lda  $constFloat+3
                        sta  $targetName+3
                        lda  $constFloat+4
                        sta  $targetName+4
                    """)
            } else {
                out("; assign float $float ($constFloat) to $target")
            }
        }
    }

    private fun assignMemoryByte(target: AssignTarget, address: Int?, identifier: IdentifierReference?) {
        if(address!=null) {
            when {
                target.register!=null -> {
                    out("  ld${target.register.name.toLowerCase()}  ${address.toHex()}")
                }
                target.identifier!=null -> {
                    val targetName = asmIdentifierName(target.identifier)
                    out("""
                        lda  ${address.toHex()}
                        sta  $targetName
                        """)
                }
                else -> TODO()
            }
        }
        else if(identifier!=null) {
            val sourceName = asmIdentifierName(identifier)
            when {
                target.register!=null -> {
                    out("""
                        ldy  #0
                        lda  ($sourceName),y
                    """)
                    when(target.register){
                        Register.A -> {}
                        Register.X -> out("  tax")
                        Register.Y -> out("  tay")
                    }
                }
                target.identifier!=null -> {
                    val targetName = asmIdentifierName(target.identifier)
                    out("""
                        ldy  #0
                        lda  ($sourceName),y
                        sta  $targetName
                    """)
                }
                else -> TODO()
            }
        }
    }
}
