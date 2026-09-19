# 第 6 课：好友关注与 Feed 推送

> 迭代 5｜参考 `dianping`：MySQL 保存关注关系，Redis Set 计算共同关注，Redis ZSet 保存 Feed 收件箱。

## 数据结构

| Key | 类型 | 内容 |
|---|---|---|
| `follows:{userId}` | Set | 当前用户关注的用户 ID |
| `feed:{userId}` | ZSet | blogId，score 为博客发布时间 |

MySQL `tb_follow` 是关注关系的持久数据，增加唯一索引：

```sql
ALTER TABLE tb_follow
ADD UNIQUE KEY uk_user_follow (user_id, follow_user_id);
```

## 任务 1：关注、取关和状态

创建 Follow、FollowMapper、IFollowService、FollowServiceImpl、FollowController。

参考项目接口：

- `PUT /follow/{id}/{isFollow}`：isFollow=true 关注，false 取关。
- `GET /follow/or/not/{id}`：查询当前用户是否关注目标用户。
- `GET /follow/common/{id}`：共同关注。

关注流程：

1. 从 UserHolder 获取 userId。
2. 插入 `(userId, followUserId)`。
3. MySQL 保存成功后，SADD 到 `follows:{userId}`。

取关流程：

1. 按两个 ID 删除 MySQL 关系。
2. 删除成功后，SREM Redis Set。

禁止关注自己。重复关注由数据库唯一索引阻止，接口按已有状态处理。

## 任务 2：共同关注

```java
Set<String> intersect = stringRedisTemplate.opsForSet()
        .intersect(FOLLOW_KEY + currentUserId, FOLLOW_KEY + targetUserId);
```

将交集中的 ID 批量查询为 UserDTO。返回 id、nickName、icon，不返回 User 实体中的手机号和密码。

Redis Set 丢失时可以根据 MySQL 关注关系重新写入；当前课程不增加单独的投影任务系统。

## 任务 3：发布博客时推送 Feed

在保存博客成功后：

1. 查询 `follow_user_id = 作者ID` 的所有粉丝。
2. 遍历粉丝，把 blogId 写入每位粉丝的 Feed。

```java
String key = FEED_KEY + fanUserId;
stringRedisTemplate.opsForZSet().add(
        key,
        blog.getId().toString(),
        System.currentTimeMillis()
);
```

这里的“消息推送”指写入 Redis Feed 收件箱，不是 WebSocket、短信或手机系统通知。当前按参考项目在发布请求中直接推送；粉丝量很大时才需要异步化。

## 任务 4：滚动分页

创建最终 ScrollResult：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
```

接口：

```text
GET /blog/of/follow?lastId={maxTime}&offset={offset}
```

查询：

```java
Set<ZSetOperations.TypedTuple<String>> tuples =
        stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(
                        FEED_KEY + userId,
                        0,
                        maxTime,
                        offset,
                        pageSize
                );
```

遍历结果得到 blogId、最小 score 和最小 score 出现次数。下一页 offset：

```java
int nextOffset = (minTime == maxTime ? offset : 0) + sameCount;
```

这里的 minTime/maxTime 应使用 `long` 值比较。连续多页都是同一毫秒时必须累计 offset，否则会重复数据。

根据 blogId 批量查 Blog，恢复 Redis 返回顺序，再补充作者和 isLike，封装为 ScrollResult。

## 当前实现边界

- MySQL 关注关系与 Redis Set 是两次写入，失败时需要从 MySQL 重建 Set。
- 发布时同步遍历粉丝，适合课程数据规模；大 V 场景后续再改 MQ 异步推送。
- `maxTime/offset` 处理同毫秒数据，但历史删除或回填时不提供严格快照。

## 完成标准

- [ ] 关注、取关和是否关注查询正确。
- [ ] 重复关注不会产生重复数据库记录。
- [ ] SINTER 返回正确共同关注，并转换为 UserDTO。
- [ ] 作者发布博客后，粉丝 Feed 出现 blogId。
- [ ] Feed 按时间倒序，跨页无重复。
- [ ] 同毫秒多条数据时 offset 能累计。

## 知识点

MySQL 关系表、唯一索引、Redis Set/SINTER、ZSet、Feed 推模式、滚动分页、maxTime/offset 和批量查询保序。

[上一课](0005-blog.md)｜[下一课](0007-sign-uv.md)。
