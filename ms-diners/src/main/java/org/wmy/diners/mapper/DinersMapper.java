package org.wmy.diners.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.wmy.commons.model.dto.DinersDTO;
import org.wmy.commons.model.pojo.Diners;

/**
 * @author wmy
 * @create 2020-11-28 16:17
 */

public interface DinersMapper {

    // 根据手机号查询食客信息
    @Select("select id, username, phone, email, is_valid " +
            " from t_diners where phone = #{phone}")
    Diners selectByPhone(String phone);

    // 根据用户名查询食客信息
    @Select("select id, username, phone, email, is_valid " +
            " from t_diners where username = #{username}")
    Diners selectByUsername(String username);

    // 新增食客信息
    @Insert("insert into " +
            " t_diners (username, password, phone, roles, is_valid, create_date, update_date) " +
            " values (#{username}, #{password}, #{phone}, \"ROLE_USER\", 1, now(), now())")
    int save(DinersDTO dinersDTO);
}
