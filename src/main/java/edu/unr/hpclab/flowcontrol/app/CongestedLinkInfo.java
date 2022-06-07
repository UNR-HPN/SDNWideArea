package edu.unr.hpclab.flowcontrol.app;

import org.onosproject.net.Link;

import java.util.LinkedList;
import java.util.List;

public class CongestedLinkInfo {
    private final Link link;
    private final List<SrcDstTrafficInfo> srcDstTrafficInfoList;
    private final long linkBandwidth;
    private final long totalLinkLoad;

    private CongestedLinkInfo(CongestionInfoBuilder congestionInfoBuilder) {
        this.link = congestionInfoBuilder.link;
        this.srcDstTrafficInfoList = congestionInfoBuilder.srcDstTrafficInfos;
        this.linkBandwidth = LinksInformationDatabase.getLinkEstimatedBandwidth(this.link);
        this.totalLinkLoad = congestionInfoBuilder.totalLinkLoad;
    }

    public Link getLink() {
        return link;
    }

    public List<SrcDstTrafficInfo> getSrcDstTrafficInfoList() {
        return srcDstTrafficInfoList;
    }

    public long getLinkBandwidth() {
        return linkBandwidth;
    }

    public long getTotalLinkLoad() {
        return totalLinkLoad;
    }

    public static class CongestionInfoBuilder {
        private final Link link;
        private final List<SrcDstTrafficInfo> srcDstTrafficInfos = new LinkedList<>();
        private long totalLinkLoad;

        public CongestionInfoBuilder(Link link) {
            this.link = link;
        }

        public CongestionInfoBuilder withSrcDstPairTrafficInfo(SrcDstTrafficInfo srcDstTrafficInfo) {
            this.srcDstTrafficInfos.add(srcDstTrafficInfo);
            return this;
        }

        public CongestionInfoBuilder withTotalLinkLoad(long totalLinkLoad) {
            this.totalLinkLoad = totalLinkLoad;
            return this;
        }

        public CongestedLinkInfo build() {
            assert link != null;
            assert !srcDstTrafficInfos.isEmpty();
            assert totalLinkLoad > 0;
            return new CongestedLinkInfo(this);
        }
    }
}