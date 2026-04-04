fun minMax(xs: List<Int>): Pair<Int, Int> {
    var mn = xs[0]
    var mx = xs[0]
    for (x in xs) {
        if (x < mn) mn = x
        if (x > mx) mx = x
    }
    return Pair(mn, mx)
}

fun main() {
    val n = nextInt()
    val xs = mutableListOf<Int>()
    for (i in 0 until n) xs.add(nextInt())

    val (mn, mx) = minMax(xs)
    println("$mn $mx")

    // destructuring in for loop
    val pairs = listOf(Pair(1, 2), Pair(3, 4), Pair(5, 6))
    for ((a, b) in pairs) {
        println("$a+$b=${a + b}")
    }
}
