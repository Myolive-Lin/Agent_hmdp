package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.core.toolkit.StringUtils.isBlank;
import static com.baomidou.mybatisplus.core.toolkit.StringUtils.replaceBlank;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IFollowService followService;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
     * 热门笔记：按点赞数倒序分页
     */

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<Blog>(
                        current,
                        SystemConstants.MAX_PAGE_SIZE
                ));


        List<Blog> records = page.getRecords();
        if (records == null || records.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        batchQueryBlogUser(records);
        records.forEach(this::isBlogLiked);
        return Result.ok(records);

    }

    /*
     * 查询最早点赞的前五位用户
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        /*
         * MySQL 的 IN 查询不保证顺序。
         * ORDER BY FIELD(id, ...) 按 ZSet 的点赞时间顺序返回用户。
         */
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr +")")
                .list()
                .stream().map(user -> BeanUtil.copyProperties(
                        user,
                        UserDTO.class
                ))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    /*
     * 发布笔记
     */

    @Override
    public Result saveBlog(Blog blog) {
        //保存成功后，将 blogId 写入每个粉丝的 Feed 收件箱
        UserDTO userDTO = UserHolder.getUser();
        blog.setUserId(userDTO.getId());

        boolean success = save(blog);

        if (!success){
            return Result.fail("新增笔记失败");
        }

        /*
         * 查询作者的粉丝：
         * tb_follow.user_id 是粉丝，
         * tb_follow.follow_user_id 是作者。
         */
        List<Follow> fans = followService.query().eq("follow_user_id", userDTO.getId()).list();
        long publishTime = System.currentTimeMillis();

        for (Follow fan : fans){
            String key = RedisConstants.FEED_KEY + fan.getUserId();
            stringRedisTemplate.opsForZSet().add(
                    key, blog.getId().toString(),publishTime
            );
        }
        return Result.ok(blog.getId());
    }

    /**
     * 查询当前用户的 Feed。
     *
     * 第一次请求：
     * maxTime = Long.MAX_VALUE
     * offset = 0
     *
     * 下一次请求使用上一次响应的 minTime 和 offset。
     */
    @Override
    public Result queryBlogOfFollow(Long maxTime, Integer offset) {


    UserDTO userDTO =  UserHolder.getUser();

    long max = maxTime == null ? Long.MAX_VALUE : maxTime;
    int currentOffset = offset == null ? 0 : offset;

    String key = RedisConstants.FEED_KEY + userDTO.getId();
    final int pageSize = 2;

    Set<ZSetOperations.TypedTuple<String>>  tuples= stringRedisTemplate
                .opsForZSet().reverseRangeByScoreWithScores(
                        key,
                        0,
                        max,
                        currentOffset,
                        pageSize
                );

    if (tuples == null || tuples.isEmpty()){
        return Result.ok(new ScrollResult(Collections.emptyList(), 0L, 0));
    }

    List<Long> blogIds = new ArrayList<Long>(tuples.size());
    long minTime = 0L;
    int sameCount = 0;

    for(ZSetOperations.TypedTuple<String> tuple: tuples){
        blogIds.add(Long.valueOf(tuple.getValue()));

        long time = tuple.getScore().longValue();

        if(time == minTime){
            sameCount++;
        }else{
            minTime = time;
            sameCount = 1;
        }
    }

    /*
     * Redis 的IN 查询不保证返回顺序
     * 使用FIELD 保持FEED的时间倒序
     */
    String idStr = StrUtil.join(",", blogIds);
    List<Blog> blogs = query()
            .in("id", blogIds)
            .last("ORDER BY FIELD(id," + idStr + ")")
            .list();

    for (Blog blog : blogs){
        queryBlogUser(blog);
        isBlogLiked(blog);
    }


        /*
         * 当前页最小时间仍等于本次 max 时，
         * 说明同一毫秒的数据跨页了，需要累加旧 offset。
         */
        int nextOffset = (minTime == max ? currentOffset : 0) + sameCount;

        ScrollResult result = new ScrollResult(
                blogs,
                minTime,
                nextOffset
        );

        return Result.ok(result);

    }



    /*
     * 查询笔记详细
     */

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);

        if(blog == null){
            return Result.fail("博客不存在");
        }

        queryBlogUser(blog); //设置的是作者的信息
        isBlogLiked(blog);   // 设置当前用户是否喜欢

        return Result.ok(blog);

    }

    /**
     * 查询单篇笔记的作者
     */
    private void queryBlogUser(Blog blog){
        User user = userService.getById(blog.getUserId());

        if (user == null) {
            return;
        }

        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 查询当前登录用户是否点赞该笔记。
     */
    private void isBlogLiked(Blog blog){
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return;
        }

        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();

        Double score = stringRedisTemplate.opsForZSet().score(
                key, userDTO.getId().toString()
        );
        blog.setIsLike(score != null);

    }

    /**
     * 点赞或取消点赞。
     */

    @Override
    public Result updateLike(Long id) {
        //从redis中判断是否有score如果无，就添加点赞，同时在sql中增加，有就是取消点赞
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;

        Double score = stringRedisTemplate.opsForZSet().score(
                key, userId.toString()
        );

        if (score == null){ //未点赞 MySQL点数 加一
            boolean success = update()
                    .setSql("liked = liked + 1")
                    .eq("id",id)
                    .update();

            if (success){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            boolean success = update()
                    .setSql("liked = liked - 1")
                    .eq("id",id)
                    .update();

            if (success) {
                stringRedisTemplate.opsForZSet().remove(
                        key,
                        userId.toString()
                );
                }
            }
        return Result.ok();
    }














    /**
     * 热门列表批量补充作者信息，避免 N + 1查询
     * 每一篇博客补充作者昵称和头像
     */
    private void batchQueryBlogUser(List<Blog> blogs){
        List<Long>  userIds = blogs.stream()
                .map(Blog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            return;
        }

        // 从userid - > User
        List<User> users = userService.listByIds(userIds);

        // 把用户列表转换成 Map: userId -> User
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,   // key：userId
                        user -> user, // value：User 对象
                        (first, second) -> first // key 重复时保留第一个
                        )
                );

        for (Blog blog : blogs){
            User user = userMap.get(blog.getUserId());

            if (user != null){
                blog.setIcon(user.getIcon());
                blog.setName(user.getNickName());
            }
        }
    }


}
