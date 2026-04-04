fun main() {
    val n = nextInt()

    // inclusive range
    var sum = 0
    for (i in 1..n) {
        sum += i
    }
    println(sum)

    // until
    var prod = 1
    for (i in 1 until n) {
        prod *= i
    }
    println(prod)

    // downTo
    for (i in n downTo 1) {
        print("$i ")
    }
    println()

    // step
    for (i in 0..n step 2) {
        print("$i ")
    }
    println()

    // while
    var x = n
    while (x > 0) {
        x /= 2
    }
    println(x)

    // repeat
    repeat(3) { i ->
        println("rep $i")
    }
}
