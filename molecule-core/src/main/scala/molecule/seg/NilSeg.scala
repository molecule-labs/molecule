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

case object NilSeg extends Seg[Nothing] {

  def map[B](f: Nothing => B): Seg[Nothing] = this

  def flatMap[B](f: Nothing => Seg[B]): Seg[Nothing] = this

  def flatMapTraversable[B](f: Nothing => Traversable[B]): Seg[Nothing] = this

  def filter(p: Nothing => Boolean)(implicit m: Message[Nothing]) = this

  def collect[B](f: PartialFunction[Nothing, B])(implicit m: Message[Nothing]): Seg[B] = this

  def takeWhile(p: Nothing => Boolean): Seg[Nothing] = this

  def dropWhile(p: Nothing => Boolean)(implicit m: Message[Nothing]): Seg[Nothing] = this

  def dropRightWhile(p: Nothing => Boolean)(implicit m: Message[Nothing]): Seg[Nothing] = this

  def exists(p: Nothing => Boolean): Boolean = false

  def indexWhere(p: Nothing => Boolean): Int = -1

  def drop(n: Int)(implicit m: Message[Nothing]): Seg[Nothing] = this

  def span(p: Nothing => Boolean) = (this, this)

  def smap[S, B](z: S)(f: (S, Nothing) => (S, B)): (S, Seg[B]) = (z, NilSeg)

  def scanLeft[B](z: B)(op: (B, Nothing) => B): Seg[B] = this

  def splitAt(n: Int) = (this, this)

  def foreach[U](f: Nothing => U): Unit = {}

  def foldLeft[B](z: B)(f: (B, Nothing) => B): B = z

  def flatten[B](implicit asTraversable: Nothing => /*<:<!!!*/ Traversable[B]): Seg[B] = this

  def concat[B](implicit asSeg: Nothing => /*<:<!!!*/ Seg[B]): Seg[B] = this

  def zip[B](seg: Seg[B]): (Seg[(Nothing, B)], Option[Either[Seg[Nothing], Seg[B]]]) =
    seg match {
      case NilSeg => (NilSeg, None)
      case _ => (NilSeg, Some(Right(seg)))
    }

  def pop(): (VSeg[Nothing], Seg[Nothing]) =
    sys.error("Seg empty")

  def step[B](empty: => B, more: (Nothing, Seg[Nothing]) => B): B =
    empty

  def head: Nothing =
    throw new NoSuchElementException

  def last: Nothing =
    throw new NoSuchElementException

  def tail: Seg[Nothing] =
    sys.error("Seg empty")

  def init: Seg[Nothing] =
    sys.error("Seg empty")

  def +:[B >: Nothing](b: B): Seg[B] =
    VSeg(b)

  def :+[B >: Nothing](b: B): Seg[B] =
    VSeg(b)

  def ++[B >: Nothing](seg: Seg[B]): Seg[B] = seg

  def length: Int = 0

  def isEmpty: Boolean = true

  def toVector = Vector.empty[Nothing]
  //def toList = Nil

  import scala.collection.mutable.Builder
  def copyTo[B >: Nothing, That](builder: Builder[B, That]): Builder[B, That] = builder

  //def copyToArray[B >: Nothing](dst:Array[B], offset:Int):Unit = ()

  def iterator = Iterator.empty

  override def toString() = "NilSeg"
}
