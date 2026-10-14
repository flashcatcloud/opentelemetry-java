/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.java6;
/** RFC4648 standard padded Base64, without line breaks. */
public final class Base64 {
  private static final Base64 ENCODER = new Base64();
  public static Base64 getEncoder() { return ENCODER; }
  public String encodeToString(byte[] value) {
    return org.apache.commons.codec.binary.Base64.encodeBase64String(value);
  }
  private Base64() {}
}
