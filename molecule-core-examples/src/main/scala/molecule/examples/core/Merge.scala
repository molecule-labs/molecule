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
import molecule.stream._
import platform.Platform

object Merge {

  def unbalanced(n: Int): IChan[Int] = {
    def stream = IChan.source(1 to n)
    stream
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream) // 8
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream) // 16
  }

  def balanced(n: Int): IChan[Int] = {
    def stream = IChan.source(1 to n)
    def m = stream.merge(stream)
    def mm = m.merge(m)
    def mmm = mm.merge(mm)
    mmm.merge(mmm)
  }

  def run(f: Int => IChan[Int])(p: Platform, n: Int) {
    val count = p.launch(
      f(n).fold(0)((count, _) => count + 1)
    ).get_!

    assert(count == 16 * n, count + "!=" + (16 * n))
    println("ok")
  }

  def manyToOne(n: Int): IChan[Int] = {
    def stream = IChan.source(1 to n)
    val (i, mkO) = channel.ManyToOne.mk[Int](1)
    (1 to n).foreach { _ => bridge(stream, mkO()) }
    i
  }

  def runManyToOne(f: Int => IChan[Int])(p: Platform, n: Int) {
    p.launch(
      f(n).take(16 * n).connect(OChan.Void[Int])
    ).get_!

    println("ok")
  }

  def main(args: Array[String]) {
    val N = 50000
    val platform = Platform("merge")
    run(unbalanced)(platform, N)
    run(balanced)(platform, N)
    runManyToOne(manyToOne)(platform, N)
  }

}