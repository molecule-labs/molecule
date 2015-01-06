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
package parsers.bytebuffer

import parsing._
import java.nio.ByteBuffer

/**
 * Retrieve a list of byte buffers for a specified amount of bytes from a
 * stream of byte buffers.
 */
final private class ByteBufferSegParser private[bytebuffer] (size: Int, acc: Seg[ByteBuffer], blen: Int) extends Parser[ByteBuffer, Seg[ByteBuffer]] {
  type Res = ParseResult[Seg[ByteBuffer], ByteBuffer]

  def reset = ByteBufferSegParser(size)

  def name = "bytebuffer.byteBufferList"

  def result(signal: Signal) =
    Some(Left(StdErrors.countNotReached(name, size, acc.map(_.remaining), StdErrors.prematureSignal(name, NilSeg, signal))))

  def apply(as: Seg[ByteBuffer]): Res = {

    @scala.annotation.tailrec
    def loop(seg: Seg[ByteBuffer], acc: Seg[ByteBuffer], blen: Int): Res = {
      if (seg.isEmpty)
        return Partial(new ByteBufferSegParser(size, acc, blen))

      val (bb, bbs) = (as.head, as.tail)

      if (bb.remaining == 0) {
        loop(bbs, acc, blen)
      } else {
        val remaining = size - blen
        if (bb.remaining >= remaining) {
          val (head, tail) = splitBuffer(bb.position + remaining, bb, bbs)
          Done(acc :+ head, tail)
        } else {
          loop(bbs, acc :+ bb, blen + bb.remaining)
        }
      }
    }

    loop(as, acc, blen)
  }

}

private object ByteBufferSegParser extends Parsers[ByteBuffer] {

  def apply(size: Int): Parser[ByteBuffer, Seg[ByteBuffer]] =
    if (size == 0) success(Seg.empty)
    else new ByteBufferSegParser(size, Seg.empty, 0)

}