import configurations.*
import jetbrains.buildServer.configs.kotlin.v10.toExtId
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

version = "2024.12"

object ProjectBranchFilters {
  val githubBranchFilter = "+:pull/*"
  val spaceBranchFilter =
    """
    +:<default>
    +:*
    -:bazel-steward*
    -:refs/merge/*
    """.trimIndent()
}

object ProjectTriggerRules {
  val triggerRules =
    """
    -:**.md
    -:**.txt
    -:**.yml
    -:**.yaml
    -:LICENSE
    -:LICENCE
    -:CODEOWNERS
    -:/.teamcity/**
    -:/tools/**
    """.trimIndent()
}

project {
  subProject(GitHub)
  subProject(Space)
}

object GitHub : Project({
  name = "GitHub"
  id(name.toExtId())
  vcsRoot(BaseConfiguration.GitHubVcs)

// setup pipeline chain for bazel-bsp
  val allSteps =
    sequential {
      buildType(ProjectFormat.GitHub)
      parallel(options = {
        onDependencyFailure = FailureAction.CANCEL
        onDependencyCancel = FailureAction.CANCEL
      }) {
        buildType(ProjectBuild.GitHub)
        buildType(ProjectUnitTests.GitHub)
        buildType(PluginBenchmark.BenchmarkDefaultGitHub)
        buildType(PluginBenchmark.BenchmarkWithVersionGitHub)
        buildType(IdeStarterTests.HotswapTestGitHub)
        buildType(IdeStarterTests.ExternalRepoResolveTestGitHub)
        buildType(ServerE2eTests.SampleRepoGitHub)
        buildType(ServerE2eTests.LocalJdkGitHub)
        buildType(ServerE2eTests.RemoteJdkGitHub)
//            buildType(ServerE2eTests.ServerDownloadsBazeliskGitHub)
        buildType(ServerE2eTests.AndroidProjectGitHub)
        buildType(ServerE2eTests.AndroidKotlinProjectGitHub)
        buildType(ServerE2eTests.ScalaProjectGitHub)
        buildType(ServerE2eTests.KotlinProjectGitHub)
        buildType(ServerE2eTests.PythonProjectGitHub)
        buildType(ServerE2eTests.JavaDiagnosticsGitHub)
        buildType(ServerE2eTests.ManualTargetsGitHub)
        buildType(ServerE2eTests.BuildSyncGitHub)
        buildType(ServerE2eTests.FirstPhaseSyncGitHub)
        buildType(ServerE2eTests.PartialSyncGitHub)
        buildType(ServerE2eTests.NestedModulesGitHub)
        buildType(ServerBenchmark.GitHub)
        buildType(StaticAnalysis.HirschgartenGitHub)
        buildType(StaticAnalysis.AndroidBazelRulesGitHub)
        buildType(StaticAnalysis.AndroidTestdpcGitHub)
        buildType(StaticAnalysis.BazelGitHub)
        buildType(StaticAnalysis.JetpackComposeGitHub)
      }

      buildType(ResultsAggregator.GitHub, options = {
        onDependencyFailure = FailureAction.ADD_PROBLEM
        onDependencyCancel = FailureAction.ADD_PROBLEM
      })
    }.buildTypes()

  // initialize all build steps for bazel-bsp
  allSteps.forEach { buildType(it) }

  // setup trigger for bazel-bsp pipeline
  ProjectFormat.GitHub.triggers {
    vcs {
      branchFilter = ProjectBranchFilters.githubBranchFilter
      triggerRules = ProjectTriggerRules.triggerRules
    }
  }

  ResultsAggregator.GitHub.triggers {
    finishBuildTrigger {
      buildType = "${ProjectFormat.GitHub.id}"
      successfulOnly = true
      branchFilter = ProjectBranchFilters.githubBranchFilter
    }
  }

  // setup display order for bazel-bsp pipeline
  buildTypesOrderIds =
    arrayListOf(
      ProjectFormat.GitHub,
      ProjectBuild.GitHub,
      ProjectUnitTests.GitHub,
      PluginBenchmark.BenchmarkDefaultGitHub,
      PluginBenchmark.BenchmarkWithVersionGitHub,
      IdeStarterTests.HotswapTestGitHub,
      IdeStarterTests.ExternalRepoResolveTestGitHub,
      ServerE2eTests.SampleRepoGitHub,
      ServerE2eTests.LocalJdkGitHub,
      ServerE2eTests.RemoteJdkGitHub,
      // ServerE2eTests.ServerDownloadsBazeliskGitHub,
      ServerE2eTests.AndroidProjectGitHub,
      ServerE2eTests.AndroidKotlinProjectGitHub,
      ServerE2eTests.ScalaProjectGitHub,
      ServerE2eTests.KotlinProjectGitHub,
      ServerE2eTests.PythonProjectGitHub,
      ServerE2eTests.JavaDiagnosticsGitHub,
      ServerE2eTests.ManualTargetsGitHub,
      ServerE2eTests.BuildSyncGitHub,
      ServerE2eTests.FirstPhaseSyncGitHub,
      ServerE2eTests.PartialSyncGitHub,
      ServerE2eTests.NestedModulesGitHub,
      ServerBenchmark.GitHub,
      StaticAnalysis.HirschgartenGitHub,
      StaticAnalysis.AndroidBazelRulesGitHub,
      StaticAnalysis.AndroidTestdpcGitHub,
      StaticAnalysis.BazelGitHub,
      StaticAnalysis.JetpackComposeGitHub,
      ResultsAggregator.GitHub,
    )
})

object Space : Project({
  name = "Space"
  id(name.toExtId())
  vcsRoot(BaseConfiguration.SpaceVcs)

// setup pipeline chain for bazel-bsp
  val allSteps =
    sequential {
      buildType(ProjectFormat.Space)
      parallel(options = {
        onDependencyFailure = FailureAction.CANCEL
        onDependencyCancel = FailureAction.CANCEL
      }) {
        buildType(ProjectBuild.Space)
        buildType(ProjectUnitTests.Space)
        buildType(PluginBenchmark.SpaceBenchmarkDefault)
        buildType(PluginBenchmark.SpaceBenchmarkWithVersion)
        buildType(IdeStarterTests.HotswapTestSpace)
        buildType(IdeStarterTests.ExternalRepoResolveTestSpace)
        buildType(ServerE2eTests.SampleRepoSpace)
        buildType(ServerE2eTests.LocalJdkSpace)
        buildType(ServerE2eTests.RemoteJdkSpace)
//            buildType(ServerE2eTests.ServerDownloadsBazeliskSpace)
        buildType(ServerE2eTests.AndroidProjectSpace)
        buildType(ServerE2eTests.AndroidKotlinProjectSpace)
        buildType(ServerE2eTests.ScalaProjectSpace)
        buildType(ServerE2eTests.KotlinProjectSpace)
        buildType(ServerE2eTests.PythonProjectSpace)
        buildType(ServerE2eTests.JavaDiagnosticsSpace)
        buildType(ServerE2eTests.ManualTargetsSpace)
        buildType(ServerE2eTests.BuildSyncSpace)
        buildType(ServerE2eTests.FirstPhaseSyncSpace)
        buildType(ServerE2eTests.PartialSyncSpace)
        buildType(ServerE2eTests.NestedModulesSpace)
        buildType(ServerBenchmark.Space)
        buildType(StaticAnalysis.HirschgartenSpace)
        buildType(StaticAnalysis.AndroidBazelRulesSpace)
        buildType(StaticAnalysis.AndroidTestdpcSpace)
        buildType(StaticAnalysis.BazelSpace)
        buildType(StaticAnalysis.JetpackComposeSpace)
      }

      buildType(ResultsAggregator.Space, options = {
        onDependencyFailure = FailureAction.ADD_PROBLEM
        onDependencyCancel = FailureAction.ADD_PROBLEM
      })
    }.buildTypes()

  // initialize all build steps for bazel-bsp
  allSteps.forEach { buildType(it) }

  // setup trigger for bazel-bsp pipeline
  ProjectFormat.Space.triggers {
    vcs {
      branchFilter = ProjectBranchFilters.spaceBranchFilter
      triggerRules = ProjectTriggerRules.triggerRules
    }
  }

  ResultsAggregator.Space.triggers {
    finishBuildTrigger {
      buildType = "${ProjectFormat.Space.id}"
      successfulOnly = true
      branchFilter = ProjectBranchFilters.spaceBranchFilter
    }
  }

  // setup display order for bazel-bsp pipeline
  buildTypesOrderIds =
    arrayListOf(
      ProjectFormat.Space,
      ProjectBuild.Space,
      ProjectUnitTests.Space,
      PluginBenchmark.SpaceBenchmarkDefault,
      PluginBenchmark.SpaceBenchmarkWithVersion,
      IdeStarterTests.HotswapTestSpace,
      IdeStarterTests.ExternalRepoResolveTestSpace,
      ServerE2eTests.SampleRepoSpace,
      ServerE2eTests.LocalJdkSpace,
      ServerE2eTests.RemoteJdkSpace,
      // ServerE2eTests.ServerDownloadsBazeliskSpace,
      ServerE2eTests.AndroidProjectSpace,
      ServerE2eTests.AndroidKotlinProjectSpace,
      ServerE2eTests.ScalaProjectSpace,
      ServerE2eTests.KotlinProjectSpace,
      ServerE2eTests.PythonProjectSpace,
      ServerE2eTests.JavaDiagnosticsSpace,
      ServerE2eTests.ManualTargetsSpace,
      ServerE2eTests.BuildSyncSpace,
      ServerE2eTests.FirstPhaseSyncSpace,
      ServerE2eTests.PartialSyncSpace,
      ServerE2eTests.NestedModulesSpace,
      ServerBenchmark.Space,
      StaticAnalysis.HirschgartenSpace,
      StaticAnalysis.AndroidBazelRulesSpace,
      StaticAnalysis.AndroidTestdpcSpace,
      StaticAnalysis.BazelSpace,
      StaticAnalysis.JetpackComposeSpace,
      ResultsAggregator.Space,
    )
})
