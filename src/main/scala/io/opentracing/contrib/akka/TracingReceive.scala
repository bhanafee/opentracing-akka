package io.opentracing.contrib.akka

import akka.actor.Actor.Receive
import io.opentracing.{References, SpanContext}
import io.opentracing.Tracer.SpanBuilder

/** Decorator to add a Span around a Receive. Akka messages are one-way, so references
  * to the received context are `FOLLOWS_FROM` rather than `CHILD_OF` */
class TracingReceive(state: SpanState, r: Receive) extends Receive {

  private val binaryExtractor: (BinaryCarrier.Payload) => Option[SpanContext] = BinaryCarrier.extract(state.tracer)

  private val textMapExtractor: (TextMapCarrier.Payload) => Option[SpanContext] = TextMapCarrier.extract(state.tracer)

  /**
    * Wraps a call to `r` in a `Span`, where the `Span` itself is maintained by `SpanState`
    *
    * @param contextualize typically adds references to other spans
    * @param v1            the original parameter for `r`
    */
  private def wrap(contextualize: SpanBuilder => SpanBuilder, v1: Any): Unit = {
    state.span = contextualize(state.tracer.buildSpan(state.operation)).start()
    r(v1)
    state.span.finish()
  }

  def tag(v1: Any): SpanBuilder => SpanBuilder = _.withTag("message", v1.getClass.getName)

  /** Proxies `r.isDefinedAt` */
  override def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)

  /** Invokes `r.apply` in the context of a new `Span` held by `state`.  The span is built,
    * started, and finished automatically. */
  override def apply(v1: Any): Unit = v1 match {
    case c: Carrier[_]#Traceable =>
      // Extract the SpanContext from the payload
      val sc: Option[SpanContext] = c.trace match {
        case b: Array[Byte] => binaryExtractor(b)
        case m: Map[String, String] => textMapExtractor(m)
      }
      // Create a builder that will add a reference to the context
      val follows = (b: SpanBuilder) => sc match {
        case Some(s) => b.addReference(References.FOLLOWS_FROM, s)
        case None => b
      }
      wrap(sb => tag(c)(follows(sb)), c)
    case c => wrap(tag(c), c)
  }

}

object TracingReceive {
  def apply(state: SpanState)(r: Receive): Receive = new TracingReceive(state, r)

  // TODO: parameterize operation name

  // TODO: parameterize tag name for message type

  // TODO: parameterize extractor for message type with comment warning about simpleName and canonicalName
}