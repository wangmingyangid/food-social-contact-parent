package org.wmy.commons.base;

/**
 * @author wmy
 * @create 2020-11-24 19:13
 */

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 实体对象公共属性
 */
@Getter
@Setter
public class BaseModel implements Serializable {

    private Integer id;
    private Date createDate;
    private Date updateDate;
    private int isValid;

}
