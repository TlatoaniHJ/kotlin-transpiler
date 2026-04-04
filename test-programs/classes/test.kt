class Counter(var value: Int = 0) {
    fun increment() { value++ }
    fun decrement() { value-- }
    fun add(n: Int) { value += n }
    fun get(): Int = value
}

class Stack<T> {
    private val data = mutableListOf<T>()
    fun push(x: T) { data.add(x) }
    fun pop(): T = data.removeLast()
    fun peek(): T = data.last()
    fun isEmpty(): Boolean = data.isEmpty()
    fun size(): Int = data.size
}

fun main() {
    val c = Counter(10)
    c.increment()
    c.increment()
    c.add(5)
    c.decrement()
    println(c.get())

    val s = Stack<Int>()
    s.push(1)
    s.push(2)
    s.push(3)
    println(s.peek())
    println(s.pop())
    println(s.size())
}
