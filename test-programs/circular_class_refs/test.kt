class Edge(val target: Vertex, val weight: Int)

class Vertex(val id: Int) {
    val edges = mutableListOf<Edge>()
}

fun main() {
    val n = 4
    val vertices = Array(n) { Vertex(it) }
    vertices[0].edges.add(Edge(vertices[1], 10))
    vertices[1].edges.add(Edge(vertices[0], 10))
    vertices[1].edges.add(Edge(vertices[2], 20))
    vertices[2].edges.add(Edge(vertices[1], 20))
    vertices[2].edges.add(Edge(vertices[3], 30))
    vertices[3].edges.add(Edge(vertices[2], 30))

    for (v in vertices) {
        val total = v.edges.sumOf { it.weight }
        println("${v.id}: $total")
    }
}
