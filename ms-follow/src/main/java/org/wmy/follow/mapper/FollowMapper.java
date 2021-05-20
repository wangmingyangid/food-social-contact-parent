package org.wmy.follow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.wmy.commons.model.pojo.Follow;

/**
 * @author wmy
 * @create 2021-05-09 11:44
 */

public interface FollowMapper {
    // 查询关注信息
    @Select("select id,diner_id,follow_diner_id,is_valid from t_follow " +
                " where diner_id = #{dinerId} and follow_diner_id = #{followDinerId}")
    Follow selectFollow(@Param("dinerId") Integer dinerId, @Param("followDinerId") Integer followDinerId);

    // 添加关注信息
    @Insert("insert into t_follow(diner_id,follow_diner_id,is_valid,create_date,update_date) " +
                " values(#{dinerId},#{followDinerId},1,now(),now())")
    int save(@Param("dinerId") Integer dinerId, @Param("followDinerId") Integer followDinerId);

    // 修改关注休息
    @Update("update t_follow set is_valid = #{isFollowed} , update_date= now() where id = #{id}")
    int update(@Param("id") Integer id, @Param("isFollowed") Integer isFollowed);
}
