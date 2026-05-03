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

/*

2
5 9
1 2 3
2 1 7
2 3 5
3 2 2
3 1 9
1 3 4
1 4 8
4 1 1
1 5 -1
4 9
1 2 8
2 1 7
2 3 9
3 2 8
3 1 7
1 3 9
1 4 -1
2 4 -1
3 4 -1


 */