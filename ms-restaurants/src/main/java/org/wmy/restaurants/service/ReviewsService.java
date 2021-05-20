package org.wmy.restaurants.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.wmy.commons.constant.ApiConstant;
import org.wmy.commons.constant.RedisKeyConstant;
import org.wmy.commons.exception.ParameterException;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.pojo.Reply;
import org.wmy.commons.model.pojo.Restaurant;
import org.wmy.commons.model.pojo.Reviews;
import org.wmy.commons.model.vo.*;
import org.wmy.commons.utils.AssertUtil;
import org.wmy.restaurants.mapper.ReplyMapper;
import org.wmy.restaurants.mapper.ReviewsMapper;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wmy
 * @create 2021-05-19 15:59
 */

@Service
public class ReviewsService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RestaurantService restaurantService;

    @Resource
    private ReviewsMapper reviewsMapper;

    @Resource
    private ReplyMapper replyMapper;

    @Resource
    private RedisTemplate redisTemplate;


    private final static int DEFAULT_NUM = 10;


    /**
     * 评论：
     * 哈哈1：不错				2
     * 	 哈哈4：胡说  		    5
     * 		 哈哈5：放屁		    6
     * 	 哈哈6：假的			    7
     * 哈哈2：很好				8
     * 哈哈3：nice!			    9
     *
     * @param restaurantId 餐厅的id
     * @param accessToken token
     * @return 多级回复列表
     *
     * 思路：
     * 1.根据餐厅id，获得针对该餐厅的所有评论
     * 2.遍历所有的评论，针对每一条评论，构建针对该评论的回复集合
     * 3.在构建回复集合的时候，针对找到的每一条回复，递归找到针对该回复的回复集合
     */
    public List<CommentVO> getMultipleReviews(Integer restaurantId,String accessToken) {

        AssertUtil.isTrue(restaurantId == null||restaurantId<1,"请选择餐厅");
        // 根据餐厅id，获得评论集合
        List<Reviews> reviews = reviewsMapper.getReviewsByRestaurantId(restaurantId);
        // 构建一个id集合，然后远程调用
        ArrayList<Integer> ids = new ArrayList<>();
        reviews.forEach(review->{
            if (!ids.contains(review.getFkDinerId())){
                ids.add(review.getFkDinerId());
            }
        });
        // 获取食客信息
        Map<Integer, ShortDinerInfo> dinerInfoMap = callDinerService(accessToken, ids);
        List<CommentVO> res = new ArrayList<>();
        // 遍历评论集合
        for(Reviews review:reviews) {
            // 创建CommentVO
            CommentVO vo= new CommentVO();
            vo.setContent(review.getContent());
            vo.setDinerInfo(dinerInfoMap.get(review.getFkDinerId()));
            // 找到针对评论的回复
            List<ReplyVO> replys= buildReplys(review.getId(),-1,accessToken);
            vo.setReplys(replys);
            res.add(vo);
        }
        return res;

    }



    /**
     * 获取餐厅的最新评论，默认10条
     * @param restaurantId id
     * @param accessToken token
     * @return
     */
    public List<ReviewsVO> getNewReviews(Integer restaurantId,String accessToken) {
        AssertUtil.isTrue(restaurantId == null||restaurantId<1,"请选择餐厅");
        // 从缓存中获得餐厅的最新评论
        String key = RedisKeyConstant.restaurant_new_reviews.getKey()+restaurantId;
        // 返回类型是LinkedHashMap
        List<LinkedHashMap> reviews = redisTemplate.opsForList().range(key, 0, DEFAULT_NUM - 1);
        // 构建dinerId 集合，调用食客服务获取DinerShortInfo
        ArrayList<Integer> ids = new ArrayList<>();
        // 构建返回结果集
        List<ReviewsVO> result = new ArrayList<>();
        reviews.forEach(review->{
            ReviewsVO reviewsVO = new ReviewsVO();
            BeanUtil.fillBeanWithMap(review,reviewsVO,true);
            result.add(reviewsVO);
            ids.add(reviewsVO.getFkDinerId());

        });

        Map<Integer, ShortDinerInfo> dinerInfoMap = callDinerService(accessToken, ids);

        // 遍历结果集，填充dinerInfo
        result.forEach(reviewsVO -> {
            ShortDinerInfo shortDinerInfo = dinerInfoMap.get(reviewsVO.getFkDinerId());
            if (shortDinerInfo != null){
                reviewsVO.setDinerInfo(shortDinerInfo);
            }
        });

        return result;
    }

    /**
     * 添加餐厅的评论
     * @param restaurantId 餐厅id
     * @param content 评论内容
     * @param accessToken token
     * @param like 喜欢与否
     */

    @Transactional(rollbackFor = Exception.class)
    public void addReview(Integer restaurantId,String content,
                          String accessToken,Integer like) {
        // 参数校验
        AssertUtil.isTrue(restaurantId == null||restaurantId<1,"请选择需要评论的餐厅");
        AssertUtil.isNotEmpty(content,"请输入评论的内容");
        AssertUtil.isTrue(content.length()>800,"评论内容过长");
        // 判断评论的餐厅是否存在
        Restaurant restaurant = restaurantService.findById(restaurantId);
        AssertUtil.isTrue(restaurant == null,"餐厅不存在");
        // 获得当前登录用户的信息
        SignInDinerInfo dinerInfo = loadSignInDinnerInfo(accessToken);
        // 构建reviews对象
        Date date = new Date();
        Reviews reviews = new Reviews();
        // TODO 在这里进行了设置，否则缓存中数据不完整
        reviews.setCreateDate(date);
        reviews.setUpdateDate(date);
        reviews.setIsValid(1);

        reviews.setFkRestaurantId(restaurantId);
        reviews.setContent(content);
        reviews.setFkDinerId(dinerInfo.getId());
        reviews.setLikeIt(like);


        // 数据入库
        int count = reviewsMapper.saveReviews(reviews);
        if(count ==0){
            return;
        }
        // 放入缓存
        redisTemplate.opsForList().leftPush(RedisKeyConstant.restaurant_new_reviews.getKey()+restaurantId,
                reviews);
    }


    /**
     * 构建回复列表
     * @param reviewId 评论id
     * @param parentId 回复的父节点id (针对谁的回复)
     * @param accessToken token
     * @return
     */
    private List<ReplyVO> buildReplys(Integer reviewId,Integer parentId,String accessToken) {
        List<ReplyVO> res = new ArrayList<>();
        // 数据库中根据parentId进行查询(reply的主键)
        List<Reply> replys = replyMapper.findSubReplyByReplyId(reviewId,parentId);
        if(replys == null || replys.isEmpty()) return res;
        // 构建一个map  key--dinerId  value--shortDinerInfo
        ArrayList<Integer> ids = new ArrayList<>();
        replys.forEach(reply->{
            if (!ids.contains(reply.getFkDinerId())){
                ids.add(reply.getFkDinerId());
            }
        });
        Map<Integer, ShortDinerInfo> dinerInfoMap = callDinerService(accessToken, ids);
        // 构建replyVO
        for(Reply reply:replys) {
            ReplyVO vo = new ReplyVO();
            vo.setContent(reply.getContent());
            vo.setDinerInfo(dinerInfoMap.get(reply.getFkDinerId()));
            // reply主键id  递归查找回复的回复
            List<ReplyVO> vo1= buildReplys(reviewId,reply.getId(),accessToken);
            vo.setReplys(vo1);
            res.add(vo);
        }
        return res;
    }

    /**
     * 远程调用dinerService 获取食客信息
     * @param accessToken
     * @param ids
     * @return
     */
    private Map<Integer, ShortDinerInfo> callDinerService(String accessToken,ArrayList<Integer> ids) {
        ResultInfo resultInfo = restTemplate.getForObject(dinersServerName + "/findByIds?access_token={accessToken}&ids={ids}",
                ResultInfo.class, accessToken, StrUtil.join(",", ids));
        if (resultInfo.getCode()!=ApiConstant.SUCCESS_CODE){
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        List<LinkedHashMap> dinerInfos = (ArrayList)resultInfo.getData();

        // 构建一个map，方便快速获取DinerShortInfo
        Map<Integer, ShortDinerInfo> dinerInfoMap = dinerInfos.stream().collect(Collectors.toMap(
                diner -> (int)diner.get("id"),
                diner -> BeanUtil.fillBeanWithMap(diner,new ShortDinerInfo(),true)
        ));
        return dinerInfoMap;
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
