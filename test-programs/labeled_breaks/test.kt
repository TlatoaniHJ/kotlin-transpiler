fun main() {
    val n = nextInt()
    val m = nextInt()

    // labeled break
    var found = false
    outer@ for (i in 0 until n) {
        for (j in 0 until m) {
            if (i + j == nextInt()) {
                println("found $i $j")
                found = true
                break@outer
            }
        }
    }
    if (!found) println("not found")
}
