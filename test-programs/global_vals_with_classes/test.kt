data class Point(val x: Int, val y: Int)

val ORIGIN = Point(0, 0)

class Grid(n: Int) {
    val cells: Array<Point>

    init {
        cells = Array(n) { ORIGIN }
    }

    fun set(index: Int, p: Point) {
        cells[index] = p
    }
}

fun main() {
    val g = Grid(3)
    println("${g.cells[0].x} ${g.cells[0].y}")
    g.set(1, Point(5, 10))
    println("${g.cells[1].x} ${g.cells[1].y}")
}
