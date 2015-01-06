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
import parsers.utils.showHex

/**
 * Extract a contiguous array of fixed size from a
 * stream of byte buffers.
 */
final private class ByteArrayParser private (size: Int, builder: ByteArrayBuilder) extends Parser[ByteBuffer, Array[Byte]] {
  type Res = ParseResult[Array[Byte], ByteBuffer]

  def reset = ByteArrayParser(size)

  def name = "bytebuffer.byteArray"

  def result(signal: Signal) =
    Some(Left(StdErrors.countNotReached(name, size, Seg(parsers.utils.showHex(builder.result)), StdErrors.prematureSignal(name, NilSeg, signal))))

  def apply(seg: Seg[ByteBuffer]): Res = {
    @scala.annotation.tailrec
    def loop(seg: Seg[ByteBuffer], builder: ByteArrayBuilder): Res = {
      if (seg.isEmpty)
        return Partial(new ByteArrayParser(size, builder))

      val (bb, bbs) = (seg.head, seg.tail)

      if (bb.remaining == 0)
        loop(bbs, builder)
      else {
        val remaining = size - builder.length
        if (bb.remaining >= remaining) {
          val (head, tail) = splitBuffer(bb.position + remaining, bb, bbs)
          Done(builder.append(head).result, tail)
        } else {
          loop(bbs, builder.append(bb))
        }
      }
    }

    loop(seg, builder)
  }

}

private object ByteArrayParser extends Parsers[ByteBuffer] {

  def apply(size: Int): Parser[ByteBuffer, Array[Byte]] =
    if (size == 0) success(new Array(0))
    else new ByteArrayParser(size, ByteArrayBuilder())

}