package org.wmy.oauth2.server.service;

import org.springframework.beans.BeanUtils;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.wmy.commons.model.domain.SignInIdentity;
import org.wmy.commons.model.pojo.Diners;
import org.wmy.commons.utils.AssertUtil;
import org.wmy.oauth2.server.mapper.DinersMapper;

import javax.annotation.Resource;

/**
 * @author wmy
 * @create 2020-11-24 19:01
 */

@Service
public class UserService implements UserDetailsService {

    @Resource
    private DinersMapper dinersMapper;

    /**
     * 通过用户名进行验证，验证通过会给令牌，并保存到redis中
     *
     * @param username 此处的用户名包含：用户名 or 手机号 or 邮箱
     * @return
     * @throws UsernameNotFoundException
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AssertUtil.isNotEmpty(username,"请输入用户名");
        Diners diners = dinersMapper.selectByAccountInfo(username);
        if(diners == null){
            throw new UsernameNotFoundException("用户名或密码错误，请重新输入");
        }

        //初始化登录认证对象
        SignInIdentity signInIdentity = new SignInIdentity();
        //拷贝属性
        BeanUtils.copyProperties(diners,signInIdentity);

        return signInIdentity;
    }
}
