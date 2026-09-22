package com.hmdp.listener;

import com.hmdp.config.SeckillRabbitConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.OrderPersistService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderListener {
    private final OrderPersistService orderPersistService;

    public SeckillOrderListener(OrderPersistService orderPersistService) {
        this.orderPersistService = orderPersistService;
    }

    @RabbitListener(queues = SeckillRabbitConfig.QUEUE) //一直监听这个队列
    public void consume(VoucherOrder order){
        orderPersistService.createOrder(order);
    }
}
