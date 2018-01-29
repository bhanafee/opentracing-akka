package io.opentracing.contrib.akka

import akka.actor.Actor.Receive
import io.opentracing.{References, SpanContext, Tracer}
import Spanned.Modifier

import scala.util.{Failure, Success, Try}

/** Decorator to add an OpenTracing span around an Actor's Receive
  * @param actor source of the tracer and parent span
  * @param r the [[Receive]] function to trace
  */
class TracingReceive(actor: ActorTracing, r: Receive)
  extends Receive {

  val extractor: Any ⇒ Try[SpanContext] = TracingReceive.context(actor.tracer)

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
    val sb = receiveModifiers.foldLeft(actor.tracer.buildSpan(actor.operation()))((sb, m) ⇒ m(sb))
    extractor(v1) match {
      case Success(sc) ⇒
        actor.span = sb.addReference(References.FOLLOWS_FROM, sc).start()
      case Failure(e) ⇒
        actor.span = sb.start()
        actor.span.log(e.getLocalizedMessage)
    }
    Try(r(v1)) match {
      case Success(_) ⇒
        actor.span.finish()
        actor.span_=(null)
      case Failure(e) ⇒
        Spanned.error(actor.span, e)
        actor.span.finish()
        actor.span_=(null)
        throw e
    }
  }

  def receiveModifiers: Seq[Modifier] = Seq(
    Spanned.tagConsumer,
    ActorTracing.tagAkkaComponent,
    ActorTracing.tagAkkaUri(actor.self),
    Spanned.timestamp())
}

object TracingReceive {

  def apply(actor: ActorTracing)(r: Receive): Receive = new TracingReceive(actor, r)

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
