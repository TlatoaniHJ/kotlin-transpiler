fun main() {
    val n = nextInt()
    val freq = mutableMapOf<String, Int>()
    for (i in 0 until n) {
        val word = nextToken()
        freq[word] = (freq[word] ?: 0) + 1
    }
    val sorted = freq.entries.sortedBy { it.key }
    for ((k, v) in sorted) {
        println("$k $v")
    }
    println(freq.size)
    println(freq.containsKey("hello"))
}
