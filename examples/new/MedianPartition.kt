import kotlin.math.max

fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val xs = List(n) { nextInt() }
        val median = xs.sorted()[n / 2]
        val dp = IntArray(n + 1) { -1 }
        dp[0] = 0
        for (r in 1..n) {
            var less = 0
            var more = 0
            for (l in r - 1 downTo 0) {
                when {
                    xs[l] < median -> less++
                    xs[l] > median -> more++
                }
                if ((r - l) % 2 == 1 && less <= (r - l) / 2 && more <= (r - l) / 2 && dp[l] != -1) {
                    dp[r] = max(dp[r], dp[l] + 1)
                }
            }
        }
        println(dp[n])
    }
}

/*

10
5
3 3 2 4 3
7
9 5 7 7 4 7 7
9
1 1 1 1 1 1 1 1 1
1
5
3
1 2 3
3
2 2 2
5
1 2 3 4 5
5
2 1 3 2 2
7
2 2 1 2 3 2 2
9
2 1 2 3 2 1 2 3 2


 */