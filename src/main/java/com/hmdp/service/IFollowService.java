package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取关。
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询当前用户是否关注目标用户。
     */
    Result isFollow(Long followUserId);

    /*
     * 查询当前用户的共同关注
     */
    Result followCommons(Long targetUserId);
}
