package org.jetbrains.bsp.bazel.server.model

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target
import org.jetbrains.bazel.commons.label.Label
import org.jetbrains.bsp.bazel.bazelrunner.utils.BazelRelease
import java.net.URI

sealed interface Project

data class FirstPhaseProject(
  val workspaceRoot: URI,
  val modules: Map<Label, Target>,
  val bazelRelease: BazelRelease,
) : Project

/** Project is the internal model of the project. Bazel/Aspect Model -> Project -> BSP Model  */
data class AspectSyncProject(
  val workspaceRoot: URI,
  val modules: List<Module>,
  val libraries: Map<Label, Library>,
  val goLibraries: Map<Label, GoLibrary>,
  val invalidTargets: List<Label>,
  val nonModuleTargets: List<NonModuleTarget>, // targets that should be displayed in the project view but are neither modules nor libraries
  val bazelRelease: BazelRelease,
) : Project {
  private val moduleMap: Map<Label, Module> = modules.associateBy(Module::label)

  fun findModule(label: Label): Module? = moduleMap[label]

  operator fun plus(project: AspectSyncProject): AspectSyncProject {
    if (workspaceRoot !=
      project.workspaceRoot
    ) {
      error("Cannot add projects with different workspace roots: $workspaceRoot and ${project.workspaceRoot}")
    }
    if (bazelRelease !=
      project.bazelRelease
    ) {
      error("Cannot add projects with different bazel versions: $bazelRelease and ${project.bazelRelease}")
    }
    val newModules = modules.toSet() + project.modules.toSet()
    val newLibraries = libraries + project.libraries
    val newGoLibraries = goLibraries + project.goLibraries
    val newInvalidTargets = invalidTargets.toSet() + project.invalidTargets.toSet()
    val newNonModuleTargets = nonModuleTargets.toSet() + project.nonModuleTargets.toSet()

    return copy(
      modules = newModules.toList(),
      libraries = newLibraries,
      goLibraries = newGoLibraries,
      invalidTargets = newInvalidTargets.toList(),
      nonModuleTargets = newNonModuleTargets.toList(),
    )
  }
}