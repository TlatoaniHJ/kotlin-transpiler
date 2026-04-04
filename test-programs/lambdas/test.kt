fun applyTwice(f: (Int) -> Int, x: Int): Int = f(f(x))

fun main() {
    val nums = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    // map
    val squares = nums.map { it * it }
    println(squares.joinToString(" "))

    // filter
    val evens = nums.filter { it % 2 == 0 }
    println(evens.joinToString(" "))

    // any / all / none
    println(nums.any { it > 8 })
    println(nums.all { it > 0 })
    println(nums.none { it > 10 })

    // fold / reduce
    val sum = nums.fold(0) { acc, x -> acc + x }
    println(sum)
    val product = nums.take(5).reduce { acc, x -> acc * x }
    println(product)

    // higher-order function
    println(applyTwice({ x -> x + 3 }, 10))

    // forEach
    var total = 0
    nums.forEach { total += it }
    println(total)

    // sumOf
    val sumSq = nums.sumOf { it.toLong() * it }
    println(sumSq)
}
