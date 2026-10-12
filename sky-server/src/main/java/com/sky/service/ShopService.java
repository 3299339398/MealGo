package com.sky.service;

import com.sky.constant.StatusConstant;
import com.sky.exception.BaseException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopService {
    private static final String STATUS_KEY = "SHOP_STATUS";
    private final RedisTemplate redisTemplate;

    public ShopService(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Integer getStatus() {
        Object status = redisTemplate.opsForValue().get(STATUS_KEY);
        return StatusConstant.ENABLE.equals(status) ? StatusConstant.ENABLE : StatusConstant.DISABLE;
    }

    public void setStatus(Integer status) {
        if (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new BaseException("营业状态只能是0或1");
        }
        redisTemplate.opsForValue().set(STATUS_KEY, status);
    }

    public void requireOpen() {
        if (!StatusConstant.ENABLE.equals(getStatus())) {
            throw new BaseException("店铺已打烊，请在营业时间下单");
        }
    }
}
