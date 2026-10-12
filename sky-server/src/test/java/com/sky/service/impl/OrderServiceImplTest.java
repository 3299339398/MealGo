package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderPricingService;
import com.sky.service.ShopService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @Mock private OrderMapper orderMapper;
    @Mock private AddressBookMapper addressBookMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private UserMapper userMapper;
    @Mock private WeChatPayUtil weChatPayUtil;
    @Mock private WebSocketServer webSocketServer;
    @Mock private OrderPricingService orderPricingService;
    @Mock private ShopService shopService;
    @InjectMocks private OrderServiceImpl service;

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
        PageHelper.clearPage();
    }

    private Orders order(Integer status, Integer payStatus) {
        return Orders.builder().id(10L).number("order-10").userId(1L)
                .status(status).payStatus(payStatus).amount(new BigDecimal("38.50")).build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void emptyCartCannotCreateOrder(boolean nullCart) {
        BaseContext.setCurrentId(1L);
        when(addressBookMapper.getById(2L)).thenReturn(AddressBook.builder().userId(1L).build());
        when(shoppingCartMapper.list(any())).thenReturn(nullCart ? null : Collections.emptyList());
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(2L);

        assertThrows(ShoppingCartBusinessException.class, () -> service.orderSubmit(dto));
        verifyNoInteractions(orderMapper, orderDetailMapper);
        verify(shoppingCartMapper, never()).deleteByUserId(any());
    }

    @Test
    void submittedOrderContainsFullAddressAndIndependentDetailId() {
        BaseContext.setCurrentId(1L);
        when(addressBookMapper.getById(2L)).thenReturn(AddressBook.builder().userId(1L)
                .provinceName("浙江省").cityName("杭州市").districtName("西湖区")
                .detail("文一路1号").phone("13800000000").consignee("张三").build());
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(
                ShoppingCart.builder().id(999L).dishId(3L).number(2).amount(new BigDecimal("18.25")).build()));
        when(orderPricingService.calculate(any())).thenReturn(new OrderPricingService.PricingResult(
                new BigDecimal("38.50"), 2, Collections.singletonList(
                OrderDetail.builder().dishId(3L).name("米饭").number(2).amount(new BigDecimal("18.25")).build())));
        doAnswer(invocation -> { invocation.<Orders>getArgument(0).setId(10L); return null; })
                .when(orderMapper).insert(any());
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(2L);
        dto.setAmount(new BigDecimal("0.01"));
        dto.setPackAmount(-999);

        assertEquals(10L, service.orderSubmit(dto).getId());
        ArgumentCaptor<Orders> orders = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(orders.capture());
        assertEquals("浙江省杭州市西湖区文一路1号", orders.getValue().getAddress());
        assertEquals("张三", orders.getValue().getConsignee());
        assertEquals(new BigDecimal("38.50"), orders.getValue().getAmount());
        assertEquals(2, orders.getValue().getPackAmount());
        verify(orderDetailMapper).insertBatch(argThat(details -> details.size() == 1
                && details.get(0).getId() == null && details.get(0).getOrderId().equals(10L)));
        verify(shoppingCartMapper).deleteByUserId(1L);
    }

    @Test
    void searchReturnsOrdersAndDishSummary() {
        Page<Orders> page = new Page<>();
        page.add(order(Orders.CONFIRMED, Orders.PAID));
        page.setTotal(1);
        when(orderMapper.pageQuery(any())).thenReturn(page);
        when(orderDetailMapper.getByOrderId(10L)).thenReturn(Collections.singletonList(
                OrderDetail.builder().name("米饭").number(2).build()));
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
        dto.setPage(1);
        dto.setPageSize(10);

        PageResult result = service.conditionSearch(dto);
        List<OrderVO> records = (List<OrderVO>) result.getRecords();
        assertEquals(1, result.getTotal());
        assertEquals(1, records.size());
        assertTrue(records.get(0).getOrderDishes().contains("米饭*2"));
    }

    @Test
    void paymentUsesStoredOrderAmount() throws Exception {
        BaseContext.setCurrentId(1L);
        when(orderMapper.getByNumber("order-10")).thenReturn(order(Orders.PENDING_PAYMENT, Orders.UN_PAID));
        when(userMapper.getById(1L)).thenReturn(User.builder().openid("openid-1").build());
        JSONObject response = new JSONObject();
        response.put("package", "prepay_id=abc");
        when(weChatPayUtil.pay(eq("order-10"), eq(new BigDecimal("38.50")), anyString(), eq("openid-1")))
                .thenReturn(response);
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("order-10");

        assertEquals("prepay_id=abc", service.payment(dto).getPackageStr());
    }

    @Test
    void anotherUsersOrderCannotBePaid() {
        BaseContext.setCurrentId(2L);
        when(orderMapper.getByNumber("order-10")).thenReturn(order(Orders.PENDING_PAYMENT, Orders.UN_PAID));
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("order-10");
        assertThrows(OrderBusinessException.class, () -> service.payment(dto));
        verifyNoInteractions(weChatPayUtil);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void allCancellationPathsRefundActualAmount(int path) throws Exception {
        BaseContext.setCurrentId(1L);
        when(orderMapper.getById(10L)).thenReturn(order(Orders.TO_BE_CONFIRMED, Orders.PAID));
        if (path == 1) {
            service.cancel(10L);
        } else if (path == 2) {
            OrdersRejectionDTO dto = new OrdersRejectionDTO();
            dto.setId(10L);
            service.rejection(dto);
        } else {
            OrdersCancelDTO dto = new OrdersCancelDTO();
            dto.setId(10L);
            service.cancel(dto);
        }
        verify(weChatPayUtil).refund("order-10", "order-10", new BigDecimal("38.50"), new BigDecimal("38.50"));
        verify(orderMapper).update(argThat(updated -> Orders.CANCELLED.equals(updated.getStatus())
                && Orders.REFUND.equals(updated.getPayStatus())));
    }

    @Test
    void failedRefundDoesNotCancelOrder() throws Exception {
        BaseContext.setCurrentId(1L);
        when(orderMapper.getById(10L)).thenReturn(order(Orders.TO_BE_CONFIRMED, Orders.PAID));
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenThrow(new java.io.IOException("failed"));
        assertThrows(java.io.IOException.class, () -> service.cancel(10L));
        verify(orderMapper, never()).update(any());
    }

    @Test
    void confirmedOrderCanBeDelivered() {
        when(orderMapper.getById(10L)).thenReturn(order(Orders.CONFIRMED, Orders.PAID));
        service.delivery(10L);
        verify(orderMapper).update(argThat(updated -> Orders.DELIVERY_IN_PROGRESS.equals(updated.getStatus())));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 5, 6})
    void invalidDeliveryStatesAreRejected(int status) {
        when(orderMapper.getById(10L)).thenReturn(order(status, Orders.PAID));
        assertThrows(OrderBusinessException.class, () -> service.delivery(10L));
        verify(orderMapper, never()).update(any());
    }

    @Test
    void deliveryCanCompleteWithDeliveryTime() {
        when(orderMapper.getById(10L)).thenReturn(order(Orders.DELIVERY_IN_PROGRESS, Orders.PAID));
        service.complete(10L);
        verify(orderMapper).update(argThat(updated -> Orders.COMPLETED.equals(updated.getStatus())
                && updated.getDeliveryTime() != null));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 6})
    void invalidCompletionStatesAreRejected(int status) {
        when(orderMapper.getById(10L)).thenReturn(order(status, Orders.PAID));
        assertThrows(OrderBusinessException.class, () -> service.complete(10L));
        verify(orderMapper, never()).update(any());
    }

    @Test
    void duplicatePaymentNotificationDoesNotResetCompletedOrder() {
        when(orderMapper.getByNumber("order-10")).thenReturn(order(Orders.COMPLETED, Orders.PAID));
        service.paySuccess("order-10");
        verify(orderMapper, never()).update(any());
        verifyNoInteractions(webSocketServer);
    }

    @Test
    void closedShopCannotCreateOrder() {
        doThrow(new com.sky.exception.BaseException("店铺已打烊")).when(shopService).requireOpen();
        assertThrows(com.sky.exception.BaseException.class, () -> service.orderSubmit(new OrdersSubmitDTO()));
        verifyNoInteractions(orderMapper, addressBookMapper, shoppingCartMapper);
    }
}
