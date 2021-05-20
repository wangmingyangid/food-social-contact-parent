package org.wmy.restaurants;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author wmy
 * @create 2021-05-18 10:17
 */

@SpringBootApplication
@MapperScan("org.wmy.restaurants.mapper")
public class RestaurantApplication {
    public static void main(String[] args) {
        SpringApplication.run(RestaurantApplication.class,args);
    }

}
