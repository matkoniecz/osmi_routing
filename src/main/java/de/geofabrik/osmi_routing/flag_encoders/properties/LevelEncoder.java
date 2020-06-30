package de.geofabrik.osmi_routing.flag_encoders.properties;

import java.util.List;

import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;


public class LevelEncoder {

    /**
     * Encode difference between minimal and maximal value of level=*.
     */
    private IntEncodedValue levelDiffEncoder;
    private final int maxDiff = 1;
    private final int levelBits = 4;
    /**
     * Encode lowest value of level=* tag.
     * Minimal supported value is -7.
     */
    private IntEncodedValue minLevelEncoder;
    private final int minLevel = -7;
    private int maxLevel;
    private int invalidValue;

    public LevelEncoder() {
        // (1 << levelBits) - 2, not -1 because the uppermost the uppermost value is reserved for "invalid"
        this.maxLevel = (1 << levelBits) - 2 + minLevel;
        this.invalidValue = (1 << levelBits) -1;
    }

    public void initForTest() {
        minLevelEncoder = new UnsignedIntEncodedValue("min_level", levelBits, true);
        minLevelEncoder.init(new EncodedValue.InitializerConfig());
        levelDiffEncoder = new UnsignedIntEncodedValue("level_diff", 1, true);
        levelDiffEncoder.init(new EncodedValue.InitializerConfig());
    }

    public List<EncodedValue> register(List<EncodedValue> encodedValues) {
        encodedValues.add(minLevelEncoder = new UnsignedIntEncodedValue("min_level", levelBits, true));
        encodedValues.add(levelDiffEncoder = new UnsignedIntEncodedValue("level_diff", 1, true));
        return encodedValues;
    }

    private int getLevelFromEncodedInt(int encoded) {
        return encoded + minLevel;
    }

    public int getLevel(IntsRef flags) {
        int encoded = minLevelEncoder.getInt(true, flags);
        return getLevelFromEncodedInt(encoded);
    }

    public int getLevelDiff(IntsRef flags) {
        return levelDiffEncoder.getInt(true, flags);
    }

    public boolean isLevelValid(IntsRef flags) {
        // invalid: level = 0, diff = 1
        return minLevelEncoder.getInt(true, flags) != invalidValue;
    }
    private int encodedIntForLevel(int level) {
        // return valid values only
        int enc = Math.min(level, maxLevel);
        enc = Math.max(minLevel, enc);
        enc = enc - minLevel;
        return enc;
    }

    private IntsRef setInvalidLevel(IntsRef flags) {
        minLevelEncoder.setInt(true, flags, invalidValue);
        levelDiffEncoder.setInt(true, flags, 0);
        return flags;
    }

    private IntsRef setEmptyLevel(IntsRef flags) {
        minLevelEncoder.setInt(true, flags, encodedIntForLevel(0));
        levelDiffEncoder.setInt(true, flags, 0);
        return flags;
    }

    /**
     * Set minimum and maximum level
     * @param value tag value of level=*
     * @return modified edge flags
     */
    public IntsRef setLevel(String value, IntsRef flags) {
        // no level=* tag set
        if (value == null || value.equals("")) {
            return setEmptyLevel(flags);
        }
        String[] levelStrs = value.split(";");
        int minValue = 1000;
        int maxValue = -1000;
        for (int i = 0; i < levelStrs.length; ++i) {
            try {
                // silently accept decimal values but truncate all decimals
                int v = (int) Double.parseDouble(levelStrs[i]);
                if (minValue > v) {
                    minValue = v;
                }
                if (maxValue < v) {
                    maxValue = v;
                }
            } catch (NumberFormatException e) {
                // If parsing failed, treat as empty level
                return setInvalidLevel(flags);
            }
        }
        if (maxValue > minValue + maxDiff || maxValue > maxLevel || maxValue < minLevel
                || minValue > maxLevel || minValue < minLevel) {
            // difference larger than 1, cannot be encoded and therefore the whole value is discarded
            return setInvalidLevel(flags);
        }
        minLevelEncoder.setInt(true, flags, encodedIntForLevel(minValue));
        levelDiffEncoder.setInt(true, flags, maxValue - minValue);
        return flags;
    }

}
