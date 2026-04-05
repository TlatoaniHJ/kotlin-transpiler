// Tests that out-of-line method declarations have explicit return types.
// Class A calls methods of class B (which is defined later in the out-of-line defs).
// If the declarations use `auto`, the compiler will reject them.

class Segment(val length: Int) {
    lateinit var tree: SegTree

    fun value(): Int = tree.query(0) + length
}

class SegTree(val size: Int) {
    val data = IntArray(size)

    fun query(index: Int): Int = data[index]

    fun update(index: Int, v: Int) {
        data[index] = v
    }
}

fun main() {
    val tree = SegTree(5)
    tree.update(2, 10)
    val seg = Segment(3)
    seg.tree = tree
    println(seg.value())
    println(tree.query(2))
}
