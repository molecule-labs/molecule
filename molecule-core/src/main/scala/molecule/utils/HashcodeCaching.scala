package molecule.utils

/**
 * Suggested by John Sullivan on scala-user mailing list (4 mai 2011)
 */
trait HashcodeCaching { self: Product =>
  override lazy val hashCode: Int =
    scala.runtime.ScalaRunTime._hashCode(this)
}