package org.wmy.diners.controller;

import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.dto.DinersDTO;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.diners.service.DinersService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author wmy
 * @create 2020-11-25 21:03
 */

@RestController
@Api(tags = "食客相关接口")
public class DinersController {

    @Resource
    private DinersService dinersService;

    @Resource
    private HttpServletRequest request;

    /**
     * 注册
     * @param dinersDTO
     * @return
     */
    @PostMapping("register")
    public ResultInfo register(@RequestBody DinersDTO dinersDTO){
        return dinersService.register(dinersDTO,request.getServletPath());
    }


    /**
     * 校验手机号是否已注册
     * @return
     */
    @GetMapping("checkPhone")
    public ResultInfo checkPhone(String phone){
        dinersService.checkPhoneIsRegistered(phone);
        return ResultInfoUtil.buildSuccess(request.getServletPath());
    }

    /**
     * 登录
     * @param account   账号
     * @param password  密码
     * @return
     */
    @GetMapping("signin")
    public ResultInfo signIn(String account,String password){
        return dinersService.signIn(account,password,request.getServletPath());
    }
}
