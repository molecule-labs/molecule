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

import molecule.{ Message, Signal }
import molecule.seg.{ Seg }
import molecule.channel.{ ROChan, RIChan, RChan, Chan, IChan }
import molecule.io._

package object io {

  /**
   * Enrich a return channel with additionnal methods.
   */
  class ResponseOutput[A](ro: ROChan[A]) {
    def reply(a: A): IO[Unit] = { ro.success_!(a); IO() }
  }

  /** Enrich a return channel. */
  final implicit def liftResponseOutput[A](rc: ROChan[A]): ResponseOutput[A] = new ResponseOutput(rc)

  /**
   * Augment channels supporting multiple protocols for easily sending request messages
   */
  class RequestOutput[A](output: Output[A]) {

    /**
     * Request asynchronous response
     */
    def requestAsync[B: Message](req: ResponseChannel[B] => A with Response[B]): IO[RIChan[B]] = {
      val (ri, ro) = RChan.mk[B]()
      output.write(req(ro)) >> IO(ri)
    }

    /**
     * Request synchronous response
     */
    def request[B: Message](req: ResponseChannel[B] => A with Response[B]): IO[B] =
      requestAsync[B](req).get()

    /**
     * Request asynchronous stream
     */
    def requestStream[B: Message](req: ResponseStreamChannel[B] => A with ResponseStream[B]): IO[IChan[B]] = {
      val (i, o) = Chan.mk[B]
      output.write(req(o)) >> IO(i)
    }
  }

  /**
   * Augment channels supporting multiple protocols for easily sending request messages
   */
  implicit def outputToRequestOutput[A](output: Output[A]): RequestOutput[A] = new RequestOutput(output)

  import molecule.channel.NativeProducer

  /**
   * Class for 'enriching' a the output side of a producer channel.
   */
  class ProducerRequestOutput[A](pc: NativeProducer[A]) {

    /**
     * Request asynchronous response
     */
    def requestAsync[B: Message](req: ResponseChannel[B] => A with Response[B]): IO[RIChan[B]] = {
      val (ri, ro) = RChan.mk[B]()
      pc.write(req(ro)) >> IO(ri)
    }

    /**
     * Request synchronous response
     */
    def request[B: Message](req: ResponseChannel[B] => A with Response[B]): IO[B] =
      requestAsync[B](req).get()

    /**
     * Request asynchronous stream
     */
    def requestStream[B: Message](req: ResponseStreamChannel[B] => A with ResponseStream[B]): IO[IChan[B]] = {
      val (i, o) = Chan.mk[B]
      pc.write(req(o)) >> IO(i)
    }
  }

  /** 'enrich' a producer channel. */
  final implicit def liftProducerRequestOutput[A](pc: NativeProducer[A]): ProducerRequestOutput[A] = new ProducerRequestOutput(pc)

  //  def manageResponse[A](rsp:Response[A])(action:IO[A]):IO[Unit] = try {
  //      (action >>\ rsp.rchan.reply).orCatch {case signal => rsp.rchan.error(signal)} 
  //   } catch {
  //	  case t => rsp.rchan.error(molecule.JThrowable(t))
  //	}

  def manageResponse[Req <: Response[A]: Message, A](req: Req)(action: IO[A]): IO[Unit] = try {
    (action >>\ req.rchan.reply).orCatch { case signal => implicitly[Message[Req]].poison(req, signal); IO() }
  } catch {
    case t => implicitly[Message[Req]].poison(req, Signal(t)); IO()
  }

}