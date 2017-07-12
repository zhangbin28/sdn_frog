package net.floodlightcontroller.frogqos;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by root on 17-7-3.
 */
public class FrogQos implements IFloodlightModule, IFrogQosService, ITopologyListener {
    private static Logger logger = LoggerFactory.getLogger(FrogQos.class);
    private ITopologyService topologyService;
    private IOFSwitchService switchService;
    private IStaticFlowEntryPusherService sfp;
    private QoSOnSwitch qoSOnSwitch;
    private IRestApiService restApiService;

    private String qos_uuid;

    private AtomicInteger queue_id;
    private Map<Integer, String> queueIdMap;

    private DatapathId dpid;

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
        l.add(IFrogQosService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<>();
        m.put(IFrogQosService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
        l.add(ITopologyService.class);
        l.add(IOFSwitchService.class);
        l.add(IStaticFlowEntryPusherService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        topologyService = context.getServiceImpl(ITopologyService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        sfp = context.getServiceImpl(IStaticFlowEntryPusherService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
        try {
            qoSOnSwitch = new QoSOnSwitch();
            qoSOnSwitch.clearQosAndQueue();
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
        queue_id = new AtomicInteger(0);
        queueIdMap = new HashMap<>();

        //restApiService.addRestletRoutable(new FrogQosWebRoutable());
    }


    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        topologyService.addListener(this);
        logger.info("init qos on switch begin");
        initQos();
        logger.info("init qos on switch over");
    }

    /**
     * 当拓扑发生变法时，会触发此函数
     *
     * @param linkUpdates
     */
    @Override
    public void topologyChanged(List<ILinkDiscovery.LDUpdate> linkUpdates) {
        try {
            qoSOnSwitch.setQosToPorts(qos_uuid);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
        for (ILinkDiscovery.LDUpdate linkUpdate : linkUpdates) {
            dpid = linkUpdate.getSrc();
            System.out.println(linkUpdate.getSrc());
        }
    }

    /**
     * 初始化交换机上的QOS
     */
    private void initQos() {
        try {
            qos_uuid = qoSOnSwitch.createQos("10000000");
            System.out.println(qos_uuid);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Override
    public Integer createQueue(String max_speed) {
        return createQueue(max_speed, null);
    }

    @Override
    public Integer createQueue(String max_speed, String min_speed) {
        String queue_uuid = null;
        try {
            if (min_speed == null) {
                queue_uuid = qoSOnSwitch.createQueue(max_speed);
            } else {
                queue_uuid = qoSOnSwitch.createQueue(max_speed, min_speed);
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
        if (queue_uuid == null) {
            return -1;
        }
        Integer queueId = queue_id.getAndIncrement();
        queueIdMap.put(queueId, queue_uuid);
        try {
            qoSOnSwitch.setQueuesToQos(qos_uuid, getQueuesStr());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
        return queueId;
    }

    @Override
    public void updateQueue(Integer queue_id, String max_speed) {
        updateQueue(queue_id, max_speed, null);
    }

    @Override
    public void updateQueue(Integer queue_id, String max_speed, String min_speed) {
        String queue_uuid = queueIdMap.get(queue_id);
        try {
            Integer retCode;
            if (min_speed == null) {
                retCode = qoSOnSwitch.updateQueue(queue_uuid, max_speed);
            } else {
                retCode = qoSOnSwitch.updateQueue(queue_uuid, max_speed, min_speed);
            }
            if (retCode != 0) {
                throw new Exception("update Queue Error");
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    @Override
    public void deleteQueue(Integer queue_id) {
        String queue_uuid = queueIdMap.get(queue_id);
        queueIdMap.remove(queue_id);
        try {
            // delete queue from qos
            qoSOnSwitch.setQueuesToQos(qos_uuid, getQueuesStr());
            // delete queue from db
            qoSOnSwitch.deleteQueue(queue_uuid);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    private String getQueuesStr() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<Integer, String> entry : queueIdMap.entrySet()) {
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
        }
        return stringBuilder.toString();
    }

}