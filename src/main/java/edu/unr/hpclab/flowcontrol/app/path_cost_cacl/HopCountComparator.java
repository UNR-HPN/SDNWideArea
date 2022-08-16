package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import org.onosproject.net.Path;

import java.util.Comparator;

public class HopCountComparator implements Comparator<Path> {
    private static final Comparator<Path> INSTANCE = new HopCountComparator();

    private HopCountComparator() {
    }

    public static Comparator<? super Path> instance() {
        return INSTANCE;
    }

    @Override
    public int compare(Path path1, Path path2) {
        return Integer.compare(path1.links().size(), path2.links().size());
    }
}
