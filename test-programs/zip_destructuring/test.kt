fun main() {
    // Basic zip with destructuring in for loop
    val xs = listOf(1, 2, 3, 4, 5)
    val ys = listOf(10, 20, 30, 40, 50)

    for ((x, y) in xs.zip(ys)) {
        println("$x $y ${x + y}")
    }

    // Zip with different-length lists (should use min length)
    val a = listOf(100, 200)
    val b = listOf(1, 2, 3, 4)
    for ((p, q) in a.zip(b)) {
        println("$p $q")
    }

    // Zip with string lists
    val names = listOf("Alice", "Bob", "Carol")
    val scores = listOf(95, 87, 92)
    for ((name, score) in names.zip(scores)) {
        println("$name: $score")
    }

    // Zip result assigned to a variable, then destructured
    val paired = xs.zip(ys)
    for ((x, y) in paired) {
        print("${x + y} ")
    }
    println()
}
