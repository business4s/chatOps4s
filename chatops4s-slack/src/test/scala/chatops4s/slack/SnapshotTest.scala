package chatops4s.slack

import org.scalatest.Assertions

import java.nio.file.{Files, Paths}

object SnapshotTest {

  private val testResourcesPath = Paths
    .get(getClass.getResource("/").toURI)
    .getParent // target/scala-x.y.z
    .getParent // target
    .getParent // chatops4s-slack
    .resolve("src/test/resources")

  def testSnapshot(content: String, path: String): Unit = {
    val filePath    = testResourcesPath.resolve(path)
    val existingOpt = Option.when(Files.exists(filePath)) {
      Files.readString(testResourcesPath.resolve(path))
    }

    val isOk = existingOpt.contains(content)

    if (!isOk) {
      Files.createDirectories(filePath.getParent)
      Files.writeString(filePath, content)
      Assertions.fail(s"Snapshot $path was not matching. A new value has been written to $filePath.")
    }
  }
}
