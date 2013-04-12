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
package parsers

import parsing._
import java.nio.ByteBuffer

package object bytebuffer {

  /**
   * Parser that accepts any byte
   */
  final val anyByte: Parser[ByteBuffer, Byte] = new Parser[ByteBuffer, Byte] {

    def name = "bytebuffer.anyByte"

    @scala.annotation.tailrec
    final def apply(seg: Seg[ByteBuffer]): Res = {
      if (seg.isEmpty)
        return Partial(this)

      val (bb, bbs) = (seg.head, seg.tail)
      if (bb.remaining == 0)
        apply(bbs)
      else {
        if (bb.remaining > 1) {
          val pos = bb.position
          val rem = bb.duplicate()
          rem.position(pos + 1)
          Done(bb.get(pos), rem +: bbs)
        } else
          Done(bb.get(bb.position), bbs)
      }
    }

    def result(signal: Signal) =
      Some(Left(StdErrors.prematureSignal(name, NilSeg, signal)))

  }

  final val anyInt: Parser[ByteBuffer, Int] =
    byteArray(4) map { arr => arr(0) << 24 | arr(1) << 16 | arr(2) << 8 | arr(3) }

  /**
   * Parse a Byte array of size n
   */
  def byteArray(n: Int): Parser[ByteBuffer, Array[Byte]] =
    ByteArrayParser(n)

  /**
   * Parse a Byte buffer of size n
   */
  def byteBuffer(n: Int): Parser[ByteBuffer, ByteBuffer] =
    byteArray(n).map(ByteBuffer.wrap(_))

  /**
   * Parse a Byte buffer list of n bytes
   */
  def byteBufferList(n: Int): Parser[ByteBuffer, List[ByteBuffer]] =
    byteBufferSeg(n) ^^ { seg => seg.copyTo[ByteBuffer, List[ByteBuffer]](List.newBuilder).result }

  /**
   * Parse a Byte buffer segment of n bytes
   */
  def byteBufferSeg(n: Int): Parser[ByteBuffer, Seg[ByteBuffer]] =
    ByteBufferSegParser(n)

  /**
   * Parser that succeeds if it matches a single byte
   */
  implicit def byte(b: Byte): Parser[ByteBuffer, Byte] = new Parser[ByteBuffer, Byte] {

    def name = "bytebuffer.byte"

    @scala.annotation.tailrec
    final def apply(seg: Seg[ByteBuffer]): Res = {
      if (seg.isEmpty)
        return Partial(this)

      val (bb, bbs) = (seg.head, seg.tail)
      if (bb.remaining == 0)
        apply(bbs)
      else if (bb.get(bb.position) != b)
        StdErrors.mismatch(name, b, bb.get(bb.position))
      else {
        if (bb.remaining > 1) {
          val rem = bb.duplicate()
          rem.position(bb.position + 1)
          Done(b, rem +: bbs)
        } else
          Done(b, bbs)
      }
    }

    def result(signal: Signal) =
      Some(Left(StdErrors.prematureSignal(name, NilSeg, signal)))
  }

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will split the received ByteBuffer at the
   * position when the condition is true.
   * It fails if the first byte of the received ByteBuffer match the condition,
   * so the resulting ByteBuffer will never be empty.
   */
  def splitIf(condition: Byte => Boolean): Parser[ByteBuffer, ByteBuffer] = new Parser[ByteBuffer, ByteBuffer] {

    def name = "bytebuffer.splitIf"

    @scala.annotation.tailrec
    final def apply(seg: Seg[ByteBuffer]): Res = {
      if (seg.isEmpty)
        return Partial(this)

      val (bb, bbs) = (seg.head, seg.tail)

      if (bb.remaining == 0)
        apply(bbs)
      else {
        var i = bb.position
        val bi = bb.get(i)
        if (condition(bi))
          Fail(name, "Not expecting " + bi)
        else {
          i += 1
          val lim = bb.limit
          while (i < lim && !condition(bb.get(i))) {
            i += 1
          }
          val (bbb, bbbs) = splitBuffer(i, bb, bbs)
          Done(bbb, bbbs)
        }
      }
    }

    def result(signal: Signal) = None
  }

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will split the received ByteBuffer at the
   * position where byte b occurs.
   * It fails if the first byte of the received ByteBuffer match the condition,
   * so the resulting ByteBuffer will never be empty.
   */
  def splitAt(b: Byte): Parser[ByteBuffer, ByteBuffer] = splitIf(_ == b)

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will split the received ByteBuffer at the
   * position where byte b or any of bytes bs occur.
   * It fails if the first byte of the received ByteBuffer match the condition,
   * so the resulting ByteBuffer will never be empty.
   */
  def splitAt(b: Byte, bs: Byte*): Parser[ByteBuffer, ByteBuffer] =
    splitIf(x =>
      bs.foldLeft(x == b)((r, bi) => r || x == bi))

  private[this] final val parsersByteBuffer = new Parsers[ByteBuffer] {}
  import parsersByteBuffer._

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer until byte b or any of bytes bs is found.
   */
  final def bufferUntil(b: Byte, bs: Byte*): Parser[ByteBuffer, ByteBuffer] = (splitAt(b, bs: _*)*) ^^ mkBuffer

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer until the received byte falls in one of the the specified ranges.
   */
  final def bufferUntil(range: (Byte, Byte), ranges: (Byte, Byte)*): Parser[ByteBuffer, ByteBuffer] =
    (splitIf(x =>
      ranges.foldLeft(inRange(x, range))((cond, r) => cond || inRange(x, r)))*) ^^ mkBuffer

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer accepting all specified bytes.
   */
  final def buffer(byte: Byte, bs: Byte*): Parser[ByteBuffer, ByteBuffer] =
    (splitIf(x =>
      bs.foldLeft(x != byte)((cond, b) => cond && x != b))*) ^^ mkBuffer

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer accepting all bytes that fall in the specified ranges.
   */
  final def buffer(range: (Byte, Byte), ranges: (Byte, Byte)*): Parser[ByteBuffer, ByteBuffer] =
    (splitIf(x =>
      ranges.foldLeft(!inRange(x, range))((cond, r) => cond && !inRange(x, r)))*) ^^ mkBuffer

  /**
   * Copies the remaining bytes of each ByteBuffer in a Seg to a single new ByteBuffer
   */
  final def mkBuffer(seg: Seg[ByteBuffer]): ByteBuffer = {
    if (seg.length == 0) return ByteBuffer.allocate(0)
    if (seg.length == 1) return seg.last

    val bbs = seg.toVector
    val len = bbs.map(_.remaining()).sum
    val bb = ByteBuffer.allocate(len)
    bbs.foreach(bb.put)
    bb.flip()
    bb
  }

  /*
   * Parser that accepts everything until byte b.
   * It fails if the first byte it receives is equal to byte b
   * def not(b:Byte):Parser[ByteBuffer, ByteBuffer] = new Parser[ByteBuffer, ByteBuffer] {
   *
   * def name = "bytebuffer.splitIf"
   *
   * @scala.annotation.tailrec
   * final def apply(seg:Seg[ByteBuffer]):Res = {
   * if (seg.isEmpty)
   * return Partial(this)
   *
   * val (bb, bbs) = (seg.head, seg.tail)
   *
   * if (bb.remaining == 0)
   * apply(bbs)
   * else {
   * var i = bb.position
   * if (bb.get(i) == b)
   * StdErrors.mismatch(name, "!" + b, bb.get(i))
   * else {
   * i += 1
   * val lim = bb.limit
   * while (i < lim && bb.get(i) != b) {
   * i += 1
   * }
   * val (bbb, bbbs) = splitAt(i, bb, bbs)
   * Done(bbb, bbbs)
   * }
   * }
   * }
   *
   * def result(signal:Signal) = None
   * }
   */

  @inline
  final private[bytebuffer] def inRange(x: Byte, range: (Byte, Byte)): Boolean =
    (range._1 <= x) && (x <= range._2)

  final private[bytebuffer] def splitBuffer(pos: Int, bb: ByteBuffer, bbs: Seg[ByteBuffer]): (ByteBuffer, Seg[ByteBuffer]) = {
    if (pos > bb.limit()) throw new IndexOutOfBoundsException

    if (bb.limit == pos) {
      (bb, bbs)
    } else {
      val part2 = bb.duplicate
      part2.position(pos)
      val part1 = bb.duplicate
      part1.limit(pos)
      (part1, part2 +: bbs)
    }
  }

}