/**
 * No Copyright.
 */

package molecule
package parsing

/**
 * Shameless rip of Scala parsers trait
 *
 * Elem is the type of input elements the provided parsers consume
 *
 * @author Sebastien Bocq
 */
trait Parsers[Elem] {

  implicit def parsers = this

  /**
   * A parser matching input elements that satisfy a given predicate
   *
   * <p>elem(kind, p) succeeds if the input starts with an element `e' for which p(e) is true.</p>
   *
   * @param  kind   The element kind, used for error messages
   * @param  p      A predicate that determines which elements match.
   * @return
   */
  def elem(kind: String, p: Elem => Boolean) =
    acceptIf(p)(kind + " expected but " + _ + " found")

  /**
   * A parser that matches only the given element `e'
   * <p>
   * The method is implicit so that elements can automatically be lifted to their parsers.
   * For example, when parsing `Token's, Identifier("new") (which is a `Token') can be used directly,
   * instead of first creating a `Parser' using accept(Identifier("new")).</p>
   *
   * @param e the `Elem' that must be the next piece of input for the returned parser to succeed
   * @return a `Parser' that succeeds if `e' is the next available input.
   */

  def elem(e: Elem): Parser[Elem, Elem] =
    accept(e)

  /**
   * A parser that matches only the given element `e'
   * <p>
   * The method is implicit so that elements can automatically be lifted to their parsers.
   * For example, when parsing `Token's, Identifier("new") (which is a `Token') can be used directly,
   * instead of first creating a `Parser' using accept(Identifier("new")).</p>
   *
   * @param e the `Elem' that must be the next piece of input for the returned parser to succeed
   * @return a `tParser' that succeeds if `e' is the next available input.
   */
  implicit def accept(e: Elem): Parser[Elem, Elem] =
    acceptIf(_ == e)("`" + e + "' expected but " + _ + " found")

  /**
   * A parser that matches only the given list of element `es'
   *
   * <p>accept(es) succeeds if the input subsequently provides the elements in the list `es'.</p>
   *
   * @param  es the list of expected elements
   * @return a Parser that recognizes a specified list of elements
   */
  def accept[ES <% List[Elem]](es: ES): Parser[Elem, Seg[Elem]] =
    acceptSeq(es)

  /**
   * The parser that matches an element in the domain of the partial function `f'
   * <p>
   * If `f' is defined on the first element in the input, `f' is applied to it to produce
   * this parser's result.</p>
   * <p>
   * Example: The parser <code>accept("name", {case Identifier(n) => Name(n)})</code>
   *          accepts an <code>Identifier(n)</code> and returns a <code>Name(n)</code>.</p>
   *
   * @param expected a description of the kind of element this parser expects (for error messages)
   * @param f a partial function that determines when this parser is successful and what its output is
   * @return A parser that succeeds if `f' is applicable to the first element of the input,
   *         applying `f' to it to produce the result.
   */
  def accept[U](expected: Elem, f: PartialFunction[Elem, U]): Parser[Elem, U] =
    acceptMatch(expected, f)

  def acceptIf(p: Elem => Boolean)(err: Elem => String): Parser[Elem, Elem] =
    new Parser[Elem, Elem] {
      def name = "acceptIf"
      def apply(seg: Seg[Elem]) =
        if (seg.isEmpty)
          Partial(this)
        else if (p(seg.head))
          Done(seg.head, seg.tail)
        else
          Fail(name, err(seg.head))

      def result(signal: Signal) =
        Some(Left(StdErrors.prematureSignal(name, NilSeg, signal)))
    }

  def acceptSignal(signal: Signal): Parser[Elem, Nothing] =
    new Parser[Elem, Nothing] {
      def name = "acceptSignal(" + signal + ")"
      def apply(seg: Seg[Elem]) =
        if (seg.isEmpty)
          Partial(this)
        else
          Fail(name, "Expected " + signal + " but found " + seg)

      def result(sig: Signal) =
        if (sig == signal)
          None
        else
          Some(Left(StdErrors.mismatch(name, signal, sig)))
    }

  def acceptAny: Parser[Elem, Elem] =
    new Parser[Elem, Elem] {
      def name = "acceptAny"
      def apply(seg: Seg[Elem]) =
        if (seg.isEmpty)
          Partial(this)
        else
          Done(seg.head, seg.tail)

      def result(signal: Signal) =
        Some(Left(StdErrors.prematureSignal(name, NilSeg, signal)))
    }

  def acceptMatch[U](expected: Elem, f: PartialFunction[Elem, U]): Parser[Elem, U] =
    new Parser[Elem, U] {
      def name = "acceptMatch"
      def apply(seg: Seg[Elem]) =
        if (seg.isEmpty)
          Partial(this)
        else if (f.isDefinedAt(seg.head))
          Done(f(seg.head), seg.tail)
        else
          StdErrors.mismatch(name, expected, seg.head)

      def result(signal: Signal) =
        Some(Left(StdErrors.prematureSignalMismatch(name, expected, NilSeg, signal)))
    }

  def acceptSeq[ES <% Iterable[Elem]](es: ES): Parser[Elem, Seg[Elem]] =
    es.foldRight[Parser[Elem, Seg[Elem]]](success(Seg.empty)) {
      (x, pxs) => elem(x) ~ pxs ^^ mkSeg
    }

  def mkList[T] = (_: ~[T, List[T]]) match { case x ~ xs => x :: xs }

  def mkSeg[T] = (_: ~[T, Seg[T]]) match { case x ~ xs => x +: xs }

  /**
   * A parser that results in an error
   *
   * @param msg The error message describing the failure.
   * @return A parser that always fails with the specified error message.
   */
  @deprecated("Use fail(msg: => String): Parser[ByteBuffer, Nothing] in stead. Not to be confused with scala.Predef.error(msg:String):Nothing or sys.error(msg:String):Nothing, which will generate a runtime error", "r95")
  def err(msg: => String): Parser[Elem, Elem] =
    new Parser[Elem, Elem] {
      def name = "err"
      def apply(seg: Seg[Elem]) = Fail(name, msg)
      def result(signal: Signal) = Some(Left(Fail(name, msg)))
    }

  /**
   * A parser that results in a parse failure
   *
   * @param msg The error message describing the failure.
   * @return A parser to Nothing that always generates a Fail result.
   */
  def fail(msg: => String): Parser[Elem, Nothing] =
    new Parser[Elem, Nothing] {
      def name = "fail"
      def apply(seg: Seg[Elem]) = Fail(name, msg)
      def result(signal: Signal) = Some(Left(Fail(name, msg)))
    }

  /**
   * A parser that always succeeds
   *
   * @param v The result for the parser
   * @return A parser that always succeeds, with the given result `v'
   */
  def success[T](v: T) =
    new Parser[Elem, T] {
      def name = "success"
      def apply(seg: Seg[Elem]) = Done(v, seg)
      def result(signal: Signal) = Some(Right(Done(v, NilSeg)))
    }

  /**
   * Log interactions with this parser
   */
  def log[T](p: => Parser[Elem, T])(name: String): Parser[Elem, T] =
    new Parser[Elem, T] {
      def name = "log"
      def apply(seg: Seg[Elem]) = {
        print(name + ": parsing " + seg)
        p(seg) match {
          case Partial(p) =>
            println(" --> partial")
            Partial(log(p)(name))
          case other =>
            println(" --> result: " + other)
            other
        }
      }
      def result(signal: Signal) = {
        print(name + ":" + signal)
        p.result(signal) match {
          case None =>
            println(" --> none")
            None
          case d @ Some(Right(done)) =>
            println(" --> " + done)
            d
          case f @ Some(Left(fail)) =>
            println(" --> " + fail)
            f
        }
      }
    }

  /**
   * A parser generator for repetitions.
   *
   * <p> rep(p)   repeatedly uses `p' to parse the input until `p' fails (the result is a List
   *  of the consecutive results of `p') </p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input.
   */
  def rep[T](p: => Parser[Elem, T]): Parser[Elem, Seg[T]] =
    rep1(p) | success(Seg.empty)

  /**
   * A parser generator for interleaved repetitions.
   *
   * <p> repsep(p, q)   repeatedly uses `p' interleaved with `q' to parse the input, until `p' fails.
   *  (The result is a `List' of the results of `p'.) </p>
   *
   * <p>Example: <code>repsep(term, ",")</code> parses a comma-separated list of term's,
   *          yielding a list of these terms</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @param q a `Parser' that parses the elements that separate the elements parsed by `p'
   * @return A parser that returns a list of results produced by repeatedly applying `p' (interleaved
   *         with `q') to the input.
   *         The results of `p' are collected in a list. The results of `q' are discarded.
   */
  def repsep[T](p: => Parser[Elem, T], q: => Parser[Elem, Any]): Parser[Elem, Seg[T]] =
    rep1sep(p, q) | success(Seg.empty)

  /**
   * A parser generator for non-empty repetitions.
   *
   * <p> rep1(p) repeatedly uses `p' to parse the input until `p' fails -- `p' must succeed at least
   *             once (the result is a `List' of the consecutive results of `p')</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input
   *        (and that only succeeds if `p' matches at least once).
   */
  def rep1[T](p: => Parser[Elem, T]): Parser[Elem, Seg[T]] =
    rep1(p, p)

  /**
   * A parser generator for non-empty repetitions.
   *
   * <p> rep1(f, p) first uses `f' (which must succeed) and then repeatedly uses `p' to
   *     parse the input until `p' fails
   *     (the result is a `List' of the consecutive results of `f' and `p')</p>
   *
   * @param first a `Parser' that parses the first piece of input
   * @param next a `Parser' that is to be applied successively to the rest of the input (if any)
   * @return A parser that returns a list of results produced by first applying `f' and then
   *         repeatedly `p' to the input (it only succeeds if `f' matches).
   */
  def rep1[T](first: => Parser[Elem, T], next: => Parser[Elem, T]): Parser[Elem, Seg[T]] =
    new Rep1ParserInit(first, next)

  import scala.collection.mutable.ListBuffer

  private[this] final class Rep1ParserInit[T](first: Parser[Elem, T], next: => Parser[Elem, T])
      extends Parser[Elem, Seg[T]] {
    def name = "rep1"
    def apply(seg: Seg[Elem]) =
      first(seg) match {
        case Done(t, es) =>
          val _next = next
          new Rep1ParserCont(_next, _next, Seg(t), es).apply(es)
        case Partial(p) =>
          Partial(new Rep1ParserInit(p, next))
        case fail: Fail => fail
      }

    def result(signal: Signal) =
      first.result(signal) match {
        case None =>
          None
        case Some(Right(Done(t, es))) =>
          val _next = next
          val (segt, result) = parseProbe(_next, _next, es, signal)
          result match {
            case None => Some(Right(Done(segt :+ t, NilSeg))) // messages have been filtered out
            case Some(Right((fail, rem))) => Some(Right(Done(segt :+ t, rem)))
            case Some(Left(fail)) => Some(Left(StdErrors.repOutOfSync(name, segt :+ t, fail)))
          }
        case Some(Left(fail)) =>
          Some(Left(StdErrors.repOutOfSync(name, Seg.empty, fail)))
      }
  }

  private[this] final class Rep1ParserCont[T](
      orig: Parser[Elem, T], p: Parser[Elem, T], parsed: Seg[T], trail: Seg[Elem]) extends Parser[Elem, Seg[T]] {
    def name = "rep1"
    def apply(seg: Seg[Elem]) =
      p(seg) match {
        case Done(t, es) =>
          new Rep1ParserCont(orig, orig, parsed :+ t, es).apply(es)
        case Partial(pnext) =>
          Partial(new Rep1ParserCont(orig, pnext, parsed, trail ++ seg))
        case _ =>
          Done(parsed, seg)
      }

    def result(signal: Signal) = {
      def loop(p: Parser[Elem, T], trail: Seg[Elem], seg: Seg[Elem], parsed: Seg[T]): Option[Either[Fail, Done[Seg[T], Elem]]] = {
        p.result(seg, signal) match {
          case None =>
            Some(Right(Done(parsed, NilSeg)))
          case Some(Right(Done(t, es))) =>
            if (es.isEmpty)
              Some(Right(Done(parsed :+ t, es)))
            else
              loop(orig, NilSeg, es, parsed :+ t)
          case Some(Left(fail)) =>
            Some(Right(Done(parsed, trail ++ seg)))
        }
      }
      loop(p, trail, NilSeg, parsed)
    }

  }

  /**
   * A parser generator for a specified number of repetitions.
   *
   * <p> repN(n, p)  uses `p' exactly `n' time to parse the input
   *       (the result is a `List' of the `n' consecutive results of `p')</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @param n the exact number of times `p' must succeed
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input
   *        (and that only succeeds if `p' matches exactly `n' times).
   */
  def repN[T](num: Int, p: => Parser[Elem, T]): Parser[Elem, Seg[T]] =
    if (num == 0) success(Seg.empty) else new RepNParserInit(num, p)

  private[this] final class RepNParserInit[T](num: Int, p: => Parser[Elem, T])
      extends Parser[Elem, Seg[T]] {
    def name = "repN"
    def apply(seg: Seg[Elem]) = {
      val _p = p
      _p(seg) match {
        case Done(t, es) =>
          if (num == 1)
            Done(Seg(t), es)
          else
            new RepNParserCont(num, num - 1, _p, _p, Seg(t)).apply(es)
        case Partial(next) =>
          Partial(new RepNParserCont(num, num, _p, next, Seg.empty))
        case f: Fail =>
          StdErrors.countNotReached(name, num, Seg.empty, f)
      }
    }
    def result(signal: Signal): Option[Either[Fail, Done[Seg[T], Elem]]] =
      Some(Left(StdErrors.countNotReached(name, num, Seg.empty, StdErrors.prematureSignal(p.name, NilSeg, signal))))
  }

  // Invariant remaining >= 1
  private final class RepNParserCont[T](
      onum: Int, remaining: Int, orig: Parser[Elem, T], p: Parser[Elem, T], parsed: Seg[T]) extends Parser[Elem, Seg[T]] {
    def name = "repN"
    def apply(seg: Seg[Elem]) =
      p(seg) match {
        case Done(t, es) =>
          if (remaining == 1)
            Done(parsed :+ t, es)
          else
            new RepNParserCont(onum, remaining - 1, orig, orig, parsed :+ t).apply(es)
        case Partial(next) =>
          Partial(new RepNParserCont(onum, remaining, orig, next, parsed))
        case fail: Fail =>
          StdErrors.countNotReached(name, onum, parsed, fail)
      }

    def result(signal: Signal) = {
      def loop(p: Parser[Elem, T], seg: Seg[Elem], parsed: Seg[T], remaining: Int): Option[Either[Fail, Done[Seg[T], Elem]]] = {
        p.result(seg, signal) match {
          case None =>
            Some(Left(StdErrors.countNotReached(name, onum, parsed, StdErrors.prematureSignal(p.name, seg, signal))))
          case Some(Right(Done(t, seg))) =>
            if (remaining == 1)
              Some(Right(Done(parsed :+ t, seg)))
            else if (seg.isEmpty) {
              Some(Left(StdErrors.countNotReached(name, onum, parsed, StdErrors.prematureSignal(p.name, seg, signal))))
            } else
              loop(orig, seg, parsed :+ t, remaining - 1)
          case Some(Left(fail)) =>
            Some(Left(StdErrors.countNotReached(name, onum, parsed, fail)))
        }
      }
      loop(p, NilSeg, parsed, remaining)
    }
  }

  /**
   * A parser generator for non-empty repetitions.
   *
   *  <p>rep1sep(p, q) repeatedly applies `p' interleaved with `q' to parse the input, until `p' fails.
   *                The parser `p' must succeed at least once.</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @param q a `Parser' that parses the elements that separate the elements parsed by `p'
   *          (interleaved with `q')
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input
   *         (and that only succeeds if `p' matches at least once).
   *         The results of `p' are collected in a list. The results of `q' are discarded.
   */
  def rep1sep[T](p: => Parser[Elem, T], q: => Parser[Elem, Any]): Parser[Elem, Seg[T]] =
    p ~ rep(q ~> p) ^^ { case x ~ y => x +: y }

  /**
   * A parser generator for optional sub-phrases.
   *
   *  <p>opt(p) is a parser that returns `Some(x)' if `p' returns `x' and `None' if `p' fails</p>
   *
   * @param p A `Parser' that is tried on the input
   * @return a `Parser' that always succeeds: either with the result provided by `p' or
   *         with the empty result
   */
  def opt[T](p: => Parser[Elem, T]): Parser[Elem, Option[T]] =
    p ^^ (x => Some(x)) | success(None)

}
