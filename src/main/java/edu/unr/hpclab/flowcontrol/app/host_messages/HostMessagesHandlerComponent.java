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
package edu.unr.hpclab.flowcontrol.app.host_messages;

import edu.unr.hpclab.flowcontrol.app.Services;
import edu.unr.hpclab.flowcontrol.app.SrcDstPair;
import org.onlab.packet.Data;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.IPv4;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
        service = {HostMessagesHandlerComponent.class},
        property = {
                "appName=Some Default String Value",
        })
public class HostMessagesHandlerComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());
    PacketProcessor p = new InternalPacketProcessor();
    private final Services services = Services.getInstance();

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
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            Ethernet inPkt = context.inPacket().parsed();
            if (inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 packet = (IPv4) inPkt.getPayload();
                if (packet.getProtocol() == IPv4.PROTOCOL_ICMP && (((ICMP) packet.getPayload()).getIcmpType() == 100)) {
                    try {
                        byte[] data = ((Data) (packet.getPayload()).getPayload()).getData();
                        String message = new String(data, StandardCharsets.UTF_8);
                        String[] tokens = message.strip().split(":");
                        SrcDstPair srcDstPair = getSrcDstPair(inPkt, tokens[tokens.length - 1]);
                        HostMessageHandler.parseAndAct(srcDstPair, tokens[0], tokens[1]);
                        context.block();
                    } catch (Exception e) {
                        log.error("", e);
                    }
                }
            }
        }

        private SrcDstPair getSrcDstPair(Ethernet inPkt, String portS) {
            int port = Integer.parseInt(portS);
            return new SrcDstPair(inPkt.getSourceMAC(), inPkt.getDestinationMAC(), port, port);
        }

    }
}
