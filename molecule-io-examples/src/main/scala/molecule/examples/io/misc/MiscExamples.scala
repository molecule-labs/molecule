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

package molecule.examples.io.misc

import molecule._
import molecule.io._
import molecule.stream._

/**
 * Smoke tests.
 */
object MiscExamples {

  import platform.Platform, channel.{ Chan, Console }

  def main(args: Array[String]): Unit = {

    val platform = Platform("misc-examples", debug = true)

    test0(platform)
    test0bis(platform)
    test1(platform)
    test2(platform)
    test3(platform)
    test3bis(platform)
    test4(platform)
    test5(platform)
    //    test6(platform) // long test

    platform.shutdown()
  }

  /**
   * Molecule that performs only streaming
   */
  def test0(platform: Platform) {

    object Passthrough extends ProcessType1x1[Char, Char, Signal] {
      def main(in: Input[Char], out: Output[Char]) =
        in flush out
    }

    val src = IChan.source("Hello World".toList)
    val dst = Console.logOut[Char]("log")

    println(platform.launch(Passthrough(src, dst)).get_!()) // EOS
  }

  def test0bis(platform: Platform) {

    val src = IChan.source("Hello World".toList)
    val log = Console.logOut[Char]("log")

    val process = src.filter(_ != 'o').connect(log)

    platform.launch(process).get_!()
  }

  /**
   * Illustrates stream manipulation functions
   */
  object Convert extends ProcessType1x1[Char, Char, Unit] {
    def main(in: Input[Char], out: Output[Char]) =
      in.map(c => c.toUpper).filter(c => c != 'O').connect(out) >> IO()
  }

  def test1(platform: Platform) {

    val src = IChan.source("Hello World".toList)
    val dst = Console.logOut[Char]("log")

    platform.launch(Convert(src, dst)).get_!()
  }

  /**
   * Shows the effect of using custom batch size
   */
  def test2(platform: Platform) {

    val src = IChan.source("Hello World".toList, 2 /** Batch size */ )
    val dst = Console.logOut[Char]("log")

    platform.launch(Convert(src, dst)).get_!()
  }

  /**
   * Molecule that combines workflow and streaming
   */
  object Encapsulate extends ProcessType1x1[Char, Char, Unit] {
    def main(in: Input[Char], out: Output[Char]) =
      for {
        _ <- out.write('*')
        _ <- out.flush(in)
        _ <- out.write('#')
      } yield ()
  }

  def test3(platform: Platform) {

    val src = IChan.source("Hello World".toList)
    val dst = Console.logOut[Char]("log")

    platform.launch(Encapsulate(src, dst)).get_!()
  }

  def test3bis(platform: Platform) {

    object Encapsulate2 extends ProcessType1x1[Char, Char, Unit] {
      def main(in: Input[Char], out: Output[Char]) =
        out.write('*') >>
          out.flush(in) >>
          out.write('#')
    }

    val src = IChan.source("Hello World".toList)
    val dst = Console.logOut[Char]("log")

    platform.launch(Encapsulate2(src, dst)).get_!()
  }

  def test4(platform: Platform) {

    val (i, o) = Chan.mk[Char]()

    val src = IChan.source("Hello World".toList)
    val dst = Console.logOut[Char]("log")

    platform.launch(Convert(src, o))
    platform.launch(Encapsulate(i, dst)).get_!()
  }

  /**
   * FSM style
   */
  def test5(platform: Platform) {

    object FSM extends ProcessType1x1[Char, Char, Unit] {

      def main(in: Input[Char], out: Output[Char]) = {

        def loop(n: Int): IO[Unit] =
          { in.read() >>\ out.write } >>
            //ioLog(n.toString) >>
            loop(n + 1)

        out.write('*') >>
          loop(1) orCatch {
            case EOS => out.write('#')
            case signal => raise(signal)
          }

      }
    }

    val src = IChan.continually("Hello World".toList).flatten.take(9999)
    val dst = Console.logOut[Char]("log")

    platform.launch(FSM(src, dst)).get_!()
  }

  def test6(platform: Platform) {

    object FSM extends ProcessType1x1[Char, Char, Unit] {

      def main(in: Input[Char], out: Output[Char]) = {

        // start state
        def header = out.write('*') >> upperCase

        def upperCase: IO[Unit] = { in.read() >>\ (c => out.write(Character.toUpperCase(c))) } >> lowerCase

        def lowerCase = { in.read() >>\ (c => out.write(Character.toLowerCase(c))) } >> underScore

        def underScore = in.read() >> out.write('_') >> upperCase

        header orCatch {
          case EOS => out.write('#')
        }
      }
    }

    val src = IChan.continually("Hello World".toList).flatten.take(999999)
    val dst = Console.logOut[Char]("log")

    platform.launch(FSM(src, dst)).get_!()
  }

}
