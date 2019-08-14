package de.geofabrik.osmi_routing.flag_encoders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class SimpleBikeFlagEncoderTest {

    protected SimpleBikeFlagEncoder encoder;
    protected EncodingManager encodingManager;
    protected BooleanEncodedValue roundaboutEnc;
    protected DecimalEncodedValue priorityEnc;
    protected DecimalEncodedValue avSpeedEnc;

    @Before
    public void setUp() {
        encodingManager = EncodingManager.create(encoder = createBikeEncoder());
	if (encoder == null) System.out.println("XXXXXXXXXXXXx encoder null");
//        roundaboutEnc = encodingManager.getBooleanEncodedValue(Roundabout.KEY);
        priorityEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
        avSpeedEnc = encoder.getAverageSpeedEnc();
    }

    protected SimpleBikeFlagEncoder createBikeEncoder() {                                                                              
        return new SimpleBikeFlagEncoder();
    }

    @Test
    public void testWayAcceptance() {
	if (encoder == null) System.out.println("XXXXXXXXXXXXx encoder null");
        ReaderWay way = new ReaderWay(1);
        way.setTag("man_made", "pier");
        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("railway", "platform");
        way.setTag("bicycle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("railway", "platform");
        assertTrue(encoder.getAccess(way).isWay());
    }

}
