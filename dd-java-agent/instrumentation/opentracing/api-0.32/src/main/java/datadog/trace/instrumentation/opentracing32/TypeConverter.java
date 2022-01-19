package datadog.trace.instrumentation.opentracing32;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import datadog.trace.instrumentation.opentracing.LogHandler;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;

// Centralized place to do conversions
public class TypeConverter {
  // TODO maybe add caching to reduce new objects being created

  private final LogHandler logHandler;

  public TypeConverter(final LogHandler logHandler) {
    this.logHandler = logHandler;
  }

  public AgentSpan toAgentSpan(final Span span) {
    if (span instanceof OTSpan) {
      return ((OTSpan) span).getDelegate();
    }
    return null == span ? null : AgentTracer.NoopAgentSpan.INSTANCE;
  }

  public OTSpan toSpan(final AgentSpan agentSpan) {
    if (agentSpan == null) {
      return null;
    }
    // check if a wrapper has already been created and attached to the agent span
    Object wrapper = agentSpan.getWrapper();
    if (wrapper instanceof OTSpan) {
      return (OTSpan) wrapper;
    }
    OTSpan otSpan = new OTSpan(agentSpan, this, logHandler);
    agentSpan.attachWrapper(otSpan);
    return otSpan;
  }

  public Scope toScope(final AgentScope scope, final boolean finishSpanOnClose) {
    if (scope == null) {
      return null;
    }
    return new OTScopeManager.OTScope(scope, finishSpanOnClose, this);
  }

  public SpanContext toSpanContext(final AgentSpan.Context context) {
    if (context == null) {
      return null;
    }
    // check if a wrapper has already been created and attached to the agent span context
    Object wrapper = context.getWrapper();
    if (wrapper instanceof OTSpanContext) {
      return (OTSpanContext) wrapper;
    }
    OTSpanContext otSpanContext = new OTSpanContext(context);
    context.attachWrapper(otSpanContext);
    return otSpanContext;
  }

  public AgentSpan.Context toContext(final SpanContext spanContext) {
    if (spanContext instanceof OTSpanContext) {
      return ((OTSpanContext) spanContext).getDelegate();
    }
    return null == spanContext ? null : AgentTracer.NoopContext.INSTANCE;
  }
}
