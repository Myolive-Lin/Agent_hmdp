package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六课集成测试：关注、共同关注、Feed 推送和滚动分页。
 */
@SpringBootTest
class FollowFeedIntegrationTest {

    @Autowired
    private IFollowService followService;

    @Autowired
    private IBlogService blogService;

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final List<Long> userIds = new ArrayList<Long>();
    private final List<Long> blogIds = new ArrayList<Long>();

    @Test
    void shouldFollowPushFeedAndScrollWithoutDuplicates() {
        User reader = createUser("feed-reader");
        User anotherReader = createUser("another-reader");
        User author = createUser("feed-author");
        User commonAuthor = createUser("common-author");

        /*
         * reader 关注 author 和 commonAuthor。
         * anotherReader 只关注 commonAuthor。
         */
        setCurrentUser(reader);
        assertSuccess(followService.follow(author.getId(), true));
        assertSuccess(followService.follow(author.getId(), true));
        assertSuccess(followService.follow(commonAuthor.getId(), true));

        assertEquals(
                1,
                followService.query()
                        .eq("user_id", reader.getId())
                        .eq("follow_user_id", author.getId())
                        .count()
        );

        assertSuccess(followService.isFollow(author.getId()));
        assertEquals(
                Boolean.TRUE,
                followService.isFollow(author.getId()).getData()
        );

        setCurrentUser(anotherReader);
        assertSuccess(followService.follow(commonAuthor.getId(), true));

        /*
         * reader 与 anotherReader 的共同关注应是 commonAuthor。
         */
        setCurrentUser(reader);
        Result commonResult = followService.followCommons(anotherReader.getId());
        assertSuccess(commonResult);

        @SuppressWarnings("unchecked")
        List<UserDTO> commonUsers =
                (List<UserDTO>) commonResult.getData();

        assertEquals(1, commonUsers.size());
        assertEquals(commonAuthor.getId(), commonUsers.get(0).getId());

        /*
         * 作者连续发布三篇笔记，发布逻辑应自动写入 reader 的 Feed。
         */
        setCurrentUser(author);
        for (int index = 1; index <= 3; index++) {
            Blog blog = new Blog();
            blog.setShopId(1L);
            blog.setTitle("第六课 Feed 测试笔记 " + index);
            blog.setImages("/imgs/blogs/feed-test.jpg");
            blog.setContent("验证发布后推送到粉丝收件箱");

            Result publishResult = blogService.saveBlog(blog);
            assertSuccess(publishResult);

            blogIds.add(((Number) publishResult.getData()).longValue());
        }

        String feedKey = RedisConstants.FEED_KEY + reader.getId();

        assertEquals(
                3L,
                stringRedisTemplate.opsForZSet().zCard(feedKey)
        );

        /*
         * 将三篇笔记设置为同一时间戳，专门验证 offset 跨页累计。
         */
        long sameTime = System.currentTimeMillis();
        for (Long blogId : blogIds) {
            stringRedisTemplate.opsForZSet().add(
                    feedKey,
                    blogId.toString(),
                    sameTime
            );
        }

        setCurrentUser(reader);

        Result firstPageResult = blogService.queryBlogOfFollow(
                Long.MAX_VALUE,
                0
        );
        assertSuccess(firstPageResult);

        ScrollResult firstPage =
                (ScrollResult) firstPageResult.getData();

        List<Blog> firstBlogs = blogsOf(firstPage);

        assertEquals(2, firstBlogs.size());
        assertEquals(sameTime, firstPage.getMinTime());
        assertEquals(2, firstPage.getOffset());

        Result secondPageResult = blogService.queryBlogOfFollow(
                firstPage.getMinTime(),
                firstPage.getOffset()
        );
        assertSuccess(secondPageResult);

        ScrollResult secondPage =
                (ScrollResult) secondPageResult.getData();

        List<Blog> secondBlogs = blogsOf(secondPage);

        assertEquals(1, secondBlogs.size());
        assertEquals(3, secondPage.getOffset());

        Set<Long> actualBlogIds = new HashSet<Long>();
        actualBlogIds.addAll(idsOf(firstBlogs));
        actualBlogIds.addAll(idsOf(secondBlogs));

        assertEquals(new HashSet<Long>(blogIds), actualBlogIds);
    }

    @SuppressWarnings("unchecked")
    private List<Blog> blogsOf(ScrollResult result) {
        assertNotNull(result);
        return (List<Blog>) result.getList();
    }

    private List<Long> idsOf(List<Blog> blogs) {
        return blogs.stream()
                .map(Blog::getId)
                .collect(Collectors.toList());
    }

    private User createUser(String nickname) {
        User user = new User();
        user.setPhone("139" + String.format(
                "%08d",
                ThreadLocalRandom.current().nextInt(100_000_000)
        ));
        user.setNickName(nickname);
        user.setIcon("/imgs/icons/test.png");

        assertTrue(userService.save(user));
        assertNotNull(user.getId());

        userIds.add(user.getId());
        return user;
    }

    private void setCurrentUser(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());
        UserHolder.setTl(dto);
    }

    private void assertSuccess(Result result) {
        assertTrue(
                Boolean.TRUE.equals(result.getSuccess()),
                result.getErrorMsg()
        );
    }

    @AfterEach
    void cleanUp() {
        UserHolder.removeUser();

        if (!blogIds.isEmpty()) {
            blogService.removeByIds(blogIds);

            for (Long blogId : blogIds) {
                stringRedisTemplate.delete(
                        RedisConstants.BLOG_LIKED_KEY + blogId
                );
            }
        }

        if (!userIds.isEmpty()) {
            followService.remove(new QueryWrapper<Follow>()
                    .in("user_id", userIds)
                    .or()
                    .in("follow_user_id", userIds));

            for (Long userId : userIds) {
                stringRedisTemplate.delete(
                        RedisConstants.FOLLOW_KEY + userId
                );
                stringRedisTemplate.delete(
                        RedisConstants.FEED_KEY + userId
                );
            }

            userService.removeByIds(userIds);
        }
    }
}
