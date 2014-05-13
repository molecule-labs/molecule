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

object Console {

  import java.io.{ BufferedReader }
  import java.io.{ FileDescriptor, FileInputStream, InputStreamReader }
  import java.nio.channels.Channels
  import platform.executors.SingleThreadedExecutor
  import platform.ThreadFactory

  private[this] lazy val stdinExecutor = SingleThreadedExecutor(new ThreadFactory("stdin", true))
  private[this] lazy val fdin = Channels.newInputStream(new FileInputStream(FileDescriptor.in).getChannel())

  import java.nio.ByteBuffer

  /**
   * Input channel that reads the standard input characters as they arrive.
   */
  lazy val stdinByteBuffer: IChan[ByteBuffer] =
    GetChan.wrap(stdinExecutor, GetChan.from(fdin, 128, false))

  /**
   * Input channel that reads the standard input line per line.
   */
  lazy val stdinLine: IChan[String] =
    GetChan.wrap(stdinExecutor, GetChan.from(new BufferedReader(new InputStreamReader(fdin)), false))

  /**
   * Channel that prints each segment it receives in a new line with a given prefix
   * on the standard output.
   */
  def logOut[A: Message](name: String): OChan[A] =
    // use core prime sieve example to stress this output
    notBlocking(PutChan.logOut[A](System.out, name, false))
  //wrap(stdoutExecutor, PutChan.logOut[A](System.out, name, false))

  /**
   * Channel that prints each segment it receives in a new line with a given prefix
   * on the standard error.
   */
  def logErr[A: Message](name: String): OChan[A] =
    notBlocking(PutChan.logOut[A](System.err, name, false))

  /**
   * Channel that prints each message it receives to a PrintStream.
   */
  def printer(name: String, out: java.io.PrintStream, closeOnSignal: Boolean): OChan[String] =
    PutChan.wrap(name, PutChan(out) { (out, s: String) => out.print(s); out }(out => if (closeOnSignal) out.close()))

  private[this] def stdPrinter(out: java.io.PrintStream): OChan[String] =
    notBlocking(PutChan(out) { (out, s: String) => out.print(s); out }(out => ()))

  /**
   * Channel that prints each string it receives on the standard output.
   */
  lazy val stdout: OChan[String] = stdPrinter(System.out)

  /**
   * Channel that prints each string it receives on the standard output.
   * Each string is separated by a new line.
   */
  lazy val stdoutLine: OChan[String] =
    notBlocking(PutChan(System.out) { (out, s: String) => out.print(s + utils.lineSep); out }(out => ()))

  import java.nio.charset.Charset
  import java.nio.{ ByteBuffer, CharBuffer }

  /**
   * Channel that prints character buffers on the standard output
   */
  def stdoutByteBuffer(decode: ByteBuffer => CharBuffer) = new OChan[ByteBuffer] {

    def write(seg: Seg[ByteBuffer], sigOpt: Option[Signal], k: OChan[ByteBuffer] => Unit): Unit = synchronized {

      if (!seg.isEmpty)
        seg.foreach { bb => print(decode(bb)) }

      if (sigOpt.isDefined) {
        close(sigOpt.get)
      } else
        k(this)
    }

    def close(signal: Signal) = {
      if (signal != EOS)
        System.err.println(signal)
      //System.out.close()
    }
  }

  /**
   * Channel that prints each string it receives on the standard error.
   */
  lazy val stderr: OChan[String] = stdPrinter(System.err)

  private[this] def notBlocking[A: Message](putChan: PutChan[A]): OChan[A] = {

    def mkOChan: OChan[A] = new OChan[A] {

      def write(seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit = {
        val next = try {
          putChan.put_!(seg)
        } catch {
          case t: Throwable =>
            seg.poison(Signal(t))
            k(OChan(Signal(t)))
            return
        }

        if (sigOpt.isDefined) {
          val signal = sigOpt.get
          next.close(signal)
          k(OChan(signal))
        } else
          k(notBlocking(next))

      }

      def close(signal: Signal): Unit = putChan.close(signal)

    }

    putChan match {
      case PutChan(signal) => OChan(signal)
      case _ => mkOChan
    }
  }

}