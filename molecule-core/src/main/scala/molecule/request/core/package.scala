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
package request

import channel.{ ROChan, RIChan, RChan, Chan }
import stream.{ IChan, OChan }
import platform.UThread

/**
 * Implicit definitions for using request-response protocols
 * in a reactive style.
 */
package object core {

  /**
   * Enrich response channels.
   */
  class RichResponseChannel[A](rchan: ResponseChannel[A]) {
    def reply_!(a: A): Unit = rchan.success_!(a)
  }

  /**
   * Enrich output channels supporting multiple protocols for easily sending request messages
   */
  implicit def enrichResponseChannel[A](rchan: ResponseChannel[A]): RichResponseChannel[A] =
    new RichResponseChannel(rchan)

  /**
   * Enrich output channels supporting request-response protocols for easily sending request messages
   */
  class RequestChannel[A](chan: OChan[A]) {

    /**
     * Request a response message.
     *
     * @param req the request.
     * @return the channel on which to emit the next request and a result channel carrying
     *  the response.
     */
    def request0[B: Message](uthread: UThread, req: ROChan[B] => A with Response[B]): RIChan[(OChan[A], B)] = {
      val (resI, resO) = RChan.mk[B]()
      val (ri, ro) = RChan.mk[(OChan[A], B)]()
      chan.write(uthread, Seg(req(resO)), None, ochan =>
        resI.onComplete(b => ro.success_!(ochan, b), signal => ro.failure_!(signal)))
      ri.dispatchTo(uthread)
    }

    def request[B: Message](uthread: UThread, req: ROChan[B] => A with Response[B]): RIChan[(OChan[A], B)] = {
      val (resI, resO) = RChan.mk[B]()
      val (ri, ro) = RChan.mk[(OChan[A], B)]()
      chan.write(uthread, Seg(req(resO)), None, ochan =>
        resI.onComplete(b => ro.success_!(ochan, b), signal => ro.failure_!(RequestSignal(ochan, signal))))
      ri.dispatchTo(uthread)
    }

    /**
     * This one is a disaster for the performance of molecule.examples.core.chameneosredux. See
     * the `main` in molecule.benchmarks.comparison.molecule.ChameneosRedux, which measures the
     * effectiveness of the L1 and L2 trampolines. In case of the core example, the L2 trampoline is almost
     * completely bypassed. Somehow the next task of the L2 trampoline executor is almost never
     * null, which indicates that there is too much parallelism (to be investigated).
     */
    //    def request0[B: Message](uthread: UThread, req: ROChan[B] => A with Response[B]): RIChan[(OChan[A], RIChan[B])] = {
    //      val (resI, resO) = RChan.mk[B]()
    //      val (ri, ro) = RChan.mk[(OChan[A], RIChan[B])]()
    //      chan.write(uthread, Seg(req(resO)), None, ochan => ro.success_!((ochan, resI)))
    //      ri.dispatchTo(uthread)
    //    }

    /**
     * Request a stream.
     *
     * @param req the request.
     * @return the channel on which to emit the next request and an input channel carrying
     *  the response stream.
     */
    def requestStream[B: Message](uthread: UThread, req: ResponseStreamChannel[B] => A with ResponseStream[B]): RIChan[(OChan[A], IChan[B])] = {
      val (resI, resO) = Chan.mk[B]()
      val (ri, ro) = RChan.mk[(OChan[A], IChan[B])]()
      chan.write(uthread, Seg(req(resO)), None, ochan => ro.success_!((ochan, resI)))
      ri.dispatchTo(uthread)
    }
  }

  /**
   * Enrich output channels supporting multiple protocols for easily sending request messages
   */
  implicit def enrichRequestChannel[A](ochan: OChan[A]): RequestChannel[A] = new RequestChannel(ochan)

  import channel.{ NativeProducer, NativeConsumer }

  /**
   * Enrich channels supporting request-response protocols for easily sending request messages
   * using a native thread.
   */
  class NativeRequestChannel[A](chan: NativeProducer[A]) {

    /**
     * Request an asynchronous response.
     *
     * @param req the request
     * @return the response.
     */
    def requestAsync[B: Message](req: ROChan[B] => A with Response[B]): RIChan[B] = {
      val (ri, ro) = RChan.mk[B]()
      chan.send(req(ro))
      ri
    }

    /**
     * Request a synchronous response.
     *
     * @param req the request
     * @return the response.
     */
    def request[B: Message](req: ROChan[B] => A with Response[B]): B =
      requestAsync[B](req).get_!

    /**
     * Request a response stream.
     *
     * @param req the request
     * @return the response.
     */
    def requestStream[B: Message](req: ResponseStreamChannel[B] => A with ResponseStream[B]): IChan[B] = {
      val (i, o) = Chan.mk[B]()
      chan.send(req(o))
      i
    }
  }

  /**
   * Enrich channels supporting multiple protocols for easily sending request messages
   * in a native manner
   */
  implicit def enrichNativeRequestChannel[A](ochan: NativeProducer[A]): NativeRequestChannel[A] = new NativeRequestChannel(ochan)

}