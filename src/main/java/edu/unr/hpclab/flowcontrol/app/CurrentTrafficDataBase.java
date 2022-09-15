package edu.unr.hpclab.flowcontrol.app;

import org.onlab.packet.MacAddress;
import org.onlab.util.DataRateUnit;
import org.onlab.util.KryoNamespace;
import org.onosproject.net.Path;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CurrentTrafficDataBase {
    public static final CurrentTrafficDataBase INSTANCE = new CurrentTrafficDataBase();
    private static final Logger log = LoggerFactory.getLogger(CurrentTrafficDataBase.class);

    private static EventuallyConsistentMap<SrcDstPair, SrcDstTrafficInfo> CURRENT_TRAFFIC_MAP;

    public static void addCurrentTraffic(SrcDstPair srcDstPair, SrcDstTrafficInfo srcDstTrafficInfo) {
        CURRENT_TRAFFIC_MAP.put(srcDstPair, srcDstTrafficInfo);
    }

    public static boolean contains(SrcDstPair srcDstPair) {
        return CURRENT_TRAFFIC_MAP.containsKey(srcDstPair);
    }

    public static void remove(SrcDstPair srcDstPair) {
        CURRENT_TRAFFIC_MAP.remove(srcDstPair);
    }

    public static Map<SrcDstPair, SrcDstTrafficInfo> getCurrentTraffic() {
        return CURRENT_TRAFFIC_MAP.entrySet().stream().filter(e -> e.getValue().isActive()).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected void activate() {
        KryoNamespace.Builder mySerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(SrcDstPair.class, SrcDstTrafficInfo.class,
                          Path.class, List.class, MacAddress.class);

        CURRENT_TRAFFIC_MAP = Services.storageService.<SrcDstPair, SrcDstTrafficInfo>eventuallyConsistentMapBuilder()
                .withName("current_traffic_map")
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .withSerializer(mySerializer)
                .build();


        Services.scheduleAtFixedRate(new CurrentTrafficAliveStatusRunnable(), Util.POLL_FREQ, Util.POLL_FREQ);
    }

    protected void deactivate() {
        writeResultsToFile();
        CURRENT_TRAFFIC_MAP.destroy();
    }

    private void writeResultsToFile() {
        String home = System.getProperty("user.home");
        try (FileWriter fw = new FileWriter(home + "/TrafficSummary.txt")) {
            for (Map.Entry<SrcDstPair, SrcDstTrafficInfo> e : CURRENT_TRAFFIC_MAP.entrySet()) {
                fw.write(e.getValue().toString());
                fw.write("\n");
                fw.flush();
            }
            fw.write(String.format("Total #moves=%s", SolutionFinder.getTotalNumberOfMoves()));
        } catch (Exception e) {
            log.error("", e);
        }

    }

    private static class CurrentTrafficAliveStatusRunnable implements Runnable {
        @Override
        public void run() {
            try {
                for (Map.Entry<SrcDstPair, SrcDstTrafficInfo> entry : getCurrentTraffic().entrySet()) {
                    entry.getValue().setCurrentRate();
                    // Ignoring traffic below 2 MBPs and Too recent traffic (less than 5 sec)
                    boolean remove = entry.getValue().getCurrentRate() < DataRateUnit.MBPS.toBitsPerSecond(2) && Util.ageInSeconds(entry.getValue().getTimeStarted()) >= 2 * Util.POLL_FREQ;
                    if (remove) {
                        log.trace("Removing {}", entry.getKey());
                        SrcDstTrafficInfo srcDstTrafficInfo = CURRENT_TRAFFIC_MAP.get(entry.getKey());
                        srcDstTrafficInfo.setInactive();
                        srcDstTrafficInfo.setTimeFinished(System.currentTimeMillis());
                        SolutionFinder.findSolutionAfterFlowTerminate();
                    }
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }
}



