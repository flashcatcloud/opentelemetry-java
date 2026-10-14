#!/usr/bin/env python3
"""Build a Java6 source variant without rewriting the upstream source tree."""
from pathlib import Path
import difflib
import re
import shutil
root = Path(__file__).resolve().parents[1]
b = root / 'java6-compat/build'
src = b / 'src'
if src.exists(): shutil.rmtree(src)
modules = ['common', 'context', 'api/all', 'sdk/common', 'sdk/trace', 'sdk/trace-shaded-deps']
files = [p for m in modules for p in (root / m / 'src/main/java').rglob('*.java')]
files += [p for m in ['trace', 'propagation'] for p in (root / 'api/incubator/src/main/java/io/opentelemetry/api/incubator' / m).rglob('*.java')]
patch = []
for p in files:
    original = p.read_text()
    s = original
    for a, c in [('java.util.function.', 'java8.util.function.'), ('java.util.stream.', 'java8.util.stream.'),
                 ('java.time.', 'org.threeten.bp.'), ('java.util.Objects', 'java8.util.Objects'),
                 ('java.util.StringJoiner', 'java8.util.StringJoiner'),
                 ('java.util.concurrent.ThreadLocalRandom', 'java8.util.concurrent.ThreadLocalRandom'),
                 ('java.nio.charset.StandardCharsets', 'io.opentelemetry.java6.StandardCharsets'),
                 ('import java.util.Base64;', 'import io.opentelemetry.java6.Base64;')]:
        s = s.replace(a, c)
    s = re.sub(r'\bAutoCloseable\b', 'java.io.Closeable', s)
    s = re.sub(r'(\b\w+(?:\.\w+|\(\))*)\.stream\(\)', r'java8.util.stream.StreamSupport.stream(\1)', s)
    s = re.sub(r'([\w.]+)\.computeIfAbsent\(', r'java8.util.Maps.computeIfAbsent(\1, ', s)
    s = s.replace('nextIdCounters\n                    .computeIfAbsent(', 'java8.util.Maps.computeIfAbsent(nextIdCounters, ')
    s = s.replace('fields.spliterator()', 'java8.lang.Iterables.spliterator(fields)')
    s = s.replace('m.forEach(this::put)', 'java8.util.Maps.forEach(m, this::put)')
    s = s.replace('componentLoader.load(spiClass).forEach(result::add)', 'java8.lang.Iterables.forEach(componentLoader.load(spiClass), result::add)')
    s = s.replace('this.tracerSdkComponentRegistry\n        .getComponents()\n        .forEach(', 'java8.lang.Iterables.forEach(this.tracerSdkComponentRegistry.getComponents(), ')
    s = re.sub(r'(\w+)\.getSuppressed\(\)', r'io.opentelemetry.java6.Exceptions.getSuppressed(\1)', s)
    s = s.replace('Comparator.comparing(', 'java8.util.Comparators.comparing(').replace('Comparator.naturalOrder()', 'java8.util.Comparators.naturalOrder()')
    s = s.replace('Collections.emptyIterator()', 'Collections.<String>emptyList().iterator()')
    s = s.replace('Long.hashCode(', 'java8.lang.Longs.hashCode(').replace('Double.hashCode(', 'java8.lang.Doubles.hashCode(')
    s = s.replace('includedPredicate(this.included, globMatchingEnabled)\n              .and(excludedPredicate(this.excluded, globMatchingEnabled))', 'java8.util.function.Predicates.and(includedPredicate(this.included, globMatchingEnabled), excludedPredicate(this.excluded, globMatchingEnabled))')
    s = s.replace('result.or(', 'java8.util.function.Predicates.or(result, ').replace('result.and(', 'java8.util.function.Predicates.and(result, ')
    s = s.replace('createGlobPatternPredicate(exclude).negate()', 'java8.util.function.Predicates.negate(createGlobPatternPredicate(exclude))')
    s = s.replace('java8.util.Maps.computeIfAbsent', 'io.opentelemetry.java6.Maps.computeIfAbsent')
    s = s.replace('Boolean.hashCode(value)', '(value ? 1231 : 1237)')
    s = s.replace('System.lineSeparator()', 'System.getProperty("line.separator")')
    name = str(p).split('/src/main/java/', 1)[1]
    dest = src / name; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_text(s)
    if s != original:
        patch.extend(difflib.unified_diff(original.splitlines(True), s.splitlines(True), fromfile=str(p.relative_to(root)), tofile='generated/'+name))
(b/'adaptation.patch').write_text(''.join(patch))
version=src/'io/opentelemetry/sdk/common/internal/OtelVersion.java'
version.write_text('package io.opentelemetry.sdk.common.internal; public final class OtelVersion { public static final String VERSION="1.66.0-java6-dev"; private OtelVersion() {} }\n')
for p in (root/'java6-compat/src').rglob('*.java'):
    dest=src/p.relative_to(root/'java6-compat/src');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
print('Prepared',len(files),'upstream sources')
