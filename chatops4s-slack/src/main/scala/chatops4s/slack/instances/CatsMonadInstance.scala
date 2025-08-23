package chatops4s.slack.instances

import cats.effect.{MonadCancelThrow, Sync}
import chatops4s.slack.Monad

given catsEffectMonadInstance[F[_]: Sync]: Monad[F] with {
  def pure[A](value: A): F[A]                     = Sync[F].pure(value)
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = Sync[F].flatMap(fa)(f)
  def raiseError[A](error: Throwable): F[A]       = Sync[F].raiseError(error)
}
