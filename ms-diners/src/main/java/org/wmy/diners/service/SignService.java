package org.wmy.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.exception.ParameterException;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.vo.SignInDinerInfo;
import org.wmy.commons.utils.AssertUtil;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/**
 *
 * 签到业务层逻辑
 * @author wmy
 * @create 2021-05-14 14:46
 */

@Service
public class SignService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Value("${service.name.ms-points-server}")
    private String pointsServiceName;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private RestTemplate restTemplate;


    /**
     * 获得当月首次签到日期
     * @param accessToken token
     * @param dateStr date
     */
    public String firstSign(String accessToken,String dateStr) {
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 获得签到日期
        Date date = getDate(dateStr);
        // 构建 key 格式 user:sign:dinerId:yyyyMM
        String key =buildSignKey(dinerInfo.getId(),date);
        // redis 中执行 bitpos key bit 命令
        Long index = (Long) redisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.bitPos(key.getBytes(), true)
        );
        if (index < 0) {
            return null;
        }
        LocalDateTime localDateTime = LocalDateTimeUtil.of(date).withDayOfMonth(index.intValue() + 1);
        return DateUtil.format(localDateTime,"yyyy--MM--dd");
       /* Long res = (Long) redisTemplate.execute(new RedisCallback() {
            @Override
            public Long doInRedis(RedisConnection connection) throws DataAccessException {
                return connection.bitPos(key.getBytes(), true);
            }
        });
        return res;*/
    }


    /**
     * TODO 日期的转换研究下
     *
     * 获取月份的签到详情，包含每一天是否进行签到  true--签到  false--未签到
     * @param accessToken token
     * @param dateStr 日期
     * @return
     */
    public Map<String,Boolean> getSignInfo(String accessToken,String dateStr) {
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 获取签到日期 2021-5-18 --> 2021-05-18 00:00:00
        Date date = getDate(dateStr);
        // 构建 key 格式 user:sign:dinerId:yyyyMM
        String key =buildSignKey(dinerInfo.getId(),date);
        // 获取某月的总天数
        int dayOfMonth = DateUtil.lengthOfMonth(DateUtil.month(date) + 1,
                DateUtil.isLeapYear(DateUtil.year(date)));

        // 构建命令 bitfield key get [u/i]num index
        BitFieldSubCommands commands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);

        // 返回的long型对象是十进制的
        List<Long> list = redisTemplate.opsForValue().bitField(key, commands);
        // 构建一个自动排序的map
        Map<String,Boolean> signInfo = new TreeMap<>();
        if (list == null || list.isEmpty()) {
            return signInfo;
        }
        long v = list.get(0) == null ? 0:list.get(0);
        // 由低位到高位进行遍历
        for (int i=dayOfMonth;i>0;i--) {
            // 获取日期时间
            LocalDateTime dateTime = LocalDateTimeUtil.of(date).withDayOfMonth(i);
            // 不等于自己表示签到；否则未签到
            boolean status = v>>1<<1!=v;
            // 构建一个 key 为日期  value 为是否签到的有序map
            signInfo.put(DateUtil.format(dateTime,"yyyy--MM--dd"),status);
            v>>=1;
        }
        return signInfo;
    }

    /**
     * 获取传入日期月的签到次数
     * @param accessToken token
     * @param dateStr 日期
     * @return 签到次数
     */
    public Long getSignCount(String accessToken,String dateStr) {
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 获取签到日期
        Date date = getDate(dateStr);
        // 构建 key 格式 user:sign:dinerId:yyyyMM
        String key =buildSignKey(dinerInfo.getId(),date);
        // 执行redis 命令 bitcount user:sign:5:202105
        return (Long) redisTemplate.execute(
                // TODO 为什么这么写
                (RedisCallback<Long>)connection -> connection.bitCount(key.getBytes())
        );

       /* Long count = (Long) redisTemplate.execute(new RedisCallback() {
            @Override
            public Long doInRedis(RedisConnection connection) throws DataAccessException {
                return connection.bitCount(key.getBytes());
            }
        });
        return count;*/
    }

    /**
     * 用户签到
     * @param accessToken token
     * @param dateStr 签到的日期  默认是当天  格式：yyyy-MM--dd
     * @return 当月连续签到次数
     */

    @Transactional(rollbackFor = Exception.class)
    public int doSign(String accessToken,String dateStr) {
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 获取签到日期
        Date date = getDate(dateStr);
        // 获取日期对应的天数，索引（从0计数）
        int dayOfMonth = DateUtil.dayOfMonth(date)-1;
        // 构建 key 格式 user:sign:dinerId:yyyyMM
        String key =buildSignKey(dinerInfo.getId(),date);
        // 查看是否已经签到
        Boolean isSigned = redisTemplate.opsForValue().getBit(key, dayOfMonth);
        AssertUtil.isTrue(isSigned,"今天已经签到过了");
        // 进行签到
        redisTemplate.opsForValue().setBit(key,dayOfMonth,true);
        // 统计连续签到次数
        int count = getContinuousSignCount(dinerInfo.getId(),date);
        // 添加积分 TODO 可使用消息队列进行优化
        return addPoints(count,dinerInfo.getId());
    }

    private int addPoints(int count,Integer dinerId) {
        // 签到一天送10积分，连续2天-20  连续3天--30 连续4天及以上送--50
        int points = 10;
        if (count ==2) {
            points = 20;
        }else if (count==3){
            points = 30;
        }else if (count>=4){
            points = 50;
        }
        HttpHeaders headers = new HttpHeaders();
        // body 以表单形式提交
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("dinerId",dinerId);
        body.add("points",points);
        body.add("types",0);
        HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(body,headers);
        // 发起请求
        ResponseEntity<ResultInfo> result = restTemplate.postForEntity(pointsServiceName, entity, ResultInfo.class);
        AssertUtil.isTrue(result.getStatusCode()!=HttpStatus.OK,"登录失败");
        ResultInfo resultInfo = result.getBody();
        if (resultInfo.getCode()!=ApiConstant.SUCCESS_CODE) {
            // TODO 失败了，事务要进行回滚 ?
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        return points;
    }

    /**
     * 统计连续签到的天数
     * @param id 用户id
     * @param date 当前统计日期
     * @return 连续签到的次数
     *
     * 算法思想：
     *  最低位为0
     *  1 1 1 1  0 1 0 0  0 0 0 0  0 0 0 1  1 1 1 0  1 0 0 0  1 1 1 1  0 0 1 0
     *  右移
     *  0 1 1 1  1 0 1 0  0 0 0 0  0 0 0 0  1 1 1 1  0 1 0 0  0 1 1 1  1 0 0 1
     *  再左移
     *  1 1 1 1  0 1 0 0  0 0 0 0  0 0 0 1  1 1 1 0  1 0 0 0  1 1 1 1  0 0 1 0
     *
     *  右移再左移等于自己，说明原来的最低为是0
     *
     *  最地位为1
     *  1 1 1 1  0 1 0 0  0 0 0 0  0 0 0 1  1 1 1 0  1 0 0 0  1 1 1 1  0 0 1 1
     *  右移
     *  0 1 1 1  1 0 1 0  0 0 0 0  0 0 0 0  1 1 1 1  0 1 0 0  0 1 1 1  1 0 0 1
     *  再左移
     *  1 1 1 1  0 1 0 0  0 0 0 0  0 0 0 1  1 1 1 0  1 0 0 0  1 1 1 1  0 0 1 0
     *
     *  右移再左移不等于自己，说明原来的最低为是1
     *
     */
    private int getContinuousSignCount(Integer id, Date date) {
        // 获取日期对应的天数，多少号
        int dayOfMonth = DateUtil.dayOfMonth(date);
        // 构建 key
        String signKey = buildSignKey(id, date);
        // 构建命令 bitfield key get [u/i]num index
        BitFieldSubCommands commands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        // 返回的long型对象是十进制的
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, commands);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int signCount = 0;
        long v = list.get(0) == null ? 0:list.get(0);
        // 取低位连续不为0的个数即可；需要考虑当天尚未签到的情况
        for (int i=dayOfMonth;i>0;i--) {
            // 右移再左移，如果等于自己，说明最低为为0，没有签到
            if (v>>1<<1==v){
                // 低位为0且非当天，说明连续签到中断了
                if (i!=dayOfMonth) break;
            }else {
                // 连续签到数加1
                signCount+=1;
            }
            // 右移一位并重新赋值，表示抹除掉最后一位
            v>>=1;
        }
        return signCount;
    }

    /**
     * 考虑到每月初需要重置连续签到次数，最简单的方式是按用户每月存一条签到数据（也可以每年存一条数据）。
     * Key的格式为user:sign:uid:yyyyMM，Value则采用长度为4个字节（32位）的位图（最大月份只有31天）。
     * 位图的每一位代表一天的签到，1表示已签，0表示未签。
     * 构建存储Key user:sign:dinerId:yyyyMM
     * e.g. user:sign:89:202011表示dinerId=89的食客在2020年11月的签到记录。
     *
     * @param dinerId
     * @return
     */
    private static String buildSignKey(int dinerId, Date date) {
        return String.format("user:sign:%d:%s", dinerId,
                DateUtil.format(date, "yyyyMM"));
    }


    private Date getDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) {
            return new Date();
        }
        try {
            return DateUtil.parse(dateStr);
        }catch (Exception e) {
            throw new ParameterException("请输入yyyy-MM--dd格式的日期");
        }
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
