/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.*;
import java.util.*;
/** Separate-process peer for unmodified Java8 SDK <-> backported Java6 SDK propagation. */
public final class PropagationPeer {
  public static void main(String[] args) throws Exception {
    Properties input = new Properties();
    FileInputStream in = new FileInputStream(args[0]);
    try { input.load(in); } finally { in.close(); }
    Map<String,String> headers = new HashMap<String,String>();
    for (String key : input.stringPropertyNames()) { headers.put(key, input.getProperty(key)); }
    Context parent = SdkCompatibilityTest.PROP.extract(Context.root(), headers, SdkCompatibilityTest.GETTER);
    SpanContext remote = Span.fromContext(parent).getSpanContext();
    SdkCompatibilityTest.check(remote.isValid() && remote.isRemote(), "not a remote W3C parent");
    SdkCompatibilityTest.RecordingExporter exporter = new SdkCompatibilityTest.RecordingExporter();
    SdkTracerProvider provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    Span span = provider.get("peer").spanBuilder("peer").setSpanKind(SpanKind.SERVER).setParent(parent).startSpan();
    Map<String,String> output = new HashMap<String,String>();
    SdkCompatibilityTest.PROP.inject(parent.with(span), output, SdkCompatibilityTest.SETTER);
    span.end(); SdkCompatibilityTest.done(provider.shutdown());
    SpanData data = SdkCompatibilityTest.find(exporter, "peer");
    SdkCompatibilityTest.check(data.getTraceId().equals(remote.getTraceId()), "changed trace ID");
    SdkCompatibilityTest.check(data.getParentSpanId().equals(remote.getSpanId()), "changed parent ID");
    Properties result = new Properties(); result.putAll(output);
    result.setProperty("observed.parent", data.getParentSpanId());
    result.setProperty("observed.span", data.getSpanId());
    result.setProperty("observed.trace", data.getTraceId());
    result.setProperty("observed.java", System.getProperty("java.version"));
    FileOutputStream out = new FileOutputStream(args[1]);
    try { result.store(out, "actual exported span and propagated headers"); } finally { out.close(); }
    System.out.println("PASS peer java=" + System.getProperty("java.version") + " trace=" + data.getTraceId()
        + " parent=" + data.getParentSpanId() + " span=" + data.getSpanId());
  }
}
