package org.jetbrains.bsp.bazel.server.diagnostics

import org.jetbrains.bsp.bazel.server.model.Label

interface DiagnosticsParser {
  fun parse(bazelOutput: String, target: Label, onlyKnownFiles: Boolean): List<Diagnostic>
}

class DiagnosticsParserImpl : DiagnosticsParser {
  override fun parse(bazelOutput: String, target: Label, onlyKnownFiles: Boolean): List<Diagnostic> {
    val output = prepareOutput(bazelOutput, target)
    val diagnostics = collectDiagnostics(output, onlyKnownFiles)
    return deduplicate(diagnostics)
  }

  private fun prepareOutput(bazelOutput: String, target: Label): Output {
    val lines = bazelOutput.lines()
    val relevantLines = lines.filterNot { line -> IgnoredLines.any { it.matches(line) } }
    return Output(relevantLines, target)
  }

  private fun collectDiagnostics(output: Output, onlyKnownFiles: Boolean): List<Diagnostic> {
    val diagnostics = mutableListOf<Diagnostic>()
    while (output.nonEmpty()) {
      for (parser in Parsers) {
        val result = parser.tryParse(output)
        if (result.isNotEmpty()) {
          diagnostics.addAll(result)
          break
        }
      }
    }

    if (diagnostics.isEmpty() && !onlyKnownFiles) {
      diagnostics.add(
        Diagnostic(
          position = Position(0, 0),
          message = output.fullOutput(),
          fileLocation = "<unknown>",
          targetLabel = output.targetLabel,
        ),
      )
    }

    return diagnostics.toList()
  }

  private fun deduplicate(parsedDiagnostics: List<Diagnostic>): List<Diagnostic> =
    parsedDiagnostics
      .groupBy { Triple(it.fileLocation, it.message, it.position) }
      .values
      .map { it.first() }

  companion object {
    private val Parsers =
      listOf(
        BazelRootMessageParser,
        CompilerDiagnosticParser,
        Scala3CompilerDiagnosticParser,
        AllCatchParser,
      )
    private val IgnoredLines =
      listOf(
        "^$".toRegex(),
        "Use --sandbox_debug to see verbose messages from the sandbox".toRegex(),
      )
  }
}
