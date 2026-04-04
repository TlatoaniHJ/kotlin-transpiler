fun main() {
    val n = nextInt()
    val xs = mutableListOf<Int>()
    for (i in 0 until n) {
        xs.add(nextInt())
    }
    println(xs.size)
    println(xs.first())
    println(xs.last())
    println(xs.lastIndex)

    // map and filter
    val doubled = xs.map { it * 2 }
    println(doubled.joinToString(" "))
    val evens = xs.filter { it % 2 == 0 }
    println(evens.size)

    // sort
    xs.sort()
    println(xs.joinToString(" "))

    // subList
    val sub = xs.subList(1, xs.size - 1)
    println(sub.joinToString(" "))

    // addAll and clear
    val ys = mutableListOf(10, 20, 30)
    xs.addAll(ys)
    println(xs.size)
    ys.clear()
    println(ys.size)
}
