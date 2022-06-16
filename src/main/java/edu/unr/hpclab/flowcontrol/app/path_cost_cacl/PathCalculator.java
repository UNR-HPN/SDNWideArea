package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import edu.unr.hpclab.flowcontrol.app.CurrentTrafficDataBase;
import edu.unr.hpclab.flowcontrol.app.LinksInformationDatabase;
import edu.unr.hpclab.flowcontrol.app.SrcDstPair;
import edu.unr.hpclab.flowcontrol.app.SrcDstTrafficInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.onosproject.net.DefaultEdgeLink;
import org.onosproject.net.EdgeLink;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.provider.ProviderId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.unr.hpclab.flowcontrol.app.Services.pathService;
import static org.onosproject.net.HostId.hostId;

public abstract class PathCalculator {
    protected final ProviderId PID = new ProviderId("flowcontrol", "edu.unr.hpclab.flowcontrol", true);

    public static List<MyPath> getPathsSortedByRateFit(SrcDstTrafficInfo srcDstTrafficInfo) {
        Function<MyPath, MyPath> function = AvailableCapacityPathWeightFunction.instance();
        Comparator<MyPath> myPathComparator = new RateFitPathComparator(srcDstTrafficInfo.getRequestedRate()).thenComparing(HopCountComparator.instance());
        return getMyPathsList(srcDstTrafficInfo, function, myPathComparator);
    }

    public static List<MyPath> getPathsSortedByRateDelayFit(SrcDstTrafficInfo srcDstTrafficInfo) {
        Function<MyPath, MyPath> function = AvailableCapacityPathWeightFunction.instance().andThen(PathDelayFunction.instance());
        Comparator<MyPath> myPathComparator = new RateFitPathComparator(srcDstTrafficInfo.getRequestedRate())
                .thenComparing(new PathDelayComparator(srcDstTrafficInfo.getRequestedDelay()))
                .thenComparing(HopCountComparator.instance());
        return getMyPathsList(srcDstTrafficInfo, function, myPathComparator);
    }


    public static List<MyPath> getPathsByMaxSharedAvailableCapacity(SrcDstTrafficInfo srcDstTrafficInfo) {
        Function<MyPath, MyPath> function = MaxSharedAvailableCapacityFunction.instance();
        Comparator<MyPath> myPathComparator = MaxSharedAvailableCapacityPathComparator.instance().thenComparing(HopCountComparator.instance());
        return getMyPathsList(srcDstTrafficInfo, function, myPathComparator);
    }

    public static List<MyPath> getMyPathsList(SrcDstTrafficInfo srcDstTrafficInfo, Function<MyPath, MyPath> function, Comparator<MyPath> comparator) {
        Collection<MyPath> paths = getKShortestPaths(srcDstTrafficInfo.getSrcDstPair());
        return paths.stream()
                .map(function)
                .sorted(comparator)
                .collect(Collectors.toList());
    }


    public static List<MyPath> getKShortestPaths(SrcDstPair srcDstPair) {
        Set<Path> paths = pathService.getKShortestPaths(hostId(srcDstPair.getSrcMac()), hostId(srcDstPair.getDstMac())).collect(Collectors.toCollection(LinkedHashSet::new));
        if (paths.isEmpty()) {
            return new ArrayList<>();
        }
        return paths.stream().map(x -> new MyPath(x.links())).collect(Collectors.toList());
    }

    protected Pair<Integer, Long> getCurrentRatesOfActiveFlows(Path path) {
        AtomicLong totalCap = new AtomicLong(0L);
        AtomicInteger totalNum = new AtomicInteger(0);
        HashSet<Link> pathLinks = new HashSet<>(path.links());
        pathLinks.removeIf(l -> l instanceof DefaultEdgeLink);
        CurrentTrafficDataBase.getCurrentTraffic().forEach((k, v) -> {
            HashSet<Link> intLinks = new HashSet<>(v.getCurrentPath().links());
            intLinks.removeIf(l -> l instanceof DefaultEdgeLink);
            if (!Collections.disjoint(pathLinks, intLinks)) {
                totalCap.addAndGet(v.getCurrentRate());
                totalNum.addAndGet(1);
            }
        });
        return Pair.of(totalNum.intValue(), totalCap.longValue());
    }
    protected Pair<Integer, Long> getCurrentRatesOfActiveFlows(Link link) {
        AtomicLong totalCap = new AtomicLong(0L);
        AtomicInteger totalNum = new AtomicInteger(0);
        CurrentTrafficDataBase.getCurrentTraffic().forEach((k, v) -> {
            if (v.getCurrentPath().links().contains(link)) {
                totalCap.addAndGet(v.getCurrentRate());
                totalNum.addAndGet(1);
            }
        });
        return Pair.of(totalNum.intValue(), totalCap.longValue());
    }

    protected Link getLowest_HighestLinkBandwidth(Path path, String type) { // type: lowest to get the lowest link bandwidth in the path, highest for vice versa
        long pathLowest = Long.MAX_VALUE;
        long pathHighest = 0;
        Link highestBw = null;
        Link lowestBw = null;
        for (Link link : path.links()) {
            if (link instanceof EdgeLink) {
                continue;
            }
            long linkbw = LinksInformationDatabase.getLinkEstimatedBandwidth(link);
            if (linkbw < pathLowest) {
                pathLowest = linkbw;
                lowestBw = link;
            }
            if (linkbw > pathHighest) {
                pathHighest = linkbw;
                highestBw = link;
            }
        }
        return type.equals("lowest") ? lowestBw : highestBw;
    }

    protected List<Path> getBestPaths(SrcDstPair srcDstPair, long rate, Path path) {
        return null;
    }
}