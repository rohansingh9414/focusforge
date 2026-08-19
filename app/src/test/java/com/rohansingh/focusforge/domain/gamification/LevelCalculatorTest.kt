package com.rohansingh.focusforge.domain.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCalculatorTest {

    @Test
    fun testLevel1_zeroXp() {
        val info = LevelCalculator.calculateLevel(0L)
        assertEquals(1, info.currentLevel)
        assertEquals(0L, info.currentLevelMinXp)
        assertEquals(100L, info.nextLevelXp)
        assertEquals(0L, info.currentLevelXpProgress)
        assertEquals(100L, info.xpRequiredForNextLevel)
        assertEquals(0.0f, info.progressPercent, 0.001f)
    }

    @Test
    fun testLevel1_partialProgress() {
        val info = LevelCalculator.calculateLevel(50L)
        assertEquals(1, info.currentLevel)
        assertEquals(0L, info.currentLevelMinXp)
        assertEquals(100L, info.nextLevelXp)
        assertEquals(50L, info.currentLevelXpProgress)
        assertEquals(50L, info.xpRequiredForNextLevel)
        assertEquals(0.5f, info.progressPercent, 0.001f)
    }

    @Test
    fun testLevel2_exactThreshold() {
        val info = LevelCalculator.calculateLevel(100L)
        assertEquals(2, info.currentLevel)
        assertEquals(100L, info.currentLevelMinXp)
        assertEquals(250L, info.nextLevelXp)
        assertEquals(0L, info.currentLevelXpProgress)
        assertEquals(150L, info.xpRequiredForNextLevel)
        assertEquals(0.0f, info.progressPercent, 0.001f)
    }

    @Test
    fun testLevel2_midProgress() {
        val info = LevelCalculator.calculateLevel(175L)
        assertEquals(2, info.currentLevel)
        assertEquals(100L, info.currentLevelMinXp)
        assertEquals(250L, info.nextLevelXp)
        assertEquals(75L, info.currentLevelXpProgress)
        assertEquals(75L, info.xpRequiredForNextLevel)
        assertEquals(0.5f, info.progressPercent, 0.001f)
    }

    @Test
    fun testMultiLevelJump() {
        val info = LevelCalculator.calculateLevel(600L)
        assertEquals(4, info.currentLevel) // 500 to 1000 is Level 4
        assertEquals(500L, info.currentLevelMinXp)
        assertEquals(1000L, info.nextLevelXp)
        assertEquals(100L, info.currentLevelXpProgress)
        assertEquals(400L, info.xpRequiredForNextLevel)
        assertEquals(0.2f, info.progressPercent, 0.001f)
    }

    @Test
    fun testMaxThresholdAndBeyond() {
        // 10000 XP reaches Level 10
        val info10k = LevelCalculator.calculateLevel(10000L)
        assertEquals(10, info10k.currentLevel)
        assertEquals(10000L, info10k.currentLevelMinXp)
        assertEquals(12500L, info10k.nextLevelXp)
        assertEquals(0L, info10k.currentLevelXpProgress)
        assertEquals(2500L, info10k.xpRequiredForNextLevel)

        // 12500 XP reaches Level 11
        val info12k = LevelCalculator.calculateLevel(12500L)
        assertEquals(11, info12k.currentLevel)
        assertEquals(12500L, info12k.currentLevelMinXp)
        assertEquals(15000L, info12k.nextLevelXp)
    }
}
