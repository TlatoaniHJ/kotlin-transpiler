class TreeNode(val value: Int) {
    var left: TreeNode? = null
    var right: TreeNode? = null
}

fun main() {
    val root = TreeNode(1)
    root.left = TreeNode(2)
    root.right = TreeNode(3)
    root.left!!.left = TreeNode(4)

    println(root.value)
    println(root.left!!.value)
    println(root.right!!.value)
    println(root.left!!.left!!.value)
    println(root.left!!.right == null)

    // Traverse and sum
    var sum = 0
    var node: TreeNode? = root
    // Walk left
    while (node != null) {
        sum += node.value
        node = node.left
    }
    println(sum)
}
