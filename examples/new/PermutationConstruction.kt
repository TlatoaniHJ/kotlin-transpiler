fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val xs = List(n) { nextLong() }
        val sums = LongArray(n)
        for (j in 1 until n) {
            sums[j] = sums[j - 1] + xs[j - 1]
        }
        val indices = (0 until n).sortedByDescending(sums::get)
        val answers = IntArray(n)
        for ((x, j) in indices.withIndex()) {
            answers[j] = x + 1
        }
        println(answers.joinToString(" "))
    }
}

/*


7
1
0
2
1000000000 -1000000000
3
1 2 3
4
-1 -2 -3 -4
5
-1 2 -3 2 -1
6
1 -1 3 -4 1 -3
7
-3 -2 -1 4 -1 -2 -3



 */