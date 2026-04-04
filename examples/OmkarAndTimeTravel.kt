const val MOD = 1000000007L

fun main() {
    val n = nextInt()
    val left = IntArray(n + 1)
    val right = IntArray(n + 1)
    for (j in 1..n) {
        val a = nextInt()
        val b = nextInt()
        left[j] = a
        right[j] = b
    }
    val sSize = nextInt()
    val s = List(sSize) { nextInt() }.sortedBy { right[it] }
    val next = IntArray(n + 1)
    var containing = n + 1
    for (j in s.reversed()) {
        if (containing == n + 1 || left[containing] < left[j] && right[j] < right[containing]) {
            next[j] = containing
            containing = j
        }
    }
    val bit = BinaryIndexTree(1, 2 * n)
    var answer = 0L
    for (j in (1..n).sortedBy { right[it] }) {
        if (next[j] != 0) {
            if (next[j] == n + 1) {
                answer += 1L + bit[1, 2 * n]
            } else {
                answer += 1L + bit[left[next[j]], right[next[j]]]
            }
            answer %= MOD
        }
        bit.update(left[j], 1L + bit[left[j], right[j]])
    }
    answer += MOD
    answer %= MOD
    println(answer)
}

class BinaryIndexTree(val treeFrom: Int, treeTo: Int) {
    val value = LongArray(treeTo - treeFrom + 2)

    fun update(index: Int, delta: Long) {
        var i = index + 1 - treeFrom
        while (i < value.size) {
            value[i] += delta
            value[i] %= MOD
            i += i and -i
        }
    }

    fun query(to: Int): Long {
        var res = 0L
        var i = to + 1 - treeFrom
        while (i > 0) {
            res += value[i]
            res %= MOD
            i -= i and -i
        }
        return res
    }

    operator fun get(from: Int, to: Int) = if (to < from) 0L else query(to) - query(from - 1)
}


/*

Sample:
16
31 32
3 26
17 19
4 24
1 28
15 21
12 16
18 29
20 23
7 8
11 14
9 22
6 30
5 10
25 27
2 13
6
3 8 2 5 12 11


 */