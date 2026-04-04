import java.util.TreeSet
import java.util.TreeMap

fun main() {
    val n = nextInt()
    val ts = TreeSet<Int>()
    for (i in 0 until n) ts.add(nextInt())

    println(ts.size)
    println(ts.first())
    println(ts.last())

    val q = nextInt()
    repeat(q) {
        val x = nextInt()
        // ceiling: smallest >= x
        val ceil = ts.ceiling(x)
        // floor: largest <= x
        val fl = ts.floor(x)
        println("$ceil $fl")
    }

    val tm = TreeMap<Int, String>()
    tm[1] = "one"
    tm[2] = "two"
    tm[3] = "three"
    for ((k, v) in tm) println("$k:$v")
}
