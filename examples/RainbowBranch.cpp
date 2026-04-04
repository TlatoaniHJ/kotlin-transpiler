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
    auto out = ostringstream();
    [&]() { auto _n = nextInt();
for (int _i = 0; _i < _n; _i++) {
    const auto n = nextInt();
    auto adj = [&]() { vector<decay_t<decltype(vector<int>())>> _v(n + 1); for (int _i = 0; _i < n + 1; _i++) _v[_i] = vector<int>(); return _v; }();
    [&]() { auto _n = n - 1;
for (int _i = 0; _i < _n; _i++) {
    const auto a = nextInt();
    const auto b = nextInt();
    adj[a].push_back(b);
    adj[b].push_back(a);
} }();
    auto degree = [&]() { vector<int> _v(n + 1); for (int _it = 0; _it < n + 1; _it++) _v[_it] = (int)adj[_it].size(); return _v; }();
    auto amts = vector<int>(n + 1);
    auto dist = [&]() { vector<int> _v(n + 1); for (int _i = 0; _i < n + 1; _i++) _v[_i] = 1; return _v; }();
    auto q = [&]() { auto _v = [&]() { auto _r = vector<decay_t<decltype([&]() { vector<int> _r; for (auto _i = 1; _i <= n; _i++) _r.push_back(_i); return _r; }().front())>>(); for (auto& _it : [&]() { vector<int> _r; for (auto _i = 1; _i <= n; _i++) _r.push_back(_i); return _r; }()) if ([&](auto _it) { return degree[_it] == 1; }(_it)) _r.push_back(_it); return _r; }(); return deque<decay_t<decltype(_v.front())>>(_v.begin(), _v.end()); }();
    while ((!q.empty())) {
        const auto a = ([&]() { auto _v = q.front(); q.erase(q.begin()); return _v; }());
        amts[dist[a]]++;
        for (auto& b : adj[a]) {
            degree[b]--;
            if (degree[b] == 1) {
                dist[b] = dist[a] + 1;
                q.push_back(b);
            }
        }
    }
    auto maxDegrees = vector<int>(n + 1);
    for (auto& a : [&]() { auto _v = [&]() { vector<int> _r; for (auto _i = 1; _i <= n; _i++) _r.push_back(_i); return _r; }(); sort(_v.begin(), _v.end(), [&](const auto& _a, const auto& _b) { return [&](auto _it) { return dist[_it]; }(_a) > [&](auto _it) { return dist[_it]; }(_b); }); return _v; }()) {
        for (auto& b : adj[a]) {
            degree[b]++;
            maxDegrees[dist[a]] = max(maxDegrees[dist[a]], degree[b]);
        }
    }
    for (auto x = n; x >= 1; x--) {
        maxDegrees[x - 1] = max(maxDegrees[x - 1], maxDegrees[x]);
    }
    for (auto x = 1; x <= n; x++) {
        amts[x] += amts[x - 1];
    }
    auto answers = [&]() { vector<int> _v(2 * n + 1); for (int _i = 0; _i < 2 * n + 1; _i++) _v[_i] = n; return _v; }();
    for (auto x = 0; x < n; x++) {
        const auto oddColors = amts[x] + 1;
        answers[oddColors] = min(answers[oddColors], 2 * x + 1);
        const auto evenColors = amts[x] + maxDegrees[x + 1];
        answers[evenColors] = min(answers[evenColors], 2 * x + 2);
    }
    for (auto x = 2 * n; x >= 1; x--) {
        answers[x - 1] = min(answers[x - 1], answers[x]);
    }
    { out << [&]() -> string { ostringstream _ss; auto&& _c = [&]() { auto _r = vector<decay_t<decltype([&](auto _it) { return answers[_it]; }([&]() { vector<int> _r; for (auto _i = 1; _i < n; _i++) _r.push_back(_i); return _r; }().front()))>>(); _r.reserve([&]() { vector<int> _r; for (auto _i = 1; _i < n; _i++) _r.push_back(_i); return _r; }().size()); for (auto& _it : [&]() { vector<int> _r; for (auto _i = 1; _i < n; _i++) _r.push_back(_i); return _r; }()) _r.push_back([&](auto _it) { return answers[_it]; }(_it)); return _r; }(); for (int _i = 0; _i < (int)_c.size(); _i++) { if (_i) _ss << " "; _ss << _c[_i]; } return _ss.str(); }() << '\n'; };
} }();
    cout << out.str();
    return 0;
}
