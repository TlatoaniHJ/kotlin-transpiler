package transpiler

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.*
import transpiler.codegen.CodeGenerator
import transpiler.parser.KotlinTranspilerParser
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Automated test harness.
 *
 * For every directory under `test-programs/` that contains a `test.kt` file:
 *   1. Transpile `test.kt` → `test.cpp` via [CodeGenerator].
 *   2. Compile `test.cpp` with `g++ -O2 -std=c++20`.
 *   3. Compile `test.kt` with `kotlinc` and run with `kotlin`.
 *   4. If a `test.in` file exists, pipe it to both programs as stdin.
 *   5. Assert that stdout from both programs is identical.
 */
class TranspilerTest {

    companion object {
        private val PROJECT_DIR = File(System.getProperty("user.dir"))
        private val TEST_PROGRAMS_DIR = File(PROJECT_DIR, "test-programs")
        private val INCLUDE_DIR = File(PROJECT_DIR, "include")
        private val IO_PROVIDER = File(PROJECT_DIR, "IOProvider.kt")
        private val TIMEOUT_SECONDS = 30L
    }

    @TestFactory
    fun allTests(): List<DynamicTest> {
        if (!TEST_PROGRAMS_DIR.exists()) return emptyList()

        return TEST_PROGRAMS_DIR.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?.mapNotNull { dir ->
                val ktFile = File(dir, "test.kt")
                if (!ktFile.exists()) return@mapNotNull null
                DynamicTest.dynamicTest(dir.name) { runTest(dir, ktFile) }
            } ?: emptyList()
    }

    private fun runTest(dir: File, ktFile: File) {
        val inputFile = File(dir, "test.in").takeIf { it.exists() }
        val expectedFile = File(dir, "test.out")

        // ── Step 1: Transpile ─────────────────────────────────────────────────
        val source = ktFile.readText()
        val ast = KotlinTranspilerParser.parse(source)
        val cpp = CodeGenerator(Config.default).generate(ast, source)

        val tmpDir = Files.createTempDirectory("transpiler_test_${dir.name}").toFile()
        try {
            val cppFile = File(tmpDir, "test.cpp")
            cppFile.writeText(cpp)

            // ── Step 2: Compile C++ ───────────────────────────────────────────
            val cppBin = File(tmpDir, "test_cpp")
            val compileCmd = mutableListOf("g++", "-O2", "-std=c++20", cppFile.absolutePath, "-o", cppBin.absolutePath)
            if (INCLUDE_DIR.exists()) {
                compileCmd.addAll(1, listOf("-I", INCLUDE_DIR.absolutePath))
            }
            val compileResult = runProcess(compileCmd, workDir = tmpDir)
            if (compileResult.exitCode != 0) {
                fail<Unit>("C++ compilation failed for ${dir.name}:\n${compileResult.stderr}\n\nGenerated C++:\n$cpp")
            }

            // ── Step 3: Compile Kotlin ────────────────────────────────────────
            val jarFile = File(tmpDir, "test.jar")
            val kotlinSources = mutableListOf(ktFile.absolutePath)
            if (IO_PROVIDER.exists()) kotlinSources.add(IO_PROVIDER.absolutePath)
            val kotlinCompile = runProcess(
                listOf("kotlinc") + kotlinSources + listOf(
                    "-include-runtime", "-d", jarFile.absolutePath,
                    "-nowarn", "-language-version", "1.9", "-api-version", "1.9"
                ),
                workDir = tmpDir
            )
            if (kotlinCompile.exitCode != 0) {
                fail<Unit>("Kotlin compilation failed for ${dir.name}:\n${kotlinCompile.stderr}")
            }

            // ── Step 4: Run both ──────────────────────────────────────────────
            val input = inputFile?.readText() ?: ""
            val cppOut = runProcess(listOf(cppBin.absolutePath), input = input, workDir = tmpDir)
            // Class name is derived from the file name: test.kt → TestKt
            val className = ktFile.nameWithoutExtension.replaceFirstChar { it.uppercase() } + "Kt"
            val ktOut  = runProcess(
                listOf("kotlin", "-classpath", jarFile.absolutePath, className),
                input = input, workDir = tmpDir
            )

            // ── Step 5: Compare ───────────────────────────────────────────────
            val expectedOutput = if (expectedFile.exists()) expectedFile.readText()
                                 else ktOut.stdout

            assertEquals(
                normalizeOutput(expectedOutput),
                normalizeOutput(cppOut.stdout),
                "Output mismatch for test '${dir.name}'.\n" +
                "Kotlin stdout:\n${ktOut.stdout}\n" +
                "C++ stdout:\n${cppOut.stdout}"
            )
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /** Normalise output for comparison: trim trailing whitespace per line, normalize line endings. */
    private fun normalizeOutput(s: String): String =
        s.lines().joinToString("\n") { it.trimEnd() }.trimEnd()

    // ─── Process runner ───────────────────────────────────────────────────────

    private data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runProcess(
        cmd: List<String>,
        input: String = "",
        workDir: File? = null
    ): ProcessResult {
        val pb = ProcessBuilder(cmd)
            .redirectErrorStream(false)
        if (workDir != null) pb.directory(workDir)

        val process = pb.start()

        // Write stdin
        if (input.isNotEmpty()) {
            process.outputStream.writer().use { it.write(input) }
        } else {
            process.outputStream.close()
        }

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw AssertionError("Process timed out after ${TIMEOUT_SECONDS}s: ${cmd.joinToString(" ")}")
        }
        return ProcessResult(process.exitValue(), stdout, stderr)
    }
}
