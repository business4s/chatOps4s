package chatops4s.slack

import chatops4s.slack.api.{UserId, users}
import sttp.monad.MonadError
import sttp.monad.syntax.*

import java.time.{Duration, Instant}

trait UserInfoCache[F[_]] {
  def get(userId: UserId): F[Option[users.UserInfo]]
  def put(userId: UserId, info: users.UserInfo): F[Unit]
}

object UserInfoCache {

  def inMemory[F[_]](
      ttl: Duration = Duration.ofMinutes(15),
      maxEntries: Int = 1000,
  )(using monad: MonadError[F]): F[UserInfoCache[F]] =
    inMemoryWithClock(ttl, maxEntries, () => Instant.now())

  private[slack] def inMemoryWithClock[F[_]](
      ttl: Duration,
      maxEntries: Int,
      clock: () => Instant,
  )(using monad: MonadError[F]): F[UserInfoCache[F]] =
    Ref.of[F, Map[UserId, CacheEntry]](Map.empty).map { ref =>
      new InMemoryUserInfoCache[F](ref, ttl, maxEntries, clock)
    }

  def noCache[F[_]](using monad: MonadError[F]): UserInfoCache[F] =
    new UserInfoCache[F] {
      def get(userId: UserId): F[Option[users.UserInfo]] = monad.unit(None)
      def put(userId: UserId, info: users.UserInfo): F[Unit] = monad.unit(())
    }

  private case class CacheEntry(value: users.UserInfo, insertedAt: Instant)

  private class InMemoryUserInfoCache[F[_]](
      ref: Ref[F, Map[UserId, CacheEntry]],
      ttl: Duration,
      maxEntries: Int,
      clock: () => Instant,
  )(using monad: MonadError[F])
      extends UserInfoCache[F] {

    def get(userId: UserId): F[Option[users.UserInfo]] =
      ref.get.map { entries =>
        entries.get(userId).collect {
          case entry if !isExpired(entry) => entry.value
        }
      }

    def put(userId: UserId, info: users.UserInfo): F[Unit] = {
      val now = clock()
      ref.update { entries =>
        val withNew = entries + (userId -> CacheEntry(info, now))
        val swept = withNew.filter { case (_, entry) => !isExpired(entry, now) }
        if (swept.size > maxEntries) {
          swept.toList.sortBy(_._2.insertedAt).drop(swept.size - maxEntries).toMap
        } else swept
      }
    }

    private def isExpired(entry: CacheEntry): Boolean =
      isExpired(entry, clock())

    private def isExpired(entry: CacheEntry, now: Instant): Boolean =
      Duration.between(entry.insertedAt, now).compareTo(ttl) > 0
  }
}
