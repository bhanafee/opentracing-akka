package io.opentracing.contrib.akka

import java.time.Instant
import java.time.temporal.ChronoField

import akka.actor.ActorRef
import io.opentracing.{References, Span, SpanContext, Tracer}
import io.opentracing.Tracer.SpanBuilder

import scala.util.{Failure, Success}

object Spanning {

  def apply(tracer: Tracer, operation: String, message: Any, modifiers: Modifier*): Span = {
    val z: SpanBuilder = tracer.buildSpan(operation)
    val op: (SpanBuilder, Modifier) => SpanBuilder = (sb, m) => m(sb, message)
    val sb = modifiers.foldLeft(z)(op)
    sb.start()
  }

  /* Modifiers */

  /** Used to stack SpanBuilder operations */
  type Modifier = (SpanBuilder, Any) => SpanBuilder

  def akkaConsumer(tracer: Tracer): Seq[Modifier] = Seq[Modifier] (
    tagAkkaComponent,
    tagAkkaConsumer,
    follows(tracer),
    timestamp()
  )

  def akkaConsumer(tracer: Tracer, ref: ActorRef): Seq[Modifier] =
    akkaConsumer(tracer) :+ tagActorUri(ref)

  def akkaProducer(span: Span): Seq[Modifier] = Seq[Modifier] (
    tagAkkaComponent,
    tagAkkaConsumer,
    follows(span),
    timestamp()
  )

  /** Akka messages are one-way, so by default references to the received context are
    * `FOLLOWS_FROM` rather than `CHILD_OF` */
  def follows(context: SpanContext): Modifier =
    (sb: SpanBuilder, _) => sb.addReference(References.FOLLOWS_FROM, context)

  def follows(span: Span): Modifier = follows(span.context())

  def follows(tracer: Tracer): Modifier =
    (sb: SpanBuilder, message: Any) => message match {
      case c: Carrier[_]#Traceable =>
        c.context(tracer) match {
          case Success(sc) => sb.addReference(References.FOLLOWS_FROM, sc)
          case Failure(_) => sb
        }
      case _ => sb
    }

  /** Add a "component" span tag indicating the framework is "akka" */
  val tagAkkaComponent: Modifier =
    (sb: SpanBuilder, _) => sb.withTag("component", "akka")

  val tagAkkaConsumer: Modifier =
    (sb: SpanBuilder, _) => sb.withTag("span.kind", "consumer")

  val tagAkkaProducer: Modifier =
    (sb: SpanBuilder, _) => sb.withTag("span.kind", "producer")

  /** Add an "akka.uri" span tag containing the actor address */
  def tagActorUri(ref: ActorRef): Modifier =
    (sb: SpanBuilder, _) => sb.withTag("akka.uri", ref.path.address.toString)

  /** Add an arbitrary span tag, taking the value from some extract function that accepts the message as input */
  def tag(name: String, extract: Any => Option[Any]): Modifier = (sb: SpanBuilder, m: Any) => extract(m) match {
    case None => sb
    case Some(s: String) => sb.withTag(name, s)
    case Some(b: Boolean) => sb.withTag(name, b)
    case Some(n: Number) => sb.withTag(name, n)
    case Some(x) => sb.withTag(name, x.toString)
  }

  /** Timestamp in microseconds */
  def micros(): Long = {
    val now = Instant.now()
    val secs = now.getLong(ChronoField.INSTANT_SECONDS)
    val micros = now.getLong(ChronoField.MICRO_OF_SECOND)
    secs * 1000000 + micros
  }

  /** Add a start timestamp using `micros()` */
  def timestamp(): Modifier = (sb: SpanBuilder, _) => sb.withStartTimestamp(micros())

}
