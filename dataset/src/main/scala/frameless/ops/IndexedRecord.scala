package frameless
package ops

import shapeless._, labelled._

trait IndexedRecord[L <: HList] extends DepFn1[L] {
  type Out <: HList
}

object IndexedRecord {
  type Aux[T <: HList, Out0] = IndexedRecord[T] { type Out = Out0 }

  def apply[T <: HList](implicit ir: IndexedRecord[T]): Aux[T, ir.Out] = ir

  implicit def hnil: Aux[HNil, HNil] = new IndexedRecord[HNil] {
    type Out = HNil
    def apply(t: HNil): HNil = HNil
  }

  implicit def hcons[H, N <: Nat, T <: HList](
    implicit
    w: Witness.Aux[N],
    rest: IndexedRecord[T]): Aux[(H, N) :: T, FieldType[N, H] :: rest.Out] = new IndexedRecord[(H, N) :: T] {
    type Out = FieldType[N, H] :: rest.Out
    def apply(t: (H, N) :: T) = field[w.T](t.head._1) :: rest(t.tail)
  }
}
