package com.ezansi.app.core.data.contentpack

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [PackVersionDetector] — the embedding compatibility checker.
 *
 * These tests verify the detection rules from EMBEDDING_CONTRACT.md §6.2:
 * - schema_version must be ≥ 2
 * - embedding_dim must be 768
 * - embedding_model must be "gemma4-1b"
 * - embedding_model_version, if present, must match the app's active version
 *
 * All tests are pure JVM — no Android/Robolectric dependencies since
 * [PackVersionDetector.checkCompatibility] operates on a plain [Map].
 */
@DisplayName("PackVersionDetector")
class PackVersionDetectorTest {

    // ── Helper ──────────────────────────────────────────────────────

    /**
     * Creates a manifest map representing a fully compatible v2 pack.
     * Tests can override individual fields by merging with this base.
     */
    private fun compatibleManifest(
        overrides: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val base = mapOf(
            "schema_version" to "2",
            "embedding_dim" to "768",
            "embedding_model" to "gemma4-1b",
            "embedding_model_version" to "gemma4-1b-hash-v1",
            "pack_id" to "test-pack",
            "version" to "1.0.0",
            "subject" to "mathematics",
            "grade" to "6",
        )
        return base + overrides
    }

    // ── Compatible packs ────────────────────────────────────────────

    @Nested
    @DisplayName("Compatible packs")
    inner class CompatibleTests {

        @Test
        @DisplayName("fully valid v2 manifest → Compatible")
        fun fullyValidV2Pack() {
            val manifest = compatibleManifest()
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.Compatible>(result)
        }

        @Test
        @DisplayName("future schema_version=3 → Compatible (≥ check)")
        fun futureSchemaVersion3() {
            val manifest = compatibleManifest(mapOf("schema_version" to "3"))
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.Compatible>(result)
        }

        @Test
        @DisplayName("future schema_version=10 → Compatible (≥ check)")
        fun futureSchemaVersion10() {
            val manifest = compatibleManifest(mapOf("schema_version" to "10"))
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.Compatible>(result)
        }

        @Test
        @DisplayName("missing embedding_model_version → Compatible (backwards compat)")
        fun missingEmbeddingModelVersion() {
            val manifest = compatibleManifest() - "embedding_model_version"
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.Compatible>(result)
        }
    }

    // ── Incompatible schema ─────────────────────────────────────────

    @Nested
    @DisplayName("IncompatibleSchema")
    inner class IncompatibleSchemaTests {

        @Test
        @DisplayName("schema_version=1 → IncompatibleSchema")
        fun schemaVersion1() {
            val manifest = compatibleManifest(mapOf("schema_version" to "1"))
            val result = PackVersionDetector.checkCompatibility(manifest)

            assertIs<PackCompatibility.IncompatibleSchema>(result)
            assert(result.message.contains("v1")) {
                "Message should mention the pack's schema version"
            }
            assert(result.message.contains("v2")) {
                "Message should mention the required schema version"
            }
        }

        @Test
        @DisplayName("missing schema_version (defaults to 1) → IncompatibleSchema")
        fun missingSchemaVersion() {
            val manifest = compatibleManifest() - "schema_version"
            val result = PackVersionDetector.checkCompatibility(manifest)

            assertIs<PackCompatibility.IncompatibleSchema>(result)
            assert(result.message.contains("v1")) {
                "Default schema_version=1 should be reported"
            }
        }

        @Test
        @DisplayName("empty manifest → IncompatibleSchema")
        fun emptyManifest() {
            val result = PackVersionDetector.checkCompatibility(emptyMap())

            assertIs<PackCompatibility.IncompatibleSchema>(result)
        }

        @Test
        @DisplayName("schema_version=0 → IncompatibleSchema")
        fun schemaVersion0() {
            val manifest = compatibleManifest(mapOf("schema_version" to "0"))
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleSchema>(result)
        }

        @Test
        @DisplayName("non-numeric schema_version (defaults to 1) → IncompatibleSchema")
        fun nonNumericSchemaVersion() {
            val manifest = compatibleManifest(mapOf("schema_version" to "beta"))
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleSchema>(result)
        }
    }

    // ── Incompatible dimension ──────────────────────────────────────

    @Nested
    @DisplayName("IncompatibleDimension")
    inner class IncompatibleDimensionTests {

        @Test
        @DisplayName("embedding_dim=384 → IncompatibleDimension")
        fun dimension384() {
            val manifest = compatibleManifest(mapOf("embedding_dim" to "384"))
            val result = PackVersionDetector.checkCompatibility(manifest)

            assertIs<PackCompatibility.IncompatibleDimension>(result)
            assert(result.message.contains("384")) {
                "Message should mention the pack's dimension"
            }
            assert(result.message.contains("768")) {
                "Message should mention the required dimension"
            }
        }

        @Test
        @DisplayName("embedding_dim=256 → IncompatibleDimension")
        fun dimension256() {
            val manifest = compatibleManifest(mapOf("embedding_dim" to "256"))
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleDimension>(result)
        }

        @Test
        @DisplayName("embedding_dim=512 → IncompatibleDimension")
        fun dimension512() {
            val manifest = compatibleManifest(mapOf("embedding_dim" to "512"))
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleDimension>(result)
        }

        @Test
        @DisplayName("missing embedding_dim (defaults to 384) → IncompatibleDimension")
        fun missingDimension() {
            // Remove embedding_dim — defaults to 384 (v1 assumption)
            val manifest = compatibleManifest() - "embedding_dim"
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleDimension>(result)
        }
    }

    // ── Incompatible model ──────────────────────────────────────────

    @Nested
    @DisplayName("IncompatibleModel")
    inner class IncompatibleModelTests {

        @Test
        @DisplayName("MiniLM model → IncompatibleModel")
        fun miniLmModel() {
            val manifest = compatibleManifest(
                mapOf("embedding_model" to "sentence-transformers/all-MiniLM-L6-v2"),
            )
            val result = PackVersionDetector.checkCompatibility(manifest)

            assertIs<PackCompatibility.IncompatibleModel>(result)
            assert(result.message.contains("MiniLM")) {
                "Message should mention the pack's model"
            }
            assert(result.message.contains("gemma4-1b")) {
                "Message should mention the required model"
            }
        }

        @Test
        @DisplayName("missing embedding_model (defaults to MiniLM) → IncompatibleModel")
        fun missingModel() {
            val manifest = compatibleManifest() - "embedding_model"
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleModel>(result)
        }

        @Test
        @DisplayName("unknown model name → IncompatibleModel")
        fun unknownModel() {
            val manifest = compatibleManifest(
                mapOf("embedding_model" to "some-other-model"),
            )
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleModel>(result)
        }
    }

    // ── Incompatible version ────────────────────────────────────────

    @Nested
    @DisplayName("IncompatibleVersion")
    inner class IncompatibleVersionTests {

        @Test
        @DisplayName("wrong embedding_model_version → IncompatibleVersion")
        fun wrongVersion() {
            val manifest = compatibleManifest(
                mapOf("embedding_model_version" to "gemma4-1b-real-v1"),
            )
            val result = PackVersionDetector.checkCompatibility(manifest)

            assertIs<PackCompatibility.IncompatibleVersion>(result)
            assert(result.message.contains("gemma4-1b-real-v1")) {
                "Message should mention the pack's version"
            }
            assert(result.message.contains("gemma4-1b-hash-v1")) {
                "Message should mention the required version"
            }
        }

        @Test
        @DisplayName("future version gemma4-1b-real-v2 → IncompatibleVersion")
        fun futureVersion() {
            val manifest = compatibleManifest(
                mapOf("embedding_model_version" to "gemma4-1b-real-v2"),
            )
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleVersion>(result)
        }
    }

    // ── Check priority order ────────────────────────────────────────

    @Nested
    @DisplayName("Priority order")
    inner class PriorityTests {

        @Test
        @DisplayName("schema check takes priority over dimension check")
        fun schemaPriorityOverDimension() {
            // Both schema and dimension are wrong — schema should be reported first
            val manifest = mapOf(
                "schema_version" to "1",
                "embedding_dim" to "384",
                "embedding_model" to "sentence-transformers/all-MiniLM-L6-v2",
            )
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleSchema>(result)
        }

        @Test
        @DisplayName("dimension check takes priority over model check")
        fun dimensionPriorityOverModel() {
            // Schema OK, both dimension and model are wrong — dimension first
            val manifest = compatibleManifest(
                mapOf(
                    "embedding_dim" to "384",
                    "embedding_model" to "wrong-model",
                ),
            )
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleDimension>(result)
        }

        @Test
        @DisplayName("model check takes priority over version check")
        fun modelPriorityOverVersion() {
            // Schema and dim OK, both model and version wrong — model first
            val manifest = compatibleManifest(
                mapOf(
                    "embedding_model" to "wrong-model",
                    "embedding_model_version" to "wrong-version",
                ),
            )
            val result = PackVersionDetector.checkCompatibility(manifest)
            assertIs<PackCompatibility.IncompatibleModel>(result)
        }
    }

    // ── Constants validation ────────────────────────────────────────

    @Nested
    @DisplayName("Constants")
    inner class ConstantsTests {

        @Test
        @DisplayName("REQUIRED_SCHEMA_VERSION is 2")
        fun requiredSchemaVersion() {
            assertEquals(2, PackVersionDetector.REQUIRED_SCHEMA_VERSION)
        }

        @Test
        @DisplayName("REQUIRED_EMBEDDING_DIM is 768")
        fun requiredEmbeddingDim() {
            assertEquals(768, PackVersionDetector.REQUIRED_EMBEDDING_DIM)
        }

        @Test
        @DisplayName("REQUIRED_EMBEDDING_MODEL is gemma4-1b")
        fun requiredEmbeddingModel() {
            assertEquals("gemma4-1b", PackVersionDetector.REQUIRED_EMBEDDING_MODEL)
        }

        @Test
        @DisplayName("CURRENT_EMBEDDING_VERSION is gemma4-1b-hash-v1")
        fun currentEmbeddingVersion() {
            assertEquals("gemma4-1b-hash-v1", PackVersionDetector.CURRENT_EMBEDDING_VERSION)
        }
    }
}
