package org.wmy.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.dto.DinersDTO;
import org.wmy.commons.model.pojo.Diners;
import org.wmy.commons.model.vo.ShortDinerInfo;
import org.wmy.commons.utils.AssertUtil;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.diners.config.OAuth2ClientConfiguration;
import org.wmy.diners.domain.OAuthDinerInfo;
import org.wmy.diners.mapper.DinersMapper;
import org.wmy.diners.vo.LoginDinerInfo;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author wmy
 * @create 2020-11-25 18:13
 * <p>
 * 食客服务业务逻辑层
 */

@Service
public class DinersService {

    @Resource
    private RestTemplate restTemplate;

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Resource
    private OAuth2ClientConfiguration oAuth2ClientConfiguration;
    @Resource
    private DinersMapper dinersMapper;
    @Resource
    private SendVerifyCodeService sendVerifyCodeService;



    /**
     * 根据ids查询食客信息
     * @param ids 多个主键 id ，之间以逗号分隔
     * @return
     */
    public List<ShortDinerInfo> findByIds(String ids) {
        AssertUtil.isNotEmpty(ids);
        String[] idAddr = ids.split(",");
        List<ShortDinerInfo> dinerInfos = dinersMapper.findByIds(idAddr);
        return dinerInfos;
    }

    /**
     * 注册用户
     *
     * @param dinersDTO
     * @param path
     * @return
     */
    public ResultInfo register(DinersDTO dinersDTO, String path) {
        //参数非空校验
        String username = dinersDTO.getUsername();
        AssertUtil.isNotEmpty(username, "请输入用户名");
        String password = dinersDTO.getPassword();
        AssertUtil.isNotEmpty(password, "请输入密码");
        String phone = dinersDTO.getPhone();
        AssertUtil.isNotEmpty(phone, "请输入手机号");
        String code = dinersDTO.getVerifyCode();
        AssertUtil.isNotEmpty(code, "请输入验证码");
        //获得验证码
        String verifyCode = sendVerifyCodeService.getCodeByPhone(phone);
        //验证码是否过期
        AssertUtil.isNotEmpty(verifyCode, "验证码已过期，请重新输入");
        //验证码一致性校验
        AssertUtil.isTrue(!code.equals(verifyCode), "验证码不一致，请重新输入");
        //验证用户名是否已注册
        Diners diners = dinersMapper.selectByUsername(username.trim());
        AssertUtil.isTrue(diners != null, "用户名已注册");
        //密码加密
        dinersDTO.setPassword(DigestUtil.md5Hex(password.trim()));
        //注册
        dinersMapper.save(dinersDTO);
        //自动登录
        return signIn(username.trim(), password.trim(), path);
    }

    /**
     * 校验手机号是否已注册
     *
     * @param phone
     */
    public void checkPhoneIsRegistered(String phone) {
        AssertUtil.isNotEmpty(phone, "请输入手机号");
        Diners diners = dinersMapper.selectByPhone(phone);
        AssertUtil.isTrue(diners == null, "手机号未注册");
        AssertUtil.isTrue(diners.getIsValid() == 0, "该用户已锁定，请联系管理员");
    }

    /**
     * @param account  账号：用户名或邮箱或手机号
     * @param password 密码
     * @param path     访问路径
     * @return
     */
    public ResultInfo signIn(String account, String password, String path) {
        //参数校验
        AssertUtil.isNotEmpty(account, "请输入用户名");
        AssertUtil.isNotEmpty(password, "请输入登录密码");
        //构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        //构建请求体（请求参数）
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("username", account);
        body.add("password", password);
        body.setAll(BeanUtil.beanToMap(oAuth2ClientConfiguration));
        //设置 Authorization
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(oAuth2ClientConfiguration.getClientId(),
                oAuth2ClientConfiguration.getSecret()));
        //组装请求头与请求体
        HttpEntity<MultiValueMap<String, Object>> httpEntity = new HttpEntity<>(body, headers);
        //发送请求
        ResponseEntity<ResultInfo> result = restTemplate.postForEntity(oauthServerName + "oauth/token", httpEntity, ResultInfo.class);
        //处理返回结果
        AssertUtil.isTrue(result.getStatusCode() != HttpStatus.OK, "登录失败");
        ResultInfo resultInfo = result.getBody();
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            //登录失败
            resultInfo.setData(resultInfo.getMessage());
            return resultInfo;
        }
        // 这里的 Data 是一个 LinkedHashMap 转成了域对象 OAuthDinerInfo
        OAuthDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new OAuthDinerInfo(), false);
        // 根据业务需求返回视图对象
        LoginDinerInfo loginDinerInfo = new LoginDinerInfo();
        loginDinerInfo.setToken(dinerInfo.getAccessToken());
        loginDinerInfo.setAvatarUrl(dinerInfo.getAvatarUrl());
        loginDinerInfo.setNickname(dinerInfo.getNickname());
        return ResultInfoUtil.buildSuccess(path, loginDinerInfo);

    }
}
