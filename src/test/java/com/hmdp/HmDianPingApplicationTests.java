package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    void test1(){
        shopService.saveShopRedis(1L,20L);
    }

}
