package com.hmdp.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"phone", "code"})
public class LoginFromDTO {
    private String phone;
    private String code;
}
