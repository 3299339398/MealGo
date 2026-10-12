package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTaskTest {
    @Mock private OrderMapper orderMapper;
    @InjectMocks private OrderTask task;

    @Test
    void timeoutTaskCancelsPendingOrders() {
        when(orderMapper.getByStatusAndOrderTimeLT(eq(Orders.PENDING_PAYMENT), any()))
                .thenReturn(Collections.singletonList(Orders.builder().id(1L).status(Orders.PENDING_PAYMENT).build()));
        task.processTimeoutOrder();
        verify(orderMapper).update(argThat(order -> Orders.CANCELLED.equals(order.getStatus()) && order.getCancelTime() != null));
    }

    @Test
    void deliveryTaskCompletesOrders() {
        when(orderMapper.getByStatusAndOrderTimeLT(eq(Orders.DELIVERY_IN_PROGRESS), any()))
                .thenReturn(Collections.singletonList(Orders.builder().id(1L).status(Orders.DELIVERY_IN_PROGRESS).build()));
        task.processDliveryOrder();
        verify(orderMapper).update(argThat(order -> Orders.COMPLETED.equals(order.getStatus()) && order.getDeliveryTime() != null));
    }
}
