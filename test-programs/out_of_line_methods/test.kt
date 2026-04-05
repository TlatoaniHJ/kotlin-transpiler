class B(val id: Int) {
    lateinit var ref: A

    fun describe(): String = "${ref.name} -> $id"
}

class A(val name: String) {
    lateinit var link: B

    fun describe(): String = "$name -> ${link.id}"
}

fun main() {
    val a = A("hello")
    val b = B(42)
    a.link = b
    b.ref = a
    println(a.describe())
    println(b.describe())
}
