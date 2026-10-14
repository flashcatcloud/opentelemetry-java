/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.java6;
/** Java6 has no suppressed exceptions. Preserve them when running on Java7+. */
public final class Exceptions {
  public static Throwable[] getSuppressed(Throwable t) {
    try {
      return (Throwable[]) Throwable.class.getMethod("getSuppressed").invoke(t);
    } catch (NoSuchMethodException e) {
      return new Throwable[0];
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read suppressed exceptions", e);
    }
  }
  private Exceptions() {}
}
