package io.opentracing.contrib.akka

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import io.opentracing.{References, Span, SpanContext, Tracer}
import Spanned.Modifier

import scala.util.{Failure, Success, Try}

/** Decorator to add an OpenTracing span around an Actor's Receive
  * @param self source of the tracer and parent span
  * @param r the [[Receive]] function to trace
  * @param modifiers applied to the child span
  */
class TracingReceive(self: Spanned,
                     r: Receive,
                     modifiers: Spanned.Modifier*)
  extends Receive {

  val extractor: Any ⇒ Try[SpanContext] = TracingReceive.context(self.tracer)

  /** Proxies `r.isDefinedAt`
    * @param x @inheritDoc
    * @return @inheritDoc
    */
  override def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)

  /** Invokes `r.apply` in the context of a new `Span` held by `state`.  The span is built,
    * started, and finished by this wrapper using manual span propagation. Span activation
    * using a `Scope` is not necessary because the Akka programming model guarantees
    * single threaded execution of `apply`.
    * @param v1 @inheritDoc
    */
  override def apply(v1: Any): Unit = {
    val sb = modifiers.foldLeft(self.tracer.buildSpan(self.operation()))((sb, m) ⇒ m(sb))
    extractor(v1) match {
      case Success(sc) ⇒
        self.span = sb.addReference(References.FOLLOWS_FROM, sc).start()
      case Failure(e) ⇒
        self.span = sb.start()
        self.span.log(e.getLocalizedMessage)
    }
    Try(r(v1)) match {
      case Success(_) ⇒
        self.span.finish()
        self.span_=(null)
      case Failure(e) ⇒
        error(self.span, e)
        self.span.finish()
        self.span_=(null)
        throw e
    }
  }

  /** Tag and log an exception thrown by `apply`
    * @param span the span that produced the error
    * @param e the error
    */
  def error(span: Span, e: Throwable): Unit = {
    val time = Spanned.micros()
    span.setTag("error", true)
    val fields: java.util.Map[String, Any] = new java.util.HashMap[String, Any]
    fields.put("event", "error")
    fields.put("error.object", e)
    span.log(time, fields)
  }
}

object TracingReceive {

  /** Default modifiers to apply to a span surrounding a Receive. */
  val defaultModifiers = Seq(ActorTracing.tagAkkaComponent, Spanned.tagConsumer, Spanned.timestamp())

  /** Tracing receive that uses the supplied modifiers */
  def apply(state: Spanned, modifiers: Modifier*)(r: Receive): Receive =
    new TracingReceive(state, r, modifiers: _*)

  /** Tracing receive that uses the default modifiers */
  def apply(state: Spanned)(r: Receive): Receive =
    apply(state, defaultModifiers: _*)(r)

  /** Tracing receive that uses the default modifiers and adds an "akka.uri" tag containing the actor address */
  def apply(state: Spanned, ref: ActorRef)(r: Receive): Receive = {
    apply(state, defaultModifiers :+ ActorTracing.tagActorUri(ref): _*)(r)
  }

  /**
    * Default method to extract a [[SpanContext]] from a message.
    * @param tracer the tracer that encoded the context
    * @param message the message containing the encoded context
    * @return the result of extracting the context from the message
    */
  def context(tracer: Tracer)(message: Any): Try[SpanContext] = message match {
    case c: Carrier[_]#Traceable ⇒ c.context(tracer)
    case _ ⇒ Failure(new IllegalArgumentException(s"Message type ${message.getClass} is not a carrier for SpanContext"))
  }
}
