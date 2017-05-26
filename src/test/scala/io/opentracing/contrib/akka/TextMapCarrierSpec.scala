package io.opentracing.contrib.akka

import io.opentracing.SpanContext
import io.opentracing.mock.MockSpan

import scala.util.Try

/** */
class TextMapCarrierSpec extends AbstractTracingSpec {

  def testSpan(): MockSpan = tracer.buildSpan("operation").start()

  "A text map carrier" should "carry span context data" in {
    val original: MockSpan.MockContext = testSpan().context()
    assume(original.spanId() > 0L, "Span id wasn't initialized")
    assume(original.traceId() > 0L, "Trace id wasn't initialized")

    val carrier: Map[String, String] = TextMapCarrier.inject(tracer, original)
    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, carrier)

    result shouldBe 'isSuccess
    val ctx = result.get.asInstanceOf[MockSpan.MockContext]
    ctx.spanId() should be(original.spanId())
    ctx.traceId() should be(original.traceId())
  }

  it should "handle baggage" in {
    val test = testSpan().setBaggageItem("key1", "value1").setBaggageItem("key2", "value2").context()
    assume(test.baggageItems().iterator().hasNext, "Test baggage wasn't created")

    val carrier: Map[String, String] = TextMapCarrier.inject(tracer, test)
    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, carrier)

    result shouldBe 'isSuccess
    val it = result.get.baggageItems().iterator()
    val first = it.next()
    val second = it.next()
    it.hasNext should be(false)

    "key1".equals(first.getKey) | "key1".equals(second.getKey) should be(true)
    "value2".equals(first.getValue) | "value2".equals(second.getValue) should be(true)
  }

  it should "handle an empty payload" in {
    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, Map.empty)

    result shouldBe 'isFailure
  }

  it should "handle a malformed payload" in {
    val malformed: Map[String, String] = Map(("bad_key", "bad_value"))

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, malformed)

    result shouldBe 'isFailure
  }
}
