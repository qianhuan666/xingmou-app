package com.xingmou.core.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordMatcherTest {
    @Test
    fun `命中关键词返回该词`() {
        assertEquals("苹果", KeywordMatcher.matches("这是苹果", listOf("苹果")))
    }

    @Test
    fun `忽略空格标点和大小写`() {
        assertEquals("dog", KeywordMatcher.matches("  It's a DOG! ", listOf("dog")))
    }

    @Test
    fun `同义词或儿语命中`() {
        assertEquals("狗狗", KeywordMatcher.matches("是狗狗", listOf("小狗", "狗狗")))
    }

    @Test
    fun `未命中返回null`() {
        assertNull(KeywordMatcher.matches("香蕉", listOf("苹果")))
    }

    @Test
    fun `空文本与纯标点返回null`() {
        assertNull(KeywordMatcher.matches("   ", listOf("苹果")))
        assertNull(KeywordMatcher.matches("。。！", listOf("苹果")))
    }

    @Test
    fun `空词表返回null`() {
        assertNull(KeywordMatcher.matches("苹果", emptyList()))
    }
}
