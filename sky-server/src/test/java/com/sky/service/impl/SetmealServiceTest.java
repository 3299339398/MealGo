package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetmealServiceTest {
    @Mock private SetmealMapper setmealMapper;
    @Mock private SetmealDishMapper setmealDishMapper;
    @InjectMocks private SetmealServiceimpl service;

    @Test
    void onSaleItemPreventsEntireBatchFromBeingDeleted() {
        when(setmealMapper.getById(1L)).thenReturn(Setmeal.builder().status(StatusConstant.DISABLE).build());
        when(setmealMapper.getById(2L)).thenReturn(Setmeal.builder().status(StatusConstant.ENABLE).build());
        assertThrows(DeletionNotAllowedException.class, () -> service.deleteBatch(Arrays.asList(1L, 2L)));
        verify(setmealMapper, never()).deleteById(any());
        verifyNoInteractions(setmealDishMapper);
    }

    @Test
    void validBatchDeletesEachItemOnce() {
        when(setmealMapper.getById(any())).thenReturn(Setmeal.builder().status(StatusConstant.DISABLE).build());
        service.deleteBatch(Arrays.asList(1L, 2L));
        verify(setmealMapper).deleteById(1L);
        verify(setmealMapper).deleteById(2L);
        verify(setmealDishMapper).deleteBySetmealId(1L);
        verify(setmealDishMapper).deleteBySetmealId(2L);
    }
}
