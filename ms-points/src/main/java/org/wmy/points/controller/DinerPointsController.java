package org.wmy.points.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.vo.DinerPointsRankVO;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.points.service.DinerPointsService;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 积分控制层
 */
@RestController
public class DinerPointsController {

    @Resource
    private DinerPointsService dinerPointsService;
    @Resource
    private HttpServletRequest request;

    /**
     * 添加积分
     *
     * @param dinerId 食客ID
     * @param points  积分
     * @param types   类型 0=签到，1=关注好友，2=添加Feed，3=添加商户评论
     * @return
     */
    @PostMapping
    public ResultInfo<Integer> addPoints(@RequestParam(required = false) Integer dinerId,
                                         @RequestParam(required = false) Integer points,
                                         @RequestParam(required = false) Integer types) {
        dinerPointsService.addPoints(dinerId, points, types);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), points);
    }

    /**
     * 查询前 20 积分排行榜，同时显示用户排名
     *
     * @return
     */
    @GetMapping
    public ResultInfo<List<DinerPointsRankVO>> dinerPointsRank(String access_token) {
        List<DinerPointsRankVO> rank = dinerPointsService.findDinerPointRank(access_token);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), rank);
    }

    /**
     * 查询前 20 积分排行榜，同时显示用户排名 -- Redis
     *
     * @return
     */
    @GetMapping("redis")
    public ResultInfo<List<DinerPointsRankVO>> dinerPointsRankFromRedis(String access_token) {
        List<DinerPointsRankVO> rank = dinerPointsService.findDinerPointRankFromRedis(access_token);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), rank);
    }


}
