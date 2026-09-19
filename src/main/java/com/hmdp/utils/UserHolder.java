package com.hmdp.utils;

import com.hmdp.dto.UserDTO;


//ThreadLocal 只保存本次请求的用户数据
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl= new ThreadLocal<>();

    public static void setTl(UserDTO userDTO){
        tl.set(userDTO);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
