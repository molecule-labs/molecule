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
package parsing

/**
 * Incremental combinator parser.
 *
 * Such parser can be used to parse raw ByteBuffer's ala AttoParsec (see molecule-nio).
 * See http://hackage.haskell.org/packages/archive/attoparsec/0.10.1.0/doc/html/Data-Attoparsec-ByteString.html
 *
 * @tparam A the type of the input messages accepted by a parser
 *         B the type of the result
 *
 */
sealed trait ParseResult[+B, +A] {
  def map[C](f: B => C): ParseResult[C, A]
}

/**
 * The parse succeeded.
 *
 * An object of this class contains the result of the parse and the remaining segment that
 * can be used in a subsequent parse cycle.
 */
case class Done[+B, +A](result: B, rem: Seg[A]) extends ParseResult[B, A] {
  def map[C](f: B => C): Done[C, A] = Done(f(result), rem)
}

/**
 * The parser needs more data.
 *
 * An object of this class contains the parser that must be used to proceed with
 * parsing once more data is available.
 */
case class Partial[+B, A](parser: Parser[A, B]) extends ParseResult[B, A] {
  def map[C](f: B => C): Partial[C, A] = Partial(parser.map(f))
}

/**
 * The parse failed.
 *
 * The error message is constructed lazily for performance when parsers are used
 * in alternative choices. Use extractors to retrieve the message.
 */
class Fail private (val name: String, _msg: => String) extends Signal with ParseResult[Nothing, Nothing] {
  lazy val msg = _msg
  def map[C](f: Nothing => C): ParseResult[C, Nothing] = this
  override def toString() = "Parser " + name + " failed:" + msg
}

object Fail {
  def apply(name: String, msg: => String): Fail = new Fail(name, msg)
  def unapply(error: Fail): Option[(String, String)] = Some((error.name, error.msg))
}

/**
 * Incremental parser
 *
 * @tparam A the type of the input messages accepted by a parser
 *         B the type of the result
 *
 */
abstract class Parser[A, +B] {
  outer =>

  import Parser._

  /**
   * The name of this parser (used for debugging)
   * @return name of this parser
   */
  def name: String

  // protected type Res = ParseResult[B, A]

  /**
   * Apply this parser to the next segment.
   *
   * @param seg the segment to parse for the next message
   * @return a parse result
   */
  def apply(seg: Seg[A]): ParseResult[B, A]

  /**
   * Apply this parser to the last segment.
   *
   * @param seg the segment to parse for a new message
   * @param signal the signal accompanying the last segment
   * @return Nothing if there is no new message left. Some Fail or Done
   * depending on if the last parse failed or succeeded.
   */
  final def result(seg: Seg[A], signal: Signal): Option[Either[Fail, Done[B, A]]] =
    if (seg.isEmpty) {
      result(signal)
    } else {
      apply(seg) match {
        case d: Done[_, _] => Some(Right(d))
        case Partial(parser) => parser.result(signal)
        case fail: Fail => Some(Left(fail))
      }
    }

  /**
   * Request result because there is no more data
   */
  def result(signal: Signal): Option[Either[Fail, Done[B, A]]]

  /**
   * Map the result of this parser to another result C
   */
  final def map[C](f: B => C): Parser[A, C] = new Parser[A, C] {
    def name: String = "map"
    def apply(seg: Seg[A]) = outer(seg).map(f)
    def result(signal: Signal): Option[Either[Fail, Done[C, A]]] =
      outer.result(signal).map(_.fold(Left(_), done => Right(done.map(f))))
  }

  /**
   * Sequence with a second parsers only after this one produced
   * a result.
   *
   * @param nextT
   * @return the resulting parser
   */
  def flatMap[C](f: B => Parser[A, C]): Parser[A, C] = new Parser[A, C] {
    def name: String = "flatMap"
    def apply(seg: Seg[A]) =
      outer(seg) match {
        case Partial(parser) =>
          Partial(parser.flatMap(f))
        case Done(b, as) =>
          f(b)(as)
        case fail: Fail =>
          fail
      }
    def result(signal: Signal): Option[Either[Fail, Done[C, A]]] =
      outer.result(signal) match {
        case None => None
        case Some(Right(Done(b, as))) => f(b).result(as, signal)
        case Some(Left(fail)) => Some(Left(fail))
      }
  }

  /**
   * transforms {@code this} of type {@code Parser[A,B]} into a parser of type {@codeParser[Z,B]}.
   *
   * @author Koen Daenen
   *
   * @param g function that maps a message of type Z into a message of type A.
   *          Given an input stream {@code ichan: IChan[Z]} and a {@code parser: Parser[A,B]},
   *          the stream transformation {@code ichan.parse(parser.contraMap(g,h)) }
   *          is equivalent to {@code ichan.map(g).parse(parser)}.
   *          The reason for contraMap is to be able to apply g until a first parsed result on a {@code input:Input[Z]};
   *          {@code input.read(parser.contraMap(g,h))} will leave, after reading the first parsed result,
   *          the stream untouched and of type Z, so one map want to apply another parser of type Parser[Z,C] afterwards.
   *
   * @param h inverse function of g.
   *        A parser may partially consume a message of an input stream.
   *        So if {@code this} leaves a partially consumed message of type A on the stream,
   *        that message will be reverse mapped to Z with function h.
   *        If h(g(a)) is not equal to a, the contraMapped parser may give unexpected results in case of partially consumed messages.
   *        In case the function h is not defined, one must use {@code contraMap[Z](g: Z => A, consume:(Z,A) => Z): Parser[Z, B]}.
   *
   * @return a contraMapped parser
   */
  def contraMapReversible[Z](g: Z => A, h: A => Z): Parser[Z, B] =
    contraMap[Z](g, (z, a) => h(a))

  /**
   * transforms {@code this} of type {@code Parser[A,B]} into a parser of type {@codeParser[Z,B]}.
   *
   * @author Koen Daenen
   *
   * @param g function that maps a message of type Z into a message of type A.
   *          Given an input stream {@code ichan: IChan[Z]} and a {@code parser: Parser[A,B]},
   *          the stream transformation {@code ichan.parse(parser.contraMap(g,h)) }
   *          is equivalent to {@code ichan.map(g).parse(parser)}.
   *          The reason for contraMap is to be able to apply g until a first parsed result on a {@code input:Input[Z]};
   *          {@code input.read(parser.contraMap(g,h))} will leave, after reading the first parsed result,
   *          the stream untouched and of type Z, so one map want to apply another parser of type Parser[Z,C] afterwards.
   *
   * @param consume function to generate a partially consumed message type Z in case the inverse function of g does not exists.
   *        A parser may partially consume a message of an input stream.
   *        So if {@code this} leaves a partially consumed message of type A on the stream,
   *        an equally consumed message of type Z will generated with function {@code consume}.
   *        In the function {@code consume(z:A,a:A):Z}, {@code z} is the message to be partially consumed,
   *        {@code a} is the partially consume value of g(z); the return value must be the partially consume z such that
   *        {@code g( consume(z,a) ) == a } and when
   *        {@code g(z) == a} than {@code consume(z,a) == z }
   *        If consume is not correctly specified, the contraMapped parser may give unexpected results in case of partially consumed messages.
   *
   * @return a contraMapped parser
   */
  def contraMap[Z](g: Z => A, consume: (Z, A) => Z): Parser[Z, B] = new Parser[Z, B] {
    final def name: String = outer.name

    def apply(seg: Seg[Z]): ParseResult[B, Z] = {
      outer.apply(seg.map(g)) match {
        case Done(result, rem) =>
          /*
           * Note: The reason why I convert the segment zseg to a vector is to drop the
           * elements without poisoning them, which drop on segment would do.
           * I need just a segment of the remaining part. The element that I drop 
           * have been parsed already into result.
           * 
           * rem MUST not be longer than seg as it is the remaining of the
           * parsing of seg.map(g) 
           */
          if (seg.length == 0) Done(result, NilSeg)
          else if (rem.isEmpty) Done(result, NilSeg)
          else {
            val remZ = seg.toVector.drop(seg.length - rem.length)
            val z = consume(remZ.head, rem.head)
            Done(result, z +: Seg.wrap(remZ.tail))
          }

        case Partial(parser) =>
          Partial(parser.contraMap(g, consume))
        case f: Fail => f
      }

    }

    def result(signal: Signal): Option[Either[Fail, Done[B, Z]]] =
      outer.result(signal) match {
        case None => None
        case Some(Left(fail)) => Some(Left(fail))
        case Some(Right(Done(result, rem))) =>
          /* Request result when there is no more data,
           * so rem is assumed to be empty. 
           */
          Some(Right(Done(result, Seg())))
      }
  }

  /**
   * Filter results of this parser.
   *
   * @param p predicate that returns true for results accepted by this parser
   * @return a filtered parser
   */
  final def filter(p: B => Boolean): Parser[A, B] =
    filterInvariant(outer, outer, p)

  private final def filterInvariant[Binv](reset: Parser[A, Binv], current: Parser[A, Binv], p: Binv => Boolean): Parser[A, Binv] = new Parser[A, Binv] {
    def name: String = "filter"
    def apply(seg: Seg[A]) =
      current(seg) match {
        case done @ Done(b, as) =>
          if (p(b)) done else outer.filterInvariant(reset, reset, p)(as)
        case Partial(next) => Partial(filterInvariant(reset, next, p))
        case err => err
      }

    def result(signal: Signal) =
      current.result(signal) match {
        case done @ Some(Right(Done(b, as))) =>
          if (p(b)) done else outer.filterInvariant(reset, reset, p).result(as, signal)
        case other => other
      }
  }

  def collect[C](f: PartialFunction[B, C]): Parser[A, C] =
    collectInvariant(outer, outer, f)

  private final def collectInvariant[Binv, C](reset: Parser[A, Binv], current: Parser[A, Binv], f: PartialFunction[Binv, C]): Parser[A, C] = new Parser[A, C] {
    def name: String = "collect"
    def apply(seg: Seg[A]) =
      current(seg) match {
        case Done(b, as) =>
          if (f.isDefinedAt(b)) Done(f(b), as)
          else collectInvariant(reset, reset, f)(as)
        case Partial(next) => Partial(outer.collectInvariant(reset, next, f))
        case err: Fail => err
      }
    def result(signal: Signal) =
      current.result(signal) match {
        case None => None
        case done @ Some(Right(Done(b, as))) =>
          if (f.isDefinedAt(b)) Some(Right(Done(f(b), as)))
          else outer.collectInvariant(reset, reset, f).result(as, signal)
        case Some(Left(fail)) => Some(Left(fail))
      }
  }

  /**
   * A parser combinator for sequential composition
   *
   * <p> `p ~ q' succeeds if `p' succeeds and `q' succeeds on the input
   *          left over by `p'.</p>
   *
   * @param q a parser that will be executed after `p' (this parser) succeeds
   * @return a `Parser' that -- on success -- returns a `~' (like a Pair, but easier to pattern match on)
   *         that contains the result of `p' and that of `q'.
   *         The resulting parser fails if either `p' or `q' fails.
   */
  def ~[U](p: => Parser[A, U]): Parser[A, ~[B, U]] = for (b <- this; u <- p) yield new ~(b, u)

  /**
   * A parser combinator for function application
   *
   * <p>`p ^^ f' succeeds if `p' succeeds; it returns `f' applied to the result of `p'.</p>
   *
   * @param f a function that will be applied to this parser's result (see `map' in `ParseResult').
   * @return a parser that has the same behaviour as the current parser, but whose result is
   *         transformed by `f'.
   */
  def ^^[U](f: B => U): Parser[A, U] = map(f)

  /**
   * A parser combinator for substituting a result with another
   *
   * <p>`p ^^^ r' succeeds if `p' succeeds; it returns `r'</p>
   *
   * @param r the substitution result
   * @return a parser that has the same behaviour as the current parser, but whose result is
   *         replaced by `r'.
   */
  def ^^^[U](r: U): Parser[A, U] = ^^(x => r)

  /**
   * A parser combinator for alternative composition
   *
   * <p>`p | q' succeeds if `p' succeeds or `q' succeeds
   *          Note that `q' is only tried if `p's failure is non-fatal (i.e., back-tracking is
   *          allowed).</p>
   *
   * @param q a parser that will be executed if `p' (this parser) fails (and allows back-tracking)
   * @return a `Parser' that returns the result of the first parser to succeed (out of `p' and `q')
   *         The resulting parser succeeds if (and only if) <ul>
   *           <li> `p' succeeds, <i>or</i>  </li>
   *           <li> if `p' fails allowing back-tracking and `q' succeeds. </li> </ul>
   */
  def |[U >: B](alternative: => Parser[A, U]): Parser[A, U] =
    new AltParserInit(this, alternative)

  private[this] final class AltParserInit[A, B](
      left: Parser[A, B], right: => Parser[A, B]) extends Parser[A, B] { outer =>
    def name: String = "alt"

    def apply(seg: Seg[A]) = {
      left(seg) match {
        case done: Done[_, _] => done
        case Partial(nleft) => Partial(new AltParserLeft(seg, nleft, right))
        case _ => right(seg)
      }
    }

    def result(signal: Signal) =
      left.result(signal) match {
        case None =>
          right.result(signal) match {
            case None => None
            case d @ Some(Right(done)) => d
            case fail => None
          }
        case d @ Some(Right(done)) => d
        case fail => fail
      }
  }

  private[this] final class AltParserLeft[A, B](
      trail: Seg[A], left: Parser[A, B], right: => Parser[A, B]) extends Parser[A, B] { outer =>
    def name: String = "alt"

    def apply(seg: Seg[A]) =
      left(seg) match {
        case done: Done[_, _] => done
        case Partial(nleft) => Partial(new AltParserLeft(trail ++ seg, nleft, right))
        case _ => right(trail ++ seg)
      }

    def result(signal: Signal) =
      left.result(signal) match {
        case None =>
          right(trail) match {
            case done: Done[_, _] => Some(Right(done))
            case Partial(parser) => parser.result(signal) match {
              case Some(Left(_)) => None
              case other => other
            }
            case _ => None
          }
        case done @ Some(Right(_)) => done
        case fail =>
          right(trail) match {
            case done: Done[_, _] => Some(Right(done))
            case Partial(parser) => parser.result(signal)
            case _ => fail
          }
      }
  }

  /**
   * A parser combinator for sequential composition which keeps only the right result
   *
   * <p> `p ~> q' succeeds if `p' succeeds and `q' succeeds on the input
   *           left over by `p'.</p>
   *
   * @param q a parser that will be executed after `p' (this parser) succeeds
   * @return a `Parser' that -- on success -- returns the result of `q'.
   */
  def ~>[U](p: => Parser[A, U]): Parser[A, U] =
    for (a <- this; b <- p) yield b

  /**
   * A parser combinator for sequential composition which keeps only the left result
   *
   * <p> `p &lt;~ q' succeeds if `p' succeeds and `q' succeeds on the input
   *           left over by `p'.</p>
   *
   * <b>Note:</b> &lt;~ has lower operator precedence than ~ or ~>.
   *
   * @param q a parser that will be executed after `p' (this parser) succeeds
   * @return a `Parser' that -- on success -- returns the result of `p'.
   */
  def <~[U](p: => Parser[A, U]): Parser[A, B] =
    for (a <- this; b <- p) yield a

  /**
   * Returns a parser that repeatedly parses what this parser parses
   *
   * @return rep(this)
   */
  def *(implicit parsers: Parsers[A]) = parsers.rep(this)

  /**
   * Returns a parser that repeatedly (at least once) parses what this parser parses.
   *
   * @return rep1(this)
   */
  def +(implicit parsers: Parsers[A]) = parsers.rep1(this)

  /**
   * Returns a parser that optionally parses what this parser parses.
   *
   * @return opt(this)
   */
  def ?(implicit parsers: Parsers[A]) = parsers.opt(this)
}

object Parser {

  val INPUT_MISSING_MSG = "Input missing, no more data"

  /**
   * Parser that returns v as result
   */
  //  def apply[Elem, T](v:T):Parser[Elem, T] = new Parser[Elem, T] {
  //	def apply(seg:Seg[Elem]) = Done(v, seg)
  //	def result = Right(Right(Done(v, NilSeg)))
  //  }
}
