package edu.unr.hpclab.flowcontrol.app;


import org.onlab.util.DataRateUnit;
import org.onlab.util.KryoNamespace;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;


@Component(immediate = true, service = {LinksInformationDatabase.class})
public class LinksInformationDatabase {
    private static final Logger log = LoggerFactory.getLogger(LinksInformationDatabase.class);
    private static EventuallyConsistentMap<Link, TimedLinkInformation> LINK_INFORMATION_MAP;
    private final Services services = Services.getInstance();

    private static TimedLinkInformation getTimedLinkInformation(Link link) {
        return Optional.ofNullable(link).map(LINK_INFORMATION_MAP::get).orElseGet(() -> new TimedLinkInformation(link));
    }

    public static void updateLinkLatestRate(Link link, long capacity) {
        if (capacity >= Util.MbpsToBps(2)) {  // ignoring values less than 1 mbps
            getTimedLinkInformation(link).addLatestRate(capacity);
        }
    }

    public static void updateLinkLatestDelay(Link link, double delay) {
        TimedLinkInformation tli = getTimedLinkInformation(link);
        if (tli.getBaseDelay() == 0 || tli.getLastDelayCheck() == 0) {
            tli.setBaseDelay(delay);
            tli.setLastDelayCheck(System.currentTimeMillis());
        } else {
            tli.updateLatestDelay(delay);
            tli.setLastDelayCheck(System.currentTimeMillis());
        }
    }

    public static void setLinkBaseDelay(Link link, double delay) {
        getTimedLinkInformation(link).setBaseDelay(delay);
    }

    public static double getLinkBaseDelay(Link link) {
        return getTimedLinkInformation(link).getBaseDelay();
    }


    public static void setLinkLastDelayCheck(Link link, long time) {
        getTimedLinkInformation(link).setLastDelayCheck(time);
    }

    public static long getLinkLastDelayCheck(Link link) {
        return getTimedLinkInformation(link).getLastDelayCheck();
    }

    public static void updateLinksLatestRate(Set<Link> links, long capacity) {
        links.forEach(link -> updateLinkLatestRate(link, capacity));
    }

    public static void forgetBandwidth(Link link) { //Used when link hasn't been discovered for a certain time
        getTimedLinkInformation(link).forgetBandwidth();
    }

    public static double getLinkUtilization(Link link) {
        return getTimedLinkInformation(link).getMostRecentLinkUtilization();
    }

    public static Long getLinkEstimatedBandwidth(Link link) {
        return getTimedLinkInformation(link).getEstimatedCapacity();
    }

    public static double getLatestLinkDelay(Link link) {
        return getTimedLinkInformation(link).getLatestDelay();
    }

    public static long getLatestLinkUpdate(Link link) {
        return getTimedLinkInformation(link).getLatestUpdated();
    }

    public static long getPenalizationTime(Link link) {
        return getTimedLinkInformation(link).getPenalizationTime();
    }


    public static Map<Link, String> getLinkInformationMap() {
        return LINK_INFORMATION_MAP.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Object::toString));
    }

    public static void deleteEntries() {
        LINK_INFORMATION_MAP.clear();
    }

    @Activate
    protected void activate() {
        KryoNamespace.Builder mySerializer = KryoNamespace.newBuilder().register(KryoNamespaces.API).register(Link.class)
                .register(FixedSizeQueue.class).register(ConnectPoint.class).register(TimedLinkInformation.class).register(Long.class);

        LINK_INFORMATION_MAP = services.storageService.<Link, TimedLinkInformation>eventuallyConsistentMapBuilder().withName("link_bandwidth_map").withTimestampProvider((k, v) -> new WallClockTimestamp()).withSerializer(mySerializer).build();


        for (Link link : services.linkService.getLinks()) {
            LINK_INFORMATION_MAP.put(link, new TimedLinkInformation(link));
        }
        //DelayCalculatorSingleton.getInstance().testLinksLatency();
    }


    private static class TimedLinkInformation { // Timed Capacity: Capacity of the link at a certain time
        private static final long DEFAULT_CAPACITY = Util.MbpsToBps(1000);
        private final Link link;
        private final FixedSizeQueue<Long> latestReportedRates;
        private long estimatedCapacity;
        private long latestUpdated;
        private double latestDelay;
        private double baseDelay;
        private long lastDelayCheck;
        private long penalizationTime;

        private TimedLinkInformation(Link link) {
            this.link = link;
            this.latestUpdated = System.currentTimeMillis();
            latestReportedRates = new FixedSizeQueue<>(5);
            this.estimatedCapacity = DEFAULT_CAPACITY;
            this.latestDelay = 0;
            this.baseDelay = 0;
        }

        private void addLatestRate(long rate) {
            latestReportedRates.addLast(rate);
            this.latestUpdated = System.currentTimeMillis();
            estimateCapacity();
        }

        private void updateLatestDelay(double delay) {
            this.latestDelay = delay;
        }

        public double getMostRecentLinkUtilization() {
            return getLastReportedRate() * 1.0 / getEstimatedCapacity();
        }

        private void estimateCapacity() {
            long prevEstimate = this.estimatedCapacity;
            long latestRate = this.getLastReportedRate();
            long avgReportedCapacity = (long) latestReportedRates.stream().mapToLong(Long::longValue).average().orElse(DEFAULT_CAPACITY);
            double bwDiff = Util.safeDivision(prevEstimate - avgReportedCapacity, (prevEstimate + avgReportedCapacity) / 2);
            if (bwDiff > 0.3) { // if the change between different values > 30%
                if (BottleneckDetector.shouldBePenalized(link)) {
                    this.estimatedCapacity = latestRate;
                    log.info("PENALIZING THIS LINK {}. New Capacity is {}", link, estimatedCapacity);
                    setPenalizationTime(System.currentTimeMillis());
                    SolutionFinder.findSolutionForDroppedLink(link);
                } else {
                    // Keeping estimated capacity as before since delay values are normal
                    this.estimatedCapacity = prevEstimate;
//                    this.latestReportedRates.addLast(this.estimatedCapacity);
//                    this.latestReportedRates.addLast(latestRate);
                }
            } else if (bwDiff < 0) {
                this.estimatedCapacity = latestRate;
            } else if (Util.ageInSeconds(penalizationTime) >= Util.POLL_FREQ * 20L) {
                forgetBandwidth();
            }
            if (estimatedCapacity < DataRateUnit.MBPS.toBitsPerSecond(600))
                log.warn("Bandwidth for link {} is {} MB", link, estimatedCapacity / (1024 * 1024));
//            log.debug("Bandwidth for link {} is {} MB", link, estimatedCapacity / (1024 * 1024));
        }

        public void forgetBandwidth() {
            this.estimatedCapacity = DEFAULT_CAPACITY;
            log.info("Returned {} capacity to {}", link, estimatedCapacity);
//            SolutionFinder.findSolutionAfterFlowTerminate();
        }

        public long getPenalizationTime() {
            return penalizationTime;
        }

        public void setPenalizationTime(long penalizationTime) {
            this.penalizationTime = penalizationTime;
        }

        public long getLastDelayCheck() {
            return this.lastDelayCheck;
        }

        public void setLastDelayCheck(long lastDelayCheck) {
            this.lastDelayCheck = lastDelayCheck;
        }

        public long getEstimatedCapacity() {
            return this.estimatedCapacity == 0 ? DEFAULT_CAPACITY : this.estimatedCapacity;
        }


        public long getLastReportedRate() {
            return latestReportedRates.size() > 0 ? latestReportedRates.getLast() : 0;
        }

        public long getLatestUpdated() {
            return latestUpdated;
        }

        public double getLatestDelay() {
            return this.latestDelay == 0 ? this.baseDelay : this.latestDelay;
        }

        public double getBaseDelay() {
            return this.baseDelay;
        }

        private void setBaseDelay(double delay) {
            this.baseDelay = delay;
        }

        public Link getLink() {
            return link;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", TimedLinkInformation.class.getSimpleName() + "[", "]")
                    .add("link=" + link)
                    .add("latestReportedRates=" + latestReportedRates)
                    .add("estimatedCapacity=" + estimatedCapacity)
                    .add("latestUpdated=" + latestUpdated)
                    .add("latestDelay=" + latestDelay)
                    .add("baseDelay=" + baseDelay)
                    .add("lastPenalizeCheck=" + lastDelayCheck)
                    .toString();
        }
    }
}
