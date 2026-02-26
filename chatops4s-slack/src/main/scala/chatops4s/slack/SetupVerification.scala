package chatops4s.slack

import java.nio.file.Path

enum SetupVerification {
  case UpToDate
  case Created(path: Path, createAppUrl: String, message: String)
  case Changed(path: Path, diff: String, message: String)
}
