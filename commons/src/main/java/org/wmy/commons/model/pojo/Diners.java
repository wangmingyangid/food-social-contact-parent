package org.wmy.commons.model.pojo;

import lombok.Getter;
import lombok.Setter;
import org.wmy.commons.base.BaseModel;

/**
 * @author wmy
 * @create 2020-11-24 19:12
 */
/**
 * 食客实体类
 */
@Getter
@Setter
public class Diners extends BaseModel {

    // 主键
    private Integer id;
    // 用户名
    private String username;
    // 昵称
    private String nickname;
    // 密码
    private String password;
    // 手机号
    private String phone;
    // 邮箱
    private String email;
    // 头像
    private String avatarUrl;
    // 角色
    private String roles;

}