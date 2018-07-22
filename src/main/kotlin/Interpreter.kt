import kotlin.math.roundToInt
import kotlin.math.roundToLong
import Opcodes as o

data class CallFrame (
        val valueStackTopOnEntry: Int
)

class Interpreter(
        private val bytecode: ByteArray,
        private val valueStackSize: Int,
        private val localsCount: Int
) {
    private val valueStack = IntArray(valueStackSize)
    private var valueStackTop = 0
    private val locals = LongArray(localsCount)
    private var offset = 0
    private var wide = false

    fun pushi(i: Int) {
        valueStack[valueStackTop++] = i
    }
    fun popi() = valueStack[--valueStackTop]
    fun pushl(l: Long) {
        pushi(l.toInt())
        pushi((l ushr 32).toInt())
    }
    fun popl(): Long {
        var result = 0L
        result = result or (popi() shl 32).toLong()
        result = result or popi().toLong()
        return result
    }
    fun pushf(f: Float) = pushi(f.toRawBits())
    fun popf(): Float = Float.fromBits(popi())
    fun pushd(d: Double) = pushl(d.toRawBits())
    fun popd(): Double = Double.fromBits(popl())

    inline fun op2I(action: (Int, Int) -> Int) {
        val a = popi()
        val b = popi()
        val result = action(a, b)
        pushi(result)
    }

    inline fun op2L(action: (Long, Long) -> Long) {
        val a = popl()
        val b = popl()
        val result = action(a, b)
        pushl(result)
    }

    inline fun op2F(action: (Float, Float) -> Float) {
        val a = popf()
        val b = popf()
        val result = action(a, b)
        pushf(result)
    }

    inline fun op2D(action: (Double, Double) -> Double) {
        val a = popd()
        val b = popd()
        val result = action(a, b)
        pushd(result)
    }

    private fun readUByte(): Int = bytecode[offset++].toInt() and 0xFF
    private fun readSByte(): Int = bytecode[offset++].toInt()

    private fun readUShort(): Int {
        val first = readUByte()
        val second = readUByte()
        return (first shl 8) or second
    }

    private fun readSShort(): Int {
        val first = readSByte()
        val second = readUByte()
        return (first shl 8) or second
    }

    private fun readSInt(): Int {
        val first = readUShort()
        val second = readUShort()
        return (first shl 16) or second
    }

    private fun getOpcodeOffset(): Int {
        return if (wide) {
            readUShort()
        } else {
            readUByte()
        }
    }

    private fun loadLocalI(offset: Int) { pushi(locals[offset].toInt()) }
    private fun loadLocalL(offset: Int) { pushl(locals[offset]) }
    private fun loadLocalF(offset: Int) { pushf(Float.fromBits(locals[offset].toInt())) }
    private fun loadLocalD(offset: Int) { pushd(Double.fromBits(locals[offset])) }

    private fun storeLocalI(offset: Int) { locals[offset] = popi().toLong() }
    private fun storeLocalL(offset: Int) { locals[offset] = popl() }
    private fun storeLocalF(offset: Int) { locals[offset] = popf().toRawBits().toLong() }
    private fun storeLocalD(offset: Int) { locals[offset] = popd().toRawBits() }

    private inline fun <reified T> arrayLoad(): Unit = TODO()

    private inline fun <reified T> arrayStore(): Unit = TODO()

    private inline fun branchIfI(cond: (Int) -> Boolean) {
        val branchOffset = readSShort()
        if (cond(popi())) {
            offset += branchOffset
        }
    }

    private inline fun branchIfI2(cond: (Int, Int) -> Boolean) {
        val branchOffset = readSShort()
        if (cond(popi(), popi())) {
            offset += branchOffset
        }
    }

    fun interpret() {
        val opcode = readUByte()
        when(opcode) {
            o.iconst_m1 -> pushi(-1)
            o.iconst_0 -> pushi(0)
            o.iconst_1 -> pushi(1)
            o.iconst_2 -> pushi(2)
            o.iconst_3 -> pushi(3)
            o.iconst_4 -> pushi(4)
            o.iconst_5 -> pushi(5)

            o.lconst_0 -> pushl(0L)
            o.lconst_1 -> pushl(1L)

            o.fconst_0 -> pushf(0.0f)
            o.fconst_1 -> pushf(1.0f)
            o.fconst_2 -> pushf(2.0f)

            o.dconst_0 -> pushd(0.0)
            o.dconst_1 -> pushd(1.0)

            o.bipush -> {
                pushi(readSByte())
            }
            o.sipush -> {
                pushi(readSShort())
            }

            o.iadd -> op2I { a, b -> a + b }
            o.isub -> op2I { a, b -> a - b }
            o.imul -> op2I { a, b -> a * b }
            o.idiv -> op2I { a, b -> a / b }
            o.irem -> op2I { a, b -> a % b }
            o.ineg -> pushi(-popi())
            o.iand -> op2I { a, b -> a and b }
            o.ior -> op2I { a, b -> a or b }
            o.ixor -> op2I { a, b -> a xor b }
            o.ishl -> op2I { a, b -> a shl b }
            o.ishr -> op2I { a, b -> a shr b }
            o.iushr -> op2I { a, b -> a ushr b }

            o.ladd -> op2L { a, b -> a + b }
            o.lsub -> op2L { a, b -> a - b }
            o.lmul -> op2L { a, b -> a * b }
            o.ldiv -> op2L { a, b -> a / b }
            o.lrem -> op2L { a, b -> a % b }
            o.lneg -> pushl(-popl())
            o.land -> op2L { a, b -> a and b }
            o.lor -> op2L { a, b -> a or b }
            o.lxor -> op2L { a, b -> a xor b }
            o.lshl -> op2L { a, b -> a shl b.toInt() }
            o.lshr -> op2L { a, b -> a shr b.toInt() }
            o.lushr -> op2L { a, b -> a ushr b.toInt() }

            o.fadd -> op2F { a, b -> a + b }
            o.fsub -> op2F { a, b -> a - b }
            o.fmul -> op2F { a, b -> a * b }
            o.fdiv -> op2F { a, b -> a / b }
            o.frem -> op2F { a, b -> a % b }
            o.fneg -> pushf(-popf())

            o.dadd -> op2D { a, b -> a + b }
            o.dsub -> op2D { a, b -> a - b }
            o.dmul -> op2D { a, b -> a * b }
            o.ddiv -> op2D { a, b -> a / b }
            o.drem -> op2D { a, b -> a % b }
            o.dneg -> pushd(-popd())

            o.iload_0, o.iload_1, o.iload_2, o.iload_3 -> loadLocalI(opcode - o.iload_0)
            o.lload_0, o.lload_1, o.lload_2, o.lload_3 -> loadLocalL(opcode - o.lload_0)
            o.fload_0, o.fload_1, o.fload_2, o.fload_3 -> loadLocalF(opcode - o.fload_0)
            o.dload_0, o.dload_1, o.dload_2, o.dload_3 -> loadLocalD(opcode - o.dload_0)

            o.istore_0, o.istore_1, o.istore_2, o.istore_3 -> storeLocalI(opcode - o.istore_0)
            o.lstore_0, o.lstore_1, o.lstore_2, o.lstore_3 -> storeLocalL(opcode - o.lstore_0)
            o.fstore_0, o.fstore_1, o.fstore_2, o.fstore_3 -> storeLocalF(opcode - o.fstore_0)
            o.dstore_0, o.dstore_1, o.dstore_2, o.dstore_3 -> storeLocalD(opcode - o.dstore_0)

            o.iload -> loadLocalI(getOpcodeOffset())
            o.lload -> loadLocalL(getOpcodeOffset())
            o.fload -> loadLocalF(getOpcodeOffset())
            o.dload -> loadLocalD(getOpcodeOffset())

            o.istore -> storeLocalI(getOpcodeOffset())
            o.lstore -> storeLocalL(getOpcodeOffset())
            o.fstore -> storeLocalF(getOpcodeOffset())
            o.dstore -> storeLocalD(getOpcodeOffset())

            o.iinc -> {
                val index = getOpcodeOffset()
                val count = if (wide) {
                    readSShort()
                } else {
                    readSByte()
                }
                val value = locals[index].toInt()
                locals[index] = (value + count).toLong()
            }

            o.baload -> arrayLoad<Byte>()
            o.caload -> arrayLoad<Char>()
            o.daload -> arrayLoad<Double>()
            o.faload -> arrayLoad<Float>()
            o.iaload -> arrayLoad<Int>()
            o.laload -> arrayLoad<Long>()
            o.saload -> arrayLoad<Short>()

            o.bastore -> arrayStore<Byte>()
            o.castore -> arrayStore<Char>()
            o.dastore -> arrayStore<Double>()
            o.fastore -> arrayStore<Float>()
            o.iastore -> arrayStore<Int>()
            o.lastore -> arrayStore<Long>()
            o.sastore -> arrayStore<Short>()

            o.wide -> {
                require(when (bytecode[offset].toInt()) {
                    o.iload, o.fload, o.aload, o.lload, o.dload,
                    o.istore, o.fstore, o.astore, o.lstore, o.dstore,
                    o.ret, o.iinc-> true
                    else -> false
                })
                wide = true
                interpret()
                wide = false
            }

            o.d2f -> pushf(popd().toFloat())
            o.d2i -> pushi(popd().let {
                if (it.isNaN()) 0 else it.roundToInt()
            })
            o.d2l -> pushl(popd().let {
                if (it.isNaN()) 0L else it.roundToLong()
            })

            o.i2b -> pushi(popi() and 0xFF)
            o.i2c -> pushi(popi() and 0xFFFF)
            o.i2d -> pushd(popi().toDouble())
            o.i2f -> pushf(popi().toFloat())
            o.i2l -> pushl(popi().toLong())
            o.i2s -> pushi(popi() and 0xFFFF)

            o.f2d -> pushd(popf().toDouble())
            o.f2i -> pushi(popf().let {
                if (it.isNaN()) 0 else it.roundToInt()
            })
            o.f2l -> pushl(popf().let {
                if (it.isNaN()) 0L else it.roundToLong()
            })

            o.l2d -> pushd(popl().toDouble())
            o.l2f -> pushf(popl().toFloat())
            o.l2i -> pushi(popd().toInt())

            o.ifeq -> branchIfI { it == 0 }
            o.ifge -> branchIfI { it >= 0 }
            o.ifgt -> branchIfI { it > 0 }
            o.ifle -> branchIfI { it <= 0 }
            o.iflt -> branchIfI { it < 0 }
            o.ifne -> branchIfI { it != 0 }

            o.goto -> {
                offset += readSShort()
            }
            o.goto_w -> {
                offset += readSInt()
            }

            o.if_icmpeq -> branchIfI2 { a, b -> a == b }
            o.if_icmpge -> branchIfI2 { a, b -> a >= b }
            o.if_icmpgt -> branchIfI2 { a, b -> a > b }
            o.if_icmple -> branchIfI2 { a, b -> a <= b }
            o.if_icmplt -> branchIfI2 { a, b -> a < b }
            o.if_icmpne -> branchIfI2 { a, b -> a != b }

            o.fcmpg -> {
                val a = popf()
                val b = popf()
                pushi(if (a.isNaN() || b.isNaN()) 1 else a.compareTo(b))
            }
            o.fcmpl -> {
                val a = popf()
                val b = popf()
                pushi(if (a.isNaN() || b.isNaN()) -1 else a.compareTo(b))
            }

            o.dcmpg -> {
                val a = popd()
                val b = popd()
                pushi(if (a.isNaN() || b.isNaN()) 1 else a.compareTo(b))
            }
            o.dcmpl -> {
                val a = popd()
                val b = popd()
                pushi(if (a.isNaN() || b.isNaN()) -1 else a.compareTo(b))
            }

            o.lcmp -> {
                val a = popl()
                val b = popl()
                pushi(a.compareTo(b))
            }

            o.nop, o.impdep1, o.impdep2, o.breakpoint -> {}

            o.pop -> {
                require(valueStackTop >= 1)
                valueStackTop -= 1
            }

            o.pop2 -> {
                require(valueStackTop >= 2)
                valueStackTop -= 2
            }

            o.swap -> {
                val a = popi()
                val b = popi()
                pushi(a)
                pushi(b)
            }

            o.dup -> {
                pushi(valueStack[valueStackTop - 1])
            }

            // This instruction is the reason the constant pool can contain java.lang.invoke.MethodType and
            // java.lang.invoke.MethodHandle
            o.invokedynamic -> throw NotImplementedError()

        }
    }
}

