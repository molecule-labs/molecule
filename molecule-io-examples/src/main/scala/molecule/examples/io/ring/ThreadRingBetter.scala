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
import molecule.channel.{ Chan, ROChan, RChan }
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
 * Here we block on a result channel instead of using `collect`.
 *
 */
object ThreadRingBetter {

  /**
   * Notification id.
   */
  class Id(val label: Int, val rchan: ROChan[Int]) {
    def found(): IO[Unit] = rchan.success(label)
  }

  implicit val idIsMessage: Message[Id] =
    Message.impure((id, signal) => id.rchan.failure(signal))

  object Node extends ProcessType2x1[Id, Int, Int, Unit] {

    def main(id: Input[Id], prev: Input[Int], next: Output[Int]) =
      /**
       * either we find 0 or the channel is closed by the previous component
       * because it found it
       */
      prev.span(_ > 0).map(_ - 1).forward(next) >>
        unless(prev.isEmpty) { id.read() >>\ { _.found() } }
  }

  object WNode extends ProcessType2x1[Id, Int, Int, Unit] {

    def main(id: Input[Id], prev: Input[Int], next: Output[Int]) =
      prev.foreach { i =>
        if (i > 0) {
          next.write(i - 1)
        } else
          { id.read() >>\ { _.found() } } >> next.close()
      }
  }

  import molecule.process.Process
  type NodeType = (IChan[Id], IChan[Int], OChan[Int]) => Process[Unit]

  import molecule.platform.Platform

  def run(platform: Platform, ringSize: Int, initVal: Int, node: NodeType) {
    val (first_i, last_o) = Chan.mk[Int]()
    val (ri, ro) = RChan.mk[Int]()

    // prepend first channel with the number to inject in thread 1
    val last_i = (1 until ringSize).foldLeft(initVal :: first_i) {
      case (prev, label) =>
        val (nextPrev, next) = Chan.mk[Int]()
        platform.launch(node(new Id(label, ro).asI, prev, next))
        nextPrev
    }

    // close the ring with last component
    platform.launch(node(new Id(ringSize, ro).asI, last_i, last_o))

    /**
     * This will block until a result is available
     */
    println(ri.get_!())
  }

  def main(args: Array[String]): Unit = {

    val platform = Platform("ring")
    run(platform, 503, 100000, Node)
    run(platform, 503, 100000, WNode)
  }

}
