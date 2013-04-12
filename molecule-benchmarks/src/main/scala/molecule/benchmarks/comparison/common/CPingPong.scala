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
package comparison
package common

object CPingPong {

  import Comparison.{ cores, warmups, runs }
  import mbench.benchmark._
  import mbench.MBench.benchmarkFolder

  // Number of cycles
  def config = mbench.benchmark.Config.static(100000)

  val xlabel = Label[Int]("threads")

  val throughput = Column.withConfig[Int, Int, Double]("throughput", "msg".perSeconds)(
    (threads, cycles, time) => threads * cycles / time
  )

  def benchmark = if (Comparison.quick.get) quick else default

  private val default = {
    val threads =
      if (cores <= 8) (1 to 3) ++ (4 to (cores) by 2) ++ Seq(2, 4).map(_ + cores)
      else (1 to 3) ++ (4 to (cores) by 4) ++ Seq(2, 4).map(_ + cores)

    Benchmark(Comparison.pingPong, threads, xlabel, warmups.get, runs.get)
      .add(throughput)
  }

  private lazy val quick =
    default.copy(is = (1 to 3) ++ (4 to (cores) by 4))

  import mbench.gnuplot._

  def settings = Seq(Plot.xtics(1))
  def labels = Seq(throughput.label)

  def plot(datfiles: Seq[DatFile]): Unit =
    Gnuplot.save(Gnuplot(datfiles, settings, labels))

  def compare(datfiles: Seq[DatFile]): Unit =
    Gnuplot.save(benchmarkFolder("overview"), Gnuplot(Comparison.pingPong, datfiles, settings, labels))

}