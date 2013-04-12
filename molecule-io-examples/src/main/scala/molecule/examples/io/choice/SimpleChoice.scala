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

package molecule.examples.io.choice

import molecule._
import molecule.io._

/**
 * Alternate reads on two streams of integers.
 *
 * This process prints only even numbers of one stream and odd numbers of
 * the other stream. Also, the stream with the highest throughput (larger batches)
 * is consumed faster than the stream with the lowest throughput.
 *
 * The example terminates when both streams have entirely been
 * consumed.
 *
 * For a less theoretical example of choice, see the stopwatch
 * controller.
 *
 */
object SimpleChoice extends ProcessType2x1[Int, Int, Int, Unit] { outer =>

  def main(i1: Input[Int], i2: Input[Int], o1: Output[Int]) = for {
    _ <- (i1 <+> i2) foreach {
      case Left(a) =>
        if (a % 2 == 0)
          o1.write(a)
        else
          IO()
      case Right(b) =>
        if (b % 2 != 0)
          o1.write(b)
        else
          IO()
    }
    _ <- o1.write(-1)
  } yield ()

  import molecule.platform.Platform
  import molecule.channel.Console

  import molecule.channel.IChan

  def main(args: Array[String]): Unit = {

    val src1 = IChan.source((1 to 100).toList, 5)

    val src2 = IChan.source((1 to 100).toList, 10)

    /**
     * Logger channel prints the output on stdout.
     */
    val log = Console.logOut[Int]("log") // .smap[Int, Int](1)((i, n) => (i+1, i+":"+n))

    val platform = Platform("simple-choice")
    platform.launch(SimpleChoice(src1, src2, log)).get_!()

  }
}
