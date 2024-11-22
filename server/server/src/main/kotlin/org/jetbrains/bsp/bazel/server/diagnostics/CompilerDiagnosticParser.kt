package org.jetbrains.bsp.bazel.server.diagnostics

import ch.epfl.scala.bsp4j.DiagnosticSeverity


object CompilerDiagnosticParser : Parser {
  override fun tryParse(output: Output): List<Diagnostic> = listOfNotNull(tryParseOne(output))

  // Example:
  // server/DiagnosticsServiceTest.kt:12:18: error: type mismatch: inferred type is String but Int was expected
  private val DiagnosticHeader =
    """
      ^                       # start of line
      (?<logLevel>\[.*\]\s*)? #optional
      (?<filePath>[^:]+)      # file path (2)
      :(?<lineNumber>\d+)     # line number (3)
      (?::(?<columnNumber>\d+))?      # optional column number (4)
      (?::\ )?                # ": " separator
      (?:(?<errorLevel>[a-zA-Z\ ]+):\ )? # optional level (5) could have been at the beginning instead
      (?<errorMessage>.*)             # actual error message (6)
      $                # end of line
      """.toRegex(RegexOption.COMMENTS)

  fun tryParseOne(output: Output): Diagnostic? {
     return output
      .tryTake(DiagnosticHeader)
      ?.let { match ->
        val path = match.groups["filePath"]?.value ?: ""
        val line = match.groups["lineNumber"]?.value?.toIntOrNull() ?: -1
        val messageLines = collectMessageLines(match.groups["errorMessage"]?.value ?: "", output)
        val column = match.groups["columnNumber"]?.value?.toIntOrNull() ?: tryFindColumnNumber(messageLines) ?: 1
        val levelText = (match.groups["logLevel"]?.value ?: match.groups["errorLevel"]?.value ?: "").lowercase()
        val level = if (levelText == "warning") DiagnosticSeverity.WARNING else DiagnosticSeverity.ERROR
        val message = messageLines.joinToString("\n")
        Diagnostic(Position(line, column), message, path, output.targetLabel, level)
      }
    }

  private fun collectMessageLines(header: String, output: Output): List<String> {
    val lines = mutableListOf<String>()
    lines.addAll(tryCollectLinesMatchingIssueDetails(output))
    if (lines.isEmpty()) {
      lines.addAll(tryCollectLinesTillErrorMarker(output))
    }
    lines.add(0, header)
    return lines
  }

  private val IssuePositionMarker = """^\s*\^\s*$""".toRegex() // ^ surrounded by whitespace only

  private fun tryCollectLinesTillErrorMarker(output: Output): List<String> {
    val peeked = output.peek(limit = 20)
    val index = peeked.indexOfFirst { IssuePositionMarker.matches(it) }
    return if (index != -1) output.take(count = index + 1) else emptyList()
  }

  private val IssueDetails = """^\s+.*|${IssuePositionMarker.pattern}""".toRegex() // indented line or ^

  private fun tryCollectLinesMatchingIssueDetails(output: Output) = generateSequence { output.tryTake(IssueDetails)?.value }.toList()

  private fun tryFindColumnNumber(messageLines: List<String>): Int? {
    val line = messageLines.find { IssuePositionMarker.matches(it) }
    return line?.indexOf("^")?. let { it + 1 }
  }
}
