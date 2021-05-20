package org.wmy.commons.constant;

import lombok.Getter;

/**
 *
 * 积分类型：0=签到，1=关注好友，2=添加评论，3=点赞商户
 */
@Getter
public enum PointTypesConstant {

    sign(0),
    follow(1),
    feed(2),
    review(3)
    ;

    private int type;

    PointTypesConstant(int key) {
        this.type = key;
    }

}
