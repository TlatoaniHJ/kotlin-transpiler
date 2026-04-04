const val MOD = 998244353L

fun main() {
    val lastPrime = IntArray(1000001)
    for (p in 2..1000000) {
        if (lastPrime[p] == 0) {
            for (k in p..1000000 step p) {
                lastPrime[k] = p
            }
        }
    }
    repeat(nextInt()) {
        val ns = List(3) { nextInt() }
        val primeFactors = mutableMapOf<Int, Int>()
        for (n in ns) {
            var n = n
            while (n != 1) {
                val p = lastPrime[n]
                n /= p
                primeFactors[p] = (primeFactors[p] ?: 0) + 1
            }
        }

        //println("primeFactors = $primeFactors")

        val secondaryPrimeFactors = mutableMapOf<Int, Int>()
        var answer = 1L
        for ((p, e) in primeFactors) {
            var totient = p - 1
            while (totient > 1) {
                val p = lastPrime[totient]
                totient /= p
                secondaryPrimeFactors[p] = (secondaryPrimeFactors[p] ?: 0) + 1
            }

            answer *= (p - 1).toLong()
            answer %= MOD
            answer *= pow(p.toLong(), -e)
            answer %= MOD
        }

        //println("seconary pimary factors = $secondaryPrimeFactors")

        for ((q, e) in secondaryPrimeFactors) {
            var here = 1L
            if (q !in primeFactors) {
                here += (pow(q.toLong(), e) - 1L) * pow(q.toLong(), -1)
                here %= MOD
            }
            here *= pow(q.toLong(), -e)
            here %= MOD
            answer *= here
            answer %= MOD
        }

        println(answer)
    }
}

fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

const val MOD_TOTIENT = MOD.toInt() - 1

fun pow(base: Long, power: Int): Long {
    var e = power
    if (e == 0) {
        return 1L
    }
    e %= MOD_TOTIENT
    if (e < 0) {
        e += MOD_TOTIENT
    }
    if (e == 0 && base == 0L) {
        return base
    }
    var b = base % MOD
    var res = 1L
    while (e > 0) {
        if (e and 1 != 0) {
            res *= b
            res %= MOD
        }
        b *= b
        b %= MOD
        e = e shr 1
    }
    return res
}

/*

Sample:
5
5 1 1
5 2 1
23 1 1
10 10 2
9 3 37


 */