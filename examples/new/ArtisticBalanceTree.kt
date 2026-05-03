fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val m = nextInt()
        val xs = List(n) { nextLong() }
        val odds = (0 until n step 2).map(xs::get).sortedDescending()
        val evens = (1 until n step 2).map(xs::get).sortedDescending()
        var numOdds = 0
        var numEvens = 0
        var answer = xs.sum()
        repeat(m) {
            val operation = nextInt()
            if (operation % 2 == 0) {
                if (numEvens < evens.size && (evens[numEvens] > 0L || numEvens == 0)) {
                    answer -= evens[numEvens]
                }
                numEvens++
            } else {
                if (numOdds < odds.size && (odds[numOdds] > 0L || numOdds == 0)) {
                    answer -= odds[numOdds]
                }
                numOdds++
            }
        }
        println(answer)
    }
}