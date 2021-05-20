package org.wmy.diners.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.diners.service.SignService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author wmy
 * @create 2021-05-14 16:27
 */

@RestController
public class SignController {
    @Resource
    private SignService signService;

    @Resource
    private HttpServletRequest request;


    /**
     * 获得月份的首次签到日期
     * @param access_token
     * @param date
     * @return
     */
    @GetMapping("firstSign")
    public ResultInfo firstSign(String access_token, String date) {
        String firstSign = signService.firstSign(access_token, date);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),firstSign);
    }

    /**
     * 获取用户签到情况 默认当月
     *
     * @param access_token
     * @param date         某个日期 yyyy-MM-dd
     * @return
     */
    @GetMapping("signInfo")
    public ResultInfo<Map<String, Boolean>> getSignInfo(String access_token, String date) {
        Map<String, Boolean> map = signService.getSignInfo(access_token, date);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), map);
    }



    /**
     * 获取签到次数
     * @param access_token
     * @param date
     * @return
     */
    @GetMapping("count")
    public ResultInfo getSignCount(String access_token,String date) {
        Long count = signService.getSignCount(access_token, date);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),count);
    }

    /**
     * 签到
     * @param access_token token
     * @param date 签到日期；可以补签
     * @return 返回连续签到数
     */
    @PostMapping("sign")
    public ResultInfo<Integer> doSign(String access_token,
                                      @RequestParam(required = false) String date) {
        int count = signService.doSign(access_token, date);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),count);
    }
}
