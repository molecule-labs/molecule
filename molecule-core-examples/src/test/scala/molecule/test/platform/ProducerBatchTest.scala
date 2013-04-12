package molecule.test.platform

import molecule._
import channel.{NativeProducer, RChan}

object ProducerBatchTest {

   def main(args: Array[String]): Unit = {
     val (i, mkP) = NativeProducer.mkManyToOne[Int](0)
     
     def send(i:Int) = {
       val (ri, ro) = RChan.mk[Unit]()
       new Thread { override def run() = {ro.success_!(()); mkP().send(i) }}.start()
       ri.get_!
     }
     
     send(1)
     send(2)
     send(3)
     Thread.sleep(100)

     import AssertChan._
     val test:AssertChan[Int] = for {
       seg1 <- read[Int](assertLength(1))
       seg2 <- read[Int](assertLength(1))
       seg3 <- read[Int](assertLength(1))
     } yield (seg1 ++ seg2 ++ seg3)
     
     test.run(i)
   }

}

