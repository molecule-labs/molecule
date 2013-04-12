package molecule
package channel
package impl

private class Bridge[A](
    @volatile private var ichan: IChan[A],
    @volatile private var ochan: OChan[A]) {

  def start(): Unit =
    Bridge.connect(this, ichan, ochan, true)

}

private[channel] object Bridge {

  def apply[A](ichan: IChan[A], ochan: OChan[A]): Unit =
    new Bridge(ichan, ochan).start()

  private final def connect[A](
    b: Bridge[A],
    ichan: IChan[A], ochan: OChan[A], init: Boolean): Unit = {

    if (init) {
      ichan match {
        case IChan(signal) =>
          ochan.close(signal)
          return
        case _ =>
      }

      b.ichan = null
      b.ochan = null
    }

    ochan match {
      case OChan(signal) =>
        ichan.poison(signal)
      case _ =>
        ichan.read((seg, ichan) => ichan match {
          case IChan(signal) =>
            ochan.write(seg, Some(signal), utils.NOOP)
          case _ =>
            ochan.write(seg, None, ochan => connect(b, ichan, ochan, false))
        })
    }
  }
}