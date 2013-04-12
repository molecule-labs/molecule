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

object FraudEvent {
  import molecule._
  import molecule.stream._
  import DSL._

  // This example joins 2 event streams. The first event stream consists of fraud warning events 
  // for which we keep the last 60 milliseconds. The second stream is withdrawal events 
  // for which we consider the last 6 millisecond. The streams are joined on account number.
  //
  // select fraud.accountNumber as accntNum, fraud.warning as warn, withdraw.amount as amount,
  //      MAX(fraud.timestamp, withdraw.timestamp) as timestamp, 'withdrawlFraud' as desc
  // from FraudWarningEvent.win:time(60 ms) as fraud,
  //      WithdrawalEvent.win:time(6 ms) as withdraw
  // where fraud.accountNumber = withdraw.accountNumber

  case class FraudWarning(accountNumber: Int, warning: String, timestamp: Long)
  case class Withdrawal(accountNumber: Int, amount: Int, timestamp: Long)

  lazy val tbase = now()

  /**
   * We can use the time of the window as parameters.
   */
  def detectFrauds(warningTimeWindow: Long, withdrawTimeWindow: Long)(warnings: IChan[FraudWarning], withdrawals: IChan[Withdrawal]): IChan[(Int, String, Int, Long)] =
    join(warnings.ftimeWindow(warningTimeWindow, _.timestamp), withdrawals.ftimeWindow(withdrawTimeWindow, _.timestamp)).flatMapSeg {
      case (fraudWarnings, withdrawals) =>
        for {
          f <- fraudWarnings
          w <- withdrawals if (w.accountNumber == f.accountNumber)
        } yield (f.accountNumber, f.warning, w.amount, math.max(f.timestamp, w.timestamp) - tbase)
    }

  def gen[A: Message](n: Int, period_ms: Long, mk: Long => A): IChan[A] = {
    import channel.Timer
    import java.util.concurrent.TimeUnit

    val infgen = IChan.fill(n)(mk(now()))
    Timer.readPeriodically(infgen, period_ms, TimeUnit.MILLISECONDS)
  }

  val warnings = Array("Cheater", "Big cheater", "Very big cheater")
  def rWarning = warnings(utils.random().nextInt(3))
  def rAccountNumber = utils.random().nextInt(10)
  def rAmount = utils.random().nextInt(1000)

  def genWarnings(n: Int, period_ms: Long): IChan[FraudWarning] =
    gen(n, period_ms, time => FraudWarning(rAccountNumber, rWarning, time))

  def genWithdrawals(n: Int, period_ms: Long): IChan[Withdrawal] =
    gen(n, period_ms, time => Withdrawal(rAccountNumber, rAmount, time))

  import platform.Platform

  def main(args: Array[String]): Unit = {
    val p = Platform("fraud-processor")

    p.launch(logProcess("Ex1", detectFrauds(30, 6)(genWarnings(20, 15), genWithdrawals(120, 1)))).get_!()
  }

}