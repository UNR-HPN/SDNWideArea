package edu.unr.hpclab.flowcontrol.app;

import java.util.ArrayDeque;

public class FixedSizeQueue<T> extends ArrayDeque<T> {
    private final int maxSize;

    public FixedSizeQueue(int size) {
        this.maxSize = size;
    }

    @Override
    public void addLast(T e) {
        super.addLast(e);
        if (this.size() > maxSize) {
            this.removeFirst();
        }
    }
}