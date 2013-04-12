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

import molecule._
import molecule.net._
import molecule.io._
import molecule.utils.{ encode, decode }
import molecule.utils.Unsigned.unsigned
import molecule.parsing.Parser

import java.nio.{ ByteBuffer, CharBuffer }

// Needed to convert from java array to scala array such that flatten works
import scala.collection.JavaConversions._

/**
 * Tested only with windows Telnet terminal.
 */
abstract class TelnetCharAdapter[R: Message](ptype: ProcessType1x1[Char, String, R]) extends ProcessType1x1[ByteBuffer, ByteBuffer, R] {

  import TelnetCharAdapter._

  def main(in: Input[ByteBuffer], out: Output[ByteBuffer]) =
    //out.write(doEchoCmd) >>
    //out.write(dontLinemodeCmd) >>
    handover {
      ptype(
        in.parse(telnetMsg).collect {
          case Character(c) => c
        },
        out.map(encode("US-ASCII")).map { s: String => CharBuffer.wrap(s) }
      )
    }
}

object TelnetCharAdapter {

  val IAC = 255.toByte

  import parsers.bytebuffer._
  import parsers.ascii

  abstract class TelnetMsg

  case class Command(operation: Byte, option: Byte) extends TelnetMsg {
    override def toString() = "Command(" + unsigned(operation) + "," + unsigned(option) + ")"
  }
  case class Character(c: Char) extends TelnetMsg

  lazy val telnetMsg: Parser[ByteBuffer, TelnetMsg] = command | char

  val command = (IAC ~ byteArray(2)) ^^ {
    case _ ~ arr => Command(arr(0), arr(1))
  }

  val char = ascii.anyChar ^^ Character

}

abstract class TelnetLineAdapter[R: Message](ptype: ProcessType1x1[String, String, R]) extends ProcessType1x1[ByteBuffer, ByteBuffer, R] {

  import TelnetLineAdapter._

  import seg._
  import parsers.charbuffer
  import java.nio.CharBuffer

  def main(in: Input[ByteBuffer], out: Output[ByteBuffer]) =
    //out.write(doEchoCmd) >>
    //out.write(dontLinemodeCmd) >>
    handover {
      ptype(
        in.parse(telnetMsg).collect {
          case Data(bb) => bb
        }.map(decode("US-ASCII")).parse(charbuffer.line(2048)),
        out.map(encode("US-ASCII")).map { s: String => CharBuffer.wrap(s.replaceAll("\n", "\r\n") + "\r\n") }
      )
    }
}

object TelnetLineAdapter {

  val IAC = 255.toByte

  import parsers.bytebuffer._

  abstract class TelnetMsg

  case class Data(cb: ByteBuffer) extends TelnetMsg
  case class Command(b1: Byte, b2: Byte) extends TelnetMsg {
    override def toString() = "Command(" + unsigned(b1) + "," + unsigned(b2) + ")"
  }

  lazy val telnetMsg: Parser[ByteBuffer, TelnetMsg] = data | command

  val data = splitAt(IAC) ^^ { Data(_) }

  val command = (IAC ~ byteArray(2)) ^^ {
    case _ ~ arr => Command(arr(0), arr(1))
  }

}
