package edu.unr.hpclab.flowcontrol.app;

import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.AvailableCapacityPathWeightFunction;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.PathCalculator;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.PathChangesComparator;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.RateFitPathComparator;
import org.onlab.util.DataRateUnit;
import org.onosproject.net.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SolutionFinder {
    static Logger log = LoggerFactory.getLogger(SolutionFinder.class);
    static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardPolicy());

    public static void findSolution(SrcDstTrafficInfo srcDstTrafficInfo) {  // When one of the flows leaves the network
        if (!SrcDstTrafficInfo.DUMMY_INSTANCE.equals(srcDstTrafficInfo)){
            if (srcDstTrafficInfo.getLatestReportedRate() < DataRateUnit.MBPS.toBitsPerSecond(5)) return;
        }
        List<SrcDstTrafficInfo> srcDstTrafficInfoList = new ArrayList<>(CurrentTrafficDataBase.getCurrentTraffic().values());
        threadPoolExecutor.submit(() -> findSolution(srcDstTrafficInfoList, 0));
    }

    private static void findSolution(List<SrcDstTrafficInfo> srcDstTrafficInfoList, int i) {
        Function<MyPath, MyPath> function = AvailableCapacityPathWeightFunction.instance();
        List<SrcDstTrafficInfo> solved = new LinkedList<>();
        int finalI = i;
        srcDstTrafficInfoList.stream()
                .filter(s -> Util.safeDivision(s.getCurrentRate(), s.getRequestedRate()) < 0.8)
                .sorted(Comparator.comparing(s -> Util.safeDivision(s.getCurrentRate(), s.getRequestedRate())))
                .forEach(
                        s -> {
                            List<MyPath> sols = PathCalculator.getMyPathsList(s, function, getMyPathComparator(s));
                            MyPath sol = sols.get(0);
                            double currentToRequestedRatio = Util.safeDivision(s.getCurrentRate(), s.getRequestedRate());
                            double solutionToRequestedRation = Util.safeDivision(Math.min(sol.getAvailableRate(), s.getRequestedRate()), s.getRequestedRate());
                            if (solutionToRequestedRation - currentToRequestedRatio >= 0.2) { // If the solution isn't 20% better, don't move
                                PathFinderAndRuleInstaller.installPathRules(s.getSrcDstPair(), sol);
                                s.setCurrentPath(sol);
                                s.setCurrentRate((long) (s.getRequestedRate() * solutionToRequestedRation));
                                solved.add(s);
                                log.info("Moved {} to a new path in the {} iteration", s.getSrcDstPair(), finalI);
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
        findSolution(srcDstTrafficInfoList, ++i);
    }


//        MyPath myPath = srcDstTrafficInfo.getCurrentPath();
//        Link bottleneckFreeLink = srcDstTrafficInfo.getCurrentPath().getBottleneckLink();
//        final long freeRate = srcDstTrafficInfo.getLatestReportedRate();
//        List<SrcDstTrafficInfo> flowsOfLink = getFlowsOfLink(bottleneckFreeLink);
//        long reqRates = flowsOfLink.stream().mapToLong(SrcDstTrafficInfo::getRequestedRate).sum();
//        long currentRates = flowsOfLink.stream().mapToLong(SrcDstTrafficInfo::getCurrentRate).sum();
//        long remaining = freeRate - (reqRates - currentRates);
//        if (remaining < 0) {
//            return; // The free rate space is still needed by the flows of that path
//        }
//        Function<MyPath, MyPath> function = AvailableCapacityPathWeightFunction.instance();
//        var solution = CurrentTrafficDataBase.getCurrentTraffic().values().stream()
//                .map(sdi -> srcDstTrafficInfo.getCurrentPath().links())
//                .map(links -> links.stream().filter(l -> !(l instanceof DefaultEdgeLink)).max(Comparator.comparing(LinksInformationDatabase::getLinkUtilization)))
//                .filter(Optional::isPresent)
//                .map(Optional::get)
//                .filter(l -> LinksInformationDatabase.getLinkUtilization(l) > 0.8)
//                .distinct()
//                .flatMap(l -> getFlowsOfLink(l).stream())
//                .filter(sdi -> Util.safeDivision(remaining, sdi.getRequestedDelay()) - Util.safeDivision(sdi.getCurrentRate(), sdi.getRequestedDelay()) >= 0.2)  // The flow that left is freeing a place that will give x1.5 times better rate
//                .sorted(Comparator.comparing(sdi -> sdi.getCurrentRate() * 1.0 / sdi.getRequestedRate()))
//                .map(sdi -> Map.entry(sdi, PathCalculator.getMyPathsList(sdi, function, getMyPathComparator(sdi))))//Get the solution for every sdi in the map
//                .filter(entry -> entry.getValue().indexOf(myPath) < entry.getValue().indexOf(entry.getKey().getCurrentPath()))// if the solution that contains the new free path is better than the existing path, use it.
//                .sorted(Comparator.comparing(entry -> entry.getValue().indexOf(myPath)))
//                .map(Map.Entry::getKey)
//                .findFirst();
//
//        if (solution.isPresent()) {
//            SrcDstTrafficInfo sol = solution.get();
//            PathFinderAndRuleInstaller.installPathRules(sol.getSrcDstPair(), myPath);
//            CurrentTrafficDataBase.remove(sol.getSrcDstPair());       // To trigger the findSolution function again
//            CurrentTrafficDataBase.addCurrentTraffic(sol.getSrcDstPair(), new SrcDstTrafficInfo(sol.getSrcDstPair(), myPath, sol.getRequestedRate(), sol.getRequestedDelay()));
//            log.info("Gave the path of {} to {}", srcDstTrafficInfo.getSrcDstPair(), sol.getSrcDstPair());
//        }

//    public static void findSolution(List<SrcDstTrafficInfo> srcDstTrafficInfos, long freeRate, Link freeBottleneckLink) {  // When one of the flows leaves the network
//        var sols = srcDstTrafficInfos.stream()
//                .filter(sdi -> sdi.getCurrentRate() * 1.0 / sdi.getRequestedRate() <= 0.6) // I am getting less than 60% of what I requested
//                .filter(sdi -> freeRate * 1.0 / sdi.getRequestedRate() >= 0.8)  // The flow that left is freeing a place that will give me more than 80% of what I requested
//                .sorted(Comparator.comparing(sdi -> sdi.getCurrentRate() * 1.0 / sdi.getRequestedRate()))
//                .map(sdi -> Map.entry(sdi, PathCalculator.getMyPathsList(sdi, function, getMyPathComparator(sdi))))
//                .filter(e -> e.getValue().contains(newFreePath))   // The path that the flow left is one of my solutions
//                .sorted(Comparator.comparing(e -> e.getValue().indexOf(newFreePath)))
//                .map(e -> Map.entry(e.getKey(), e.getValue().get(e.getValue().indexOf(newFreePath))))
//                .collect(Collectors.toList());
//
//        List<SrcDstTrafficInfo> solved = new LinkedList<>();
//        long freeRate1 = freeRate;
//        for (var sol : sols) {
//            if (freeRate1 > 0) {
//                SrcDstTrafficInfo trafficInfo = sol.getKey();
//                PathFinderAndRuleInstaller.installPathRules(trafficInfo.getSrcDstPair(), sol.getValue());
//                freeRate1 -= trafficInfo.getRequestedRate();
//                solved.add(trafficInfo);
//            }
//        }
//
//        for (var sol : solved) {
//            findSolution(sol);
//        }
//    }

    public static void findSolution(Link link) {    // When one bottleneck is detected
        long linkBw = LinksInformationDatabase.getLinkEstimatedBandwidth(link);
        List<SrcDstTrafficInfo> srcDstTrafficInfoList = CurrentTrafficDataBase.getCurrentTraffic().values().stream()
                .filter(srcDstTrafficInfo -> srcDstTrafficInfo.getCurrentPath().links().contains(link))
                .sorted(Comparator.comparing(SrcDstTrafficInfo::getRequestedRate))
                .collect(Collectors.toList());

        if (srcDstTrafficInfoList.size() == 0) {
            return;
        }

        int i = 0;
        long remaining = linkBw;
        while (remaining > 0 && i < srcDstTrafficInfoList.size()) {
            long rate = srcDstTrafficInfoList.get(i).getRequestedRate();
            if (rate <= remaining) {
                remaining -= rate;
                i++;
            } else {
                break;
            }
        }
        srcDstTrafficInfoList = srcDstTrafficInfoList.subList(i, srcDstTrafficInfoList.size());
        Function<MyPath, MyPath> function = AvailableCapacityPathWeightFunction.instance();
        for (SrcDstTrafficInfo s : srcDstTrafficInfoList) {
            List<MyPath> sols = PathCalculator.getMyPathsList(s, function, getMyPathComparator(s));
            MyPath sol = sols.get(0);
            double remainingToRequestedRatio = Util.safeDivision(remaining, s.getRequestedRate());
            double solutionToRequestedRation = Util.safeDivision(Math.min(sol.getAvailableRate(), s.getRequestedRate()), s.getRequestedRate());
            if (solutionToRequestedRation - remainingToRequestedRatio >= 0.2) { // If the solution is 20% better, move, else, keep it.
                PathFinderAndRuleInstaller.installPathRules(s.getSrcDstPair(), sol);
                s.setCurrentPath(sol);
                log.info("Moved {} to a new path", s.getSrcDstPair());
            } else {
                remaining = 0;
            }
        }
    }

    private static Comparator<MyPath> getMyPathComparator(SrcDstTrafficInfo s) {
        return new RateFitPathComparator(s.getRequestedRate()).thenComparing(new PathChangesComparator(s.getCurrentPath()));
    }

    static List<SrcDstTrafficInfo> getFlowsOfLink(Link link) {
        return CurrentTrafficDataBase.getCurrentTraffic().values().stream().filter(srcDstTrafficInfo -> srcDstTrafficInfo.getCurrentPath().links().contains(link))
                .collect(Collectors.toList());
    }
}
