package com.udl.smartrain.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLabelMapperTest {

    @Test
    fun `maps uci har classes to smartrain categories`() {
        assertEquals("Desplacament suau", ActivityLabelMapper.smarTrainLabelFor(0))
        assertEquals("Alta intensitat", ActivityLabelMapper.smarTrainLabelFor(1))
        assertEquals("Alta intensitat", ActivityLabelMapper.smarTrainLabelFor(2))
        assertEquals("Repos", ActivityLabelMapper.smarTrainLabelFor(3))
        assertEquals("Repos", ActivityLabelMapper.smarTrainLabelFor(4))
        assertEquals("Repos", ActivityLabelMapper.smarTrainLabelFor(5))
    }

    @Test
    fun `returns unknown label for unsupported class`() {
        assertEquals("Desconeguda", ActivityLabelMapper.modelLabelFor(99))
        assertEquals("Desconeguda", ActivityLabelMapper.smarTrainLabelFor(99))
    }
}
