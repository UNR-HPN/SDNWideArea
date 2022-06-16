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

import static edu.unr.hpclab.flowcontrol.app.Services.cfgService;
import static edu.unr.hpclab.flowcontrol.app.Services.packetService;
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
    private final Logger log = LoggerFactory.getLogger(getClass());
    PacketProcessor p = new InternalPacketProcessor();
    private String appName;

    @Activate
    protected void activate() {

        packetService.addProcessor(p, 1);

        cfgService.registerProperties(getClass());
        log.info("Service Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(p);
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
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
        Cache<SrcDstPair, Long> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();


        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            Ethernet inPkt = context.inPacket().parsed();
            if (inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                SrcDstPair srcDstPair = getSrcDstPair(inPkt);
                if (srcDstPair == null) {
                    return;
                }
                if (CurrentTrafficDataBase.getCurrentTrafficValue(srcDstPair.reversed()) != null) {
                    log.info("This is a ACK traffic {}", srcDstPair);
                    return;
                }
                log.info("New Flow Joining {}", srcDstPair);
                if (cache.getIfPresent(srcDstPair) != null) {
                    log.info("****** Packet Processor: Already dealt with this TCP packet *******");
                    context.block();
                    return;
                } else {
                    cache.put(srcDstPair, 1L);
                }
                log.debug("**** {} -> {} ****", inPkt.getSourceMAC(), inPkt.getDestinationMAC());
                log.debug("****** Packet Processor1: TCP PACKET *******");
                long requestedRate = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.RATE_REQUEST).longValue();
                double requestedDelay = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.DELAY_REQUEST).doubleValue();
                log.info("Requested Rate {}, Requested Delay {} for {}", requestedRate, requestedDelay, srcDstPair);
                SrcDstTrafficInfo srcDstTrafficInfo = new SrcDstTrafficInfo(srcDstPair, null, requestedRate, requestedDelay);
                MyPath path = PathFinderAndRuleInstaller.applyAndGetPath(srcDstTrafficInfo);
                // Ignore the ACK traffic
                srcDstTrafficInfo.setCurrentPath(path);
                CurrentTrafficDataBase.addCurrentTraffic(srcDstPair, srcDstTrafficInfo); // Add current traffic to the current traffic DB
                context.treatmentBuilder().setOutput(path.links().get(1).src().port());
                context.send();
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
            return new SrcDstPair(ethPacket.getSourceMAC(), ethPacket.getDestinationMAC(), srcPort, dstPort);
        }

    }
}
