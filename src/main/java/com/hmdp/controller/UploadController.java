package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/upload")
public class UploadController {

    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file")MultipartFile image){
        try{
            String originalFilename = image.getOriginalFilename();
            String fileName = createNewFileName(originalFilename);
            image.transferTo(
                    new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName)
            );
            log.debug("文件上传成功：{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }

    }


    private String createNewFileName(String originalFilename){
        //得到最后一个后缀
        String suffix = StrUtil.subAfter(
                originalFilename,
                ".",
                true
        );

        String name = UUID.randomUUID().toString();

        int hash = name.hashCode();

        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;

        //创建目录
        File dir = new  File(
                SystemConstants.IMAGE_UPLOAD_DIR,
                StrUtil.format("/blogs/{}/{}",d1,d2)
        );

        if (!dir.exists()){
            dir.mkdirs();
        }

        return StrUtil.format(
                "/blogs/{}/{}/{}.{}", d1, d2, name, suffix
        );


    }
}
