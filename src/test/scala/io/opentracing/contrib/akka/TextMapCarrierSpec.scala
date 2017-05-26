package io.opentracing.contrib.akka

import io.opentracing.SpanContext
import io.opentracing.mock.MockSpan

import scala.util.Try

/** */
class TextMapCarrierSpec extends AbstractTracingSpec {

  def testSpan(): MockSpan = tracer.buildSpan("operation").start()

  "A text map carrier"  should "handle an empty payload" in {
    val test: Map[String, String] = Map.empty

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, test)

    result shouldBe 'isFailure
  }

  it should "generate span context data" in {
    val test: MockSpan.MockContext = testSpan().context()
    assume(test.spanId() > 0L, "Span id wasn't initialized")
    assume(test.traceId() > 0L, "Trace id wasn't initialized")

    val result: Map[String, String] = TextMapCarrier.inject(tracer, test)

    result should not be 'isEmpty
    result.size should be (2)
    result.get("spanid").isEmpty should be (false)
    result.get("traceid").isEmpty should be (false)
  }

  it should "generate baggage items" in {
    val test = testSpan().setBaggageItem("key1", "value1").setBaggageItem("key2", "value2").context()
    assume(test.baggageItems().iterator().hasNext, "Test baggage wasn't created")

    val result: Map[String, String] = TextMapCarrier.inject(tracer, test)

    result.get("key1") should be (Some("value1"))
    result.get("key2") should be (Some("value2"))
  }

  it should "extract payload data" in {
    val test: Map[String, String] = Map(("spanid", "13"),("traceid", "17"))

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, test)

    result.isSuccess should be (true)
    val mock = result.get.asInstanceOf[MockSpan.MockContext]
    mock.spanId() shouldBe (13L)
    mock.traceId() shouldBe (17L)
  }

  it should "handle a malformed payload" in {
    val test: Map[String, String] = Map(("bad_key", "bad_value"))

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, test)

    result shouldBe 'isFailure
  }

  it should "extract baggage items" in {
    val test: Map[String, String] = Map(("spanid", "13"),("traceid", "17"),("key1", "value1"))

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer, test)

    result shouldBe 'isSuccess
    val resultIter = result.get.baggageItems().iterator()
    resultIter.hasNext should be (true)
    val baggage = resultIter.next()
    baggage.getKey shouldBe ("key1")
    baggage.getValue shouldBe ("value1")
    resultIter.hasNext should be (false)
  }

}
