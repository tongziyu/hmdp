package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description:
 * @Author: Ian
 * @Date: 2024/4/20 18:16
 */
@Configuration
public class RedisConfig {

    /**
     * 配置Redisson客户端
     * @return
     */
    @Bean
    public RedissonClient redissonClient(){
        // 配置类
        Config config = new Config();
        // 添加redis地址,这里添加了单点的地址,也可以使用config.useClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://localhost:6379").setPassword("20001030");
        // 创建客户端,并交给容器管理
        return Redisson.create(config);

    }
}
