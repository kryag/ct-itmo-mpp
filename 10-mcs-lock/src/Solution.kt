import java.util.concurrent.atomic.*

/**
 * @author Kiriak Aleksandr
 */
class Solution(private val env: Environment) : Lock<Solution.Node> {
    private val tail = AtomicReference<Node?>(null)

    override fun lock(): Node {
        val curNode = Node()
        tail.getAndSet(curNode)?.link(curNode) ?: return curNode
        return curNode.also { while (it.isLocked.value) env.park() }
    }

    override fun unlock(node: Node) {
        if (node.nextNode.value == null) {
            if (tail.compareAndSet(node, null)) return
            while (node.nextNode.value == null);
        }
        node.nextNode.value!!.also {
            it.isLocked.value = false
            env.unpark(it.thread)
        }
    }

    class Node {
        val thread: Thread = Thread.currentThread()
        val isLocked = AtomicReference(true)
        val nextNode = AtomicReference<Node?>(null)

        fun link(node: Node) {
            nextNode.value = node
        }
    }
}
