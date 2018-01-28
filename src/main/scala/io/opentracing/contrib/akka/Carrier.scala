package io.opentracing.contrib.akka

import io.opentracing.{SpanContext, Tracer}

import scala.util.Try

/** Carrier type `P` should be treated as opaque to enable switching between representations, and immutable.
  * @tparam P the datatype used for the carrier payload.
  */
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

  /**
    * Returns a payload encoding the [[SpanContext]]
    * @param t the [[Tracer]] performing the encoding
    * @param c the [[SpanContext]] to encode
    * @return the encoded [[SpanContext]]
    */
  def inject(t: Tracer)(c: SpanContext): Payload

  /** Extract the tracer-specific payload to describe a [[SpanContext]].
    * @param t the tracer that generated the payload
    * @param p the payload containing the encoded context
    * @return the result of decoding the payload into a [[SpanContext]]
    */
  def extract(t: Tracer)(p: Payload): Try[SpanContext]

}
