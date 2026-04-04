fun main() {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    fun lcm(a: Int, b: Int): Long = a.toLong() * b / gcd(a, b)

    val t = nextInt()
    repeat(t) {
        val a = nextInt()
        val b = nextInt()
        println("${gcd(a, b)} ${lcm(a, b)}")
    }
}
