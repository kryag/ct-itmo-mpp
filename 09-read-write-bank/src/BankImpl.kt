import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Bank implementation.
 *
 * @author Kiriak Aleksandr
 */
class BankImpl(n: Int) : Bank {
    private val accounts: Array<Account> = Array(n) { Account() }

    override val numberOfAccounts: Int
        get() = accounts.size

    override fun getAmount(index: Int): Long {
        val account = accounts[index]
        val lock = account.lock
        return lock.readLock().withLock { accounts[index].amount }
    }

    override val totalAmount: Long
        get() {
            accounts.forEach { it.lock.readLock().lock() }
            val totalAmount = accounts.sumOf { account -> account.amount }
            accounts.reversed().forEach { it.lock.readLock().unlock() }
            return totalAmount
        }

    override fun deposit(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        val lock = account.lock
        return lock.writeLock().withLock {
            check(!(amount > Bank.MAX_AMOUNT || account.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        val lock = account.lock
        return lock.writeLock().withLock {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }

        val from = accounts[fromIndex]
        val to = accounts[toIndex]

        val isDirectOrder = fromIndex < toIndex
        val firstLock = if (isDirectOrder) from.lock else to.lock
        val secondLock = if (isDirectOrder) to.lock else from.lock

        firstLock.writeLock().withLock {
            secondLock.writeLock().withLock {
                check(amount <= from.amount) { "Underflow" }
                check(!(amount > Bank.MAX_AMOUNT || to.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
                from.amount -= amount
                to.amount += amount
            }
        }
    }

    override fun consolidate(fromIndices: List<Int>, toIndex: Int) {
        require(fromIndices.isNotEmpty()) { "empty fromIndices" }
        require(fromIndices.distinct() == fromIndices) { "duplicates in fromIndices" }
        require(toIndex !in fromIndices) { "toIndex in fromIndices" }

        val fromList = fromIndices.map { accounts[it] }
        val lockList = (fromIndices + toIndex).sorted().map { accounts[it].lock }
        val to = accounts[toIndex]

        lockList.forEach { it.writeLock().lock() }

        val amount = fromList.sumOf { it.amount }
        if (to.amount + amount > Bank.MAX_AMOUNT) {
            lockList.reversed().forEach { it.writeLock().unlock() }
            throw IllegalStateException("Overflow")
        }
        for (from in fromList) from.amount = 0
        to.amount += amount

        lockList.reversed().forEach { it.writeLock().unlock() }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0

        /**
         * Lock for thread-safe.
         */
        val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    }
}