package io.opentracing.contrib.akka

import io.opentracing.mock.{MockSpan, MockTracer}

class SpannedSpec extends AbstractTracingSpec {

  def testSpanned(): Spanned = new Spanned() {
    override val tracer: MockTracer = SpannedSpec.this.tracer
    override val operation = "operation"
  }

  "A spanned holder" should "return a non-null Span immediately after creation" in {
    val result = testSpanned()
    result.span should not be null
  }

  it should "return the span that was set" in {
    val test: MockSpan = tracer.buildSpan("test").start()
    val result = testSpanned()
    result.span = test
    result.span should be (test)
  }

  it should "return the same span until it is changed" in {
    val test1 = tracer.buildSpan("test1").start()
    val test2 = tracer.buildSpan("test2").start()
    val result = testSpanned()
    result.span = test1
    result.span should be (test1)
    result.span should be (test1)
    result.span = test2
    result.span should be (test2)
    result.span should not be test1
  }

  it should "return a new Span after it is set to null" in {
    val test = testSpanned()
    val initial = test.span
    test.span = null
    val result = test.span
    result should not be null
    result should not be initial
  }
}
