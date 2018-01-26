package io.opentracing.contrib.akka

import io.opentracing.{SpanContext, Tracer}

import scala.util.Try

/** Carrier type `P` should be treated as opaque to enable switching between representations, and immutable. */
trait Carrier[P] {
  type Payload = P

  /** Adds a carrier to message case classes. To keep the trace information out of the usual
    * convenience functions on a case class, separate it into a second parameter group.
    *
    * Example:
    *
    * {{{case class Message(foo: String, bar: Int)(val trace: Payload) extends Traceable
    *
    * msg match {
    *   case ("foo", 1) ⇒ ???
    * }}}
    */
  trait Traceable {
    val trace: Payload
    def context(t: Tracer): Try[SpanContext] = extract(t)(trace)
  }

  /** Generate the tracer-specific payload to transmit a span context. */
  def inject(t: Tracer)(c: SpanContext): Payload

  /** Extract the tracer-specific payload to describe a span context. */
  def extract(t: Tracer)(p: Payload): Try[SpanContext]

}
