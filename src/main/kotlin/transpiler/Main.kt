package transpiler

import transpiler.codegen.CodeGenerator
import transpiler.parser.KotlinTranspilerParser
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val (inputPath, inlineSource, outputPath, config) = parseArgs(args)

    val source = if (inlineSource != null) {
        inlineSource
    } else {
        val inputFile = File(inputPath!!)
        if (!inputFile.exists()) {
            System.err.println("Error: input file not found: $inputPath")
            exitProcess(1)
        }
        inputFile.readText()
    }

    val ast = try {
        KotlinTranspilerParser.parse(source)
    } catch (e: Exception) {
        System.err.println("Parse error in $inputPath: ${e.message}")
        exitProcess(1)
    }

    val cpp = try {
        CodeGenerator(config).generate(ast)
    } catch (e: Exception) {
        System.err.println("Code generation error: ${e.message}")
        if (System.getenv("TRANSPILER_DEBUG") != null) e.printStackTrace()
        exitProcess(1)
    }

    if (outputPath == "-") {
        print(cpp)
    } else {
        val out = File(outputPath)
        out.parentFile?.mkdirs()
        out.writeText(cpp)
        System.err.println("Written: $outputPath")
    }
}

private data class ParsedArgs(
    val inputPath: String?,
    val inlineSource: String?,
    val outputPath: String,
    val config: Config
)

private fun parseArgs(args: Array<String>): ParsedArgs {
    if (args.isEmpty()) usage()

    var inputPath: String? = null
    var inlineSource: String? = null
    var outputPath: String? = null
    var useUnorderedMap = false

    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "-o", "--output" -> {
                outputPath = args.getOrNull(++i) ?: usage()
            }
            "-e", "--eval" -> {
                inlineSource = args.getOrNull(++i) ?: usage()
            }
            "--unordered-map" -> useUnorderedMap = true
            "--help", "-h"    -> usage()
            else -> {
                if (inputPath == null) inputPath = arg
                else usage()
            }
        }
        i++
    }

    if (inputPath == null && inlineSource == null) usage()

    // Default output: stdout for --eval, same name with .cpp extension for file input
    if (outputPath == null) {
        outputPath = if (inlineSource != null) "-"
                     else inputPath!!.removeSuffix(".kt") + ".cpp"
    }

    return ParsedArgs(
        inputPath    = inputPath,
        inlineSource = inlineSource,
        outputPath   = outputPath!!,
        config       = Config(useUnorderedMapForMutableMapOf = useUnorderedMap)
    )
}

private fun usage(): Nothing {
    System.err.println(
        """
        Usage: transpiler <input.kt> [-o <output.cpp>] [options]

        Options:
          -o, --output <file>   Output file (default: input with .cpp extension)
          -e, --eval <code>     Transpile inline Kotlin code and print C++ to stdout
          --unordered-map       Use std::unordered_map for mutableMapOf() (default: std::map)
          -h, --help            Show this help

        Output '-' writes to stdout.
        """.trimIndent()
    )
    exitProcess(1)
}
