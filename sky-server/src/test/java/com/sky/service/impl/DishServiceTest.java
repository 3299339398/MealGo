package com.sky.service.impl;

import com.sky.dto.DishDTO;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishServiceTest {
    @Mock private DishMapper dishMapper;
    @Mock private DishFlavorMapper dishFlavorMapper;
    @InjectMocks private DishServiceimpl service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void dishWithoutFlavorsCanBeSaved(boolean nullFlavors) {
        DishDTO dto = new DishDTO();
        dto.setFlavors(nullFlavors ? null : Collections.emptyList());
        service.saveWithFlavor(dto);
        verify(dishMapper).insert(any());
        verifyNoInteractions(dishFlavorMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void flavorsCanBeClearedWithoutEmptyInsert(boolean nullFlavors) {
        DishDTO dto = new DishDTO();
        dto.setId(1L);
        dto.setFlavors(nullFlavors ? null : Collections.emptyList());
        service.updateWithFlavor(dto);
        verify(dishFlavorMapper).deleteByDishId(1L);
        verify(dishFlavorMapper, never()).insertBatch(any());
    }
}
