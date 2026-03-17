package com.ezansi.app.core.common

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [EzansiResult] sealed class and extension functions.
 *
 * Validates the core result wrapper that every repository and engine
 * method uses for consistent success/error/loading handling (PRD §15).
 */
@DisplayName("EzansiResult")
class EzansiResultTest {

    // ── Success ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Success")
    inner class SuccessTests {

        @Test
        @DisplayName("wraps data correctly")
        fun wrapsDataCorrectly() {
            val result = EzansiResult.Success(42)
            assertEquals(42, result.data)
        }

        @Test
        @DisplayName("wraps string data")
        fun wrapsStringData() {
            val result = EzansiResult.Success("hello")
            assertEquals("hello", result.data)
        }

        @Test
        @DisplayName("wraps null data")
        fun wrapsNullData() {
            val result = EzansiResult.Success(null)
            assertNull(result.data)
        }

        @Test
        @DisplayName("wraps list data")
        fun wrapsListData() {
            val list = listOf(1, 2, 3)
            val result = EzansiResult.Success(list)
            assertEquals(listOf(1, 2, 3), result.data)
        }

        @Test
        @DisplayName("is an EzansiResult subtype")
        fun isEzansiResultSubtype() {
            val result: EzansiResult<Int> = EzansiResult.Success(42)
            assertIs<EzansiResult.Success<Int>>(result)
        }
    }

    // ── Error ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error")
    inner class ErrorTests {

        @Test
        @DisplayName("wraps message correctly")
        fun wrapsMessageCorrectly() {
            val result = EzansiResult.Error("Something went wrong")
            assertEquals("Something went wrong", result.message)
        }

        @Test
        @DisplayName("wraps optional cause")
        fun wrapsOptionalCause() {
            val cause = RuntimeException("root cause")
            val result = EzansiResult.Error("fail", cause)
            assertEquals(cause, result.cause)
        }

        @Test
        @DisplayName("cause defaults to null")
        fun causeDefaultsToNull() {
            val result = EzansiResult.Error("fail")
            assertNull(result.cause)
        }

        @Test
        @DisplayName("is an EzansiResult subtype")
        fun isEzansiResultSubtype() {
            val result: EzansiResult<Nothing> = EzansiResult.Error("oops")
            assertIs<EzansiResult.Error>(result)
        }

        @Test
        @DisplayName("can be used as EzansiResult of any type")
        fun covariantNothingType() {
            val result: EzansiResult<String> = EzansiResult.Error("oops")
            assertIs<EzansiResult.Error>(result)
        }
    }

    // ── Loading ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Loading")
    inner class LoadingTests {

        @Test
        @DisplayName("is a singleton data object")
        fun isSingleton() {
            val a = EzansiResult.Loading
            val b = EzansiResult.Loading
            assertSame(a, b)
        }

        @Test
        @DisplayName("can be used as EzansiResult of any type")
        fun covariantNothingType() {
            val result: EzansiResult<String> = EzansiResult.Loading
            assertIs<EzansiResult.Loading>(result)
        }
    }

    // ── map ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("map extension")
    inner class MapTests {

        @Test
        @DisplayName("transforms Success data")
        fun transformsSuccessData() {
            val result = EzansiResult.Success(10).map { it * 2 }
            assertIs<EzansiResult.Success<Int>>(result)
            assertEquals(20, result.data)
        }

        @Test
        @DisplayName("transforms Success type")
        fun transformsSuccessType() {
            val result = EzansiResult.Success(42).map { it.toString() }
            assertIs<EzansiResult.Success<String>>(result)
            assertEquals("42", result.data)
        }

        @Test
        @DisplayName("passes Error through unchanged")
        fun passesErrorThrough() {
            val error: EzansiResult<Int> = EzansiResult.Error("oops")
            val result = error.map { it * 2 }
            assertIs<EzansiResult.Error>(result)
            assertEquals("oops", result.message)
        }

        @Test
        @DisplayName("passes Loading through unchanged")
        fun passesLoadingThrough() {
            val loading: EzansiResult<Int> = EzansiResult.Loading
            val result = loading.map { it * 2 }
            assertIs<EzansiResult.Loading>(result)
        }
    }

    // ── getOrNull ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrNull extension")
    inner class GetOrNullTests {

        @Test
        @DisplayName("returns data for Success")
        fun returnsDataForSuccess() {
            assertEquals("hello", EzansiResult.Success("hello").getOrNull())
        }

        @Test
        @DisplayName("returns null for Error")
        fun returnsNullForError() {
            assertNull(EzansiResult.Error("oops").getOrNull())
        }

        @Test
        @DisplayName("returns null for Loading")
        fun returnsNullForLoading() {
            assertNull(EzansiResult.Loading.getOrNull())
        }
    }

    // ── getOrElse ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrElse extension")
    inner class GetOrElseTests {

        @Test
        @DisplayName("returns data for Success")
        fun returnsDataForSuccess() {
            val result = EzansiResult.Success(42).getOrElse { -1 }
            assertEquals(42, result)
        }

        @Test
        @DisplayName("invokes fallback for Error")
        fun invokesFallbackForError() {
            val result: Int = EzansiResult.Error("oops").getOrElse { -1 }
            assertEquals(-1, result)
        }

        @Test
        @DisplayName("invokes fallback for Loading")
        fun invokesFallbackForLoading() {
            val result: Int = EzansiResult.Loading.getOrElse { 99 }
            assertEquals(99, result)
        }

        @Test
        @DisplayName("fallback receives the original result")
        fun fallbackReceivesOriginal() {
            var receivedResult: EzansiResult<Int>? = null
            val error: EzansiResult<Int> = EzansiResult.Error("oops")
            error.getOrElse { receivedResult = it; -1 }
            assertIs<EzansiResult.Error>(receivedResult)
        }
    }

    // ── Equality ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Equality and data class contracts")
    inner class EqualityTests {

        @Test
        @DisplayName("Success instances with same data are equal")
        fun successEquality() {
            assertEquals(EzansiResult.Success(42), EzansiResult.Success(42))
        }

        @Test
        @DisplayName("Error instances with same message are equal")
        fun errorEquality() {
            assertEquals(
                EzansiResult.Error("oops"),
                EzansiResult.Error("oops"),
            )
        }

        @Test
        @DisplayName("Success and Error are not equal")
        fun successNotEqualToError() {
            assertTrue(EzansiResult.Success(42) != EzansiResult.Error("42"))
        }
    }
}
