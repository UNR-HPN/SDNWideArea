package edu.unr.hpclab.flowcontrol.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import org.onlab.graph.ScalarWeight;
import org.onosproject.net.HostLocation;
import org.onosproject.net.Link;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.TcpPortCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static edu.unr.hpclab.flowcontrol.app.Services.appId;
import static edu.unr.hpclab.flowcontrol.app.Services.flowRuleService;


public class SrcDstTrafficInfo {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SrcDstPair srcDstPair;
    private final long timeStarted;
    private long requestedRate;
    private double requestedDelay;
    private MyPath currentPath;
    private List<MyPath> altPaths = new LinkedList<>();
    private long currentRate;
    private long highestRecordedRate;
    private final CurrentRateWatcher currentRateWatcher = new CurrentRateWatcher();

    public SrcDstTrafficInfo(SrcDstPair srcDstPair, MyPath currentPath) {
        this.srcDstPair = srcDstPair;
        this.currentPath = currentPath;
        timeStarted = new Date().getTime();
    }

    public SrcDstTrafficInfo(SrcDstPair srcDstPair, MyPath path, long requestedRate, double requestedDelay) {
        this(srcDstPair, path);
        this.requestedRate = requestedRate;
        this.requestedDelay = requestedDelay;
    }

    public double getRequestedDelay() {
        return requestedDelay;
    }

    public void setRequestedDelay(long requestedDelay) {
        this.requestedDelay = requestedDelay;
    }

    public long getRequestedRate() {
        return requestedRate;
    }

    public Long getTimeStarted() {
        return timeStarted;
    }

    public long getCurrentRate() {
        return currentRate;
    }

    public void setCurrentRate(long rate) {
        this.currentRate = rate;
        log.debug("Setting current rate of {} to {}", srcDstPair, rate);
    }

    public synchronized void setCurrentRate() {
        currentRateWatcher.calc();
        if (this.currentRate > highestRecordedRate) {
            this.highestRecordedRate = currentRate;
        }
    }

    public SrcDstPair getSrcDstPair() {
        return srcDstPair;
    }

    public long getHighestRecordedRate() {
        return highestRecordedRate;
    }

    public MyPath getCurrentPath() {
        return this.currentPath;
    }

    public void setCurrentPath(MyPath currentPath) {
        this.currentPath = currentPath;
    }

    public List<MyPath> getAltPaths() {
//        return this.altPaths = AbstractPathCalculator.getBestPaths(srcDstPair, highestRecordedRate, this.getCurrentPath());
        return this.altPaths = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SrcDstTrafficInfo that = (SrcDstTrafficInfo) o;
        return Objects.equals(srcDstPair, that.srcDstPair) && Objects.equals(currentPath, that.currentPath)
                && Objects.equals(timeStarted, that.timeStarted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcDstPair, currentPath, timeStarted);
    }

    @Override
    public String toString() {
        return super.toString();
//        ObjectMapper mapper = new ObjectMapper();
//        ObjectNode rootNode = getJsonNode(mapper);
//        try {
//            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
//        } catch (JsonProcessingException e) {
//            return "";
//        }
    }

    public ObjectNode getJsonNode(ObjectMapper mapper) {
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = mapper.createObjectNode();
        rootNode.set(srcDstPair.toString(), childNode);
        childNode.set("current_path", getPathJson(this.currentPath));
//        childNode.putArray("alt_paths")
//                .addAll(altPathsFormatter());
        return rootNode;
    }

    private List<ObjectNode> altPathsFormatter() {
        List<ObjectNode> paths = new LinkedList<>();
        for (MyPath p : this.altPaths) {
            ObjectNode pathJson = getPathJson(p);
            paths.add(pathJson);
        }
        return paths;
    }

    private ObjectNode getPathJson(MyPath p) {
        ObjectNode pathJson = new ObjectNode(JsonNodeFactory.instance);
        pathJson.put("cost", ((ScalarWeight) p.weight()).value());
        pathJson.put("hopCount", p.links().size());
        pathJson.putArray("links").addAll(formatLinksPath(p));
        return pathJson;
    }

    private List<ObjectNode> formatLinksPath(MyPath path) {
        List<ObjectNode> linksJson = new LinkedList<>();
        for (Link l : path.links()) {
            ObjectNode jo = new ObjectNode(JsonNodeFactory.instance);
            jo.put("src->dst", String.format("%s -> %s", l.src().toString(), l.dst()));
            jo.put("util", LinksInformationDatabase.getLinkUtilization(l));
            jo.put("bw", LinksInformationDatabase.getLinkEstimatedBandwidth(l));
            linksJson.add(jo);
        }

        return linksJson;
    }

   private class CurrentRateWatcher {
        private final Logger log = LoggerFactory.getLogger(getClass());

        private long latestReadTime;
        private long latestBytesMatched;

        void calc() {
            HostLocation host = Util.getHostByMac(srcDstPair.getSrcMac()).location();
            Predicate<FlowEntry> filter = fe -> ((EthCriterion) fe.selector().getCriterion(Criterion.Type.ETH_SRC)).mac().equals(srcDstPair.getSrcMac())
                    && ((EthCriterion) fe.selector().getCriterion(Criterion.Type.ETH_DST)).mac().equals(srcDstPair.getDstMac())
                    && ((TcpPortCriterion) fe.selector().getCriterion(Criterion.Type.TCP_SRC)).tcpPort().toInt() == srcDstPair.getSrcPort()
                    && ((TcpPortCriterion) fe.selector().getCriterion(Criterion.Type.TCP_DST)).tcpPort().toInt() == srcDstPair.getDstPort();

            Optional<FlowEntry> feo = StreamSupport.stream(flowRuleService.getFlowEntriesById(appId).spliterator(), false)
                    .filter(f -> f.deviceId().equals(host.elementId()))
                    .filter(filter).findFirst();

            if (feo.isPresent()) {
                FlowEntry fe = feo.get();
                long currentBytesMatched = fe.bytes();
                if (this.latestReadTime == 0 || latestBytesMatched == 0){
                    this.latestBytesMatched = currentBytesMatched;
                    this.latestReadTime = System.currentTimeMillis();
                }
                if (this.latestReadTime < fe.lastSeen()) {
                    currentRate = ((currentBytesMatched - this.latestBytesMatched) * 8 / Util.POLL_FREQ);
                    this.latestBytesMatched = currentBytesMatched;
                    this.latestReadTime = System.currentTimeMillis();
                    log.debug("Flow rule is present for {}. Setting current rate for {}", srcDstPair, currentRate);
                }
            }else {
                currentRate = 0;
                log.debug("No flow rules found for {}. Setting current rate for {}", srcDstPair, currentRate);
            }
            log.info("Current Rate for {}: {}", srcDstPair, currentRate);
            //        log.info("Current Rate (Device Service): {}", deviceService.getDeltaStatisticsForPort(host.deviceId(), host.port()).bytesReceived());
        }
    }
}
