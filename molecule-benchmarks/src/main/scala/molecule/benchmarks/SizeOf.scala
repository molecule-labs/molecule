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

package molecule
package benchmarks

import molecule.io._
import molecule.platform.Platform
import channel.{ NativeProducer, RChan, Chan }

object SizeOf {

  object Node extends ProcessType1x1[Unit, Unit, Unit] {
    def main(i: Input[Unit], o: Output[Unit]) =
      i.read() >> o.write()
  }

  // from http://www.javaworld.com/javaworld/javatips/jw-javatip130.html
  def main(args: Array[String]) {
    // Warm up all classes/methods we will use
    runGC()
    usedMemory()
    // Array to keep strong references to allocated objects
    val count = 200000
    val objects = new Array[Object](count)

    val platform = Platform("SizeOf")

    def test(n: Int) = {
      val (first_i, first_o) = NativeProducer.mkOneToOne[Unit](1)
      val (next_i, next_o) = Chan.mk[Unit]()
      platform.launch(Node(first_i, next_o))

      // prepend first channel with the number to inject in thread 1
      val last = (2 until n).foldLeft(next_i) {
        case (prev, _) =>
          val (nextPrev, next) = Chan.mk[Unit]()
          platform.launch(Node(prev, next))
          nextPrev
      }

      val (done, ro) = RChan.mk[Unit]()
      // close the ring with last component
      platform.launch(Node(last, channel.OChan.Void[Unit]), ro)
      (first_o, done)
    }

    // Warmup
    val (stop1, done1) = test(20)
    stop1.send(())
    done1.get_!()
    // Discard the warm up object
    runGC();
    val heap1 = usedMemory(); // Take a before heap snapshot

    val (stop2, done2) = test(count)
    runGC();
    val heap2 = usedMemory(); // Take an after heap snapshot:
    stop2.send(())
    done2.get()

    val size = math.round((heap2 - heap1).toFloat / count)

    println("'before' heap: " + heap1 +
      ", 'after' heap: " + heap2);
    println("heap delta: " + (heap2 - heap1) + "} size = " + size + " bytes");

    platform.shutdown()
  }

  val s_runtime = Runtime.getRuntime();

  def runGC() {

    def _runGC() {
      var usedMem1 = usedMemory()
      var usedMem2 = java.lang.Long.MAX_VALUE
      var i = 0
      while ((usedMem1 < usedMem2) && (i < 500)) {
        i += 1
        s_runtime.runFinalization();
        s_runtime.gc();
        Thread.`yield`;

        usedMem2 = usedMem1;
        usedMem1 = usedMemory();
      }
    }

    // It helps to call Runtime.gc()
    // using several method calls:
    for (r <- 0 until 4) _runGC()

  }

  def usedMemory(): Long =
    s_runtime.totalMemory() - s_runtime.freeMemory()

}