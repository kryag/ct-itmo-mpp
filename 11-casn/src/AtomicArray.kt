import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val arr = Array(size) { Reference(initialValue) }

    fun get(index: Int) = arr[index].value

    fun set(index: Int, value: E) {
        arr[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) = arr[index].compareAndSet(expected, update)

    fun cas2(
        indexA: Int, expectA: E, updateA: E,
        indexB: Int, expectB: E, updateB: E
    ): Boolean {
        if (indexA == indexB) {
            return expectA == expectB && cas(indexA, expectA, updateB)
        }

        val isDirectOrder = indexA < indexB
        val elems = if (isDirectOrder) arr[indexA] to arr[indexB] else arr[indexB] to arr[indexA]
        val expects = if (isDirectOrder) expectA to expectB else expectB to expectA
        val updates = if (isDirectOrder) updateA to updateB else updateB to updateA

        val desc = CASNDescriptor(
            elems.first, expects.first, updates.first,
            elems.second, expects.second, updates.second
        )

        if (!elems.first.compareAndSet(expects.first, desc)) {
            return false
        }

        desc.complete()
        return desc.outcome.value == Decision.SUCCESS
    }

    private inner class Reference<T>(value: T) {
        val v = atomic<Any?>(value)

        var value: T
            @Suppress("UNCHECKED_CAST")
            get() = v.loop {
                when (it) {
                    is AtomicArray<*>.Descriptor -> it.complete()
                    else -> return it as T
                }
            }
            set(update) {
                v.loop {
                    when (it) {
                        is AtomicArray<*>.Descriptor -> it.complete()
                        else -> if (v.compareAndSet(it, update)) return
                    }
                }
            }

        fun compareAndSet(expected: Any?, update: Any?): Boolean {
            while (true) {
                if (value != expected) return false
                if (v.compareAndSet(expected, update)) return true
            }
        }
    }

    private fun <A, B> dcssMod(
        a: Reference<A>, expectA: Any?, updateA: Any?,
        b: Reference<B>, expectB: Any?
    ): Boolean {
        val desc = DCSSDescriptor(a, expectA, updateA, b, expectB)
        if (!a.compareAndSet(expectA, desc)) {
            return false
        }
        desc.complete()
        return desc.outcome.value == Decision.SUCCESS
    }

    private abstract inner class Descriptor {
        val outcome: Reference<Decision> = Reference(Decision.UNDECIDED)
        abstract fun complete()
    }

    private inner class DCSSDescriptor<A, B>(
        val a: Reference<A>, val expectA: Any?, val updateA: Any?,
        val b: Reference<B>, val expectB: Any?
    ) : Descriptor() {
        override fun complete() {
            val decision = if (b.value == expectB) Decision.SUCCESS else Decision.FAIL
            outcome.v.compareAndSet(Decision.UNDECIDED, decision)
            val update = if (outcome.value == Decision.SUCCESS) updateA else expectA
            a.v.compareAndSet(this, update)
        }
    }

    private inner class CASNDescriptor<A, B>(
        val a: Reference<A>, val expectA: Any?, val updateA: Any?,
        val b: Reference<B>, val expectB: Any?, val updateB: Any?
    ) : Descriptor() {
        override fun complete() {
            val isOnDesc = b.v.value == this || dcssMod(b, expectB, this, outcome, Decision.UNDECIDED)
            val decision = if (isOnDesc) Decision.SUCCESS else Decision.FAIL
            outcome.v.compareAndSet(Decision.UNDECIDED, decision)
            val success = outcome.value == Decision.SUCCESS
            val updA = if (success) updateA else expectA
            val updB = if (success) updateB else expectB
            a.v.compareAndSet(this, updA)
            b.v.compareAndSet(this, updB)
        }
    }

    private enum class Decision {
        SUCCESS, FAIL, UNDECIDED
    }
}
