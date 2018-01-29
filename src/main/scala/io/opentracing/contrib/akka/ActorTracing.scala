package io.opentracing.contrib.akka

import akka.actor.{Actor, ActorRef}
import Spanned.Modifier

/** Adds Akka-specific behavior to [[Spanned]]. */
trait ActorTracing extends Actor with Spanned {

  /** Default is actor path. */
  override def operation(): String = self.path.name

  override def traceModifiers: Seq[Modifier] = Seq(
    Spanned.tagProducer,
    Spanned.follows(span),
    ActorTracing.tagAkkaComponent)
}

object ActorTracing {

  /** Add a `component = akka` span tag */
  val tagAkkaComponent: Modifier = _.withTag("component", "akka")

  def tagAkkaUri(ref: ActorRef): Modifier = _.withTag("akka.uri", ref.path.address.toString)
}
