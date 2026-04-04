data class Point(val x: Int, val y: Int)

fun dist2(a: Point, b: Point): Long {
    val dx = (a.x - b.x).toLong()
    val dy = (a.y - b.y).toLong()
    return dx * dx + dy * dy
}

fun main() {
    val n = nextInt()
    val pts = mutableListOf<Point>()
    for (i in 0 until n) {
        val x = nextInt()
        val y = nextInt()
        pts.add(Point(x, y))
    }
    pts.sortWith(compareBy({ it.x }, { it.y }))
    for (p in pts) println("${p.x} ${p.y}")
    // destructuring
    val (x0, y0) = pts[0]
    println("$x0 $y0")
}
