package io.opentracing.contrib.akka

import io.opentracing.SpanContext
import io.opentracing.mock.{MockSpan, MockTracer}
import io.opentracing.mock.MockSpan.MockContext

import scala.util.Try

/** */
class TextMapCarrierSpec extends AbstractTracingSpec {

  def testSpan(): MockSpan = tracer.buildSpan("operation").start()

  it should "generate span context data" in {
    val test: MockContext = testSpan().context()
    assert(test.spanId() > 0L)
    assert(test.traceId() > 0L)

    val result: Map[String, String] = TextMapCarrier.inject(tracer)(test)

    result should not be 'isEmpty
    result.size should be (2)
    result.get("spanid").isEmpty should be (false)
    result.get("traceid").isEmpty should be (false)
  }

  ignore should "generate baggage items" in {
    val test = testSpan().setBaggageItem("key1", "value1").setBaggageItem("key2", "value2").context()
    assert(!test.baggageItems().iterator().hasNext)

    val result: Map[String, String] = TextMapCarrier.inject(tracer)(test)

    result.get("key1") should be (Some("key1"))
    result.get("key2") should be (Some("key2"))
  }

  it should "extract payload data" in {
    val test: Map[String, String] = Map(("spanid", "13"),("traceid", "17"))

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer)(test)

    result.isSuccess should be (true)
    val mock = result.get.asInstanceOf[MockContext]
    mock.spanId() should be (13L)
    mock.traceId() should be (17L)
  }

  it should "handle an empty payload" in {
    val test: Map[String, String] = Map.empty

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer)(test)

    result.isFailure should be (true)
  }

  it should "handle a malformed payload" in {
    val test: Map[String, String] = Map(("bad_key", "bad_value"))

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer)(test)

    result.isFailure should be (true)
  }

  ignore should "extract baggage items" in {
    val test: Map[String, String] = Map(("spanid", "13"),("traceid", "17"),("key1", "value1"))

    val result: Try[SpanContext] = TextMapCarrier.extract(tracer)(test)

    result.isSuccess should be (true)
    val resultIter = result.get.baggageItems().iterator()
    resultIter.hasNext should be (true)
    resultIter.next() should be (("key1", "value1"))
    resultIter.hasNext should be (false)
  }

}
