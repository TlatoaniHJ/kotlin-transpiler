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
const auto HORIZONTAL_IDENTITY = Horval(0, 0, 0);
const auto VERTICAL_IDENTITY = Verval(0, 0, 0, 0);

struct Node {
    const int ix;
    Node parent;
    long long parentLength = -(1);
    int subTreeSize = 1;
    vector<Edge> edges = vector<Edge>();
    Node* bestChild = nullptr;
    Hortree hortree;
    Vertree vertree;
    decltype(-(1)) treeix = -(1);

    Node(int ix_) : ix(ix_) {
    }

    auto update() {
        if (parent.bestChild == this) {
            vertree.update(treeix, Verval(parentLength, hortree.longest() + parentLength, hortree.longest(), hortree.posAnswer()));
        } else {
            parent.hortree.update(treeix, Horval(max(vertree.longest(), hortree.longest()) + parentLength, 0, max({vertree.posAnswer(), hortree.posAnswer(), vertree.longest() + hortree.longest()})));
        }
    }

    auto toString() override {
        return [&]() -> std::string { ostringstream _ss; _ss << "nodes["; _ss << ix; _ss << "]"; return _ss.str(); }();
    }

};

struct Edge {
    const Node node;
    const long long length;
    const int ix;

    Edge(Node node_, long long length_, int ix_) : node(node_), length(length_), ix(ix_) {
    }

};

struct Horval {
    long long longest1;
    long long longest2;
    long long posAnswer;

    Horval(long long longest1_, long long longest2_, long long posAnswer_) : longest1(longest1_), longest2(longest2_), posAnswer(posAnswer_) {
    }

    Horval plus(Horval other) {
        if (longest1 > other.longest1) {
            return Horval(longest1, max(longest2, other.longest1), max(posAnswer, other.posAnswer));
        } else {
            return Horval(other.longest1, max(longest1, other.longest2), max(posAnswer, other.posAnswer));
        }
    }

    bool operator==(const Horval& o) const { return longest1 == o.longest1 && longest2 == o.longest2 && posAnswer == o.posAnswer; }
    bool operator<(const Horval& o) const { return longest1 < o.longest1 || (longest1 == o.longest1 && (longest2 < o.longest2 || (longest2 == o.longest2 && (posAnswer < o.posAnswer)))); }
};

struct Verval {
    long long length;
    long long longest;
    long long longestToUse;
    long long posAnswer;

    Verval(long long length_, long long longest_, long long longestToUse_, long long posAnswer_) : length(length_), longest(longest_), longestToUse(longestToUse_), posAnswer(posAnswer_) {
    }

    auto times(Verval other) {
        return Verval(length + (int)other.size(), max(longest, other.longest + length), max(longestToUse + (int)other.size(), other.longestToUse), max({posAnswer, other.posAnswer, longestToUse + other.longest}));
    }

    bool operator==(const Verval& o) const { return length == o.length && longest == o.longest && longestToUse == o.longestToUse && posAnswer == o.posAnswer; }
    bool operator<(const Verval& o) const { return length < o.length || (length == o.length && (longest < o.longest || (longest == o.longest && (longestToUse < o.longestToUse || (longestToUse == o.longestToUse && (posAnswer < o.posAnswer)))))); }
};

struct Hortree {
    const int length;
    const std::vector<Horval> array;

    Hortree(int amt) {
        auto l = 1;
        while (l < amt) {
            l *= 2;
        }
        length = l;
        _array = [&]() { vector<decay_t<decltype(HORIZONTAL_IDENTITY)>> _v(length * 2); for (int _i = 0; _i < length * 2; _i++) _v[_i] = HORIZONTAL_IDENTITY; return _v; }();
    }

    auto update(int index, Horval newVal) {
        auto node = index + length;
        _array[node] = newVal;
        node = (node >> 1);
        while (node > 0) {
            _array[node] = _array[2 * node] + _array[2 * node + 1];
            node = (node >> 1);
        }
    }

    auto longest() {
        return _array[1].longest1;
    }

    auto posAnswer() {
        return max(_array[1].longest1 + _array[1].longest2, _array[1].posAnswer);
    }

};

struct Vertree {
    const Node topNode;
    const int length;
    const std::vector<Verval> array;

    Vertree(Node topNode_, int amt) : topNode(topNode_) {
        auto l = 1;
        while (l < amt) {
            l *= 2;
        }
        length = l;
        _array = [&]() { vector<decay_t<decltype(VERTICAL_IDENTITY)>> _v(length * 2); for (int _i = 0; _i < length * 2; _i++) _v[_i] = VERTICAL_IDENTITY; return _v; }();
    }

    auto update(int index, Verval newVal) {
        auto node = index + length;
        _array[node] = newVal;
        node = (node >> 1);
        while (node > 0) {
            _array[node] = _array[2 * node] * _array[2 * node + 1];
            node = (node >> 1);
        }
    }

    auto longest() {
        return _array[1].longest;
    }

    auto posAnswer() {
        return max(_array[1].longestToUse, _array[1].posAnswer);
    }

};

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    cout << boolalpha;
    const auto n = nextInt();
    const auto q = nextInt();
    const auto w = nextLong();
    auto nodes = Array(n + 1, [&](auto _it) { return Node(_it); });
    nodes[1].parent = nodes[0];
    nodes[1].edges.push_back(Edge(nodes[0], 0, 0));
    for (auto i = 0; i < n - 1; i++) {
        const auto a = nextInt();
        const auto b = nextInt();
        const auto length = nextLong();
        nodes[a].edges.push_back(Edge(nodes[b], length, i));
        nodes[b].edges.push_back(Edge(nodes[a], length, i));
    }
    auto dfs = [&]() { vector<decay_t<decltype(nodes[1])>> _v(n); for (int _i = 0; _i < n; _i++) _v[_i] = nodes[1]; return _v; }();
    auto qodes = [&]() { vector<decay_t<decltype(nodes[0])>> _v(n - 1); for (int _i = 0; _i < n - 1; _i++) _v[_i] = nodes[0]; return _v; }();
    auto j = 1;
    for (auto i = 0; i < n; i++) {
        for (auto k = 0; k < (int)dfs[i].edges.size() - 1; k++) {
            auto edge = dfs[i].edges[k];
            if (edge.node == dfs[i].parent) {
                edge = dfs[i].edges.back();
                dfs[i].edges[k] = edge;
            }
            qodes[edge.ix] = edge.node;
            edge.node.parent = dfs[i];
            edge.node.parentLength = (int)edge.size();
            dfs[j] = edge.node;
            j++;
        }
        ([&]() { auto&& _c = dfs[i].edges; _c.erase(_c.begin() + (int)dfs[i].edges.size() - 1); }());
    }
    for (auto& node : ([&]() { auto&& _c = dfs; return vector<decay_t<decltype(_c.front())>>(_c.rbegin(), _c.rend()); }())) {
        node.parent.subTreeSize += node.subTreeSize;
        auto bestIx = -(1);
        for (auto k = 0; k < (int)node.edges.size(); k++) {
            node.edges[k].node.treeix = k;
            if (node.bestChild == nullptr || node.bestChild.subTreeSize < node.edges[k].node.subTreeSize) {
                node.bestChild = node.edges[k].node;
                bestIx = k;
            }
        }
        if (node.bestChild != nullptr) {
            node.edges.back().node.treeix = bestIx;
            node.edges[bestIx] = node.edges.back();
            ([&]() { auto&& _c = node.edges; _c.erase(_c.begin() + (int)node.edges.size() - 1); }());
        }
        node.hortree = Hortree((int)node.edges.size());
    }
    for (auto& node : dfs) {
        if (node.parent.bestChild == node) {
            continue;
        }
        auto amt = 0;
        Node* subNode = node.bestChild;
        while (subNode != nullptr) {
            subNode.treeix = amt;
            subNode = subNode.bestChild;
            amt++;
        }
        node.vertree = Vertree(node, amt);
        subNode = node.bestChild;
        while (subNode != nullptr) {
            subNode.vertree = node.vertree;
            subNode = subNode.bestChild;
        }
    }
    for (auto& node : ([&]() { auto&& _c = dfs; return vector<decay_t<decltype(_c.front())>>(_c.rbegin(), _c.rend()); }())) {
        if (node != nodes[1]) {
            node.update();
        }
    }
    long long last = 0;
    for (auto i = 1; i <= q; i++) {
        const auto d = (nextLong() + last) % (n - 1);
        auto e = (nextLong() + last) % w;
        auto node = qodes[(int)(d)];
        node.parentLength = e;
        while (node != nodes[1]) {
            node.update();
            if (node == node.parent.bestChild) {
                node = node.vertree.topNode;
            } else {
                node = node.parent;
            }
        }
        last = max({nodes[1].hortree.posAnswer(), nodes[1].vertree.posAnswer(), nodes[1].hortree.longest() + nodes[1].vertree.longest()});
        cout << last << '\n';
    }
    return 0;
}
