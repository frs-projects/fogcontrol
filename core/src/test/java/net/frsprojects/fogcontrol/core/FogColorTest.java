package net.frsprojects.fogcontrol.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FogColorTest {
    @Test
    void parsesAllPrefixes() {
        assertEquals(0x88AACC, FogColor.parseHex("#88AACC"));
        assertEquals(0x88AACC, FogColor.parseHex("88aacc"));
        assertEquals(0x88AACC, FogColor.parseHex("0x88AACC"));
        assertEquals(0x88AACC, FogColor.parseHex("  0X88aacc "));
        assertEquals(0x000000, FogColor.parseHex("#000000"));
        assertEquals(0xFFFFFF, FogColor.parseHex("#FFFFFF"));
    }

    @Test
    void rejectsInvalid() {
        assertEquals(-1, FogColor.parseHex(null));
        assertEquals(-1, FogColor.parseHex(""));
        assertEquals(-1, FogColor.parseHex("#FFF"));
        assertEquals(-1, FogColor.parseHex("#88AACCDD"));
        assertEquals(-1, FogColor.parseHex("#GGGGGG"));
        assertEquals(-1, FogColor.parseHex("#-12345"));
        assertEquals(-1, FogColor.parseHex("#+12345"));
    }

    @Test
    void roundTripsThroughHexAndRgb() {
        int rgb = FogColor.fromRgb(0x12, 0xAB, 0xEF);
        assertEquals(0x12ABEF, rgb);
        assertEquals("#12ABEF", FogColor.toHex(rgb));
        assertEquals(rgb, FogColor.parseHex(FogColor.toHex(rgb)));
        assertEquals(1.0F, FogColor.blue(0x0000FF));
        assertEquals(0.0F, FogColor.red(0x0000FF));
    }
}
