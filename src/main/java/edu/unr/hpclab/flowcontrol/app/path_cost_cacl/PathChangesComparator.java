package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;


import org.onosproject.net.Link;
import org.onosproject.net.Path;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PathChangesComparator implements Comparator<MyPath> {
    private final Path originalPath;

    public PathChangesComparator(Path originalPath) {
        this.originalPath = originalPath;
    }

    @Override
    public int compare(MyPath path1, MyPath path2) {
        return Integer.compare(numberOfChanges(originalPath, path1), numberOfChanges(originalPath, path2));
    }

    private int numberOfChanges(Path originalPath, Path newPath) {
        List<Link> intersection = originalPath.links().stream()
                .filter(newPath.links()::contains)
                .collect(Collectors.toList());

        return newPath.links().size() - intersection.size();
    }

}
