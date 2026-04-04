fun classify(n: Int): String {
    return when {
        n < 0  -> "negative"
        n == 0 -> "zero"
        n < 10 -> "small"
        n < 100 -> "medium"
        else   -> "large"
    }
}

fun grade(score: Int): String = when (score) {
    in 90..100 -> "A"
    in 80..89  -> "B"
    in 70..79  -> "C"
    else       -> "F"
}

fun main() {
    val n = nextInt()
    println(classify(n))
    val abs = if (n >= 0) n else -n
    println(abs)
    val score = nextInt()
    println(grade(score))
}
