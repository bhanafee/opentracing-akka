package io.opentracing.contrib.akka

import io.opentracing.SpanContext
import io.opentracing.mock.MockSpan.MockContext
import io.opentracing.mock.MockSpan

import scala.util.Try

/** */
class BinaryCarrierSpec extends AbstractTracingSpec {

  def testSpan(): MockSpan = tracer.buildSpan("operation").start()

  "A binary carrier" should "handle an empty payload" in {
    val test = Array.emptyByteArray

    val result: Try[SpanContext] = BinaryCarrier.extract(tracer, test)

    result shouldBe 'isFailure
  }

  ignore should "generate span context data" in {
    val test: MockContext = testSpan().context()

    val result: Array[Byte] = BinaryCarrier.inject(tracer, test)

    //assert(result.length > 0, "Empty array injected")
    // TODO: How to check for spanid, traceid
  }

  ignore should "generate baggage items" in {
    val test = testSpan().setBaggageItem("key1", "value1").setBaggageItem("key2", "value2")

    val result: Array[Byte] = BinaryCarrier.inject(tracer, test.context())

    // TODO: How to check?
  }

  ignore should "extract payload data" in {
    val test = Array.emptyByteArray // TODO: encode spanid = 13, traceid = 17

    val result: Try[SpanContext] = BinaryCarrier.extract(tracer, test)

    result shouldBe 'isSuccess
    val mock = result.get.asInstanceOf[MockContext]
    mock.spanId() should be (13L)
    mock.traceId() should be (17L)
  }

  it should "handle a malformed payload" in {
    val test = Array[Byte](0, 1)

    val result: Try[SpanContext] = BinaryCarrier.extract(tracer, test)

    result shouldBe 'isFailure
  }

  ignore should "extract baggage items" in {
    val test = Array.emptyByteArray // TODO: encode spanid = 13, traceid = 17, key1 = value1

    val result: Try[SpanContext] = BinaryCarrier.extract(tracer, test)

    result shouldBe 'isSuccess
    val resultIter = result.get.baggageItems().iterator()
    resultIter shouldBe 'hasNext
    resultIter.next() should be (("key1", "value1"))
    resultIter.hasNext should be (false)
  }

}
