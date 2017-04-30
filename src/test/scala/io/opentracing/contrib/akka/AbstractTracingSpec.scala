package io.opentracing.contrib.akka

import io.opentracing.mock.MockTracer
import org.scalatest.{FlatSpec, Matchers}

/** Base class for OpenTracing Akka tests*/
class AbstractTracingSpec extends FlatSpec with Matchers {
  val tracer: MockTracer = new MockTracer()

}
