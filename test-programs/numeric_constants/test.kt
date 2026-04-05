fun main() {
    // Int constants
    println(Int.MAX_VALUE)
    println(Int.MIN_VALUE)

    // Long constants
    println(Long.MAX_VALUE)
    println(Long.MIN_VALUE)

    // Use in expressions
    var best = Int.MAX_VALUE
    val xs = listOf(5, 3, 8, 1, 7)
    for (x in xs) {
        if (x < best) best = x
    }
    println(best)

    var worst = Int.MIN_VALUE
    for (x in xs) {
        if (x > worst) worst = x
    }
    println(worst)

    // Long constants in expressions
    var longBest = Long.MAX_VALUE
    val big = listOf(1000000000L, 2000000000L, 500000000L)
    for (x in big) {
        if (x < longBest) longBest = x
    }
    println(longBest)
}
