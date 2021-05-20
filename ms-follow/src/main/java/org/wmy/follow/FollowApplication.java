package org.wmy.follow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author wmy
 * @create 2021-05-09 15:53
 */

@SpringBootApplication
@MapperScan("org.wmy.follow.mapper")
public class FollowApplication {
    public static void main(String[] args) {
        SpringApplication.run(FollowApplication.class,args);
    }
}
