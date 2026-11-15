package net.frsprojects.fogcontrol.core;

/** Helpers for 0xRRGGBB colors. */
public final class FogColor {
    private FogColor() {}

    /** Parses "#RRGGBB", "RRGGBB" or "0xRRGGBB". Returns -1 if invalid. */
    public static int parseHex(String text) {
        if (text == null) return -1;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.length() != 6) return -1;
        // Digit by digit: Integer.parseInt would also accept a sign ("#+12345", "#-12345").
        int rgb = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(s.charAt(i), 16);
            if (digit < 0) return -1;
            rgb = (rgb << 4) | digit;
        }
        return rgb;
    }

    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static int fromRgb(int r, int g, int b) {
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static float red(int rgb)   { return ((rgb >> 16) & 0xFF) / 255.0F; }
    public static float green(int rgb) { return ((rgb >> 8) & 0xFF) / 255.0F; }
    public static float blue(int rgb)  { return (rgb & 0xFF) / 255.0F; }
}
