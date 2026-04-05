// Tests that a local variable named "node" inside a method does not get
// treated as a pointer just because another class has a pointer member named "node".

class Edge(val node: TreeNode, val weight: Int)

class TreeNode(val id: Int) {
    val edges = mutableListOf<Edge>()

    fun sumWeights(): Int {
        var node = 0  // local int named "node" — must NOT be treated as a pointer
        for (e in edges) {
            node = node + e.weight
        }
        return node
    }

    fun halvePath(): Int {
        var node = 16
        node = node shr 1
        node = node shr 2
        return node
    }
}

fun main() {
    val a = TreeNode(1)
    val b = TreeNode(2)
    a.edges.add(Edge(b, 10))
    a.edges.add(Edge(b, 20))
    println(a.sumWeights())
    println(a.halvePath())
}
