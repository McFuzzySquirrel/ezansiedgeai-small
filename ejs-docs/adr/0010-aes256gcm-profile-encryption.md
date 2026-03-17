---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0010"
  title: AES-256-GCM with Android Keystore for Learner Data Encryption
  date: 2026-03-17
  status: accepted
  session_id: ejs-session-2026-03-17-01
  session_journey: ejs-docs/journey/2026/ejs-session-2026-03-17-01.md

actors:
  humans:
    - id: McFuzzySquirrel
      role: lead developer
  agents:
    - id: learner-data-engineer
      role: data layer implementation
    - id: qa-test-engineer
      role: FakeAndroidKeyStore for testing

context:
  repo: ezansiedgeai-small
  branch: agent-forge/build-agent-team
---

# Session Journey

- Session Journey: `ejs-docs/journey/2026/ejs-session-2026-03-17-01.md`

# Context

eZansiEdgeAI stores learner profiles, preferences, and chat history on shared devices. Multiple learners (siblings) use the same phone. POPIA (Protection of Personal Information Act) requires that personal data be protected. Devices may be lost, stolen, or accessed by others. The app has no cloud backup — all data is local only.

Related: [ADR 0005 — Privacy-First Ethical Personalisation](0005-privacy-first-ethical-personalisation.md)

Key constraints:
- Data must be encrypted at rest (POPIA compliance)
- No external encryption libraries (minimise APK size and attack surface)
- Must work on Android 10+ (API 29+)
- Must be testable without a real Android device (unit tests)
- Shared devices mean physical access to storage is likely

---

# Session Intent

Implement encryption for all learner-personal data (profiles, preferences, chat history) that provides meaningful protection on shared devices while remaining testable and maintainable.

# Collaboration Summary

The learner-data-engineer agent designed the encryption layer during P0-105. The qa-test-engineer agent later created FakeAndroidKeyStore to enable unit testing without Android Keystore hardware. Both decisions were accepted without modification.

---

# Decision Trigger / Significance

Encryption implementation is a security-critical decision that affects data format, key management, test infrastructure, and crash recovery. Changing encryption schemes later requires data migration. This must be decided once and applied consistently across all data stores.

# Considered Options

## Option A: EncryptedSharedPreferences (Jetpack Security)

Android's built-in encrypted key-value store.

- **Pros:** Simple API, Google-maintained, handles key management
- **Cons:** Key-value only (no structured data), poor performance for chat history, limited to SharedPreferences semantics, adds Jetpack Security dependency (~200 KB)

## Option B: SQLCipher

Full-database encryption for SQLite.

- **Pros:** Transparent encryption, industry standard, protects entire database
- **Cons:** ~8 MB native library (significant for 50 MB APK budget), all-or-nothing encryption, complex key management, performance overhead on every query

## Option C: AES-256-GCM with Android Keystore (Selected)

Field-level encryption using Android's hardware-backed (or software-backed) Keystore. Each sensitive field is encrypted independently.

- **Pros:** Zero additional dependencies (built into Android SDK), hardware-backed keys when available, field-level granularity, ~0 KB APK overhead
- **Cons:** More code to maintain, requires FakeAndroidKeyStore for tests, per-field overhead

---

# Decision

Use AES-256-GCM encryption with keys stored in Android Keystore for all learner-personal data. Encryption is applied at the field level — individual profile fields and chat messages are encrypted/decrypted independently.

**Key files:**
- `core/data/src/main/kotlin/com/ezansi/app/core/data/encryption/ProfileEncryption.kt` — encryption/decryption operations
- `core/data/src/main/kotlin/com/ezansi/app/core/data/profile/ProfileStore.kt` — atomic encrypted file writes
- `core/data/src/main/kotlin/com/ezansi/app/core/data/chat/ChatHistoryStoreImpl.kt` — encrypted chat messages in SQLite
- `core/data/src/test/kotlin/com/ezansi/app/core/data/FakeAndroidKeyStore.kt` — test utility

---

# Rationale

1. **Zero dependency overhead:** AES-256-GCM and Android Keystore are part of the Android SDK. No additional libraries needed.
2. **POPIA compliance:** All personal data (names, preferences, chat content) is encrypted at rest. An attacker with file-system access sees only ciphertext.
3. **Field-level granularity:** Non-sensitive metadata (timestamps, pack IDs) can remain queryable in SQLite while personal content is encrypted. This enables efficient queries without decrypting entire databases.
4. **Hardware-backed keys:** On devices with a Trusted Execution Environment (TEE), keys never leave secure hardware. On devices without TEE, software-backed keys still provide meaningful protection.
5. **Testability:** FakeAndroidKeyStore registers an in-memory Security Provider that generates real AES keys — tests exercise actual encrypt/decrypt paths without requiring Android Keystore hardware.

The team accepted the trade-off of more encryption code in exchange for zero external dependencies and APK size impact.

---

# Consequences

### Positive
- Zero APK size overhead from encryption
- Hardware-backed key protection when TEE is available
- Field-level encryption allows selective protection
- Crash-safe: atomic write pattern (write-temp → fsync → rename) prevents corruption
- Fully testable via FakeAndroidKeyStore

### Negative / Trade-offs
- ~200 lines of encryption code to maintain (ProfileEncryption.kt)
- FakeAndroidKeyStore must be installed in every test class that touches encrypted data
- Per-field encryption adds ~1–2 ms overhead per encrypt/decrypt operation
- If Android Keystore is cleared (factory reset, key invalidation), all encrypted data becomes inaccessible — acceptable since profiles are recreatable

---

# Key Learnings

Android Keystore is powerful but requires a testing strategy. FakeAndroidKeyStore (registering a custom Security Provider with in-memory key storage) is the cleanest approach — it exercises real crypto paths without mocking. Call `FakeAndroidKeyStoreSpi.reset()` between tests to avoid key leakage.

---

# Agent Guidance

- **Always encrypt personal data** — names, preferences, chat messages. Never store in plaintext.
- **Use ProfileEncryption.kt** — do not create alternative encryption utilities
- **Non-sensitive data** (UI state, onboarding flags, pack metadata) does NOT need encryption — use SharedPreferences or plain files
- **In tests:** Call `installFakeAndroidKeyStore()` in `@BeforeEach` and `FakeAndroidKeyStoreSpi.reset()` in `@AfterEach`
- **Atomic writes:** Always use the write-temp → fsync → rename pattern for encrypted files to prevent corruption on abrupt power loss

---

# Reuse Signals (Optional)

```yaml
reuse:
  patterns:
    - "AES-256-GCM field-level encryption for on-device personal data"
    - "FakeAndroidKeyStore for unit testing crypto without hardware"
    - "Atomic file writes: temp → fsync → rename"
  prompts:
    - "Encrypt this field using ProfileEncryption before storing"
  anti_patterns:
    - "Do not store learner names, preferences, or chat content in plaintext"
    - "Do not use EncryptedSharedPreferences — it's key-value only"
    - "Do not add SQLCipher — too large for APK budget"
  future_considerations:
    - "If SQLCipher APK impact becomes acceptable, consider full-DB encryption for chat history"
    - "Key rotation strategy needed if app supports cloud backup in future"
```
