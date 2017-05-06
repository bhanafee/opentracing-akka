package io.opentracing.contrib.akka

import akka.actor.Actor.Receive
import io.opentracing.Tracer.SpanBuilder
import io.opentracing.{References, SpanContext}

import scala.util.Try

/** Decorator to add a Span around a Receive. Akka messages are one-way, so references
  * to the received context are `FOLLOWS_FROM` rather than `CHILD_OF` */
class TracingReceive(state: Spanned, r: Receive) extends Receive {

  private val binaryExtractor: (BinaryCarrier.Payload) => Try[SpanContext] = BinaryCarrier.extract(state.tracer)

  private val textMapExtractor: (TextMapCarrier.Payload) => Try[SpanContext] = TextMapCarrier.extract(state.tracer)

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

  /** Proxies `r.isDefinedAt` */
  override def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)

  /** Invokes `r.apply` in the context of a new `Span` held by `state`.  The span is built,
    * started, and finished automatically. */
  override def apply(v1: Any): Unit = v1 match {
    case c: Carrier[_]#Traceable =>
      // Extract the SpanContext from the payload
      val sc: Option[SpanContext] = (c.trace match {
        case b: Array[Byte] => binaryExtractor(b)
        case m: Map[String, String] => textMapExtractor(m)
      }).toOption
      // Create a builder that will add a reference to the context
      val follows = (b: SpanBuilder) => sc match {
        case Some(s) => b.addReference(References.FOLLOWS_FROM, s)
        case None => b
      }
      wrap(sb => TracingReceive.tag(c)(follows(sb)), c)
    case c => wrap(TracingReceive.tag(c), c)
  }

}

object TracingReceive {
  def apply(state: Spanned)(r: Receive): Receive = new TracingReceive(state, r)


  def tag(v1: Any): SpanBuilder => SpanBuilder = _.withTag("message", v1.getClass.getName)


  /* TODO: figure out reference strategy.
     - Always FOLLOW_FROM? Or if CHILD_OF useful for always sender()?
     - What is correct SpanContext in a send back to sender()?
     - How to recognize return messages?
  */

  // TODO: parameterize operation name (set via default in SpanState or during builder or in Span)

  // TODO: stackable functions to add tags
  // _.withTag("message", v1.getClass.getName)

  // TODO: parameterize tag for message type (warn about simpleName and canonicalName)
}