package edu.unr.hpclab.flowcontrol.app;

import org.onlab.packet.MacAddress;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

public class SrcDstPair {
    private final MacAddress src;
    private final MacAddress dst;

    private int tcpPort;

    public SrcDstPair(MacAddress src, MacAddress dst) {
        this.src = src;
        this.dst = dst;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SrcDstPair srcDstPair = (SrcDstPair) o;
        return Objects.equals(src, srcDstPair.src) && Objects.equals(dst, srcDstPair.dst);
    }

    public MacAddress getSrc() {
        return src;
    }

    public MacAddress getDst() {
        return dst;
    }

    @Override
    public int hashCode() {
        return Objects.hash(src, dst);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("src->dst:", src + "->" + dst)
                .toString();
    }
}
