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

package molecule.examples.net

/**
 * Todo
 *
 */
object Telnet {

  import java.nio.ByteBuffer

  trait Serializable {
    def toNet: List[String]
  }

  /**
   * Interpret as character command
   */
  val IAC = 255.toByte
  /**
   * End of subnegotiation parameters.
   */
  val SE = 240.toByte
  /**
   * No operation
   */
  val NOP = 241.toByte
  /**
   * Data mark. Indicates the position of a Synch event within the data stream.
   * This should always be accompanied by a TCP urgent notification.
   */
  val DM = 242.toByte
  /**
   * Break. Indicates that the "break" or "attention" key was hit.
   */
  val BRK = 243.toByte
  /**
   * Suspend, interrupt or abort the process to which the NVT is connected.
   */
  val IP = 244.toByte
  /**
   * Abort output. Allows the current process to run to completion but
   * do not send its output to the user.
   */
  val AO = 245.toByte
  /**
   * Are you there. Send back to the NVT some visible evidence that the AYT
   * was received.
   */
  val AYT = 246.toByte
  /**
   * Erase character. The receiver should delete the last preceding undeleted
   * character from the data stream.
   */
  val EC = 247.toByte
  /**
   * Erase line. Delete characters from the data stream back to but not
   * including the previous CRLF.
   */
  val EL = 248.toByte
  /**
   * Go ahead. Used, under certain circumstances, to tell the other end that
   * it can transmit.
   */
  val GA = 249.toByte
  /**
   * Subnegotiation of the indicated option follows
   */
  val SB = 250.toByte
  /**
   * Indicates the desire to begin performing, or confirmation that you are now
   * performing, the indicated option.
   */
  val WILL = 251.toByte
  /**
   * Indicates the refusal to perform, or continue performing, the indicated
   * option.
   */
  val WONT = 252.toByte
  /**
   * Indicates the request that the other party perform, or confirmation
   * that you are expecting the other party to perform, the indicated option.
   */
  val DO = 253.toByte
  /**
   * Indicates the demand that the other party stop performing, or confirmation that
   * you are no longer expecting the other party to perform, the indicated option.
   */
  val DONT = 254.toByte

  val LINEMODE = 34.toByte
  val ECHO = 1.toByte

  def iac(operation: Byte, option: Byte) = {
    val bb = ByteBuffer.allocate(3)
    bb.put(IAC); bb.put(operation); bb.put(option)
    bb.flip
    bb
  }

  def suppressEchoCmd = iac(WILL, ECHO)

  def doEchoCmd = iac(DO, ECHO)

  def dontLinemodeCmd = iac(DONT, LINEMODE)

  sealed abstract class Opt(value: Byte) {
    type SubOpt <: Serializable
  }

  /**
   * Echo.
   * RFC 857
   */
  case object Echo extends Opt(1.toByte) {
    type SubOpt = Nothing
  }

  /**
   * suppress go ahead
   * RFC 858
   */
  case object Sgh extends Opt(3.toByte) {
    type SubOpt = Nothing
  }

  /**
   * Status
   * RFC 859
   */
  case object Status extends Opt(5.toByte) {
    type SubOpt = Nothing
  }
  /**
   * Timing mark
   * RFC 860
   */
  case object TimingMark extends Opt(6.toByte) {
    type SubOpt = Nothing
  }
  /**
   * Terminal type
   * RFC 1091
   */
  case object TerminalType extends Opt(24.toByte) {
    type SubOpt = Nothing
  }
  /**
   * Window size
   * RFC 1073
   */
  case object WindowSize extends Opt(31.toByte) {
    type SubOpt = Nothing
  }
  /**
   * Terminal speed
   * RFC 1079
   */
  case object TerminalSpeed extends Opt(32.toByte) {
    type SubOpt = Nothing
  }
  /**
   * Remote flow control
   * RFC 1372
   */
  case object RemoteFlowControl extends Opt(33.toByte) {
    type SubOpt = Nothing
  }
  /**
   * Line mode
   * RFC 1184
   */
  case object LineMode extends Opt(34.toByte) {
    type SubOpt = Nothing
  }

  /**
   * Environment variables
   * RFC 1408
   * http://www.faqs.org/rfcs/rfc1408.html
   */
  case object Environ extends Opt(36.toByte) {

    type SubOpt = SubOption

    object Type extends Enumeration {
      type Type = Value

      val VAR = Value(0, "VAR")
      val VALUE = Value(1, "VALUE")
      val ESC = Value(2, "ESC")
      val USERVAR = Value(3, "USERVAR")
    }
    import Type._

    sealed trait SubOption extends Serializable
    case class Is(vars: (Type, String, String)*) extends Opt(0.toByte) with SubOption {
      def toNet() = List(vars.map { case (t, n, v) => t + " " + n + " " + v }.mkString(" "))
    }

    case class Send(vars: (Type, Option[String])*) extends Opt(1.toByte) with SubOption {
      def toNet() = List(vars.map { case (t, n) => t + (if (n.isDefined) (" " + n) else "") }.mkString(" "))
    }
    case class Info(vars: (Type, String, String)*) extends Opt(2.toByte) with SubOption {
      def toNet() = List(vars.map { case (t, n, v) => t + " " + n + " " + v }.mkString(" "))
    }
  }

  abstract class Command(cmd: Byte, opt: Opt)
  abstract class NegotiatedCommand[O <: Opt](cmd: Byte, opt: Opt, subopt: Option[O#SubOpt]) extends Command(cmd, opt)

  case class EchoCommand(cmd: Byte) extends Command(cmd, Echo)
  case class EnvironCommand(cmd: Byte, subopt: Option[Environ.SubOpt] = None) extends NegotiatedCommand[Environ.type](cmd, Environ, subopt)

}