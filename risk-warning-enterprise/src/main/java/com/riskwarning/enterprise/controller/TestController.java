package com.riskwarning.enterprise.controller;

import com.riskwarning.common.result.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @RequestMapping
    public Result hello() {
        return Result.success("Hello, Risk Warning Enterprise Service!");
    }
}
