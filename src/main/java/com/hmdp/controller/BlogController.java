package com.hmdp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/blog")
public class BlogController {

    private final IBlogService blogService;

    public BlogController(IBlogService blogService) {
        this.blogService = blogService;
    }


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(
            @PathVariable("id") Long id) {
        return blogService.updateLike(id);
    }

    /**
     * 查询当前用户发布的笔记。
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1")Integer current
    ){
        UserDTO userDTO = UserHolder.getUser();

        Page<Blog> page = blogService.query()
                .eq("user_id", userDTO.getId())
                .page(
                        new Page<Blog>(
                                current,
                                SystemConstants.MAX_PAGE_SIZE
                        )
                );

        List<Blog> records = page.getRecords();

        return Result.ok(records);
    }


    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current){
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryById(
            @PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询指定用户发布的笔记。
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current",defaultValue = "1")Integer currrent,
            @RequestParam("id") Long id){
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(currrent, SystemConstants.MAX_PAGE_SIZE));

        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(
            @PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

}
