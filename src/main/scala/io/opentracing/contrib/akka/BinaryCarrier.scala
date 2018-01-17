package io.opentracing.contrib.akka

import io.opentracing.{SpanContext, Tracer}
import io.opentracing.propagation.Format.Builtin.BINARY

import scala.util.{Failure, Success, Try}

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
