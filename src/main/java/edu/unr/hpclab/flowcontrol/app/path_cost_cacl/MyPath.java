package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import org.onlab.graph.ScalarWeight;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.Link;
import org.onosproject.net.provider.ProviderId;

import java.util.List;

public class MyPath extends DefaultPath {
    private static final ProviderId PID = new ProviderId("flowcontrol", "edu.unr.hpclab.flowcontrol", true);

    private Link bottleneckLink;
    private long availableRate;
    private double delay;
    private long maxAvailableSharedRate;
    private long sharedRate;

    public MyPath(List<Link> links) {
        super(PID, links, new ScalarWeight(0));
    }

    public Link getBottleneckLink() {
        return bottleneckLink;
    }

    public void setBottleneckLink(Link bottleneckLink) {
        this.bottleneckLink = bottleneckLink;
    }

    public long getSharedRate() {
        return sharedRate;
    }

    public void setSharedRate(long sharedRate) {
        this.sharedRate = sharedRate;
    }

    public long getMaxAvailableSharedRate() {
        return maxAvailableSharedRate;
    }

    public void setMaxAvailableSharedRate(long maxAvailableSharedRate) {
        this.maxAvailableSharedRate = maxAvailableSharedRate;
    }

    public long getAvailableRate() {
        return availableRate;
    }

    public void setAvailableRate(long availableRate) {
        this.availableRate = availableRate;
    }

    public double getDelay() {
        return delay;
    }

    public void setDelay(double delay) {
        this.delay = delay;
    }
}
