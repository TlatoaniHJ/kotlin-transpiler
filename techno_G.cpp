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
struct BinaryIndexTree {
    const int treeFrom;
    int treeTo;
    decltype(vector<long>(treeTo - treeFrom + 2)) value = vector<long>(treeTo - treeFrom + 2);

    BinaryIndexTree(int treeFrom_, int treeTo_) : treeFrom(treeFrom_) {
    }

    auto update(int index, long long delta) {
        auto i = index + 1 - treeFrom;
        while (i < (int)value.size()) {
            value[i] += delta;
            value[i] %= MOD;
            i += (i & -(i));
        }
    }

    long long query(int to) {
        auto res = 0LL;
        auto i = to + 1 - treeFrom;
        while (i > 0) {
            res += value[i];
            res %= MOD;
            i -= (i & -(i));
        }
        return res;
    }

    auto get(int from, int to) {
        return (to < from ? 0LL : query(to) - query(from - 1));
    }

};

const auto MOD = 1000000007LL;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    cout << boolalpha;
    const auto n = nextInt();
    auto left = vector<int>(n + 1);
    auto right = vector<int>(n + 1);
    for (auto j = 1; j <= n; j++) {
        const auto a = nextInt();
        const auto b = nextInt();
        left[j] = a;
        right[j] = b;
    }
    const auto sSize = nextInt();
    const auto s = [&]() { auto _v = List(sSize, [&]() { return nextInt(); }); sort(_v.begin(), _v.end(), [&](const auto& _a, const auto& _b) { return [&](auto _it) { return right[_it]; }(_a) < [&](auto _it) { return right[_it]; }(_b); }); return _v; }();
    auto next = vector<int>(n + 1);
    auto containing = n + 1;
    for (auto& j : string(s.rbegin(), s.rend())) {
        if (containing == n + 1 || left[containing] < left[j] && right[j] < right[containing]) {
            next[j] = containing;
            containing = j;
        }
    }
    auto bit = BinaryIndexTree(1, 2 * n);
    auto answer = 0LL;
    for (auto& j : [&]() { auto _v = /* range 1..n */; sort(_v.begin(), _v.end(), [&](const auto& _a, const auto& _b) { return [&](auto _it) { return right[_it]; }(_a) < [&](auto _it) { return right[_it]; }(_b); }); return _v; }()) {
        if (next[j] != 0) {
            if (next[j] == n + 1) {
                answer += 1LL + bit[1];
            } else {
                answer += 1LL + bit[left[next[j]]];
            }
            answer %= MOD;
        }
        bit.update(left[j], 1LL + bit[left[j]]);
    }
    answer += MOD;
    answer %= MOD;
    cout << answer << '\n';
    return 0;
}
