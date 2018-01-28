package io.opentracing.contrib.akka

import akka.actor.{Actor, ActorRef}
import Spanned.Modifier
import io.opentracing.Span

/** Adds Akka-specific behavior to [[Spanned]]. */
trait ActorTracing extends Actor with Spanned {

  /** Default is actor path. */
  override def operation(): String = self.path.name

  /** Adds `component = akka` tag to the superclass implementation. */
  override def trace(op: String, modifiers: Modifier*)(body: Span ⇒ Unit): Unit =
    super.trace(op, ActorTracing.tagAkkaComponent +: Spanned.tagProducer +: modifiers: _*)(body)

  /** Adds `akka.uri` and `component = akka` tags to the superclass implementation. */
  def trace(ref: ActorRef, op: String, modifiers: Modifier*)(body: Span ⇒ Unit): Unit =
    trace(op, ActorTracing.tagActorUri(ref) +: modifiers: _*)(body)
}

object ActorTracing {

  /** Add a `component = akka` span tag */
  val tagAkkaComponent: Modifier = _.withTag("component", "akka")

  /** Add an `akka.uri` span tag containing the actor address */
  def tagActorUri(ref: ActorRef): Modifier = _.withTag("akka.uri", ref.path.address.toString)
}
