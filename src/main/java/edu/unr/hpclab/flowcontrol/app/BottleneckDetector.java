package edu.unr.hpclab.flowcontrol.app;

import org.onosproject.net.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;


public class BottleneckDetector {
    private static final Logger log = LoggerFactory.getLogger(BottleneckDetector.class);
    public static boolean shouldBePenalized(Link link) {
        double baseDelay = LinksInformationDatabase.getLinkBaseDelay(link);
        long lastDelayCheck = LinksInformationDatabase.getLinkLastDelayCheck(link);
        if (Util.ageInSeconds(lastDelayCheck) > Util.POLL_FREQ * 2 || lastDelayCheck == 0) {
            DelayCalculatorSingleton.getInstance().testLinksLatency(Collections.singletonList(link));
        } else {
            return false;
        }
        double latestDelay = LinksInformationDatabase.getLatestLinkDelay(link);
        return Util.safeDivision(latestDelay, baseDelay) >= 10;
    }
}