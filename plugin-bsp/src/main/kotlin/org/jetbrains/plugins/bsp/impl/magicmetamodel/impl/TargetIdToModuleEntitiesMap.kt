package org.jetbrains.plugins.bsp.impl.magicmetamodel.impl

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.bsp.impl.magicmetamodel.ProjectDetails
import org.jetbrains.plugins.bsp.impl.magicmetamodel.TargetNameReformatProvider
import org.jetbrains.plugins.bsp.impl.magicmetamodel.impl.workspacemodel.ModuleDetails
import org.jetbrains.plugins.bsp.impl.magicmetamodel.impl.workspacemodel.impl.updaters.transformers.ModuleDetailsToJavaModuleTransformer
import org.jetbrains.plugins.bsp.impl.magicmetamodel.impl.workspacemodel.impl.updaters.transformers.ModuleDetailsToPythonModuleTransformer
import org.jetbrains.plugins.bsp.impl.magicmetamodel.impl.workspacemodel.includesPython
import org.jetbrains.plugins.bsp.impl.magicmetamodel.impl.workspacemodel.isJvmOrAndroidTarget
import org.jetbrains.plugins.bsp.workspacemodel.entities.BuildTargetInfo
import org.jetbrains.plugins.bsp.workspacemodel.entities.Module
import java.nio.file.Path

object TargetIdToModuleEntitiesMap {
  operator fun invoke(
    projectDetails: ProjectDetails,
    targetIdToModuleDetails: Map<BuildTargetIdentifier, ModuleDetails>,
    targetIdToTargetInfo: Map<BuildTargetIdentifier, BuildTargetInfo>,
    projectBasePath: Path,
    moduleNameProvider: TargetNameReformatProvider,
    libraryNameProvider: TargetNameReformatProvider,
    hasDefaultPythonInterpreter: Boolean,
    isAndroidSupportEnabled: Boolean,
  ): Map<BuildTargetIdentifier, Module> {
    val moduleDetailsToJavaModuleTransformer =
      ModuleDetailsToJavaModuleTransformer(
        targetIdToTargetInfo,
        moduleNameProvider,
        libraryNameProvider,
        projectBasePath,
        isAndroidSupportEnabled,
      )
    val moduleDetailsToPythonModuleTransformer =
      ModuleDetailsToPythonModuleTransformer(
        targetIdToTargetInfo,
        moduleNameProvider,
        libraryNameProvider,
        hasDefaultPythonInterpreter,
      )

    return runBlocking(Dispatchers.Default) {
      projectDetails.targetIds
        .map {
          async {
            val moduleDetails = targetIdToModuleDetails.getValue(it)
            val module =
              if (moduleDetails.target.languageIds.includesPython()) {
                moduleDetailsToPythonModuleTransformer.transform(moduleDetails)
              } else if (moduleDetails.target.languageIds.isJvmOrAndroidTarget()) {
                moduleDetailsToJavaModuleTransformer.transform(moduleDetails)
              } else {
                return@async null
              }
            it to module
          }
        }.awaitAll()
        .asSequence()
        .filterNotNull()
        .toMap()
    }
  }
}

@TestOnly
public fun Collection<String>.toDefaultTargetsMap(): Map<BuildTargetIdentifier, BuildTargetInfo> =
  associateBy(
    keySelector = { BuildTargetIdentifier(it) },
    valueTransform = { BuildTargetInfo(id = BuildTargetIdentifier(it)) },
  )