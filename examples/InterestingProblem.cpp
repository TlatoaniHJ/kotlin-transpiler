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
    const auto xs = [&]() { auto _a = vector<decltype(0)>{0}; auto _b = [&]() { vector<decay_t<decltype(nextInt())>> _v(n); for (int _i = 0; _i < n; _i++) _v[_i] = nextInt(); return _v; }(); _a.insert(_a.end(), _b.begin(), _b.end()); return _a; }();
    auto subDP = [&]() { vector<decay_t<decltype([&]() { vector<int> _v(n + 1); for (int _i = 0; _i < n + 1; _i++) _v[_i] = n; return _v; }())>> _v(n + 1); for (int _i = 0; _i < n + 1; _i++) _v[_i] = [&]() { vector<int> _v(n + 1); for (int _i = 0; _i < n + 1; _i++) _v[_i] = n; return _v; }(); return _v; }();
    for (auto l = n; l >= 1; l--) {
        for (auto r = l + 1; r <= n; r += 2) {
            const auto diff = l - xs[l];
            if (diff >= 0 && diff % 2 == 0) {
                for (auto k = l + 1; k <= r; k += 2) {
                    const auto inside = (k == l + 1 ? 0 : subDP[l + 1][k - 1]);
                    if (inside <= diff) {
                        const auto next = (k == r ? 0 : subDP[k + 1][r]);
                        subDP[l][r] = min(subDP[l][r], max({diff, inside, next - (k - l + 1)}));
                    }
                }
            }
        }
    }
    auto dp = vector<int>(n + 1);
    for (auto r = 1; r <= n; r++) {
        dp[r] = dp[r - 1];
        for (auto l = r - 1; l >= 1; l -= 2) {
            if (subDP[l][r] <= dp[l - 1]) {
                dp[r] = max(dp[r], dp[l - 1] + r - l + 1);
            }
        }
    }
    cout << dp[n] / 2 << '\n';
} }();
    return 0;
}
