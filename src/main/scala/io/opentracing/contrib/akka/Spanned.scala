package io.opentracing.contrib.akka

import java.time.Instant
import java.time.temporal.ChronoField

import akka.actor.ActorRef
import io.opentracing.Tracer.SpanBuilder
import io.opentracing.{Span, Tracer}

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
    if (_span == null) {
      _span = tracer.buildSpan(operation()).start()
    }
    _span
  }

  /** Sets the current span. */
  def span_=(s: Span): Unit = _span = s

}

object Spanned {
  /** Used to stack SpanBuilder operations */
  type Modifier = SpanBuilder => SpanBuilder

  /** Add a "component" span tag indicating the framework is "akka" */
  val tagAkkaComponent: Modifier = _.withTag("component", "akka")

  val tagConsumer: Modifier = _.withTag("span.kind", "consumer")

  val tagProducer: Modifier = _.withTag("span.kind", "producer")

  /** Add an "akka.uri" span tag containing the actor address */
  def tagActorUri(ref: ActorRef): Modifier = _.withTag("akka.uri", ref.path.address.toString)

  /** Timestamp in microseconds */
  def micros(): Long = {
    val now = Instant.now()
    val secs = now.getLong(ChronoField.INSTANT_SECONDS)
    val micros = now.getLong(ChronoField.MICRO_OF_SECOND)
    secs * 1000000 + micros
  }

  /** Add a start timestamp using `micros()` */
  def timestamp(): Modifier = _.withStartTimestamp(micros())
}
