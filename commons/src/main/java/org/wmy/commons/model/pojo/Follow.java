package org.wmy.commons.model.pojo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.wmy.commons.base.BaseModel;

/**
 * @author wmy
 * @create 2021-05-09 11:39
 */

@ApiModel(description = "食客关注实体类")
@Setter
@Getter
public class Follow extends BaseModel {

    @ApiModelProperty("用户Id")
    private int dinerId;
    @ApiModelProperty("关注用户的ID")
    private int followDinerId;
}
