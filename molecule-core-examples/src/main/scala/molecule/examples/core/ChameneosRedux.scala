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

package molecule.examples.core

import molecule._

import molecule.request._
import molecule.request.core._

import molecule.channel.{ RIChan }
import molecule.stream._

object ChameneosRedux {

  /**
   * Colors
   */
  sealed abstract class Color
  case object Blue extends Color
  case object Red extends Color
  case object Yellow extends Color

  val colorTypes = Array(Blue, Red, Yellow)

  /**
   * Chameneos Identity
   */
  case class ChameneoId(id: Int, color: Color)

  /**
   * Message sent by each chameneos to the ChameneosRedux application
   * when the Mall is closed
   */
  case class ChameneoReport(nbMeetings: Int)

  /**
   * Messages sent to chameneos
   */
  abstract class ChameneoMessage
  // Message sent by the mall to the first chameneos with the channel to the second chameneos
  // The first chameneos can only send a copy message on that channel and nothing else
  case class MeetRequest(peer: ChameneoId)(val rchan: ResponseChannel[Copy]) extends ChameneoMessage with Response[Copy]
  // Message sent by the mall to the second chameneos by the first chameneos
  case class Copy(peer: ChameneoId) extends ChameneoMessage

  // Message definition is the union of its child types
  // Don't define explicitly this type else it will mess up inference
  implicit val ChameneoMessageIsMessage = Message[ChameneoMessage](Message[MeetRequest], Message[Copy])

  /**
   * Messages sent to the mall service
   */
  case class MallRequest(id: ChameneoId)(val rchan: ResponseChannel[ChameneoMessage]) extends Response[ChameneoMessage] {
    override def toString = "MallRequest(" + id + "," + rchan.hashCode + ")"
  }

  import process.CoreProcess

  /**
   * Mall service
   */
  def mall(nbMeetings: Int, requests: IChan[MallRequest]) =
    CoreProcess.singleton("mall", {

      requests.grouped(2).take(nbMeetings).foreach { mates =>
        val Seg(first, second) = mates
        //println(Thread.currentThread + ":meet")
        first.rchan.reply_!(MeetRequest(second.id)(second.rchan))
      }

    })

  /**
   * Chameneos Factory
   */
  val chameneo =
    CoreProcess.factory[(ChameneoId, OChan[MallRequest]), Int]("chameneo", {
      case ((id, mall), t) =>

        def complement(c1: Color, c2: Color) = (c1, c2) match {
          case (Blue, Blue) => Blue
          case (Blue, Red) => Yellow
          case (Blue, Yellow) => Red
          case (Red, Blue) => Yellow
          case (Red, Red) => Red
          case (Red, Yellow) => Blue
          case (Yellow, Blue) => Red
          case (Yellow, Red) => Blue
          case (Yellow, Yellow) => Yellow
        }

        def behave(mall: OChan[MallRequest], nbMeetings: Int, self: ChameneoId, exit: Int => RIChan[Nothing]): RIChan[Nothing] = {
          //println(Thread.currentThread + ":" + t.hashCode + ":->request")
          mall.request(t, MallRequest(self)).orCatch { case RequestSignal(_, EOS) => exit(nbMeetings) }.flatMap {
            case (mall, result) => result match {
              case req @ MeetRequest(other) =>
                val newColor = complement(self.color, other.color)
                val newSelf = self.copy(color = newColor)

                // println(Thread.currentThread + ":" + t.hashCode + ":request")
                req.rchan.reply_!(Copy(newSelf))
                behave(mall, nbMeetings + 1, newSelf, exit)

              case Copy(other) =>
                val newSelf = self.copy(color = other.color)

                // println(Thread.currentThread + ":" + t.hashCode + ":copy")
                behave(mall, nbMeetings + 1, newSelf, exit)
            }
          }
        }

        RIChan.callcc(behave(mall, 0, id, _))
    })

  import molecule.platform.Platform
  import molecule.channel.{ Chan, ManyToOne }

  def run(mallPlatform: Platform, reduxPlatform: Platform, nbChameneos: Int, nbMeetings: Int, sst: Int): Unit = {
    assert(nbChameneos > 1, "Number of chameneos should be greater than 1")

    val (ichan, mkOChan) = ManyToOne.mk[MallRequest](sst)

    mallPlatform.launch(mall(nbMeetings, ichan))

    val colors = (0 until nbChameneos) map { i => colorTypes(i % colorTypes.length) }

    val chameneos: Seq[RIChan[Int]] = colors.zipWithIndex.map {
      case (color, i) =>
        val id = ChameneoId(i, color)
        reduxPlatform.launch(chameneo(id, mkOChan()))
    }

    val report = RIChan.parl(chameneos).map { _.sum }.get_!()
    // or, since it is acceptable to block here:
    // val report = chameneos.map(_.get_!()).sum 

    println(report)
  }

  def main(args: Array[String]): Unit = {
    val platform = Platform("chameneos-redux", nbThreads = 12)
    run(platform, platform, 120, 60000, 0)
  }
}