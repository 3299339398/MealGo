package com.sky.service;

import com.sky.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopServiceTest {
    private RedisTemplate redis;
    private ValueOperations values;
    private ShopService service;

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new ShopService(redis);
    }

    @Test
    void uninitializedStoreDefaultsToClosed() {
        assertEquals(0, service.getStatus());
        assertThrows(BaseException.class, service::requireOpen);
    }

    @Test
    void openStoreAllowsOrders() {
        when(values.get("SHOP_STATUS")).thenReturn(1);
        assertEquals(1, service.getStatus());
        assertDoesNotThrow(service::requireOpen);
    }

    @Test
    void rejectsInvalidStatusWithoutWritingRedis() {
        assertThrows(BaseException.class, () -> service.setStatus(2));
        verify(values, never()).set(any(), any());
    }

    @Test
    void userEndpointReturnsTheActualStatus() {
        when(values.get("SHOP_STATUS")).thenReturn(1);
        com.sky.controller.user.ShopController controller = new com.sky.controller.user.ShopController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "shopService", service);
        assertEquals(1, controller.status().getData());
    }
}
