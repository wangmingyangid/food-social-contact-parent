package org.wmy.oauth2.server.mapper;

/**
 * @author wmy
 * @create 2020-11-24 19:06
 */

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.wmy.commons.model.pojo.Diners;

/**
 * 食客mapper
 */
public interface DinersMapper {

    // 根据用户名 or 手机号 or 邮箱查询用户信息
    @Select("select id, username, nickname, phone, email, " +
            "password, avatar_url, roles, is_valid from t_diners where " +
            "(username = #{account} or phone = #{account} or email = #{account})")
    Diners selectByAccountInfo(@Param("account") String account);
}
