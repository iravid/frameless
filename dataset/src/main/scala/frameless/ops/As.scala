package frameless
package ops

import shapeless.ops.record.Values
import shapeless.{Generic, HList}

class As[T, U](implicit val encoder: TypedEncoder[U])

object As extends LowPriorityAs {
  implicit def deriveProduct[T, U, S <: HList](
    implicit
    e: TypedEncoder[U],
    t: Generic.Aux[T, S],
    u: Generic.Aux[U, S]
  ): As[T, U] = new As[T, U]
}

trait LowPriorityAs {
  implicit def deriveHList[T <: HList, V <: HList, U](
    implicit
      e: TypedEncoder[U],
      v: Values.Aux[T, V],
      u: Generic.Aux[U, V]
  ): As[T, U] = new As[T, U]
}
