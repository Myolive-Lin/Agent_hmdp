package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;
    /**
     * 关注时先写 MySQL，再写 Redis Set。
     * 取关时先删除 MySQL，再删除 Redis Set。
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        if (followUserId == null || followUserId <= 0) {
            return Result.fail("目标用户ID不合法");
        }

        UserDTO currentUser = UserHolder.getUser();
        Long userId = currentUser.getId();

        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }

        String key = RedisConstants.FOLLOW_KEY + userId;

        if (Boolean.TRUE.equals(isFollow)) {
            //已关注时候直接补写Redis，保证接口可重复调用，数据库唯一索引仍是最终防重复保护。

            int count = query().eq("user_id",userId)
                    .eq("follow_user_id", followUserId)
                    .count();
            if (count > 0){
                stringRedisTemplate.opsForSet().add(
                        key, followUserId.toString()
                );
                return Result.ok();
            }

            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);

            boolean success = save(follow);

            if (!success) {
                return Result.fail("关注失败");
            }

            stringRedisTemplate.opsForSet()
                    .add(key, followUserId.toString());

            return Result.ok();

        }else {
            /*
             * 取关：即使数据库不存在记录，也删除 Redis 中可能残留的数据。
             */
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));

            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            return Result.ok();
        }

    }

    /**
     * 查询关注状态以 MySQL 为准。
     */
    @Override
    public Result isFollow(Long followUserId) {
        if (followUserId == null || followUserId <= 0) {
            return Result.fail("目标用户ID不合法");

        }
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            return Result.fail("目标用户ID不合法");

        }
        Long userId = UserHolder.getUser().getId();

        String userKey = RedisConstants.FOLLOW_KEY + userId;
        String targetUserIdKey = RedisConstants.FOLLOW_KEY + targetUserId;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, targetUserIdKey);

        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 查看对应的用户
        List<Long> userIds =intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //将user转换成UserDTO
        List<UserDTO>userDTOS = userService.listByIds(userIds).stream().map(
                user -> BeanUtil.copyProperties(
                        user,
                        UserDTO.class
                )
        ).collect(Collectors.toList());


        return Result.ok(userDTOS);

    }
}
