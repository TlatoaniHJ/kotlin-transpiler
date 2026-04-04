fun main() {
    val a = nextInt()
    val b = nextInt()
    println(a + b)
    println(a - b)
    println(a * b)
    println(a / b)
    println(a % b)
    println(maxOf(a, b))
    println(minOf(a, b))
    println(Math.abs(a - b))
    val x = a.toLong() * b.toLong()
    println(x)
}
