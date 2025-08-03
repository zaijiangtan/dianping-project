package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断是关注还是取关
        if (isFollow) {
            // 3.关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            save(follow);
        } else {
            // 4.取关
            remove(new QueryWrapper<Follow>().eq("follow_user_id", id).eq("user_id", userId));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断是否关注
        Integer count = query().eq("follow_user_id", id).eq("user_id", userId).count();
        return Result.ok(count > 0);
    }
}
