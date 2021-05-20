package org.wmy.restaurants.service;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.wmy.commons.constant.RedisKeyConstant;
import org.wmy.commons.model.pojo.Restaurant;
import org.wmy.restaurants.RestaurantApplicationTest;
import org.wmy.restaurants.mapper.RestaurantMapper;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wmy
 * @create 2021-05-18 10:23
 */

@Slf4j
public class RestaurantTest extends RestaurantApplicationTest {

    @Resource
    private RestaurantMapper mapper;

    @Resource
    private RedisTemplate redisTemplate;

    // 采用管道进行插入
    @Test
    public void test02() {
        // 查询餐厅
        List<Restaurant> restaurants = mapper.findAll();
        long start = System.currentTimeMillis();
        redisTemplate.executePipelined((RedisCallback<Long>)connection -> {
            StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
            Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
            restaurants.forEach(restaurant -> {
                // 构建key
                String key = RedisKeyConstant.restaurants.getKey()+restaurant.getId();
                // 构建map
                Map<String, Object> map = BeanUtil.beanToMap(restaurant);
                HashMap<byte[], byte[]> valueMap = new HashMap<>();
                map.forEach((k,v)->{
                    valueMap.put(stringRedisSerializer.serialize(k),jackson2JsonRedisSerializer.serialize(v));
                });

                connection.hMSet(stringRedisSerializer.serialize(key),valueMap);

            });
            return null;
        });

        long end = System.currentTimeMillis();
        log.info("共计用时：",end-start);
    }

    // 逐行插入
    @Test
    public void test01() {
        // 查询餐厅
        List<Restaurant> restaurants = mapper.findAll();
        long start = System.currentTimeMillis();
        // 遍历循环，逐个插入缓存
        restaurants.forEach(restaurant -> {
            // 构建key
            String key = RedisKeyConstant.restaurants.getKey()+restaurant.getId();
            // 构建map
            Map<String, Object> map = BeanUtil.beanToMap(restaurant);
            redisTemplate.opsForHash().putAll(key,map);
        });
        long end = System.currentTimeMillis();
        log.info("共计用时：",end-start);
    }

    @Test
    public void test03() {
        redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            HashMap<String, String> map = new HashMap<>();
            map.put("姓名","网民");
            map.put("性别","男");
            map.put("年龄","18");
            HashMap<byte[], byte[]> byteMap = new HashMap<>();
            map.forEach((k,v)->{
                byteMap.put(k.getBytes(),v.getBytes());
            });
            connection.hMSet("user".getBytes(),byteMap);
            return null;
        });
    }
}
