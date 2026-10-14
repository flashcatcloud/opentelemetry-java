# Java 6 tracing SDK variant

This directory builds a **separate, experimental Java 6 artifact from the v1.66.0 SDK sources**. The upstream production sources and Gradle build are not rewritten. This is a working tracing SDK variant, not a replacement test SDK: tests use the real `SdkTracerProvider`, `SdkSpan`, W3C propagators, `SimpleSpanProcessor`, and `BatchSpanProcessor`. Only the acceptance exporter records spans in memory.

Included source modules: `common`, `context`, `api/all`, `sdk/common`, `sdk/trace`, `sdk/trace-shaded-deps`, and the tracing/propagation packages of `api/incubator` (276 upstream source files). The metrics/logs **APIs** are part of `api/all`; their SDKs are not included. Autoconfiguration, OTLP, instrumentation and Agent integration are outside this artifact.

## Reproduce

Requires Python 3, Docker, Maven Central access, and an existing licensed Oracle JDK 6u45 Linux amd64 installation. Run from the repository root:

```sh
python3 java6-compat/verify.py --jdk6 /absolute/path/jdk1.6.0_45
```

The command downloads dependencies pinned by SHA256, generates adapted sources, compiles under Java8, backports bytecode with Retrolambda, checks links against the supplied Oracle6 `rt.jar`, packages artifacts, and runs the behavioral and interop matrix. Docker images used: `eclipse-temurin:8-jdk` and `debian:bookworm-slim`, explicitly `linux/amd64`. Their tags are not immutable; the logs record the tested JDK versions. Existing service containers are never used or restarted.

Outputs under ignored `java6-compat/build/`:

- `dist/opentelemetry-trace-java6.jar`: SDK variant; `dist/acceptance-tests.jar`: separate tests.
- Four runtime dependency jars: streamsupport1.7.4, ThreeTenBP1.6.8, Commons Codec1.10, JCTools4.0.7.
- `adaptation.patch`: reviewable generated-source diff.
- `results.json`: commands, exit codes and timings; `logs/`: individual command output.
- `8-6-8-*.properties` / `6-8-6-*.properties`: actual cross-process carrier and exported span IDs.

AutoValue1.11.1, Error Prone annotations2.50.0 and JCTools4.0.7 match this upstream tag. Retrolambda and annotation processors are build-only. Commons Codec1.15 was rejected by the real-Java6 API test because its class version is51; the pinned1.10 encoder has class version50. All runtime jars retain their own license notices.

## Compatibility changes

`prepare.py` generates a variant that uses streamsupport for Java8 functional/collection APIs, ThreeTenBP for time types, explicit charset/Base64 helpers, Java6 `Closeable`, and an atomic map adapter for registry factories. Retrolambda handles lambdas, static/default interface methods and try-with-resources. Generated class versions are asserted to be50. All base runtime dependency classes must be <=50; module descriptors and multi-release entries are excluded from this check because Java6 does not load them as base classes.

`check_jdk6.py` checks method/field references from **all emitted SDK and test classes** against actual Oracle6 `rt.jar`. It is not a complete API/ABI verifier: reflection, dependency-internal guarded branches, all class descriptors and access checks still require targeted validation. This is complemented by executing with `-Xverify:all` on Java6.

Upstream source language remains Java8. The output's function/time signatures and default-method ABI differ from the official Java8 binaries. **Do not mix this artifact with an unmodified official Agent/SDK at runtime.** Future instrumentation, helpers and API consumers must use the same compatibility variant and be transformed together; Retrolambda documents this closed-world requirement for default methods. Java8 wire interoperability is tested; Java8 binary compatibility is not claimed.

The map adapter serializes creation for internal maps to preserve factory side effects and registry identity. The suppressed-exception adapter returns no suppressed exceptions on Java6, where the JDK has no such feature; it retains them reflectively on newer JDKs. Classpath isolation, shading, full concurrency/performance and complete upstream test-suite execution remain necessary before a production Agent release.

## Verified behavior

On real Oracle `1.6.0_45`, with normal and strict Context storage, and on Java8 both with this variant and unmodified official1.66.0 artifacts:

- Real exported server/client/async/downstream spans, parent IDs and remote trace ID.
- W3C traceparent/tracestate and baggage injection/extraction; malformed-parent rejection.
- Nested Scope restoration and no Context leak in a reused executor thread.
- Attributes, events, exception recording, error status and timestamps.
- Batch export100 spans, forceFlush, plus shutdown draining the101st span.
- Parent-based unsampled decision, API primitive/byte values, attribute/event limits, registry identity under concurrent lookups and exporter failure recovery.

Separate JVMs also verify unmodified Java8 SDK → Java6 variant → unmodified Java8 SDK and Java6 → Java8 → Java6. Properties files carry the headers between processes; this tests the W3C propagators, **not HTTP auto-instrumentation or a network exporter**.

References: [Retrolambda](https://github.com/luontola/retrolambda), [streamsupport](https://github.com/streamsupport/streamsupport), [ThreeTenBP](https://github.com/ThreeTen/threetenbp).
