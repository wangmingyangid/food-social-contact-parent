package org.wmy.restaurants.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.wmy.commons.model.pojo.Reply;

import java.util.List;

/**
 * @author wmy
 * @create 2021-05-19 21:14
 */
public interface ReplyMapper {

    @Select("select * from t_reviews_reply where fk_reply_id=#{replyId} and fk_review_id=#{reviewId}")
    List<Reply> findSubReplyByReplyId(@Param("reviewId") Integer reviewId,@Param("replyId") Integer replyId);

    @Select("select * from t_reviews_reply where fk_reply_id is null")
    List<Reply> findSubReplyRootReplyId();
}
