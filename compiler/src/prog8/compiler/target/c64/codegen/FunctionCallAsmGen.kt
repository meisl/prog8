package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.ast.statements.SubroutineParameter
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.codegen.assignment.*


internal class FunctionCallAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctionCallStatement(stmt: IFunctionCall) {
        translateFunctionCall(stmt)
        // functioncalls no longer return results on the stack, so simply ignore the results in the registers
    }


    internal fun translateFunctionCall(stmt: IFunctionCall) {
        // output the code to setup the parameters and perform the actual call
        // does NOT output the code to deal with the result values!
        val sub = stmt.target.targetSubroutine(program.namespace) ?: throw AssemblyError("undefined subroutine ${stmt.target}")
        val saveX = sub.shouldSaveX()
        val regSaveOnStack = sub.asmAddress==null       // rom-routines don't require registers to be saved on stack, normal subroutines do because they can contain nested calls
        val (keepAonEntry: Boolean, keepAonReturn: Boolean) = sub.shouldKeepA()
        if(saveX) {
            if(regSaveOnStack)
                asmgen.saveRegisterStack(CpuRegister.X, keepAonEntry)
            else
                asmgen.saveRegisterLocal(CpuRegister.X, (stmt as Node).definingSubroutine()!!)
        }

        val subName = asmgen.asmSymbolName(stmt.target)
        if(stmt.args.isNotEmpty()) {
            if(sub.asmParameterRegisters.isEmpty()) {
                // via variables
                for(arg in sub.parameters.withIndex().zip(stmt.args)) {
                    argumentViaVariable(sub, arg.first, arg.second)
                }
            } else {
                // via registers
                if(sub.parameters.size==1) {
                    // just a single parameter, no risk of clobbering registers
                    argumentViaRegister(sub, IndexedValue(0, sub.parameters.single()), stmt.args[0])
                } else {
                    when {
                        stmt.args.all {it is AddressOf ||
                                it is NumericLiteralValue ||
                                it is StringLiteralValue ||
                                it is ArrayLiteralValue ||
                                it is IdentifierReference} -> {
                            // There's no risk of clobbering for these simple argument types. Optimize the register loading directly from these values.
                            for(arg in sub.parameters.withIndex().zip(stmt.args)) {
                                argumentViaRegister(sub, arg.first, arg.second)
                            }
                        }
                        else -> {
                            // Risk of clobbering due to complex expression args. Evaluate first, then assign registers.
                            // TODO not used yet because code is larger: registerArgsViaVirtualRegistersEvaluation(stmt, sub)
                            registerArgsViaStackEvaluation(stmt, sub)
                        }
                    }
                }
            }
        }
        asmgen.out("  jsr  $subName")
        if(saveX) {
            if(regSaveOnStack)
                asmgen.restoreRegisterStack(CpuRegister.X, keepAonReturn)
            else
                asmgen.restoreRegisterLocal(CpuRegister.X)
        }
    }

    private fun registerArgsViaVirtualRegistersEvaluation(stmt: IFunctionCall, sub: Subroutine) {
        // This is called when one or more of the arguments are 'complex' and
        // cannot be assigned to a cpu register easily or risk clobbering other cpu registers.
        // To solve this, the expressions are first evaluated into the 'virtual registers and then loaded from there.

        // TODO not used yet; code generated here is bigger than the eval-stack based code because it always treats the virtual regs as words and also sometimes uses the stack for evaluation and then copies it to a virtual register.

        if(sub.parameters.isEmpty())
            return

        // 1. load all arguments left-to-right into the R0..R15 registers
        for (vrarg in stmt.args.zip(Cx16VirtualRegisters)) {
            asmgen.assignExpressionToRegister(vrarg.first, vrarg.second)
        }

        // 2. Gather up the arguments in the correct registers (in specific order to not clobber earlier values)
        var argForCarry: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForXregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForAregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null

        for(argi in stmt.args.zip(sub.asmParameterRegisters).withIndex()) {
            val valueIsInVirtualReg = Cx16VirtualRegisters[argi.index]
            val valueIsInVirtualRegAsmString = valueIsInVirtualReg.toString().toLowerCase()
            when {
                argi.value.second.statusflag == Statusflag.Pc -> {
                    require(argForCarry == null)
                    argForCarry = argi
                }
                argi.value.second.statusflag != null -> throw AssemblyError("can only use Carry as status flag parameter")
                argi.value.second.registerOrPair in setOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) -> {
                    require(argForXregister==null)
                    argForXregister = argi
                }
                argi.value.second.registerOrPair in setOf(RegisterOrPair.A, RegisterOrPair.AY) -> {
                    require(argForAregister == null)
                    argForAregister = argi
                }
                argi.value.second.registerOrPair == RegisterOrPair.Y -> {
                    asmgen.out("  ldy  cx16.$valueIsInVirtualRegAsmString")
                }
                argi.value.second.registerOrPair in Cx16VirtualRegisters -> {
                    if(argi.value.second.registerOrPair != valueIsInVirtualReg) {
                        asmgen.out("""
                            lda  cx16.$valueIsInVirtualRegAsmString  
                            sta  cx16.${argi.value.second.registerOrPair.toString().toLowerCase()}
                            lda  cx16.$valueIsInVirtualRegAsmString+1   
                            sta  cx16.${argi.value.second.registerOrPair.toString().toLowerCase()}+1
                        """)
                    }
                }
                else -> throw AssemblyError("weird argument")
            }
        }

        if(argForCarry!=null) {
            asmgen.out("""
                lda  cx16.${Cx16VirtualRegisters[argForCarry.index].toString().toLowerCase()}
                beq  +
                sec
                bcs  ++
+               clc
+               php""")             // push the status flags
        }

        if(argForAregister!=null) {
            when(argForAregister.value.second.registerOrPair) {
                RegisterOrPair.A -> asmgen.out("  lda  cx16.${Cx16VirtualRegisters[argForAregister.index].toString().toLowerCase()}")
                RegisterOrPair.AY -> asmgen.out("  lda  cx16.${Cx16VirtualRegisters[argForAregister.index].toString().toLowerCase()} |  ldy  cx16.${Cx16VirtualRegisters[argForAregister.index].toString().toLowerCase()}+1")
                else -> throw AssemblyError("weird arg")
            }
        }

        if(argForXregister!=null) {
            if(argForAregister!=null)
                asmgen.out("  pha")
            when(argForXregister.value.second.registerOrPair) {
                RegisterOrPair.X -> asmgen.out("  ldx  cx16.${Cx16VirtualRegisters[argForXregister.index].toString().toLowerCase()}")
                RegisterOrPair.AX -> asmgen.out("  lda  cx16.${Cx16VirtualRegisters[argForXregister.index].toString().toLowerCase()} |  ldx  cx16.${Cx16VirtualRegisters[argForXregister.index].toString().toLowerCase()}+1")
                RegisterOrPair.XY -> asmgen.out("  ldx  cx16.${Cx16VirtualRegisters[argForXregister.index].toString().toLowerCase()} |  ldy  cx16.${Cx16VirtualRegisters[argForXregister.index].toString().toLowerCase()}+1")
                else -> throw AssemblyError("weird arg")
            }
            if(argForAregister!=null)
                asmgen.out("  pla")
        }

        if(argForCarry!=null)
            asmgen.out("  plp")       // set the carry flag back to correct value
    }


    private fun registerArgsViaStackEvaluation(stmt: IFunctionCall, sub: Subroutine) {
        // this is called when one or more of the arguments are 'complex' and
        // cannot be assigned to a register easily or risk clobbering other registers.

        if(sub.parameters.isEmpty())
            return

        // 1. load all arguments reversed onto the stack: first arg goes last (is on top).
        for (arg in stmt.args.reversed())
            asmgen.translateExpression(arg)

        var argForCarry: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForXregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForAregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null

        asmgen.out("  inx")     // align estack pointer

        for(argi in stmt.args.zip(sub.asmParameterRegisters).withIndex()) {
            when {
                argi.value.second.statusflag == Statusflag.Pc -> {
                    require(argForCarry == null)
                    argForCarry = argi
                }
                argi.value.second.statusflag != null -> throw AssemblyError("can only use Carry as status flag parameter")
                argi.value.second.registerOrPair in setOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) -> {
                    require(argForXregister==null)
                    argForXregister = argi
                }
                argi.value.second.registerOrPair in setOf(RegisterOrPair.A, RegisterOrPair.AY) -> {
                    require(argForAregister == null)
                    argForAregister = argi
                }
                argi.value.second.registerOrPair == RegisterOrPair.Y -> {
                    asmgen.out("  ldy  P8ESTACK_LO+${argi.index},x")
                }
                argi.value.second.registerOrPair in Cx16VirtualRegisters -> {
                        asmgen.out("""
                            lda  P8ESTACK_LO+${argi.index},x
                            sta  cx16.${argi.value.second.registerOrPair.toString().toLowerCase()}
                            lda  P8ESTACK_HI+${argi.index},x
                            sta  cx16.${argi.value.second.registerOrPair.toString().toLowerCase()}+1
                        """)
                    }
                else -> throw AssemblyError("weird argument")
            }
        }

        if(argForCarry!=null) {
            asmgen.out("""
                lda  P8ESTACK_LO+${argForCarry.index},x
                beq  +
                sec
                bcs  ++
+               clc
+               php""")             // push the status flags
        }

        if(argForAregister!=null) {
            when(argForAregister.value.second.registerOrPair) {
                RegisterOrPair.A -> asmgen.out("  lda  P8ESTACK_LO+${argForAregister.index},x")
                RegisterOrPair.AY -> asmgen.out("  lda  P8ESTACK_LO+${argForAregister.index},x |  ldy  P8ESTACK_HI+${argForAregister.index},x")
                else -> throw AssemblyError("weird arg")
            }
        }

        if(argForXregister!=null) {

            if(argForAregister!=null)
                asmgen.out("  pha")
            when(argForXregister.value.second.registerOrPair) {
                RegisterOrPair.X -> asmgen.out("  lda  P8ESTACK_LO+${argForXregister.index},x |  tax")
                RegisterOrPair.AX -> asmgen.out("  ldy  P8ESTACK_LO+${argForXregister.index},x |  lda  P8ESTACK_HI+${argForXregister.index},x |  tax |  tya")
                RegisterOrPair.XY -> asmgen.out("  ldy  P8ESTACK_HI+${argForXregister.index},x |  lda  P8ESTACK_LO+${argForXregister.index},x |  tax")
                else -> throw AssemblyError("weird arg")
            }
            if(argForAregister!=null)
                asmgen.out("  pla")
        } else {
            repeat(sub.parameters.size - 1) { asmgen.out("  inx") }       // unwind stack
        }

        if(argForCarry!=null)
            asmgen.out("  plp")       // set the carry flag back to correct value
    }

    private fun argumentViaVariable(sub: Subroutine, parameter: IndexedValue<SubroutineParameter>, value: Expression) {
        // pass parameter via a regular variable (not via registers)
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("unknown dt")
        val valueDt = valueIDt.typeOrElse(DataType.STRUCT)
        if(!isArgumentTypeCompatible(valueDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val varName = asmgen.asmVariableName(sub.scopedname+"."+parameter.value.name)
        asmgen.assignExpressionToVariable(value, varName, parameter.value.type, sub)
    }

    private fun argumentViaRegister(sub: Subroutine, parameter: IndexedValue<SubroutineParameter>, value: Expression) {
        // pass argument via a register parameter
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("unknown dt")
        val valueDt = valueIDt.typeOrElse(DataType.STRUCT)
        if(!isArgumentTypeCompatible(valueDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val paramRegister = sub.asmParameterRegisters[parameter.index]
        val statusflag = paramRegister.statusflag
        val register = paramRegister.registerOrPair
        val requiredDt = parameter.value.type
        if(requiredDt!=valueDt) {
            if(valueDt largerThan requiredDt)
                throw AssemblyError("can only convert byte values to word param types")
        }
        when {
            statusflag!=null -> {
                if(requiredDt!=valueDt)
                    throw AssemblyError("for statusflag, byte value is required")
                if (statusflag == Statusflag.Pc) {
                    // this param needs to be set last, right before the jsr
                    // for now, this is already enforced on the subroutine definition by the Ast Checker
                    when(value) {
                        is NumericLiteralValue -> {
                            val carrySet = value.number.toInt() != 0
                            asmgen.out(if(carrySet) "  sec" else "  clc")
                        }
                        is IdentifierReference -> {
                            val sourceName = asmgen.asmVariableName(value)
                            asmgen.out("""
            pha
            lda  $sourceName
            beq  +
            sec  
            bcs  ++
+           clc
+           pla
""")
                        }
                        else -> {
                            asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                            asmgen.out("""
                                beq  +
                                sec
                                bcs  ++
+                               clc
+""")
                        }
                    }
                }
                else throw AssemblyError("can only use Carry as status flag parameter")
            }
            else -> {
                // via register or register pair
                register!!
                if(requiredDt largerThan valueDt) {
                    // we need to sign extend the source, do this via temporary word variable
                    val scratchVar = asmgen.asmVariableName("P8ZP_SCRATCH_W1")
                    asmgen.assignExpressionToVariable(value, scratchVar, DataType.UBYTE, sub)
                    asmgen.signExtendVariableLsb(scratchVar, valueDt)
                    asmgen.assignVariableToRegister(scratchVar, register)
                }
                else {
                    val target = AsmAssignTarget.fromRegisters(register, sub, program, asmgen)
                    val src = if(valueDt in PassByReferenceDatatypes) {
                        if(value is IdentifierReference) {
                            val addr = AddressOf(value, Position.DUMMY)
                            AsmAssignSource.fromAstSource(addr, program, asmgen).adjustSignedUnsigned(target)
                        } else {
                            AsmAssignSource.fromAstSource(value, program, asmgen).adjustSignedUnsigned(target)
                        }
                    } else {
                        AsmAssignSource.fromAstSource(value, program, asmgen).adjustSignedUnsigned(target)
                    }
                    asmgen.translateNormalAssignment(AsmAssignment(src, target, false, Position.DUMMY))
                }
            }
        }
    }

    private fun isArgumentTypeCompatible(argType: DataType, paramType: DataType): Boolean {
        if(argType isAssignableTo paramType)
            return true
        if(argType in ByteDatatypes && paramType in ByteDatatypes)
            return true
        if(argType in WordDatatypes && paramType in WordDatatypes)
            return true

        // we have a special rule for some types.
        // strings are assignable to UWORD, for example, and vice versa
        if(argType==DataType.STR && paramType==DataType.UWORD)
            return true
        if(argType==DataType.UWORD && paramType == DataType.STR)
            return true

        return false
    }
}
