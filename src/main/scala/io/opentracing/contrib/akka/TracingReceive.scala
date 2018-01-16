package io.opentracing.contrib.akka

import scala.util.{Failure, Success, Try}
import akka.actor.Actor.Receive
import akka.actor.ActorRef
import io.opentracing.Tracer.SpanBuilder
import io.opentracing.Span

/** Decorator to add an OpenTracing Span around an Actor's Receive */
class TracingReceive(r: Receive,
                     state: Spanned,
                     operation: TracingReceive.Operation,
                     modifiers: SpanModifiers.Modifier*)
  extends Receive {

  /** Proxies `r.isDefinedAt` */
  override def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)

  /** Invokes `r.apply` in the context of a new `Span` held by `state`.  The span is built,
    * started, and finished by this wrapper using manual span propagation. Span activation
    * using a `Scope` is not necessary because the Akka programming model guarantees
    * single threaded execution of `apply`. */
  override def apply(v1: Any): Unit = {
    val z: SpanBuilder = state.tracer.buildSpan(operation(v1))
    val op: (SpanBuilder, SpanModifiers.Modifier) => SpanBuilder = (sb, m) => m(sb, v1)
    val sb = modifiers.foldLeft(z)(op)
    state.span = sb.start()
    Try(r(v1)) match {
      case Success(_) =>
        state.span.finish()
        state.span_=(null)
      case Failure(e) =>
        error(state.span, e)
        state.span.finish()
        state.span_=(null)
        throw e
    }
  }

  /** Tag and log an exception thrown by `apply` */
  def error(span: Span, e: Throwable, micros: => Long = SpanModifiers.micros()): Unit = {
    val time = micros
    span.setTag("error", true)
    val fields: java.util.Map[String, Any] = new java.util.HashMap[String, Any]
    fields.put("event", "error")
    fields.put("error.object", e)
    span.log(time, fields)
  }
}

object TracingReceive {
  import SpanModifiers._
  /** Used to specify the span's operation name */
  type Operation = Any => String

  def apply(state: Spanned, operation: Operation, modifiers: Modifier*)(r: Receive): Receive =
    new TracingReceive(r, state, operation, modifiers: _*)

  /** Tracing receive that
    * - uses the message type as the operation name
    * - includes a FOLLOWS_FROM reference to any span found in the message
    * - tags the "component" as "akka"
    */
  def apply(state: Spanned)(r: Receive): Receive =
    new TracingReceive(r, state, messageClassIsOperation, tagAkkaComponent, follows(state.tracer), timestamp())

  /** Tracing receive that
    * - uses the message type as the operation name
    * - includes a FOLLOWS_FROM reference to any span found in the message
    * - tags the "component" as "akka"
    * - tags the "akka.uri" as the actor address
    */
  def apply(state: Spanned, ref: ActorRef)(r: Receive) =
    new TracingReceive(r, state, messageClassIsOperation, tagAkkaComponent, follows(state.tracer), tagActorUri(ref), timestamp())

  /** Use a constant operation name */
  def constantOperation(operation: String): Operation = _ => operation

  /** Use the message type as the trace operation name */
  val messageClassIsOperation: Operation = _.getClass.getName

  /** Use the actor name as the trace operation name */
  def actorNameIsOperation(ref: ActorRef): Operation = constantOperation(ref.path.name)
}
