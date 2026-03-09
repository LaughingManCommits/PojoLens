package laughing.man.commits.util;

import java.util.UUID;

public final class IdUtil {
    private static final char[] HEX_ALPHA = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p'
    };

    private IdUtil() {
    }

    public static String newUUID() {
        UUID uuid = UUID.randomUUID();
        char[] out = new char[36];

        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        writeHexAlpha(out, 0, msb >>> 32, 8);
        out[8] = '_';
        writeHexAlpha(out, 9, msb >>> 16, 4);
        out[13] = '_';
        writeHexAlpha(out, 14, msb, 4);
        out[18] = '_';
        writeHexAlpha(out, 19, lsb >>> 48, 4);
        out[23] = '_';
        writeHexAlpha(out, 24, lsb, 12);

        return new String(out);
    }

    private static void writeHexAlpha(char[] out, int offset, long value, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            out[offset + i] = HEX_ALPHA[(int) (value & 0xF)];
            value >>>= 4;
        }
    }
}