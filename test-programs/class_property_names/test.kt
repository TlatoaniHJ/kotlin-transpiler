class Segment(val length: Int, val size: Int) {
    fun area(): Int = length * size
}

class Container(val array: IntArray) {
    fun sum(): Int {
        var result = 0
        for (x in array) {
            result += x
        }
        return result
    }
}

fun main() {
    val s = Segment(3, 5)
    println(s.length)
    println(s.size)
    println(s.area())

    val c = Container(intArrayOf(1, 2, 3, 4, 5))
    println(c.sum())
}
