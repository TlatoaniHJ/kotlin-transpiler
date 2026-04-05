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
struct LazySegmentTree;

struct LazySegmentTree {
    int treeFrom;
    int treeTo;
    std::vector<int> value;
    std::vector<int> lazy;

    LazySegmentTree(int treeFrom_, int treeTo_) : treeFrom(treeFrom_), treeTo(treeTo_) {
        const auto length = treeTo - treeFrom + 1;
        auto e = 0;
        while ((1 << e) < length) {
            e++;
        }
        value = vector<int>((1 << e + 1));
        lazy = vector<int>((1 << e + 1));
    }

    void update(int from, int to, int delta);
    int update(int from, int to, int segFrom, int segTo, int node, int delta);
    auto get(int from, int to);
    int query(int from, int to, int segFrom, int segTo, int node);
};


void LazySegmentTree::update(int from, int to, int delta) {
    if (from <= to) {
        update(from, to, treeFrom, treeTo, 1, delta);
    }
}

int LazySegmentTree::update(int from, int to, int segFrom, int segTo, int node, int delta) {
    if (from > segTo || to < segFrom) {
    } else if (from <= segFrom && to >= segTo) {
        value[node] += delta;
        lazy[node] += delta;
    } else {
        const auto mid = (segFrom + segTo) / 2;
        value[node] = lazy[node] + min(update(from, to, segFrom, mid, 2 * node, delta), update(from, to, mid + 1, segTo, 2 * node + 1, delta));
    }
    return value[node];
}

auto LazySegmentTree::get(int from, int to) {
    return query(from, to, treeFrom, treeTo, 1);
}

int LazySegmentTree::query(int from, int to, int segFrom, int segTo, int node) {
    if (from > segTo || to < segFrom) {
        return INT_MAX;
    } else if (from <= segFrom && to >= segTo) {
        return value[node];
    } else {
        const auto mid = (segFrom + segTo) / 2;
        return lazy[node] + min(query(from, to, segFrom, mid, 2 * node), query(from, to, mid + 1, segTo, 2 * node + 1));
    }
}

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    cout << boolalpha;
    [&]() { auto _n = nextInt();
for (int _i = 0; _i < _n; _i++) {
    const auto n = nextInt();
    const auto m = nextInt();
    auto seen = vector<int>(m + 1);
    auto positions = [&]() { vector<decay_t<decltype([&](int _i) { return vector<int>(n + 1); }(0))>> _v; _v.reserve(m + 1); for (int _i = 0; _i < m + 1; _i++) _v.push_back(vector<int>(n + 1)); return _v; }();
    auto ranks = [&]() { vector<decay_t<decltype([&](int _i) { return nextInt(); }(0))>> _v; _v.reserve(n * m); for (int _i = 0; _i < n * m; _i++) _v.push_back(nextInt()); return _v; }();
    auto colors = [&]() { vector<decay_t<decltype([&](int _i) { return nextInt(); }(0))>> _v; _v.reserve(n * m); for (int _i = 0; _i < n * m; _i++) _v.push_back(nextInt()); return _v; }();
    for (auto& [rank, color] : [&]() { auto& _a = ranks; auto& _b = colors; auto _n = min(_a.size(), _b.size()); vector<pair<decay_t<decltype(_a[0])>, decay_t<decltype(_b[0])>>> _r(_n); for (int _i = 0; _i < (int)_n; _i++) _r[_i] = {_a[_i], _b[_i]}; return _r; }()) {
        seen[color]++;
        positions[color][rank] = seen[color];
    }
    auto startAfter = [&]() { vector<decay_t<decltype([&](int _i) { return vector<int>(n + 2); }(0))>> _v; _v.reserve(m + 1); for (int _i = 0; _i < m + 1; _i++) _v.push_back(vector<int>(n + 2)); return _v; }();
    auto minPosition = [&]() { vector<decay_t<decltype([&](int _i) { return vector<int>(n + 2); }(0))>> _v; _v.reserve(m + 1); for (int _i = 0; _i < m + 1; _i++) _v.push_back(vector<int>(n + 2)); return _v; }();
    for (auto color = 1; color <= m; color++) {
        minPosition[color][n + 1] = n + 1;
        for (auto rank = n; rank >= 1; rank--) {
            minPosition[color][rank] = min(positions[color][rank], minPosition[color][rank + 1]);
        }
        for (auto rank = 1; rank <= n; rank++) {
            startAfter[color][rank] = (positions[color][rank] > positions[color][rank - 1] ? startAfter[color][rank - 1] : rank - 1);
        }
        startAfter[color][0] = -(1);
    }
    auto segTree = LazySegmentTree(0, n);
    auto answer = n;
    for (auto rank = 1; rank <= n; rank++) {
        for (auto color = 1; color <= m; color++) {
            segTree.update(startAfter[color][rank - 1] + 1, startAfter[color][rank], 1);
        }
        if (([&]() { auto&& _c = [&]() { vector<int> _r; for (auto _i = 1; _i <= m; _i++) _r.push_back(_i); return _r; }(); return all_of(_c.begin(), _c.end(), [&](auto color) { return minPosition[color][rank + 1] > positions[color][rank]; }); }())) {
            const auto here = segTree.get(0, rank - 1);
            answer = min(answer, here + n - rank);
            segTree.update(rank, rank, here);
        } else {
            segTree.update(rank, rank, n);
        }
        segTree.update(0, rank - 1, 1);
    }
    cout << answer - 1 << '\n';
} }();
    return 0;
}
