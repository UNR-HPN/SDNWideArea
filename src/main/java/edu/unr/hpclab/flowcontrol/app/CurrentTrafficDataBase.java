package edu.unr.hpclab.flowcontrol.app;

import org.onlab.packet.MacAddress;
import org.onlab.util.DataRateUnit;
import org.onlab.util.KryoNamespace;
import org.onosproject.net.Path;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.EventuallyConsistentMapEvent;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Component(immediate = true,
        service = {CurrentTrafficDataBase.class}
)
public class CurrentTrafficDataBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinksInformationDatabase.class);
    private static final Timer timer = new Timer();
    private final Services services = Services.getInstance();

    protected static EventuallyConsistentMap<SrcDstPair, SrcDstTrafficInfo> CURRENT_TRAFFIC_MAP;

    public static void addCurrentTraffic(SrcDstPair srcDstPair, SrcDstTrafficInfo srcDstTrafficInfo) {
        CURRENT_TRAFFIC_MAP.put(srcDstPair, srcDstTrafficInfo);
    }

    public static boolean contains(SrcDstPair srcDstPair) {
        return CURRENT_TRAFFIC_MAP.containsKey(srcDstPair);
    }

    public static void remove(SrcDstPair srcDstPair) {
        CURRENT_TRAFFIC_MAP.remove(srcDstPair);
    }

    public static SrcDstTrafficInfo getCurrentTrafficValue(SrcDstPair srcDstPair) {
        return CURRENT_TRAFFIC_MAP.get(srcDstPair);
    }

    public static Map<SrcDstPair, SrcDstTrafficInfo> getCurrentTraffic() {
        Map<SrcDstPair, SrcDstTrafficInfo> map = new HashMap<>();
        CURRENT_TRAFFIC_MAP.entrySet().forEach((k) -> map.put(k.getKey(), k.getValue()));
        return Collections.unmodifiableMap(map);
    }

    @Activate
    protected void activate() {
        KryoNamespace.Builder mySerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(SrcDstPair.class, SrcDstTrafficInfo.class,
                          Path.class, List.class, MacAddress.class);

        CURRENT_TRAFFIC_MAP = services.storageService.<SrcDstPair, SrcDstTrafficInfo>eventuallyConsistentMapBuilder()
                .withName("current_traffic_map")
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .withSerializer(mySerializer)
                .build();

        CURRENT_TRAFFIC_MAP.addListener((x) -> {
            if (x.type() == EventuallyConsistentMapEvent.Type.REMOVE) {   //
                SolutionFinder.findSolutionAfterFlowLeave();
//                LOGGER.info("Removed srcDstPair {}", x.value().getSrcDstPair());
            }
        });

        CurrentTrafficAliveStatusTimerTask scheduleApp = new CurrentTrafficAliveStatusTimerTask();
        timer.schedule(scheduleApp, TimeUnit.SECONDS.toMillis(Util.POLL_FREQ), TimeUnit.SECONDS.toMillis(Util.POLL_FREQ));
    }

    @Deactivate
    protected void deactivate() {
        timer.cancel();
    }

    private static class CurrentTrafficAliveStatusTimerTask extends TimerTask {

        @Override
        public void run() {
            for (Map.Entry<SrcDstPair, SrcDstTrafficInfo> entry : CURRENT_TRAFFIC_MAP.entrySet()) {
                // Ignoring traffic below 2 MBPs and Too recent traffic (less than 5 sec)
                entry.getValue().setCurrentRate();   // recalculate it to get accurate results
                boolean remove = entry.getValue().getCurrentRate() < DataRateUnit.MBPS.toBitsPerSecond(2) && Util.ageInSeconds(entry.getValue().getTimeStarted()) >= 2 * Util.POLL_FREQ;
                if (remove) {
                    LOGGER.info("Removing {}", entry.getKey());
                    CURRENT_TRAFFIC_MAP.remove(entry.getKey());
                }
            }
        }
    }
}



