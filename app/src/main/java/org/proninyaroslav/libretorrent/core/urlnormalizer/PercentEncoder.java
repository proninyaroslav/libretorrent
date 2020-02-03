package org.proninyaroslav.libretorrent.core.urlnormalizer;

import com.anthonynsimon.url.exceptions.InvalidHexException;
import com.anthonynsimon.url.exceptions.MalformedURLException;

/**
 * PercentEncoder handles the escaping and unescaping of characters in URLs.
 * It escapes character based on the context (part of the URL) that is being dealt with.
 * <p>
 * Supports UTF-8 escaping and unescaping.
 */
final class PercentEncoder {
    /**
     * Byte masks to aid in the decoding of UTF-8 byte arrays.
     */
    private static final short[] utf8Masks = new short[]{0b00000000, 0b11000000, 0b11100000, 0b11110000};

    /**
     * Disallow instantiation of class.
     */
    private PercentEncoder() {
    }

    private static boolean needsUnescaping(String str) {
        return (str.indexOf('%') >= 0);
    }

    /**
     * Returns an unescaped string.
     *
     * @throws MalformedURLException if an invalid escape sequence is found.
     */
    public static String decode(String str) throws MalformedURLException {
        // The string might not need unescaping at all, check first.
        if (!needsUnescaping(str)) {
            return str;
        }

        char[] chars = str.toCharArray();
        StringBuilder result = new StringBuilder();
        int len = str.length();
        int i = 0;
        while (i < chars.length) {
            char c = chars[i];
            if (c != '%') {
                result.append(c);
                i++;
            } else {
                if (i + 2 >= len) {
                    throw new MalformedURLException("invalid escape sequence");
                }
                byte code;
                try {
                    code = unhex(str.substring(i + 1, i + 3).toCharArray());
                } catch (InvalidHexException e) {
                    throw new MalformedURLException(e.getMessage());
                }
                int readBytes = 0;
                for (short mask : utf8Masks) {
                    if ((code & mask) == mask) {
                        readBytes++;
                    } else {
                        break;
                    }
                }
                byte[] buffer = new byte[readBytes];
                for (int j = 0; j < readBytes; j++) {
                    if (str.charAt(i) != '%') {
                        byte[] currentBuffer = new byte[j];
                        System.arraycopy(buffer, 0, currentBuffer, 0, j);
                        buffer = currentBuffer;
                        break;
                    }
                    if (i + 3 > len) {
                        buffer = "\uFFFD".getBytes();
                        break;
                    }
                    try {
                        buffer[j] = unhex(str.substring(i + 1, i + 3).toCharArray());
                    } catch (InvalidHexException e) {
                        throw new MalformedURLException(e.getMessage());
                    }
                    i += 3;
                }
                result.append(new String(buffer));
            }
        }
        return result.toString();
    }

    /**
     * Returns a byte representation of a parsed array of hex chars.
     *
     * @throws InvalidHexException if the provided array of hex characters is invalid.
     */
    private static byte unhex(char[] hex) throws InvalidHexException {
        int result = 0;
        for (int i = 0; i < hex.length; i++) {
            char c = hex[hex.length - i - 1];
            int index = -1;
            if ('0' <= c && c <= '9') {
                index = c - '0';
            } else if ('a' <= c && c <= 'f') {
                index = c - 'a' + 10;
            } else if ('A' <= c && c <= 'F') {
                index = c - 'A' + 10;
            }
            if (index < 0 || index >= 16) {
                throw new InvalidHexException("not a valid hex char: " + c);
            }
            result += index * pow(16, i);
        }
        return (byte) result;
    }

    private static int pow(int base, int exp) {
        int result = 1;
        int expRemaining = exp;
        while (expRemaining > 0) {
            result *= base;
            expRemaining--;
        }
        return result;
    }
}
