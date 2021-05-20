package org.wmy.points;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author wmy
 * @create 2021-05-15 16:30
 */

@MapperScan("org.wmy.points.mapper")
@SpringBootApplication
public class PointsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PointsApplication.class,args);
    }
}
