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

package molecule.examples.io.primesieve

import molecule._
import molecule.io._

/**
 * Create a filter component with 2 inputs and 1 output
 * - ints:Input[Int] = channel that receives the stream of integers
 * - log:Output[Int] = that logs the primes
 *
 * Kahn, Gilles and David Macqueen (1976). "Coroutines and Networks of Parallel Processes".
 * Research Report, page 20.
 *
 * @author Sebastien Bocq
 */
object PrimeSieve extends ProcessType1x1[Int, Int, Unit] {

  def main(ints: Input[Int], log: Output[Int]) =
    for {
      // get the initial prime or exit if none is available
      prime <- ints.read() orCatch {
        case EOS => shutdown()
      }
      // send the prime to the log channel
      _ <- log.write(prime)
      // launch next component passing the log channel as output
      _ <- handover(PrimeSieve(ints.filter(_ % prime != 0), log))
    } yield ()

  import molecule.platform.Platform
  import molecule.channel.Console

  def run(platform: Platform, N: Int) {

    val ints = channel.IChan(2 to N)

    /**
     * Logger channel prints the output on stdout.
     */
    val log = Console.logOut[Int]("log") //.smap[Int, Int](0)((i, n) => (i+1, i+":"+n))

    platform.launch(PrimeSieve(ints, log)).get_!()

  }

  def main(args: Array[String]): Unit = {
    val platform = Platform("prime-sieve")
    run(platform, 400000)
  }
}
