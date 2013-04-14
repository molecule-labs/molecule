package molecule
package channel

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
trait Predefs {

  /**
   * Class that enriches a value with additional methods.
   */
  class RichValToIChan[A](a: A) {

    /**
     * Wrap this value in a result channel that returns this value.
     */
    def asI(implicit m: Message[A]): channel.RIChan[A] = channel.RIChan.success(a)
  }

  /**
   * Enrich a value with additional methods.
   */
  implicit def enrichValToIChan[A](a: A): RichValToIChan[A] = new RichValToIChan(a)

  /**
   * Class that enriches a traversable with additional methods.
   */
  class RichTraversableToIChan[A](as: Traversable[A]) {

    /**
     * Converts a traversable into a system-level channel that emits the elements
     * it contains.
     *
     * @return a system-level input channel
     */
    def source(implicit m: Message[A]): channel.IChan[A] = channel.IChan.source(as)
  }

  /**
   * Enrich a traversable with additional methods.
   */
  implicit def enrichTraversableToIChan[A](as: Traversable[A]): RichTraversableToIChan[A] =
    new RichTraversableToIChan(as)

  import java.util.{ concurrent => juc }

  /**
   * Class that enriches a `juc.Executor` with additional methods.
   */
  class RichExecutor(executor: juc.Executor) {

    /**
     * Execute a thunk of code captured by a by-name argument.
     *
     * @param thunk the thunk of code to execute.
     * @return unit
     */
    def dispatch(thunk: => Unit): Unit =
      executor.execute(new Runnable { def run() = thunk })
  }

  /**
   * Enrich a `juc.Executor` with additional methods.
   */
  implicit def enrichExecutor[A](executor: juc.Executor): RichExecutor =
    new RichExecutor(executor)

  /**
   * Bridge a system-level input interface and a system-level output interface.
   * All the segments coming on the input are forwarded to the output in a
   * tight immutable loop in the threads of the sender and the receiver
   * without further level of indirection. If the input terminates first, the
   * output is closed with the termination signal of the input. If the output
   * terminates first, the input is poisoned with the termination signal
   * of the output.
   *
   * @param ichan the input.
   * @param ochan the output.
   * @return Unit
   */
  final def bridge[A](ichan: IChan[A], ochan: OChan[A]): Unit =
    impl.Bridge(ichan, ochan)

  /**
   * Pass the result from a result channel to a return channel.
   *
   * @param richan the input.
   * @param rochan the output.
   * @return Unit
   */
  final def bridge[A](richan: RIChan[A], rochan: ROChan[A]): Unit =
    richan.onComplete(rochan.success_!, rochan.failure_!)

}
