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
package seg

/**
 * Collection of messages carried on channels.
 *
 */
abstract class Seg[+A] {
  // The less methods, the better

  /**
   * Builds a new collection by applying a function to all messages of this segment.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned collection.
   *  @return       a new segment resulting from applying the given function
   *                `f` to each message of this segment and collecting the results.
   *
   *  @usecase def map[B](f: A => B): Seg[B]
   *
   *  @return       a new segment resulting from applying the given function
   *                `f` to each message of this segment and collecting the results.
   */
  def map[B](f: A => B): Seg[B]

  /**
   * Builds a new collection by applying a function to all messages of this segment
   *  and concatenating the results.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned collection.
   *  @return       a new segment resulting from applying the given collection-valued function
   *                `f` to each message of this segment and concatenating the results.
   *
   *  @usecase def flatMap[B](f: A => Seg[B]): Seg[B]
   *
   *  @return       a new segment resulting from applying the given collection-valued function
   *                `f` to each message of this segment and concatenating the results.
   */
  def flatMap[B](f: A => Seg[B]): Seg[B]

  /**
   * Builds a new collection by applying a function to all messages of this segment
   *  and concatenating the results.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned collection.
   *  @return       a new segment resulting from applying the given collection-valued function
   *                `f` to each message of this segment and concatenating the results.
   *
   *  @usecase def flatMapTraversable[B](f: A => Traversable[B]): Seg[B]
   *
   *  @return       a new segment resulting from applying the given collection-valued function
   *                `f` to each message of this segment and concatenating the results.
   */
  def flatMapTraversable[B](f: A => Traversable[B]): Seg[B]

  /**
   * Selects all messages of this segment which satisfy a predicate.
   *
   *  @param p     the predicate used to test messages.
   *  @param m     a message typeclass used to poison messages filtered out (i.e. messages that do not satisfy `p`).
   *  @return      a new segment consisting of all messages of this segment that satisfy the given
   *               predicate `p`. Their order may not be preserved.
   */
  def filter(p: A => Boolean)(implicit m: Message[A]): Seg[A]

  /**
   * Builds a new collection by applying a partial function to all messages of this segment
   *  on which the function is defined.
   *
   *  @param pf     the partial function which filters and maps the segment.
   *  @param m      a message typeclass used to poison messages not collected.
   *  @tparam B     the message type of the returned collection.
   *  @return       a new segment resulting from applying the partial function
   *                `pf` to each message on which it is defined and collecting the results.
   *                The order of the messages is preserved.
   *
   *  @usecase def collect[B](pf: PartialFunction[A, B]): Seg[B]
   *
   *  @return       a new segment resulting from applying the given partial function
   *                `pf` to each message on which it is defined and collecting the results.
   *                The order of the messages is preserved.
   */
  def collect[B](pf: PartialFunction[A, B])(implicit m: Message[A]): Seg[B]

  /**
   * Drops `n` elements from the head of this segment.
   *
   *  @param n  The number of elements to drop.
   *  @param m  A message typeclass used to poison messages dropped.
   *  @return   a segment whose `n` first elements have been dropped.
   */
  def drop(n: Int)(implicit m: Message[A]): Seg[A]

  /**
   * Drops longest prefix of messages that satisfy a predicate.
   *
   *  @param   p  The predicate used to test messages.
   *  @param m    A message typeclass used to poison messages dropped.
   *  @return  the longest suffix of this segment whose first message
   *           does not satisfy the predicate `p`.
   */
  def dropWhile(p: A => Boolean)(implicit m: Message[A]): Seg[A]

  /**
   * Drops longest suffix of messages that satisfy a predicate.
   *
   *  @param   p  The predicate used to test messages.
   *  @param m    A message typeclass used to poison messages dropped.
   *  @return  the longest prefix of this segment whose first message
   *           does not satisfy the predicate `p`.
   */
  def dropRightWhile(p: A => Boolean)(implicit m: Message[A]): Seg[A]

  /**
   * Applies a function `f` to all messages of this segments.
   *
   *  @param  f   the function that is applied for its side-effect to every message.
   *              The result of function `f` is discarded.
   *
   *  @tparam  U  the type parameter describing the result of function `f`.
   *              This result will always be ignored. Typically `U` is `Unit`,
   *              but this is not necessary.
   *
   *  @usecase def foreach(f: A => Unit): Unit
   */
  def foreach[U](f: A => U): Unit

  /**
   * Applies a binary operator to a start value and all messages of this segment,
   *  going left to right.
   *
   *  @param   z    the start value.
   *  @param   op   the binary operator.
   *  @tparam  B    the result type of the binary operator.
   *  @return  the result of inserting `op` between consecutive messages of this segment,
   *           going left to right with the start value `z` on the left:
   *           {{{
   *             op(...op(z, x,,1,,), x,,2,,, ..., x,,n,,)
   *           }}}
   *           where `x,,1,,, ..., x,,n,,` are the messages of this segment.
   */
  def foldLeft[B](z: B)(f: (B, A) => B): B

  /**
   * Returns a pair containing:
   *  - the segment formed from this segment and another segment by combining corresponding
   *  elements in pairs.
   *  - An optional segment containing the remaining elements if one of the two segments
   *    is longer than the other.
   *
   *  @tparam  B     the type of the second half of the returned pairs
   *  @param         the other segment
   *  @return        a new segment containing pairs consisting of
   *                 corresponding elements of this segment and `seg`
   *                 and an optional segment containing the remaining elements
   *                 if one of the two segments is longer than the other
   */
  def zip[B](seg: Seg[B]): (Seg[(A, B)], Option[Either[Seg[A], Seg[B]]])

  /**
   * Returns a tuple containing the head of this segment as a value segment and the tail
   * of the segment.
   *
   * @return a tuple containing the head of this segment as a value segment and the tail
   * of the segment.
   */
  def pop: (VSeg[A], Seg[A])

  /**
   * Catamorphism.
   *
   * @param empty the value returned if the segment is empty.
   * @param more the function applied to the head and the tail of the segment if it is not empty.
   *
   * @tparam B the return type of the arguments.
   *
   * @return the value returned by 'empty' or 'more' depending on if the segment is empty or not.
   */
  def step[B](empty: => B, more: (A, Seg[A]) => B): B

  /**
   * Selects the first message of this segment.
   *
   *  @return  the first message of this segment.
   *  @throws `NoSuchmessageException` if the segment is empty.
   */
  def head: A

  /**
   * Selects all messages except the first.
   *
   *  @return  a segment consisting of all messages of this segment
   *           except the first one.
   *  @throws `UnsupportedOperationException` if the segment is empty.
   */
  def last: A

  /**
   * Selects all messages except the first.
   *
   *  @return  a segment consisting of all messages of this segment
   *           except the first one.
   *  @throws `UnsupportedOperationException` if the segment is empty.
   */
  def tail: Seg[A]

  /**
   * Selects all messages except the last.
   *
   *  @return  a segment consisting of all messages of this segment
   *           except the last one.
   *  @throws `UnsupportedOperationException` if the segment is empty.
   */
  def init: Seg[A]

  /**
   * Converts this segment of traversable collections into
   *  a segment in which all message collections are concatenated.
   *
   *  @tparam B the type of the messages of each traversable collection.
   *  @param asTraversable an implicit conversion which asserts that the message
   *          type of this segment is a `Traversable`.
   *  @return a new segment resulting from concatenating all message.
   *  @usecase def flatten[B]: Seg[B]
   */
  def flatten[B](implicit asTraversable: A => /*<:<!!!*/ Traversable[B]): Seg[B]

  /**
   * Return a segments that concatenates all the segments it contains.
   *
   *  @tparam B the type of the messages of each traversable collection.
   *  @param asSeg an implicit conversion which asserts that the message
   *          type of this segment is a `Seg`.
   *  @return a new segment resulting from concatenating all segments.
   *  @usecase def concat[B]: Seg[B]
   */
  def concat[B](implicit asSeg: A => /*<:<!!!*/ Seg[B]): Seg[B]

  /**
   * Tests whether a predicate holds for some of the messages of this segment.
   *
   *  @param   p     the predicate used to test messages.
   *  @return        `true` if the given predicate `p` holds for some of the
   *                 messages of this segment, otherwise `false`.
   */
  def exists(p: A => Boolean): Boolean

  /**
   * Finds index of first message satisfying some predicate.
   *
   *  @param   p     the predicate used to test messages.
   *  @return  the index of the first message of this segment that satisfies the predicate `p`,
   *           or `-1`, if none exists.
   */
  //def indexWhere(p: A => Boolean):Int

  /**
   * Splits this segment into a prefix/suffix pair according to a predicate.
   *
   *  Note: `c span p`  is equivalent to (but possibly more efficient than)
   *  `(c takeWhile p, c dropWhile p)`, provided the evaluation of the
   *  predicate `p` does not cause any side-effects.
   *
   *  @param p the test predicate
   *  @return  a pair consisting of the longest prefix of this segment whose
   *           messages all satisfy `p`, and the rest of this segment.
   */
  def span(p: A => Boolean): (Seg[A], Seg[A])

  /**
   * Split a collection at the message that satisfies the predicate.
   *
   *  @param p the test predicate
   *  @return  a pair consisting of the longest prefix of this segment whose
   *           messages do not satisfy `p`, and the rest of this segment.
   */
  def break(p: A => Boolean) = span(!p(_))

  /**
   * Produces a collection containing cummulative results of applying the operator going left to right.
   *
   * @tparam B      the type of the messages in the resulting collection
   * @param z       the initial value
   * @param op      the binary operator applied to the intermediate result and the message
   * @return        a segment with the intermediate results, incuding the initial value.
   */
  def scanLeft[B](z: B)(op: (B, A) => B): Seg[B]

  /**
   * Produces a collection containing cummulative results of applying the operator going left to right.
   *
   * @tparam B      the type of the messages in the resulting collection
   * @param z       the initial state
   * @param op      the binary operator applied to the intermediate state and the message
   * @return        a segment with the intermediate results.
   */
  def smap[S, B](z: S)(f: (S, A) => (S, B)): (S, Seg[B])

  def groupBy[K](f: A => K): collection.immutable.Map[K, Seg[A]] = {
    import scala.collection.immutable.Map
    foldLeft(Map.empty[K, Seg[A]])({ (m, a) =>
      val k = f(a)
      val s = m.getOrElse(k, Seg())
      m + (k -> (s :+ a))
    })
  }

  /**
   * Splits this segment into two at a given position.
   *  Note: `c splitAt n` is equivalent to (but possibly more efficient than)
   *         `(c take n, c drop n)`.
   *
   *  @param n the position at which to split.
   *  @return  a pair of segments consisting of the first `n`
   *           messages of this segment, and the other messages.
   */
  def splitAt(n: Int): (Seg[A], Seg[A])

  /**
   * Prepend an message to this segment
   *
   *  @param b the message
   */
  def +:[B >: A](b: B): Seg[B]

  /**
   * Append an message to this segment
   *
   *  @param b the message
   */
  def :+[B >: A](b: B): Seg[B]

  /**
   * Append a segment to this segment
   *
   * Complexity is O(lmin) effective, where lmin is the length of
   * the smallest segment.
   *
   *  @param seg the segment
   */
  def ++[B >: A](seg: Seg[B]): Seg[B]

  /**
   * The length of the segment.
   *
   *  Note: `xs.length` and `xs.size` yield the same result.
   *
   *  @return     the number of messages in this segment.
   */
  def length: Int

  /**
   * The size of the segment.
   *
   *  Note: `xs.length` and `xs.size` yield the same result.
   *
   *  @return     the number of messages in this segment.
   */
  @inline
  final def size: Int = length

  /**
   * Tests whether this segment is empty.
   *
   *  @return    `true` if the segment contain no messages, `false` otherwise.
   */
  def isEmpty: Boolean

  /**
   * Copies values of this segment to a vector.
   *
   */
  def toVector: Vector[A] // use for Grouped and repetition parsers

  //def toList:List[A] 

  // used for Grouped, which must return a list
  import scala.collection.mutable.Builder
  def copyTo[B >: A, That](builder: Builder[B, That]): Builder[B, That]

  /**
   * Copies values of this segment to an array.
   *  Fills the given array `xs` with values of this segment, beginning at index `start`.
   *  Copying will stop once either the end of the current segment is reached,
   *  or the end of the array is reached.
   *
   *  @param  xs     the array to fill.
   *  @param  offset the offset in the array from which messages must be copied.
   *  @tparam B      the type of the messages of the array.
   *
   *  @usecase def copyToArray(xs: Array[A], start: Int): Unit
   */
  //def copyToArray[B >: A](dst:Array[B], offset:Int):Unit

  /**
   * Copies values of this segment to an array.
   *  Fills the given array `xs` with values of this segment.
   *  Copying will stop once either the end of the current segment is reached,
   *  or the end of the array is reached.
   *
   *  @param  xs     the array to fill.
   *  @tparam B      the type of the messages of the array.
   *
   *  @usecase def copyToArray(xs: Array[A]): Unit
   */
  //def copyToArray[B >: A](dst:Array[B]):Unit =
  //copyToArray(dst, 0)

  /**
   * Converts this segment to an array.
   *
   *  @tparam B the type of the messages of the array. A `ClassManifest` for
   *            this type must be available.
   *  @return   an array containing all messages of this segment.
   *
   *  @usecase def toArray: Array[A]
   *  @return  an array containing all messages of this segment.
   *           A `ClassManifest` must be available for the message type of this segment.
   */
  //def toArray[B >: A: ClassManifest]: Array[B] = {
  //  val bs = new Array[B](length)
  // copyToArray(bs)
  //	bs
  //}

  /**
   * Converts this segment to an traversable.
   *
   *  @tparam B the type of the messages of the array. A `ClassManifest` for
   *            this type must be available.
   *  @return   an array containing all messages of this segment.
   *
   *  @usecase def toArray: Array[A]
   *  @return  an array containing all messages of this segment.
   *           A `ClassManifest` must be available for the message type of this segment.
   */
  // def toTraversable:Traversable[A] = iterator.toTraversable

  // def iterator:Iterator[A]

  /**
   * Poison all messages in this segment
   *
   * @param m a message typeclass used to poison every message in this segment.
   */
  def poison(signal: Signal)(implicit m: Message[A]): Unit =
    foreach { m.poison(_, signal) }

}

object Seg {

  final val Nil = NilSeg

  final def unapplySeq[A](seg: Seg[A]): Option[Seq[A]] =
    seg match {
      case NilSeg => Some(Vector.empty)
      case VSeg(v) => Some(Seq(v))
      case SegN(fst, snd, vector) => Some(fst +: snd +: vector)
    }

  final def empty[A]: Seg[A] = NilSeg
  final def apply[A](): Seg[A] = NilSeg

  final def apply[A](value: A): Seg[A] = VSeg(value)

  final def apply[A](a1: A, a2: A): Seg[A] =
    SegN(a1, a2)

  final def apply[A](a1: A, a2: A, as: A*): Seg[A] =
    SegN(a1, a2, as)

  def wrap[A](vector: Vector[A]): Seg[A] =
    SegN(vector)

  def wrap[A](array: Array[A]): Seg[A] = {
    if (array.isEmpty)
      NilSeg
    else if (array.length == 1) {
      VSeg(array(0))
    } else
      SegN(array)
  }

  def wrap[A](t: Traversable[A]): Seg[A] = {
    if (t.isEmpty)
      NilSeg
    else {
      val a = t.head
      val tail = t.tail
      if (tail.isEmpty)
        VSeg(a)
      else {
        SegN(a, tail.head, tail.tail)
      }
    }
  }

  def zipAsMuch[A, B](as: Seg[A], bs: Seg[B]): (Seg[(A, B)], Option[Either[Seg[A], Seg[B]]]) = {

    as match {
      case NilSeg =>
        bs match {
          case NilSeg => (NilSeg, None)
          case _ => (NilSeg, Some(Right(bs)))
        }
      case VSeg(a) =>
        bs match {
          case NilSeg => (NilSeg, Some(Left(as)))
          case VSeg(b) => (VSeg((a, b)), None)
          case _ => (VSeg((a, bs.head)), Some(Right(bs.tail)))
        }
      case SegN(a1, a2, va) =>
        bs match {
          case NilSeg => (NilSeg, Some(Left(as)))
          case VSeg(b) => (VSeg((as.head, b)), Some(Left(as.tail)))
          case SegN(b1, b2, vb) =>
            val seg = SegN((a1, b1), (a2, b2), va.zip(vb))
            val la = va.length
            val lb = vb.length
            if (la == lb)
              (seg, None)
            else if (la < lb)
              (seg, Some(Right(Seg.wrap(vb.drop(la)))))
            else
              (seg, Some(Left(Seg.wrap(va.drop(lb)))))
        }

    }
  }

}
