package de.geofabrik.osmi_routing.flag_encoders.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.storage.IntsRef;

public class LevelEncoderTest {

    private LevelEncoder encoder;
    private IntsRef flags;

    public LevelEncoderTest() {
    }

    @Before
    public void setUp() {
        encoder = new LevelEncoder();
        encoder.initForTest();
        flags = new IntsRef(48);
    }

    @Test
    public void testEmpty() {
        encoder.setLevel("", flags);
        assertTrue(encoder.isLevelValid(flags));
        assertEquals(0, encoder.getLevel(flags));
        assertEquals(0, encoder.getLevelDiff(flags));
    }

    @Test
    public void testValid() {
        encoder.setLevel("4", flags);
        assertTrue(encoder.isLevelValid(flags));
        assertEquals(4, encoder.getLevel(flags));
        assertEquals(0, encoder.getLevelDiff(flags));
    }

    @Test
    public void testValidAnother() {
        encoder.setLevel("1", flags);
        assertTrue(encoder.isLevelValid(flags));
        assertEquals(1, encoder.getLevel(flags));
        assertEquals(0, encoder.getLevelDiff(flags));
    }

    @Test
    public void testValidNegative() {
        encoder.setLevel("-4", flags);
        assertTrue(encoder.isLevelValid(flags));
        assertEquals(-4, encoder.getLevel(flags));
        assertEquals(0, encoder.getLevelDiff(flags));
    }

    @Test
    public void testValidTwo() {
        encoder.setLevel("3;4", flags);
        assertTrue(encoder.isLevelValid(flags));
        assertEquals(3, encoder.getLevel(flags));
        assertEquals(1, encoder.getLevelDiff(flags));
    }

    @Test
    public void testValidTwoNegative() {
        encoder.setLevel("-3;-4", flags);
        assertTrue(encoder.isLevelValid(flags));
        assertEquals(-4, encoder.getLevel(flags));
        assertEquals(1, encoder.getLevelDiff(flags));
    }

    @Test
    public void testValidTwoNegativeSwapped() {
        encoder.setLevel("-4;-3", flags);
        assertTrue(encoder.isLevelValid(flags));
        assertEquals(-4, encoder.getLevel(flags));
        assertEquals(1, encoder.getLevelDiff(flags));
    }

    @Test
    public void testInvalid() {
        encoder.setLevel("-4,3", flags);
        assertFalse(encoder.isLevelValid(flags));
    }

    @Test
    public void testTooLarge() {
        encoder.setLevel("14", flags);
        assertFalse(encoder.isLevelValid(flags));
    }

    @Test
    public void testTooLargeDiff1() {
        encoder.setLevel("14;15", flags);
        assertFalse(encoder.isLevelValid(flags));
    }

    @Test
    public void testRaiseLargeDiff() {
        encoder.setLevel("2;4", flags);
        // Difference too large, therefore defaults are stored.
        assertFalse(encoder.isLevelValid(flags));
    }
}
