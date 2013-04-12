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
 * Segment that contains one and only one value
 */
private[seg] case class VSeg[+A](value: A) extends Seg[A] {

  def map[B](f: A => B): VSeg[B] = new VSeg(f(value))

  def flatMap[B](f: A => Seg[B]): Seg[B] =
    f(value)

  def flatMapTraversable[B](f: A => Traversable[B]): Seg[B] = {
    val fm = f(value)
    Seg.wrap(fm)
  }

  def filter(p: A => Boolean)(implicit m: Message[A]): Seg[A] =
    if (p(value)) this else { m.poison(value, EOS); NilSeg }

  def collect[B](f: PartialFunction[A, B])(implicit m: Message[A]): Seg[B] =
    if (f.isDefinedAt(value)) VSeg(f(value)) else { m.poison(value, EOS); NilSeg }

  def takeWhile(p: A => Boolean): (Seg[A], Seg[A]) =
    if (p(value)) (this, NilSeg) else (NilSeg, this)

  def dropWhile(p: A => Boolean)(implicit m: Message[A]): Seg[A] =
    if (p(value)) { m.poison(value, EOS); NilSeg } else this

  def dropRightWhile(p: A => Boolean)(implicit m: Message[A]): Seg[A] =
    dropWhile(p)

  def foreach[U](f: A => U): Unit = f(value)

  def foldLeft[B](z: B)(f: (B, A) => B): B = f(z, value)

  def flatten[B](implicit asTraversable: A => /*<:<!!!*/ Traversable[B]): Seg[B] = {
    Seg.wrap(asTraversable(value))
  }

  def concat[B](implicit asSeg: A => /*<:<!!!*/ Seg[B]): Seg[B] = {
    asSeg(value)
  }

  def zip[B](seg: Seg[B]): (Seg[(A, B)], Option[Either[Seg[A], Seg[B]]]) =
    seg match {
      case NilSeg => (NilSeg, Some(Left(this)))
      case VSeg(b) => (VSeg((value, b)), None)
      case _ => (VSeg((value, seg.head)), Some(Right(seg.tail)))
    }

  def pop(): (VSeg[A], Seg[A]) = (this, NilSeg)

  def step[B](empty: => B, more: (A, Seg[A]) => B): B =
    more(value, NilSeg)

  def head: A = value

  def last: A = value

  def tail: Seg[A] = NilSeg

  def init: Seg[A] = NilSeg

  def exists(p: A => Boolean): Boolean = p(value)

  def indexWhere(p: A => Boolean) = if (p(value)) 0 else -1

  def drop(n: Int)(implicit m: Message[A]): Seg[A] =
    if (n <= 0) this else {
      m.poison(value, EOS)
      NilSeg
    }

  def span(p: A => Boolean): (Seg[A], Seg[A]) =
    if (p(value))
      (this, NilSeg)
    else
      (NilSeg, this)

  def smap[S, B](z: S)(f: (S, A) => (S, B)): (S, Seg[B]) = {
    val (s, b) = f(z, value)
    (s, new VSeg(b))
  }

  def scanLeft[B](z: B)(op: (B, A) => B): Seg[B] =
    new VSeg(op(z, value))

  def splitAt(n: Int): (Seg[A], Seg[A]) =
    if (n == 0)
      (NilSeg, this)
    else
      (this, NilSeg)

  def +:[B >: A](b: B): Seg[B] =
    SegN[B](b, value, Nil)

  def :+[B >: A](b: B): Seg[B] =
    SegN[B](value, b, Nil)

  def ++[B >: A](seg: Seg[B]): Seg[B] =
    seg match {
      case NilSeg => this
      case VSeg(v) =>
        SegN[B](value, v, Vector.empty)
      case _ =>
        value +: seg
    }

  def length: Int = 1

  def isEmpty: Boolean = false

  def copy[B >: A](value: B = this.value): VSeg[B] =
    new VSeg(value)

  def update[A](v: A): VSeg[A] = new VSeg(v)

  def toVector = Vector(value)

  // def toList = List(value)

  import scala.collection.mutable.Builder
  def copyTo[B >: A, That](builder: Builder[B, That]): Builder[B, That] = {
    builder += value
  }

  //def copyToArray[B >: A](dst:Array[B], offset:Int):Unit = 
  //  dst(offset) = value

  def iterator = Iterator.single(value)

  override def toString() = "[" + value.toString + "]"

}

object VSeg {

  def update[A](vs: VSeg[A], v: A) = vs.copy(value = v)

}
