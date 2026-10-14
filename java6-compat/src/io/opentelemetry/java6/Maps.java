/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.java6;
import java.util.Map;
import java8.util.function.Function;
/** Internal SDK maps use this adapter for atomic creation, including factory side effects. */
public final class Maps {
  public static <K,V> V computeIfAbsent(Map<K,V> map, K key, Function<? super K,? extends V> factory) {
    synchronized (map) {
      V value = map.get(key);
      if (value == null) {
        value = factory.apply(key);
        if (value != null) { map.put(key, value); }
      }
      return value;
    }
  }
  private Maps() {}
}
