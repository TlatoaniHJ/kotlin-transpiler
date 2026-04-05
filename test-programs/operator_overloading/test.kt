data class Vec2(val x: Int, val y: Int) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Int) = Vec2(x * scale, y * scale)
}

fun main() {
    val a = Vec2(1, 2)
    val b = Vec2(3, 4)
    val c = a + b
    println("${c.x} ${c.y}")
    val d = b - a
    println("${d.x} ${d.y}")
    val e = a * 3
    println("${e.x} ${e.y}")
}
