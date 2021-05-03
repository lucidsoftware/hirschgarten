package org.jetbrains.bazel

import org.backuity.clist.{Command, arg, opt}
import org.jetbrains.bazel.sonatype.SonatypeClient.StagingRepositoryProfile
import org.jetbrains.bazel.sonatype.{SonatypeClient, SonatypeCoordinates, SonatypeException, SonatypeService}
import org.sonatype.spice.zapper.Path
import wvlet.log.{LogLevel, LogSupport}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class SonatypeKeys extends Command {
  var sonatypeRepository: String =
    opt[String](
      description = "Sonatype repository URL: e.g. https://oss.sonatype.org/service/local",
      default = "https://oss.sonatype.org/service/local"
    )
  var sonatypeUsername: Option[String] =
    arg[Option[String]](required = false, description = "Username for the sonatype repository")
  var sonatypePassword: Option[String] =
    arg[Option[String]](required = false, description = "Password for the sonatype repository")
  var sonatypeProfileName: String =
    arg[String](description = "Profile name at Sonatype: e.g. org.xerial")
  var sonatypeSessionName: Option[String] =
    opt[Option[String]](description = "Used for identifying a sonatype staging repository")
  var sonatypeCoordinates: String = arg[String](description = "Coordinates at Sonatype: e.g. org.xerial.sbt-sonatype")
  var sonatypeTimeoutMillis: Int =
    opt[Int](default = 60 * 60 * 1000, description = "milliseconds before giving up Sonatype API requests")
  var sonatypeLogLevel: String =
    opt[String](default = "info", description = "log level: trace, debug, info warn, error")
  var sonatypeProjectJar: String        = arg[String](description = "Path to project jar")
  var sonatypeProjectSourcesJar: String = arg[String](description = "Path to project sources jar")
  var sonatypeProjectDocsJar: String    = arg[String](description = "Path to project docs jar")
  var sonatypeProjectPom: String        = arg[String](description = "Path to project pom file")
}

class Sonatype(sonatypeKeys: SonatypeKeys) extends LogSupport {

  val username: String = sonatypeKeys.sonatypeUsername.getOrElse(
    sys.env.getOrElse(
      "SONATYPE_USERNAME",
      throw new IllegalArgumentException("SONATYPE_USERNAME variable is not defined")
    )
  )
  val password: String = sonatypeKeys.sonatypePassword.getOrElse(
    sys.env.getOrElse(
      "SONATYPE_PASSWORD",
      throw new IllegalArgumentException("SONATYPE_PASSWORD variable is not defined")
    )
  )

  lazy val sonatypeSplitCoordinates: SonatypeCoordinates =
    SonatypeCoordinates(sonatypeKeys.sonatypeCoordinates)

  private implicit val ec = ExecutionContext.global

  lazy val rest: SonatypeService = {
    val logLevel = LogLevel(sonatypeKeys.sonatypeLogLevel)
    wvlet.log.Logger.setDefaultLogLevel(logLevel)
    val sonatypeClient = new SonatypeClient(
      repositoryUrl = sonatypeKeys.sonatypeRepository,
      username = username,
      password = password,
      timeoutMillis = sonatypeKeys.sonatypeTimeoutMillis
    )

    new SonatypeService(
      sonatypeClient,
      sonatypeKeys.sonatypeProfileName
    )
  }

  lazy val sonatypeSessionName: String = {
    sonatypeKeys.sonatypeSessionName.getOrElse(sonatypeSplitCoordinates.sonatypeArtifactId)
  }

  lazy val filesPaths: List[Path] = List(
    new Path(sonatypeKeys.sonatypeProjectJar),
    new Path(sonatypeKeys.sonatypeProjectSourcesJar),
    new Path(sonatypeKeys.sonatypeProjectDocsJar),
    new Path(sonatypeKeys.sonatypeProjectPom)
  )

  private def withSonatypeService()(
      body: SonatypeService => Unit
  ): Boolean = {
    try {
      body(rest)
      true
    } catch {
      case e: SonatypeException =>
        error(e.toString)
        false
      case e: Throwable =>
        error(e)
        false
    } finally {
      rest.close()
    }
  }

  private def prepare(rest: SonatypeService): StagingRepositoryProfile = {
    val descriptionKey = sonatypeSessionName
    // Drop a previous staging repository if exists
    val dropTask = Future.apply(rest.dropIfExistsByKey(descriptionKey))
    // Create a new one
    val createTask = Future.apply(rest.createStage(descriptionKey))
    // Run two tasks in parallel
    val merged                     = dropTask.zip(createTask)
    val (droppedRepo, createdRepo) = Await.result(merged, Duration.Inf)
    createdRepo
  }

  def openRepo(): Unit = {
    withSonatypeService() { rest =>
      rest.openOrCreateByKey(sonatypeSessionName)
    }
  }

  def bundleRelease(): Unit = {
    withSonatypeService() { rest =>
      val repo = prepare(rest)
      rest.uploadBundle(
        sonatypeSplitCoordinates.sonatypeGroupId,
        repo.deployUrl(sonatypeKeys.sonatypeRepository),
        filesPaths
      )
      rest.closeAndPromote(repo)
    }
  }
}
