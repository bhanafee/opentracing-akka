package io.opentracing.contrib.akka

import java.nio.ByteBuffer

import io.opentracing.propagation.Format.Builtin.BINARY
import io.opentracing.{SpanContext, Tracer}

import scala.util.Try

/** Adapt the `BINARY` format to an `Array[Byte]` payload */
object BinaryCarrier extends Carrier[Array[Byte]] {

  /** Maximum size of payload array returned by `generate`. */
  val MaxPayloadBytesGenerated = 2000

  override def generate(t: Tracer)(c: SpanContext): Payload = {
    val b: ByteBuffer = ByteBuffer.allocate(MaxPayloadBytesGenerated)
    t.inject(c, BINARY, b)
    val p = new Array[Byte](b.position())
    b.get(p)
    p
  }

  override def extract(t: Tracer)(p: Payload): Option[SpanContext] =
    if (p.isEmpty) None
    else Try(t.extract(BINARY, ByteBuffer.wrap(p))).toOption

}
