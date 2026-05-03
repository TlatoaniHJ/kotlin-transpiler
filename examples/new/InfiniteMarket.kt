fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val m = nextInt()
        val out = Array(n + 1) { mutableListOf<Int>() }
        val support = IntArray(n + 1)
        val inEdges = Array(n + 1) { mutableListOf<Int>() }
        val from = IntArray(m)
        val to = IntArray(m)
        val back = Array(m) { mutableListOf<Int>() }
        val removed = BooleanArray(m)
        repeat(m) {
            val a = nextInt()
            from[it] = a
            val b = nextInt()
            to[it] = b
            out[a].add(it)
            inEdges[b].add(it)
            val s = nextInt() - 1
            if (s != -2) {
                support[b]++
                back[s].add(it)
            } else {
                removed[it] = true
            }
        }
        fun remove(a: Int) {
            if (support[a] == 0) {
                for (e in inEdges[a]) {
                    for (f in back[e]) {
                        val b = to[f]
                        if (!removed[f]) {
                            removed[f] = true
                            support[b]--
                            remove(b)
                        }
                    }
                }
                for (e in out[a]) {
                    val b = to[e]
                    if (!removed[e]) {
                        removed[e] = true
                        support[b]--
                        remove(b)
                    }
                }
            }
        }
        remove(n)
        var answer = "nO"
        for (a in 1..n) {
            if (support[a] != 0) {
                answer = "yEs"
            }
        }
        println(answer)
    }
}