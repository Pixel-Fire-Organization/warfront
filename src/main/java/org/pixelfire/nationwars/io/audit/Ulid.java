package org.pixelfire.nationwars.io.audit;

import java.security.SecureRandom;

/**
 * A minimal ULID generator (<a href="https://github.com/ulid/spec">ulid/spec</a>): 48 bits of
 * millisecond timestamp followed by 80 bits of randomness, Crockford Base32 encoded to a 26-character
 * string that sorts lexicographically the same way it sorts by creation time.
 */
public final class Ulid
{
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TIMESTAMP_CHARS = 10;
    private static final int RANDOM_CHARS = 16;
    private static final int LENGTH = TIMESTAMP_CHARS + RANDOM_CHARS;
    private static final long MAX_TIMESTAMP = (1L << 48) - 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid()
    {
    }

    public static String generate()
    {
        return generate(System.currentTimeMillis());
    }

    static String generate(final long timestampMillis)
    {
        if (timestampMillis < 0 || timestampMillis > MAX_TIMESTAMP)
        {
            throw new IllegalArgumentException("timestamp out of range for a 48-bit ULID timestamp: " + timestampMillis);
        }

        final char[] chars = new char[LENGTH];
        encodeTimestamp(timestampMillis, chars);
        encodeRandomness(randomBytes(), chars);
        return new String(chars);
    }

    /**
     * The millisecond timestamp encoded in a ULID's first 10 characters, for tests and for locating
     * which day's log file an entry belongs to.
     */
    public static long timestampMillis(final String ulid)
    {
        if (ulid.length() != LENGTH)
        {
            throw new IllegalArgumentException("not a ULID (expected " + LENGTH + " characters): " + ulid);
        }
        long timestamp = 0;
        for (int i = 0; i < TIMESTAMP_CHARS; i++)
        {
            timestamp = (timestamp << 5) | decodeChar(ulid.charAt(i));
        }
        return timestamp;
    }

    private static byte[] randomBytes()
    {
        final byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        return randomness;
    }

    private static void encodeTimestamp(final long timestampMillis, final char[] out)
    {
        long remaining = timestampMillis;
        for (int i = TIMESTAMP_CHARS - 1; i >= 0; i--)
        {
            out[i] = ALPHABET[(int) (remaining & 0x1F)];
            remaining >>>= 5;
        }
    }

    private static void encodeRandomness(final byte[] randomness, final char[] out)
    {
        int bitBuffer = 0;
        int bitsInBuffer = 0;
        int byteIndex = 0;
        for (int outIndex = TIMESTAMP_CHARS; outIndex < LENGTH; outIndex++)
        {
            if (bitsInBuffer < 5)
            {
                bitBuffer = (bitBuffer << 8) | (randomness[byteIndex++] & 0xFF);
                bitsInBuffer += 8;
            }
            bitsInBuffer -= 5;
            out[outIndex] = ALPHABET[(bitBuffer >>> bitsInBuffer) & 0x1F];
        }
    }

    private static int decodeChar(final char c)
    {
        for (int i = 0; i < ALPHABET.length; i++)
        {
            if (ALPHABET[i] == c)
            {
                return i;
            }
        }
        throw new IllegalArgumentException("not a valid Crockford Base32 character: " + c);
    }
}
