package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据id查询博文信息
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询热门博文
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞博文
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询点赞该博文的所有人
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 根据当前用户id和查询用户id返回当前页信息
     * @param current
     * @param id
     * @return
     */
    Result queryBlogByUserId(Integer current, Long id);

    /**
     * 保存用户发送的博文，
     * 并推送到所有粉丝的收件箱
     * @param blog
     */
    Result saveBlog(Blog blog);

    /**
     * 获取滚动分页的单页博文
     * @param max
     * @param offset
     * @return
     */
    Result getScrollPage(Long max, Long offset);
}
