package org.wmy.points.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.wmy.commons.model.pojo.DinerPoints;
import org.wmy.commons.model.vo.DinerPointsRankVO;

import java.util.List;

/**
 * @author wmy
 * @create 2021-05-15 15:37
 */
public interface DinerPointMapper {

    // 添加积分
    @Insert("insert into t_diner_points(fk_diner_id,points,types,is_valid,create_date,update_date)"
            +" values(#{fkDinerId},#{points},#{types},1,now(),now())")
    void save(DinerPoints dinerPoints);

    // TODO 看 rank() 函数的功能
    // 查询积分排行榜 TOPN
    @Select("SELECT t1.fk_diner_id AS id, " +
            " sum( t1.points ) AS total, " +
            " rank () over ( ORDER BY sum( t1.points ) DESC ) AS ranks," +
            " t2.nickname, t2.avatar_url " +
            " FROM t_diner_points t1 LEFT JOIN t_diners t2 ON t1.fk_diner_id = t2.id " +
            " WHERE t1.is_valid = 1 AND t2.is_valid = 1 " +
            " GROUP BY t1.fk_diner_id " +
            " ORDER BY total DESC LIMIT #{top}")
    List<DinerPointsRankVO> findTopN(@Param("top") int top);

    // 根据食客 ID 查询当前食客的积分排名
    @Select("SELECT id, total, ranks, nickname, avatar_url FROM (" +
            " SELECT t1.fk_diner_id AS id, " +
            " sum( t1.points ) AS total, " +
            " rank () over ( ORDER BY sum( t1.points ) DESC ) AS ranks," +
            " t2.nickname, t2.avatar_url " +
            " FROM t_diner_points t1 LEFT JOIN t_diners t2 ON t1.fk_diner_id = t2.id " +
            " WHERE t1.is_valid = 1 AND t2.is_valid = 1 " +
            " GROUP BY t1.fk_diner_id " +
            " ORDER BY total DESC ) r " +
            " WHERE id = #{dinerId}")
    DinerPointsRankVO findDinerRank(@Param("dinerId") int dinerId);

}
