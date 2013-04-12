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
import scala.annotation.tailrec

/**
 * TODO: try a finger tree or other efficient dequeue.
 */
private[seg] case class SegN[+A](fst: A, snd: A, vector: Vector[A]) extends Seg[A] {

  def apply(idx: Int) = vector(idx)

  def length: Int = 2 + vector.length

  def map[B](f: A => B): SegN[B] =
    new SegN(f(fst), f(snd), vector.map(f))

  def flatMap[B](f: A => Seg[B]): Seg[B] = {
    vector.foldLeft(NilSeg ++ f(fst) ++ f(snd))((b, a) => b ++ f(a))
  }

  def flatMapTraversable[B](f: A => Traversable[B]): Seg[B] = {
    val builder = Vector.newBuilder[B]
    builder ++= f(fst)
    builder ++= f(snd)
    SegN(vector.foldLeft(builder)((b, a) => b ++= f(a)).result)
  }

  def filter(p: A => Boolean)(implicit m: Message[A]): Seg[A] = {
    if (p(fst)) {
      if (p(snd)) {
        new SegN(fst, snd, filterVect(p, vector))
      } else {
        m.poison(snd, EOS)
        SegN.mk1(fst, filterVect(p, vector))
      }
    } else {
      m.poison(fst, EOS)
      if (p(snd)) {
        SegN.mk1(snd, filterVect(p, vector))
      } else {
        m.poison(snd, EOS)
        SegN(filterVect(p, vector))
      }
    }
  }

  private[this] final def filterVect(p: A => Boolean, vector: Vector[A])(implicit m: Message[A]): Vector[A] = {
    val b = Vector.newBuilder[A]
    for (a <- vector) {
      if (p(a)) b += a else m.poison(a, EOS)
    }
    b.result
  }

  private[this] final def dropWhileVect(p: A => Boolean, vector: Vector[A])(implicit m: Message[A]): Vector[A] = {
    val it = vector.iterator
    @scala.annotation.tailrec
    def drop: Unit =
      if (it.hasNext) {
        val a = it.next
        if (p(a)) {
          m.poison(a, EOS)
          drop
        }
      }
    drop
    if (it.isEmpty)
      Vector.empty[A]
    else {
      val b = Vector.newBuilder[A]
      while (it.hasNext) {
        val a = it.next
        b += a
      }
      b.result
    }
  }

  def dropWhile(p: A => Boolean)(implicit m: Message[A]): Seg[A] = {
    if (p(fst)) {
      if (p(snd)) {
        SegN(dropWhileVect(p, vector))
      } else {
        SegN.mk1(snd, vector)
      }
    } else
      this
  }

  def dropRightWhile(p: A => Boolean)(implicit m: Message[A]): Seg[A] = {

    def dropRightWhileVect(p: A => Boolean, vector: Vector[A])(implicit m: Message[A]): Vector[A] = {
      val it = vector.reverseIterator
      @scala.annotation.tailrec
      def drop: Unit =
        if (it.hasNext) {
          val a = it.next
          if (p(a)) {
            m.poison(a, EOS)
            drop
          }
        }
      drop
      if (it.isEmpty)
        Vector.empty[A]
      else {
        val b = Vector.newBuilder[A]
        var v = Vector.empty[A]
        while (it.hasNext) {
          val a = it.next
          v = a +: v
        }
        v
      }
    }

    val v = dropRightWhileVect(p, vector)
    if (v.isEmpty) {
      if (p(snd)) {
        if (p(fst)) {
          NilSeg
        } else {
          VSeg(fst)
        }
      } else
        SegN(fst, snd, v)
    } else {
      SegN(fst, snd, v)
    }
  }

  def collect[B](f: PartialFunction[A, B])(implicit m: Message[A]): Seg[B] = {
    if (f.isDefinedAt(fst)) {
      if (f.isDefinedAt(snd)) {
        new SegN(f(fst), f(snd), collectVect(f, vector))
      } else {
        m.poison(snd, EOS)
        SegN.mk1(f(fst), collectVect(f, vector))
      }
    } else {
      m.poison(fst, EOS)
      if (f.isDefinedAt(snd)) {
        SegN.mk1(f(snd), collectVect(f, vector))
      } else {
        m.poison(snd, EOS)
        SegN(collectVect(f, vector))
      }
    }
  }

  private[this] final def collectVect[B](f: PartialFunction[A, B], vector: Vector[A])(implicit m: Message[A]): Vector[B] = {
    val it = vector.iterator
    @scala.annotation.tailrec
    def searchHit: Unit =
      if (it.hasNext) {
        val a = it.next
        if (!f.isDefinedAt(a)) {
          m.poison(a, EOS)
          searchHit
        }
      }
    searchHit
    if (it.isEmpty)
      Vector.empty[B]
    else {
      val b = Vector.newBuilder[B]
      while (it.hasNext) {
        val a = it.next
        if (f.isDefinedAt(a)) b += f(a) else m.poison(a, EOS)
      }
      b.result
    }
  }

  def foreach[U](f: A => U): Unit = {
    f(fst)
    f(snd)
    vector.foreach(f)
  }

  def foldLeft[B](z: B)(f: (B, A) => B): B = vector.foldLeft(f(f(z, fst), snd))(f)

  def flatten[B](implicit asTraversable: A => /*<:<!!!*/ Traversable[B]): Seg[B] =
    flatMapTraversable(asTraversable)

  def concat[B](implicit asSeg: A => /*<:<!!!*/ Seg[B]): Seg[B] =
    flatMap(asSeg)

  def step[B](empty: => B, more: (A, Seg[A]) => B): B =
    more(head, tail)

  def head: A = fst

  def last: A = if (vector.isEmpty) snd else vector.last

  def tail: Seg[A] =
    SegN.mk1(snd, vector)

  def init: Seg[A] =
    if (vector.isEmpty)
      VSeg(fst)
    else
      new SegN(fst, snd, vector.init)

  def zip[B](seg: Seg[B]): (Seg[(A, B)], Option[Either[Seg[A], Seg[B]]]) =
    seg match {
      case NilSeg => (NilSeg, Some(Left(this)))
      case VSeg(b) => (VSeg((fst, b)), Some(Left(this.tail)))
      case SegN(b1, b2, vb) =>
        val seg = SegN((fst, b1), (fst, b2), vector.zip(vb))
        val la = vector.length
        val lb = vb.length
        if (la == lb)
          (seg, None)
        else if (la < lb)
          (seg, Some(Right(Seg.wrap(vb.drop(la)))))
        else
          (seg, Some(Left(Seg.wrap(vector.drop(lb)))))
    }

  def pop(): (VSeg[A], Seg[A]) =
    (new VSeg(fst), tail)

  def exists(p: A => Boolean): Boolean =
    p(fst) || p(snd) || vector.exists(p)

  def drop(n: Int)(implicit m: Message[A]): Seg[A] = {
    val (a, b) = splitAt(n)
    a.poison(EOS)
    b
  }

  def indexWhere(p: A => Boolean) =
    if (p(fst))
      0
    else if (p(snd))
      1
    else {
      val i = vector.indexOf(p)
      if (i < 0) i else 2 + i
    }

  def span(p: A => Boolean): (Seg[A], Seg[A]) = {
    if (p(fst)) {
      if (p(snd)) {
        val (l, r) = vector.span(p)
        (new SegN(fst, snd, l), SegN(r))
      } else {
        (VSeg(fst), SegN.mk1(snd, vector))
      }
    } else
      (NilSeg, this)
  }

  def scanLeft[B](z: B)(op: (B, A) => B): Seg[B] = {
    val fstB = z
    val sndB = op(fstB, fst)
    val trdB = op(sndB, snd)
    new SegN(fstB, sndB, vector.scanLeft(trdB)(op))
  }

  def smap[S, B](z: S)(f: (S, A) => (S, B)): (S, Seg[B]) = {
    val s0 = z
    val (s1, fstB) = f(s0, fst)
    val (s2, sndB) = f(s1, snd)

    var acc = s2
    var r = Vector.newBuilder[B]
    r.sizeHint(vector)
    var it = vector.iterator
    while (it.hasNext) {
      val (s, b) = f(acc, it.next)
      acc = s
      r += b
    }
    (acc, new SegN(fstB, sndB, r.result))
  }

  def splitAt(n: Int): (Seg[A], Seg[A]) = {
    if (n == 0) {
      (NilSeg, this)
    } else if (n == 1) {
      (VSeg(fst), SegN.mk1(snd, vector))
    } else {
      val (l, r) = vector.splitAt(n - 2)
      (new SegN(fst, snd, l), SegN(r))
    }
  }

  final def +:[B >: A](b: B): SegN[B] =
    new SegN(b, fst, snd +: vector)

  final def :+[B >: A](b: B): SegN[B] =
    new SegN(fst, snd, vector :+ b)

  def ++[B >: A](seg: Seg[B]): SegN[B] =
    seg match {
      case NilSeg => this
      case VSeg(elem) => this.:+[B](elem)
      case SegN(nfst, nsnd, vect) =>
        if (vect.length > vector.length) {
          // new SegN(fst, snd, vector.foldRight[Vector[B]](nfst +: nsnd +: vect)(_ +: _))
          // Scala type inference???
          val z = nfst +: nsnd +: vect
          new SegN(fst, snd, vector.foldRight[Vector[B]](z)(_ +: _))
        } else {
          val z = vector.asInstanceOf[Vector[B]] :+ nfst :+ nsnd
          new SegN(fst, snd, vect.foldLeft[Vector[B]](z)(_ :+ _))
        }
    }

  def isEmpty: Boolean = false

  def toVector = fst +: snd +: vector

  //def toList = fst :: snd :: vector.toList

  import scala.collection.mutable.Builder
  def copyTo[B >: A, That](builder: Builder[B, That]): Builder[B, That] = {
    builder += fst
    builder += snd
    builder ++= vector
  }

  //def copyToArray[B >: A](dst:Array[B], offset:Int):Unit = {
  //  dst(offset) = fst
  //  dst(offset + 1) = snd
  //  vector.copyToArray(dst, offset + 2)
  //}

  // def iterator = fst +: snd +: vector.iterator

  override def toString() = "[" + fst + "," + snd + (if (vector.isEmpty) "]" else "," + vector.mkString("", ",", "]"))

}

object SegN {

  final private[seg] def apply[A](vector: Vector[A]): Seg[A] = {
    if (vector.length == 0) {
      NilSeg
    } else if (vector.length == 1) {
      new VSeg(vector.head)
    } else {
      val tail = vector.tail
      new SegN(vector.head, tail.head, tail.tail)
    }
  }

  // The JVM may decide to inline if we don't overload this methods  
  private final def mk1[A](a: A, v: Vector[A]): Seg[A] = {
    if (v.isEmpty)
      VSeg(a)
    else
      new SegN(a, v.head, v.tail)
  }

  final private[seg] def apply[A](a: A, v: Vector[A]): Seg[A] = mk1(a, v)

  final private[seg] def apply[A](a1: A, a2: A): Seg[A] =
    new SegN(a1, a2, Vector.empty)

  //  final private[seg] def apply[A](a1:A, a2:A, v:Vector[A]):Seg[A] = 
  //    new SegN(a1, a2, v)

  final private[seg] def apply[A](a1: A, a2: A, t: Traversable[A]): SegN[A] = {
    val b = Vector.newBuilder[A]
    b.sizeHint(t)
    b ++= t
    new SegN(a1, a2, b.result())
  }

  //  private[seg] def apply[A](t:Traversable[A], length:Int):SegN[A] = {
  //    val b = Vector.newBuilder[A]
  //    b.sizeHint(t, length)
  //    b ++= t
  //    new SegN(b.result())
  //  }

  private[seg] def apply[A](array: Array[A]): SegN[A] = {
    val b = Vector.newBuilder[A]
    b.sizeHint(array, array.length - 2)
    b ++= array
    new SegN(array(0), array(1), b.result())
  }

}
