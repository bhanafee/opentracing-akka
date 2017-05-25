package io.opentracing.contrib.akka

import java.time.Instant
import java.time.temporal.ChronoField

import scala.util.{Failure, Success, Try}
import akka.actor.Actor.Receive
import akka.actor.ActorRef
import io.opentracing.Tracer.SpanBuilder
import io.opentracing.{References, Span}

/** Decorator to add an OpenTracing Span around an Actor's Receive. */
class TracingReceive(r: Receive,
                     state: Spanned,
                     operation: TracingReceive.Operation,
                     modifiers: TracingReceive.Modifier*)
  extends Receive {

  /** Proxies `r.isDefinedAt` */
  override def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)

  /** Invokes `r.apply` in the context of a new `Span` held by `state`.  The span is built,
    * started, and finished by this wrapper using manual span propagation. Span propagation
    * using an `ActiveSpan` is not necessary because the Akka programming model guarantees
    * single threaded execution of `apply`. */
  override def apply(v1: Any): Unit = {
    val z: SpanBuilder = state.tracer.buildSpan(operation(v1))
    val op: (SpanBuilder, TracingReceive.Modifier) => SpanBuilder = (sb, m) => m(sb, v1)
    val sb = modifiers.foldLeft(z)(op)
    state.span = sb.startManual()
    Try (r(v1)) match {
      case Success(_) => state.span.finish()
      case Failure(e) =>
        error(state.span, e)
        state.span.finish()
        throw e
    }
  }

  /** Tag and log an exception thrown by `apply` */
  def error(span: Span, e: Throwable): Unit = {
    span.setTag("error", true)
    val fields: java.util.Map[String, Any] = new java.util.HashMap[String, Any]
    fields.put("event", "error")
    fields.put("error.object", e)
    span.log(fields)
  }
}

object TracingReceive {
  /** Used to specify the span's operation name. */
  type Operation = Any => String

  /** Used to stack SpanBuilder operations */
  type Modifier = (SpanBuilder, Any) => SpanBuilder

  def apply(state: Spanned, operation: Operation, modifiers: Modifier*)(r: Receive): Receive =
    new TracingReceive(r, state, operation, modifiers: _*)

  /** Tracing receive that
    * - uses the message type as the operation name
    * - includes a FOLLOWS_FROM reference to any span found in the message
    * - tags the "component" as "akka"
    */
  def apply(state: Spanned)(r: Receive): Receive =
    new TracingReceive(r, state, messageClassIsOperation, follows(state), tagAkkaComponent, timestamp())

  /** Tracing receive that
    * - uses the message type as the operation name
    * - includes a FOLLOWS_FROM reference to any span found in the message
    * - tags the "component" as "akka"
    * - tags the "akka.uri" as the actor address
    */
  def apply(state: Spanned, ref: ActorRef)(r: Receive) =
    new TracingReceive(r, state, messageClassIsOperation, follows(state), tagAkkaComponent, tagActorUri(ref), timestamp())

  /** Use a constant operation name. */
  def constantOperation(operation: String): Operation = _ => operation

  /** Use the message type as the trace operation name. */
  def messageClassIsOperation: Operation = _.getClass.getName

  /** Use the actor name as the trace operation name. */
  def actorNameIsOperation(ref: ActorRef): Operation = constantOperation(ref.path.name)

  /** Akka messages are one-way, so by default references to the received context are
    * `FOLLOWS_FROM` rather than `CHILD_OF` */
  def follows(state: Spanned, reference: String = References.FOLLOWS_FROM): Modifier =
    (b: SpanBuilder, m: Any) => m match {
      // The type parameter to Carrier is erased, so match on Carrier then match on the trace parameter
      case c: Carrier[_]#Traceable =>
        // Extract the SpanContext from the payload
        (c.trace match {
          case b: Array[Byte] => BinaryCarrier.extract(state.tracer, b)
          // The type parameters to Map are erased, but the Carrier trait is sealed and there's only one Map trace
          case m: Map[String, String]@unchecked => TextMapCarrier.extract(state.tracer, m)
        }) match {
          case Success(s) => b.addReference(reference, s)
          case Failure(_) => b
        }
      case _ => b
    }

  /** Add a "component" span tag indicating the framework is "akka" */
  val tagAkkaComponent: Modifier =
    (b: SpanBuilder, _) => b.withTag("component", "akka")

  /** Add an "akka.uri" span tag containing the actor address. */
  def tagActorUri(ref: ActorRef): Modifier =
    (b: SpanBuilder, _) => b.withTag("akka.uri", ref.path.address.toString)

  /** Add an arbitrary span tag, taking the value from some extract function that accepts the message as input. */
  def tag(name: String, extract: Any => Any): Modifier = (sb: SpanBuilder, m: Any) => extract(m) match {
    case s: String => sb.withTag(name, s)
    case b: Boolean => sb.withTag(name, b)
    case n: Number => sb.withTag(name, n)
    case x => sb.withTag(name, x.toString)
  }

  def timestamp() : Modifier = (sb: SpanBuilder, _) => {
    val now = Instant.now()
    val secs = now.getLong(ChronoField.INSTANT_SECONDS)
    val micros = now.getLong(ChronoField.MICRO_OF_SECOND)
    sb.withStartTimestamp(secs * 1000000 + micros)
  }
}