// Transpiled from Kotlin to C++ using https://github.com/TlatoaniHJ/kotlin-transpiler
//
// import java.util.TreeMap
// import kotlin.math.max
//
// fun main() {
//     repeat(nextInt()) {
//         val n = nextInt()
//         val adj = Array(n + 1) { mutableListOf<Int>() }
//         repeat(n - 1) {
//             val a = nextInt()
//             val b = nextInt()
//             adj[a].add(b)
//             adj[b].add(a)
//         }
//         val subtree = IntArray(n + 1) { 1 }
//         var centroid = 0
//         fun recur1(a: Int, parent: Int) {
//             for (b in adj[a]) {
//                 if (b != parent) {
//                     recur1(b, a)
//                     subtree[a] += subtree[b]
//                 }
//             }
//             if (centroid == 0 && 2 * subtree[a] >= n) {
//                 centroid = a
//             }
//         }
//         recur1(1, 0)
//         //println("centroid = $centroid")
//         val answers = LongArray(n + 1)
//         fun recur2(a: Int, parent: Int): TreeMap<Int, Long> {
//             subtree[a] = 1
//             var result = TreeMap<Int, Long>()
//             val subtreeFreqs = mutableMapOf<Int, Long>()
//             val belows = mutableListOf<Pair<Int, TreeMap<Int, Long>>>()
//             for (b in adj[a]) {
//                 if (b != parent) {
//                     var below = recur2(b, a)
//                     subtree[a] += subtree[b]
//                     answers[n - subtree[b]] += subtree[b].toLong()
//                     for ((x, f) in subtreeFreqs) {
//                         answers[n - x - subtree[b]] += f * subtree[b].toLong()
//                     }
//                     subtreeFreqs[subtree[b]] = (subtreeFreqs[subtree[b]] ?: 0L) + subtree[b].toLong()
//
//                     belows.add(Pair(b, below))
//                 }
//             }
//             for ((b, below) in belows) {
//                 var below = below
//
//                 val new = subtree[a] - subtree[b]
//                 var store = 0L
//                 while (below.isNotEmpty() && below.firstKey() < new) {
//                     store += below.remove(below.firstKey())!!
//                 }
//                 if (store > 0L) {
//                     below[new] = (below[new] ?: 0L) + store
//                 }
//                 if (below.size > result.size) {
//                     val temp = below
//                     below = result
//                     result = temp
//                 }
//                 for ((x, f) in below) {
//                     result[x] = (result[x] ?: 0L) + f
//                 }
//             }
//             result[subtree[a]] = (result[subtree[a]] ?: 0L) + 1L
//             //println("a = $a | result = $result")
//             return result
//         }
//         val freqs = mutableMapOf<Pair<Int, Int>, Long>()
//         for (b in adj[centroid]) {
//             val below = recur2(b, centroid)
//             for ((x, f) in below) {
//                 for ((p, g) in freqs) {
//                     val (subtreeSize, y) = p
//                     answers[maxOf(x, y, n - subtree[b] - subtreeSize)] += f * g
//                 }
//             }
//             for ((x, f) in below) {
//                 freqs[Pair(subtree[b], x)] = (freqs[Pair(subtree[b], x)] ?: 0L) + f
//                 answers[max(x, n - subtree[b])] += f
//             }
//         }
//         answers[n] += n.toLong()
//         println(answers.toList().subList(1, n + 1).joinToString(" "))
//     }
// }
// /*
//
// 1
// 2
// 1 2
//
//  */
//
// /*
//
//
// /Users/Tlatoani/Library/Java/JavaVirtualMachines/adopt-openjdk-11.0.11/Contents/Home/bin/java -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=51937:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8 -classpath /Users/Tlatoani/Projects/KotlinHeroes/out/production/KotlinHeroes:/Users/Tlatoani/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.9.23/kotlin-stdlib-1.9.23.jar:/Users/Tlatoani/.m2/repository/org/jetbrains/annotations/13.0/annotations-13.0.jar StatisticsOnTreeKt
// 7
// 1
// 2
// 1 2
// 3
// 1 2
// 1 3
// 4
// 1 2
// 2 3
// 3 4
// 5
// 1 2
// 2 3
// 2 4
// 2 5
// 7
// 3 4
// 1 5
// 2 3
// 2 6
// 5 2
// 7 3
// 8
// 2 1
// 3 2
// 4 1
// 5 3
// 6 2
// 7 6
// 8 1
// 1
// 1 2
// 1 2 3
// 0 2 4 4
// 0 0 6 4 5
// 0 0 0 0 12 9 7
// 0 0 0 0 0 17 11 8
//
// Process finished with exit code 0
//
//
//
//
// 1
// 4
// 1 2
// 2 3
// 3 4
//
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
int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    cout << boolalpha;
    [&]() { auto _n = nextInt();
for (int _i = 0; _i < _n; _i++) {
    const auto n = nextInt();
    auto adj = [&]() { vector<decay_t<decltype([&](int _i) { return vector<int>(); }(0))>> _v; _v.reserve(n + 1); for (int _i = 0; _i < n + 1; _i++) _v.push_back(vector<int>()); return _v; }();
    [&]() { auto _n = n - 1;
for (int _i = 0; _i < _n; _i++) {
    const auto a = nextInt();
    const auto b = nextInt();
    adj[a].push_back(b);
    adj[b].push_back(a);
} }();
    auto subtree = [&]() { vector<int> _v; _v.reserve(n + 1); for (int _i = 0; _i < n + 1; _i++) _v.push_back(1); return _v; }();
    auto centroid = 0;
    function<void(int, int)> recur1 = [&](int a, int parent) {
        for (auto& b : adj[a]) {
            if (b != parent) {
                recur1(b, a);
                subtree[a] += subtree[b];
            }
        }
        if (centroid == 0 && 2 * subtree[a] >= n) {
            centroid = a;
        }
    };
    recur1(1, 0);
    auto answers = vector<long long>(n + 1);
    function<std::map<int, long long>(int, int)> recur2 = [&](int a, int parent) {
        subtree[a] = 1;
        auto result = map<int, long long>();
        auto subtreeFreqs = map<int, long long>();
        auto belows = vector<std::pair<int, std::map<int, long long>>>();
        for (auto& b : adj[a]) {
            if (b != parent) {
                auto below = recur2(b, a);
                subtree[a] += subtree[b];
                answers[n - subtree[b]] += (long long)(subtree[b]);
                for (auto& [x, f] : subtreeFreqs) {
                    answers[n - x - subtree[b]] += f * (long long)(subtree[b]);
                }
                subtreeFreqs[subtree[b]] = ([&]() { auto&& _c = subtreeFreqs; auto&& _k = subtree[b]; return _c.count(_k) ? _c[_k] : 0LL; }()) + (long long)(subtree[b]);
                belows.push_back(make_pair(b, below));
            }
        }
        for (auto& [b, below] : belows) {
            // var below = below (self-shadow, skipped)
            const auto _new = subtree[a] - subtree[b];
            auto store = 0LL;
            while ((!below.empty()) && below.begin()->first() < _new) {
                store += ([&]() { auto&& _c = below; auto _it = find(_c.begin(), _c.end(), below.begin()->first()); if (_it != _c.end()) _c.erase(_it); }());
            }
            if (store > 0LL) {
                below[_new] = ([&]() { auto&& _c = below; auto&& _k = _new; return _c.count(_k) ? _c[_k] : 0LL; }()) + store;
            }
            if ((int)below.size() > (int)result.size()) {
                const auto temp = below;
                below = result;
                result = temp;
            }
            for (auto& [x, f] : below) {
                result[x] = ([&]() { auto&& _c = result; auto&& _k = x; return _c.count(_k) ? _c[_k] : 0LL; }()) + f;
            }
        }
        result[subtree[a]] = ([&]() { auto&& _c = result; auto&& _k = subtree[a]; return _c.count(_k) ? _c[_k] : 0LL; }()) + 1LL;
        return result;
    };
    auto freqs = map<std::pair<int, int>, long long>();
    for (auto& b : adj[centroid]) {
        const auto below = recur2(b, centroid);
        for (auto& [x, f] : below) {
            for (auto& [p, g] : freqs) {
                auto [subtreeSize, y] = p;
                answers[max({x, y, n - subtree[b] - subtreeSize})] += f * g;
            }
        }
        for (auto& [x, f] : below) {
            freqs[make_pair(subtree[b], x)] = ([&]() { auto&& _c = freqs; auto&& _k = make_pair(subtree[b], x); return _c.count(_k) ? _c[_k] : 0LL; }()) + f;
            answers[max(x, n - subtree[b])] += f;
        }
    }
    answers[n] += (long long)(n);
    [&]() { auto&& _c = ([&]() { auto&& _c = ([&]() { auto&& _c = answers; return vector<decay_t<decltype(_c.front())>>(_c.begin(), _c.end()); }()); return vector<decay_t<decltype(_c.front())>>(_c.begin() + 1, _c.begin() + n + 1); }()); for (int _i = 0; _i < (int)_c.size(); _i++) { if (_i) cout << " "; cout << _c[_i]; } cout << '\n'; }();
} }();
    return 0;
}
