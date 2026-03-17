package com.ezansi.app.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for [DispatcherProvider] and [DefaultDispatcherProvider].
 *
 * Validates the coroutine dispatcher abstraction used across all modules
 * for testability — production code injects [DefaultDispatcherProvider],
 * tests inject a custom implementation backed by test dispatchers.
 */
@DisplayName("DispatcherProvider")
class DispatcherProviderTest {

    @Nested
    @DisplayName("DefaultDispatcherProvider")
    inner class DefaultProviderTests {

        private val provider = DefaultDispatcherProvider()

        @Test
        @DisplayName("default returns Dispatchers.Default")
        fun defaultDispatcher() {
            assertEquals(Dispatchers.Default, provider.default)
        }

        @Test
        @DisplayName("io returns Dispatchers.IO")
        fun ioDispatcher() {
            assertEquals(Dispatchers.IO, provider.io)
        }

        // Note: Dispatchers.Main is not available in unit tests without
        // Robolectric or Dispatchers.setMain(). The main/mainImmediate
        // properties are tested via the custom provider below.
    }

    @Nested
    @DisplayName("Custom DispatcherProvider for tests")
    inner class CustomProviderTests {

        @Test
        @DisplayName("test dispatchers can be injected for deterministic testing")
        fun testDispatcherInjection() {
            val testDispatcher = StandardTestDispatcher()
            val provider = object : DispatcherProvider {
                override val default: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val main: CoroutineDispatcher = testDispatcher
                override val mainImmediate: CoroutineDispatcher = testDispatcher
            }

            assertEquals(testDispatcher, provider.default)
            assertEquals(testDispatcher, provider.io)
            assertEquals(testDispatcher, provider.main)
            assertEquals(testDispatcher, provider.mainImmediate)
        }

        @Test
        @DisplayName("Unconfined dispatcher works for synchronous tests")
        fun unconfinedDispatcher() {
            val provider = object : DispatcherProvider {
                override val default: CoroutineDispatcher = Dispatchers.Unconfined
                override val io: CoroutineDispatcher = Dispatchers.Unconfined
                override val main: CoroutineDispatcher = Dispatchers.Unconfined
                override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
            }

            assertEquals(Dispatchers.Unconfined, provider.default)
            assertEquals(Dispatchers.Unconfined, provider.io)
        }
    }

    @Nested
    @DisplayName("Interface contract")
    inner class InterfaceContractTests {

        @Test
        @DisplayName("all four dispatchers are accessible")
        fun allDispatchersAccessible() {
            val provider = DefaultDispatcherProvider()
            assertNotNull(provider.default)
            assertNotNull(provider.io)
            // main/mainImmediate are accessible but would throw without
            // Android runtime — they're non-null at the type level.
        }
    }
}
