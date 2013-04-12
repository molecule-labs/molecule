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

object CPrimeSieve {

  case class Config(N: Int) {
    override def toString = "N=" + N
  }

  import mbench.benchmark._
  import mbench.MBench.benchmarkFolder

  def config = mbench.benchmark.Config.static(Config(150000))

  val xlabel = Label[Int]("threads")

  val speedup = Column.timeSpeedup

  import Comparison.{ cores, warmups, runs }

  def benchmark = if (Comparison.quick.get) quick else default

  private val default = {
    val threads =
      if (cores <= 8) Seq(1) ++ (2 to (cores) by 2) ++ Seq(2, 4).map(_ + cores)
      else
        Seq(1) ++ (2 to (cores / 2) by 2) ++ ((cores / 2 + 4) to cores by 4) ++ List(2, 4).map(_ + cores)

    Benchmark(Comparison.primeSieve, threads, xlabel, warmups.get, runs.get)
      .add(speedup)
  }

  private lazy val quick =
    default.copy(
      is = if (cores < 8) default.is
      else (2 to (cores / 2) by 4) ++ ((cores / 2 + 4) to cores by 4) ++ List(4).map(_ + cores)
    )

  import mbench.gnuplot._

  def plot(datfiles: Seq[DatFile]): Unit =
    Gnuplot.save(Gnuplot(datfiles, Plot.xtics(1), Plot.logy))

  def compare(datfiles: Seq[DatFile]): Unit =
    Gnuplot.save(benchmarkFolder("overview"), Gnuplot(Comparison.primeSieve, datfiles, Plot.xtics(1), Plot.logy))

}