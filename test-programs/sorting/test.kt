data class Point(val x: Int, val y: Int)

fun main() {
    val n = nextInt()
    val pts = mutableListOf<Point>()
    for (i in 0 until n) {
        val x = nextInt()
        val y = nextInt()
        pts.add(Point(x, y))
    }

    // sortBy
    pts.sortBy { it.x }
    for (p in pts) println("${p.x} ${p.y}")

    // sortByDescending
    pts.sortByDescending { it.y }
    for (p in pts) println("${p.x} ${p.y}")

    // sorted, sortedBy
    val nums = mutableListOf(5, 2, 8, 1, 9, 3)
    val sorted = nums.sorted()
    println(sorted.joinToString(" "))
    val sortedBy = nums.sortedByDescending { it }
    println(sortedBy.joinToString(" "))

    // sortWith via compareBy
    pts.sortWith(compareBy { it.x })
    for (p in pts) println("${p.x} ${p.y}")
}
