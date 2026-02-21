package chatops4s.slack

import java.net.URLEncoder
import java.nio.file.{Files, Path}

object ManifestCheck {

  class ManifestException(message: String) extends RuntimeException(message)

  class ManifestCreated(val path: Path, guide: String) extends ManifestException(guide)

  class ManifestChanged(val path: Path, diff: String) extends ManifestException(diff)

  def verify(manifest: String, appName: String, path: Path): Unit = {
    if (!Files.exists(path)) {
      val parent = path.getParent
      if (parent != null) Files.createDirectories(parent): Unit
      Files.writeString(path, manifest)
      val url    = createAppUrl(manifest)
      val guide  =
        s"""Slack manifest written to $path
           |
           |This looks like a first-time setup. To create your Slack app:
           |
           |1. Open this URL to create the app from the manifest:
           |   $url
           |
           |2. Once created, install the app to your workspace. (App settings > Install App)
           |
           |3. Use the following tokens for SlackGateway.start:
           |   bot token: xoxb-...  (from OAuth & Permissions > Bot User OAuth Token)
           |   app token: xapp-...  (from Basic Information > App-Level Tokens, with connections:write scope)
           |""".stripMargin
      throw new ManifestCreated(path, guide)
    }

    val existing = Files.readString(path)
    if (existing == manifest) return

    Files.writeString(path, manifest)
    val diff    = buildDiff(existing, manifest)
    val message =
      s"""Slack manifest has changed. Updated $path
         |
         |Please update your Slack app manifest to match:
         |  App settings > App Manifest > paste the contents of $path
         |
         |Changes:
         |$diff
         |""".stripMargin
    throw new ManifestChanged(path, message)
  }

  def createAppUrl(manifest: String): String = {
    val encoded = URLEncoder.encode(manifest, "UTF-8")
    s"https://api.slack.com/apps?new_app=1&manifest_json=$encoded"
  }

  private def buildDiff(old: String, current: String): String = {
    val oldLines = old.linesIterator.toVector
    val newLines = current.linesIterator.toVector
    val lcs      = longestCommonSubsequence(oldLines, newLines)
    formatDiff(oldLines, newLines, lcs).mkString("\n")
  }

  private def longestCommonSubsequence(a: Vector[String], b: Vector[String]): Vector[String] = {
    val dp = Array.ofDim[Int](a.length + 1, b.length + 1)
    for (i <- 1 to a.length; j <- 1 to b.length)
      dp(i)(j) =
        if (a(i - 1) == b(j - 1)) dp(i - 1)(j - 1) + 1
        else math.max(dp(i - 1)(j), dp(i)(j - 1))

    @annotation.tailrec
    def backtrack(i: Int, j: Int, acc: List[String]): List[String] =
      if (i == 0 || j == 0) acc
      else if (a(i - 1) == b(j - 1)) backtrack(i - 1, j - 1, a(i - 1) :: acc)
      else if (dp(i - 1)(j) >= dp(i)(j - 1)) backtrack(i - 1, j, acc)
      else backtrack(i, j - 1, acc)

    backtrack(a.length, b.length, Nil).toVector
  }

  private def formatDiff(
      oldLines: Vector[String],
      newLines: Vector[String],
      common: Vector[String],
  ): Vector[String] = {
    val buf = Vector.newBuilder[String]
    var oi  = 0
    var ni  = 0
    for (line <- common) {
      while (oi < oldLines.length && oldLines(oi) != line) { buf += s"- ${oldLines(oi)}"; oi += 1 }
      while (ni < newLines.length && newLines(ni) != line) { buf += s"+ ${newLines(ni)}"; ni += 1 }
      buf += s"  $line"
      oi += 1
      ni += 1
    }
    while (oi < oldLines.length) { buf += s"- ${oldLines(oi)}"; oi += 1 }
    while (ni < newLines.length) { buf += s"+ ${newLines(ni)}"; ni += 1 }
    buf.result()
  }
}
