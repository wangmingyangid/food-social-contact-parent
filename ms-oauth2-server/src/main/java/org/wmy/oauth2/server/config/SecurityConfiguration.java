package org.wmy.oauth2.server.config;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;


import javax.annotation.Resource;

/**
 * @author wmy
 * @create 2020-11-23 20:45
 *
 * security 配置类
 */

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    //注入redis连接工厂
    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    //初始化RedisTokenStore 用于将token 存储至Redis
    @Bean
    public RedisTokenStore redisTokenStore(){
        RedisTokenStore redisTokenStore = new RedisTokenStore(redisConnectionFactory);
        redisTokenStore.setPrefix("TOKEN:");//设置key的层级前缀，方便查询
        return redisTokenStore;
    }

    //初始化密码管理器Md5加密
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new PasswordEncoder() {
            /**
             * 加密
             * @param charSequence 原始密码
             * @return 加密后的密码
             */
            @Override
            public String encode(CharSequence charSequence) {
                return DigestUtil.md5Hex(charSequence.toString());
            }

            /**
             * 校验密码
             * @param charSequence 原始密码
             * @param s 加密密码
             * @return
             */
            @Override
            public boolean matches(CharSequence charSequence, String s) {
                return DigestUtil.md5Hex(charSequence.toString()).equals(s);
            }
        };
    }

    //初始化认证管理对象

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    //放行和认证规则

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                //放行的请求
                .antMatchers("/oauth/**","/actuator/**").permitAll()
                .and()
                .authorizeRequests()
                //其它请求必须认证才能访问
                .anyRequest().authenticated();
    }
}
