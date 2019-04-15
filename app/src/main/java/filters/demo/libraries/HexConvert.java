package filters.demo.libraries;

/**
 * Convert bytes to hex string.
 */
final class HexConvert {
  private static final char[] HEX_DIGITS_ARRAY = "0123456789ABCDEF".toCharArray();
  // Utility class
  private HexConvert() { }
  /**
   * Converts the given byte array to a hex-encoded String.
   *
   * <p>If the byte array is null or empty, returns an empty String.
   */
  static String bytesToHex(byte[] bytes) {
    if (bytes == null) {
      return "";
    }
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_DIGITS_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_DIGITS_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }
}