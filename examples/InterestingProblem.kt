import kotlin.math.max
import kotlin.math.min

fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val xs = listOf(0) + List(n) { nextInt() }
        val subDP = Array(n + 1) { IntArray(n + 1) { n } }
        for (l in n downTo 1) {
            for (r in l + 1..n step 2) {
                val diff = l - xs[l]
                if (diff >= 0 && diff % 2 == 0) {
                    for (k in l + 1..r step 2) {
                        val inside = if (k == l + 1) 0 else subDP[l + 1][k - 1]
                        if (inside <= diff) {
                            val next = if (k == r) 0 else subDP[k + 1][r]
                            subDP[l][r] = min(
                                subDP[l][r], maxOf(
                                    diff,
                                    inside,
                                    next - (k - l + 1)
                                )
                            )
                        }
                    }
                }
            }
        }
        val dp = IntArray(n + 1)
        for (r in 1..n) {
            dp[r] = dp[r - 1]
            for (l in r - 1 downTo 1 step 2) {
                if (subDP[l][r] <= dp[l - 1]) {
                    dp[r] = max(dp[r], dp[l - 1] + (r - l + 1))
                }
            }
        }
        println(dp[n] / 2)
    }
}

/*

6
5
1 5 3 2 4
8
2 1 3 4 5 6 7 8
3
1 2 3
4
1 2 4 4
5
4 4 1 3 5
1
1


 */