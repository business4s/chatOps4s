package chatops4s.slack

import sttp.monad.MonadError
import sttp.monad.syntax.*

private[slack] object monadSyntax {

  extension [F[_], A](fa: F[A])(using monad: MonadError[F]) {
    def void: F[Unit] = fa.map(_ => ())
    def as[B](b: B): F[B] = fa.map(_ => b)
    def >>[B](fb: => F[B]): F[B] = fa.flatMap(_ => fb)
  }

  extension [F[_], A](list: List[A])(using monad: MonadError[F]) {
    def traverse_[B](f: A => F[B]): F[Unit] =
      list.foldLeft(monad.unit(()))((acc, a) => acc.flatMap(_ => f(a).map(_ => ())))
  }

  extension [F[_], A](opt: Option[A])(using monad: MonadError[F]) {
    def traverse_[B](f: A => F[B]): F[Unit] =
      opt match {
        case Some(a) => f(a).map(_ => ())
        case None    => monad.unit(())
      }
  }
}
