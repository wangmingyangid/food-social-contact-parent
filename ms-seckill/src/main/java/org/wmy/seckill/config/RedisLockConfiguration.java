package org.wmy.seckill.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.wmy.seckill.model.RedisLock;

import javax.annotation.Resource;

@Configuration
public class RedisLockConfiguration {

    @Resource
    private RedisTemplate redisTemplate;

    @Bean
    public RedisLock redisLock() {
        RedisLock redisLock = new RedisLock(redisTemplate);
        return redisLock;
    }
}
