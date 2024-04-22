package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 通过id查询blog
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询热点博客
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 修改点赞数量
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询点赞前五名
     * @param id
     * @return
     */
    Result queryLikedUser(Long id);
}
