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
    const auto m = nextInt();
    const auto q = nextInt();
    auto desktop = [&]() { vector<decay_t<decltype([&]() { auto _s = nextToken(); return vector<char>(_s.begin(), _s.end()); }())>> _v(n); for (int _i = 0; _i < n; _i++) _v[_i] = [&]() { auto _s = nextToken(); return vector<char>(_s.begin(), _s.end()); }(); return _v; }();
    auto amt = accumulate(desktop.begin(), desktop.end(), 0LL, [&](auto _s, auto& _x) { return _s + [&](auto _it) { return (int)count_if(_it.begin(), _it.end(), [&](auto _it) { return _it == '*'; }); }(_x); });
    auto res = 0;
    for (auto j = 0; j < amt; j++) {
        const auto x = j / n;
        const auto y = j % n;
        if (desktop[y][x] == '.') {
            res++;
        }
    }
    auto out = ostringstream();
    [&]() { auto _n = q;
for (int _i = 0; _i < _n; _i++) {
    const auto y = nextInt() - 1;
    const auto x = nextInt() - 1;
    const auto z = n * x + y;
    if (desktop[y][x] == '.') {
        desktop[y][x] = '*';
        if (z < amt) {
            res--;
        }
        if (desktop[amt % n][amt / n] == '.') {
            res++;
        }
        amt++;
    } else {
        desktop[y][x] = '.';
        if (z < amt) {
            res++;
        }
        amt--;
        if (desktop[amt % n][amt / n] == '.') {
            res--;
        }
    }
    { out << res << '\n'; };
} }();
    cout << out.str();
    return 0;
}
