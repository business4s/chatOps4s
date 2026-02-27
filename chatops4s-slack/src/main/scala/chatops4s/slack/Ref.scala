package chatops4s.slack

import sttp.monad.MonadError

import java.util.concurrent.atomic.AtomicReference

// Thread-safety: Ref.update uses AtomicReference.updateAndGet (CAS-retry). Individual operations
// are atomic but get-then-update pairs are NOT. Fine for handler maps (registration at startup).
// For token rotation, see RefreshingSlackConfigApi.
private[slack] class Ref[F[_], A](ref: AtomicReference[A])(using monad: MonadError[F]) {
  def get: F[A]                  = monad.eval(ref.get())
  def update(f: A => A): F[Unit] = monad.eval { ref.updateAndGet(a => f(a)); () }
}

private[slack] object Ref {
  def of[F[_], A](initial: A)(using monad: MonadError[F]): F[Ref[F, A]] =
    monad.eval(new Ref(new AtomicReference(initial)))
}
