package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import edu.unr.hpclab.flowcontrol.app.LinksInformationDatabase;
import edu.unr.hpclab.flowcontrol.app.SrcDstPair;
import org.apache.commons.lang3.tuple.Pair;
import org.onlab.graph.ScalarWeight;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.Link;
import org.onosproject.net.Path;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AvailableCapacityPathCalculatorSingleton extends PathCalculator implements Comparator<Path> {
    protected List<Path> getBestPaths(SrcDstPair srcDstPair, long rate, Path path) {
        List<MyPath> paths = getKShortestPaths(srcDstPair);
        Map<Path, Link> pathToLowestBandwidth = paths.stream().collect(Collectors.toMap(Function.identity(), k -> getLowest_HighestLinkBandwidth(k, "lowest")));
        Map<Path, Integer> pathToFlowNumber = new HashMap<>();
        return pathToLowestBandwidth.entrySet().stream().map(e -> {
                    Pair<Integer, Long> flowsToTotalRate = getCurrentRatesOfActiveFlows(e.getKey());
                    pathToFlowNumber.put(e.getKey(), flowsToTotalRate.getLeft());
                    long lowestBw = LinksInformationDatabase.getLinkEstimatedBandwidth(e.getValue());
                    long totalRate = flowsToTotalRate.getRight();
                    int noFlows = flowsToTotalRate.getLeft();
                    if (path != null && !path.equals(e.getKey())) {
                        totalRate = Math.min(totalRate + rate, lowestBw);
                    }
                    if (path == null || !path.equals(e.getKey()) || noFlows == 0) {
                        noFlows += 1;
                    }
                    long availableCapacity = lowestBw - totalRate; // Lowest Link BW - total Rate passing by that link
                    long shareRate = lowestBw / noFlows;    // Lowest BW / number of flows passing by the link
                    long cost = Math.max(shareRate, availableCapacity);
                    return (Path) new DefaultPath(PID, e.getKey().links(), ScalarWeight.toWeight(cost));
                })
                .sorted(Comparator.comparing(Path::weight).reversed().thenComparing(pathToFlowNumber::get).thenComparing(p -> p.links().size()))
                .collect(Collectors.toList());
    }

    @Override
    public int compare(Path path, Path t1) {
        return 0;
    }
}
