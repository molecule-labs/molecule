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

abstract class Message[-A] { outer =>
  def poison(a: A, signal: Signal)

  def unmap[B](f: B => A): Message[B] = this match {
    case PureMessage => PureMessage
    case _ =>
      new Message[B] {
        def poison(message: B, signal: Signal): Unit =
          outer.poison(f(message), signal)
      }
  }

  def unmapMaybe[B](f: B => Option[A]): Message[B] = this match {
    case PureMessage => PureMessage
    case _ =>
      new Message[B] {
        def poison(message: B, signal: Signal): Unit =
          f(message) match {
            case Some(a) => outer.poison(a, signal)
            case None =>
          }
      }
  }

  def toTraversable: Message[Traversable[A]] = this match {
    case PureMessage => PureMessage
    case _ =>
      new Message[Traversable[A]] {
        def poison(message: Traversable[A], signal: Signal): Unit =
          message foreach { outer.poison(_, signal) }
      }
  }

  def toSeg: Message[Seg[A]] = this match {
    case PureMessage => PureMessage
    case _ =>
      new Message[Seg[A]] {
        def poison(message: Seg[A], signal: Signal): Unit =
          message foreach { outer.poison(_, signal) }
      }
  }

  def zip[B](mb: Message[B]): Message[(A, B)] = (this, mb) match {
    case (PureMessage, PureMessage) => PureMessage
    case (_, PureMessage) => mb.unmap[(A, B)](_._2)
    case (PureMessage, _) => this.unmap[(A, B)](_._1)
    case _ =>
      new Message[(A, B)] {
        def poison(message: (A, B), signal: Signal): Unit = {
          outer.poison(message._1, signal)
          mb.poison(message._2, signal)
        }
      }
  }

  def either[B](mb: Message[B]): Message[Either[A, B]] = (this, mb) match {
    case (PureMessage, PureMessage) => PureMessage
    case (_, PureMessage) => this.unmap[Either[A, B]](_.left.get)
    case (PureMessage, _) => mb.unmap[Either[A, B]](_.right.get)
    case _ =>
      new Message[Either[A, B]] {
        def poison(message: Either[A, B], signal: Signal): Unit = {
          message match {
            case Left(a) => outer.poison(a, signal)
            case Right(b) => mb.poison(b, signal)
          }
        }
      }
  }
}

/**
 * Traits that marks "pure" message i.e. messages who do not carry channels.
 */
trait PureMessage extends Message[Any]

/**
 * Message typeclass for "pure" message.
 */
object PureMessage extends PureMessage {
  def poison(message: Any, signal: Signal): Unit = {}
}

/**
 * The LowestPrioMessageImplicits trait provides implicit objects of type Message[A]
 * with PureMessage as implementation for all types,
 * but that are partially overridden by higher-priority conversions in objects
 * LowPrioMessageImplicits and Message, or custom objects.
 */
trait LowestPrioMessageImplicits {

  /**
   * Defines an implicit Message[A] for any type A <: AnyRef
   * unless overridden by a higher-priority conversion of type
   *   implicit def bMessage[A <: B]: Message[A]
   *
   * @return PureMessage,
   *         which means that an object of type A is assumed NOT to carry resources,
   *         that should be cleaned-up when a channel of type A gets poisoned.
   *
   * In the object Message a number of more specific implicit
   * messages are defined, but in case you define your own class
   * embedding resources, it is your own responsibility to define
   * an implicit object implementing the abstract class Message
   * in which you customize the clean-up.
   */
  implicit def anyRefMessage[A <: AnyRef]: Message[A] = PureMessage

  /**
   * Defines an implicit Message[A] for any type A <: AnyVal
   * for which there is no implicit in scope defined as
   *   implicit def bMessage[A <: B]: Message[A]
   *
   * @return PureMessage
   *
   * classes of type AnyVal are not supposed to carry resources, that require
   * customized cleanup, still bear in mind the remark made at def anyRefMessage
   * in case you would make your custom Val types.
   *
   */
  implicit def anyValMessage[A <: AnyVal]: Message[A] = PureMessage
}

/**
 * The LowPrioMessageImplicits trait provides implicit objects of type Message[T[A]]
 * for a number of general typed classes,
 * but that are (or can be) partially overridden by higher-priority conversions in
 * the object Message, or custom objects.
 *
 * Currently only Traversable[A] is covered, which does cover (almost) the whole
 * Scala colletion library. Take care when using Java collections. Even if you would
 * import scala.collection.JavaConversions._, the Java collections would get augmented
 * with Scala collection operators, but the implicit Message[T[A]] will not be found
 * and Message[AnyRef] will be taken as defined in LowestPrioMessageImplicits.
 * So, either you have to define the implicit in your custom object add it to this trait.
 */
trait LowPrioMessageImplicits extends LowestPrioMessageImplicits {

  implicit def traversableIsMessage[CC[A] <: Traversable[A], A](implicit ma: Message[A]): Message[CC[A]] =
    ma.toTraversable

}

/**
 * The object Message provides implicit objects of type Message[A]
 * for a number of common final classes or sealed abstract classes.
 * In case you require a custom poison implementation for your custom class C,
 * define a companion object that extends LowPrioMessageImplicits in which you define
 * the implicit object Message[C], see examples in package molecule.request
 */
object Message extends LowPrioMessageImplicits {

  case class MessageSummand[A](m: Message[A], manifest: ClassManifest[A]) {
    def instanceOf(o: Any): Boolean = manifest.erasure.isInstance(o)
    def poison(o: Any, signal: Signal): Unit = {
      //println("poison:" + manifest)
      m.poison(o.asInstanceOf[A], signal)
    }
  }

  object MessageSummand {
    implicit def mToSummand[A](m: Message[A])(implicit cm: ClassManifest[A]): MessageSummand[A] =
      MessageSummand(m, cm)
  }

  def apply[A](implicit m: Message[A]): Message[A] = m

  def poison[A: Message](a: A, signal: Signal): Unit = Message[A].poison(a, signal)

  def impure[A](poisonMessage: (A, Signal) => Unit): Message[A] = new Message[A] {
    def poison(m: A, signal: Signal): Unit = poisonMessage(m, signal)
  }

  def apply[M](m1: MessageSummand[_ <: M], m2: MessageSummand[_ <: M], ms: MessageSummand[_ <: M]*): Message[M] = new Message[M] {
    def poison(h: M, signal: Signal) =
      if (m1.instanceOf(h))
        m1.poison(h, signal)
      else if (m2.instanceOf(h))
        m2.poison(h, signal)
      else
        ms.find(_.instanceOf(h)).foreach(_.poison(h, signal))
  }

  implicit val unitMessage: Message[Unit] = PureMessage
  implicit val intMessage: Message[Int] = PureMessage
  implicit val charMessage: Message[Char] = PureMessage
  implicit val longMessage: Message[Long] = PureMessage
  implicit val boolMessage: Message[Boolean] = PureMessage
  implicit val stringMessage: Message[String] = PureMessage
  implicit val byteMessage: Message[Byte] = PureMessage
  implicit val shortMessage: Message[Short] = PureMessage
  implicit val floatMessage: Message[Float] = PureMessage
  implicit val doubleMessage: Message[Double] = PureMessage
  implicit val bigDecimalMessage: Message[BigDecimal] = PureMessage

  implicit val signalMessage: Message[Signal] = PureMessage

  import java.nio.{ CharBuffer, ByteBuffer }
  implicit val bbMessage: Message[ByteBuffer] = PureMessage
  implicit val cbMessage: Message[CharBuffer] = PureMessage

  implicit def optionIsMessage[A](implicit m: Message[A]): Message[Option[A]] =
    m.unmapMaybe[Option[A]](identity)

  implicit def eitherIsMessage[A, B](implicit ma: Message[A], mb: Message[B]): Message[Either[A, B]] =
    ma either mb

  implicit def product1IsMessage[A](implicit m: Message[A]): Message[Product1[A]] =
    m.unmap[Product1[A]](_._1)

  implicit def product2IsMessage[A, B](implicit ma: Message[A], mb: Message[B]): Message[Product2[A, B]] =
    (ma zip mb).unmap[Product2[A, B]](p => (p._1, p._2))

  implicit def product3IsMessage[A, B, C](implicit ma: Message[A], mb: Message[B], mc: Message[C]): Message[Product3[A, B, C]] =
    (ma zip mb zip mc).unmap[Product3[A, B, C]] { case (a, b, c) => ((a, b), c) }

  implicit def product4IsMessage[P <: Product4[A, B, C, D], A, B, C, D](implicit ma: Message[A], mb: Message[B], mc: Message[C], md: Message[D]): Message[Product4[A, B, C, D]] =
    (ma zip mb zip mc zip md).unmap[Product4[A, B, C, D]] { case (a, b, c, d) => (((a, b), c), d) }

  implicit def product5IsMessage[P <: Product5[A, B, C, D, E], A, B, C, D, E](implicit ma: Message[A], mb: Message[B], mc: Message[C], md: Message[D], me: Message[E]): Message[Product5[A, B, C, D, E]] =
    (ma zip mb zip mc zip md zip me).unmap[Product5[A, B, C, D, E]] { case (a, b, c, d, e) => ((((a, b), c), d), e) }

  implicit def mapIsMessage[A, K](implicit ma: Message[A]): Message[Map[K, A]] = new Message[Map[K, A]] {
    def poison(map: Map[K, A], signal: Signal): Unit = map.foreach(kv => ma.poison(kv._2, signal))
  }

  implicit def segOfSegIsMessage[A](implicit ma: Message[A]): Message[Seg[A]] =
    ma.toSeg

}