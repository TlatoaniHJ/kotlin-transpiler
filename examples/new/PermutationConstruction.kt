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