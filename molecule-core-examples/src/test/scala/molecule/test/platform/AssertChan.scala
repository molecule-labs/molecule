package molecule.test.platform

import molecule._
import channel.IChan

class AssertChan[A](private val run:(IChan[A], (Seg[A], IChan[A]) => Unit) => Unit) {

  def map(f:Seg[A] => Seg[A]):AssertChan[A] = new AssertChan[A]((ichan, k) =>
    run(ichan, (seg, ichan) => k(f(seg), ichan))
  )
  def flatMap(f:Seg[A] => AssertChan[A]):AssertChan[A] = new AssertChan[A]((ichan, k) =>
    run(ichan, (seg, ichan) => f(seg).run(ichan, k))
  )
  def run(ichan:IChan[A]):Unit = {
    import channel.RChan
    val (ri, ro) = RChan.mk[Unit]
    run(ichan, (_, _) => ro.success_!())
    ri.get_!()
  }
}

object AssertChan {
    
  def read[A](assert:Seg[A] => Unit):AssertChan[A] = new AssertChan[A]((ichan:IChan[A], k:(Seg[A], IChan[A]) => Unit) => 
    ichan.read((seg, ichan) => {assert(seg); k(seg, ichan)})
  )
  
  def assertLength[A](length:Int)(seg:Seg[A]):Unit = {
    println(length + " " + seg)
    assert(seg.length == length, "Expected a length of " + length + " but found " + seg.length + " in " + seg)
  }
}