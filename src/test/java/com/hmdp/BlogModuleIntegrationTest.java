package com.hmdp;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BlogModuleIntegrationTest {

    @Autowired
    private IBlogService blogService;

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long blogId;
    private Long firstUserId;
    private Long secondUserId;

    @Test
    void shouldPublishLikeAndQueryBlog()
            throws Exception {

        User firstUser = createUser("blog-test-first");
        User secondUser = createUser("blog-test-second");

        firstUserId = firstUser.getId();
        secondUserId = secondUser.getId();

        /*
         * 1. 用户一发布笔记
         */
        setCurrentUser(firstUser);

        Blog blog = new Blog();
        blog.setShopId(1L);
        blog.setTitle("第五课集成测试笔记");
        blog.setImages("/imgs/blogs/test.jpg");
        blog.setContent("测试发布、点赞、点赞榜和详情查询");

        Result saveResult = blogService.saveBlog(blog);

        assertTrue(Boolean.TRUE.equals(saveResult.getSuccess()));
        assertNotNull(saveResult.getData());

        blogId = ((Number) saveResult.getData()).longValue();

        Blog savedBlog = blogService.getById(blogId);

        assertNotNull(savedBlog);
        assertEquals(firstUserId, savedBlog.getUserId());
        assertEquals("第五课集成测试笔记", savedBlog.getTitle());

        /*
         * 2. 查询详情：作者信息正确，尚未点赞
         */
        Result detailBeforeLike = blogService.queryBlogById(blogId);

        assertTrue(Boolean.TRUE.equals(detailBeforeLike.getSuccess()));

        Blog beforeLike =
                (Blog) detailBeforeLike.getData();

        assertEquals(firstUser.getNickName(), beforeLike.getName());
        assertEquals(firstUser.getIcon(), beforeLike.getIcon());
        assertFalse(Boolean.TRUE.equals(beforeLike.getIsLike()));

        /*
         * 3. 用户一点赞
         */
        Result firstLikeResult = blogService.updateLike(blogId);

        assertTrue(Boolean.TRUE.equals(firstLikeResult.getSuccess()));

        Blog afterFirstLike = blogService.getById(blogId);

        assertEquals(
                Integer.valueOf(1),
                afterFirstLike.getLiked()
        );

        Double firstUserScore = stringRedisTemplate
                .opsForZSet()
                .score(
                        RedisConstants.BLOG_LIKED_KEY + blogId,
                        firstUserId.toString()
                );

        assertNotNull(firstUserScore);

        /*
         * 4. 查询详情：当前用户已点赞
         */
        Result detailAfterLike = blogService.queryBlogById(blogId);

        Blog likedBlog = (Blog) detailAfterLike.getData();

        assertTrue(Boolean.TRUE.equals(likedBlog.getIsLike()));

        /*
         * 5. 用户二点赞，稍等以保证 ZSet 时间顺序不同
         */
        Thread.sleep(5L);

        setCurrentUser(secondUser);

        Result secondLikeResult = blogService.updateLike(blogId);

        assertTrue(Boolean.TRUE.equals(secondLikeResult.getSuccess()));

        Blog afterSecondLike = blogService.getById(blogId);

        assertEquals(
                Integer.valueOf(2),
                afterSecondLike.getLiked()
        );

        /*
         * 6. 点赞榜必须按最早点赞顺序返回
         */
        Result likesResult = blogService.queryBlogLikes(blogId);

        assertTrue(Boolean.TRUE.equals(likesResult.getSuccess()));
        assertTrue(likesResult.getData() instanceof List);

        @SuppressWarnings("unchecked")
        List<UserDTO> users =
                (List<UserDTO>) likesResult.getData();

        assertEquals(2, users.size());
        assertEquals(firstUserId, users.get(0).getId());
        assertEquals(secondUserId, users.get(1).getId());

        /*
         * 7. 用户一取消点赞
         */
        setCurrentUser(firstUser);

        Result unlikeResult = blogService.updateLike(blogId);

        assertTrue(Boolean.TRUE.equals(unlikeResult.getSuccess()));

        Blog afterUnlike = blogService.getById(blogId);

        assertEquals(
                Integer.valueOf(1),
                afterUnlike.getLiked()
        );

        Double removedScore = stringRedisTemplate
                .opsForZSet()
                .score(
                        RedisConstants.BLOG_LIKED_KEY + blogId,
                        firstUserId.toString()
                );

        assertNull(removedScore);

        /*
         * 8. 热门笔记接口可正常分页查询
         */
        Result hotResult = blogService.queryHotBlog(1);

        assertTrue(Boolean.TRUE.equals(hotResult.getSuccess()));
        assertNotNull(hotResult.getData());
    }

    private User createUser(String nickname) {
        User user = new User();

        String phone = "139" + String.format(
                "%08d",
                ThreadLocalRandom.current()
                        .nextInt(100_000_000)
        );

        user.setPhone(phone);
        user.setNickName(nickname);
        user.setIcon("/imgs/icons/test.png");

        boolean success = userService.save(user);

        assertTrue(success);
        assertNotNull(user.getId());

        return user;
    }

    private void setCurrentUser(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());

        UserHolder.setTl(dto);
    }

    @AfterEach
    void cleanUp() {
        UserHolder.removeUser();

        if (blogId != null) {
            blogService.removeById(blogId);

            stringRedisTemplate.delete(
                    RedisConstants.BLOG_LIKED_KEY + blogId
            );
        }

        if (firstUserId != null) {
            userService.removeById(firstUserId);
        }

        if (secondUserId != null) {
            userService.removeById(secondUserId);
        }
    }
}