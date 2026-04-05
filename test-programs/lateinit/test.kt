class Container(val id: Int) {
    lateinit var label: String
    lateinit var neighbor: Container
}

fun main() {
    val a = Container(1)
    val b = Container(2)
    a.label = "first"
    b.label = "second"
    a.neighbor = b
    b.neighbor = a
    println(a.label)
    println(a.neighbor.label)
    println(b.neighbor.id)
}
