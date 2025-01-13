package org.jetbrains.bsp.bazel.server.bsp.managers

import ch.epfl.scala.bsp4j.TextDocumentIdentifier
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.jsonrpc.CancelChecker
import org.jetbrains.bsp.bazel.bazelrunner.BazelRunner
import org.jetbrains.bsp.bazel.server.bep.BepServer
import org.jetbrains.bsp.bazel.server.diagnostics.DiagnosticsService
import org.jetbrains.bsp.bazel.server.model.Label
import org.jetbrains.bsp.bazel.server.paths.BazelPathsResolver
import org.jetbrains.bsp.bazel.workspacecontext.TargetsSpec
import org.jetbrains.bsp.protocol.JoinedBuildClient

// TODO: remove this file once we untangle the spaghetti and use the method from ExecuteService

class BazelBspCompilationManager(
  private val bazelRunner: BazelRunner,
  private val bazelPathsResolver: BazelPathsResolver,
  private val hasAnyProblems: MutableMap<Label, Set<TextDocumentIdentifier>>,
  val client: JoinedBuildClient,
  val workspaceRoot: Path,
) {
  fun buildTargetsWithBep(
    cancelChecker: CancelChecker,
    targetSpecs: TargetsSpec,
    extraFlags: List<String> = emptyList(),
    originId: String? = null,
    environment: List<Pair<String, String>> = emptyList(),
  ): BepBuildResult {
    val target = targetSpecs.values.firstOrNull()
    val diagnosticsService = DiagnosticsService(workspaceRoot, hasAnyProblems = hasAnyProblems)
    val bepServer = BepServer(client, diagnosticsService, originId, target, bazelPathsResolver)
    val bepReader = BepReader(bepServer)
    return try {
      runBlocking {
        val readerFuture =
          async(Dispatchers.Default) {
            bepReader.start()
          }
        /*
         * example:
         * bazel build --tool_tag=bazelbsp:0.0.0 --aspects=@@bazelbsp_aspect//aspects:core.bzl%bsp_target_info_aspect ...
         */
        val command =
          bazelRunner.buildBazelCommand {
            build {
              options.addAll(extraFlags)
              addTargetsFromSpec(targetSpecs)
              this.environment.putAll(environment)
              useBes(bepReader.eventFile.toPath().toAbsolutePath())
            }
          }
        val result =
          bazelRunner
            .runBazelCommand(command, originId = originId, serverPidFuture = bepReader.serverPid)
            .waitAndGetResult(cancelChecker, true)
        bepReader.finishBuild()
        readerFuture.await()
        BepBuildResult(result, bepServer.bepOutput)
      }
    } finally {
      bepReader.finishBuild()
    }
  }
}
