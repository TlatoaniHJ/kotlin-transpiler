import kotlin.math.max
import kotlin.math.min

fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val adj = Array(n + 1) { mutableListOf<Int>() }
        repeat(n - 1) {
            val a = nextInt()
            val b = nextInt()
            adj[a].add(b)
            adj[b].add(a)
        }
        val degree = IntArray(n + 1) { adj[it].size }
        val amts = IntArray(n + 1)
        val dist = IntArray(n + 1) { 1 }
        val q = ArrayDeque((1..n).filter { degree[it] == 1 })
        while (q.isNotEmpty()) {
            val a = q.removeFirst()
            amts[dist[a]]++
            for (b in adj[a]) {
                degree[b]--
                if (degree[b] == 1) {
                    dist[b] = dist[a] + 1
                    q.add(b)
                }
            }
        }
        val maxDegrees = IntArray(n + 1)
        for (a in (1..n).sortedByDescending(dist::get)) {
            for (b in adj[a]) {
                degree[b]++
                maxDegrees[dist[a]] = max(maxDegrees[dist[a]], degree[b])
            }
        }
        for (x in n downTo 1) {
            maxDegrees[x - 1] = max(maxDegrees[x - 1], maxDegrees[x])
        }
        for (x in 1..n) {
            amts[x] += amts[x - 1]
        }
        val answers = IntArray((2 * n) + 1) { n }
        for (x in 0 until n) {
            val oddColors = amts[x] + 1
            answers[oddColors] = min(answers[oddColors], (2 * x) + 1)
            val evenColors = amts[x] + maxDegrees[x + 1]
            answers[evenColors] = min(answers[evenColors], (2 * x) + 2)
        }
        for (x in (2 * n) downTo 1) {
            answers[x - 1] = min(answers[x - 1], answers[x])
        }
        println((1 until n).map(answers::get).joinToString(" "))
    }
}

/*

Sample:
3
6
3 4
6 1
3 2
3 1
4 5
8
8 6
7 4
8 5
2 7
3 2
5 2
1 2
3
1 2
2 3


 */