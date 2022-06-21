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
import org.onosproject.net.DefaultEdgeLink;
import org.onosproject.net.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.unr.hpclab.flowcontrol.app.ThreadsEnum.*;

public class SolutionFinder {
    static Logger log = LoggerFactory.getLogger(SolutionFinder.class);
    private final static Services services = Services.getInstance();


    public static void findSolutionAfterFlowLeave() {  // When one of the flows leaves the network
        findSolution(null);
    }


    public static void findSolutionForDroppedLink(Link link) {    // When one bottleneck is detected
        findSolution(link);
    }

    private static void findSolution(Link link) {
        Stream<SrcDstTrafficInfo> stream = CurrentTrafficDataBase.getCurrentTraffic().values().stream();

        if (link != null){
            stream = stream.filter(srcDstTrafficInfo -> srcDstTrafficInfo.getCurrentPath().links().contains(link));
        }

        Map<Boolean, List<SrcDstTrafficInfo>> srcDstTrafficInfoListGroupedByRequestedRate = stream.collect(Collectors.groupingBy(x -> x.getRequestedRate() > 0));

        List<SrcDstTrafficInfo> flowsWithRequestedRate = srcDstTrafficInfoListGroupedByRequestedRate.get(true);
        List<SrcDstTrafficInfo> flowsWithoutRequestedRate = srcDstTrafficInfoListGroupedByRequestedRate.get(false);

        if (!Tools.isNullOrEmpty(flowsWithRequestedRate)) {
            services.getExecutor(SOLUTION_FINDER).submit(() -> findSolutionForFlowsWithRequestedRate(flowsWithRequestedRate, 0));
        }
        if (!Tools.isNullOrEmpty(flowsWithoutRequestedRate)) {
            services.getExecutor(SOLUTION_FINDER).submit(() -> findSolutionForFlowsWithoutRequestedRate(flowsWithoutRequestedRate));
        }
    }

    private static void findSolutionForFlowsWithRequestedRate(List<SrcDstTrafficInfo> srcDstTrafficInfoList, int i) {
        Function<MyPath, MyPath> function = AvailableCapacityPathWeightFunction.instance();
        List<SrcDstTrafficInfo> solved = new LinkedList<>();
        int finalI = i;
        srcDstTrafficInfoList.stream()
                .filter(s -> Util.safeDivision(s.getCurrentRate(), s.getRequestedRate()) < 0.8)
                .filter(s -> Util.ageInSeconds(s.getTimeStarted()) >= Util.POLL_FREQ * 2)
                .sorted(Comparator.comparing(s -> Util.safeDivision(s.getCurrentRate(), s.getRequestedRate())))
                .forEach(
                        s -> {
                            List<MyPath> sols = PathCalculator.getMyPathsList(s, function, getPathComparatorForPathsWithRequestedRateAndDelay(s));
                            MyPath sol = sols.get(0);
                            double currentToRequestedRatio = Util.safeDivision(s.getCurrentRate(), s.getRequestedRate());
                            double solutionToRequestedRatio = Util.safeDivision(Math.min(sol.getAvailableRate(), s.getRequestedRate()), s.getRequestedRate());
                            if (solutionToRequestedRatio / currentToRequestedRatio >= 1.5) { // If the solution isn't 20% better, don't move
                                PathFinderAndRuleInstaller.installPathRules(s.getSrcDstPair(), sol);
                                s.setCurrentPath(sol);
                                long newRate = (long) (s.getRequestedRate() * solutionToRequestedRatio);
                                log.info("Moved {} to a new path in the {} iteration. It will have now {} instead of {}", s.getSrcDstPair(), finalI, newRate, s.getCurrentRate());
                                s.setCurrentRate(newRate);
                                solved.add(s);
                            }
                        }
                );
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(Util.POLL_FREQ));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        srcDstTrafficInfoList.removeAll(solved);
        if (srcDstTrafficInfoList.isEmpty() || solved.isEmpty()) {
            return;
        }
        findSolutionForFlowsWithRequestedRate(srcDstTrafficInfoList, ++i);
    }


    private static void findSolutionForFlowsWithoutRequestedRate(List<SrcDstTrafficInfo> flowsWithoutRequestedRate) {
        Function<MyPath, MyPath> function = MaxSharedAvailableCapacityFunction.instance();
        flowsWithoutRequestedRate.stream()
                .filter(ct -> Util.ageInSeconds(ct.getTimeStarted()) > Util.POLL_FREQ * 2)
                .filter(s -> s.getCurrentPath().links().stream().anyMatch(l -> !(l instanceof DefaultEdgeLink) && LinksInformationDatabase.getLinkUtilization(l) > 0.8))
                .forEach(
                        s -> {
                            List<MyPath> sols = PathCalculator.getMyPathsList(s, function, getPathComparatorForPathsWithoutRequestedRateAndDelay(s));
                            MyPath sol = sols.get(0);
                            long highestRecordedRate = s.getHighestRecordedRate();
                            long solMaxAvailableSharedRate = sol.getMaxAvailableSharedRate();
                            long currentMaxAvailableSharedRate = sols.get(sols.indexOf(s.getCurrentPath())).getMaxAvailableSharedRate();
                            if (Util.safeDivision((solMaxAvailableSharedRate - highestRecordedRate), currentMaxAvailableSharedRate) >= 1.5) { // If the solution isn't 20% better, don't move
                                PathFinderAndRuleInstaller.installPathRules(s.getSrcDstPair(), sol);
                                s.setCurrentPath(sol);
                                long newRate = (long) (s.getRequestedRate() * 1.5);
                                s.setCurrentRate(newRate);
                                log.info("Moved {} to a new path. It will have now roughly {} instead of {}", s.getSrcDstPair(),  newRate, s.getCurrentRate());
                            }
                        }
                );
    }


    private static Comparator<MyPath> getPathComparatorForPathsWithRequestedRateAndDelay(SrcDstTrafficInfo s) {
        return new RateFitPathComparator(s.getRequestedRate()).thenComparing(new PathChangesComparator(s.getCurrentPath())).thenComparing(new PathDelayComparator(s.getRequestedDelay()));
    }

    private static Comparator<MyPath> getPathComparatorForPathsWithoutRequestedRateAndDelay(SrcDstTrafficInfo s) {
        return MaxSharedAvailableCapacityPathComparator.instance().thenComparing(new PathChangesComparator(s.getCurrentPath()));
    }
}
