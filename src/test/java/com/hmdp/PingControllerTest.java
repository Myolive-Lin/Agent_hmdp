package com.hmdp;
import org.junit.jupiter.api.Test; //junit 5的测试包
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
class PingControllerTest {
    @Autowired
    private MockMvc mockMvc; //Spring找到容器并注入

    @Test
    void shouldReturnPong() throws  Exception{
        mockMvc.perform((get("/ping")))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }
}
