import kotlin.math.min

fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val m = nextInt()
        val seen = IntArray(m + 1)
        val positions = Array(m + 1) { IntArray(n + 1) }
        val ranks = List(n * m) { nextInt() }
        val colors = List(n * m) { nextInt() }
        for ((rank, color) in ranks.zip(colors)) {
            seen[color]++
            positions[color][rank] = seen[color]
        }
        val startAfter = Array(m + 1) { IntArray(n + 2) }
        val minPosition = Array(m + 1) { IntArray(n + 2) }
        for (color in 1..m) {
            minPosition[color][n + 1] = n + 1
            for (rank in n downTo 1) {
                minPosition[color][rank] = min(positions[color][rank], minPosition[color][rank + 1])
            }
            for (rank in 1..n) {
                startAfter[color][rank] = if (positions[color][rank] > positions[color][rank - 1]) {
                    startAfter[color][rank - 1]
                } else {
                    rank - 1
                }
            }
            startAfter[color][0] = -1
        }
        val segTree = LazySegmentTree(0, n)
        var answer = n
        for (rank in 1..n) {
            for (color in 1..m) {
                segTree.update(startAfter[color][rank - 1] + 1, startAfter[color][rank], 1)
            }
            if ((1..m).all { color -> minPosition[color][rank + 1] > positions[color][rank] }) {
                val here = segTree[0, rank - 1]
                answer = min(answer, here + (n - rank))
                segTree.update(rank, rank, here)
            } else {
                segTree.update(rank, rank, n)
            }
            segTree.update(0, rank - 1, 1)
        }
        println(answer - 1)
    }
}

class LazySegmentTree(val treeFrom: Int, val treeTo: Int) {
    val value: IntArray
    val lazy: IntArray

    init {
        val length = treeTo - treeFrom + 1
        var e = 0
        while (1 shl e < length) {
            e++
        }
        value = IntArray(1 shl (e + 1))
        lazy = IntArray(1 shl (e + 1))
    }

    fun update(from: Int, to: Int, delta: Int) {
        if (from <= to) {
            update(from, to, treeFrom, treeTo, 1, delta)
        }
    }

    fun update(from: Int, to: Int, segFrom: Int, segTo: Int, node: Int, delta: Int): Int {
        if (from > segTo || to < segFrom) {

        } else if (from <= segFrom && to >= segTo) {
            value[node] += delta
            lazy[node] += delta
        } else {
            val mid = (segFrom + segTo) / 2
            value[node] = lazy[node] + min(
                update(from, to, segFrom, mid, 2 * node, delta),
                update(from, to, mid + 1, segTo, (2 * node) + 1, delta)
            )
        }
        return value[node]
    }

    operator fun get(from: Int, to: Int) = query(from, to, treeFrom, treeTo, 1)

    fun query(from: Int, to: Int, segFrom: Int, segTo: Int, node: Int): Int {
        if (from > segTo || to < segFrom) {
            return Int.MAX_VALUE
        } else if (from <= segFrom && to >= segTo) {
            return value[node]
        } else {
            val mid = (segFrom + segTo) / 2
            return lazy[node] + min(
                query(from, to, segFrom, mid, 2 * node),
                query(from, to, mid + 1, segTo, (2 * node) + 1)
            )
        }
    }
}
