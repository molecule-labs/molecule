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

import molecule.{ process => proc }
import channel.ROChan
import stream.{ IChan, OChan }

/**
 * The base trait for all process types.
 *
 * @tparam R the result type returned by its process instances.
 *
 */
trait ProcessType[R] {

  /**
   * Shutdown the process.
   *
   * @param result the termination result of the process.
   * @return nothing
   */
  protected def shutdown(result: R): IO[Nothing] = new IO[Nothing]({ (t, _) =>
    t.fatal(new impl.Shutdown(result))
  })

  /**
   * Halt this process and hand over the execution to another process.
   *
   * @param process an action that returns the function that creates the process
   *          to which to hand over the execution using the return channel
   *          of this process.
   * @return nothing
   */
  def handover(process: IO[proc.Process[R]]): IO[Nothing] =
    process >>\ { handover(_) }

  /**
   * Halt this process and hand over the execution to another process.
   *
   * @param f the function that creates the process to which to hand over
   *          the execution using the return channel of this process.
   * @return nothing
   */
  def handover(process: proc.Process[R]): IO[Nothing] =
    new IO[Nothing]({ (t, _) =>
      val rochan = t.activity.rchan.asInstanceOf[ROChan[R]]
      process.start(t.uthread, rochan)
      t.fatal(impl.Handover)
    })

}

/**
 * Process type with no input or output.
 */
abstract class ProcessType0x0[R] extends proc.ProcessType0x0[R] with ProcessType[R] {

  def apply() = new impl.Process0x0(this)

  def main(): IO[R]
}

/**
 * Process type with one input channel of type A
 */
abstract class ProcessType1x0[A: Message, R] extends proc.ProcessType1x0[A, R] with ProcessType[R] {

  def apply(input: Input[A]): IO[proc.Process1x0[A, R]] =
    input.release $ { apply(_) }

  final def apply(ichan: IChan[A]) =
    new impl.Process1x0(this, ichan)

  def main(input: Input[A]): IO[R]
}

/**
 * Process type with one output channel of type A
 */
abstract class ProcessType0x1[A: Message, R] extends proc.ProcessType0x1[A, R] with ProcessType[R] {

  def apply(output: Output[A]): IO[proc.Process0x1[A, R]] =
    output.release $ { apply(_) }

  def apply(ochan: OChan[A]) =
    new impl.Process0x1(this, ochan)

  def main(output: Output[A]): IO[R]
}

/**
 * Process type with one input and one output.
 */
abstract class ProcessType1x1[A: Message, B: Message, R] extends proc.ProcessType1x1[A, B, R] with ProcessType[R] {

  def apply(input: Input[A], output: Output[B]): IO[proc.Process1x1[A, B, R]] =
    input.release() ~ output.release() $
      flatten2(apply(_, _))

  def apply(ichan: IChan[A], ochan: OChan[B]): impl.Process1x1[A, B, R] =
    new impl.Process1x1(this, ichan, ochan)

  def main(input: Input[A], output: Output[B]): IO[R]
}

abstract class ProcessType2x0[A: Message, B: Message, R] extends proc.ProcessType2x0[A, B, R] with ProcessType[R] {

  def apply(input1: Input[A], input2: Input[B]): IO[proc.Process2x0[A, B, R]] =
    input1.release() ~ input2.release() $
      flatten2(apply(_, _))

  final def apply(ichan1: IChan[A], ichan2: IChan[B]) =
    new impl.Process2x0(this, ichan1, ichan2)

  def main(input1: Input[A], input2: Input[B]): IO[R]
}

abstract class ProcessType2x1[A: Message, B: Message, E: Message, R] extends proc.ProcessType2x1[A, B, E, R] with ProcessType[R] {

  def apply(input1: Input[A], input2: Input[B], output: Output[E]): IO[proc.Process2x1[A, B, E, R]] =
    input1.release() ~ input2.release() ~ output.release() $
      flatten3(apply(_, _, _))

  final def apply(ichan1: IChan[A], ichan2: IChan[B], ochan: OChan[E]) =
    new impl.Process2x1(this, ichan1, ichan2, ochan)

  def main(input1: Input[A], input2: Input[B], output: Output[E]): IO[R]
}

abstract class ProcessType1x2[A: Message, E: Message, F: Message, R] extends proc.ProcessType1x2[A, E, F, R] with ProcessType[R] {

  def apply(input: Input[A], output1: Output[E], output2: Output[F]): IO[proc.Process1x2[A, E, F, R]] =
    input.release() ~ output1.release() ~ output2.release() $
      flatten3(apply(_, _, _))

  def apply(ichan: IChan[A], ochan1: OChan[E], ochan2: OChan[F]): impl.Process1x2[A, E, F, R] =
    new impl.Process1x2(this, ichan, ochan1, ochan2)

  def main(input: Input[A], output1: Output[E], output2: Output[F]): IO[R]
}

abstract class ProcessType2x2[A: Message, B: Message, E: Message, F: Message, R] extends proc.ProcessType2x2[A, B, E, F, R] with ProcessType[R] {

  def apply(input1: Input[A], input2: Input[B], output1: Output[E], output2: Output[F]): IO[proc.Process2x2[A, B, E, F, R]] =
    input1.release() ~ input2.release() ~ output1.release() ~ output2.release() $
      flatten4(apply(_, _, _, _))

  final def apply(ichan1: IChan[A], ichan2: IChan[B], ochan1: OChan[E], ochan2: OChan[F]) =
    new impl.Process2x2(this, ichan1, ichan2, ochan1, ochan2)

  def main(input1: Input[A], input2: Input[B], output1: Output[E], output2: Output[F]): IO[R]
}

abstract class ProcessType3x0[A: Message, B: Message, C: Message, R] extends proc.ProcessType3x0[A, B, C, R] with ProcessType[R] {

  def apply(input1: Input[A], input2: Input[B], input3: Input[C]): IO[proc.Process3x0[A, B, C, R]] =
    input1.release() ~ input2.release() ~ input3.release() $
      flatten3(apply(_, _, _))

  final def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C]) =
    new impl.Process3x0(this, ichan1, ichan2, ichan3)

  def main(input1: Input[A], input2: Input[B], input3: Input[C]): IO[R]
}

abstract class ProcessType3x1[A: Message, B: Message, C: Message, E: Message, R] extends proc.ProcessType3x1[A, B, C, E, R] with ProcessType[R] {

  def apply(input1: Input[A], input2: Input[B], input3: Input[C], output1: Output[E]): IO[proc.Process3x1[A, B, C, E, R]] =
    input1.release() ~ input2.release() ~ input3.release() ~ output1.release() $
      flatten4(apply(_, _, _, _))

  final def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C], ochan1: OChan[E]) =
    new impl.Process3x1(this, ichan1, ichan2, ichan3, ochan1)

  def main(input1: Input[A], input2: Input[B], input3: Input[C], output1: Output[E]): IO[R]
}

object ProcessType {

  object Anonymous extends proc.ProcessType {
    override def name = "Anonymous (execIO)"
  }

  /**
   * Factory methods for anonymous class components
   *
   * It is extremely important to reevaluate the IO block
   * each time a new instance is created , that is why we
   * use non strict eval
   */
  def apply[R](typeName: String, io: => IO[R]): ProcessType0x0[R] =
    new ProcessType0x0[R] {
      override def name = typeName

      def main() = io
    }

  def apply[A: Message, R](typeName: String, f: Input[A] => IO[R]): ProcessType1x0[A, R] =
    new ProcessType1x0[A, R] {
      override def name = typeName

      def main(i1: Input[A]) = f(i1)
    }

  def apply[A: Message, R](typeName: String, f: Output[A] => IO[R]): ProcessType0x1[A, R] =
    new ProcessType0x1[A, R] {
      override def name = typeName

      def main(o1: Output[A]) = f(o1)
    }

  def apply[A: Message, B: Message, R](typeName: String, f: (Input[A], Output[B]) => IO[R]): ProcessType1x1[A, B, R] =
    new ProcessType1x1[A, B, R] {
      override def name = typeName

      def main(i1: Input[A], o1: Output[B]) = f(i1, o1)
    }

  def apply[A: Message, B: Message, R](typeName: String, f: (Input[A], Input[B]) => IO[R]): ProcessType2x0[A, B, R] =
    new ProcessType2x0[A, B, R] {
      override def name = typeName

      def main(i1: Input[A], i2: Input[B]) = f(i1, i2)
    }

  //  def apply[A:Message, B:Message](typeName:String, f:(Output[A], Output[B]) => IO[Any]):ProcessType0x2[A, B] = 
  //    new ProcessType0x2[A, B] {
  //      override def name = typeName
  //
  //      def main(o1:Output[A], o2:Output[B]) = f(o1, o2)
  //    }

  def apply[A: Message, B: Message, C: Message, R](typeName: String,
    f: (Input[A], Input[B], Output[C]) => IO[R]): ProcessType2x1[A, B, C, R] =
    new ProcessType2x1[A, B, C, R] {
      override def name = typeName

      def main(i1: Input[A], i2: Input[B], o1: Output[C]) = f(i1, i2, o1)
    }

  //  def apply[A:Message, B:Message, C:Message](typeName:String, 
  //    f:(Input[A], Output[B], Output[C]) => IO[Any]
  //  ):ProcessType1x2[A, B, C] = 
  //    new ProcessType1x2[A, B, C] {
  //      override def name = typeName
  //
  //      def main(i1:Input[A], o1:Output[B], o2:Output[C]) = f(i1, o1, o2)
  //    }

  def apply[A: Message, B: Message, C: Message, D: Message, R](typeName: String,
    f: (Input[A], Input[B], Output[C], Output[D]) => IO[R]): ProcessType2x2[A, B, C, D, R] =
    new ProcessType2x2[A, B, C, D, R] {
      override def name = typeName

      def main(i1: Input[A], i2: Input[B], o1: Output[C], o2: Output[D]) = f(i1, i2, o1, o2)
    }

  def apply[A: Message, B: Message, C: Message, R](typeName: String,
    f: (Input[A], Input[B], Input[C]) => IO[R]): ProcessType3x0[A, B, C, R] =
    new ProcessType3x0[A, B, C, R] {
      override def name = typeName

      def main(i1: Input[A], i2: Input[B], i3: Input[C]) = f(i1, i2, i3)
    }

  //  def apply[A:Message, B:Message, C:Message](typeName:String, impure:Boolean = false, 
  //    f:(Output[A], Output[B], Output[C]) => IO[Any]
  //  ):ProcessType0x3[A, B, C] = 
  //    new ProcessType0x3[A, B, C] {
  //      override def name = typeName
  //
  //      def main(o1:Output[A], o2:Output[B], o3:Output[C]) = f(o1, o2, o3)
  //    }

}