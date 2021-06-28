package prog8.compiler.target.cpu6502.codegen.assignment

import prog8.ast.IMemSizer
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.cpu6502.codegen.AsmGen


internal enum class TargetStorageKind {
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER,
    STACK
}

internal enum class SourceStorageKind {
    LITERALCHAR,
    LITERALNUMBER,
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER,
    STACK,              // value is already present on stack
    EXPRESSION,         // expression in ast-form, still to be evaluated
}

internal class AsmAssignTarget(val kind: TargetStorageKind,
                               private val program: Program,
                               private val asmgen: AsmGen,
                               val datatype: DataType,
                               val scope: Subroutine?,
                               private val variableAsmName: String? = null,
                               val array: ArrayIndexedExpression? = null,
                               val memory: DirectMemoryWrite? = null,
                               val register: RegisterOrPair? = null,
                               val origAstTarget: AssignTarget? = null
                               )
{
    val constMemoryAddress by lazy { memory?.addressExpression?.constValue(program)?.number?.toInt() ?: 0}
    val constArrayIndexValue by lazy { array?.indexer?.constIndex() }
    val asmVarname: String
        get() = if(array==null)
            variableAsmName!!
        else
            asmgen.asmVariableName(array.arrayvar)

    lateinit var origAssign: AsmAssignment

    init {
        if(register!=null && datatype !in NumericDatatypes)
            throw AssemblyError("register must be integer or float type")
    }

    companion object {
        fun fromAstAssignment(assign: Assignment, program: Program, asmgen: AsmGen): AsmAssignTarget = with(assign.target) {
            val idt = inferType(program)
            if(!idt.isKnown)
                throw AssemblyError("unknown dt")
            val dt = idt.typeOrElse(DataType.UNDEFINED)
            when {
                identifier != null -> AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, dt, assign.definingSubroutine(), variableAsmName = asmgen.asmVariableName(identifier!!), origAstTarget =  this)
                arrayindexed != null -> AsmAssignTarget(TargetStorageKind.ARRAY, program, asmgen, dt, assign.definingSubroutine(), array = arrayindexed, origAstTarget =  this)
                memoryAddress != null -> AsmAssignTarget(TargetStorageKind.MEMORY, program, asmgen, dt, assign.definingSubroutine(), memory =  memoryAddress, origAstTarget =  this)
                else -> throw AssemblyError("weird target")
            }
        }

        fun fromRegisters(registers: RegisterOrPair, scope: Subroutine?, program: Program, asmgen: AsmGen): AsmAssignTarget =
                when(registers) {
                    RegisterOrPair.A,
                    RegisterOrPair.X,
                    RegisterOrPair.Y -> AsmAssignTarget(TargetStorageKind.REGISTER, program, asmgen, DataType.UBYTE, scope, register = registers)
                    RegisterOrPair.AX,
                    RegisterOrPair.AY,
                    RegisterOrPair.XY -> AsmAssignTarget(TargetStorageKind.REGISTER, program, asmgen, DataType.UWORD, scope, register = registers)
                    RegisterOrPair.FAC1,
                    RegisterOrPair.FAC2 -> AsmAssignTarget(TargetStorageKind.REGISTER, program, asmgen, DataType.FLOAT, scope, register = registers)
                    RegisterOrPair.R0,
                    RegisterOrPair.R1,
                    RegisterOrPair.R2,
                    RegisterOrPair.R3,
                    RegisterOrPair.R4,
                    RegisterOrPair.R5,
                    RegisterOrPair.R6,
                    RegisterOrPair.R7,
                    RegisterOrPair.R8,
                    RegisterOrPair.R9,
                    RegisterOrPair.R10,
                    RegisterOrPair.R11,
                    RegisterOrPair.R12,
                    RegisterOrPair.R13,
                    RegisterOrPair.R14,
                    RegisterOrPair.R15 -> AsmAssignTarget(TargetStorageKind.REGISTER, program, asmgen, DataType.UWORD, scope, register = registers)
                }
    }
}

internal class AsmAssignSource(val kind: SourceStorageKind,
                               private val program: Program,
                               private val asmgen: AsmGen,
                               val datatype: DataType,
                               private val variableAsmName: String? = null,
                               val array: ArrayIndexedExpression? = null,
                               val memory: DirectMemoryRead? = null,
                               val register: RegisterOrPair? = null,
                               val number: NumericLiteralValue? = null,
                               val expression: Expression? = null
)
{
    val constMemoryAddress by lazy { memory?.addressExpression?.constValue(program)?.number?.toInt() ?: 0}
    val constArrayIndexValue by lazy { array?.indexer?.constIndex() }

    val asmVarname: String
        get() = if(array==null)
            variableAsmName!!
        else
            asmgen.asmVariableName(array.arrayvar)

    companion object {
        fun fromAstSource(indexer: ArrayIndex, program: Program, asmgen: AsmGen): AsmAssignSource = fromAstSource(indexer.indexExpr, program, asmgen)

        fun fromAstSource(expr: Expression, program: Program, asmgen: AsmGen): AsmAssignSource {
            val cv = expr.constValue(program)
            if(cv!=null)
                return AsmAssignSource(SourceStorageKind.LITERALNUMBER, program, asmgen, cv.type, number = cv)

            return when(expr) {
                is CharLiteral -> AsmAssignSource(SourceStorageKind.LITERALCHAR, program, asmgen, DataType.CHAR, expression = expr)
                is NumericLiteralValue -> AsmAssignSource(SourceStorageKind.LITERALNUMBER, program, asmgen, expr.type, number = cv)
                is StringLiteralValue -> throw AssemblyError("string literal value should not occur anymore for asm generation")
                is ArrayLiteralValue -> throw AssemblyError("array literal value should not occur anymore for asm generation")
                is IdentifierReference -> {
                    val dt = expr.inferType(program).typeOrElse(DataType.UNDEFINED)
                    val varName=asmgen.asmVariableName(expr)
                    // special case: "cx16.r[0-15]" are 16-bits virtual registers of the commander X16 system
                    if(dt == DataType.UWORD && varName.lowercase().startsWith("cx16.r")) { // TODO: very bad hack!
                        val regStr = varName.lowercase().substring(5)
                        val reg = RegisterOrPair.valueOf(regStr.uppercase())
                        AsmAssignSource(SourceStorageKind.REGISTER, program, asmgen, dt, register = reg)
                    } else {
                        AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, dt, variableAsmName = varName)
                    }
                }
                is DirectMemoryRead -> {
                    AsmAssignSource(SourceStorageKind.MEMORY, program, asmgen, DataType.UBYTE, memory = expr)
                }
                is ArrayIndexedExpression -> {
                    val dt = expr.inferType(program).typeOrElse(DataType.UNDEFINED)
                    AsmAssignSource(SourceStorageKind.ARRAY, program, asmgen, dt, array = expr)
                }
                is FunctionCall -> {
                    when (val sub = expr.target.targetStatement(program)) {
                        is Subroutine -> {
                            val returnType = sub.returntypes.zip(sub.asmReturnvaluesRegisters).firstOrNull { rr -> rr.second.registerOrPair != null || rr.second.statusflag!=null }?.first
                                    ?: throw AssemblyError("can't translate zero return values in assignment")

                            AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, returnType, expression = expr)
                        }
                        is BuiltinFunctionStatementPlaceholder -> {
                            val returnType = expr.inferType(program)
                            if(!returnType.isKnown)
                                throw AssemblyError("unknown dt")
                            AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, returnType.typeOrElse(DataType.UNDEFINED), expression = expr)
                        }
                        else -> {
                            throw AssemblyError("weird call")
                        }
                    }
                }
                else -> {
                    val dt = expr.inferType(program)
                    if(!dt.isKnown)
                        throw AssemblyError("unknown dt")
                    AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, dt.typeOrElse(DataType.UNDEFINED), expression = expr)
                }
            }
        }
    }

    fun adjustSignedUnsigned(target: AsmAssignTarget): AsmAssignSource {
        // allow some signed/unsigned relaxations

        fun withAdjustedDt(newType: DataType) =
                AsmAssignSource(kind, program, asmgen, newType, variableAsmName, array, memory, register, number, expression)

        if(target.datatype!=datatype) {
            if(target.datatype in ByteDatatypes && datatype in ByteDatatypes) {
                return withAdjustedDt(target.datatype)
            } else if(target.datatype in WordDatatypes && datatype in WordDatatypes) {
                return withAdjustedDt(target.datatype)
            }
        }
        return this
    }

}


internal class AsmAssignment(val source: AsmAssignSource,
                             val target: AsmAssignTarget,
                             val isAugmentable: Boolean,
                             memsizer: IMemSizer,
                             val position: Position) {

    init {
        if(target.register !in setOf(RegisterOrPair.XY, RegisterOrPair.AX, RegisterOrPair.AY))
            require(source.datatype != DataType.UNDEFINED) { "must not be placeholder/undefined datatype" }
            require(memsizer.memorySize(source.datatype) <= memsizer.memorySize(target.datatype)) {
                "source storage size must be less or equal to target datatype storage size"
            }
    }
}
