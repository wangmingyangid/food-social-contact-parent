package org.wmy.feeds.controller;

import org.springframework.web.bind.annotation.*;
import org.wmy.commons.model.domain.ResultInfo;
import org.wmy.commons.model.pojo.Feeds;
import org.wmy.commons.model.vo.FeedsVO;
import org.wmy.commons.utils.ResultInfoUtil;
import org.wmy.feeds.service.FeedsService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author wmy
 * @create 2021-05-11 15:56
 */

@RestController
public class FeedsController {

    @Resource
    private FeedsService service;

    @Resource
    private HttpServletRequest request;


    /**
     * 分业查询关注用户的动态
     * @param page 要查寻的页码
     * @param access_token token
     * @return
     */
    @GetMapping("{page}")
    public ResultInfo getForPage(@PathVariable Integer page,String access_token) {
        List<FeedsVO> feedsVOS = service.selectForPage(page, access_token);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),feedsVOS);
    }

    @PostMapping("updateFollowingFeeds/{followingId}")
    public ResultInfo addFollowingFeeds(@PathVariable Integer followingId,
                                        @RequestParam int type,
                                        String access_token) {
        service.addFollowingFeeds(followingId,access_token,type);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),"操作成功");
    }

    @DeleteMapping("{id}")
    public ResultInfo delete(@PathVariable Integer id,String access_token) {
        service.delete(access_token,id);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),"删除成功");
    }

    /**
     * 添加 feed；只有一个接口，可以默认不写请求映射
     * @param feeds
     * @param access_token
     * @return
     */

    @PostMapping
    public ResultInfo create(@RequestBody Feeds feeds, String access_token) {

        service.create(feeds,access_token);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),"添加成功");
    }
}
