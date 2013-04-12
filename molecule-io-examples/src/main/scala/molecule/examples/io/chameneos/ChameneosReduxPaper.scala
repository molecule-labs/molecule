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

/**
 * Distributed version of chameneos redux where a chameneos changes
 * its color and passes the result to the peer chameneos using a direct
 * P2P connection.
 *
 * Kaiser, C. and J.F. Pradat-Peyre (2003). "Chameneos, a concurrency game for Java,
 * Ada and others." In ACS/IEEE Int. Conf. AICCSA'03, IEEE Computer Society
 * Press, Los Alamitos.
 */
package molecule.examples.io.chameneos

import molecule._
import molecule.channel.{ Chan, ManyToOne, OChanFactory }
import molecule.io._

object ChameneosReduxPaper {

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

  type ReplyCh[-A] = channel.OChan[A]

  trait ReplyChannel[-A] {
    def replyCh: ReplyCh[A]
  }

  //implicit def rpIsMessage[A]:Message[ReplyChannel[A]] = new Message[ReplyChannel[A]] {
  //  def poison(m:ReplyChannel[A], signal:Signal):Unit = m.replyCh.close(signal)
  //}

  implicit def rpIsMessage[R <: ReplyChannel[_]]: Message[R] = new Message[R] {
    def poison(m: R, signal: Signal): Unit = m.replyCh.close(signal)
  }

  def requestTo[Rq <: ReplyChannel[Rp], Rp: Message](out: Output[Rq])(mkReq: ReplyCh[Rp] => Rq): IO[Rp] = {
    val (i, o) = Chan.mk[Rp]
    out.write(mkReq(o)) >> open(i) >>\ { _.read() }
  }
  def replyTo[A: Message](ch: ReplyChannel[A])(a: A): IO[Unit] =
    open(ch.replyCh) >>\ { out =>
      out.write(a) >> out.close()
    }

  /**
   * Messages sent to chameneos
   */
  abstract class ChameneoMessage
  // Message sent by the mall to the first chameneos with the channel to the second chameneos
  // The first chameneos can only send a copy message on that channel and nothing else
  case class MeetRequest(peer: ChameneoId)(
    val replyCh: ReplyCh[Copy]) extends ChameneoMessage with ReplyChannel[Copy]
  // Message sent by the mall to the second chameneos by the first chameneos
  case class Copy(peer: ChameneoId) extends ChameneoMessage

  // Message definition is the union of its child types
  // Don't define explicitly this type else it will mess up inference
  implicit val ChameneoMessageIsMessage = Message[ChameneoMessage](Message[MeetRequest], Message[Copy])

  /**
   * Messages sent to the mall service
   */
  case class MallRequest(id: ChameneoId)(
      val replyCh: ReplyCh[ChameneoMessage]) extends ReplyChannel[ChameneoMessage] {
    override def toString = "MallRequest(" + id + "," + replyCh.hashCode + ")"
  }

  /**
   * Mall servie
   */
  object Mall extends ProcessType2x0[Int, MallRequest, Unit] {
    override def name = "Mall"

    def main(cfg: Input[Int], requests: Input[MallRequest]) =
      for {
        nbMeetings <- cfg.read()
        _ <- requests.grouped(2).take(nbMeetings).foreach { mates =>
          val Seg(first, second) = mates
          replyTo(first)(MeetRequest(second.id)(second.replyCh)).orCatch { case s => IO(println(s)) }
        }
      } yield ()
  }

  /**
   * Chameneos factory
   */
  object Chameneo extends ProcessType1x1[ChameneoId, MallRequest, Int] {

    override def name = "Chameneo"

    private def complement(c1: Color, c2: Color) = (c1, c2) match {
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

    def main(cfg: Input[ChameneoId], mall: Output[MallRequest]): IO[Int] = {

      def behave(nbMeetings: Int, selfId: ChameneoId): IO[Nothing] =
        {
          requestTo(mall)(MallRequest(selfId)).orCatch {
            case EOS => shutdown(nbMeetings)
          }
        } >>\ {
          case other @ MeetRequest(otherId) =>
            val newColor = complement(selfId.color, otherId.color)
            val newSelfId = selfId.copy(color = newColor)

            replyTo(other)(Copy(newSelfId)) >> behave(nbMeetings + 1, newSelfId)
          case Copy(otherId) =>

            val newSelfId = selfId.copy(color = otherId.color)

            behave(nbMeetings + 1, newSelfId)
        }

      for {
        id <- cfg.read()
        nbMeetings <- behave(0, id)
      } yield nbMeetings
    }
  }

  /**
   * Master application: Launch chameneos on a given mall and collect reports
   */
  object ChameneosRedux extends ProcessType1x0[(Seq[Color], OChanFactory[MallRequest]), Unit] {
    override def name = "ChameneosRedux"

    def main(cfg: Input[(Seq[Color], OChanFactory[MallRequest])]) = {

      def start(colors: Seq[Color], mallCh: OChanFactory[MallRequest]): IO[Unit] = {

        val supervisorThreads: Seq[IO[Int]] = colors.zipWithIndex.map {
          case (color, i) =>

            val id = ChameneoId(i, color)

            //{launch(Chameneo(id, mallCh.clientChannel)).get() >>&\ {n => ioLog(id +":" + n)}} orCatch {case s => println(s); IO(0)}
            launch(Chameneo(id.asI, mallCh())).get() orCatch { case s => println(s); IO(0) }
        }

        // Launch threads in parallel on available CPU's in round robin fashion
        parl(supervisorThreads).map { _.sum } >>\ { s => println(s); IO() }
      }

      for {
        (colors, mallCh) <- cfg.read()
        _ <- start(colors, mallCh)
      } yield ()
    }
  }

  import molecule.platform.Platform

  def run(mallPlatform: Platform, reduxPlatform: Platform, nbChameneos: Int, nbMeetings: Int): Unit = {
    assert(nbChameneos > 1, "Number of chameneos should be greater than 1")

    val (ichan, mkOChan) = ManyToOne.mk[MallRequest](nbChameneos)

    mallPlatform.launch(Mall(nbMeetings.asI, ichan))

    val colors = (0 until nbChameneos) map { i => colorTypes(i % colorTypes.length) }

    reduxPlatform.launch(ChameneosRedux((colors, mkOChan).asI)).get_!()
  }

  def main(args: Array[String]): Unit = {
    val platform = Platform("chameneos-redux") //debug = true
    run(platform, platform, 12, 600) // 12, 600
  }
}