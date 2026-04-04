import kotlin.math.max
import kotlin.math.min

fun main() {
    repeat(nextInt()) {
        val n = nextInt()
        val s = nextToken()

        println(min(solveOuter(s), 1 + solveOuter(")$s")))
    }
}

fun solveOuter(s: String): Int {
    var minBalanced = 0
    var finalBalanced = 0
    for (chara in s) {
        when (chara) {
            '(' -> finalBalanced++
            ')' -> finalBalanced--
        }
        minBalanced = min(minBalanced, finalBalanced)
    }

    var minDeformed = 0
    var maxDeformed = 0
    var finalDeformed = 0
    var sign = 1
    for (chara in s) {
        when (chara) {
            '(' -> finalDeformed += sign
            ')' -> sign *= -1
        }
        minDeformed = min(minDeformed, finalDeformed)
        maxDeformed = max(maxDeformed, finalDeformed)
    }

    //println("minBalanced = $minBalanced, finalBalanced = $finalBalanced")
    //println("minDeformed = $minDeformed, maxDeformed = $maxDeformed, finalDeformed = $finalDeformed, sign = $sign")

    var answer = solve(minBalanced, finalBalanced, minDeformed, finalDeformed, sign)
    //println("answer here = $answer")
    for (inBetween in 0 until answer) {
        val here = solve(
            min(-1, -1 + inBetween + minBalanced),
            -1 + inBetween + finalBalanced,
            -inBetween - maxDeformed,
            -inBetween - finalDeformed,
            -sign
        ) + 1 + inBetween
        answer = min(answer, here)
    }
    return answer
}

fun solve(minBalanced: Int, finalBalanced: Int, minDeformed: Int, finalDeformed: Int, sign: Int): Int {
    //println("minBalanced = $minBalanced, finalBalanced = $finalBalanced, minDeformed = $minDeformed, finalDeformed = $finalDeformed, sign = $sign")
    var beginning = max(-minBalanced, -minDeformed)
    var realFinalDeformed = beginning + finalDeformed
    val parity = if (sign == 1) 0 else 1

    // ()((())

    var end = realFinalDeformed + beginning + finalBalanced - 1
    //println("end = $end")
    var extra = 0
    if (end < 0) {
        end++
        extra++
    }
    if ((parity + end) % 2 == 0) {
        end++
        extra++
    }
    var here = beginning + end + realFinalDeformed + extra
    if (sign == 1 && beginning + finalBalanced == 0 && realFinalDeformed > 0) {
        here += 4
    }

    return here
}

/*

Sample:
3
3
()(
1
)
7
(())())


 */