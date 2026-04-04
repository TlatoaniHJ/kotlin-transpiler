const val MOD = 1_000_000_007L

fun main() {
    val pow2 = LongArray(5001)
    pow2[0] = 1L
    for (k in 1..5000) {
        pow2[k] = (2L * pow2[k - 1]) % MOD
    }
    repeat(nextInt()) {
        val n = nextInt()
        val adj = Array(n + 1) { mutableListOf<Int>() }
        repeat(n - 1) {
            val a = nextInt()
            val b = nextInt()
            adj[a].add(b)
            adj[b].add(a)
        }
        var answer = 0L
        fun recur(a: Int, parent: Int): Int {
            var here = 1
            for (b in adj[a]) {
                if (b != parent) {
                    val below = recur(b, a)
                    answer += pow2[n] - pow2[below] - pow2[n - below] + 1L
                    answer %= MOD
                    here += below
                }
            }
            return here
        }
        recur(1, 0)

        for (r in 1..n) {
            val d = adj[r].size
            val freqs = Array(d) { IntArray(n) }
            fun recur(a: Int, parent: Int, group: Int, depth: Int) {
                freqs[group][depth]++
                for (b in adj[a]) {
                    if (b != parent) {
                        recur(b, a, group, depth + 1)
                    }
                }
            }
            for ((g, a) in adj[r].withIndex()) {
                recur(a, r, g, 1)
            }
            val disp = IntArray(d) { freqs[it].sum() }
            for (l in 1 until n) {
                for (g in 0 until d) {
                    disp[g] -= freqs[g][l - 1]
                }
                val totalDisp = disp.sum()
                for (g in 0 until d) {
                    answer += (pow2[disp[g]] - 1L) * (pow2[totalDisp - disp[g]] - 1L)
                    answer %= MOD
                }
                var subtractend = pow2[totalDisp] - 1L
                for (g in 0 until d) {
                    subtractend -= pow2[disp[g]] - 1L
                }
                answer -= 2L * subtractend
                answer %= MOD
            }
        }
        answer += MOD
        answer %= MOD
        println(answer)
    }
}

/*

Sample:
5
3
1 2
2 3
7
3 1
1 2
3 5
4 5
3 6
6 7
22
4 11
9 7
3 18
19 8
16 20
5 22
13 20
15 12
2 8
12 1
17 4
6 7
1 21
10 18
7 3
20 15
14 21
18 4
8 15
22 17
11 14
1
2
2 1


 */