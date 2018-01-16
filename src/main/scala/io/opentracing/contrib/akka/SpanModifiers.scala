package io.opentracing.contrib.akka

import java.time.Instant
import java.time.temporal.ChronoField

import akka.actor.ActorRef
import io.opentracing.{References, Tracer}
import io.opentracing.Tracer.SpanBuilder

import scala.util.{Failure, Success}

object SpanModifiers {

  /** Used to stack SpanBuilder operations */
  type Modifier = (SpanBuilder, Any) => SpanBuilder


  /** Akka messages are one-way, so by default references to the received context are
    * `FOLLOWS_FROM` rather than `CHILD_OF` */
  def follows(tracer: Tracer, reference: String = References.FOLLOWS_FROM): Modifier =
    (b: SpanBuilder, m: Any) => m match {
      // The type parameter to Carrier is erased, so match on Carrier then match on the trace parameter
      case c: Carrier[_]#Traceable =>
        // Extract the SpanContext from the payload
        (c.trace match {
          case b: Array[Byte] => BinaryCarrier.extract(tracer, b)
          // The type parameters to Map are erased, but the Carrier trait is sealed and there's only one Map trace
          case m: Map[String, String]@unchecked => TextMapCarrier.extract(tracer, m)
        }) match {
          case Success(s) => b.addReference(reference, s)
          case Failure(_) => b
        }
      case _ => b
    }

  /** Add a "component" span tag indicating the framework is "akka" */
  val tagAkkaComponent: Modifier =
    (b: SpanBuilder, _) => b.withTag("component", "akka")

  /** Add an "akka.uri" span tag containing the actor address */
  def tagActorUri(ref: ActorRef): Modifier =
    (b: SpanBuilder, _) => b.withTag("akka.uri", ref.path.address.toString)

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
