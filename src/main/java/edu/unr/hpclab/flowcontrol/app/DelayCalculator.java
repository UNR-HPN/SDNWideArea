package edu.unr.hpclab.flowcontrol.app;

import org.onlab.packet.Data;
import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.MacAddress;
import org.onlab.util.KryoNamespace;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.openflow.controller.Dpid;
import org.onosproject.openflow.controller.OpenFlowEventListener;
import org.onosproject.openflow.controller.OpenFlowSwitch;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;
import org.projectfloodlight.openflow.protocol.OFEchoReply;
import org.projectfloodlight.openflow.protocol.OFEchoRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.ByteBuffer;

import static edu.unr.hpclab.flowcontrol.app.Services.*;

public class DelayCalculator {
    private static final DelayCalculator instance = new DelayCalculator();
    private final Logger logger = LoggerFactory.getLogger(DelayCalculator.class);
    private final EventuallyConsistentMap<DeviceId, Long> controllerToSwitchDelayMap = storageService.<DeviceId, Long>eventuallyConsistentMapBuilder()
            .withName("DELAY_TO_SWITCH_MAP").withTimestampProvider((k, v) -> new WallClockTimestamp())
            .withSerializer(KryoNamespace.newBuilder().register(KryoNamespaces.API).register(Long.class, DeviceId.class))
            .build();

    private DelayCalculator() {
    }

    public static DelayCalculator getInstance() {   //Singleton
        return instance;
    }

    public void testLinksLatency() {
        testLinksLatency(linkService.getLinks(), true);
    }

    public void testLinksLatency(Iterable<Link> links, boolean isBase) {
        Thread currentThread = Thread.currentThread();
        InBoundProcessor inBoundProcessor = new InBoundProcessor(currentThread);
        InternalOpenFlowEventListener listener = new InternalOpenFlowEventListener(currentThread);
        packetService.addProcessor(inBoundProcessor, 0);
        openFlowControllerService.addEventListener(listener);
        testLinksLatency(links, currentThread, isBase);
        openFlowControllerService.removeEventListener(listener);
        packetService.removeProcessor(inBoundProcessor);
    }


    private void testLinksLatency(Iterable<Link> links, Thread currentThread, boolean isBase) {
        links.forEach(link -> {
            try {
                synchronized (currentThread) {
                    sendEcho(link.src());
                    currentThread.wait();
                    sendEcho(link.dst());
                    currentThread.wait();
                    sendProbingPacket(link, isBase);
                    currentThread.wait();
                }
            } catch (Exception e) {
                logger.error("", e);
            }
        });
    }

    private void sendProbingPacket(Link link, boolean isBase) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(link.src().port()).build();

        Ethernet ethPacket = new Ethernet();
        ethPacket.setSourceMACAddress(MacAddress.valueOf("00:00:01:01:01:01"));
        ethPacket.setDestinationMACAddress(MacAddress.valueOf("00:00:02:02:02:02"));
        ethPacket.setEtherType(Ethernet.TYPE_IPV4);
        ethPacket.setPad(true);


        IPv4 ip = new IPv4();
        ip.setSourceAddress("10.10.10.10");
        ip.setDestinationAddress("10.10.10.20");
        ip.setPayload(ethPacket);

        ethPacket.setPayload(ip);

        try {
            Info info = new Info(link, isBase);
            byte[] data = Util.serializeObjectToByteArray(info);
            ip.setPayload(Data.deserializer().deserialize(data, 0, data.length));
        } catch (DeserializationException e) {
            logger.error("", e);
        }
        OutboundPacket packet = new DefaultOutboundPacket(link.src().deviceId(), treatment, ByteBuffer.wrap(ethPacket.serialize()));
        packetService.emit(packet);
    }

    private void sendEcho(ConnectPoint connectPoint) {
        Dpid srcDevice = Dpid.dpid(connectPoint.deviceId().uri());
        OpenFlowSwitch sw = openFlowControllerService.getSwitch(srcDevice);
        if (sw == null) {
            return;
        }
        OFEchoRequest statsRequest = sw.factory().echoRequest(Util.longToByteArray(System.nanoTime()));
        sw.sendMsg(statsRequest);
    }

    static class Info implements Serializable {
        private final long serialVersionUID = 6529685098267757690L;
        String srcId;
        String dstId;
        long time;

        boolean isBase;

        public Info(Link link, boolean isBase) {
            this.srcId = link.src().toString();
            this.dstId = link.dst().toString();
            this.time = System.nanoTime();
            this.isBase = isBase;
        }
    }

    class InBoundProcessor implements PacketProcessor {
        final Thread thread;

        public InBoundProcessor(Thread currentThread) {
            thread = currentThread;
        }

        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 p = (IPv4) ethPkt.getPayload();
                if (p.getSourceAddress() == IPv4.toIPv4Address("10.10.10.10")) {
                    try {
                        byte[] data = ((Data) p.getPayload()).getData();
                        Info info = (Info) Util.toObject(data);
                        long receive_time = System.nanoTime();
                        long sent_time = info.time;
                        Link link = linkService.getLink(ConnectPoint.fromString(info.srcId), ConnectPoint.fromString(info.dstId));
                        double x = (controllerToSwitchDelayMap.get(link.src().deviceId()) * 1.0) + (controllerToSwitchDelayMap.get(link.dst().deviceId()) * 1.0);
                        double delay = ((receive_time - sent_time) - x / 2) / 1e6;
                        if (info.isBase) {
                            LinksInformationDatabase.setLinkBaseDelay(link, delay);
                        } else {
                            LinksInformationDatabase.updateLinkLatestDelay(link, delay);
                        }
                        logger.info("Delay for link {} is {}", Util.formatLink(link), delay);
                        context.block();
                        synchronized (thread) {
                            thread.notify();
                        }
                    } catch (Exception e) {
                        logger.info("", e);
                    }
                }
            }
        }
    }

    class InternalOpenFlowEventListener implements OpenFlowEventListener {
        private final Thread thread;

        public InternalOpenFlowEventListener(Thread currentThread) {
            this.thread = currentThread;
        }

        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            if (msg.getType().equals(OFType.ECHO_REPLY)) {
                controllerToSwitchDelayMap.put(DeviceId.deviceId(Dpid.uri(dpid)), System.nanoTime() - Util.byteArrayToLong(((OFEchoReply) msg).getData()));
                synchronized (thread) {
                    thread.notify();
                }
            }
        }
    }
}
