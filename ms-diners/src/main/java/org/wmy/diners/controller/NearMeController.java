package org.wmy.diners.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.vo.NearMeDinerVO;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.diners.service.NearMeService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
public class NearMeController {

    @Resource
    private NearMeService nearMeService;
    @Resource
    private HttpServletRequest request;


    /**
     *
     * @param access_token token
     * @param radius 查询半径
     * @param lon 经度
     * @param lat 纬度
     * @return
     */
    @GetMapping("findNearMe")
    public ResultInfo findNearMe(String access_token,Integer radius,
                                 @RequestParam Float lon,
                                 @RequestParam Float lat) {
        List<NearMeDinerVO> nearMeDinerVOS = nearMeService.findNearMe(access_token, radius, lon, lat);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),nearMeDinerVOS);
    }

    /**
     * 更新食客坐标
     * 一般情况下，客户端要定时获取用户的位置信息，并上传到服务器端进行更新
     *
     * @param access_token token
     * @param lon 经度
     * @param lat 纬度
     * @return
     */
    @PostMapping("updateLocation")
    public ResultInfo updateDinerLocation(String access_token,
                                          @RequestParam Float lon,
                                          @RequestParam Float lat) {
        nearMeService.updateDinerLocation(access_token, lon, lat);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "更新成功");
    }

}
