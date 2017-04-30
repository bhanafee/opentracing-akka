package io.opentracing.contrib.akka

import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import io.opentracing.mock.{MockSpan, MockTracer}

class SpanStateSpec extends FlatSpec {
  val tracer: MockTracer = new MockTracer()

  def testSpanState(): SpanState = new SpanState(tracer, "operation")

  "A newly created SpanState" should "return a non-null Span" in {
    val test = testSpanState()
    test.span should not be null
  }

  "A SpanState" should "return the span that was set" in {
    val test = testSpanState()
    val span: MockSpan = tracer.buildSpan("test").start()
    test.span = span
    test.span should be (span)
  }

  "A SpanState" should "return the same span until it is changed" in {
    val test = testSpanState()
    val span1 = tracer.buildSpan("test1").start()
    val span2 = tracer.buildSpan("test2").start()
    test.span = span1
    test.span should be (span1)
    test.span should be (span1)
    test.span = span2
    test.span should be (span2)
    test.span should not be span1
  }

  "A SpanState" should "return a non-null Span after it is set to null" in {
    val test = testSpanState()
    val initial = test.span
    test.span = null
    val next = test.span
    next should not be null
    next should not be initial
  }
}
