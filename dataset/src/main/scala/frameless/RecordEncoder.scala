package frameless

import org.apache.spark.sql.FramelessInternals
import org.apache.spark.sql.catalyst.analysis.GetColumnByOrdinal
import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.catalyst.expressions.objects.{Invoke, NewInstance}
import org.apache.spark.sql.types._
import shapeless.labelled.FieldType
import shapeless._

import scala.reflect.ClassTag
import shapeless.ops.nat.ToInt

case class RecordEncoderField(
  ordinal: Int,
  name: String,
  encoder: TypedEncoder[_]
)

trait RecordEncoderFields[T <: HList] extends Serializable {
  def value: List[RecordEncoderField]
}

object RecordEncoderFields {
  implicit val deriveNil: RecordEncoderFields[HNil] = new RecordEncoderFields[HNil] {
    def value: List[RecordEncoderField] = Nil
  }

  implicit def deriveRecordSymbol[K <: Symbol, H, T <: HList](
    implicit
    key: Witness.Aux[K],
    head: TypedEncoder[H],
    tail: RecordEncoderFields[T]
  ): RecordEncoderFields[FieldType[K, H] :: T] = new RecordEncoderFields[FieldType[K, H] :: T] {
    def value: List[RecordEncoderField] = {
      val fieldName = key.value.name
      val fieldEncoder = RecordEncoderField(0, fieldName, head)

      fieldEncoder :: tail.value.map(x => x.copy(ordinal = x.ordinal + 1))
    }
  }

  implicit def deriveRecordNat[K <: Nat, H, T <: HList](
    implicit
    toInt: ToInt[K],
    head: TypedEncoder[H],
    tail: RecordEncoderFields[T]
  ): RecordEncoderFields[FieldType[K, H] :: T] = new RecordEncoderFields[FieldType[K, H] :: T] {
    def value: List[RecordEncoderField] = {
      val fieldName = s"_${toInt()}"
      val fieldEncoder = RecordEncoderField(0, fieldName, head)

      fieldEncoder :: tail.value.map(x => x.copy(ordinal = x.ordinal + 1))
    }
  }
}

class RecordEncoder[G <: HList](
  implicit
  fields: Lazy[RecordEncoderFields[G]],
  classTag: ClassTag[G]
) extends TypedEncoder[G] {
  def nullable: Boolean = false

  def sourceDataType: DataType = FramelessInternals.objectTypeFor[G]

  def targetDataType: DataType = {
    val structFields = fields.value.value.map { field =>
      StructField(
        name = field.name,
        dataType = field.encoder.targetDataType,
        nullable = field.encoder.nullable,
        metadata = Metadata.empty
      )
    }

    StructType(structFields)
  }

  def extractorFor(path: Expression): Expression = {
    val nameExprs = fields.value.value.map { field =>
      Literal(field.name)
    }

    val valueExprs = fields.value.value.map { field =>
      val fieldPath = Invoke(path, field.name, field.encoder.sourceDataType, Nil)
      field.encoder.extractorFor(fieldPath)
    }

    // the way exprs are encoded in CreateNamedStruct
    val exprs = nameExprs.zip(valueExprs).flatMap {
      case (nameExpr, valueExpr) => nameExpr :: valueExpr :: Nil
    }

    CreateNamedStruct(exprs)
  }

  def constructorFor(path: Expression): Expression = {
    val exprs = fields.value.value.map { field =>
      val fieldPath = path match {
        case BoundReference(ordinal, dataType, nullable) =>
          GetColumnByOrdinal(field.ordinal, field.encoder.sourceDataType)
        case other =>
          GetStructField(path, field.ordinal, Some(field.name))
      }
      field.encoder.constructorFor(fieldPath)
    }

    NewInstance(classTag.runtimeClass, exprs, sourceDataType, propagateNull = true)
  }
}
