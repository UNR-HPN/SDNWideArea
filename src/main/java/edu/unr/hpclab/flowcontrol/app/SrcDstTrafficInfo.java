package edu.unr.hpclab.flowcontrol.app;

import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import org.onosproject.net.HostLocation;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.TcpPortCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class SrcDstTrafficInfo {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Services services = Services.getInstance();
    private final SrcDstPair srcDstPair;
    private final long timeStarted;
    private final long requestedRate;
    private final double requestedDelay;
    private final CurrentRateWatcher currentRateWatcher = new CurrentRateWatcher();
    private long timeFinished;
    private int numberOfMoves;
    private MyPath currentPath;
    private long currentRate;
    private long highestRecordedRate;
    private long latestMoveTime;
    private boolean active;

    public SrcDstTrafficInfo(SrcDstPair srcDstPair, MyPath currentPath) {
        this(srcDstPair, currentPath, 0, 0);
    }

    public SrcDstTrafficInfo(SrcDstPair srcDstPair, MyPath path, long requestedRate, double requestedDelay) {
        this.srcDstPair = srcDstPair;
        this.currentPath = path;
        this.requestedRate = requestedRate;
        this.requestedDelay = requestedDelay;
        this.active = true;
        this.timeStarted = System.currentTimeMillis();
        this.timeFinished = System.currentTimeMillis();
        this.latestMoveTime = System.currentTimeMillis();
        this.numberOfMoves = 0;
    }

    public long getTimeFinished() {
        return timeFinished;
    }

    public void setTimeFinished(long timeFinished) {
        this.timeFinished = timeFinished;
    }

    public long getLatestMoveTime() {
        return latestMoveTime;
    }

    public void setLatestMoveTime(long latestMoveTime) {
        this.latestMoveTime = latestMoveTime;
    }

    public int getNumberOfMoves() {
        return numberOfMoves;
    }

    public void increaseNumberOfMoves() {
        this.numberOfMoves++;
    }

    public double getRequestedDelay() {
        return requestedDelay;
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

    public boolean isActive() {
        return active;
    }

    public void setInactive() {
        this.active = false;
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
        return new StringJoiner(", ", SrcDstTrafficInfo.class.getSimpleName() + "[", "]")
                .add("srcDstPair=" + srcDstPair)
                .add("duration(s)=" + (timeFinished-timeStarted)/1000)
                .add("requestedRate=" + requestedRate)
                .add("requestedDelay=" + requestedDelay)
                .add("numberOfMoves=" + numberOfMoves)
                .add("highestRecordedRate=" + highestRecordedRate)
                .toString();
    }

    private class CurrentRateWatcher {
        private final Logger log = LoggerFactory.getLogger(getClass());

        private long latestReadTime;
        private long latestBytesMatched;

        void calc() {
            try {
                HostLocation host = Util.getHostByMac(srcDstPair.getSrcMac()).location();
                Predicate<FlowEntry> filter = fe -> ((EthCriterion) fe.selector().getCriterion(Criterion.Type.ETH_SRC)).mac().equals(srcDstPair.getSrcMac())
                        && ((EthCriterion) fe.selector().getCriterion(Criterion.Type.ETH_DST)).mac().equals(srcDstPair.getDstMac())
                        && ((TcpPortCriterion) fe.selector().getCriterion(Criterion.Type.TCP_SRC)).tcpPort().toInt() == srcDstPair.getSrcPort()
                        && ((TcpPortCriterion) fe.selector().getCriterion(Criterion.Type.TCP_DST)).tcpPort().toInt() == srcDstPair.getDstPort();

                Optional<FlowEntry> feo = StreamSupport.stream(services.flowRuleService.getFlowEntriesById(services.appId).spliterator(), false)
                        .filter(f -> f.deviceId().equals(host.elementId()))
                        .filter(filter).findFirst();

                if (feo.isPresent()) {
                    FlowEntry fe = feo.get();
                    long currentBytesMatched = fe.bytes();
                    if (this.latestReadTime == 0 || latestBytesMatched == 0) {
                        this.latestBytesMatched = currentBytesMatched;
                        this.latestReadTime = System.currentTimeMillis();
                    }
                    if (this.latestReadTime < fe.lastSeen()) {
                        currentRate = ((currentBytesMatched - this.latestBytesMatched) * 8 / Util.POLL_FREQ);
                        this.latestBytesMatched = currentBytesMatched;
                        this.latestReadTime = System.currentTimeMillis();
                    }
                } else {
                    currentRate = 0;
                    log.trace("No flow rules found for {}. Setting current rate for {}", srcDstPair, currentRate);
                }
                if (currentRate > 0) {
                    log.trace("Current Rate for {}: {}", srcDstPair, currentRate);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }
}
