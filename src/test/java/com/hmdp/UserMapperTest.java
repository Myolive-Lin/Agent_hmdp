package com.hmdp;

import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional // 自动回滚，不在数据库中真实的插入
class UserMapperTest{

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldInsertAndQueryUser(){
        String phone = "139" + String.format(
                "08d", ThreadLocalRandom.current().nextInt(100_000_000)

        );

        User user = new User();
        user.setPhone(phone);
        user.setNickName("test-user");
        user.setIcon("");

        int rows = userMapper.insert(user);
        assertEquals(1,rows);
        assertNotNull(user.getId());

        User saveUser = userMapper.selectById(user.getId());
        assertNotNull(saveUser);
        assertEquals(phone, saveUser.getPhone());


    }


}