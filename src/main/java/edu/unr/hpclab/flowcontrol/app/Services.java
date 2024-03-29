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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Services {
    private static final Map<ThreadsEnum, ThreadPoolExecutor> threadPoolExecutorMap = new HashMap<>();
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);
    public static CoreService coreService = DefaultServiceDirectory.getService(CoreService.class);
    public static ApplicationId appId = coreService.registerApplication("edu.unr.hpclab.flowcontrol");
    public static LinkService linkService = DefaultServiceDirectory.getService(LinkService.class);
    public static DeviceService deviceService = DefaultServiceDirectory.getService(DeviceService.class);
    public static FlowRuleService flowRuleService = DefaultServiceDirectory.getService(FlowRuleService.class);
    public static FlowStatisticService flowStatsService = DefaultServiceDirectory.getService(FlowStatisticService.class);
    public static HostService hostService = DefaultServiceDirectory.getService(HostService.class);
    public static NetworkConfigService configService = DefaultServiceDirectory.getService(NetworkConfigService.class);
    public static ComponentConfigService cfgService = DefaultServiceDirectory.getService(ComponentConfigService.class);
    public static PacketService packetService = DefaultServiceDirectory.getService(PacketService.class);
    public static FlowObjectiveService flowObjectiveService = DefaultServiceDirectory.getService(FlowObjectiveService.class);
    public static OpenFlowController openFlowControllerService = DefaultServiceDirectory.getService(OpenFlowController.class);
    public static StorageService storageService = DefaultServiceDirectory.getService(StorageService.class);
    public static PathService pathService = DefaultServiceDirectory.getService(PathService.class);
    public static ClusterService clusterService = DefaultServiceDirectory.getService(ClusterService.class);

    public static void deactivate() {
        scheduledExecutorService.shutdownNow();
        threadPoolExecutorMap.forEach((k, v) -> v.shutdownNow());
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, int time, int delay) {
        return scheduledExecutorService.scheduleAtFixedRate(runnable, time, delay, TimeUnit.SECONDS);
    }

    public static ScheduledFuture<?> scheduleAfterNSeconds(Runnable runnable, int delay) {
        return scheduledExecutorService.schedule(runnable, delay, TimeUnit.SECONDS);
    }

    public static ThreadPoolExecutor getExecutor(ThreadsEnum th) {
        return threadPoolExecutorMap.computeIfAbsent(th, (x) -> new ThreadPoolExecutor(th.getPoolSize(), th.getPoolSize(), 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(th.getQSize()), new ThreadPoolExecutor.DiscardPolicy()));
    }
}
