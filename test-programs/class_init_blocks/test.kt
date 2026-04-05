class SegTree(n: Int) {
    val size: Int
    val tree: IntArray

    init {
        var s = 1
        while (s < n) {
            s *= 2
        }
        size = s
        tree = IntArray(size * 2)
    }

    fun update(index: Int, value: Int) {
        var i = index + size
        tree[i] = value
        i = i shr 1
        while (i > 0) {
            tree[i] = tree[2 * i] + tree[(2 * i) + 1]
            i = i shr 1
        }
    }

    fun query(l: Int, r: Int): Int {
        var left = l + size
        var right = r + size + 1
        var result = 0
        while (left < right) {
            if (left and 1 == 1) {
                result += tree[left]
                left++
            }
            if (right and 1 == 1) {
                right--
                result += tree[right]
            }
            left = left shr 1
            right = right shr 1
        }
        return result
    }
}

fun main() {
    val n = 5
    val seg = SegTree(n)
    for (i in 0 until n) {
        seg.update(i, i + 1)
    }
    println(seg.query(0, 4))
    println(seg.query(1, 3))
    println(seg.query(2, 2))
    seg.update(2, 10)
    println(seg.query(0, 4))
}
