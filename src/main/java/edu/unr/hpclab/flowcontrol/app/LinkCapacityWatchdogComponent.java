/*
 * Copyright 2021-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.unr.hpclab.flowcontrol.app;

import org.onlab.util.KryoNamespace;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.EventuallyConsistentMapEvent;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import static edu.unr.hpclab.flowcontrol.app.Services.*;
import static org.onlab.util.Tools.get;
import static org.onosproject.net.device.DeviceEvent.Type.PORT_STATS_UPDATED;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
        service = {LinkCapacityWatchdogComponent.class},
        property = {
                "appName=Some Default String Value",
        })
public class LinkCapacityWatchdogComponent {
    private final Services services = Services.getInstance();

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Timer timer = new Timer();
    EventuallyConsistentMap<String, Long> PREV_FLOW_STATS;
    EventuallyConsistentMap<String, Long> PREV_PORT_STATS;
    private String appName;
    InternalDeviceListener internalDeviceListener = new InternalDeviceListener();

    @Activate
    protected void activate() {
        services.deviceService.addListener(internalDeviceListener);
        services.cfgService.registerProperties(getClass());
        log.info("Service Started");
//        timer.schedule(new Task(), TimeUnit.SECONDS.toMillis(Util.POLL_FREQ), TimeUnit.SECONDS.toMillis(Util.POLL_FREQ));

        KryoNamespace.Builder mySerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(String.class)
                .register(Long.class);

        PREV_FLOW_STATS = services.storageService.<String, Long>eventuallyConsistentMapBuilder()
                .withName("PREV_FLOW_STATS")
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .withSerializer(mySerializer)
                .build();

        PREV_PORT_STATS = services.storageService.<String, Long>eventuallyConsistentMapBuilder()
                .withName("PREV_PORT_STATS")
                .withSerializer(mySerializer)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .build();

        CurrentTrafficDataBase.CURRENT_TRAFFIC_MAP.addListener(event -> {
            if (event.type().equals(EventuallyConsistentMapEvent.Type.REMOVE)) {
                PREV_FLOW_STATS.entrySet().removeIf((e) -> e.getKey().contains(event.key().toString()));
                PREV_PORT_STATS.entrySet().removeIf((e) -> e.getKey().contains(event.key().toString()));
            }
        });
    }

    @Deactivate
    protected void deactivate() {
       services.cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
        timer.cancel();
        services.deviceService.removeListener(internalDeviceListener);
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            appName = get(properties, "appName");
        }
        log.info("Reconfigured");
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceEvent.Type type = event.type();
            DeviceId deviceId = event.subject().id();
            if (type == PORT_STATS_UPDATED) {
                List<PortStatistics> portStatisticsList = services.deviceService.getPortDeltaStatistics(deviceId);
                portStatisticsList.forEach(portStatistics -> {
                    long bytesReceived = Util.getBytesReceivingRate(portStatistics);
                    long bytesSent = Util.getBytesSendingRate(portStatistics);
                    if (bytesReceived > 0) {
                        Set<Link> ingressLinks = services.linkService.getIngressLinks(new ConnectPoint(deviceId, portStatistics.portNumber()));
                        LinksInformationDatabase.updateLinksLatestRate(ingressLinks, bytesReceived);
                    }
                    if (bytesSent > 0) {
                        Set<Link> egressLinks = services.linkService.getEgressLinks(new ConnectPoint(deviceId, portStatistics.portNumber()));
                        LinksInformationDatabase.updateLinksLatestRate(egressLinks, bytesSent);
                    }
                });
            }
        }
    }


    class Task extends TimerTask {
        @Override
        public void run() {
            writeToFile();
//            try {
//                CurrentTrafficDataBase.getCurrentTraffic().values().forEach(srcDstTrafficInfo -> {
////                    SrcDstPair srcDstPair = srcDstTrafficInfo.getSrcDstPair();
//                    srcDstTrafficInfo.getCurrentPath().links().forEach(l -> {
//                        if (l.src().elementId() instanceof DeviceId && l.dst().elementId() instanceof DeviceId) {
//                            BottleneckDetector.testPathLatency(l);
//                        }
////                        if (l.src().elementId() instanceof DeviceId) {
////                            long currentFlowStats = 0;
////                            PortNumber outputPort = l.src().port();
////                            DeviceId deviceId = l.src().deviceId();
//////                            List<PortNumber> takenInPorts = new ArrayList<>();
////                            String key = String.format("%s%s%s", srcDstPair, deviceId, outputPort);
////                            for (FlowEntry fe : flowRuleService.getFlowEntries(deviceId)) {
////                                if (fe.appId() == Util.appId.id()) {
////                                    Instruction instruction = fe.treatment().immediate().get(0);
////                                    if (instruction instanceof Instructions.OutputInstruction && ((Instructions.OutputInstruction) instruction).port().equals(outputPort)) {
//////                                        PortNumber inPort = ((PortCriterion) (fe.selector().getCriterion(Criterion.Type.IN_PORT))).port();
////                                        currentFlowStats += fe.packets();
//////                                        if (!takenInPorts.contains(inPort)) {
//////                                            currentFlowStats += deviceService.getDeltaStatisticsForPort(deviceId, inPort).packetsReceived();
//////                                            takenInPorts.add(inPort);
//////                                        }
////                                    }
////                                }
////                            }
////                            long deltaF = currentFlowStats - Optional.ofNullable(PREV_FLOW_STATS.get(key)).orElse(0L);
////                            PREV_FLOW_STATS.put(key, currentFlowStats);
////                            long currentPortStats = deviceService.getStatisticsForPort(deviceId, outputPort).packetsSent();
////                            long deltaSentPackets = currentPortStats - Optional.ofNullable(PREV_PORT_STATS.get(key)).orElse(0L);
////                            PREV_PORT_STATS.put(key, currentPortStats);
////                            long loss = Math.abs(deltaF - deltaSentPackets);
////                            LinksInformationDatabase.addLinkPacketLoss(l, loss);
////                            log.info("Packet loss on link: {} is: {}", Util.formatLink(l), Math.abs(loss));
////                        }
//                    });
//                    writeToFile(srcDstTrafficInfo);
//                });
//            } catch (Exception e) {
//                log.info("", e);
//            }
        }

        private void writeToFile() {

            try (FileWriter fw = new FileWriter("/home/oabuhamdan/NetworkLinkUtil.txt", true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                AtomicLong total = new AtomicLong();
                CurrentTrafficDataBase.getCurrentTraffic().values().forEach(
                        ct -> {
                            total.addAndGet(ct.getCurrentRate());
                        });
                if (total.get() > 0) {
                    out.print(LocalDate.now() + "\t");
                    out.println(total);
                }
//                CurrentTrafficDataBase.getCurrentTraffic().values().forEach(
//                        ct -> ct.getCurrentPath().links().forEach(l -> {
//                            if (l instanceof DefaultEdgeLink) {
//                                return;
//                            }
//                              BottleneckDetector.shouldBePenalized(l, 0);
//                              out.print(Util.formatLink(l) + ",");
//                              out.print(LinksInformationDatabase.getLinkUtilization(l) + ",");
//                              out.print(LinksInformationDatabase.getLinkDelay(l));
//                              out.print('\n');
//                              out.flush();
//                              }
//                        ));

//

//                out.print(srcDstTrafficInfo.getSrcDstPair() + ",");
//                srcDstTrafficInfo.getCurrentPath().links().stream().filter(LinksInformationDatabase::containsEntry).forEach(l -> {
//                    out.print(Util.formatLink(l) + ",");
//                    out.print(LinksInformationDatabase.getLinkDelay(l) + ",");
//                    out.print(LinksInformationDatabase.getLinkBandwidth(l) + ",");
//                });
            } catch (Exception e) {
                log.info("", e);
            }
        }
    }
}
