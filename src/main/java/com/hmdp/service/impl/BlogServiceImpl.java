package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 通过id查询blog
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);

        if (blog == null){
            return Result.fail("笔记不存在!");
        }

        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 查询热点博客
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }


    /**
     * 修改点赞数量
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();

        // 1.判断redis中,该用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        // 2.如果没有点赞,则点赞
        if (score == null){
            // 修改数据库
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success){
                // 修改redis
                stringRedisTemplate.opsForZSet().add(key, String.valueOf(userId),System.currentTimeMillis());
            }
        }else{
            // 如果点赞了,则取消点赞
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    private void isBlogLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        if (user == null){
            // 用户未登录
            return;
        }

        Long userId = UserHolder.getUser().getId();

        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();

        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(score == null? false:true);
    }

    /**
     * 查询点赞前五名
     * @param id
     * @return
     */
    @Override
    public Result queryLikedUser(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;


        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (range == null || range.isEmpty()){
            // 没有点赞
            return Result.ok();
        }
        List<UserDTO> userList = new ArrayList();

        for (String str : range){
            User byId = userService.getById(str);
            UserDTO userDTO = new UserDTO();

            // 将user对象转换成userDTO
            BeanUtils.copyProperties(byId,userDTO);
            userList.add(userDTO);
        }
        return Result.ok(userList);
    }
}
