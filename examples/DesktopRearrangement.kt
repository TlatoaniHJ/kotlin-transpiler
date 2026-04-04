import java.util.*

fun main() {
    val n = nextInt()
    val m = nextInt()
    val q = nextInt()
    val desktop = Array(n) { nextToken().toCharArray() }
    var amt = desktop.sumOf { it.count { it == '*' } }
    var res = 0
    for (j in 0 until amt) {
        val x = j / n
        val y = j % n
        if (desktop[y][x] == '.') {
            res++
        }
    }
    val out = StringBuilder()
    repeat(q) {
        val y = nextInt() - 1
        val x = nextInt() - 1
        val z = (n * x) + y
        if (desktop[y][x] == '.') {
            desktop[y][x] = '*'
            if (z < amt) {
                res--
            }
            if (desktop[amt % n][amt / n] == '.') {
                res++
            }
            amt++
        } else {
            desktop[y][x] = '.'
            if (z < amt) {
                res++
            }
            amt--
            if (desktop[amt % n][amt / n] == '.') {
                res--
            }
        }
        out.appendln(res)
    }
    print(out)
}


/*

Sample:
4 4 8
..**
.*..
*...
...*
1 3
2 3
3 1
2 3
3 4
4 3
2 3
2 2



 */