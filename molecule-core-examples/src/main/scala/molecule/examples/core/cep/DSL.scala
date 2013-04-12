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

package molecule.examples.core.cep

/**
 * A small illustration of using Molecule to implement a very
 * basic DSL for CEP.
 */
object DSL {
  import molecule._
  import molecule.stream._
  import channel.Console

  trait Window[A] {
    def add(a: A): Window[A]
    def window: Seg[A]
  }

  def window[A](z: Window[A])(ichan: IChan[A]): IChan[Seg[A]] = {
    ichan.smap(z) { (w, a) =>
      val nw = w.add(a)
      (nw, nw.window)
    }
  }

  case class SizeWindow[A: Message](size: Int, window: Seg[A]) extends Window[A] {
    def add(a: A): SizeWindow[A] = {
      if (window.size < size) copy(window = window :+ a)
      else copy(window = window.drop(1) :+ a)
    }
  }

  case class TimeWindow[A: Message](time: Long, window: Seg[(Long, A)]) extends Window[(Long, A)] {
    def add(a: (Long, A)): TimeWindow[A] = {
      val last_t = a._1
      val nwindow = window.dropWhile { case (t, _) => last_t - t >= time }
      copy(window = nwindow :+ a)
    }
  }

  case class FTimeWindow[A: Message](time: Long, getTime: A => Long, window: Seg[A]) extends Window[A] {
    def add(a: A): FTimeWindow[A] = {
      val last_t = getTime(a)
      val nwindow = window.dropWhile { a => last_t - getTime(a) >= time }
      copy(window = nwindow :+ a)
    }
  }

  def sizeWindow[A: Message](size: Int)(ichan: IChan[A]): IChan[Seg[A]] =
    window(SizeWindow(size, Seg()))(ichan)

  def timeWindow[A: Message](time: Long)(ichan: IChan[(Long, A)]): IChan[Seg[(Long, A)]] =
    window(TimeWindow(time, Seg()))(ichan)

  def ftimeWindow[A: Message](time: Long, getTime: A => Long)(ichan: IChan[A]): IChan[Seg[A]] =
    window(FTimeWindow[A](time, getTime, Seg()))(ichan)

  // milliseconds
  def now() = System.nanoTime / 1000000

  def timestamp[A](ichan: IChan[A]): IChan[(Long, A)] =
    ichan.mapSeg { seg =>
      val time = System.nanoTime / 1000000
      seg.map(a => (time, a))
    }

  def join[A: Message, B: Message](ia: IChan[Seg[A]], ib: IChan[Seg[B]]): IChan[(Seg[A], Seg[B])] =
    ia.interleave(ib).smap((Seg.empty[A], Seg.empty[B])) {
      case ((as, bs), Left(nas)) =>
        val s = (nas, bs)
        (s, s)
      case ((as, bs), Right(nbs)) =>
        val s = (as, nbs)
        (s, s)
    }

  class AugmentedEsperIChanWithWindow[A: Message](ichan: IChan[A]) {

    def timestamp: IChan[(Long, A)] =
      DSL.timestamp(ichan)

    def sizeWindow(size: Int): IChan[Seg[A]] =
      DSL.sizeWindow(size)(ichan)

    def timeWindow(time: Long): IChan[Seg[A]] =
      DSL.timeWindow(time)(DSL.timestamp(ichan)).map(_.map(_._2))

    def ftimeWindow(time: Long, getTime: A => Long): IChan[Seg[A]] =
      DSL.ftimeWindow(time, getTime)(ichan)

    def timeSpan(time: Long): IChan[A] = {
      val n = now()
      ichan.span(_ => (now() - n) < time)
    }

  }

  implicit def augmentEsperIChanWithWindow[A: Message](ichan: IChan[A]): AugmentedEsperIChanWithWindow[A] =
    new AugmentedEsperIChanWithWindow(ichan)

  def avg[A: Numeric](seg: Seg[A]): Double = {
    val num = implicitly[Numeric[A]]
    import num._

    val (sum, count) = seg.foldLeft((0.0, 0)) {
      case ((sum, count), a) =>
        (sum + a.toDouble(), count + 1)
    }
    sum / count
  }

  def avg[B, A: Numeric](project: B => A)(seg: Seg[B]): Double = {
    val num = implicitly[Numeric[A]]
    import num._

    val (sum, count) = seg.foldLeft((0.0, 0)) {
      case ((sum, count), a) =>
        (sum + project(a).toDouble(), count + 1)
    }
    sum / count
  }

  import process.Process

  def logProcess[A: Message](name: String, i: IChan[A]): Process[Either[Signal, Signal]] = {
    i connect Console.logOut[A](name)
  }

  trait Pattern[A, Pattern] extends parsing.Parsers[A] {
    import parsing._

    def pattern: Parser[A, Pattern]

    private[this] lazy val _parser: Parser[A, Pattern] = pattern

    private[cep] lazy val filter: Parser[A, Pattern] =
      acceptSignal(EOS) | _parser | (acceptAny ~> filter)
  }

  class AugmentedEsperIChanWithPattern[A: Message](ichan: IChan[A]) {

    def filterPattern[P: Message](pattern: Pattern[A, P]): IChan[P] =
      ichan.parse(pattern.filter)

  }

  implicit def augmentEsperIChanWithPattern[A: Message](ichan: IChan[A]): AugmentedEsperIChanWithPattern[A] =
    new AugmentedEsperIChanWithPattern(ichan)

}