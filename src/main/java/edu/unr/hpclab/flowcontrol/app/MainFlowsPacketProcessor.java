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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Skeletal ONOS application component.
 */

public class MainFlowsPacketProcessor {
    public static final MainFlowsPacketProcessor INSTANCE = new MainFlowsPacketProcessor();
    private static final Logger log = LoggerFactory.getLogger(MainFlowsPacketProcessor.class);
    private PacketProcessor packetProcessor;

    protected void activate() {
        packetProcessor = new InternalPacketProcessor();
        Services.packetService.addProcessor(packetProcessor, PacketProcessor.director(1));
    }

    protected void deactivate() {
        Services.packetService.removeProcessor(packetProcessor);
//        cachedThreadPool.shutdown();
    }

    private static final class InternalPacketProcessor implements PacketProcessor {
        private final Cache<SrcDstPair, Long> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();
        PathFinderAndRuleInstaller basePathFinderAndRuleInstaller = new PathFinderAndRuleInstaller();
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
                    }

                    long requestedRate = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.RATE_REQUEST).longValue();
                    double requestedDelay = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.DELAY_REQUEST).doubleValue();

                    boolean firstCond = srcDstPair.getSrcPort() == srcDstPair.getDstPort();
                    boolean secondCond = srcDstPair.getSrcPort() > 10000 && srcDstPair.getDstPort() > 10000;
                    boolean thirdCond = cache.getIfPresent(srcDstPair.reversed()) == null;
                    boolean forthCond = requestedRate > 0 && requestedDelay > 0;
                    boolean fifthCond = !CurrentTrafficDataBase.contains(srcDstPair) && !CurrentTrafficDataBase.contains(srcDstPair.reversed());

                    SrcDstTrafficInfo srcDstTrafficInfo = new SrcDstTrafficInfo(srcDstPair, null, requestedRate, requestedDelay);

                    if (firstCond && secondCond && thirdCond && fifthCond) {
                        cache.put(srcDstPair, 1L);
                        if (!forthCond) {
                            String imposter = requestedDelay > 0 ? "Req Rate" : requestedRate == 0 ? "Req Rate & Req Delay" : "Req Delay";
                            log.warn("SrcDst {} has zero value of {} ", srcDstPair, imposter);
                        }
                        log.info("Flow #{} Joined {}", flows, srcDstPair);
                        long t1 = System.currentTimeMillis();
                        handleTrafficPath(context, srcDstPair, srcDstTrafficInfo);
                        long t2 = System.currentTimeMillis() - t1;
                        if (t2 > 50) {
                            log.warn("Took {}ms to handle joining of flow {}", t2, srcDstPair);
                        }
                        flows++;
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        }

        private void handleTrafficPath(PacketContext context, SrcDstPair srcDstPair, SrcDstTrafficInfo srcDstTrafficInfo) {
            CurrentTrafficDataBase.addCurrentTraffic(srcDstPair, srcDstTrafficInfo);
            MyPath path = basePathFinderAndRuleInstaller.applyAndGetPath(srcDstTrafficInfo);
            srcDstTrafficInfo.setCurrentPath(path);
            context.treatmentBuilder().setOutput(path.links().get(1).src().port());
            context.send();
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
            return new SrcDstPair(ethPacket.getSourceMAC(), ethPacket.getDestinationMAC(), srcPort, dstPort, packet.getProtocol());
        }

    }
}