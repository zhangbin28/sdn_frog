package net.floodlightcontroller.frogqos;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * Created by root on 17-7-3.
 * FrogQoS服务接口
 */
public interface IFrogQosService extends IFloodlightService {
    /**
     * 创建队列
     * @param max_speed 队列最大速度
     * @return 队列 int id
     */
    Integer createQueue(String max_speed);

    /**
     * 创建队列
     * @param max_speed 队列最大速度
     * @param min_speed 队列最小速度
     * @return 队列 int id
     */
    Integer createQueue(String max_speed, String min_speed);

    /**
     * 修改队列
     * @param queue_id 队列 int id
     * @param max_speed 队列最大速度
     */
    void updateQueue(Integer queue_id, String max_speed);

    /**
     * 修改队列
     * @param queue_id 队列 int id
     * @param max_speed 队列最大速度
     * @param min_speed 队列最小速度
     */
    void updateQueue(Integer queue_id, String max_speed, String min_speed);

    /**
     * 删除队列
     * @param queue_id 队列int id
     */
    void deleteQueue(Integer queue_id);
}