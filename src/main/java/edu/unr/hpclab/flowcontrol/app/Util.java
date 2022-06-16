package edu.unr.hpclab.flowcontrol.app;


import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.IPv4;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TCP;
import org.onlab.packet.UDP;
import org.onlab.util.DataRateUnit;
import org.onosproject.cfg.ConfigProperty;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Link;
import org.onosproject.net.LinkKey;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.basics.BasicLinkConfig;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static edu.unr.hpclab.flowcontrol.app.Services.*;


public class Util {
    static final Logger log = LoggerFactory.getLogger(Util.class);
    static int POLL_FREQ = getPollFreq();

    private static int getPollFreq() {
        ConfigProperty pollFreq = cfgService.getProperty("org.onosproject.provider.of.device.impl.OpenFlowDeviceProvider", "portStatsPollFrequency");
        if (pollFreq == null) {
            return 5;
        } else {
            return pollFreq.asInteger();
        }
    }

    public static List<PortNumber> getCongestedIngressPorts(DeviceId deviceId, int count) {
        Stream<PortNumber> stream = deviceService.getPortDeltaStatistics(deviceId).stream().
                sorted(Comparator.comparing(PortStatistics::packetsReceived).reversed()).map(PortStatistics::portNumber);

        if (count > 0) {
            stream = stream.limit(count);
        }

        return stream.collect(Collectors.toList());
    }

    public static Set<PortNumber> getCongestedIngressPortsToLink(DeviceId deviceId, int count, Link link) {
        List<PortNumber> ingressCongestedPorts = getCongestedIngressPorts(deviceId, count);
        Set<PortNumber> ingressCongestedPortsToOutput = new HashSet<>();
        for (FlowEntry fe : flowRuleService.getFlowEntries(deviceId)) {
            for (Instruction instruction : fe.treatment().immediate()) {
                if (instruction instanceof Instructions.OutputInstruction && ((Instructions.OutputInstruction) instruction).port().equals(link.src().port())) {
                    PortNumber inPort = ((PortCriterion) fe.selector().getCriterion(Criterion.Type.IN_PORT)).port();
                    if (ingressCongestedPorts.contains(inPort)) {
                        ingressCongestedPortsToOutput.add(inPort);
                    }
                }
            }
        }
        return ingressCongestedPortsToOutput;
    }

    public static MacAddress getDstMacForHostPackets(Host host, DeviceId deviceId) {
        MacAddress dst = null;
        outer:
        for (FlowEntry fe : flowRuleService.getFlowEntries(deviceId)) {
            for (Criterion cr : fe.selector().criteria()) {
                if (cr.type().equals(Criterion.Type.ETH_SRC) && ((EthCriterion) cr).mac().equals(host.mac())) {
                    dst = ((EthCriterion) fe.selector().getCriterion(Criterion.Type.ETH_DST)).mac();
                    break outer;
                }
            }
        }
        return dst;
    }

//    public static List<SrcDstPair> getSrcDstMacsForPortOutToLink(DeviceId deviceId, PortNumber inPort, PortNumber outPort) {
//        List<SrcDstPair> srcDstPairs = new LinkedList<>();
//        for (FlowEntry fe : flowRuleService.getFlowEntries(deviceId)) {
//            Instruction instruction = fe.treatment().immediate().get(0);
//            if (instruction instanceof Instructions.OutputInstruction && ((Instructions.OutputInstruction) instruction).port().equals(outPort)) {
//                Criterion criterion = fe.selector().getCriterion(Criterion.Type.IN_PORT);
//                if (criterion != null && ((PortCriterion) criterion).port().equals(inPort)) {
//                    MacAddress src = ((EthCriterion) fe.selector().getCriterion(Criterion.Type.ETH_SRC)).mac();
//                    MacAddress dst = ((EthCriterion) fe.selector().getCriterion(Criterion.Type.ETH_DST)).mac();
//                    srcDstPairs.add(new SrcDstPair(src, dst));
//                }
//            }
//        }
//        return srcDstPairs;
//    }

//    public static Map<PortNumber, List<SrcDstPair>> getSrcDstMacsForInPortsOutToLink(DeviceId deviceId, Set<PortNumber> inPorts, Link link) {
//        Map<PortNumber, List<SrcDstPair>> srcDstMacs = new HashMap<>();
//        inPorts.forEach(port -> srcDstMacs.put(port, getSrcDstMacsForPortOutToLink(deviceId, port, link.src().port())));
//        return srcDstMacs;
//    }


    public static Host getHostByMac(MacAddress macAddress) {
        return hostService.getHostsByMac(macAddress).iterator().next();
    }

    public static void setBandwidthsToLinks() {
        List<Integer> bandwidths = IntStream.rangeClosed(1, linkService.getLinkCount()).mapToObj(x -> x * 50).collect(Collectors.toList());
        Iterator<Integer> bandwidthsIterator = bandwidths.iterator();
        linkService.getLinks().forEach(l -> {
            long bandwidth = bandwidthsIterator.next() * 1024L * 1024L;
            LinkKey linkKey = LinkKey.linkKey(l);
            log.debug("Bandwidth for link {} is {} MB", linkKey, bandwidth / (1024 * 1024));
            configService.addConfig(linkKey, BasicLinkConfig.class).bandwidth(bandwidth).apply();
        });
    }

    public static long MbpsToBps(Number num) {
        return DataRateUnit.MBPS.toBitsPerSecond(num.longValue());
    }

    public static long BpsToMbps(Number num) {
        return num.longValue() / (1024 * 1024);
    }

    public static long getBytesReceivingRate(PortStatistics portStatistics) {
        float duration = ((float) portStatistics.durationSec()) + (((float) portStatistics.durationNano()) / TimeUnit.SECONDS.toNanos(1));
        return Math.round(portStatistics.bytesReceived() * 8 / duration);
    }

    public static long getBytesSendingRate(PortStatistics portStatistics) {
        float duration = ((float) portStatistics.durationSec()) + (((float) portStatistics.durationNano()) / TimeUnit.SECONDS.toNanos(1));
        return Math.round(portStatistics.bytesSent() * 8 / duration);
    }

    public static String formatLink(Link link) {
        final String LINK_STRING_FORMAT = "%s -> %s";
        return String.format(LINK_STRING_FORMAT, link.src(), link.dst());
    }

    public static String formatLinksPath(Path path) {
        final String LINK_STRING_FORMAT = "  %s -> %s  // ";
        StringBuilder res = new StringBuilder();
        path.links().forEach(l -> {
            res.append(String.format(LINK_STRING_FORMAT, l.src().toString(), l.dst()));
        });
        return res.toString();
    }

    public static long ageInMilliSeconds(long t) {
        return System.currentTimeMillis() - t;
    }

    public static double ageInSeconds(long t) {
        return ageInMilliSeconds(t) / 1e3;
    }

    public static byte[] serializeObjectToByteArray(Object obj) {
        byte[] bytes = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            bytes = bos.toByteArray();
        } catch (Exception e) {
            log.info("", e);
        }
        return bytes;
    }

    public static Object toObject(byte[] bytes) {
        Object obj = null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes); ObjectInputStream ois = new ObjectInputStream(bis)) {
            obj = ois.readObject();
        } catch (Exception e) {
            log.info("", e);
        }

        return obj;
    }

    public static byte[] longToByteArray(long n) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES);
        byteBuffer.putLong(n);
        return byteBuffer.array();
    }

    public static long byteArrayToLong(byte[] arr) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(arr);
        return byteBuffer.getLong();
    }

    public static String toString(byte[] bytes) {
        return new String(bytes);
    }

    public static double safeDivision(Number num1, Number num2) {
        if (num2.doubleValue() == 0) {
            num2 = 1;
        }
        return num1.doubleValue() / num2.doubleValue();
    }
}