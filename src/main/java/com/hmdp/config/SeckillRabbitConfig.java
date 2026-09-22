package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeckillRabbitConfig {
    public static final String EXCHANGE = "seckill.order.exchange";
    public static final String QUEUE = "seckill.order.queue";
    public static final String ROUTING_KEY = "seckill.order";

    public static final String DLX = "seckill.order.dlx";
    public static final String DLQ = "seckill.order.dlq";
    public static final String DLQ_ROUTING_KEY = "seckill.order.dead";

    @Bean
    public DirectExchange seckillExchange(){
        return new DirectExchange(EXCHANGE, true, false);
    }


    @Bean
    public Queue seckillQueue(){
        return QueueBuilder.durable(QUEUE) //创建持久化队列，RabbitMQ重启后仍存在
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY) //指定死信交换机和死信路由
                .build();
    }

    @Bean
    public Binding seckillBinding(){
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(ROUTING_KEY);
    }

    @Bean
    public DirectExchange deadLetterExchange(){
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue(){
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding(){
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ_ROUTING_KEY);
    }

    //Jackson2JsonMessageConverter，消息转换器，Java对象发送时自动转JSON，消费时转回对象
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }


}
