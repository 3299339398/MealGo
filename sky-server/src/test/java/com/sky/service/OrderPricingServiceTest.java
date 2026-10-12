package com.sky.service;

import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPricingServiceTest {
    @Mock private DishMapper dishMapper;
    @Mock private SetmealMapper setmealMapper;
    private OrderPricingService service;

    @BeforeEach
    void setUp() { service = new OrderPricingService(dishMapper, setmealMapper, 2); }

    @Test
    void usesLatestPricesAndBuildsNewOrderSnapshots() {
        when(dishMapper.getById(1L)).thenReturn(Dish.builder().id(1L).name("新菜名").price(new BigDecimal("12.35")).status(1).build());
        when(setmealMapper.getById(2L)).thenReturn(Setmeal.builder().id(2L).name("套餐").price(new BigDecimal("20.00")).status(1).build());
        OrderPricingService.PricingResult result = service.calculate(Arrays.asList(
                ShoppingCart.builder().id(999L).dishId(1L).number(2).amount(new BigDecimal("0.01")).name("旧菜名").build(),
                ShoppingCart.builder().setmealId(2L).number(1).amount(new BigDecimal("0.01")).build()));
        assertEquals(new BigDecimal("46.70"), result.getAmount());
        assertEquals("新菜名", result.getDetails().get(0).getName());
        assertEquals(new BigDecimal("12.35"), result.getDetails().get(0).getAmount());
        assertNull(result.getDetails().get(0).getId());
        assertEquals(2, result.getPackagingFee());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantity(int quantity) {
        assertThrows(OrderBusinessException.class, () -> service.calculate(Collections.singletonList(
                ShoppingCart.builder().dishId(1L).number(quantity).build())));
    }

    @Test
    void rejectsItemWithBothDishAndSetmeal() {
        assertThrows(OrderBusinessException.class, () -> service.calculate(Collections.singletonList(
                ShoppingCart.builder().dishId(1L).setmealId(2L).number(1).build())));
    }

    @Test
    void rejectsRemovedAndDisabledDishes() {
        when(dishMapper.getById(1L)).thenReturn(null, Dish.builder().status(0).build());
        ShoppingCart item = ShoppingCart.builder().dishId(1L).number(1).build();
        assertThrows(OrderBusinessException.class, () -> service.calculate(Collections.singletonList(item)));
        assertThrows(OrderBusinessException.class, () -> service.calculate(Collections.singletonList(item)));
    }

    @Test
    void rejectsDisabledSetmeal() {
        when(setmealMapper.getById(1L)).thenReturn(Setmeal.builder().status(0).build());
        assertThrows(OrderBusinessException.class, () -> service.calculate(Collections.singletonList(
                ShoppingCart.builder().setmealId(1L).number(1).build())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001"})
    void rejectsInvalidPrices(String price) {
        when(dishMapper.getById(1L)).thenReturn(Dish.builder().status(1).price(new BigDecimal(price)).build());
        assertThrows(OrderBusinessException.class, () -> service.calculate(Collections.singletonList(
                ShoppingCart.builder().dishId(1L).number(1).build())));
    }

    @Test
    void rejectsNegativePackagingFee() {
        assertThrows(IllegalArgumentException.class, () -> new OrderPricingService(dishMapper, setmealMapper, -1));
    }
}
