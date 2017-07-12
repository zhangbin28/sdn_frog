package net.floodlightcontroller.frogqos;

import org.apache.commons.io.IOUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by root on 17-7-3.
 */
public class QoSOnSwitch {
    private String QoS_UUID;
    private String QoS_Queue_Str = null;


    /**
     * clear qos and queue
     *
     * @throws Exception
     */
    public void clearQosAndQueue() throws Exception {
        String cmdQos = "ovs-vsctl --all destroy qos";
        Runtime.getRuntime().exec(cmdQos);
        String cmdQueue = "ovs-vsctl --all destroy queue";
        Runtime.getRuntime().exec(cmdQueue);
    }

    public List<String> findPorts() throws Exception {
        String cmd = "ovs-vsctl --column name find port";
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();
        String ret = IOUtils.toString(process.getInputStream());
        Pattern pattern = Pattern.compile("\".*\"");
        Matcher matcher = pattern.matcher(ret);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group());
        }
        return names;
    }


    public String createQos(String max_rate) throws Exception {
        String cmd = String.format("ovs-vsctl create qos type=linux-htb other-config:max-rate=%s", max_rate);
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();
        System.out.println(process.exitValue());
        this.QoS_UUID = IOUtils.toString(process.getInputStream()).trim();
        return QoS_UUID;
    }

    public String createQos(String max_rate, String min_rate) throws Exception {
        String cmd = String.format("ovs-vsctl create qos type=linux-htb other-config:max-rate=%s,min-rate=%s", max_rate, min_rate);
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();
        this.QoS_UUID = IOUtils.toString(process.getInputStream()).trim();
        return QoS_UUID;
    }


    public void setQosToPorts(String qos_uuid) throws Exception {
        List<String> ports = findPorts();
        for (String port : ports) {
            String cmd = String.format("sudo ovs-vsctl set port %s qos=%s", port, qos_uuid);
            cmd = cmd.replace("\"", "");
            System.out.println(cmd);
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        }
    }

    public void setQosToPorts(String qos_uuid, List<String> ports) throws Exception {
        if (ports == null) {
            return;
        }
        for (String port : ports) {
            setQosToPort(qos_uuid, port);
        }
    }

    public void setQosToPort(String qos_uuid, String port_name) throws Exception {
        String cmd = String.format("sudo ovs-vsctl set port %s qos=%s", port_name, qos_uuid);
        Runtime.getRuntime().exec(cmd);
    }


    public String createQueue(String max_rate) throws Exception {
        String cmd = String.format("ovs-vsctl create queue other-config:max-rate=%s", max_rate);
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();
        return IOUtils.toString(process.getInputStream()).trim();
    }

    public String createQueue(String max_rate, String min_rate) throws Exception {
        String cmd = String.format("ovs-vsctl create queue other-config:max-rate=%s,min-rate=%s", max_rate, min_rate);
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();
        return IOUtils.toString(process.getInputStream()).trim();
    }

    public Integer updateQueue(String queue_uuid, String max_rate) throws Exception {
        String cmd = String.format("ovs-vsctl set queue %s other_config:max-rate=%s", queue_uuid, max_rate);
        Process process = Runtime.getRuntime().exec(cmd);
        return process.waitFor();
    }

    public Integer updateQueue(String queue_uuid, String max_rate, String min_rate) throws Exception {
        String cmd = String.format("ovs-vsctl set queue %s other_config:max-rate=%s,min-rate=%s", queue_uuid, max_rate, min_rate);
        Process process = Runtime.getRuntime().exec(cmd);
        return process.waitFor();
    }

    public Integer deleteQueue(String queue_uuid) throws Exception {
        String cmd = String.format("ovs-vsctl destroy queue %s", queue_uuid);
        Process process = Runtime.getRuntime().exec(cmd);
        return process.waitFor();
    }

    public void setQueueToQos(String qos_uuid, int queue_id, String queue_uuid) throws Exception {
        if (QoS_Queue_Str == null) {
            QoS_Queue_Str = String.valueOf(queue_id) + "=" + queue_uuid + ",";
        } else {
            QoS_Queue_Str += String.valueOf(queue_id) + "=" + queue_uuid + ",";
        }
        setQueuesToQos(qos_uuid, QoS_Queue_Str);
    }

    public void setQueuesToQos(String qos_uuid, String queues_str) throws Exception {
        String cmd = String.format("ovs-vsctl set qos %s queues=%s", qos_uuid, queues_str);
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();
    }
}