package org.wmy.commons.constant;

import lombok.Getter;

@Getter
public enum RedisKeyConstant {

    verify_code("verify_code:", "验证码"),

    seckill_vouchers("seckill_vouchers:","秒杀劵的key"),

    lock_key("lockBy:","分布式锁的key")

    ;


    private String key;
    private String desc;

    RedisKeyConstant(String key, String desc) {
        this.key = key;
        this.desc = desc;
    }

}