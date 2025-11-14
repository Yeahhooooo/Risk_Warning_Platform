package com.riskwarning.user.controller;

import com.riskwarning.common.result.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @RequestMapping("/hello")
    public Result hello() {
        return Result.success("hello risk warning user service!");
    }
}
