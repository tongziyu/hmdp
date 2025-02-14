package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    /**
     * 根据id查询blog
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result getBlogById(@PathVariable("id") Long id){
        return blogService.queryBlogById(id);
    }


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        // UserDTO user = UserHolder.getUser();
        // blog.setUserId(user.getId());
        // 保存探店博文
        //blogService.save(blog);
        // 返回id
        //return Result.ok(blog.getId());
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        //blogService.update()
        //      .setSql("liked = liked + 1").eq("id", id).update();
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryHotBlog(current);
    }

    @GetMapping("/likes/{id}")
    public Result queryLikedUser(@PathVariable Long id){

        return blogService.queryLikedUser(id);
    }

    /**
     * 查询用户博客
     * @param current
     * @param id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current",defaultValue = "1") Integer current,
            @RequestParam(value = "id") Long id
    ){
        Page<Blog> pageBlogs = blogService.query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(pageBlogs.getRecords());
    }

    @GetMapping("/of/follow")
    public Result queryBlogFollow(@RequestParam("lastId")Long lastId,
                                @RequestParam(value = "offset",defaultValue = "0") Integer offset){
        return blogService.queryBlogFollow(lastId,offset);

    }
}
