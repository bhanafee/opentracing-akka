package io.opentracing.contrib.akka

import io.opentracing.{Span, Tracer}

/** Holds current span. */
trait Spanned {
  /** tracer implementation */
  val tracer: Tracer

  /** default operation name */
  def operation: String

  /** Internal holder for the span this wraps. */
  private[this] var _span: Span = _

  /** Returns the current span, creating and starting one if necessary. */
  def span: Span = {
    // Not thread-safe, but if there are two threads acting on the span there are other problems.
    if (_span == null) {
      _span = tracer.buildSpan(operation).start()
    }
    _span
  }

  /** Sets the current span. */
  def span_=(s: Span): Unit = _span = s

  /** Generates TextMap representation of the current span context. */
  def trace(): TextMapCarrier.Payload = TextMapCarrier.inject(tracer)(span.context())

  /** Generates binary representation of the current span context. */
  def traceB(): BinaryCarrier.Payload = BinaryCarrier.inject(tracer)(span.context())
}
