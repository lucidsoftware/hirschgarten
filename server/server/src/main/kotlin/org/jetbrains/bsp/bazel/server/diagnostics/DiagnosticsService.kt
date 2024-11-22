package org.jetbrains.bsp.bazel.server.diagnostics

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams
import ch.epfl.scala.bsp4j.TextDocumentIdentifier
import org.jetbrains.bsp.bazel.server.model.Label
import java.nio.file.Path
import java.util.Collections

class DiagnosticsService(
  workspaceRoot: Path,
  private val parser: DiagnosticsParser = DiagnosticsParserImpl(),
  private val mapper: DiagnosticBspMapper = DiagnosticBspMapper(workspaceRoot),
  private val hasAnyProblems: MutableMap<Label, Set<TextDocumentIdentifier>>, 
) {

  private val updatedInThisRun = mutableSetOf<PublishDiagnosticsParams>()

  val bspState: Map<Label, Set<TextDocumentIdentifier>>
    get() = Collections.unmodifiableMap(hasAnyProblems)

  fun extractDiagnostics(
    bazelOutput: String,
    targetLabel: Label,
    originId: String?,
    diagnosticsFromProgress: Boolean,
  ): List<PublishDiagnosticsParams> {
    val parsedDiagnostics = parser.parse(bazelOutput, targetLabel, diagnosticsFromProgress)
    val events = mapper.createDiagnostics(parsedDiagnostics, originId)
    if (diagnosticsFromProgress) updatedInThisRun.addAll(events)
    updateProblemState(events)
    return events
  }

  fun clearFormerDiagnostics(targetLabel: Label): List<PublishDiagnosticsParams> {
    val docs = hasAnyProblems[targetLabel]
    hasAnyProblems.remove(targetLabel)
    val toClear =
      if (updatedInThisRun.isNotEmpty()) {
        val updatedDocs = updatedInThisRun.map { it.textDocument }.toSet()
        hasAnyProblems[targetLabel] = updatedDocs
        updatedInThisRun.clear()
        docs?.subtract(updatedDocs)
      } else {
        docs
      }
    return toClear
      ?.map { PublishDiagnosticsParams(it, BuildTargetIdentifier(targetLabel.value), emptyList(), true) }
      .orEmpty()
  }
  private fun updateProblemState(events: List<PublishDiagnosticsParams>) {
    events
      .groupBy { it.buildTarget.uri }
      .forEach { group ->
        val buildTarget = Label.parse(group.key)
        val params = group.value
        val docs = params.map { it.textDocument }.toSet()
        hasAnyProblems[buildTarget] = docs
      }
  }
}
