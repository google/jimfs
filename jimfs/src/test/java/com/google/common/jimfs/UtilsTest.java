package com.google.common.jimfs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilsTest {
    @Test
    public void testPowerOf2() {
        assertEquals(1, Util.nextPowerOf2(0));
        assertEquals(1, Util.nextPowerOf2(1));
        assertEquals(2, Util.nextPowerOf2(2));
        assertEquals(4, Util.nextPowerOf2(3));
        assertEquals(4, Util.nextPowerOf2(4));
        assertEquals(8, Util.nextPowerOf2(5));

        // When power provided is negative
        assertEquals(0, Util.nextPowerOf2(-5));
        assertEquals(0, Util.nextPowerOf2(-10));
    }
}
