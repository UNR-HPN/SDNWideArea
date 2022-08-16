package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import org.onosproject.net.Link;
import org.onosproject.net.Path;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RateToChangesComparator extends PathCalculator implements Comparator<MyPath> {
    private final long requestedRate;
    private final Path originalPath;

    public RateToChangesComparator(long requestedRate, Path originalPath) {
        this.requestedRate = requestedRate;
        this.originalPath = originalPath;
    }

    private int numberOfChanges(Path originalPath, Path newPath) {
        List<Link> intersection = originalPath.links().stream()
                .filter(newPath.links()::contains)
                .collect(Collectors.toList());

        return newPath.links().size() - intersection.size();
    }


    @Override
    public int compare(MyPath path1, MyPath path2) {
        double available1 = path1.getAvailableRate();
        double available2 = path2.getAvailableRate();
        double aMin1 = Math.min(requestedRate, available1);
        double aMin2 = Math.min(requestedRate, available2);
        double changes1 = Math.max(numberOfChanges(originalPath, path1) * 0.6, 1);
        double changes2 = Math.max(numberOfChanges(originalPath, path2) * 0.6, 1);
        return Double.compare(aMin1 / changes1, aMin2 / changes2) * -1;
    }
}
