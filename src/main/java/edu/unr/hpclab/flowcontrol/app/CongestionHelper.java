package edu.unr.hpclab.flowcontrol.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.unr.hpclab.flowcontrol.app.Util.formatLinksPath;

public class CongestionHelper {
    static Logger log = LoggerFactory.getLogger(CongestionHelper.class);

    public static void solveCongestionFacade(Link link) {
        try {
            CongestedLinkInfo congestedLinkInfo = buildCongestionInfo(link);
            CongestionCause cause = findCongestionCause(congestedLinkInfo);
            boolean solved;
            String message = "";
            if (CongestionCause.NO_DATA.equals(cause)) {
                return;
            } else if (CongestionCause.MULTIPLE_IN_TRAFFIC.equals(cause)) {
                solved = solveMultipleTrafficIssueSolution(congestedLinkInfo, link);
                if (solved) {
                    message = String.format("MULTIPLE_IN_TRAFFIC issue was fully diverted for %s", link);
                }
            } else {
                solved = solveLowBandwidthIssue(congestedLinkInfo.getSrcDstTrafficInfoList().get(0));
                message = String.format("LOW_BANDWIDTH issue was solved for %s", link);
            }
            if (solved) {
                log.info(message);
            }
//            congestedLinkInfo.getSrcDstTrafficInfoList().forEach(x -> congestedSolutionsCache.put(x.getSrcDstPair(), 1L));
        } catch (Exception e) {
            log.error("", e);
        }
    }

    private static CongestedLinkInfo buildCongestionInfo(Link link) {
        CongestedLinkInfo.CongestionInfoBuilder congestionInfoBuilder = new CongestedLinkInfo.CongestionInfoBuilder(link);//edIngressPortsToLink(deviceId, -1, link);
        Map<SrcDstPair, SrcDstTrafficInfo> map = CurrentTrafficDataBase.getCurrentTraffic();
        map.forEach((k, v) -> {
            if (Util.ageInSeconds(v.getTimeStarted()) < Util.POLL_FREQ * 2) {
                return;
            }
            if (v.getCurrentPath().links().contains(link)) {
                congestionInfoBuilder.withSrcDstPairTrafficInfo(v);
            }
        });
        return congestionInfoBuilder.build();
    }

    public static CongestionCause findCongestionCause(CongestedLinkInfo congestedLinkInfo) {
        if (congestedLinkInfo.getSrcDstTrafficInfoList().size() > 1) {
            log.debug("There are multiple traffics passing by link {}", congestedLinkInfo);
            return CongestionCause.MULTIPLE_IN_TRAFFIC;
        }
        if (congestedLinkInfo.getSrcDstTrafficInfoList().size() == 0) {
            return CongestionCause.NO_DATA;
        } else {
            return CongestionCause.LOW_BANDWIDTH;
        }
    }

    private static boolean solveLowBandwidthIssue(SrcDstTrafficInfo srcDstTrafficInfo) {
        ArrayList<MyPath> pathsWithImprovement = getImprovementDifferenceOfBestSolutions(srcDstTrafficInfo);
        if (!pathsWithImprovement.isEmpty()) { // If the improvement is more than 5 Mbps, go ahead with it
            MyPath newPath = pathsWithImprovement.get(0);
            setupNewSolutionPath(srcDstTrafficInfo, newPath);
            log.info("New sol for srcDst {} pair with links\n: {}", srcDstTrafficInfo.getSrcDstPair(), formatLinksPath(newPath));
            return true;
        }
        return false;
    }

    private static ArrayList<MyPath> getImprovementDifferenceOfBestSolutions(SrcDstTrafficInfo srcDstTrafficInfo) {
        Path currentPath = srcDstTrafficInfo.getCurrentPath();
        List<MyPath> altPaths = srcDstTrafficInfo.getAltPaths();
        ArrayList<MyPath> solutions = new ArrayList<>();
        int index = altPaths.indexOf(currentPath);
        if (index == -1) {
            return solutions;
        }
        Weight currentPathWeight = altPaths.get(altPaths.indexOf(currentPath)).weight();
        for (int i = 0; i < altPaths.indexOf(currentPath); i++) {
            MyPath altPath = altPaths.get(i);
            if (((ScalarWeight) altPath.weight()).value() / ((ScalarWeight) currentPathWeight).value() > 1.2) {
                solutions.add(altPath);
            }
        }
        return solutions;
    }

    public static boolean solveMultipleTrafficIssueSolution(CongestedLinkInfo congestedLinkInfo, Link link) throws InterruptedException {
        ArrayList<SrcDstTrafficInfo> srcDstTrafficInfoList = new ArrayList<>(congestedLinkInfo.getSrcDstTrafficInfoList());
        srcDstTrafficInfoList.sort(Comparator.comparing(SrcDstTrafficInfo::getHighestRecordedRate));
        boolean solved = true;
        long rates = 0;
        long linkBW = LinksInformationDatabase.getLinkEstimatedBandwidth(link);
        for (SrcDstTrafficInfo s : srcDstTrafficInfoList) {
            rates += s.getHighestRecordedRate();
            if (rates < linkBW) {
                continue;
            }
            solved &= solveLowBandwidthIssue(s);
        }
        return solved;
    }

    private static void setupNewSolutionPath(SrcDstTrafficInfo srcDstTrafficInfo, MyPath altPath) {
        SrcDstPair srcDstPair = srcDstTrafficInfo.getSrcDstPair();
        PathFinderAndRuleInstaller.installPathRules(srcDstPair, altPath);
        srcDstTrafficInfo.setCurrentPath(altPath);
        CurrentTrafficDataBase.addCurrentTraffic(srcDstPair, srcDstTrafficInfo);
    }

    private static void outputCongestionToFile(CongestedLinkInfo congestedLinkInfo) {
        try (FileWriter fileWriter = new FileWriter("/home/oabuhamdan/CongestionInfo/info.txt", true)) {
            final ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.putArray(LocalDateTime.now().toString()).addAll(congestedLinkInfo.getSrcDstTrafficInfoList().stream().map(x -> x.getJsonNode(mapper)).collect(Collectors.toList()));
            String out = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            fileWriter.write(out);
            fileWriter.write("\n-----------\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    enum CongestionCause {
        MULTIPLE_IN_TRAFFIC, LOW_BANDWIDTH, NO_DATA
    }
}