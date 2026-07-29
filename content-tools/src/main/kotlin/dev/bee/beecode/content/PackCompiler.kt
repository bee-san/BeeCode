package dev.bee.beecode.content

import java.io.File

/**
 * Compiles an authoring directory into the pack a client ships.
 *
 * Invoked by the build, so a client never parses YAML, never walks a content tree,
 * and never has `reference.py` on disk. Refuses to write anything when validation
 * fails: a broken pack must break the build, not reach a learner.
 *
 * Usage: `PackCompiler <packDirectory> <outputFile>`
 */
object PackCompiler {
    fun compile(packDirectory: File, outputFile: File): CompileResult {
        val result = ProblemLoader().loadPack(packDirectory)
        if (!result.isValid) {
            return CompileResult.Failed(result.describeFailures())
        }
        if (result.problems.isEmpty()) {
            // An empty pack would produce an app with nothing to study, which is
            // worse than a build failure because it looks like it worked.
            return CompileResult.Failed("No Problems were found in ${packDirectory.path}")
        }

        val json = ProblemPack.encode(packId = packDirectory.name, problems = result.problems)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json)

        return CompileResult.Compiled(
            problemCount = result.problems.size,
            testCount = result.problems.sumOf { it.tests.size },
            bytes = json.length,
            outputPath = outputFile.absolutePath,
        )
    }

    sealed interface CompileResult {
        data class Compiled(
            val problemCount: Int,
            val testCount: Int,
            val bytes: Int,
            val outputPath: String,
        ) : CompileResult

        data class Failed(val reason: String) : CompileResult
    }
}

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage: PackCompiler <packDirectory> <outputFile>")
        kotlin.system.exitProcess(2)
    }
    when (val result = PackCompiler.compile(File(args[0]), File(args[1]))) {
        is PackCompiler.CompileResult.Compiled -> println(
            "BeeCode pack: ${result.problemCount} Problems, ${result.testCount} tests, " +
                "${result.bytes} bytes -> ${result.outputPath}",
        )
        is PackCompiler.CompileResult.Failed -> {
            System.err.println("BeeCode pack compilation failed:\n${result.reason}")
            kotlin.system.exitProcess(1)
        }
    }
}
