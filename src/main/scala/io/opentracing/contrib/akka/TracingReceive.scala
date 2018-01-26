package io.opentracing.contrib.akka

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import io.opentracing.{References, Span, SpanContext, Tracer}
import Spanned.Modifier

import scala.util.{Failure, Success, Try}

/** Decorator to add an OpenTracing Span around an Actor's Receive */
class TracingReceive(state: Spanned,
                     r: Receive,
                     modifiers: Spanned.Modifier*)
  extends Receive {

  /** Proxies `r.isDefinedAt` */
  override def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)

  /** Invokes `r.apply` in the context of a new `Span` held by `state`.  The span is built,
    * started, and finished by this wrapper using manual span propagation. Span activation
    * using a `Scope` is not necessary because the Akka programming model guarantees
    * single threaded execution of `apply`. */
  override def apply(v1: Any): Unit = {
    val sb = modifiers.foldLeft(state.tracer.buildSpan(state.operation()))((sb, m) ⇒ m(sb))
    context(state.tracer)(v1) match {
      case Success(sc) ⇒
        state.span = sb.addReference(References.FOLLOWS_FROM, sc).start()
      case Failure(e) ⇒
        state.span = sb.start()
        state.span.log(e.getLocalizedMessage)
    }
    Try(r(v1)) match {
      case Success(_) ⇒
        state.span.finish()
        state.span_=(null)
      case Failure(e) ⇒
        error(state.span, e)
        state.span.finish()
        state.span_=(null)
        throw e
    }
  }

  /** Tag and log an exception thrown by `apply` */
  def error(span: Span, e: Throwable, micros: ⇒ Long = Spanned.micros()): Unit = {
    val time = micros
    span.setTag("error", true)
    val fields: java.util.Map[String, Any] = new java.util.HashMap[String, Any]
    fields.put("event", "error")
    fields.put("error.object", e)
    span.log(time, fields)
  }

  def context(tracer: Tracer)(message: Any): Try[SpanContext] = message match {
    case c: Carrier[_]#Traceable ⇒ c.context(tracer)
    case _ ⇒ Failure(new IllegalArgumentException(s"Message type ${message.getClass} is not a carrier for SpanContext"))
  }
}

object TracingReceive {

  /** Default modifiers to apply to a span surrounding a Receive. */
  val defaultModifiers = Seq(Spanned.tagAkkaComponent, Spanned.tagConsumer, Spanned.timestamp())

  /** Tracing receive that uses the supplied modifiers */
  def apply(state: Spanned, modifiers: Modifier*)(r: Receive): Receive =
    new TracingReceive(state, r, modifiers: _*)

  /** Tracing receive that uses the default modifiers */
  def apply(state: Spanned)(r: Receive): Receive =
    apply(state, defaultModifiers: _*)(r)

  /** Tracing receive that uses the default modifiers and adds an "akka.uri" tag containing the actor address */
  def apply(state: Spanned, ref: ActorRef)(r: Receive): Receive = {
    apply(state, defaultModifiers :+ Spanned.tagActorUri(ref): _*)(r)
  }
}
