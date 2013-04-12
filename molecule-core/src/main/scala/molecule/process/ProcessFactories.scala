package molecule
package process

trait ProcessFactories {

  import stream.{ IChan, OChan }

  type ProcessFactory0x0[+R] = () => Process[R]

  type ProcessFactory1x0[-A, +R] = (IChan[A]) => Process[R]
  type ProcessFactory0x1[+E, +R] = (OChan[E]) => Process[R]
  type ProcessFactory1x1[-A, +E, +R] = (IChan[A], OChan[E]) => Process[R]

  type ProcessFactory2x0[-A, -B, +R] = (IChan[A], IChan[B]) => Process[R]
  type ProcessFactory2x1[-A, -B, +E, +R] = (IChan[A], IChan[B], OChan[E]) => Process[R]
  type ProcessFactory0x2[+E, +F, +R] = (OChan[E], OChan[F]) => Process[R]
  type ProcessFactory1x2[-A, +E, +F, +R] = (IChan[A], OChan[E], OChan[F]) => Process[R]
  type ProcessFactory2x2[-A, -B, +E, +F, +R] = (IChan[A], IChan[B], OChan[E], OChan[F]) => Process[R]

  type ProcessFactory3x0[-A, -B, -C, +R] = (IChan[A], IChan[B], IChan[C]) => Process[R]
  type ProcessFactory3x1[-A, -B, -C, +E, +R] = (IChan[A], IChan[B], IChan[C], OChan[E]) => Process[R]
  type ProcessFactory3x2[-A, -B, -C, +E, +F, +R] = (IChan[A], IChan[B], IChan[C], OChan[E], OChan[F]) => Process[R]
  type ProcessFactory0x3[+E, +F, +G, +R] = (OChan[E], OChan[F], OChan[G]) => Process[R]
  type ProcessFactory1x3[-A, +E, +F, +G, +R] = (IChan[A], OChan[E], OChan[F], OChan[G]) => Process[R]
  type ProcessFactory2x3[-A, -B, +E, +F, +G, +R] = (IChan[A], IChan[B], OChan[E], OChan[F], OChan[G]) => Process[R]
  type ProcessFactory3x3[-A, -B, -C, +E, +F, +G, +R] = (IChan[A], IChan[B], IChan[C], OChan[E], OChan[F], OChan[G]) => Process[R]

}