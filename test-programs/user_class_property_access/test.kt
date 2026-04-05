// Tests that accessing properties like .length on user-defined class instances
// does NOT get mapped to .size() (which is the stdlib mapping for collections).

class Edge(val node: Int, val length: Long, val ix: Int)

class Graph(val n: Int) {
    val edges = mutableListOf<Edge>()

    fun addEdge(a: Int, b: Int, len: Long, ix: Int) {
        edges.add(Edge(b, len, ix))
    }

    fun totalLength(): Long {
        var total: Long = 0
        for (i in 0 until edges.size) {
            val edge = edges[i]
            total += edge.length
        }
        return total
    }
}

fun main() {
    val g = Graph(3)
    g.addEdge(0, 1, 100, 0)
    g.addEdge(0, 2, 200, 1)
    println(g.totalLength())
    val e = g.edges[0]
    println(e.length)
    println(e.ix)
}
