/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.java6;
import java.nio.charset.Charset;
/** Java6 charset constants; no default-charset dependency. */
public final class StandardCharsets {
  public static final Charset UTF_8 = Charset.forName("UTF-8");
  public static final Charset US_ASCII = Charset.forName("US-ASCII");
  public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
  private StandardCharsets() {}
}
