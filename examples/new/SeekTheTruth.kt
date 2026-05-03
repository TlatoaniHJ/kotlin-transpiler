fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        println(0)
        fun insert(x: Long): Int {
            println("I $x")
            return nextInt()
        }
        fun query(y: Long): Int {
            println("Q $y")
            return nextInt()
        }
        fun answer(k: Int, c: Long) {
            println("A $k $c")
        }
        if (insert(0) == 1) {
            insert((1L shl n) - 1L)
            var upper = (1L shl n) - 1L
            var lower = 1L
            while (upper > lower) {
                val mid = (upper + lower + 1L) / 2L
                if (query(mid) == 1) {
                    lower = mid
                } else {
                    upper = mid - 1L
                }
            }
            answer(1, upper)
        } else {
            var upper = (1L shl n) - 1L
            var lower = 1L
            while (upper > lower) {
                val mid = (upper + lower + 1L) / 2L
                if (query(mid) == 1) {
                    lower = mid
                } else {
                    upper = mid - 1L
                }
            }
            val c = upper
            if (c == (1L shl n) - 1L) {
                if (insert(1) == 2) {
                    answer(2, c)
                } else {
                    answer(3, c)
                }
            } else {
                insert((1L shl n) - 1L)
                if (query((1L shl n) - 1L) == 1) {
                    answer(2, c)
                } else {
                    answer(3, c)
                }
            }
        }
    }
}

/*

1
2
1
2
1
1

 */