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
 * Copyright(c) 2012 - Alcatel-Lucent - All rights reserved.
 */
package molecule
package examples.parsing.contramap

import platform._
import io._
import stream._
import seg.Seg
import parsing._
import channel.Console
import scala.util.matching.Regex

/**
 * @author Koen Daenen
 */
object TestContraMap {

  case class Sentence(sentence: String) extends Parser[String, String] {
    private val MatchAdot = """(.*)\.\s*""".r
    private val MatchAdotB = """(.*)\.\s*(.+)""".r

    final val name: String = "Sentence"

    final private def addWord(sentence: String, word: String): String = {
      if (sentence == "") word
      else sentence + " " + word
    }

    final private def loop(sentence: String, seg: Seg[String]): ParseResult[String, String] = {
      if (seg.isEmpty) Partial(Sentence(sentence))
      else {
        seg.head match {
          case MatchAdot(a) => Done(addWord(sentence, a) + ".", seg.tail)
          case MatchAdotB(a, b) => Done(addWord(sentence, a) + ".", b +: seg.tail)
          case s: String =>
            if (seg.length == 1) Partial(Sentence(addWord(sentence, s)))
            else loop(addWord(sentence, s), seg.tail)
        }
      }
    }

    def apply(seg: Seg[String]): ParseResult[String, String] =
      loop("", seg)

    def result(signal: Signal): Option[Either[Fail, Done[String, String]]] =
      if (sentence.length > 0) {
        Some(Right(Done(sentence + ".", NilSeg)))
      } else
        None

  }

  val sentenseParser = Sentence("")

  def main(args: Array[String]): Unit = {

    val pf = Platform("")

    val ichan1: IChan[(String, Int)] = channel.IChan(("I climb in", 2), ("a tree.    I", 2),
      ("fall out of", 2), ("the three.  That was fun", 4))

    val seg: Seg[(String, Int)] = Seg(("A", 4), ("Story.", 2))

    val result1 = "A Story.\nI climb in a tree.\nI fall out of the three.\nThat was fun.\n"

    pf.execIO_! {
      for {
        in <- use(seg ++: ichan1)
        out <- use(Console.stdoutLine)
        par <- in.map { _._1 }.parse(sentenseParser).fold("") { (p, s) => p + s + "\n" }
        _ <- ioLog(par)
        _ <- IO {
          assert(par == result1, "par did not match expectations")
        }
        _ <- ioLog("********")
      } yield ()
    }

    val pairParser = sentenseParser.contraMap[(String, Int)](_._1, (pair, str) => (str, pair._2))

    pf.execIO_! {
      for {
        in <- use(seg ++: ichan1)
        out <- use(Console.stdoutLine)
        par <- in.parse(pairParser).fold("") { (p, s) => p + s + "\n" }
        _ <- ioLog(par)
        _ <- IO {
          assert(par == result1, "par did not match expectations")
        }
        _ <- ioLog("********")
      } yield ()
    }

    pf.execIO_! {
      for {
        in <- use(ichan1)
        out <- use(Console.stdoutLine)
        s1 <- in.read(pairParser)
        _ <- ioLog(s1)
        _ <- IO {
          assert(s1 == "I climb in a tree.", "s1 did not match expectations")
        }
        tot <- in.map(_._2).fold(0) { (t, i) => t + i }
        _ <- ioLog(tot.toString())
        _ <- IO {
          assert(tot == 8, "tot did not match expectations")
        }
        _ <- ioLog("********")
      } yield ()
    }

  }

}