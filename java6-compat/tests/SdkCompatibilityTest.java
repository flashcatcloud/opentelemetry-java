/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.*;
import io.opentelemetry.context.propagation.*;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.*;
import java.util.concurrent.*;

/** Behavioral acceptance test, compiled with the same default-method backport as the SDK. */
public final class SdkCompatibilityTest {
  static final TextMapGetter<Map<String,String>> GETTER = new TextMapGetter<Map<String,String>>() {
    public Iterable<String> keys(Map<String,String> c) { return c.keySet(); }
    public String get(Map<String,String> c, String k) { return c.get(k); }
  };
  static final TextMapSetter<Map<String,String>> SETTER = new TextMapSetter<Map<String,String>>() {
    public void set(Map<String,String> c, String k, String v) { c.put(k, v); }
  };
  static final TextMapPropagator PROP = TextMapPropagator.composite(
      W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance());
  static class RecordingExporter implements SpanExporter {
    final List<SpanData> spans = Collections.synchronizedList(new ArrayList<SpanData>());
    volatile boolean closed;
    volatile boolean fail;
    public CompletableResultCode export(Collection<SpanData> batch) {
      if (fail) { return CompletableResultCode.ofFailure(); }
      spans.addAll(batch); return CompletableResultCode.ofSuccess();
    }
    public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
    public CompletableResultCode shutdown() { closed = true; return CompletableResultCode.ofSuccess(); }
  }
  static void check(boolean condition, String message) {
    if (!condition) { throw new AssertionError(message); }
  }
  static void done(CompletableResultCode result) {
    result.join(10, TimeUnit.SECONDS);
    check(result.isSuccess(), "operation failed or timed out");
  }
  static SpanData find(RecordingExporter exporter, String name) {
    for (SpanData data : exporter.spans) { if (data.getName().equals(name)) { return data; } }
    throw new AssertionError("missing span: " + name);
  }
  public static void main(String[] args) throws Exception {
    RecordingExporter exporter = new RecordingExporter();
    SdkTracerProvider provider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    Tracer tracer = provider.get("java6-test", "1");
    Map<String,String> incoming = new HashMap<String,String>();
    incoming.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    incoming.put("tracestate", "vendor=value");
    incoming.put("baggage", "tenant=flashcat");
    Context extracted = PROP.extract(Context.root(), incoming, GETTER);
    check(Span.fromContext(extracted).getSpanContext().isRemote(), "remote context lost");
    check("flashcat".equals(Baggage.fromContext(extracted).getEntryValue("tenant")), "baggage missing");
    Span server = tracer.spanBuilder("server").setSpanKind(SpanKind.SERVER).setParent(extracted).startSpan();
    Map<String,String> outbound = new HashMap<String,String>();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Scope scope = extracted.with(server).makeCurrent();
      try {
        check(Span.current() == server, "current span missing");
        Span client = tracer.spanBuilder("client").setSpanKind(SpanKind.CLIENT).startSpan();
        Scope childScope = client.makeCurrent();
        try {
          PROP.inject(Context.current(), outbound, SETTER);
          check(outbound.get("traceparent").equals("00-" + client.getSpanContext().getTraceId()
              + "-" + client.getSpanContext().getSpanId() + "-01"), "incorrect injected parent ID");
          check("vendor=value".equals(outbound.get("tracestate")), "tracestate lost");
          check(outbound.get("baggage").contains("tenant=flashcat"), "baggage injection failed");
          client.setAttribute("db.system", "mysql");
          client.addEvent("query", Attributes.of(AttributeKey.stringKey("sql"), "SELECT ?"));
          client.recordException(new IllegalStateException("test-error"));
          client.setStatus(StatusCode.ERROR, "test-error");
        } finally { childScope.close(); client.end(); }
        check(Span.current() == server, "nested scope restoration failed");
        executor.submit(Context.current().wrap(new Runnable() {
          public void run() { tracer.spanBuilder("async-child").startSpan().end(); }
        })).get(10, TimeUnit.SECONDS);
        check(!executor.submit(new Callable<Boolean>() {
          public Boolean call() { return Span.current().getSpanContext().isValid(); }
        }).get(10, TimeUnit.SECONDS), "executor context leak");
        Context downstream = PROP.extract(Context.root(), outbound, GETTER);
        tracer.spanBuilder("downstream").setSpanKind(SpanKind.SERVER).setParent(downstream).startSpan().end();
      } finally { scope.close(); server.end(); }
    } finally { executor.shutdownNow(); }
    check(!Span.current().getSpanContext().isValid(), "main thread context leak");
    done(provider.forceFlush());
    SpanData s = find(exporter, "server"), c = find(exporter, "client");
    check(s.getTraceId().equals("4bf92f3577b34da6a3ce929d0e0e4736"), "trace ID changed");
    check(s.getParentSpanId().equals("00f067aa0ba902b7"), "remote parent ID changed");
    check(c.getParentSpanId().equals(s.getSpanId()), "client parent wrong");
    check(find(exporter, "async-child").getParentSpanId().equals(s.getSpanId()), "async parent wrong");
    check(find(exporter, "downstream").getParentSpanId().equals(c.getSpanId()), "downstream parent wrong");
    check(c.getStatus().getStatusCode() == StatusCode.ERROR, "error status missing");
    check("mysql".equals(c.getAttributes().get(AttributeKey.stringKey("db.system"))), "attribute lost");
    check(c.getEvents().size() == 2, "query/exception events lost");
    check(c.getEndEpochNanos() >= c.getStartEpochNanos(), "invalid timestamps");
    check(exporter.spans.size() == 4, "unexpected exported span count");
    Map<String,String> bad = new HashMap<String,String>(); bad.put("traceparent", "00-invalid");
    check(!Span.fromContext(PROP.extract(Context.root(), bad, GETTER)).getSpanContext().isValid(), "malformed parent accepted");
    done(provider.shutdown()); check(exporter.closed, "exporter not closed");
    System.out.println("PASS spans,parent IDs,W3C,baggage,scope restoration,async isolation,attributes,events,status");

    RecordingExporter batch = new RecordingExporter();
    SdkTracerProvider bp = SdkTracerProvider.builder().addSpanProcessor(BatchSpanProcessor.builder(batch)
        .setMaxQueueSize(2048).setMaxExportBatchSize(32).setScheduleDelay(1, TimeUnit.DAYS).build()).build();
    Set<String> ids = new HashSet<String>();
    for (int i = 0; i < 100; i++) {
      Span span = bp.get("batch").spanBuilder("batch-" + i).startSpan();
      check(ids.add(span.getSpanContext().getSpanId()), "duplicate span ID"); span.end();
    }
    done(bp.forceFlush()); check(batch.spans.size() == 100, "batch flush dropped spans");
    bp.get("batch").spanBuilder("shutdown-drain").startSpan().end();
    done(bp.shutdown()); check(batch.spans.size() == 101 && batch.closed, "shutdown did not drain/close");
    System.out.println("PASS batch export=101,forceFlush,shutdown drain,ID generation");

    RecordingExporter dropped = new RecordingExporter();
    SdkTracerProvider sampler = SdkTracerProvider.builder().setSampler(Sampler.parentBased(Sampler.alwaysOn()))
        .addSpanProcessor(SimpleSpanProcessor.create(dropped)).build();
    incoming.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");
    Span unsampled = sampler.get("sampling").spanBuilder("dropped")
        .setParent(PROP.extract(Context.root(), incoming, GETTER)).startSpan();
    check(!unsampled.isRecording() && !unsampled.getSpanContext().isSampled(), "remote sampling flag lost");
    unsampled.end(); done(sampler.shutdown()); check(dropped.spans.isEmpty(), "unsampled span exported");
    System.out.println("PASS parent-based sampling java=" + System.getProperty("java.version"));
    io.opentelemetry.api.common.Value.of(true).hashCode();
    io.opentelemetry.api.common.Value.of(42L).hashCode();
    io.opentelemetry.api.common.Value.of(1.5).hashCode();
    check("AQID".equals(io.opentelemetry.api.common.Value.of(new byte[] {1,2,3}).asString()), "Base64 mismatch");
    RecordingExporter limited = new RecordingExporter();
    SdkTracerProvider lp = SdkTracerProvider.builder()
        .setSpanLimits(SpanLimits.builder().setMaxNumberOfAttributes(1).setMaxNumberOfEvents(1).build())
        .addSpanProcessor(SimpleSpanProcessor.create(limited)).build();
    Span ls = lp.get("limits").spanBuilder("limits").startSpan();
    ls.setAttribute("first", 1L).setAttribute("second", 2L).addEvent("first").addEvent("second"); ls.end();
    SpanData ld = find(limited, "limits");
    check(ld.getAttributes().size() == 1 && ld.getTotalAttributeCount() == 2, "attribute limits broken");
    check(ld.getEvents().size() == 1 && ld.getTotalRecordedEvents() == 2, "event limits broken");
    ExecutorService workers = Executors.newFixedThreadPool(8);
    final Tracer expected = lp.get("registry", "1");
    List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
    try {
      for (int i = 0; i < 32; i++) {
        futures.add(workers.submit(new Callable<Boolean>() {
          public Boolean call() { return lp.get("registry", "1") == expected; }
        }));
      }
      for (Future<Boolean> f : futures) { check(f.get(10, TimeUnit.SECONDS), "registry identity changed"); }
    } finally { workers.shutdownNow(); }
    done(lp.shutdown());
    RecordingExporter failing = new RecordingExporter(); failing.fail = true;
    SdkTracerProvider fp = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(failing)).build();
    fp.get("failure").spanBuilder("not-exported").startSpan().end();
    failing.fail = false;
    fp.get("failure").spanBuilder("recovered").startSpan().end();
    done(fp.shutdown());
    check(failing.spans.size() == 1 && failing.spans.get(0).getName().equals("recovered"), "export failure recovery broken");
    System.out.println("PASS API values,limits,concurrent registry,export failure recovery");

  }
}
