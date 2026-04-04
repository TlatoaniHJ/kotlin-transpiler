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
int solveOuter(std::string s);
int solve(int minBalanced, int finalBalanced, int minDeformed, int finalDeformed, int sign);

int solveOuter(std::string s) {
    auto minBalanced = 0;
    auto finalBalanced = 0;
    for (auto& chara : s) {
        if (chara == '(') {
            finalBalanced++;
        } else if (chara == ')') {
            finalBalanced--;
        }
        minBalanced = min(minBalanced, finalBalanced);
    }
    auto minDeformed = 0;
    auto maxDeformed = 0;
    auto finalDeformed = 0;
    auto sign = 1;
    for (auto& chara : s) {
        if (chara == '(') {
            finalDeformed += sign;
        } else if (chara == ')') {
            sign *= -(1);
        }
        minDeformed = min(minDeformed, finalDeformed);
        maxDeformed = max(maxDeformed, finalDeformed);
    }
    auto answer = solve(minBalanced, finalBalanced, minDeformed, finalDeformed, sign);
    for (auto inBetween = 0; inBetween < answer; inBetween++) {
        const auto here = solve(min(-(1), -(1) + inBetween + minBalanced), -(1) + inBetween + finalBalanced, -(inBetween) - maxDeformed, -(inBetween) - finalDeformed, -(sign)) + 1 + inBetween;
        answer = min(answer, here);
    }
    return answer;
}

int solve(int minBalanced, int finalBalanced, int minDeformed, int finalDeformed, int sign) {
    auto beginning = max(-(minBalanced), -(minDeformed));
    auto realFinalDeformed = beginning + finalDeformed;
    const auto parity = (sign == 1 ? 0 : 1);
    auto end = realFinalDeformed + beginning + finalBalanced - 1;
    auto extra = 0;
    if (end < 0) {
        end++;
        extra++;
    }
    if (parity + end % 2 == 0) {
        end++;
        extra++;
    }
    auto here = beginning + end + realFinalDeformed + extra;
    if (sign == 1 && beginning + finalBalanced == 0 && realFinalDeformed > 0) {
        here += 4;
    }
    return here;
}

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    cout << boolalpha;
    [&]() { auto _n = nextInt();
for (int _i = 0; _i < _n; _i++) {
    const auto n = nextInt();
    const auto s = nextToken();
    cout << min(solveOuter(s), 1 + solveOuter([&]() -> std::string { ostringstream _ss; _ss << ")"; _ss << s; return _ss.str(); }())) << '\n';
} }();
    return 0;
}
