// Transpiled from Kotlin to C++ using https://github.com/TlatoaniHJ/kotlin-transpiler
//
// const val MOD = 998244353L
//
// fun main() {
//     val lastPrime = IntArray(1000001)
//     for (p in 2..1000000) {
//         if (lastPrime[p] == 0) {
//             for (k in p..1000000 step p) {
//                 lastPrime[k] = p
//             }
//         }
//     }
//     repeat(nextInt()) {
//         val ns = List(3) { nextInt() }
//         val primeFactors = mutableMapOf<Int, Int>()
//         for (n in ns) {
//             var n = n
//             while (n != 1) {
//                 val p = lastPrime[n]
//                 n /= p
//                 primeFactors[p] = (primeFactors[p] ?: 0) + 1
//             }
//         }
//
//         //println("primeFactors = $primeFactors")
//
//         val secondaryPrimeFactors = mutableMapOf<Int, Int>()
//         var answer = 1L
//         for ((p, e) in primeFactors) {
//             var totient = p - 1
//             while (totient > 1) {
//                 val p = lastPrime[totient]
//                 totient /= p
//                 secondaryPrimeFactors[p] = (secondaryPrimeFactors[p] ?: 0) + 1
//             }
//
//             answer *= (p - 1).toLong()
//             answer %= MOD
//             answer *= pow(p.toLong(), -e)
//             answer %= MOD
//         }
//
//         //println("seconary pimary factors = $secondaryPrimeFactors")
//
//         for ((q, e) in secondaryPrimeFactors) {
//             var here = 1L
//             if (q !in primeFactors) {
//                 here += (pow(q.toLong(), e) - 1L) * pow(q.toLong(), -1)
//                 here %= MOD
//             }
//             here *= pow(q.toLong(), -e)
//             here %= MOD
//             answer *= here
//             answer %= MOD
//         }
//
//         println(answer)
//     }
// }
//
// fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
//
// const val MOD_TOTIENT = MOD.toInt() - 1
//
// fun pow(base: Long, power: Int): Long {
//     var e = power
//     if (e == 0) {
//         return 1L
//     }
//     e %= MOD_TOTIENT
//     if (e < 0) {
//         e += MOD_TOTIENT
//     }
//     if (e == 0 && base == 0L) {
//         return base
//     }
//     var b = base % MOD
//     var res = 1L
//     while (e > 0) {
//         if (e and 1 != 0) {
//             res *= b
//             res %= MOD
//         }
//         b *= b
//         b %= MOD
//         e = e shr 1
//     }
//     return res
// }
//
// /*
//
// Sample:
// 5
// 5 1 1
// 5 2 1
// 23 1 1
// 10 10 2
// 9 3 37
//
//
//  */

#include <bits/stdc++.h>
using namespace std;

int nextInt() { int x; cin >> x; return x; }
long long nextLong() { long long x; cin >> x; return x; }
double nextDouble() { double x; cin >> x; return x; }
float nextFloat() { float x; cin >> x; return x; }
string nextToken() { string x; cin >> x; return x; }
string nextLine() { string x; getline(cin, x); return x; }

mt19937 rng(chrono::steady_clock::now().time_since_epoch().count());
mt19937_64 rng64(chrono::steady_clock::now().time_since_epoch().count());
int randInt(int lo, int hi) { return uniform_int_distribution<int>(lo, hi)(rng); }
long long randLong(long long lo, long long hi) { return uniform_int_distribution<long long>(lo, hi)(rng64); }
double randDouble() { return uniform_real_distribution<double>(0.0, 1.0)(rng); }
const auto MOD = 998244353LL;
const auto MOD_TOTIENT = (int)(MOD) - 1;

int gcd(int a, int b);
long long pow(long long base, int power);

int gcd(int a, int b) {
    return (b == 0 ? a : [](auto _a, auto _b) -> decltype(_a) { _a = _a < 0 ? -_a : _a; _b = _b < 0 ? -_b : _b; while (_b) { auto _t = _b; _b = _a % _b; _a = _t; } return _a; }(b, a % b));
}

long long pow(long long base, int power) {
    auto e = power;
    if (e == 0) {
        return 1LL;
    }
    e %= MOD_TOTIENT;
    if (e < 0) {
        e += MOD_TOTIENT;
    }
    if (e == 0 && base == 0LL) {
        return base;
    }
    auto b = base % MOD;
    auto res = 1LL;
    while (e > 0) {
        if ((e & 1) != 0) {
            res *= b;
            res %= MOD;
        }
        b *= b;
        b %= MOD;
        e = (e >> 1);
    }
    return res;
}

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    cout << boolalpha;
    auto lastPrime = vector<int>(1000001);
    for (auto p = 2; p <= 1000000; p++) {
        if (lastPrime[p] == 0) {
            for (auto k = p; k <= 1000000; k += p) {
                lastPrime[k] = p;
            }
        }
    }
    [&]() { auto _n = nextInt();
for (int _i = 0; _i < _n; _i++) {
    auto ns = [&]() { vector<decay_t<decltype([&](int _i) { return nextInt(); }(0))>> _v; _v.reserve(3); for (int _i = 0; _i < 3; _i++) _v.push_back(nextInt()); return _v; }();
    auto primeFactors = map<int, int>();
    for (auto& n : ns) {
        // var n = n (self-shadow, skipped)
        while (n != 1) {
            const auto p = lastPrime[n];
            n /= p;
            primeFactors[p] = ([&]() { auto&& _c = primeFactors; auto&& _k = p; return _c.count(_k) ? _c[_k] : 0; }()) + 1;
        }
    }
    auto secondaryPrimeFactors = map<int, int>();
    auto answer = 1LL;
    for (auto& [p, e] : primeFactors) {
        auto totient = p - 1;
        while (totient > 1) {
            const auto p = lastPrime[totient];
            totient /= p;
            secondaryPrimeFactors[p] = ([&]() { auto&& _c = secondaryPrimeFactors; auto&& _k = p; return _c.count(_k) ? _c[_k] : 0; }()) + 1;
        }
        answer *= (long long)(p - 1);
        answer %= MOD;
        answer *= pow((long long)(p), -(e));
        answer %= MOD;
    }
    for (auto& [q, e] : secondaryPrimeFactors) {
        auto here = 1LL;
        if (!((primeFactors.count(q) > 0))) {
            here += (pow((long long)(q), e) - 1LL) * pow((long long)(q), -(1));
            here %= MOD;
        }
        here *= pow((long long)(q), -(e));
        here %= MOD;
        answer *= here;
        answer %= MOD;
    }
    cout << answer << '\n';
} }();
    return 0;
}
