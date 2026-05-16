package com.capstone.orderservice.config;

import com.capstone.orderservice.service.RedisReservationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisReservationListener reservationListener) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // Listen to key expiration events (requires 'notify-keyspace-events Ex' in redis.conf)
        container.addMessageListener(reservationListener, new PatternTopic("__keyevent@*__:expired"));
        
        return container;
    }
}
