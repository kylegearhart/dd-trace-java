package datadog.cws.tls;

import datadog.trace.api.DDId;

final class Span {

  private final DDId traceId;
  private final DDId spanId;

  Span(DDId traceId, DDId spanId) {
    System.out.println("HERE IN Span ctor");
    this.traceId = traceId;
    this.spanId = spanId;
  }

  DDId getTraceId() {
    return traceId;
  }

  DDId getSpanId() {
    return spanId;
  }
}
