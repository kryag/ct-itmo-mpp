import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * @author Kiriak Aleksandr
 */
class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        return flatCombine(element) { queue.addLast(element) }
    }

    override fun dequeue(): E? {
        return flatCombine(Dequeue) { queue.removeFirstOrNull() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> flatCombine(taskElement: Any?, action: () -> R): R {
        var taskIdx = -1
        var isInTasks = false
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                for (i in 0..<TASKS_FOR_COMBINER_SIZE) {
                    when (val cur = tasksForCombiner[i]) {
                        is Result<*>, null -> continue
                        is Dequeue -> tasksForCombiner[i] = Result(queue.removeFirstOrNull())
                        else -> tasksForCombiner[i] = Result(queue.addLast(cur as E))
                    }
                }
                val res = if (isInTasks) {
                    (tasksForCombiner.getAndSet(taskIdx, null) as Result<*>).value as R
                } else {
                    action()
                }
                combinerLock.set(false)
                return res
            }
            if (isInTasks) {
                val res = tasksForCombiner[taskIdx]
                if (res is Result<*>) {
                    tasksForCombiner[taskIdx] = null
                    return res.value as R
                }
            } else {
                taskIdx = randomCellIndex()
                if (tasksForCombiner.compareAndSet(taskIdx, null, taskElement)) {
                    isInTasks = true
                }
            }
        }
    }

    private fun randomCellIndex(): Int = ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue

private class Result<V>(
    val value: V
)
