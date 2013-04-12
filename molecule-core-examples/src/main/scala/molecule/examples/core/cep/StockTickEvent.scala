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

package molecule.examples.core.cep

object StockTickEvent {
  import molecule._
  import molecule.stream._

  // From Esper tutorial

  case class StockTick(symbol: String, price: Double)

  import platform.Platform
  import channel.Timer
  import java.util.concurrent.TimeUnit
  import DSL._

  // This is a sample EPL statement that computes the average price for the last 30 seconds 
  // of stock tick events:
  //  
  // select avg(price) from StockTickEvent.win:time(30 ms) 

  def example1(stockTickEvents: IChan[StockTick]): IChan[Double] =
    stockTickEvents.timeWindow(30).map(avg(_.price))

  import scala.collection.Map

  //A sample EPL that returns the average price per symbol for the last 100 stock ticks.
  //
  //select symbol, avg(price) as averagePrice
  //    from StockTickEvent.win:length(100)
  //group by symbol

  def example2(stockTickEvents: IChan[StockTick]): IChan[Map[String, Double]] =
    stockTickEvents.sizeWindow(100).map(seg => seg.groupBy(_.symbol).mapValues(avg(_.price)))

  // A sample pattern that alerts on each IBM stock tick with a price greater then 80 and 
  // within the next 10 seconds:
  // every StockTickEvent(symbol="A", price>80) where timer:within(500 ms)
  def example3(stockTickEvents: IChan[StockTick]): IChan[StockTick] =
    stockTickEvents.timeSpan(500).filter {
      case StockTick(symbol, price) =>
        (symbol == "IBM") && (price > 80)
    }

  def genStockTicks(n: Int, period_ms: Long): IChan[StockTick] = {
    val symbols = Array("ALU", "IBM", "ECS")
    def rSymbol = symbols(utils.random().nextInt(3))
    def rPrice = utils.random().nextInt(100) + 1

    val infgen = IChan.fill(n)(StockTick(rSymbol, rPrice))
    Timer.readPeriodically(infgen, period_ms, TimeUnit.MILLISECONDS)
  }

  def main(args: Array[String]) {
    val p = platform.Platform("stock-processor")

    //p.launch(genStockTicks(200, 100) connect OChan.logOut[StockTick]("test")).get_!()
    p.launch(logProcess("Ex1", example1(genStockTicks(20, 5)))).get_!()
    p.launch(logProcess("Ex2", example2(genStockTicks(1000, 1)))).get_!()
    p.launch(logProcess("Ex3", example3(genStockTicks(1000, 1)))).get_!()
  }
}