import java.util.PriorityQueue
import kotlin.math.max

fun main() {
    val n = nextInt()
    val x = nextInt()
    val k = nextInt()
    val requested = List(n) { nextInt() }
    val fulfilled = BooleanArray(n)
    var answer = 0L
    val minPQ = PriorityQueue<Int>(compareBy(requested::get))
    val maxPQ = PriorityQueue<Int>(compareByDescending(requested::get))
    for (j in max(0, n - k)..n - 1) {
        minPQ.add(j)
        maxPQ.add(j)
    }
    for (t in n - 1 downTo 0) {
        if (t >= k) {
            minPQ.add(t - k)
            maxPQ.add(t - k)
        }
        if ((t + 1) % (x + 1) == 0) {
            while (fulfilled[maxPQ.peek()]) {
                maxPQ.remove()
            }
            val j = maxPQ.remove()
            answer += (requested[j] / 2).toLong()
            fulfilled[j] = true
        } else {
            while (fulfilled[minPQ.peek()]) {
                minPQ.remove()
            }
            val j = minPQ.remove()
            fulfilled[j] = true
        }
    }
    println(answer)
}

/*

Sample:
3 1 1
6 4 14


 */