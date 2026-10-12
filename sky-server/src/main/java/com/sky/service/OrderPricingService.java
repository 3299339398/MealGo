package com.sky.service;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.entity.OrderDetail;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Recalculates prices from the current menu instead of trusting client totals or cart snapshots. */
@Service
public class OrderPricingService {
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;
    private final int packagingFee;

    public OrderPricingService(DishMapper dishMapper, SetmealMapper setmealMapper,
                               @Value("${mealgo.order.packaging-fee:2}") int packagingFee) {
        if (packagingFee < 0) {
            throw new IllegalArgumentException("打包费不能为负数");
        }
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
        this.packagingFee = packagingFee;
    }

    public PricingResult calculate(List<ShoppingCart> cart) {
        if (CollectionUtils.isEmpty(cart)) {
            throw new OrderBusinessException("购物车为空");
        }
        BigDecimal total = BigDecimal.valueOf(packagingFee);
        List<OrderDetail> details = new ArrayList<>();
        for (ShoppingCart item : cart) {
            if (item == null || item.getNumber() == null || item.getNumber() <= 0
                    || (item.getDishId() == null) == (item.getSetmealId() == null)) {
                throw new OrderBusinessException("购物车商品或数量不合法");
            }
            OrderDetail detail = new OrderDetail();
            BigDecimal price;
            if (item.getDishId() != null) {
                Dish dish = dishMapper.getById(item.getDishId());
                if (dish == null || !StatusConstant.ENABLE.equals(dish.getStatus())) {
                    throw new OrderBusinessException("购物车中包含已下架菜品，请重新选择");
                }
                price = dish.getPrice();
                detail.setDishId(dish.getId());
                detail.setName(dish.getName());
                detail.setImage(dish.getImage());
                detail.setDishFlavor(item.getDishFlavor());
            } else {
                Setmeal setmeal = setmealMapper.getById(item.getSetmealId());
                if (setmeal == null || !StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                    throw new OrderBusinessException("购物车中包含已下架套餐，请重新选择");
                }
                price = setmeal.getPrice();
                detail.setSetmealId(setmeal.getId());
                detail.setName(setmeal.getName());
                detail.setImage(setmeal.getImage());
            }
            if (price == null || price.signum() <= 0 || price.stripTrailingZeros().scale() > 2) {
                throw new OrderBusinessException("商品价格不合法，请联系商家");
            }
            detail.setNumber(item.getNumber());
            detail.setAmount(price.setScale(2, RoundingMode.UNNECESSARY));
            details.add(detail);
            total = total.add(price.multiply(BigDecimal.valueOf(item.getNumber())));
        }
        if (total.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new OrderBusinessException("订单金额超出允许范围");
        }
        return new PricingResult(total.setScale(2, RoundingMode.UNNECESSARY), packagingFee, details);
    }

    @Getter
    @AllArgsConstructor
    public static class PricingResult {
        private final BigDecimal amount;
        private final int packagingFee;
        private final List<OrderDetail> details;
    }
}
