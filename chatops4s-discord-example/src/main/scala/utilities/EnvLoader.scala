package utilities

import scala.io.Source

object EnvLoader {
  def loadEnv(path: String = ".env"): Unit = {
    val source = Source.fromFile(path)
    for (line <- source.getLines()) {
      val trimmed = line.trim
      if (!trimmed.startsWith("#") && trimmed.contains("=")) {
        val Array(key, value) = trimmed.split("=", 2)
        sys.props += (key -> value)
      }
    }
    source.close()
  }

  def get(key: String): String = {
    sys.props
      .get(key)
      .getOrElse(
        throw new RuntimeException(s"Environment variable $key not found"),
      )
  }
}
