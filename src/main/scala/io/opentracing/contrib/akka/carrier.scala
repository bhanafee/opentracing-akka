package io.opentracing.contrib.akka

import io.opentracing.propagation.Format.Builtin.{BINARY, TEXT_MAP}
import io.opentracing.{SpanContext, Tracer}

import scala.util.{Failure, Success, Try}

/** Carrier type `P` should be treated as opaque to enable switching between representations, and immutable. */
sealed trait Carrier[P] {
  type Payload = P

  /** Adds a carrier to message case classes. To keep the trace information out of the usual
    * convenience functions on a case class, separate it into a second parameter group.
    *
    * Example:
    *
    * {{{case class Message(foo: String, bar: Int)(val trace: Payload) extends Traceable
    *
    * msg match {
    *   case ("foo", 1) => ???
    * }}}
    */
  trait Traceable {
    val trace: Payload
  }

  /** Generate the tracer-specific payload to transmit a span context. */
  def inject(t: Tracer, c: SpanContext): Payload

  /** Extract the tracer-specific payload to describe a span context. */
  def extract(t: Tracer, p: Payload): Try[SpanContext]

}

/** Adapt the `BINARY` format to an `Array[Byte]` payload */
object BinaryCarrier extends Carrier[Array[Byte]] {

  import java.nio.ByteBuffer

  /** Maximum size of payload array returned by `generate`. */
  val MaxPayloadBytesGenerated = 2000

  override def inject(t: Tracer, c: SpanContext): Payload = {
    val b: ByteBuffer = ByteBuffer.allocate(MaxPayloadBytesGenerated)
    t.inject(c, BINARY, b)
    val p = new Array[Byte](b.position())
    b.get(p)
    p
  }

  override def extract(t: Tracer, p: Payload): Try[SpanContext] =
    if (p.isEmpty) Failure(new NoSuchElementException("Empty payload"))
    else Try(t.extract(BINARY, ByteBuffer.wrap(p))) match {
      case Success(null) => Failure(new NullPointerException("Tracer.extract returned null"))
      case x => x
    }

}

/** Adapts the `TEXT_MAP` format to an immutable `Map[String, String]` payload */
object TextMapCarrier extends Carrier[Map[String, String]] {

  import java.util.{Iterator => JIterator, Map => JMap}

  import io.opentracing.propagation.TextMap

  import scala.collection.JavaConverters._

  /** Stub TextMap implementation so `generate` and `extract` only need the functional parts. */
  private class TextMapAdapter extends TextMap {
    override def iterator(): JIterator[JMap.Entry[String, String]] =
      throw new UnsupportedOperationException("Tried to read value from empty carrier")

    override def put(key: String, value: String): Unit =
      throw new UnsupportedOperationException("Tried to put value to immutable carrier")
  }

  override def inject(t: Tracer, c: SpanContext): Payload = {
    var kvs: List[(String, String)] = List.empty
    t.inject(c, TEXT_MAP, new TextMapAdapter {
      override def put(key: String, value: String): Unit = kvs = (key, value) :: kvs
    })
    Map(kvs: _*)
  }

  override def extract(t: Tracer, p: Payload): Try[SpanContext] =
    if (p.isEmpty) Failure(new NoSuchElementException("Empty payload"))
    else {
      Try(t.extract(TEXT_MAP, new TextMapAdapter {
        override def iterator(): JIterator[JMap.Entry[String, String]] =
          p.asJava.entrySet().iterator()
      })) match {
        case Success(null) => Failure(new NullPointerException("Tracer.extract returned null"))
        case x => x
      }
    }

}
