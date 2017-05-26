package io.opentracing.contrib.akka

import io.opentracing.SpanContext
import io.opentracing.mock.MockSpan

import scala.util.Try

/** */
class BinaryCarrierSpec extends AbstractTracingSpec {

  def testSpan(): MockSpan = tracer.buildSpan("operation").start()

  "A binary carrier" should "carry span context data" in pending

  it should "handle baggage" in pending

  "A binary carrier" should "handle an empty payload" in {
    val result: Try[SpanContext] = BinaryCarrier.extract(tracer, Array.emptyByteArray)

    result shouldBe 'isFailure
  }

  it should "handle a malformed payload" in {
    val malformed = Array[Byte](0, 1)

    val result: Try[SpanContext] = BinaryCarrier.extract(tracer, malformed)

    result shouldBe 'isFailure
  }

}
