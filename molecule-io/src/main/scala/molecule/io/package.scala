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

import stream.{ IChan, OChan }

/**
 * This package offers monadic process types and various combinators
 * for monadic actions (see value members below).
 *
 */
package object io {

  /**
   * Raise a signal as a user-level exception.
   * @param signal the signal to raise.
   * @return nothing.
   */
  def raise(signal: Signal): IO[Nothing] =
    new IO[Nothing]((t, _) => t.raise(signal))

  import scala.collection.generic.CanBuildFrom
  import channel.{ RIChan, RChan, ROChan }

  /**
   * Fork an interleaved action.
   *
   * @param a the action.
   * @return A result channel carrying the result of the action.
   */
  def spawn[A: Message](a: IO[A]): IO[RIChan[A]] = new IO[RIChan[A]]({ (t, k) =>
    val (ri, ro) = RChan.mk[A]()
    t.askOrHandle(t.activity.rootCtx, new impl.Context(), a.ask, ro.success_!, ro.failure_!, t.fatal)
    k(ri)
  })

  import impl.Context

  /**
   * Execute a list of interleaved actions and return their results as a list.
   * If one action fails all the other actions are terminated and a single signal
   * corresponding to the exception is raised.
   *
   * @param ios a list of actions.
   * @return the list of results. Results occur in the same as the order as the actions that produced them.
   */
  def parl[A, That](ios: Iterable[IO[A]])(implicit ma: Message[A], bf: CanBuildFrom[Nothing, A, That]): IO[That] =
    if (ios.isEmpty) {
      IO(bf().result())
    } else new IO[That]({ (t, k) =>
      import scala.collection.immutable.SortedMap

      val tot = ios.size
      var cnt = 0
      var status: Either[Signal, Array[Either[Context, A]]] = Right(Array.fill(tot)(Left(new Context())))

      def checkDone(last: Int, results: Array[Either[Context, A]]) {
        if (cnt == tot) {
          val builder = bf()
          var i = 0
          while (i < tot) {
            builder += results(i).right.get
            results(i) = null
            i += 1
          }
          status = Left(Signal("parl over"))
          k(builder.result)
        }
      }

      val ka: Int => A => Unit = { i =>
        a =>
          status match {
            case Right(results) =>
              if (results(i).isRight) {
                System.err.println("collision!!!" + i + " -> " + a)
                System.err.println(new Exception().getStackTraceString)
                System.exit(0)
              }
              results(i) = Right(a)
              cnt += 1
              checkDone(i, results)
            case Left(signal) => // fatal
              ma.poison(a, signal)
          }
      }

      val error: (Signal => Unit) => Signal => Unit = { raise =>
        signal =>
          status match {
            case Right(results) =>
              results.foreach {
                case Right(a) => ma.poison(a, signal)
                case Left(ctx) => ctx.shutdown(signal)
              }
              status = Left(signal)
              raise(signal)
            case Left(signal) =>
            // We already came here
          }
      }

      val ctxs = status.right.get
      ios.zipWithIndex.foreach {
        case (action, i) =>
          t.askOrHandle(ctxs(i).left.get, action.ask, ka(i), error(t.raise), error(t.fatal))
      }
    })

  /**
   * Execute a list of interleaved actions and return when they have all terminated. Contrarily
   * to `parl`, this action discards the intermediate results.
   * @param ios the list of actions.
   * @return a unit action that returns after all the actions have terminated.
   */
  def parl_[A: Message](ios: Iterable[IO[A]]): IO[Unit] =
    parl(ios) >> IO()

  /**
   * Execute a list of interleaved actions and return their results as a list.
   *
   * @param ios a list of actions.
   * @return the list of results. Results occur in the same as the order as the actions that produced them.
   */
  def parle[A, That](ios: Iterable[IO[A]])(implicit ma: Message[A], bf: CanBuildFrom[Nothing, Either[Signal, A], That]): IO[That] =
    if (ios.isEmpty) {
      IO(bf().result())
    } else new IO[That]({ (t, k) =>

      val tot = ios.size
      var cnt = 0
      val results: Array[Either[Context, Either[Signal, A]]] = Array.fill(tot)(Left(new Context()))

      var fatalSignal: Signal = null

      def checkDone() {
        if (cnt == tot) {
          val builder = bf()
          var i = 0
          while (i < tot) {
            builder += results(i).right.get
            results(i) = null
            i += 1
          }
          k(builder.result)
        }
      }

      val ka: Int => A => Unit = { i =>
        a =>
          if (fatalSignal == null) {
            results(i) = Right(Right(a))
            checkDone()
          } else
            ma.poison(a, fatalSignal)
      }

      val er: Int => Signal => Unit = { i =>
        signal =>
          if (fatalSignal == null) {
            results(i) = Right(Left(signal))
            checkDone()
          }
      }

      val ef: Signal => Unit = { signal =>
        if (fatalSignal == null) {
          fatalSignal = signal
          var i = 0
          while (i < tot) {
            results(i) match {
              case Left(ctx) =>
                ctx.shutdown(signal)
              case Right(Right(a)) =>
                ma.poison(a, signal)
              case _ =>
            }
            results(i) = null
            i += 1
          }
          t.fatal(signal)
        }
      }

      ios.zipWithIndex.foreach {
        case (action, i) =>
          t.askOrHandle(results(i).left.get, action.ask, ka(i), er(i), ef)
      }
    })

  import java.util.concurrent.TimeUnit

  /**
   * Read a value on an input within a specified timeout.
   *
   * @param in   the input on which to read a value from
   * @param time the timeout
   * @param unit the time unit of the delay parameter.
   * @return Some value if it can be read within the specified timeout, else none.
   */
  def readWithin[A](in: Input[A], time: Int, unit: TimeUnit): IO[Option[A]] = managed {
    val timer = channel.Timer.timeout(time, unit)
    use(timer) >>\ { t =>
      (t <+%> in).read().map(_.fold(Function.const(None), Some(_)))
    }
  }

  /**
   * Close all the resources acquired by the action passed as argument
   * once it terminates.
   * @param io the action to manage.
   * @param the managed action.
   */
  def managed[A](io: IO[A]): IO[A] = io.orCatch { case s => raise(s) }

  /**
   * Close all the resources acquired in the current context.
   * Efficient loops with resource control can be written in terms
   * of manage and resetCtx e.g.
   * def forever(io:IO[Unit]):IO[Unit] = {
   *   def loop:IO[Unit] =
   *     io >> resetCtx() >> managedLoop
   *   manage(loop)
   * }
   *
   * @return the unit action
   */
  def resetCtx(): IO[Unit] = new IO[Unit]({ (t, k) =>
    t.context.shutdown(EOS)
    k()
  })

  import molecule.{ process => proc }

  /**
   * Launch a process
   *
   * @param process a a process that returns a result of type R.
   * @return the result channel
   */
  def launch[R: Message](process: proc.Process[R]): IO[RIChan[R]] =
    IO.launch(process)

  /**
   * Launch a process
   *
   * @param process a a process that returns a result of type R.
   * @param rc the return channel
   * @return unit
   */
  def launch[R: Message](process: proc.Process[R], rc: ROChan[R]): IO[Unit] =
    IO.launch(process, rc)

  /**
   * Launch a process
   *
   * @param process an action that returns a process.
   * @return the result channel
   */
  def launch[R: Message](process: IO[proc.Process[R]]): IO[RIChan[R]] =
    IO.launch(process)

  /**
   * Launch a process
   *
   * @param process an action that returns a process.
   * @return the result channel
   */
  def launch[R: Message](process: IO[proc.Process[R]], rc: ROChan[R]): IO[Unit] =
    IO.launch(process, rc)

  /**
   * Use an input channel within in the context of the process.
   * The returned process-level input is attached as a resource
   * to the process context, and will get poisoned automatically
   * when the process terminates, unless the input is explicitly
   * released before (see API of Input[A]).
   *
   * @param ichan a first-class input channel.
   * @return an action that returns the process-level channel.
   */
  def use[A: Message](ichan: IChan[A]): IO[Input[A]] =
    IO.use(System.identityHashCode(ichan), ichan)

  /**
   * Use an output channel within in the context of the process.
   * The returned process-level output is attached as a resource
   * to the process context, and will get closed automatically
   * when the process terminates, unless the input is explicitly
   * released before (see API of Output[A]).
   *
   * @param ochan a first-class output channel.
   * @return an action that returns the process-level channel.
   */
  def use[A >: Nothing: Message](ochan: OChan[A]): IO[Output[A]] =
    IO.use(System.identityHashCode(ochan), ochan)

  /**
   * Attach a resource to the resource control of this process such
   * that it gets automatically closed once the process exits.
   *
   * @param resource a resource.
   * @return an action that returns the attached resource.
   */
  def attach[T <: Resource](resource: T): IO[T] = new IO[T]({ (t, k) =>
    t.context.add(resource)
    k(resource)
  })

  /**
   * Detach a resource from the resource control of this process.
   *
   * @param resource a resource.
   * @return an action that returns the attached resource.
   */
  def detach[T <: Resource](resource: T): IO[T] = new IO[T]({ (t, k) =>
    t.context.remove(resource)
    k(resource)
  })

  /**
   * Wait for the termination of a process and return its result.
   *
   * @param rchan the result channel.
   */
  def join[R: Message](rchan: RIChan[R]): IO[R] =
    use(rchan) >>\ { _.read() }

  //def join[R:Message](ior:IO[RIChan[R]]):IO[R] =
  //  ior >>\ {join(_)}

  /**
   * Another class for 'enriching' a result channel returned within an IO.
   */
  /**
   * Enrich a result channel with additional methods.
   */
  class RichIORIChan[A: Message](rc: RIChan[A]) {
    /**
     * Execute an action in parallel (interleaved parallelism) once
     * the result is available. This is useful to cleanup stateful
     * resources associated with a process.
     *
     */
    def onResult(handle: Either[Signal, A] => IO[Unit]): IO[Unit] =
      new IO((t, k) => {
        rc.read {
          case (seg, IChan(signal)) =>
            if (seg.isEmpty)
              t.askOrHandle(handle(Left(signal)).ask, utils.NOOP, t.fatal, t.fatal)
            else
              t.askOrHandle(handle(Right(seg.head)).ask, utils.NOOP, t.fatal, t.fatal)
        }
        k()
      })

    /**
     * Returns the result received on a result channel.
     */
    def get(): IO[A] = join(rc)
  }

  /**
   * Enrich a result channel returned within an IO with additional methods.
   */
  class RichIOIORIChan[A: Message](iorc: IO[RIChan[A]]) {
    /**
     * Execute an action in parallel (interleaved parallelism) once
     * the result is available. This is useful to cleanup stateful
     * resources associated with a process.
     *
     */
    def onResult(handle: Either[Signal, A] => IO[Unit]): IO[Unit] =
      iorc >>\ { rc => new RichIORIChan(rc).onResult(handle) }

    /**
     * Returns the result received on a result channel.
     */
    def get(): IO[A] = iorc >>\ (join(_))

  }

  /** Enrich a result channel. */
  final implicit def enrichIORIChan[A: Message](rc: RIChan[A]): RichIORIChan[A] = new RichIORIChan(rc)

  /** Enrich a result channel. */
  final implicit def enrichIOIORIChan[A: Message](rc: IO[RIChan[A]]): RichIOIORIChan[A] = new RichIOIORIChan(rc)

  /**
   * Enrich a return channel with additional methods.
   */
  class RichIOROChan[A](ro: ROChan[A]) {
    def success(a: A): IO[Unit] = { ro.success_!(a); IO() }
    def failure(signal: Signal): IO[Unit] = { ro.failure_!(signal); ioLogErr(signal.toString) }
  }

  /** Enrich a return channel. */
  final implicit def enrichIOROChan[A](rc: ROChan[A]): RichIOROChan[A] = new RichIOROChan(rc)

  import channel.NativeProducer

  /**
   * Enrich a native producer channel.
   */
  class RichIOProducer[A](pc: NativeProducer[A]) {

    def write(a: A): IO[Unit] = try { pc.send(a); IO() } catch {
      case s: Signal => raise(s)
      case t => raise(Signal(t))
    }
  }

  /** Enrich a producer channel. */
  final implicit def enrichIONativeProducer[A](pc: NativeProducer[A]): RichIOProducer[A] = new RichIOProducer(pc)

  import java.util.concurrent.TimeUnit

  /**
   * Suspend this process for a certain duration (non-blocking).
   *
   * @param timeout the duration of sleep.
   * @param unit the time unit.
   * @return a unit action that returns after the sleep period.
   */
  def sleep(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): IO[Unit] =
    use(channel.Timer.timeout(timeout, unit)) >>\ { _.read() } >> IO()

  /**
   * Call with current continuation.
   *
   * @param call a function that takes the current continuation as argument.
   * @return an action that returns the parameter passed to `call`.
   */
  def callcc[A](call: (A => IO[Nothing]) => IO[Nothing]): IO[A] =
    new IO[A]({ (t, k) =>
      val continue: A => IO[Nothing] = (a =>
        new IO[Nothing]({ (_, _) =>
          k(a)
        })
      )
      call(continue).ask(t, utils.NOOP)
    })

  /**
   * Execute a an action if the guard is true.
   *
   * @param b the guard.
   * @param io the action to execute if b is true.
   * @return a unit action.
   */
  def guard(b: Boolean)(io: => IO[Unit]): IO[Unit] =
    if (b) io else IO()

  /**
   * Execute a an action unless the guard is true.
   *
   * @param b the guard.
   * @param io the action to execute if b is false.
   * @return a unit action.
   */
  def unless(b: Boolean)(io: => IO[Unit]): IO[Unit] =
    if (!b) io else IO()

  /**
   * Return a Random.
   *
   * @return an action that returns a Random.
   */
  def getRandom: IO[java.util.Random] = IO(utils.random())

  /**
   * Action that gets the current platform.
   */
  val getPlatform: IO[platform.Platform] =
    new IO((t, k) => k(t.platform))

  /**
   * Action that gets the current platform.
   */
  def getPlatformAs[P <: platform.Platform]: IO[P] =
    new IO((t, k) => k(t.platform.asInstanceOf[P]))

  /**
   * Action that logs a message.
   *
   * @param msg the message to log.
   * @return an action that logs a message.
   */
  def ioLog(msg: String): IO[Unit] =
    new IO((t, k) => { t.platform.log(t.activity + ":" + msg); k() })

  /**
   * Action that logs a warning message.
   *
   * @param msg the message to log.
   * @return an action that logs a warning message.
   */
  def ioLogWarn(msg: String): IO[Unit] =
    new IO((t, k) => { t.platform.logWarn(t.activity + ":" + msg); k() })

  /**
   * Action that logs an error message.
   *
   * @param msg the message to log.
   * @return an action that logs a error message.
   */
  def ioLogErr(msg: String): IO[Unit] =
    new IO((t, k) => { t.platform.logErr(t.activity + ":" + msg); k() })

  /**
   * Action that logs a debug message.
   *
   * @param msg the message to log.
   * @return an action that logs a debug message.
   */
  def ioLogDbg(msg: String): IO[Unit] =
    new IO((t, k) => { t.platform.logDbg(t.activity + ":" + msg); k() })

  /**
   * Enrich an output of strings.
   */
  class RichIOStringOutput(out: Output[String]) {
    def writeLn(s: String) = out.write(s + utils.lineSep)
  }

  /**
   * Implicit conversion to 'enrich' an output of strings.
   */
  implicit def enrichIOStringOutput(out: Output[String]): RichIOStringOutput =
    new RichIOStringOutput(out)

  /**
   * Enrich a Platftorm with additional methods.
   *
   */
  class RichIOPlatform(p: platform.Platform) {
    def execIO[A: Message](io: IO[A]): RIChan[A] = {
      p.launch(new impl.Process[A] {
        def ptype = ProcessType.Anonymous
        def behavior: IO[A] = io
      })
    }

    def execIO_![A: Message](io: IO[A]): A =
      execIO(io).get_!()
  }

  implicit def enrichIOPlatform(p: platform.Platform): RichIOPlatform =
    new RichIOPlatform(p)
}