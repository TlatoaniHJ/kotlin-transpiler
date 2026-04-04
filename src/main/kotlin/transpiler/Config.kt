package transpiler

/**
 * Transpiler configuration options.
 */
data class Config(
    /**
     * If true, `mutableMapOf()` maps to `std::unordered_map` instead of `std::map`.
     * Default is false (ordered map) since unordered_map can be slow in the worst case.
     */
    val useUnorderedMapForMutableMapOf: Boolean = false
) {
    companion object {
        val default = Config()
    }
}
