package edu.unr.hpclab.flowcontrol.app;

import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.AvailableCapacityPathWeightFunction;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MaxSharedAvailableCapacityFunction;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MaxSharedAvailableCapacityPathComparator;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.PathCalculator;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.PathChangesComparator;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.PathDelayComparator;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.RateFitPathComparator;
import org.onlab.util.Tools;
import org.onosproject.net.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.unr.hpclab.flowcontrol.app.ThreadsEnum.SOLUTION_FINDER;

public class SolutionFinder {
    static Logger log = LoggerFactory.getLogger(SolutionFinder.class);

    private static int numberOfTotalMoves = 0;
    private static int priority = 21;

    public static void findSolutionAfterFlowTerminate() {  // When one of the flows leaves the network
        findSolution(null);
    }

    public static void findSolutionForDroppedLink(Link link) {    // When one bottleneck is detected
        findSolution(link);
    }

    private static void findSolution(Link link) {
        PathFinderAndRuleInstaller pathFinderAndRuleInstaller = new PathFinderAndRuleInstaller(priority++);

        Stream<SrcDstTrafficInfo> stream = CurrentTrafficDataBase.getCurrentTraffic().values().stream()
                .filter(s -> Util.ageInSeconds(s.getTimeStarted()) > Util.POLL_FREQ * 2)
                .filter(s -> s.getCurrentRate() > Util.MbpsToBps(2))
                .filter(s -> Util.ageInSeconds(s.getLatestMoveTime()) >= Util.POLL_FREQ * 2);

        if (link != null) {
            stream = stream.filter(srcDstTrafficInfo -> srcDstTrafficInfo.getCurrentPath().links().contains(link));
        }
        Map<Boolean, List<SrcDstTrafficInfo>> srcDstTrafficInfoListGroupedByRequestedRate = stream.collect(Collectors.groupingBy(x -> x.getRequestedRate() > 0));

        List<SrcDstTrafficInfo> flowsWithRequestedRate = srcDstTrafficInfoListGroupedByRequestedRate.get(true);
        List<SrcDstTrafficInfo> flowsWithoutRequestedRate = srcDstTrafficInfoListGroupedByRequestedRate.get(false);


        if (!Tools.isNullOrEmpty(flowsWithRequestedRate)) {
            Services.getExecutor(SOLUTION_FINDER).submit(() -> findSolutionForFlowsWithRequestedRate(flowsWithRequestedRate, pathFinderAndRuleInstaller));
        }
//        if (!Tools.isNullOrEmpty(flowsWithoutRequestedRate)) {
//            log.debug("Initial count of flows WITHOUT Requested Rate to find solution for is {}", flowsWithoutRequestedRate.size());
//            Services.getExecutor(SOLUTION_FINDER).submit(() -> findSolutionForFlowsWithoutRequestedRate(flowsWithoutRequestedRate, pathFinderAndRuleInstaller));
//        }
    }

    private static void findSolutionForFlowsWithRequestedRate(List<SrcDstTrafficInfo> srcDstTrafficInfoList, PathFinderAndRuleInstaller pathFinderAndRuleInstaller) {
        long t1 = System.currentTimeMillis();
        Function<MyPath, MyPath> function = AvailableCapacityPathWeightFunction.instance();
        srcDstTrafficInfoList.stream()
                .filter(s -> Util.safeDivision(s.getCurrentRate(), s.getRequestedRate()) < 0.75)
                .sorted(Comparator.comparing(s -> Util.safeDivision(s.getCurrentRate(), s.getRequestedRate())))
                .forEach(
                        s -> {
                            List<MyPath> sols = PathCalculator.getMyPathsList(s, function, getPathComparatorForPathsWithRequestedRateAndDelay(s));
                            inspectSols(s, sols);
                            MyPath sol = sols.get(0);
                            long newRate = Math.min(sol.getAvailableRate(), s.getRequestedRate());
                            double increaseRate = Util.safeDivision(newRate - s.getCurrentRate(), s.getCurrentRate());
                            if (increaseRate >= 0.25) { // If the solution isn't 25% better, don't move
                                log.info("Moved {} to a new path. It should have now {} instead of {}", s.getSrcDstPair(), newRate, s.getCurrentRate());
                                log.debug("Used This Solution:::Available Rate {}. Shared Rate {}. Bottleneck link {}", sol.getAvailableRate(), sol.getSharedRate(), sol.getBottleneckLink());
                                movingActions(pathFinderAndRuleInstaller, s, sol, newRate);
                            }
                        }
                );
        long t2 = System.currentTimeMillis() - t1;
        if (t2 > 50) {
            log.warn("Spent {} ms Inside findSolution For Flows WITH Requested Rate", t2);
        }
    }

    private static void inspectSols(SrcDstTrafficInfo s, List<MyPath> sols) {
        log.debug("Flow {} asked for {} while having {}. The solutions are as the following", s.getSrcDstPair(), s.getRequestedRate(), s.getCurrentRate());
        sols.forEach(ss -> log.debug("Available Rate {}. Max Available Shared Rate {}. Bottleneck link {}", ss.getAvailableRate(), ss.getMaxAvailableSharedRate(), ss.getBottleneckLink()));
        log.debug("########################################################################");
    }

    private static void findSolutionForFlowsWithoutRequestedRate(List<SrcDstTrafficInfo> flowsWithoutRequestedRate, PathFinderAndRuleInstaller pathFinderAndRuleInstaller) {
        long t1 = System.currentTimeMillis();
        Function<MyPath, MyPath> function = MaxSharedAvailableCapacityFunction.instance();
        List<MyPath> pathsTaken = new ArrayList<>();
        flowsWithoutRequestedRate.stream()
                .filter(s -> LinksInformationDatabase.getLinkUtilization(s.getCurrentPath().getBottleneckLink()) > 0.8)
                .forEach(
                        s -> {
                            List<MyPath> sols = PathCalculator.getMyPathsList(s, function, getPathComparatorForPathsWithoutRequestedRateAndDelay(s));
                            inspectSols(s, sols);
                            sols.removeAll(pathsTaken); // Making paths disjoint since I have no Idea about the next rate of this flow
                            int indexOfCurrentPath = sols.indexOf(s.getCurrentPath());
                            if (indexOfCurrentPath == 0) {
                                return; // Currently this is the best path
                            }
                            MyPath sol = sols.get(0);
                            long highestRecordedRate = s.getHighestRecordedRate();
                            long solMaxAvailableSharedRate = sol.getMaxAvailableSharedRate();
                            long newRate = Math.min(highestRecordedRate, solMaxAvailableSharedRate);
                            double increaseRate = Util.safeDivision(newRate - s.getCurrentRate(), s.getCurrentRate());
                            if (increaseRate >= 0.25) { // If the solution isn't 25% better, don't move
                                pathsTaken.add(sol);
                                movingActions(pathFinderAndRuleInstaller, s, sol, newRate);
                                log.info("Moved {} to a new path. It will have now roughly {} instead of {}", s.getSrcDstPair(), newRate, s.getCurrentRate());
                            }
                        }
                );
        long t2 = System.currentTimeMillis() - t1;
        if (t2 > 50) {
            log.warn("Spent {} ms Inside findSolution For Flows WITHOUT Requested Rate", t2);
        }
    }

    private static void movingActions(PathFinderAndRuleInstaller pathFinderAndRuleInstaller, SrcDstTrafficInfo s, MyPath sol, long newRate) {
        pathFinderAndRuleInstaller.installPathRules(s.getSrcDstPair(), sol);
        s.setCurrentPath(sol);
        s.setCurrentRate(newRate);
        s.increaseNumberOfMoves();
        s.setLatestMoveTime(System.currentTimeMillis());
        numberOfTotalMoves++;
    }


    private static Comparator<MyPath> getPathComparatorForPathsWithRequestedRateAndDelay(SrcDstTrafficInfo s) {
        return new RateFitPathComparator(s.getRequestedRate()).thenComparing(new PathChangesComparator(s.getCurrentPath())).thenComparing(new PathDelayComparator(s.getRequestedDelay()));
    }

    private static Comparator<MyPath> getPathComparatorForPathsWithoutRequestedRateAndDelay(SrcDstTrafficInfo s) {
        return MaxSharedAvailableCapacityPathComparator.instance().thenComparing(new PathChangesComparator(s.getCurrentPath()));
    }

    public static int getTotalNumberOfMoves() {
        return numberOfTotalMoves;
    }
}