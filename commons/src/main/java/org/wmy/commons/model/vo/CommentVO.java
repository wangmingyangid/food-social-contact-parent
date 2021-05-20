package org.wmy.commons.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.wmy.commons.model.pojo.Reviews;

import java.util.List;

/**
 * @author wmy
 * @create 2021-05-19 20:31
 */

@Getter
@Setter
@ApiModel(description = "多级餐厅评论实体类")
public class CommentVO {

    @ApiModelProperty("食客信息")
    private ShortDinerInfo dinerInfo;

    @ApiModelProperty("评论内容")
    String content;

    @ApiModelProperty("回复列表")
    List<ReplyVO> replys;
}
