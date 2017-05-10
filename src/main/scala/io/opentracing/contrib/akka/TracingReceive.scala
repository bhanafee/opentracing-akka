package io.opentracing.contrib.akka

import java.time.Instant
import java.time.temporal.ChronoField

import scala.util.{Failure, Success}
import akka.actor.Actor.Receive
import akka.actor.ActorRef
import io.opentracing.Tracer.SpanBuilder
import io.opentracing.References

/** Decorator to add an OpenTracing Span around a Actor's Receive. */
class TracingReceive(r: Receive,
                     state: Spanned,
                     operation: TracingReceive.Operation,
                     modifiers: TracingReceive.Modifier*)
  extends Receive {

  /** Proxies `r.isDefinedAt` */
  override def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)

  /** Invokes `r.apply` in the context of a new `Span` held by `state`.  The span is built,
    * started, and finished automatically. */
  override def apply(v1: Any): Unit = {
    val z: SpanBuilder = state.tracer.buildSpan(operation(v1))
    val op: (SpanBuilder, TracingReceive.Modifier) => SpanBuilder = (sb, m) => m(sb, v1)
    val sb = modifiers.foldLeft(z)(op)
    state.span = sb.withStartTimestamp(nowMicroseconds()).start()
    r(v1)
    state.span.finish()
  }

  def nowMicroseconds(): Long = {
    val now = Instant.now()
    val secs = now.getLong(ChronoField.INSTANT_SECONDS)
    val micros = now.getLong(ChronoField.MICRO_OF_SECOND)
    secs * 1000000 + micros
  }

}

object TracingReceive {
  /** Used to specify the span's operation name. */
  type Operation = Any => String

  /** Used to stack SpanBuilder operations */
  type Modifier = (SpanBuilder, Any) => SpanBuilder

  def apply(state: Spanned, operation: Operation, modifiers: Modifier*)(r: Receive): Receive =
    new TracingReceive(r, state, operation, modifiers: _*)

  def apply(state: Spanned)(r: Receive): Receive =
    new TracingReceive(r, state, messageClassIsOperation, follows(state), tagAkkaComponent)

  def apply(state: Spanned, ref: ActorRef)(r: Receive) =
    new TracingReceive(r, state, messageClassIsOperation, follows(state), tagAkkaComponent, tagActorUri(ref))

  def constantOperation(operation: String): Operation = _ => operation

  def messageClassIsOperation: Operation = _.getClass.getName

  def actorNameIsOperation(ref: ActorRef): Operation = constantOperation(ref.path.name)

  /** Akka messages are one-way, so by default references to the received context are
    * `FOLLOWS_FROM` rather than`CHILD_OF` */
  def follows(state: Spanned, reference: String = References.FOLLOWS_FROM): Modifier =
    (b: SpanBuilder, m: Any) => m match {
      case c: Carrier[_]#Traceable =>
        // Extract the SpanContext from the payload
        (c.trace match {
          case b: Array[Byte] => BinaryCarrier.extract(state.tracer, b)
          case m: Map[String, String] => TextMapCarrier.extract(state.tracer, m)
        }) match {
          case Success(s) => b.addReference(reference, s)
          case Failure(_) => b
        }
      case _ => b
    }

  val tagAkkaComponent: Modifier =
    (b: SpanBuilder, _) => b.withTag("component", "akka")

  def tagActorUri(ref: ActorRef): Modifier =
    (b: SpanBuilder, _) => b.withTag("akka.uri", ref.path.name)

  def tag(name: String, extract: Any => String): Modifier =
    (b: SpanBuilder, m: Any) => b.withTag(name, extract(m))

//  def tag(name: String, extract: Any => Boolean): Modifier =
//    (b: SpanBuilder, m: Any) => b.withTag(name, extract(m))
//
//  def tag(name: String, extract: Any => Number): Modifier =
//    (b: SpanBuilder, m: Any) => b.withTag(name, extract(m))
}