class Item(val index: Int) {
    var value = 0
}

fun main() {
    val items = Array(5, ::Item)
    for (item in items) {
        item.value = item.index * 10
    }
    for (item in items) {
        println("${item.index} ${item.value}")
    }
}
