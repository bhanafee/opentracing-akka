package io.opentracing.contrib.akka

import java.util.{Iterator => JIterator, Map => JMap}

import io.opentracing.propagation.Format.Builtin.TEXT_MAP
import io.opentracing.propagation.TextMap
import io.opentracing.{SpanContext, Tracer}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/** Adapts the `TEXT_MAP` format to an immutable `Map[String, String]` payload */
object TextMapCarrier extends Carrier[Map[String, String]] {

  /** Stub TextMap implementation so `generate` and `extract` only need the functional parts. */
  private class TextMapAdapter extends TextMap {
    override def iterator(): JIterator[JMap.Entry[String, String]] =
      throw new UnsupportedOperationException("Tried to read value from empty carrier")

    override def put(key: String, value: String): Unit =
      throw new UnsupportedOperationException("Tried to put value to immutable carrier")
  }

  override def inject(t: Tracer)(c: SpanContext): Payload = {
    var kvs: List[(String, String)] = List.empty
    t.inject(c, TEXT_MAP, new TextMapAdapter {
      override def put(key: String, value: String): Unit = kvs = (key, value) :: kvs
    })
    Map(kvs: _*)
  }

  override def extract(t: Tracer)(p: Payload): Try[SpanContext] =
    if (p.isEmpty) Failure(new NoSuchElementException("Empty payload"))
    else {
      Try(t.extract(TEXT_MAP, new TextMapAdapter {
        override def iterator(): JIterator[JMap.Entry[String, String]] =
          p.asJava.entrySet().iterator()
      })) match {
        case Success(null) =>  Failure(new NullPointerException("Tracer.extract returned null"))
        case x => x
      }
    }

}
