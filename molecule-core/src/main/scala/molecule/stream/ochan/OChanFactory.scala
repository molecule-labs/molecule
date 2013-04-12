package molecule
package stream
package ochan

class OChanFactory[-A](val mk: () => OChan[A]) extends Function0[OChan[A]] {

  def apply(): OChan[A] = mk()

  def transform[B](f: OChan[A] => OChan[B]): OChanFactory[B] =
    OChanFactory(() => f(mk()))

}

object OChanFactory {

  def apply[A](mk: () => OChan[A]): OChanFactory[A] = new OChanFactory(mk)

  def lift[A: Message](factory: channel.OChanFactory[A]): OChanFactory[A] = apply(() => OChan.lift(factory()))

  implicit def ochanFactoryIsMessage[A]: Message[OChanFactory[A]] = PureMessage

}