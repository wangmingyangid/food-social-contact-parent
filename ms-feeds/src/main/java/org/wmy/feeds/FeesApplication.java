package org.wmy.feeds;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author wmy
 * @create 2021-05-11 17:52
 */

@SpringBootApplication
@MapperScan("org.wmy.feeds.mapper")
public class FeesApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeesApplication.class,args);
    }
}
