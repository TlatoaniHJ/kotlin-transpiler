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
