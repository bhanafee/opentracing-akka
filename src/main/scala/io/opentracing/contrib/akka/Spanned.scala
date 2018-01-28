package io.opentracing.contrib.akka

import io.opentracing.{Span, Tracer}

import scala.util.{Failure, Try}

/** Holds current span. */
trait Spanned {
  /** tracer implementation */
  val tracer: Tracer

  /** default operation name */
  def operation(): String

  /** Internal holder for the span this wraps. */
  private[this] var _span: Span = _

  /** Returns the current span, creating and starting one if necessary. */
  def span: Span = {
    // Not thread-safe, but if there are two threads acting on the span there are other problems.
    if (_span eq null) {
      _span = tracer.buildSpan(operation()).start()
    }
    _span
  }

  /** Sets the current span. */
  def span_=(s: Span): Unit = _span = s

  /** Convenience conversion used in trace bodies. */
  implicit val span2MapPayload: Span ⇒ TextMapCarrier.Payload = s ⇒ TextMapCarrier.inject(tracer)(s.context())

  /** Convenience conversion used in trace bodies. */
  implicit val span2ByteArrayPayload: Span ⇒ BinaryCarrier.Payload = s ⇒ BinaryCarrier.inject(tracer)(s.context())

  /** Start a child span. The span will be tagged automatically as ``span.kind = producer``, and if the
    * body throws an exception the span will be tagged with ``error = true`` and the exception will be
    * logged as ``error.object``.
    * @param op operation for the child span
    * @param modifiers applied to the SpanBuilder for the child span
    * @param body the block to be traced
    */
  def trace(op: String, modifiers: Spanned.Modifier*)(body: Span ⇒ Unit): Unit = {
    val child: Span = (Spanned.tagProducer +: modifiers).foldLeft(tracer.buildSpan(op))((sb, m) ⇒ m(sb)).start()
    Try(body(child)) match {
      case Failure(e) ⇒
        child.setTag("error", true)
        val fields: java.util.Map[String, Any] = new java.util.HashMap[String, Any]
        fields.put("event", "error")
        fields.put("error.object", e)
        span.log(Spanned.micros(), fields)
        child.finish()
      case _ ⇒ child.finish()
    }
  }

}


import java.time.Instant
import java.time.temporal.ChronoField

import io.opentracing.Tracer.SpanBuilder

object Spanned {
  /** Used to stack SpanBuilder operations */
  type Modifier = SpanBuilder ⇒ SpanBuilder

  /** Tags ``span.kind = consumer``. */
  val tagConsumer: Modifier = _.withTag("span.kind", "consumer")

  /** Tags ``span.kind = producer``. */
  val tagProducer: Modifier = _.withTag("span.kind", "producer")

  /** Timestamp in microseconds */
  def micros(): Long = {
    val now = Instant.now()
    val secs = now.getLong(ChronoField.INSTANT_SECONDS)
    val micros = now.getLong(ChronoField.MICRO_OF_SECOND)
    secs * 1000000 + micros
  }

  /** Adds a start timestamp using `micros()` */
  def timestamp(): Modifier = _.withStartTimestamp(micros())
}
