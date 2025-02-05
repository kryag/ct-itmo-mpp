/**
 * Lamport's monotonic clock
 *
 * @author Kiriak Aleksandr
 */
class Solution : MonotonicClock {
    /**
    Store two copies of the clock: [l1, m1, r] and [l2, m2, r].
    As an optimization, we can store only one right digit.
    */
    private var l1 by RegularInt(0)
    private var l2 by RegularInt(0)

    private var m1 by RegularInt(0)
    private var m2 by RegularInt(0)

    private var r by RegularInt(0)

    /**
     * Write the time to the second copy from left to right,
     * then write the second copy to the first copy from right to left.
     */
    override fun write(time: Time) {
        l2 = time.d1
        m2 = time.d2

        r = time.d3

        m1 = m2
        l1 = l2
    }

    /**
     * Read the first copy from left to right,
     * then read the second copy from right to left.
     */
    override fun read(): Time {
        val x1 = l1
        val y1 = m1

        val z = r

        val y2 = m2
        val x2 = l2

        return Time(x2,
            if (x1 == x2) y2 else 0,
            if (x1 == x2 && y1 == y2) z else 0
        )
    }
}
