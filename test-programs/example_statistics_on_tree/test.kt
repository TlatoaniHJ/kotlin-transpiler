import java.util.TreeMap
import kotlin.math.max

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
        val subtree = IntArray(n + 1) { 1 }
        var centroid = 0
        fun recur1(a: Int, parent: Int) {
            for (b in adj[a]) {
                if (b != parent) {
                    recur1(b, a)
                    subtree[a] += subtree[b]
                }
            }
            if (centroid == 0 && 2 * subtree[a] >= n) {
                centroid = a
            }
        }
        recur1(1, 0)
        val answers = LongArray(n + 1)
        fun recur2(a: Int, parent: Int): TreeMap<Int, Long> {
            subtree[a] = 1
            var result = TreeMap<Int, Long>()
            val subtreeFreqs = mutableMapOf<Int, Long>()
            val belows = mutableListOf<Pair<Int, TreeMap<Int, Long>>>()
            for (b in adj[a]) {
                if (b != parent) {
                    var below = recur2(b, a)
                    subtree[a] += subtree[b]
                    answers[n - subtree[b]] += subtree[b].toLong()
                    for ((x, f) in subtreeFreqs) {
                        answers[n - x - subtree[b]] += f * subtree[b].toLong()
                    }
                    subtreeFreqs[subtree[b]] = (subtreeFreqs[subtree[b]] ?: 0L) + subtree[b].toLong()

                    belows.add(Pair(b, below))
                }
            }
            for ((b, below) in belows) {
                var below = below

                val new = subtree[a] - subtree[b]
                var store = 0L
                while (below.isNotEmpty() && below.firstKey() < new) {
                    store += below.remove(below.firstKey())!!
                }
                if (store > 0L) {
                    below[new] = (below[new] ?: 0L) + store
                }
                if (below.size > result.size) {
                    val temp = below
                    below = result
                    result = temp
                }
                for ((x, f) in below) {
                    result[x] = (result[x] ?: 0L) + f
                }
            }
            result[subtree[a]] = (result[subtree[a]] ?: 0L) + 1L
            return result
        }
        val freqs = mutableMapOf<Pair<Int, Int>, Long>()
        for (b in adj[centroid]) {
            val below = recur2(b, centroid)
            for ((x, f) in below) {
                for ((p, g) in freqs) {
                    val (subtreeSize, y) = p
                    answers[maxOf(x, y, n - subtree[b] - subtreeSize)] += f * g
                }
            }
            for ((x, f) in below) {
                freqs[Pair(subtree[b], x)] = (freqs[Pair(subtree[b], x)] ?: 0L) + f
                answers[max(x, n - subtree[b])] += f
            }
        }
        answers[n] += n.toLong()
        println(answers.toList().subList(1, n + 1).joinToString(" "))
    }
}
