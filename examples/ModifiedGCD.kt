import java.util.TreeSet

fun main() {
    val a = nextInt()
    val b = nextInt()
    val g = gcd(a, b)
    val divisors = TreeSet<Int>()
    for (x in 1..32000) {
        if (g % x == 0) {
            divisors.add(x)
            divisors.add(g / x)
        }
    }
    repeat(nextInt()) {
        val from = nextInt()
        val to = nextInt()
        val answer = divisors.floor(to)
        println(if (answer >= from) answer else -1)
    }
}

fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)