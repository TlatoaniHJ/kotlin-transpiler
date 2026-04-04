package transpiler.codegen

/**
 * Emits C++ boilerplate that is included at the top of every transpiled file.
 * Each section is only emitted when the corresponding feature is actually used.
 */
object Boilerplate {

    val INCLUDE = "#include <bits/stdc++.h>\nusing namespace std;\n"

    val IO_HELPERS = """
int nextInt() { int x; cin >> x; return x; }
long long nextLong() { long long x; cin >> x; return x; }
double nextDouble() { double x; cin >> x; return x; }
float nextFloat() { float x; cin >> x; return x; }
string nextToken() { string x; cin >> x; return x; }
string nextLine() { string x; getline(cin, x); return x; }
""".trimIndent()

    val DYNAMIC_BITSET = """
struct DynamicBitSet {
    int n;
    vector<uint64_t> data;

    explicit DynamicBitSet(int n = 0, bool val = false)
        : n(n), data((n + 63) / 64, val ? ~0ULL : 0ULL) {
        trim();
    }

    void trim() {
        if (n % 64 != 0 && !data.empty())
            data.back() &= (1ULL << (n % 64)) - 1ULL;
    }

    void set(int i) { data[i / 64] |= 1ULL << (i % 64); }
    void clear(int i) { data[i / 64] &= ~(1ULL << (i % 64)); }
    void set(int i, bool v) { if (v) set(i); else clear(i); }
    bool get(int i) const { return (data[i / 64] >> (i % 64)) & 1; }
    bool operator[](int i) const { return get(i); }

    void flip(int i) { data[i / 64] ^= 1ULL << (i % 64); }
    void flip() {
        for (auto& w : data) w = ~w;
        trim();
    }

    DynamicBitSet& operator&=(const DynamicBitSet& o) {
        for (int i = 0; i < (int)data.size(); i++) data[i] &= o.data[i];
        return *this;
    }
    DynamicBitSet& operator|=(const DynamicBitSet& o) {
        for (int i = 0; i < (int)data.size(); i++) data[i] |= o.data[i];
        return *this;
    }
    DynamicBitSet& operator^=(const DynamicBitSet& o) {
        for (int i = 0; i < (int)data.size(); i++) data[i] ^= o.data[i];
        return *this;
    }
    void andNot(const DynamicBitSet& o) {
        for (int i = 0; i < (int)data.size(); i++) data[i] &= ~o.data[i];
    }

    int cardinality() const {
        int cnt = 0;
        for (auto w : data) cnt += __builtin_popcountll(w);
        return cnt;
    }

    int nextSetBit(int from) const {
        for (int i = from; i < n; ) {
            int word = i / 64, bit = i % 64;
            uint64_t w = data[word] >> bit;
            if (w) return word * 64 + bit + __builtin_ctzll(w);
            i = (word + 1) * 64;
        }
        return -1;
    }
    int nextClearBit(int from) const {
        for (int i = from; i < n; ) {
            int word = i / 64, bit = i % 64;
            uint64_t w = (~data[word]) >> bit;
            if (w) return word * 64 + bit + __builtin_ctzll(w);
            i = (word + 1) * 64;
        }
        return n;
    }

    int size() const { return n; }
    int length() const {
        for (int i = (int)data.size() - 1; i >= 0; i--) {
            if (data[i]) return i * 64 + 64 - __builtin_clzll(data[i]);
        }
        return 0;
    }
    bool isEmpty() const { return cardinality() == 0; }
    bool intersects(const DynamicBitSet& o) const {
        for (int i = 0; i < (int)data.size(); i++) if (data[i] & o.data[i]) return true;
        return false;
    }
};
""".trimIndent()

    val RANDOM_HELPERS = """
mt19937 rng(chrono::steady_clock::now().time_since_epoch().count());
mt19937_64 rng64(chrono::steady_clock::now().time_since_epoch().count());
int randInt(int lo, int hi) { return uniform_int_distribution<int>(lo, hi)(rng); }
long long randLong(long long lo, long long hi) { return uniform_int_distribution<long long>(lo, hi)(rng64); }
double randDouble() { return uniform_real_distribution<double>(0.0, 1.0)(rng); }
""".trimIndent()

    /**
     * Builds the complete boilerplate string for a given set of required features.
     */
    fun build(
        needsIo: Boolean = true,
        needsBitSet: Boolean = false,
        needsRandom: Boolean = false
    ): String = buildString {
        append(INCLUDE)
        appendLine()
        if (needsIo) {
            appendLine(IO_HELPERS)
        }
        if (needsBitSet) {
            appendLine()
            appendLine(DYNAMIC_BITSET)
        }
        if (needsRandom) {
            appendLine()
            appendLine(RANDOM_HELPERS)
        }
    }
}
