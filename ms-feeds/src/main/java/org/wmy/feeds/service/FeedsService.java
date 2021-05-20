package org.wmy.feeds.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.constant.RedisKeyConstant;
import org.wmy.commons.exception.ParameterException;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.pojo.Feeds;
import org.wmy.commons.model.vo.FeedsVO;
import org.wmy.commons.model.vo.ShortDinerInfo;
import org.wmy.commons.model.vo.SignInDinerInfo;
import org.wmy.commons.utils.AssertUtil;
import org.wmy.feeds.mapper.FeedsMapper;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wmy
 * @create 2021-05-11 10:10
 */

@Service
public class FeedsService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Value("${service.name.ms-follow-server}")
    private String followServerName;

    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private FeedsMapper feedsMapper;

    /**
     * 分页拉取关注的人的动态
     * @param page
     * @param accessToken
     * @return
     */
    public List<FeedsVO> selectForPage(Integer page,String accessToken) {
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        String key = RedisKeyConstant.following_feeds.getKey()+dinerInfo.getId();
        // 根据页数构建查询集合的起始和终止索引
        int start = (page-1)*ApiConstant.PAGE_SIZE;
        int end =page*ApiConstant.PAGE_SIZE-1;
        // 根据权重从 redis 中查询该页的数据id集合
        Set<Integer> feedIds = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (feedIds == null || feedIds.isEmpty()){
            return Lists.newArrayList();
        }
        // 根据 id 集合从 Mysql 中批量查询评论信息
        List<Feeds> feeds = feedsMapper.findFeedsByIds(feedIds);
        List<Integer> followingDinerIds = new ArrayList<>();
        // 根据评论中的 diner_id 构建集合，然后远程调用 diners 服务
        List<FeedsVO> feedVOs = feeds.stream().
                map(feed -> {
                    FeedsVO feedsVO = new FeedsVO();
                    BeanUtil.copyProperties(feed, feedsVO);
                    followingDinerIds.add(feed.getFkDinerId());
                    return feedsVO;
                }).collect(Collectors.toList());

        ResultInfo resultInfo = restTemplate.getForObject(dinersServerName + "/findByIds?access_token={accessToken}&ids={ids}",
                ResultInfo.class, accessToken, StrUtil.join(",", followingDinerIds));
        List<LinkedHashMap> dinerInfoMaps = (ArrayList)resultInfo.getData();

        // 构建一个 map 集合，key--dinerId  value--ShortDinerInfo对象
       /* HashMap<Integer, ShortDinerInfo> shortDinerInfoHashMap = new HashMap<>();
        dinerInfoMaps.forEach(e->shortDinerInfoHashMap.put((Integer) e.get("id"),
                BeanUtil.fillBeanWithMap(e,new ShortDinerInfo(),true)));*/
        Map<Integer, ShortDinerInfo> dinerInfos = dinerInfoMaps.stream().collect(Collectors.toMap(
                //key
                diner -> (Integer) diner.get("id"),
                //value
                diner -> BeanUtil.fillBeanWithMap(diner, new ShortDinerInfo(), true))
        );
        // 遍历评论集合，将 ShortDinerInfo 对象设置进去
        feedVOs.forEach(feedVO->feedVO.setDinerInfo(dinerInfos.get(feedVO.getFkDinerId())));
        // 返回
        return feedVOs;
    }


    /**
     * 把关注对象的 feed 添加到 redis 缓存
     * @param followingId 关注的用户的 id
     * @param accessToken token
     * @param type 1 代表关注 0 代表取关
     */
    public void addFollowingFeeds(Integer followingId,String accessToken,int type) {

        AssertUtil.isTrue(followingId == null || followingId<1,"请选择关注的好友");
        // 查询出关注用户的所有动态
        List<Feeds> feeds = feedsMapper.findByDinnerId(followingId);
        if (feeds == null || feeds.isEmpty()) {
            return;
        }
        // 查询出当前登录用户的信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 将关注用户的状态放入当前登录用户的关注feed集合中
        String key = RedisKeyConstant.following_feeds.getKey()+dinerInfo.getId();
        if (type == 1){
            // 关注
            Set<DefaultTypedTuple<Integer>> collect = feeds.stream()
                    .map(feed -> new DefaultTypedTuple<>(feed.getId(), (double) feed.getUpdateDate().getTime()))
                    .collect(Collectors.toSet());
            redisTemplate.opsForZSet().add(key,collect);

//            // 效率低
//            feeds.forEach(feed->{
//                redisTemplate.opsForZSet().add(key,feed.getId(),feed.getUpdateDate().getTime());
//            });
        }else {
            // 取关
            List<Integer> collect = feeds.stream()
                    .map(feed -> feed.getId())
                    .collect(Collectors.toList());
            // 需要把集合变成数组（可变参数是兼容数组类参数的，但是数组类参数却无法兼容可变参数）
            redisTemplate.opsForZSet().remove(key,collect.toArray(new Integer[]{}));
        }
    }

    /**
     * 删除 feed
     * @param accessToken
     * @param feedId
     */

    @Transactional(rollbackFor = Exception.class)
    public void delete(String accessToken,Integer feedId) {
        AssertUtil.isNotEmpty(accessToken,"请登录");
        AssertUtil.isTrue(feedId == null || feedId <1,"请选择要删除的选项");
        // 获取当前的登录用户
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 查询当前评论是否存在
        Feeds feeds = feedsMapper.find(feedId);
        AssertUtil.isNotNull(feeds,"该评论已被删除");
        AssertUtil.isTrue(!feeds.getFkDinerId().equals(dinerInfo.getId()),"只能删除自己的评论");
        // 删除评论
        int delete = feedsMapper.delete(feedId);
        if (delete ==0) {
            return;
        }
        // 在 redis 粉丝列表中删除对该评论的引用 (可采用异步队列优化)
        List<Integer> followers = findFollowers(dinerInfo.getId());
        followers.forEach(follow ->{
            String key = RedisKeyConstant.following_feeds.getKey()+follow;
            redisTemplate.opsForZSet().remove(key,feeds.getId());
        });
    }

    /**
     * 添加 feeds
     * @param feeds 动态内容
     * @param accessToken 前登录用户的 accessToken
     * @return
     */

    @Transactional(rollbackFor = Exception.class)
    public void create(Feeds feeds,String accessToken) {
        // 发表的内容不为空；token 不能为空
        AssertUtil.isNotEmpty(feeds.getContent(),"发表的内容不能为空");
        AssertUtil.isTrue(feeds.getContent().length() > 255,"内容大小超限");
        AssertUtil.isNotEmpty(accessToken,"请登录");
        // 获取当前登录用户的信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 构建feeds，保存到数据库
        feeds.setFkDinerId(dinerInfo.getId());
        int insert = feedsMapper.insert(feeds);
        AssertUtil.isTrue(insert == 0,"添加失败");

        // TODO 推送消息到粉丝的列表中 --- 后须应该采用异步消息队列解决性能问题
        // 获取当前用户的粉丝列表
        List<Integer> followers  = findFollowers(dinerInfo.getId());
        // 为每一个粉丝推送消息；以时间为分数存储至ZSet
        long now = System.currentTimeMillis();
        followers.forEach(follow->{
            String key = RedisKeyConstant.following_feeds.getKey()+follow;
            redisTemplate.opsForZSet().add(key,feeds.getId(),now);
        });

    }

    /**
     * 获取用户的粉丝（远程调用）
     * @param id diner id
     * @return 粉丝列表
     */
    private List<Integer> findFollowers(Integer id) {
        String url = followServerName+"followers/"+id;
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        //  返回普通类型就不会被转成 LinkedHashMap
        List<Integer> followers = (List<Integer>)resultInfo.getData();
        return followers;
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
}
