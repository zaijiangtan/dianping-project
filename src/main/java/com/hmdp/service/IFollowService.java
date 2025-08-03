package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取关用户
     * @param id 被关注/取关的用户id
     * @param isFollow
     * @return
     */
    Result follow(Long id, Boolean isFollow);

    /**
     * 判断当前用户是否关注过该用户
     * @param id
     * @return
     */
    Result isFollow(Long id);
}
