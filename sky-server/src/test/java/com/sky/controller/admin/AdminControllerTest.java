package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.entity.Setmeal;
import com.sky.service.EmployeeService;
import com.sky.service.SetmealService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminControllerTest {
    private AnnotationConfigApplicationContext context;

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager("setmealCache"); }
        @Bean SetmealService setmealService() { return mock(SetmealService.class); }
        @Bean SetmealController adminSetmealController() { return new SetmealController(); }
        @Bean com.sky.controller.user.SetmealController userSetmealController() {
            return new com.sky.controller.user.SetmealController();
        }
    }

    @BeforeEach
    void setUp() { context = new AnnotationConfigApplicationContext(TestConfig.class); }

    @AfterEach
    void tearDown() { context.close(); }

    @Test
    void employeeSaveRespondsWithSuccessJson() throws Exception {
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", mock(EmployeeService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/admin/employee").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"张三\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
    }

    @Test
    void setmealSaveRespondsWithSuccessAndInvalidatesUserCache() throws Exception {
        SetmealService service = context.getBean(SetmealService.class);
        when(service.list(any())).thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(Setmeal.builder().id(1L).build()));
        com.sky.controller.user.SetmealController user = context.getBean(com.sky.controller.user.SetmealController.class);
        assertEquals(0, user.list(2L).getData().size());
        assertEquals(0, user.list(2L).getData().size());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(context.getBean(SetmealController.class)).build();
        mvc.perform(post("/admin/setmeal").contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        assertEquals(1, user.list(2L).getData().size());
        verify(service, times(2)).list(any());
        verify(service).saveWithdish(any(SetmealDTO.class));
    }

    @Test
    void setmealDeletionAcceptsIdsInQueryString() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(context.getBean(SetmealController.class)).build();
        mvc.perform(delete("/admin/setmeal").param("ids", "1,2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        verify(context.getBean(SetmealService.class)).deleteBatch(Arrays.asList(1L, 2L));
    }
}
