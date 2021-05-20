package org.wmy.seckill.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.constant.RedisKeyConstant;
import org.wmy.commons.exception.ParameterException;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.pojo.SeckillVouchers;
import org.wmy.commons.model.pojo.VoucherOrders;
import org.wmy.commons.model.vo.SignInDinerInfo;
import org.wmy.commons.utils.AssertUtil;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.seckill.mapper.SeckillVouchersMapper;
import org.wmy.seckill.mapper.VoucherOrdersMapper;
import org.wmy.seckill.model.RedisLock;

import javax.annotation.Resource;
import java.util.*;

/**
 * 秒杀业务逻辑层
 */
@Service
public class SeckillService {

    @Resource
    private SeckillVouchersMapper seckillVouchersMapper;
    @Resource
    private VoucherOrdersMapper voucherOrdersMapper;
    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private DefaultRedisScript defaultRedisScript;
    @Resource
    private RedisLock redisLock;


    /**
     * 抢购代金券
     * <p>
     * 不通过事务的情况下，要判断库存在先，下单在后
     * <p>
     * 通过事务控制时，下单可以在前
     *
     * @param voucherId   代金券 ID
     * @param accessToken 登录token
     * @Para path 访问路径
     */

    @Transactional(rollbackFor = Exception.class)
    public ResultInfo doSeckill(Integer voucherId, String accessToken, String path) {
        // 基本参数校验
        AssertUtil.isTrue(voucherId == null || voucherId < 0, "请选择需要抢购的代金券");
        AssertUtil.isNotEmpty(accessToken, "请登录");

        //采用redis
        String key = RedisKeyConstant.seckill_vouchers.getKey() + voucherId;
        Map<String, Object> map = redisTemplate.opsForHash().entries(key);
        AssertUtil.isTrue(map.isEmpty(), "该代金券并未有抢购活动");
        SeckillVouchers seckillVouchers = BeanUtil.mapToBean(map, SeckillVouchers.class, true, null);


        // 判断是否开始、结束
        Date now = new Date();
        AssertUtil.isTrue(now.before(seckillVouchers.getStartTime()), "该抢购还未开始");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()), "该抢购已结束");
        // 判断是否卖完
        AssertUtil.isTrue(seckillVouchers.getAmount() < 1, "该券已经卖完了");
        // 获取登录用户信息
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            resultInfo.setPath(path);
            return resultInfo;
        }
        // 这里的data是一个LinkedHashMap，SignInDinerInfo
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        // 判断登录用户是否已抢到(一个用户针对这次活动只能买一次)
        VoucherOrders order = voucherOrdersMapper.findDinerOrder(dinerInfo.getId(),
                seckillVouchers.getFkVoucherId());
        AssertUtil.isTrue(order != null, "该用户已抢到该代金券，无需再抢");


        //使用redis锁一个账号只能购买一次
        String lockName = RedisKeyConstant.lock_key.getKey() + dinerInfo.getId() + ":" + voucherId;
        long expireTime = seckillVouchers.getEndTime().getTime() - now.getTime();
        String lockKey = redisLock.tryLock(lockName, expireTime);

        try {
            //不为空意味着拿到锁了，可以下单
            if (StrUtil.isNotBlank(lockKey)) {
                // 下单
                VoucherOrders voucherOrders = new VoucherOrders();
                voucherOrders.setFkDinerId(dinerInfo.getId());
                voucherOrders.setFkVoucherId(seckillVouchers.getFkVoucherId());
                String orderNo = IdUtil.getSnowflake(1, 1).nextIdStr();
                voucherOrders.setOrderNo(orderNo);
                voucherOrders.setOrderType(1);
                voucherOrders.setStatus(0);
                long count = voucherOrdersMapper.save(voucherOrders);
                AssertUtil.isTrue(count == 0, "用户抢购失败");

                //redis + lua
                //扣库存
                List<String> keys = new ArrayList<>();
                keys.add(key);
                keys.add("amount");
                Long amount = (Long) redisTemplate.execute(defaultRedisScript, keys);
                AssertUtil.isTrue(amount == null || amount < 1, "该劵已经卖完了");
            }

        } catch (Exception e) {
            //手动回滚事务（因为异常被捕获了）
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            //解锁
            redisLock.unlock(lockName, lockKey);
            if (e instanceof ParameterException) {
                return ResultInfoUtil.buildError(0, "该劵已经卖完了", path);
            }
        }

        return ResultInfoUtil.buildSuccess(path, "抢购成功");
    }

    /**
     * 未实现一人一单
     * <p>
     * 抢购代金券
     * <p>
     * 不通过事务的情况下，要判断库存在先，下单在后
     * <p>
     * 通过事务控制时，下单可以在前
     *
     * @param voucherId   代金券 ID
     * @param accessToken 登录token
     * @Para path 访问路径
     */
    public ResultInfo doSeckill1(Integer voucherId, String accessToken, String path) {
        // 基本参数校验
        AssertUtil.isTrue(voucherId == null || voucherId < 0, "请选择需要抢购的代金券");
        AssertUtil.isNotEmpty(accessToken, "请登录");

        //注释掉原始的 关系型数据库 流程
        // 判断此代金券是否加入抢购
        //SeckillVouchers seckillVouchers = seckillVouchersMapper.selectVoucher(voucherId);
        //AssertUtil.isTrue(seckillVouchers == null, "该代金券并未有抢购活动");
        // 判断是否有效
        //AssertUtil.isTrue(seckillVouchers.getIsValid() == 0, "该活动已结束");

        //采用redis
        String key = RedisKeyConstant.seckill_vouchers.getKey() + voucherId;
        Map<String, Object> map = redisTemplate.opsForHash().entries(key);
        AssertUtil.isTrue(map.isEmpty(), "该代金券并未有抢购活动");
        SeckillVouchers seckillVouchers = BeanUtil.mapToBean(map, SeckillVouchers.class, true, null);


        // 判断是否开始、结束
        Date now = new Date();
        AssertUtil.isTrue(now.before(seckillVouchers.getStartTime()), "该抢购还未开始");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()), "该抢购已结束");
        // 判断是否卖完
        AssertUtil.isTrue(seckillVouchers.getAmount() < 1, "该券已经卖完了");
        // 获取登录用户信息
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            resultInfo.setPath(path);
            return resultInfo;
        }
        // 这里的data是一个LinkedHashMap，SignInDinerInfo
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        // 判断登录用户是否已抢到(一个用户针对这次活动只能买一次)
        VoucherOrders order = voucherOrdersMapper.findDinerOrder(dinerInfo.getId(),
                seckillVouchers.getId());
        AssertUtil.isTrue(order != null, "该用户已抢到该代金券，无需再抢");

        //注释掉原来 关系型数据库 流程
        // 扣库存
        //int count = seckillVouchersMapper.stockDecrease(seckillVouchers.getId());
        //AssertUtil.isTrue(count == 0, "该券已经卖完了");

        //redis
        //扣库存
        //问题：redis中扣库存是两步操作，包含查询库存和扣库存；这样写无法保证原子性，最终会出现超卖（amount<0），但是订单数量是正确的
        //long count = redisTemplate.opsForHash().increment(key, "amount", -1);
        //AssertUtil.isTrue(count < 0 ,"该劵已经卖完了");

        //redis + lua
        //扣库存
        List<String> keys = new ArrayList<>();
        keys.add(key);
        keys.add("amount");
        Long amount = (Long) redisTemplate.execute(defaultRedisScript, keys);
        AssertUtil.isTrue(amount == null || amount < 1, "该劵已经卖完了");


        // 下单
        VoucherOrders voucherOrders = new VoucherOrders();
        voucherOrders.setFkDinerId(dinerInfo.getId());
        //redis中不需要维护外键信息（商户增加秒杀活动时也没有设置，关系型数据库是自动生成的）
        //voucherOrders.setFkSeckillId(seckillVouchers.getId());
        voucherOrders.setFkVoucherId(seckillVouchers.getFkVoucherId());
        String orderNo = IdUtil.getSnowflake(1, 1).nextIdStr();
        voucherOrders.setOrderNo(orderNo);
        voucherOrders.setOrderType(1);
        voucherOrders.setStatus(0);
        long count = voucherOrdersMapper.save(voucherOrders);
        AssertUtil.isTrue(count == 0, "用户抢购失败");

        return ResultInfoUtil.buildSuccess(path, "抢购成功");
    }


    /**
     * 添加需要抢购的代金券
     *
     * @param seckillVouchers
     */
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVouchers(SeckillVouchers seckillVouchers) {
        // 非空校验
        AssertUtil.isTrue(seckillVouchers.getFkVoucherId() == null, "请选择需要抢购的代金券");
        AssertUtil.isTrue(seckillVouchers.getAmount() == 0, "请输入抢购总数量");
        Date now = new Date();
        AssertUtil.isNotNull(seckillVouchers.getStartTime(), "请输入开始时间");
        // 生产环境下面一行代码需放行，这里注释方便测试
        // AssertUtil.isTrue(now.after(seckillVouchers.getStartTime()), "开始时间不能早于当前时间");
        AssertUtil.isNotNull(seckillVouchers.getEndTime(), "请输入结束时间");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()), "结束时间不能早于当前时间");
        AssertUtil.isTrue(seckillVouchers.getStartTime().after(seckillVouchers.getEndTime()), "开始时间不能晚于结束时间");

        //注释掉原始的走 关系型数据库 的流程
        // 验证数据库中是否已经存在该券的秒杀活动
        //SeckillVouchers seckillVouchersFromDb = seckillVouchersMapper.selectVoucher(seckillVouchers.getFkVoucherId());
        //AssertUtil.isTrue(seckillVouchersFromDb != null, "该券已经拥有了抢购活动");
        // 插入数据库
        //seckillVouchersMapper.save(seckillVouchers);

        //采用redis实现，把秒杀活动的代金劵放入redis
        String key = RedisKeyConstant.seckill_vouchers.getKey() +
                seckillVouchers.getFkVoucherId();
        //验证redis中是否存在该劵的秒杀活动(用hash不涉及序列化；操作比较快)
        Map<String, Object> map = redisTemplate.opsForHash().entries(key);
        AssertUtil.isTrue(!CollectionUtils.isEmpty(map) && (int) map.get("amount") > 0, "该劵已拥有抢购活动");
        //插入redis
        seckillVouchers.setIsValid(1);
        seckillVouchers.setCreateDate(now);
        seckillVouchers.setUpdateDate(now);

        redisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(seckillVouchers));

    }

}
