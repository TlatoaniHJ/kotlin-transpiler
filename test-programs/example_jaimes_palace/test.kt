import java.util.Stack

fun main() {
    val p = nextInt()
    val d = nextInt()
    val stack = Stack<Int>()
    repeat(p) {
        stack.push(0)
    }
    repeat(d) {
        val k = nextInt()
        val used = List(k) { stack.pop() + 1 }.sortedDescending()
        for (u in used) {
            stack.push(u)
        }
    }
    println(stack.max())
}

/*

Sample:
10 4
5 3 5 2


 */