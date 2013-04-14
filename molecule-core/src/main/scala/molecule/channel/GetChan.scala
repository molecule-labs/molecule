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
 * Trait for blocking system-level input channels
 * with blocking and message-at-a-time semantics.
 *
 * This interface assumes that `get_!` and `poison` methods are invoked
 * sequentially.
 *
 * This trait is useful to wrap legacy blocking input interfaces and can easily
 * be wrapped in a non-blocking `IChan` using the `wrap` method in the
 * companion object (see the implementation of [[molecule.channel.Console]]
 * for examples).
 *
 * Note that the pattern captured by this trait is safer than the observer
 * pattern in multi-threaded settings because objects inheriting
 * this class can maintain state in an immutable manner by returning their
 * next state together with the next message.
 *
 * @tparam A The type of messages.
 */
trait GetChan[+A] {

  /**
   * Get a single message from this channel.
   *
   * This method will block until a a new message is
   * available.
   *
   * @return the message and the continuation of the channel.
   */
  def get_!(): (Either[Signal, (A, GetChan[A])])

  /**
   * Poison this channel and any message it may have buffered.
   *
   * @param signal the poison signal.
   * @return Unit
   */
  def poison(signal: Signal): Unit
}

object GetChan {

  private[this] val GetChanIsPoisonable = new Message[GetChan[Any]] {
    def poison(message: GetChan[Any], signal: Signal): Unit = {
      message.poison(signal)
    }
  }

  implicit def getChanIsMessage[A]: Message[GetChan[A]] = GetChanIsPoisonable

  private[this] class Nil(val signal: Signal) extends GetChan[Nothing] {

    def get_!(): (Either[Signal, (Nothing, GetChan[Nothing])]) =
      throw new Error("Cannot read an empty channel:" + signal)

    def poison(signal: Signal): Unit = {}

    override def equals(that: Any): Boolean = {
      that.isInstanceOf[Nil] && (this.signal == that.asInstanceOf[Nil].signal);
    }

    override def hashCode = signal.hashCode
  }

  def unapply(getChan: GetChan[_]): Option[Signal] =
    if (getChan.isInstanceOf[Nil])
      Some(getChan.asInstanceOf[Nil].signal)
    else
      None

  private[this] final val eos = new Nil(EOS)

  /**
   * Construct a blocking input channel that terminates immediately with a signal
   *
   *  @param signal the termination signal.
   *  @tparam       A the type of the messages carried by the input channel.
   *  @return An input channel that terminates immediately.
   */
  def empty[A](signal: Signal): GetChan[A] = signal match {
    case EOS => eos
    case _ => new Nil(signal)
  }

  /**
   * Construct a blocking input channel that terminates immediately with EOS
   *
   *  @param signal the termination signal.
   *  @tparam       A the type of the messages carried by the input channel.
   *  @return An input channel that terminates immediately.
   */
  def empty[A]: GetChan[A] = empty(EOS)

  def apply[I, A](blockingInput: I)(read: I => Option[(A, I)])(close: I => Unit): GetChan[A] =
    new GetChan[A] {
      def get_!(): (Either[Signal, (A, GetChan[A])]) =
        try {
          read(blockingInput) match {
            case Some((a, next)) => Right((a, apply(next)(read)(close)))
            case None => Left(EOS)
          }
        } catch {
          case t => Left(Signal(t))
        }
      def poison(signal: Signal): Unit = close(blockingInput)
    }

  import java.io.InputStream
  import java.nio.ByteBuffer

  def from(in: InputStream, bufferSize: Int, close: Boolean = true): GetChan[ByteBuffer] = GetChan(in) { in =>
    val buf = new Array[Byte](bufferSize)
    val count = in.read(buf)
    if (count < 0) None else Some((ByteBuffer.wrap(buf, 0, count), in))
  } { in => if (close) in.close() }

  import java.io.BufferedReader

  def from(br: BufferedReader, close: Boolean): GetChan[String] = GetChan(br) { in =>
    val message = br.readLine()
    if (message == null) None else Some((message, br))
  } { in => if (close) in.close() }

  def from(br: BufferedReader): GetChan[String] = from(br, true)

  import java.util.concurrent.Executor

  def wrap[A](executor: Executor, getChan: GetChan[A]): IChan[A] = {

    def mkIChan: IChan[A] = new IChan[A] {

      def read(k: (Seg[A], IChan[A]) => Unit): Unit = executor.execute(new Runnable {
        def run() {
          try {
            getChan.get_!() match {
              case Right((a, next)) => k(Seg(a), wrap(executor, next))
              case Left(signal) => k(Seg.empty, IChan.empty(signal))
            }
          } catch {
            case t => k(Seg.empty, IChan.empty(Signal(t)))
          }
        }
      })

      def poison(signal: Signal): Unit = getChan.poison(signal)

    }

    getChan match {
      case GetChan(signal) => IChan.empty(signal)
      case _ => mkIChan
    }
  }

  import platform.executors.SingleThreadedExecutor
  import platform.ThreadFactory

  def wrap[A](inputName: String, blockingChan: GetChan[A]): IChan[A] = {
    val executor = SingleThreadedExecutor(new ThreadFactory(inputName, true))
    wrap(executor, blockingChan)
  }

  def blocking[I, A](inputName: String)(blockingInput: I)(read: I => Option[(A, I)])(close: I => Unit): IChan[A] =
    wrap(inputName, GetChan(blockingInput)(read)(close))

  def blocking[I, A](executor: Executor)(blockingInput: I)(read: I => Option[(A, I)])(close: I => Unit): IChan[A] =
    wrap(executor, GetChan(blockingInput)(read)(close))

}
