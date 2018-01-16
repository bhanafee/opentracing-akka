package io.opentracing.contrib.akka

import io.opentracing.{SpanContext, Tracer}
import io.opentracing.mock.{MockSpan, MockTracer}
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Try

/** */
class CarrierSpec extends FlatSpec with Matchers {
  val tracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)

  def testSpanContext[T](tracer: Tracer, carrier: Carrier[T]): Unit = {
    assume(tracer.isInstanceOf[MockTracer], "Wasn't a mock span (need direct access to span and trace IDs)")
    val span = tracer.buildSpan("operation").start()
    val original: MockSpan.MockContext = span.context().asInstanceOf[MockSpan.MockContext]
    assume(original.spanId() > 0L, "Span id wasn't initialized")
    assume(original.traceId() > 0L, "Trace id wasn't initialized")

    val p: T = carrier.inject(tracer, original)
    val result: Try[SpanContext] = carrier.extract(tracer, p)

    result shouldBe 'isSuccess
    val ctx = result.get.asInstanceOf[MockSpan.MockContext]
    ctx.spanId() should be(original.spanId())
    ctx.traceId() should be(original.traceId())
  }

  def testBaggage[T](tracer: Tracer, carrier: Carrier[T]): Unit = {
    val span = tracer.buildSpan("operation").start()
    val test = span.setBaggageItem("key1", "value1").setBaggageItem("key2", "value2").context()
    assume(test.baggageItems().iterator().hasNext, "Test baggage wasn't created")

    val p: T = carrier.inject(tracer, test)
    val result: Try[SpanContext] = carrier.extract(tracer, p)

    result shouldBe 'isSuccess
    val it = result.get.baggageItems().iterator()
    val first = it.next()
    val second = it.next()
    it.hasNext should be(false)

    "key1".equals(first.getKey) | "key1".equals(second.getKey) should be(true)
    "value2".equals(first.getValue) | "value2".equals(second.getValue) should be(true)
  }

  def testEmpty[T](tracer: Tracer, carrier: Carrier[T], empty: T): Unit = {
    val result: Try[SpanContext] = carrier.extract(tracer, empty)

    result shouldBe 'isFailure
  }

  def testMalformed[T](tracer: Tracer, carrier: Carrier[T], malformed: T): Unit = {
    val result: Try[SpanContext] = carrier.extract(tracer, malformed)
    result shouldBe 'isFailure
  }

  "A text map carrier" should "carry span context data" in testSpanContext(tracer, TextMapCarrier)

  it should "handle baggage" in testBaggage(tracer, TextMapCarrier)

  it should "handle an empty payload" in testEmpty(tracer, TextMapCarrier, Map.empty.asInstanceOf[Map[String, String]])

  it should "handle a malformed payload" in testMalformed(tracer, TextMapCarrier, Map(("bad_key", "bad_value")))

  // Fails because mock tracer doesn't handle BINARY
  "A binary carrier" should "carry span context data" in pending

  // Fails because mock tracer doesn't handle BINARY
  it should "handle baggage" in pending

  it should "handle an empty payload" in testEmpty(tracer, BinaryCarrier, Array.emptyByteArray)

  it should "handle a malformed payload" in testMalformed(tracer, BinaryCarrier, Array[Byte](0, 1))

}
