package edu.unr.hpclab.flowcontrol.app;

import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cluster.ClusterService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.statistic.FlowStatisticService;
import org.onosproject.net.topology.PathService;
import org.onosproject.openflow.controller.OpenFlowController;
import org.onosproject.store.service.StorageService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Services {
    private static Services instance = null;
    private final Map<ThreadsEnum, ThreadPoolExecutor> map = new HashMap<>();
    public CoreService coreService = DefaultServiceDirectory.getService(CoreService.class);
    public final ApplicationId appId = coreService.registerApplication("edu.unr.hpclab.flowcontrol");
    public LinkService linkService = DefaultServiceDirectory.getService(LinkService.class);
    public DeviceService deviceService = DefaultServiceDirectory.getService(DeviceService.class);
    public FlowRuleService flowRuleService = DefaultServiceDirectory.getService(FlowRuleService.class);
    public FlowStatisticService flowStatsService = DefaultServiceDirectory.getService(FlowStatisticService.class);
    public HostService hostService = DefaultServiceDirectory.getService(HostService.class);
    public NetworkConfigService configService = DefaultServiceDirectory.getService(NetworkConfigService.class);
    public ComponentConfigService cfgService = DefaultServiceDirectory.getService(ComponentConfigService.class);
    public PacketService packetService = DefaultServiceDirectory.getService(PacketService.class);
    public FlowObjectiveService flowObjectiveService = DefaultServiceDirectory.getService(FlowObjectiveService.class);
    public OpenFlowController openFlowControllerService = DefaultServiceDirectory.getService(OpenFlowController.class);
    public StorageService storageService = DefaultServiceDirectory.getService(StorageService.class);
    public PathService pathService = DefaultServiceDirectory.getService(PathService.class);
    public ClusterService clusterService = DefaultServiceDirectory.getService(ClusterService.class);

    public static Services getInstance() {   //Singleton
        if (instance == null) {
            instance = new Services();
        }
        return instance;
    }

    public ThreadPoolExecutor getExecutor(ThreadsEnum th) {
        return map.computeIfAbsent(th, (x) -> new ThreadPoolExecutor(th.getPoolSize(), th.getPoolSize(), 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(th.getQSize()), new ThreadPoolExecutor.DiscardPolicy()));
    }
}
