package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    IUserService userService;

    @Resource
    IFollowService followService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 1.获取博文信息
        Blog blog = getById(id);
        if (blog == null) {
            // 2.不存在则返回错误信息
            return Result.fail("该博文不存在");
        }
        // 3.设置博文发布者信息
        setBlogUser(blog);

        // 4.查询当前用户是否给该博文点过赞
        isBlogLiked(blog);

        // 4.返回最终的博文信息
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::setBlogUser);
        // 查询当前用户是否给该博文点过赞
        records.forEach(this::isBlogLiked);
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取用户id
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前用户是否点赞过了
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            // 3.没点赞过，则点赞加1，并加入redis的点赞用户set
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.已经点赞过，则点赞-1，从redis的点赞用户set中移除
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1.从redis查询点赞的前五个人的id
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 2.从id转换成User，再转换成UserDTO
        String idsStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("order by field(id, " + idsStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 3.返回用户信息
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        Long id = user.getId();
        blog.setUserId(id);
        // 2.保存探店博文
        save(blog);

        // 3.获取所有粉丝信息
        List<Follow> follows = followService.query().eq("follow_user_id", id).list();
        for (Follow follow : follows) {
            // 4.获取粉丝id
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;

            // 5.推送到收件箱
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result getScrollPage(Long max, Long offset) {
        // 1.获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;

        // 2.zrevrangebyscore key min max offset count
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        // 3.获取下一轮滚动分页需要的数据 max count
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>();
        int cnt = 1;
        long minTime = 0L;
        for(ZSetOperations.TypedTuple<String> tuple : tuples) {
            // 3.1获取博文id
            ids.add(Long.valueOf(tuple.getValue()));
            // 3.2 获取max count
            if (tuple.getScore().longValue() == minTime) {
                cnt++;
            } else {
                minTime = tuple.getScore().longValue();
                cnt = 1;
            }
        }

        // 4.获取博文
        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id, " + idsStr + ")").list();

        for (Blog blog : blogs) {
            // 4.1查询发布blog的用户信息
            isBlogLiked(blog);
            // 4.2查询当前用户是否给该博文点过赞
            setBlogUser(blog);
        }

        // 5.组装结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(cnt);
        scrollResult.setList(blogs);

        return Result.ok(scrollResult);
    }

    /**
     * 查询并设置博文发布者信息
     * @param blog
     */
    private void setBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询当前用户是否给该博文点过赞
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        // 1.获取用户id
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 1.1用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前用户是否点赞过了
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 3.设置是否点赞过
        blog.setIsLike(score != null);
    }

}
