package org.wmy.diners.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.diners.service.SendVerifyCodeService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author wmy
 * @create 2020-11-28 15:51
 */


@RestController
public class SendVerifyCodeController {
    @Resource
    private SendVerifyCodeService sendVerifyCodeService;
    @Resource
    private HttpServletRequest request;

    /**
     * 发送验证码
     *
     * @param phone
     * @return
     */
    @GetMapping("send")
    public ResultInfo send(String phone) {
        sendVerifyCodeService.send(phone);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "发送成功");
    }
}
