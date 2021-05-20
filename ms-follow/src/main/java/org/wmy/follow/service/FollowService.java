package org.wmy.follow.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.constant.RedisKeyConstant;
import org.wmy.commons.exception.ParameterException;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.pojo.Follow;
import org.wmy.commons.model.vo.ShortDinerInfo;
import org.wmy.commons.model.vo.SignInDinerInfo;
import org.wmy.commons.utils.AssertUtil;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.follow.mapper.FollowMapper;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author wmy
 * @create 2021-05-09 11:45
 */

@Service
public class FollowService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Value("${service.name.ms-diners-server}")
    private String dinerServerName;

    @Value("${service.name.ms-feeds-server}")
    private String feedsServerName;

    @Resource
    private FollowMapper mapper;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 获取粉丝列表
     * @return
     */
    public Set<Integer> findFollowers(Integer id) {
        AssertUtil.isNotNull(id,"请选择查看的用户");
        Set<Integer> followers = redisTemplate.opsForSet().members(RedisKeyConstant.followers.getKey()+id);
        return followers;
    }


    /**
     * 获得共同关注的好友
     * @param dinerId  被浏览的食客（关注对象）
     * @param accessToken  根据token，可以从认证中心获得登录用户的信息
     * @param path 访问地址
     * @return
     */
    public ResultInfo findCommonsFriends(Integer dinerId,String accessToken,String path) {
        // 是否选择了关注对象
        AssertUtil.isTrue(dinerId == null,"请选择关注对象");
        // 从认证中心获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 计算交集
        String loginDinnerKey = RedisKeyConstant.following.getKey()+dinerInfo.getId();
        String dinerKey = RedisKeyConstant.following.getKey()+dinerId;
        Set<Integer> followingDinerIds = redisTemplate.opsForSet().intersect(loginDinnerKey, dinerKey);
        // 调用食客服务，批量获取食客信息
        // TODO 返回的数据为啥是List<LinkedHashMap>
        ResultInfo resultInfo = restTemplate.getForObject(dinerServerName + "findByIds?access_token={access_token}&ids={ids}",
                ResultInfo.class, accessToken, StrUtil.join(",",followingDinerIds));
        if (resultInfo.getCode()!=ApiConstant.SUCCESS_CODE){
            resultInfo.setPath(path);
            return resultInfo;
        }
        //处理结果集
        List<LinkedHashMap> dinerInfoMaps = (ArrayList)resultInfo.getData();
        List<ShortDinerInfo> shortDinerInfos = dinerInfoMaps.stream()
                .map(diner -> BeanUtil.fillBeanWithMap(diner, new ShortDinerInfo(), true))
                .collect(Collectors.toList());
        return ResultInfoUtil.buildSuccess(path,shortDinerInfos);

    }

    /**
     *
     * @param followDinerId 被关注的食客的ID
     * @param accessToken 根据token，可以从认证中心获得登录用户的信息
     * @param isFollowed 是否关注，1-表示关注 0-取消关注
     * @param path 访问地址
     * @return
     */
    public ResultInfo follow(Integer followDinerId,String accessToken,
                             int isFollowed,String path) {

        // 非空校验
        AssertUtil.isTrue(followDinerId == null || followDinerId < 1,
                            "请选择要关注的人");
        AssertUtil.mustLogin(accessToken);
        //从认证中心获取用户信息
        SignInDinerInfo dinerInfo =  loadSignInDinnerInfo(accessToken);
        // 获得关注信息
        Follow follow = mapper.selectFollow(dinerInfo.getId(), followDinerId);
        // 如果没有关注信息，且要进行关注操作
        if (follow == null && isFollowed ==1) {
            int save = mapper.save(dinerInfo.getId(), followDinerId);
            // 在redis中添加关注列表
            if (save == 1) {
                addToRedisSet(dinerInfo.getId(),followDinerId);
                // 保存 feed
                sendSaveOrRemoveFeed(followDinerId,accessToken,1);
            }
            return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE,"关注成功",path,"关注成功");
         }
        // 如果有关注信息，且关注信息有效，要进行取关操作
        if (follow != null && follow.getIsValid() == 1 && isFollowed == 0) {
            int update = mapper.update(follow.getId(), isFollowed);
            if (update == 1) {
                removeFromRedisSet(dinerInfo.getId(),followDinerId);
                // 删除feed
                sendSaveOrRemoveFeed(followDinerId,accessToken,0);
            }
            return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE,"成功取关",path,"成功取关");
        }
        // 如果有关注信息，且关注信息无效，要进行重新关注
        if (follow != null && follow.getIsValid() == 0 && isFollowed == 1) {
            int update = mapper.update(follow.getId(), isFollowed);
            if (update == 1) {
                addToRedisSet(dinerInfo.getId(),followDinerId);
                // 保存 feed
                sendSaveOrRemoveFeed(followDinerId,accessToken,1);
            }
            return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE,"关注成功",path,"关注成功");
        }
        return ResultInfoUtil.buildSuccess(path,"操作成功");
    }

    /**
     * 远程调用feeds服务进行 feed 添加或删除
     * @param followingDinerId 关注/取关 的人Id
     * @param accessToken token
     * @param type 1--关注 0--取关
     */
    private void sendSaveOrRemoveFeed(Integer followingDinerId,String accessToken,int type) {
        String url =feedsServerName+"updateFollowingFeeds/"+followingDinerId+"?access_token="+accessToken;
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        // 在请求头中设置请求体的内容类型
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 构建请求体
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("type",type);
        // 构建请求
        HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        // 发送请求
        restTemplate.postForEntity(url,entity,ResultInfo.class);
    }

    private void removeFromRedisSet(Integer dinnerId, Integer followDinerId) {
        redisTemplate.opsForSet().remove(RedisKeyConstant.following.getKey()+dinnerId,followDinerId);
        redisTemplate.opsForSet().remove(RedisKeyConstant.followers.getKey()+followDinerId, dinnerId);
    }

    /**
     *
     * @param accessToken token
     * @return 当前登录用户
     *
     * 从统一认证中心获取当前登录用户的信息
     */
    private SignInDinerInfo loadSignInDinnerInfo(String accessToken) {
        AssertUtil.mustLogin(accessToken);
        String url =oauthServerName+"user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode()!=ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        return dinerInfo;
    }

    private void addToRedisSet(Integer dinnerId,Integer followDinerId) {
        redisTemplate.opsForSet().add(RedisKeyConstant.following.getKey()+dinnerId, followDinerId);
        redisTemplate.opsForSet().add(RedisKeyConstant.followers.getKey()+followDinerId, dinnerId);
    }
}
