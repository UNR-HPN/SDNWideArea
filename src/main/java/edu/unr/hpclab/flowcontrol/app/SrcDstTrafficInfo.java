package edu.unr.hpclab.flowcontrol.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import org.onlab.graph.ScalarWeight;
import org.onlab.util.DataRateUnit;
import org.onosproject.net.HostLocation;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.PortStatistics;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static edu.unr.hpclab.flowcontrol.app.Services.deviceService;


public class SrcDstTrafficInfo {
    public final static SrcDstTrafficInfo DUMMY_INSTANCE = new SrcDstTrafficInfo(null, null);
    private final SrcDstPair srcDstPair;
    private final long timeStarted;
    private long requestedRate;
    private double requestedDelay;
    private MyPath currentPath;
    private List<MyPath> altPaths = new LinkedList<>();
    private long currentRate;
    private long highestRecordedRate;
    private long latestReportedRate;


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

    public long getLatestReportedRate() {
        return latestReportedRate;
    }

    public long getRequestedRate() {
        return requestedRate;
    }

    public void setRequestedRate(long requestedRate) {
        this.requestedRate = requestedRate;
    }

    public Long getTimeStarted() {
        return timeStarted;
    }

    public long getCurrentRate() {
        setCurrentRate();   // recalculate it to get accurate results
        return currentRate;
    }

    public void setCurrentRate(long rate) {
        this.currentRate = rate;
    }

    public void setCurrentRate() {
        HostLocation host = Util.getHostByMac(srcDstPair.getSrc()).location();
        PortNumber portNumber = host.port();
        PortStatistics portStatistics = deviceService.getDeltaStatisticsForPort(host.deviceId(), portNumber);
        long rate = Util.getBytesReceivingRate(portStatistics);
        this.currentRate = rate;
        if (this.currentRate > highestRecordedRate) {
            this.highestRecordedRate = currentRate;
        }
        if (rate > DataRateUnit.MBPS.toBitsPerSecond(2)) {
            this.latestReportedRate = rate;
        }
    }

    public SrcDstPair getSrcDstPair() {
        return srcDstPair;
    }

    public long getHighestRecordedRate() {
        setCurrentRate();   // recalculate it to get accurate results
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
}
