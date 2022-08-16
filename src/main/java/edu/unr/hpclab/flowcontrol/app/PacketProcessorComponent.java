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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import edu.unr.hpclab.flowcontrol.app.host_messages.HostMessageHandler;
import edu.unr.hpclab.flowcontrol.app.host_messages.HostMessageType;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.TCP;
import org.onlab.packet.UDP;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
        service = {PacketProcessorComponent.class},
        property = {
                "appName=Some Default String Value",
        })
public class PacketProcessorComponent {
    private final Services services = Services.getInstance();

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final PacketProcessor packetProcessor = new InternalPacketProcessor();
//    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

    private String appName;

    @Activate
    protected void activate() {
        services.packetService.addProcessor(packetProcessor, 1);
        services.cfgService.registerProperties(getClass());
        log.info("Service Started");
    }

    @Deactivate
    protected void deactivate() {
        services.packetService.removeProcessor(packetProcessor);
        services.cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
//        cachedThreadPool.shutdown();
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            appName = get(properties, "appName");
        }
        log.info("Reconfigured");
    }

    private final class InternalPacketProcessor implements PacketProcessor {
        private final Cache<SrcDstPair, Long> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();
        PathFinderAndRuleInstaller pathFinderAndRuleInstaller = new PathFinderAndRuleInstaller(20);
        private int flows = 0;

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }
            Ethernet inPkt = context.inPacket().parsed();
            if (inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                SrcDstPair srcDstPair = getSrcDstPair(inPkt);
                try {
                    if (srcDstPair == null) {
                        return;
                    }
                    if (cache.getIfPresent(srcDstPair) != null) {
                        context.block();
                        return;
                    } else {
                        cache.put(srcDstPair, 1L);
                    }

                    long requestedRate = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.RATE_REQUEST).longValue();
                    double requestedDelay = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.DELAY_REQUEST).doubleValue();

                    boolean firstCond = srcDstPair.getSrcPort() == srcDstPair.getDstPort();
                    boolean secondCond = srcDstPair.getSrcPort() > 10000 && srcDstPair.getDstPort() > 10000;
                    boolean thirdCond = cache.getIfPresent(srcDstPair.reversed()) == null;
                    boolean forthCond = requestedRate > 0 && requestedDelay > 0;


                    if (firstCond && secondCond && thirdCond && forthCond) {
                        log.info("Flow #{} Joined {}", flows, srcDstPair);
//                        if (requestedRate == 0 || requestedDelay == 0) {
//                            log.warn("Requested Rate {}, Requested Delay {} for {}", requestedRate, requestedDelay, srcDstPair);
//                        }
                        flows++;
                    } else {
                        log.debug("SrcDst Pair {} joined", srcDstPair);
                    }

//                    services.getExecutor(ThreadsEnum.SOLUTION_FINDER).submit(() -> {
                    long t1 = System.currentTimeMillis();
                    SrcDstTrafficInfo srcDstTrafficInfo = new SrcDstTrafficInfo(srcDstPair, null, requestedRate, requestedDelay);
                    MyPath path = pathFinderAndRuleInstaller.applyAndGetPath(srcDstTrafficInfo);
                    srcDstTrafficInfo.setCurrentPath(path);
                    CurrentTrafficDataBase.addCurrentTraffic(srcDstPair, srcDstTrafficInfo);
                    context.treatmentBuilder().setOutput(path.links().get(1).src().port());
                    context.send();
                    long t2 = System.currentTimeMillis() - t1;
                    if (t2 > 50) {
                        log.warn("Took {}ms to handle joining of flow {}", t2, srcDstPair);
                    }
//                    });
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }

        private SrcDstPair getSrcDstPair(Ethernet ethPacket) {
            IPv4 packet = (IPv4) ethPacket.getPayload();
            int srcPort;
            int dstPort;
            if (packet.getProtocol() == IPv4.PROTOCOL_TCP) {
                srcPort = ((TCP) packet.getPayload()).getSourcePort();
                dstPort = ((TCP) packet.getPayload()).getDestinationPort();
            } else if (packet.getProtocol() == IPv4.PROTOCOL_UDP) {
                srcPort = ((UDP) packet.getPayload()).getSourcePort();
                dstPort = ((UDP) packet.getPayload()).getDestinationPort();
            } else {
                return null;
            }
//            if (srcPort < 10000 || dstPort < 10000) {
//                return null;
//            }
//            if (srcPort == dstPort){
//                log.info("Hoooooray! {}", new SrcDstPair(ethPacket.getSourceMAC(), ethPacket.getDestinationMAC(), srcPort, dstPort));
//		return null;
//            }
            //log.debug("Match!! Src Port {}, Dst Port {}", srcPort, dstPort);
            return new SrcDstPair(ethPacket.getSourceMAC(), ethPacket.getDestinationMAC(), srcPort, dstPort);
        }

    }
}
