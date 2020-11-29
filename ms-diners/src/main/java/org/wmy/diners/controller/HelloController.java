package org.wmy.diners.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wmy
 * @create 2020-11-21 10:03
 */

@RestController
@RequestMapping("/hello")
public class HelloController {

    @GetMapping
    public String hello(String name){
        return "hello " + name;
    }
}
