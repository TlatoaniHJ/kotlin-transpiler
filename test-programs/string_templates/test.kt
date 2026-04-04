fun main() {
    val name = nextToken()
    val age = nextInt()
    println("Hello, $name!")
    println("You are $age years old.")
    println("Next year you will be ${age + 1}.")
    val sb = StringBuilder()
    sb.append("built:")
    sb.append(age)
    println(sb.toString())
}
