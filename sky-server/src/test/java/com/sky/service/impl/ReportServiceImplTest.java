package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.vo.OrderReportVO;
import com.sky.vo.UserReportVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {
    @Mock private OrderMapper orderMapper;
    @Mock private UserMapper userMapper;
    @InjectMocks private ReportServiceImpl service;

    @Test
    void distinguishesTotalUsersFromNewUsers() {
        when(userMapper.countByMap(anyMap())).thenAnswer(invocation ->
                ((java.util.Map<?, ?>) invocation.getArgument(0)).containsKey("begin") ? 3 : 20);
        LocalDate date = LocalDate.of(2026, 10, 12);
        UserReportVO result = service.getUserStatistics(date, date);
        assertEquals("20", result.getTotalUserList());
        assertEquals("3", result.getNewUserList());
    }

    @Test
    void reportsAllOrdersSeparatelyFromCompletedOrders() {
        when(orderMapper.countByMap(anyMap())).thenAnswer(invocation ->
                Orders.COMPLETED.equals(((java.util.Map<?, ?>) invocation.getArgument(0)).get("status")) ? 2 : 5);
        LocalDate date = LocalDate.of(2026, 10, 12);
        OrderReportVO result = service.getOrderStatistics(date, date.plusDays(1));
        assertEquals(10, result.getTotalOrderCount());
        assertEquals(4, result.getValidOrderCount());
        assertEquals(0.4, result.getOrderCompletionRate(), 0.00001);
        assertEquals("5,5", result.getOrderCountList());
    }

    @Test
    void reversedDateRangeIsRejectedInsteadOfLooping() {
        LocalDate date = LocalDate.of(2026, 10, 12);
        assertThrows(BaseException.class, () -> service.getTurnoverStatistics(date, date.minusDays(1)));
        assertThrows(BaseException.class, () -> service.getUserStatistics(date, date.minusDays(1)));
        assertThrows(BaseException.class, () -> service.getOrderStatistics(date, date.minusDays(1)));
        assertThrows(BaseException.class, () -> service.getSalesTop10(date, date.minusDays(1)));
    }
}
