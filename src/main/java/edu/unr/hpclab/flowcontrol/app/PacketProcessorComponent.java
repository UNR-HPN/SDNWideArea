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
    PacketProcessor p = new InternalPacketProcessor();
    private String appName;

    @Activate
    protected void activate() {

        services.packetService.addProcessor(p, 1);

        services.cfgService.registerProperties(getClass());
        log.info("Service Started");
    }

    @Deactivate
    protected void deactivate() {
        services.packetService.removeProcessor(p);
        services.cfgService.unregisterProperties(getClass(), false);
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
                try {
                    if (srcDstPair == null) {
                        throw new RuntimeException("Neither TCP nor UDP flow or a Flow with a reserved port");
                    }
                    // Ignore the ACK traffic
                    if (CurrentTrafficDataBase.getCurrentTrafficValue(srcDstPair.reversed()) != null) {
                        throw new RuntimeException(String.format("This is a ACK traffic %s", srcDstPair));
                    }
                    log.info("New Flow Joining {}", srcDstPair);
                    if (cache.getIfPresent(srcDstPair) != null) {
                        context.block();
                        throw new RuntimeException("Packet Processor: Already dealt with this TCP packet");
                    } else {
                        cache.put(srcDstPair, 1L);
                    }

                    log.debug("**** {} -> {} ****", inPkt.getSourceMAC(), inPkt.getDestinationMAC());
                    long requestedRate = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.RATE_REQUEST).longValue();
                    double requestedDelay = HostMessageHandler.getLatestMessage(srcDstPair, HostMessageType.DELAY_REQUEST).doubleValue();
                    log.info("Requested Rate {}, Requested Delay {} for {}", requestedRate, requestedDelay, srcDstPair);
                    SrcDstTrafficInfo srcDstTrafficInfo = new SrcDstTrafficInfo(srcDstPair, null, requestedRate, requestedDelay);
                    MyPath path = PathFinderAndRuleInstaller.applyAndGetPath(srcDstTrafficInfo);
                    srcDstTrafficInfo.setCurrentPath(path);
                    CurrentTrafficDataBase.addCurrentTraffic(srcDstPair, srcDstTrafficInfo); // Add current traffic to the current traffic DB
                    context.treatmentBuilder().setOutput(path.links().get(1).src().port());
                    context.send();
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
            if (srcPort < 1024 || dstPort < 1023) {
                return null;
            }
            return new SrcDstPair(ethPacket.getSourceMAC(), ethPacket.getDestinationMAC(), srcPort, dstPort);
        }

    }
}
