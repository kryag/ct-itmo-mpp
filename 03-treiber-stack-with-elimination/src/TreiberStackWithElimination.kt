import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * @author Kiriak Aleksandr
 */
open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = AtomicReferenceArray<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        val index = randomCellIndex()
        if (!eliminationArray.compareAndSet(index, CELL_STATE_EMPTY, element)) {
            return false
        }
        for (i in 0..<ELIMINATION_WAIT_CYCLES) {}
        return eliminationArray.getAndSet(index, CELL_STATE_EMPTY) == CELL_STATE_RETRIEVED
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val element = eliminationArray.get(index)
        if (element == CELL_STATE_EMPTY || element == CELL_STATE_RETRIEVED) {
            return null
        }
        return if (eliminationArray.compareAndSet(index, element, CELL_STATE_RETRIEVED)) {
            element as E
        } else {
            null
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.length())

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}
