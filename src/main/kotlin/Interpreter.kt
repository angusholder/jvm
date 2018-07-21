import kotlin.math.roundToInt
import kotlin.math.roundToLong
import Opcodes as o

class Interpreter(
        private val bytecode: ByteArray,
        private val stackSize: Int,
        private val localsCount: Int
) {
    private val stack = IntArray(stackSize)
    private var stackTop = 0
    private val locals = LongArray(localsCount)
    private var offset = 0
    private var wide = false

    fun pushi(i: Int) {
        stack[stackTop++] = i
    }
    fun popi() = stack[--stackTop]
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

    inline fun <reified T> pop(): T {
        return when (T::class) {
            Int::class -> popi() as T
            Long::class -> popl() as T
            Float::class -> popf() as T
            Double::class -> popd() as T
            else -> throw IllegalStateException()
        }
    }

    inline fun <reified T> push(t: T) {
        when (T::class) {
            Int::class -> pushi(t as Int)
            Long::class -> pushl(t as Long)
            Float::class -> pushf(t as Float)
            Double::class -> pushd(t as Double)
            else -> throw IllegalStateException()
        }
    }

    inline fun <reified T> op1(action: (T) -> T) {
        val a = pop<T>()
        val result = action(a)
        push(result)
    }

    inline fun <reified T> op2(action: (T, T) -> T) {
        val a = pop<T>()
        val b = pop<T>()
        val result = action(a, b)
        push(result)
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

    private inline fun <reified T> loadLocalConstOffset(opcode: Int, base: Int) {
        loadLocal<T>(opcode - base)
    }

    private inline fun <reified T> storeLocalConstOffset(opcode: Int, base: Int) {
        storeLocal<T>(opcode - base)
    }

    private fun getOpcodeOffset(): Int {
        return if (wide) {
            readUShort()
        } else {
            readUByte()
        }
    }

    private inline fun <reified T> loadLocalVarOffset() {
        val offset = getOpcodeOffset()
        loadLocal<T>(offset)
    }

    private inline fun <reified T> storeLocalVarOffset() {
        val offset = getOpcodeOffset()
        storeLocal<T>(offset)
    }

    private inline fun <reified T> loadLocal(offset: Int) {
        val t: T = when (T::class) {
            Int::class -> locals[offset].toInt() as T
            Long::class -> locals[offset] as T
            Float::class -> Float.fromBits(locals[offset].toInt()) as T
            Double::class -> Double.fromBits(locals[offset]) as T
            else -> throw IllegalStateException()
        }
        push(t)
    }

    private inline fun <reified T> storeLocal(offset: Int) {
        val t: T = pop()
        when (T::class) {
            Int::class -> {
                locals[offset] = (t as Int).toLong()
            }
            Long::class -> {
                locals[offset] = t as Long
            }
            Float::class -> {
                locals[offset] = (t as Float).toRawBits().toLong() // TODO: Check it doesn't sign extend?
            }
            Double::class -> {
                locals[offset] = (t as Double).toRawBits()
            }
            else -> throw IllegalStateException()
        }
    }

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

            o.iadd -> op2<Int> { a, b -> a + b }
            o.isub -> op2<Int> { a, b -> a - b }
            o.imul -> op2<Int> { a, b -> a * b }
            o.idiv -> op2<Int> { a, b -> a / b }
            o.irem -> op2<Int> { a, b -> a % b }
            o.ineg -> op1<Int> { a -> -a }
            o.iand -> op2<Int> { a, b -> a and b }
            o.ior -> op2<Int> { a, b -> a or b }
            o.ixor -> op2<Int> { a, b -> a xor b }
            o.ishl -> op2<Int> { a, b -> a shl b }
            o.ishr -> op2<Int> { a, b -> a shr b }
            o.iushr -> op2<Int> { a, b -> a ushr b }

            o.ladd -> op2<Long> { a, b -> a + b }
            o.lsub -> op2<Long> { a, b -> a - b }
            o.lmul -> op2<Long> { a, b -> a * b }
            o.ldiv -> op2<Long> { a, b -> a / b }
            o.lrem -> op2<Long> { a, b -> a % b }
            o.lneg -> op1<Long> { a -> -a }
            o.land -> op2<Long> { a, b -> a and b }
            o.lor -> op2<Long> { a, b -> a or b }
            o.lxor -> op2<Long> { a, b -> a xor b }
            o.lshl -> op2<Long> { a, b -> a shl b.toInt() }
            o.lshr -> op2<Long> { a, b -> a shr b.toInt() }
            o.lushr -> op2<Long> { a, b -> a ushr b.toInt() }

            o.fadd -> op2<Float> { a, b -> a + b }
            o.fsub -> op2<Float> { a, b -> a - b }
            o.fmul -> op2<Float> { a, b -> a * b }
            o.fdiv -> op2<Float> { a, b -> a / b }
            o.frem -> op2<Float> { a, b -> a % b }
            o.fneg -> op1<Float> { a -> -a }

            o.dadd -> op2<Double> { a, b -> a + b }
            o.dsub -> op2<Double> { a, b -> a - b }
            o.dmul -> op2<Double> { a, b -> a * b }
            o.ddiv -> op2<Double> { a, b -> a / b }
            o.drem -> op2<Double> { a, b -> a % b }
            o.dneg -> op1<Double> { a -> -a }

            o.iload_0, o.iload_1, o.iload_2, o.iload_3 -> loadLocalConstOffset<Int>(opcode, o.iload_0)
            o.lload_0, o.lload_1, o.lload_2, o.lload_3 -> loadLocalConstOffset<Long>(opcode, o.lload_0)
            o.fload_0, o.fload_1, o.fload_2, o.fload_3 -> loadLocalConstOffset<Float>(opcode, o.fload_0)
            o.dload_0, o.dload_1, o.dload_2, o.dload_3 -> loadLocalConstOffset<Double>(opcode, o.dload_0)

            o.istore_0, o.istore_1, o.istore_2, o.istore_3 -> storeLocalConstOffset<Int>(opcode, o.iload_0)
            o.lstore_0, o.lstore_1, o.lstore_2, o.lstore_3 -> storeLocalConstOffset<Long>(opcode, o.lload_0)
            o.fstore_0, o.fstore_1, o.fstore_2, o.fstore_3 -> storeLocalConstOffset<Float>(opcode, o.fload_0)
            o.dstore_0, o.dstore_1, o.dstore_2, o.dstore_3 -> storeLocalConstOffset<Double>(opcode, o.dload_0)

            o.iload -> loadLocalVarOffset<Int>()
            o.lload -> loadLocalVarOffset<Long>()
            o.fload -> loadLocalVarOffset<Float>()
            o.dload -> loadLocalVarOffset<Double>()

            o.istore -> storeLocalVarOffset<Int>()
            o.lstore -> storeLocalVarOffset<Long>()
            o.fstore -> storeLocalVarOffset<Float>()
            o.dstore -> storeLocalVarOffset<Double>()

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

            o.nop -> {}

            o.pop -> {
                require(stackTop >= 1)
                stackTop -= 1
            }

            o.pop2 -> {
                require(stackTop >= 2)
                stackTop -= 2
            }

            o.swap -> {
                val a = popi()
                val b = popi()
                pushi(a)
                pushi(b)
            }

            o.dup -> {
                pushi(stack[stackTop - 1])
            }

        }
    }
}

