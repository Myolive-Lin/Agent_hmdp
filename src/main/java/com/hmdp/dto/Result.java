package com.hmdp.dto;



//目的，统一后端返回给前端的数据格式
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // 自动getter setter
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public static Result ok(){
        return new Result(true, null, null, null);
    }

    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }

    public static Result ok(Object data, Long total
    ){
        return new Result(true, null, data, total);
    }

    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }



}
