package com.riskwarning.common.component;

import com.riskwarning.common.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisComponent {


    @Autowired
    private RedisUtil redisUtil;

}
