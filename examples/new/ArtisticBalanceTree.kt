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

/*

6
7 4
1 2 3 4 5 6 7
1 2 3 4
7 4
1 -2 3 4 -5 -6 -7
7 6 5 4
7 5
21 -45 234 -8 423 12 -987
6 6 6 6 6
7 5
-21 45 -234 8 -423 -12 987
7 7 7 7 7
7 3
-1 2 -3 4 5 6 7
1 2 3
7 3
-1 -2 -3 -4 -5 -6 -7
1 2 3


 */