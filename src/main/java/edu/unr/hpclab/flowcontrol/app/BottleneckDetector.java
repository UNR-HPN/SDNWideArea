package edu.unr.hpclab.flowcontrol.app;

import org.onosproject.net.Link;

import java.util.Collections;


public class BottleneckDetector {
    public static boolean shouldBePenalized(Link link) {
        double baseDelay = LinksInformationDatabase.getLinkBaseDelay(link);
        long lastDelayCheck = LinksInformationDatabase.getLinkLastDelayCheck(link);
        if (baseDelay == 0) {
            DelayCalculatorSingelton.getInstance().testLinksLatency(Collections.singletonList(link), true);
            LinksInformationDatabase.setLinkLastDelayCheck(link, System.currentTimeMillis());
            return false;
        }
        if (Util.ageInSeconds(lastDelayCheck) > Util.POLL_FREQ * 2 || lastDelayCheck == 0) {
            DelayCalculatorSingelton.getInstance().testLinksLatency(Collections.singletonList(link), false);
            LinksInformationDatabase.setLinkLastDelayCheck(link, System.currentTimeMillis());
        } else {
            return false;
        }
        double latestDelay = LinksInformationDatabase.getLatestLinkDelay(link);
        return Util.safeDivision(latestDelay, baseDelay) >= 10;
    }
}