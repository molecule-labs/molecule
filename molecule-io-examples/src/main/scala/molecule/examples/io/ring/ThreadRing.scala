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

package molecule.examples.io.ring

import molecule._
import molecule.io._
import molecule.channel.Chan
import molecule.stream._

/**
 * Classical Thread-Ring example.
 *
 *  Halen, J., R. Karlsson, and M. Nilsson (1998). "Performance measurements of threads
 *   in Java and processes in Erlang."
 *   http://www.sics.se/~joe/ericsson/du98024.html.
 *
 * Implementations in various programming languages can be found
 * on Alioth Shootout Benchmark Games.
 *
 */
object ThreadRing {

  object Node extends ProcessType2x2[Int, Int, Int, Int, Unit] {

    def main(label: Input[Int], prev: Input[Int], next: Output[Int], result: Output[Int]) =
      /**
       * either we find 0 or the channel is closed by the previous component
       * because it found it
       */
      prev.span(_ > 0).map(_ - 1).forward(next) >>
        unless(prev.isEmpty) { result.write(label) }
  }

  object WNode extends ProcessType2x2[Int, Int, Int, Int, Unit] {

    def main(label: Input[Int], prev: Input[Int], next: Output[Int], result: Output[Int]) =
      prev.foreach { i =>
        if (i > 0) {
          next.write(i - 1)
        } else
          result.write(label) >> next.close()
      }
  }

  import molecule.process.Process

  type NodeType = (IChan[Int], IChan[Int], OChan[Int], OChan[Int]) => Process[Unit]

  import molecule.platform.Platform
  import molecule.channel.Console

  def run(platform: Platform, ringSize: Int, initVal: Int, node: NodeType) {
    val (first_i, last_o) = Chan.mk[Int]()
    val result = Console.logOut[Int]("result")

    // prepend first channel with the number to inject in thread 1
    val last_i = (1 until ringSize).foldLeft(initVal :: first_i) {
      case (prev, label) =>
        val (nextPrev, next) = Chan.mk[Int]()
        platform.launch(node(label, prev, next, result))
        nextPrev
    }

    // close the ring with last component
    platform.launch(node(ringSize, last_i, last_o, result))

    /**
     * This will block until all processes created by this application
     * have terminated.
     */
    platform.collect()
  }

  def main(args: Array[String]): Unit = {

    val platform = Platform("ring")
    //run(platform, 503, 100000, Node)
    //Thread.sleep(20000)
    run(platform, 503, 100000, WNode)
  }

}
