import java.util.PriorityQueue
import kotlin.random.Random

var n = 0

fun main() {
    val random = Random(23)
    val out = StringBuilder()
    repeat(nextInt()) {
        n = nextInt()
        val hashes = LongArray(n + 1) { random.nextLong() }
        val l = IntArray(n + 1)
        val r = IntArray(n + 1)
        val parent = IntArray(n + 1)
        val degree = IntArray(n + 1)
        for (a in 1..n) {
            val x = nextInt()
            val y = nextInt()
            if (x > 0) {
                l[a] = x
                r[a] = y
                parent[x] = a
                parent[y] = a
                degree[a] = 2
            }
        }
        val segTrees = Array<SegmentTree?>(n + 1) { null }
        val values = IntArray(n + 1)
        var lastValue = 1
        var last: SegmentTree? = null
        val pq = PriorityQueue<Int>(object: Comparator<Int> {
            override fun compare(x: Int?, y: Int?): Int {
                var left = segTrees[x!!]
                var right = segTrees[y!!]
                return compare(left, right)
            }
        })
        for (a in 1..n) {
            if (degree[a] == 0) {
                pq.add(a)
            }
        }
        while (pq.isNotEmpty()) {
            val a = pq.remove()
            if (compare(last, segTrees[a]) != 0) {
                lastValue++
                last = segTrees[a]
            }
            values[a] = lastValue

            val p = parent[a]
            if (p > 0) {
                degree[p]--
                if (degree[p] == 0) {
                    segTrees[p] = insert(segTrees[l[p]], values[r[p]], hashes[values[r[p]]])
                    pq.add(p)
                }
            }
        }
        val answer = (1..n).sortedBy(values::get)
        out.appendLine(answer.joinToString(" "))
    }
    print(out)
}

sealed class SegmentTree(val hash: Long)
data class Internal(val left: SegmentTree?, val right: SegmentTree?): SegmentTree(left.hash() xor right.hash())
data class Leaf(val freq: Long, val baseHash: Long): SegmentTree(freq * baseHash)

fun SegmentTree?.hash() = this?.hash ?: 0L

fun insert(node: SegmentTree?, value: Int, hash: Long, segFrom: Int, segTo: Int): SegmentTree {
    if (segFrom == segTo) {
        node as Leaf?
        return Leaf((node?.freq ?: 0L) + 1L, hash)
    } else {
        val mid = (segFrom + segTo) / 2
        node as Internal?
        val left = node?.left
        val right = node?.right
        if (value <= mid) {
            return Internal(insert(left, value, hash, segFrom, mid), right)
        } else {
            return Internal(left, insert(right, value, hash, mid + 1, segTo))
        }
    }
}

fun insert(node: SegmentTree?, value: Int, hash: Long) = insert(node, value, hash, 1, n)

fun compare(node1: SegmentTree?, node2: SegmentTree?, segFrom: Int, segTo: Int): Int {
    //println("[$segFrom, $segTo]: comparing $node1 and $node2")
    if (segFrom == segTo) {
        node1 as Leaf?
        node2 as Leaf?
        return (node1?.freq ?: 0L).compareTo(node2?.freq ?: 0L)
    } else {
        val mid = (segFrom + segTo) / 2
        node1 as Internal?
        node2 as Internal?
        if (node1?.right.hash() != node2?.right.hash()) {
            return compare(node1?.right, node2?.right, mid + 1, segTo)
        } else {
            return compare(node1?.left, node2?.left, segFrom, mid)
        }
    }
}

fun compare(node1: SegmentTree?, node2: SegmentTree?): Int {
   // println("comparing $node1 and $node2")
    if (node1.hash() == node2.hash()) {
        return 0
    }
    val result = compare(node1, node2, 1, n)
   // println("result = $result")
    return result
}