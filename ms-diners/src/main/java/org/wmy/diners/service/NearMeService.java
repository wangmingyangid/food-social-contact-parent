package org.wmy.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.constant.RedisKeyConstant;
import org.wmy.commons.exception.ParameterException;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.vo.NearMeDinerVO;
import org.wmy.commons.model.vo.ShortDinerInfo;
import org.wmy.commons.model.vo.SignInDinerInfo;
import org.wmy.commons.utils.AssertUtil;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author wmy
 * @create 2021-05-17 10:49
 */

@Service
public class NearMeService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private DinersService dinersService;

    private static final Integer DEFAULT_RADIUS = 1000;
    private static final Integer DEFAULT_SHOW_NUM = 20;


    /**
     * 查找附近的人
     * @param accessToken token
     * @param radius 半径范围；默认1000m
     * @param lon 经度
     * @param lat 纬度
     * @return 附近的人集合
     */
    public List<NearMeDinerVO> findNearMe(String accessToken,Integer radius,
                                          Float lon,Float lat) {

        // 获取当前的登录用户
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // radius 默认是1000m
        if (radius == null) {
            radius = DEFAULT_RADIUS;
        }
        Point point = null;
        String key =RedisKeyConstant.diner_location.getKey();
        // 如果没有上传用户的经纬度，从redis中获取
        if (lon == null || lat == null) {
            List<Point> location = redisTemplate.opsForGeo().position(key, dinerInfo.getId());
            AssertUtil.isTrue(location == null,"获取用户信息失败");
            point = location.get(0);
        }else {
            point = new Point(lon,lat);
        }
        // 执行redis 命令，拿到附近的人的id集合 georadius key longitude latitude radius m withdist count num
        // 距离对象，单位 m
        Distance distance = new Distance(radius, RedisGeoCommands.DistanceUnit.METERS);
        // 以用户位置为圆心，默认1000m 半径的圆
        Circle circle = new Circle(point, distance);
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs();
        // 设置显示的条目数，包含距离，由近到远进行排序
        args.limit(DEFAULT_SHOW_NUM).includeDistance().sortAscending();
        GeoResults<RedisGeoCommands.GeoLocation<Integer>> results = redisTemplate.opsForGeo().radius(key, circle, args);
        // 构建返回的结果集，把距离设置进去
        LinkedHashMap<Integer, NearMeDinerVO> nearMeDinerVOMap = new LinkedHashMap<>();
        results.forEach(result->{
            NearMeDinerVO nearMeDinerVO = new NearMeDinerVO();
            double dis = result.getDistance().getValue();
            // 四舍五入保留一位小数；TODO 后期需要扩展处理，根据距离显示m或km
            String dist = NumberUtil.round(dis,1)+"m";
            Integer dinerId = result.getContent().getName();
            nearMeDinerVO.setId(dinerId);
            nearMeDinerVO.setDistance(dist);
            nearMeDinerVOMap.put(dinerId,nearMeDinerVO);
        });
        // 调用 DinerService 的findByIds方法，获取用户的信息
        Integer[] ids = nearMeDinerVOMap.keySet().toArray(new Integer[]{});
        List<ShortDinerInfo> dinerInfos = dinersService.findByIds(StrUtil.join(",", ids));
        // 补充信息
        dinerInfos.forEach(info->{
            Integer dinerId = info.getId();
            NearMeDinerVO nearMeDinerVO = nearMeDinerVOMap.get(dinerId);
            nearMeDinerVO.setAvatarUrl(info.getAvatarUrl());
            nearMeDinerVO.setNickname(info.getNickname());
        });
        // 返回
        return Lists.newArrayList(nearMeDinerVOMap.values());
    }

    /**
     * 更新用户的位置信息
     * @param accessToken token
     * @param lon 经度
     * @param lat 维度
     */
    public void updateDinerLocation(String accessToken,Float lon,Float lat) {
        AssertUtil.isTrue(lon==null,"获取经度失败");
        AssertUtil.isTrue(lat==null,"获取纬度失败");
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 构建key
        String key = RedisKeyConstant.diner_location.getKey();
        RedisGeoCommands.GeoLocation<Integer> location = new RedisGeoCommands.GeoLocation<>(dinerInfo.getId(),
                new Point(lon,lat));
        redisTemplate.opsForGeo().add(key,location);
    }

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
