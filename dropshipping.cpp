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
    const auto n = nextInt();
    const auto x = nextInt();
    const auto k = nextInt();
    auto requested = [&]() { vector<decay_t<decltype(nextInt())>> _v(n); for (int _i = 0; _i < n; _i++) _v[_i] = nextInt(); return _v; }();
    auto fulfilled = vector<bool>(n);
    auto answer = 0LL;
    auto minPQ = [&]() { auto _cmp = [&](const auto& _a, const auto& _b) { return [&](auto _it) { return requested[_it]; }(_a) < [&](auto _it) { return requested[_it]; }(_b); }; return priority_queue<int, vector<int>, decltype(_cmp)>(_cmp); }();
    auto maxPQ = [&]() { auto _cmp = [&](const auto& _a, const auto& _b) { return [&](auto _it) { return requested[_it]; }(_a) > [&](auto _it) { return requested[_it]; }(_b); }; return priority_queue<int, vector<int>, decltype(_cmp)>(_cmp); }();
    for (auto j = max(0, n - k); j <= n - 1; j++) {
        minPQ.push(j);
        maxPQ.push(j);
    }
    for (auto t = n - 1; t >= 0; t--) {
        if (t >= k) {
            minPQ.push(t - k);
            maxPQ.push(t - k);
        }
        if (t + 1 % x + 1 == 0) {
            while (fulfilled[maxPQ.top()]) {
                ([&]() { auto _v = maxPQ.top(); maxPQ.pop(); return _v; }());
            }
            const auto j = ([&]() { auto _v = maxPQ.top(); maxPQ.pop(); return _v; }());
            answer += (long long)(requested[j] / 2);
            fulfilled[j] = true;
        } else {
            while (fulfilled[minPQ.top()]) {
                ([&]() { auto _v = minPQ.top(); minPQ.pop(); return _v; }());
            }
            const auto j = ([&]() { auto _v = minPQ.top(); minPQ.pop(); return _v; }());
            fulfilled[j] = true;
        }
    }
    cout << answer << '\n';
    return 0;
}
