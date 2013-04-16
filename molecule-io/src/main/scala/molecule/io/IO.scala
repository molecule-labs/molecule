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
package io

import stream.{ IChan, OChan }

/**
 * The Molecule IO Monad
 *
 * @param ask function called to "execute" or "ask for" the result of the IO action.
 */
final class IO[+A](
    private[io] final val ask: (impl.UThreadContext, A => Unit) => Unit) {

  /**
   * Bind the result of this action to the argument of a
   * function resulting in the next IO action to execute.
   * In other words, the new action created by `bind` passes control to the action
   * created as a reaction to the result of the current action.
   *
   * @param react the function used to create the next action.
   * @tparam B the type of the next action.
   * @return the next action.
   */
  def bind[B](react: A => IO[B]): IO[B] =
    new IO[B]((t, k) =>
      ask(t, a => react(a).ask(t, k))
    )

  /**
   * The monadic `bind` operator.
   *
   * @param react the function used to create the next action.
   * @tparam B the type of the next action.
   * @return the next action.
   */
  @inline
  final def >>\[B](react: A => IO[B]): IO[B] = bind(react)

  /**
   * An operator that acts like `bind` but returns the result
   * of the previous action.
   *
   * @param react the function used to create the next action.
   * @tparam B the type of the next action.
   * @return the result of the previous action.
   */
  def >>&\[B](react: A => IO[B]): IO[A] = for {
    a <- this
    _ <- react(a)
  } yield a

  /**
   * Sequence an action after this one and return the result of that action.
   *
   * @param next the next action.
   * @tparam B the type of the next action.
   * @return the result of the next action.
   */
  @inline
  final def andThen[B](next: => IO[B]): IO[B] = bind(_ => next)

  /**
   * Operator equivalent to `andThen`.
   *
   * @param next the next action.
   * @tparam B the type of the next action.
   * @return the result of the next action.
   */
  @inline
  final def >>[B](next: => IO[B]): IO[B] = bind(_ => next)

  /**
   * Sequence to unrelated actions and return the result of the first action.
   *
   * @param next the next action.
   * @tparam B the type of the next action.
   * @return the result of the first action.
   */
  def >>&[B](next: => IO[B]): IO[A] = for {
    a <- this
    _ <- next
  } yield a

  /**
   * Sequence an action after this one and return a pair containing the results of
   * both actions.
   *
   * @param next the next action.
   * @tparam B the type of the next action.
   * @return the pair with both results (implicit conversions allow to match ~ as a tuple, otherwise
   * use flatten x methods).
   */
  def seq[B](next: IO[B]): IO[A ~ B] =
    for {
      a <- this
      b <- next
    } yield new ~(a, b)

  /**
   * Sequence an action after this one and return a pair containing the results of
   * both actions.
   *
   * @param next the next action.
   * @tparam B the type of the next action.
   * @return the pair with both results (implicit conversions allow to match ~ as a tuple, otherwise
   * use flatten x methods).
   */
  @inline
  final def ~[B](next: IO[B]): IO[A ~ B] = this seq next

  /**
   * Sequence an action after this one and return the result of that action.
   *
   * @param next the next action.
   * @tparam B the type of the next action.
   * @return the result of the next action.
   */
  @inline
  final def ~>[B](next: => IO[B]): IO[B] = bind(_ => next)

  /**
   * Sequence an action after this one and return the result of this action.
   *
   * @param next the next action.
   * @tparam B the type of the next action.
   * @return the result of this action.
   */
  final def <~[B](next: => IO[B]): IO[A] =
    for {
      a <- this
      _ <- next
    } yield a

  /**
   * An operator that acts like `bind` but returns the result
   * of the previous action.
   *
   * @param react the function used to create the next action.
   * @tparam B the type of the next action.
   * @return the result of the previous action.
   */
  final def <~\[B](react: A => IO[B]): IO[A] = for {
    a <- this
    _ <- react(a)
  } yield a

  /** Same as `bind` */
  @inline
  final def ~>\[B](f: A => IO[B]): IO[B] = bind(f)

  /** Same as `bind` */
  @inline
  final def flatMap[B](f: A => IO[B]): IO[B] = bind(f)

  /**
   * Apply a function to the result of this action.
   *
   * @param f the function to apply.
   * @tparam B the type of the resulting action.
   * @return the transformed action.
   */
  def map[B](f: A => B): IO[B] =
    new IO[B]({ (t, k) =>
      ask(t, a => k(f(a)))
    })

  /**
   * Apply a function to the result of this action.
   *
   * @param f the function to apply.
   * @tparam B the type of the resulting action.
   * @return the transformed action.
   */
  @inline
  final def $[B](f: A => B): IO[B] = map(f)

  /**
   * Execute an action or catch a user-level exception raised by this action.
   * If a user-level exception is thrown, any resource dynamically acquired by
   * this action is shutdown. Then, if its signal matches a signal for which
   * the partial function is defined the action results in the action defined
   * by the partial function handler. Otherwise, the signal is propageted
   * further in the parent's context.
   *
   * @param f the partial function invoked if a user-level exception occurs.
   * @return a managed action.
   */
  def orCatch[B >: A](f: PartialFunction[Signal, IO[B]]): IO[B] = new IO[B]({ (t, k) =>
    t.askOrHandle(ask, k, { signal =>
      if (f.isDefinedAt(signal))
        f(signal).ask(t, k)
      else
        t.raise(signal)
    })
  })

  /** Function required by Scala for pattern matching */
  def filter(p: A => Boolean): IO[A] = new IO[A]({ (t, k) =>
    ask(t, a => if (p(a)) k(a) else sys.error("filter predicate not matched"))
  })

  import utils._

  /**
   * Interleave the execution of this action with the execution of another action.
   *
   * @param other the other action
   * @return the pair of both results (implicit conversions allow to match ~ as a tuple, otherwise
   * use flatten x methods).
   */
  def par[B](other: IO[B])(implicit ma: Message[A], mb: Message[B]): IO[A ~ B] = this |~| other

  /**
   * Operator for `par`.
   *
   * @param action the other action
   * @return an action that returns the results of both actions in a pair.
   */
  def |~|[B](other: IO[B])(implicit ma: Message[A], mb: Message[B]): IO[A ~ B] = new IO[A ~ B]({ (t, k) =>
    var first: Either[Signal, Any] = null

    val ka: A => Unit = { a =>
      if (first == null)
        first = Right(a)
      else first match {
        case Right(b) =>
          first = Left(EOS)
          k(new ~(a, b.asInstanceOf[B]))
        case Left(signal) =>
          // Signal already raised
          ma.poison(a, signal)
      }
    }

    val kb: B => Unit = { b =>
      if (first == null)
        first = Right(b)
      else first match {
        case Right(a) =>
          first = Left(EOS)
          k(new ~(a.asInstanceOf[A], b))
        case Left(signal) =>
          // Signal already raised
          mb.poison(b, signal)
      }
    }

    import impl.Context

    // Both threads will share the same context such that all of them fail 
    // if there is an uncaught exception (crash loudly)
    // Note that even if the context is poisoned by action a, kb could still
    // be executed just after since the continuation may have been submitted 
    // in the meantime. That is why we need to track termination in first.
    val ca = new Context()
    val cb = new Context()

    val error: ((Any, Signal) => Unit) => (Signal => Unit) => Signal => Unit = { poison =>
      raise => signal =>
        ca.shutdown(signal)
        cb.shutdown(signal)
        if (first == null) {
          first = Left(EOS)
          raise(signal)
        } else first match {
          case Right(any) =>
            first = Left(EOS)
            poison(any, signal)
            raise(signal)
          case Left(signal) =>
          // Signal already raised
        }
    }

    val ea = error((any, signal) => mb.poison(any.asInstanceOf[B], signal))
    val eb = error((any, signal) => ma.poison(any.asInstanceOf[A], signal))

    // We yield the stack for (rare) situations where this is called recursively 
    // (If you can, use parl instead)
    t.submit {
      t.askOrHandle(ca, this.ask, ka, ea(t.raise), ea(t.fatal))
      t.askOrHandle(cb, other.ask, kb, eb(t.raise), eb(t.fatal))
    }
  })

  /**
   * Same as `par` but discard the result.
   *
   * @param action the other action.
   * @return an action that returns Unit after both actions have terminated.
   */
  def |*|[B](action: IO[B])(implicit ma: Message[A], mb: Message[B]): IO[Unit] = { this |~| action } >> IO()

}

object IO {

  import molecule.{ process => proc }

  /**
   * Create an action that returns the result of a thunk of code.
   *
   * @param a a call-by-name argument.
   * @return an action that returns the result of the call-by-name argument.
   */
  @inline final def apply[A](a: => A): IO[A] =
    new IO((t, k) => k(a))

  private[this] def strict[A](a: A): IO[A] =
    new IO((t, k) => k(a))

  private[this] final val unit = strict(())

  /**
   * The unit action.
   *
   * @return an action that returns unit.
   */
  final def apply() = unit

  import channel.{ ROChan, RIChan }

  /**
   * Launch a process
   *
   * @param process a process that returns a result of type R.
   * @return the action that returns the result channel of the process.
   */
  def launch[R: Message](process: proc.Process[R]): IO[RIChan[R]] =
    new IO[RIChan[R]]({ (t, k) =>
      k(t.platform.launch(process))
    })

  /**
   * Launch a process
   *
   * @param process a process that returns a result of type R.
   * @param rc the return channel
   * @return unit
   */
  def launch[R: Message](process: proc.Process[R], rc: ROChan[R]): IO[Unit] =
    new IO[Unit]({ (t, k) =>
      t.submit(k(t.platform.launch(process, rc)))
    })

  /**
   * Launch a process
   *
   * @param process an process action that returns a result of type R.
   * @return the result channel
   */
  def launch[R: Message](p: IO[proc.Process[R]]): IO[RIChan[R]] =
    p >>\ { launch(_) }

  /**
   * Launch a process
   *
   * @param process an process action that returns a result of type R.
   * @return the result channel
   */
  def launch[R: Message](ff: IO[proc.Process[R]], rc: ROChan[R]): IO[Unit] =
    ff >>\ { f => launch(f, rc) }

  /**
   * Use an input channel within in the context of the process.
   * The returned process-level input is attached as a resource
   * to the process context, and will get poisoned automatically
   * when the process terminates, unless the input is explicitly
   * released before (see API of Input[A]).
   *
   * @param id an identifier
   * @param ichan a first-class input channel.
   * @return an action that returns the process-level channel.
   */
  private[io] def use[A: Message](id: Int, ichan: IChan[A]): IO[Input[A]] =
    new IO[Input[A]]((t, k) => k(Input(t, id, ichan)))

  /**
   * Use an output channel within in the context of the process.
   * The returned process-level output is attached as a resource
   * to the process context, and will get closed automatically
   * when the process terminates, unless the input is explicitly
   * released before (see API of Output[A]).
   *
   * @param id an identifier
   * @param ochan a first-class output channel.
   * @return an action that returns the process-level channel.
   */
  private[io] def use[A: Message](id: Int, ochan: OChan[A]): IO[Output[A]] =
    new IO[Output[A]]((t, k) => k(Output(t, id, ochan)))

}