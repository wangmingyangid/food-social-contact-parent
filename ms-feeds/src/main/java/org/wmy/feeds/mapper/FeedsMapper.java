package org.wmy.feeds.mapper;

import org.apache.ibatis.annotations.*;
import org.wmy.commons.model.pojo.Feeds;

import java.util.List;
import java.util.Set;

/**
 * @author wmy
 * @create 2021-05-11 9:56
 */
public interface FeedsMapper {
    // 添加feed
    @Insert("insert into t_feed(content,fk_diner_id,praise_amount,comment_amount,fk_restaurant_id,create_date,update_date,is_valid) "+
                " values(#{content},#{fkDinerId},#{praiseAmount},#{commentAmount},#{fkRestaurantId},now(),now(),1)")
   @Options(useGeneratedKeys = true,keyProperty = "id")
    int insert(Feeds feed);

    // 删除feed - 软删除
    @Update("update t_feed set is_valid=0,update_date = now() where id = #{id} and is_valid = 1")
    int delete(@Param("id") Integer id);

    // 单条查询feed，注意筛选条件
    @Select("select * from t_feed where id =#{id} and is_valid = 1")
    Feeds find(@Param("id")Integer id);

    @Select("select id,update_date from t_feed where fk_diner_id=#{dinerId} and is_valid = 1")
    List<Feeds> findByDinnerId(@Param("dinerId") Integer dinerId);

    // 根据多主键查询 Feed
    @Select("<script> " +
            " select id, content, fk_diner_id, praise_amount, " +
            " comment_amount, fk_restaurant_id, create_date, update_date, is_valid " +
            " from t_feed where is_valid = 1 and id in " +
            " <foreach item=\"id\" collection=\"feedIds\" open=\"(\" separator=\",\" close=\")\">" +
            "   #{id}" +
            " </foreach> order by id desc" +
            " </script>")
    List<Feeds> findFeedsByIds(@Param("feedIds") Set<Integer> feedIds);

}
