package org.wmy.restaurants.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.wmy.commons.model.pojo.Reviews;

import java.util.List;

public interface ReviewsMapper {

    // 插入餐厅评论
    @Insert("insert into t_reviews (fk_restaurant_id, fk_diner_id, content, like_it, is_valid, create_date, update_date)" +
            " values (#{fkRestaurantId}, #{fkDinerId}, #{content}, #{likeIt}, #{isValid},#{createDate},#{updateDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int saveReviews(Reviews reviews);

    @Select("select * from t_reviews where fk_restaurant_id=#{restaurantId}")
    List<Reviews> getReviewsByRestaurantId(@Param("restaurantId") Integer restaurantId);

}
