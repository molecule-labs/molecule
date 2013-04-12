/*
 * Copyright (C) 2013 Alcatel-Lucent.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package molecule
package channel

/**
 * Trait for blocking system-level output channels.
 *
 * This interface assumes that `put_!` and `close`
 * methods are invoked sequentially.
 *
 * This trait is useful to wrap legacy blocking output interfaces and can easily
 * be wrapped in a non-blocking `OChan` using `OChan.wrap` method
 * (see `IChan.stdout`, which wraps standard output).
 *
 * @tparam A The type of messages.
 */
trait PutChan[-A] {

  /**
   * Put a segment on this channel.
   *
   * This method may block if there is no space available on the channel.
   *
   * @param a segment.
   *
   * @return The continuation of this channel on which the next message must be put.
   */
  def put_!(as: Seg[A]): PutChan[A]

  /**
   * Close this channel.
   *
   * Subsequently to a call to this method, any new segment written to this channel will be poisoned.
   * Contrarily to the poison method on input channels, messages buffered are not poisoned and
   * may still be read by a consumer listening on the other end of this channel.
   *
   * @param signal the termination signal.
   * @return Unit
   */
  def close(signal: Signal): Unit
}

object PutChan {

  private[this] val PutChanIsPoisonable = new Message[PutChan[Nothing]] {
    def poison(message: PutChan[Nothing], signal: Signal): Unit = {
      message.close(signal)
    }
  }

  implicit def PutChanIsMessage[A]: Message[PutChan[A]] = PutChanIsPoisonable

  private[this] class Nil[A: Message](val signal: Signal) extends PutChan[A] {

    def put_!(as: Seg[A]): PutChan[A] = {
      as.poison(signal)
      throw new Error("Cannot write on NilChan:" + signal)
    }

    def close(signal: Signal): Unit = {}

    override def equals(that: Any): Boolean = {
      that.isInstanceOf[Nil[_]] && (this.signal == that.asInstanceOf[Nil[_]].signal);
    }

    override def hashCode = signal.hashCode
  }

  def apply[A: Message](signal: Signal): PutChan[A] =
    new Nil(signal)

  def unapply(PutChan: PutChan[_]): Option[Signal] =
    if (PutChan.isInstanceOf[Nil[_]])
      Some(PutChan.asInstanceOf[Nil[_]].signal)
    else
      None

  def apply[O, A: Message](out: O)(write: (O, A) => O)(close: O => Unit): PutChan[A] = {
    val _close = close
    new PutChan[A] {
      def put_!(as: Seg[A]): PutChan[A] = try {
        apply(as.foldLeft(out)((out, a) => write(out, a)))(write)(_close)
      } catch { case t => PutChan[A](Signal(t)) }
      def close(signal: Signal) = _close(out)
    }
  }

  def batch[O, A: Message](out: O)(write: (O, Seg[A]) => O)(close: (O, Signal) => Unit): PutChan[A] = {
    val _close = close
    new PutChan[A] {
      def put_!(as: Seg[A]): PutChan[A] = try {
        batch(write(out, as))(write)(_close)
      } catch { case t => PutChan[A](Signal(t)) }
      def close(signal: Signal) = _close(out, signal)
    }
  }

  import java.io.PrintStream
  def to(ps: PrintStream, close: Boolean): PutChan[String] = PutChan(ps) { (ps, s: String) =>
    ps.print(s)
    ps
  } { ps => if (close) ps.close() }

  def logOut[A: Message](ps: PrintStream, name: String, close: Boolean): PutChan[A] = PutChan.batch(ps) { (ps, seg: Seg[A]) =>
    if (!seg.isEmpty)
      ps.println(name + ":" + seg)
    ps
  } { (ps, signal) => ps.println(name + ":" + signal); if (close) ps.close() }

  import java.util.concurrent.Executor

  def wrap[A: Message](executor: Executor, putChan: PutChan[A]): OChan[A] = {

    def mkOChan: OChan[A] = new OChan[A] {

      def write(seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit = executor.execute(new Runnable {
        def run() {
          val next = try {
            putChan.put_!(seg)
          } catch {
            case t =>
              seg.poison(Signal(t))
              k(OChan(Signal(t)))
              return
          }

          if (sigOpt.isDefined) {
            try {
              val signal = sigOpt.get
              next.close(signal)
              k(OChan(signal)) // This can cause a RejectedExecutionException if the core pool is shutdown ahead 
            } catch { case _ => () }
          } else
            k(wrap(executor, next))

        }
      })

      def close(signal: Signal): Unit = putChan.close(signal)

    }

    putChan match {
      case PutChan(signal) => OChan(signal)
      case _ => mkOChan
    }
  }

  import platform.executors.SingleThreadedExecutor
  import platform.ThreadFactory

  def wrap[A: Message](outputName: String, blockingChan: PutChan[A]): OChan[A] = {
    val executor = SingleThreadedExecutor(new ThreadFactory(outputName, true))
    wrap(executor, blockingChan)
  }

  def blocking[O, A: Message](outputName: String)(blockingOutput: O)(write: (O, A) => O)(close: O => Unit): OChan[A] =
    wrap(outputName, PutChan(blockingOutput)(write)(close))

  def blocking[O, A: Message](executor: Executor)(blockingOutput: O)(write: (O, A) => O)(close: O => Unit): OChan[A] =
    wrap(executor, PutChan(blockingOutput)(write)(close))

}
