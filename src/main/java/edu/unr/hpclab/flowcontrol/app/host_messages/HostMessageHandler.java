package edu.unr.hpclab.flowcontrol.app.host_messages;

import edu.unr.hpclab.flowcontrol.app.SrcDstPair;
import org.onlab.packet.MacAddress;
import org.onlab.util.KryoNamespace;
import org.onosproject.net.Path;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static edu.unr.hpclab.flowcontrol.app.Services.storageService;

public class HostMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(HostMessageHandler.class);

    final private static KryoNamespace.Builder mySerializer = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(SrcDstPair.class, HostMessageType.class, String.class,
                      Path.class, List.class, MacAddress.class);

    private static final EventuallyConsistentMap<SrcDstPair, Map<HostMessageType, Queue<String>>> HOST_MESSAGES_MAP =
            storageService.<SrcDstPair, Map<HostMessageType, Queue<String>>>eventuallyConsistentMapBuilder()
                    .withName("hostMessagesMap")
                    .withTimestampProvider((x, y) -> new WallClockTimestamp())
                    .withSerializer(mySerializer)
                    .build();

    public static void parseAndAct(SrcDstPair srcDstPair, String message) {
        String[] tokens = message.strip().split(":");
        HostMessageType type = parseAndGetType(tokens[0]);
        String value = preprocessValue(tokens[1], type);
        log.info(String.format("%s:%s", type, value));
        if (HOST_MESSAGES_MAP.containsKey(srcDstPair)) {
            if (HOST_MESSAGES_MAP.get(srcDstPair).containsKey(type)) {
                HOST_MESSAGES_MAP.get(srcDstPair).get(type).add(value);
            } else {
                HOST_MESSAGES_MAP.get(srcDstPair).put(type, new ArrayDeque<>());
                HOST_MESSAGES_MAP.get(srcDstPair).get(type).add(value);
            }
        } else {
            HOST_MESSAGES_MAP.put(srcDstPair, new HashMap<>());
            HOST_MESSAGES_MAP.get(srcDstPair).put(type, new ArrayDeque<>());
            HOST_MESSAGES_MAP.get(srcDstPair).get(type).add(value);
        }
    }

    private static String preprocessValue(String value, HostMessageType type) {
        if (HostMessageType.RATE_REQUEST.equals(type)) {
            return processDataRate(value);
        } else if (HostMessageType.DELAY_REQUEST.equals(type)) {
            return processDelay(value);
        }
        return "0";
    }

    private static String processDelay(String value) {
        int i = value.indexOf("ms");
        if (i > 0) {
            value = value.substring(0, i);
        }
        return value;
    }

    private static String processDataRate(String value) {
        int lastCharIndex = value.length() - 1;
        char mul = value.charAt(lastCharIndex);
        long rate = Long.parseLong(value.substring(0, lastCharIndex));
        if (mul == 'K') {
            rate *= 1000;
        } else if (mul == 'M') {
            rate *= 1000000;
        } else if (mul == 'G') {
            rate *= 1000000000;
        }
        return String.valueOf(rate);
    }

    public static Number getLatestMessage(SrcDstPair srcDstPair, HostMessageType hostMessageType) {
        String msg = Optional.ofNullable(HOST_MESSAGES_MAP.get(srcDstPair)).map(x -> x.get(hostMessageType)).map(Queue::poll).orElse(null);
        if (msg != null) {
            return hostMessageType.parse(msg);
        } else {
            return 0;
        }
    }

    private static HostMessageType parseAndGetType(String message) {
        message = message.toLowerCase().trim();
        if ("rate".equals(message)) {
            return HostMessageType.RATE_REQUEST;
        } else if ("delay".equals(message)) {
            return HostMessageType.DELAY_REQUEST;
        } else {
            return HostMessageType.UNKNOWN;
        }
    }
}
