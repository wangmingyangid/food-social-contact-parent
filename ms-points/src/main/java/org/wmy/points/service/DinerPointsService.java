package org.wmy.points.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.constant.RedisKeyConstant;
import org.wmy.commons.exception.ParameterException;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.pojo.DinerPoints;
import org.wmy.commons.model.vo.DinerPointsRankVO;
import org.wmy.commons.model.vo.ShortDinerInfo;
import org.wmy.commons.model.vo.SignInDinerInfo;
import org.wmy.commons.utils.AssertUtil;
import org.wmy.points.mapper.DinerPointMapper;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * @author wmy
 * @create 2021-05-15 15:42
 */

@Service
public class DinerPointsService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;

    @Resource
    private DinerPointMapper pointMapper;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;

    private static final int TOPN =20;

    /**
     * 查询积分排行榜前20名，并显示个人排行
     * Redis 实现
     * @param accessToken token
     * @return 榜上用户信息
     */
    public List<DinerPointsRankVO> findDinerPointRankFromRedis(String accessToken) {
        // 获取当前登录用户的信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 从redis 中查找排行最高的20人  TODO 命令：
        Set<ZSetOperations.TypedTuple<Integer>> set = redisTemplate.opsForZSet().reverseRangeWithScores(
                RedisKeyConstant.diner_points.getKey(), 0, 19);
        // 构建一个 ids 的集合
        ArrayList<Integer> idList = new ArrayList<>();
        LinkedHashMap<Integer, DinerPointsRankVO> rankVoMap = new LinkedHashMap<>();
        // 初始化排名
        int rank = 1;
        for (ZSetOperations.TypedTuple<Integer> typedTuple : set) {
            // 食客id
            Integer dinerId = typedTuple.getValue();
            // 权重
            int points = typedTuple.getScore().intValue();
            idList.add(dinerId);
            DinerPointsRankVO rankVO = new DinerPointsRankVO();
            rankVO.setTotal(points);
            rankVO.setId(dinerId);
            rankVO.setRanks(rank++);

            rankVoMap.put(dinerId,rankVO);
        }

        // 远程调用 diners 服务获取用户基本信息
        ResultInfo resultInfo = restTemplate.getForObject(dinersServerName + "findByIds?ids={ids}&access_token={access_token}",
                ResultInfo.class, StrUtil.join(",", idList),accessToken);
        if (resultInfo.getCode()!=ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        // 完善DinerPointsRankVO 昵称和头像
        List<LinkedHashMap> data = (ArrayList)resultInfo.getData();
        data.forEach(e->{
            ShortDinerInfo shortDinerInfo = BeanUtil.fillBeanWithMap(e, new ShortDinerInfo(), false);
            DinerPointsRankVO dinerPointsRankVO = rankVoMap.get(shortDinerInfo.getId());
            dinerPointsRankVO.setAvatarUrl(shortDinerInfo.getAvatarUrl());
            dinerPointsRankVO.setNickname(shortDinerInfo.getNickname());
        });
        // 自己在top 中
        if (rankVoMap.containsKey(dinerInfo.getId())) {
            DinerPointsRankVO dinerPointsRankVO = rankVoMap.get(dinerInfo.getId());
            dinerPointsRankVO.setIsMe(1);
            return Lists.newArrayList(rankVoMap.values());
        }
        // 不在top 中，获取个人排名和积分
        Long myRank = redisTemplate.opsForZSet().reverseRank(RedisKeyConstant.diner_points.getKey(),
                dinerInfo.getId());
        if (myRank != null) {
            DinerPointsRankVO vo = new DinerPointsRankVO();
            BeanUtil.copyProperties(dinerInfo,vo);
            vo.setIsMe(1);
            // 排名从0开始计数
            vo.setRanks(myRank.intValue()+1);
            // 获取积分
            Double score = redisTemplate.opsForZSet().score(RedisKeyConstant.diner_points.getKey(),
                    dinerInfo.getId());
            vo.setTotal(score.intValue());
            rankVoMap.put(dinerInfo.getId(),vo);
        }

        return Lists.newArrayList(rankVoMap.values());
    }
    /**
     * 查询积分排行榜前20名，并显示个人排行
     * MySql 实现
     * @param accessToken token
     * @return 榜上用户信息
     */
    public List<DinerPointsRankVO> findDinerPointRank(String accessToken) {
        // 获得当前用户信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 查询出TOP 20 (已经是排过序的)
        List<DinerPointsRankVO> topN = pointMapper.findTopN(TOPN);
        // 看自己是否在榜上
        LinkedHashMap<Integer,DinerPointsRankVO> rankMaps= new LinkedHashMap<>();
        // 构造一个 key--dinerId  value--DinerPointsRankVO 的map （用LinkedHashMap 保证有序性）
        topN.forEach(dinerPointsRankVO -> rankMaps.put(dinerPointsRankVO.getId(),dinerPointsRankVO));
        // 在榜上
        if (rankMaps.containsKey(dinerInfo.getId())) {
            DinerPointsRankVO dinerPointsRankVO = rankMaps.get(dinerInfo.getId());
            dinerPointsRankVO.setIsMe(1);
            return Lists.newArrayList(rankMaps.values()) ;
        }
        // 不在榜上，从数据库查出个人排行信息
        DinerPointsRankVO myRank = pointMapper.findDinerRank(dinerInfo.getId());
        myRank.setIsMe(1);
        topN.add(myRank);
        return topN;
    }

    /**
     * 添加积分
     * @param dinerId id
     * @param points 积分
     * @param types 积分类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Integer dinerId,Integer points,Integer types) {

        AssertUtil.isNotNull(dinerId==null,"食客不能为空");
        AssertUtil.isNotNull(points==null ||points<1,"积分不能为空");
        AssertUtil.isNotNull(types == null,"请选择对应的积分类型");
        DinerPoints dinerPoints = new DinerPoints();
        dinerPoints.setFkDinerId(dinerId);
        dinerPoints.setPoints(points);
        dinerPoints.setTypes(types);
        pointMapper.save(dinerPoints);
        // 把积分保存到 redis 中  命令：zincrby key increment member
        redisTemplate.opsForZSet().incrementScore(RedisKeyConstant.diner_points.getKey(),
                dinerId,points);
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
