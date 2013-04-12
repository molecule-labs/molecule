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
import molecule.platform._
import molecule.channel.{ Chan, ROChan, RChan }
import stream._

object ThreadRing {

  def run(platform: Platform, size: Int, N: Int) {

    def component(label: Int, prev: IChan[Int], next: OChan[Int], result: ROChan[Int]) =
      prev.span(_ > 0, _ >>\ { (zero, _) =>
        result.success_!(label)
        IChan.empty[Int](EOS) // poison remaining of the input
      }).map(_ - 1) connect next

    // create ring and hook them up
    val (first_i, last_o) = Chan.mk[Int]()

    val (ri, ro) = RChan.mk[Int]()

    // prepend first channel with the number to inject in thread 1
    val last_i = (1 until size).foldLeft(N :: first_i) {
      case (prev, label) =>
        val (newPrev, next) = Chan.mk[Int]()
        platform.launch(component(label, prev, next, ro))
        newPrev
    }

    // close the ring with last component
    platform.launch(component(size, last_i, last_o, ro))

    println(ri.get_!)
  }

  def main(args: Array[String]): Unit = {
    val platform = Platform("thread-ring")
    run(platform, 503, 500000)
  }

}

