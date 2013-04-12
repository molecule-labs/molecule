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
import platform._
import channel.Console
import stream._

object PrimeSieve {

  def sieve(stream: IChan[Int]): IChan[Int] =
    stream.flatMap { (p, next) => p :: sieve(next.filter(_ % p != 0)) }

  val allPrimes = sieve(IChan.from(2))

  def primes(max: Int): IChan[Int] = sieve(IChan.source(2 to max))

  def run(platform: Platform, max: Int) {

    val log = Console.logOut[Int]("log")

    val process = primes(max) connect log

    platform.launch(process).get_!
  }

  def main(args: Array[String]): Unit = {
    val platform = Platform("prime-sieve")
    run(platform, 150000)
    platform.shutdown()
  }

}