package edu.unr.hpclab.flowcontrol.app;

import org.onlab.graph.ScalarWeight;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.EdgeLink;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.provider.ProviderId;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NewPathWeigher {
    public static final ScalarWeight INSTALLATION_COST = ScalarWeight.toWeight(0.2);
    private static final ProviderId PID = new ProviderId("flowcontrol", "edu.unr.hpclab.flowcontrol", true);
    private final int highestGlobalHopCount;
    private final long highestGlobalBandwidth;
    private final Map<Path, Long> pathLowestLinkBandwidth;
    private final Path currentPath;
    private final long currentRate;

    public NewPathWeigher(int highestGlobalHopCount, long highestGlobalBandwidth, Map<Path, Long> pathLowestLinkBandwidth, long currentRate, Path currentPath) {
        this.highestGlobalHopCount = highestGlobalHopCount;
        this.highestGlobalBandwidth = highestGlobalBandwidth;
        this.pathLowestLinkBandwidth = pathLowestLinkBandwidth;
        this.currentPath = currentPath;
        this.currentRate = currentRate;
    }


    public List<Path> getBestPaths(Collection<Path> paths) {
        return paths.stream()
                .map(p -> new DefaultPath(PID, p.links(), calculatePathCost(p)))
                .sorted(Comparator.comparing(Path::weight))
                .collect(Collectors.toList());
    }

    public ScalarWeight calculatePathCost(Path p) {
        double totalUtilization = 0;
        for (Link link : p.links()) {
            if (link instanceof EdgeLink) {
                continue;
            }
            totalUtilization += getUtilization(link);
        }
        int hopCount = (int) ((ScalarWeight) p.weight()).value();
        double avgUtilization = totalUtilization / (hopCount - 2);
        double normalizedBw = 1 - ((pathLowestLinkBandwidth.get(p) * 1.0 / highestGlobalBandwidth));
        double normalizeHopCount = (hopCount * 1.0 / highestGlobalHopCount) / 2;
        return ScalarWeight.toWeight(avgUtilization + normalizedBw + normalizeHopCount);
    }

    private double getUtilization(Link link) {
        double util;
        long bw = LinksInformationDatabase.getLinkEstimatedBandwidth(link);
        double linkUtil = LinksInformationDatabase.getLinkUtilization(link);
        if (this.currentRate != 0 && linkUtil != 0 && currentPath != null && !currentPath.links().contains(link)) {
            util = (linkUtil + (this.currentRate * 1.0 / bw));
        } else {
            util = Math.max(linkUtil, this.currentRate * 1.0 / bw);
        }
        return util;
    }
}
