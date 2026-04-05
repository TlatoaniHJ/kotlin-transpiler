// Tests that Array(n) { classInstance } creates pointer arrays,
// so mutations to the original are reflected through the array reference.

class Counter(val id: Int) {
    var count = 0
}

fun main() {
    val counters = Array(3) { Counter(it) }
    counters[0].count = 10
    counters[1].count = 20
    counters[2].count = 30

    // Create a reference array pointing to existing counters
    val refs = Array(2) { counters[0] }
    refs[0] = counters[1]
    refs[1] = counters[2]

    // Mutate via refs — should be visible through counters since they're references
    refs[0].count += 5
    refs[1].count += 7

    println(counters[0].count)
    println(counters[1].count)
    println(counters[2].count)
}
