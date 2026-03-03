# Edge Device Architecture — eZansiEdgeAI

## Role

The school edge device is a **capability booster, not the brain**. If it is
powered off, stolen, or broken, every learner phone continues to function
identically. The edge device provides three categories of enhancement:

1. **Content distribution** — faster pack delivery over LAN.
2. **Speech services** — shared STT and TTS that phones cannot run locally.
3. **Optional heavier inference** — a larger model for complex queries.

---

## Hardware Envelope

The edge device must be deployable on whatever hardware a school can source:

| Tier | Example Hardware | RAM | Storage | Notes |
|------|-----------------|-----|---------|-------|
| Minimum | Raspberry Pi 5 | 8 GB | 64 GB SD | Sufficient for content + speech |
| Target | Refurbished laptop / mini-PC | 16 GB | 256 GB SSD | Can run larger model |
| Stretch | Donated workstation | 32 GB+ | 512 GB+ | Full capability set |

**Power:** Must tolerate unclean shutdown (load shedding). Filesystem must be
corruption-resistant (ext4 with journal, or btrfs).

**OS:** Linux-based (Ubuntu Server LTS or Raspberry Pi OS Lite). Headless
operation — no monitor/keyboard required after initial setup.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                   School Edge Device                 │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │           WiFi Service Layer                  │  │
│  │  ┌──────────────┐ ┌────────────────────────┐  │  │
│  │  │ mDNS         │ │ Local API Gateway      │  │  │
│  │  │ Advertiser   │ │ (REST, port 8080)      │  │  │
│  │  └──────────────┘ └────────────┬───────────┘  │  │
│  │  ┌──────────────┐              │              │  │
│  │  │ Content      │◄─────────────┤              │  │
│  │  │ Distribution │              │              │  │
│  │  │ Server       │   ┌──────────▼───────────┐  │  │
│  │  └──────────────┘   │ Update Server        │  │  │
│  │                     │ (pack versioning)     │  │  │
│  │                     └──────────────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │           AI Services                         │  │
│  │  ┌──────────────┐ ┌──────────────────────┐    │  │
│  │  │ Shared STT   │ │ Shared TTS           │    │  │
│  │  │ (Whisper     │ │ (Piper / Coqui)      │    │  │
│  │  │  small/base) │ │                      │    │  │
│  │  └──────────────┘ └──────────────────────┘    │  │
│  │  ┌──────────────────────────────────────────┐ │  │
│  │  │ Optional Larger LLM                      │ │  │
│  │  │ (7B class, Q4, for complex queries)      │ │  │
│  │  └──────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │           Data Services                       │  │
│  │  ┌──────────────┐ ┌──────────────────────┐    │  │
│  │  │ Shared       │ │ School Knowledge     │    │  │
│  │  │ Content      │ │ Base (optional       │    │  │
│  │  │ Cache        │ │ school-specific      │    │  │
│  │  │              │ │ content)             │    │  │
│  │  └──────────────┘ └──────────────────────┘    │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │           Management Layer                    │  │
│  │  ┌──────────────┐ ┌──────────────────────┐    │  │
│  │  │ Health       │ │ Admin CLI            │    │  │
│  │  │ Monitor      │ │ (setup + diagnostics)│    │  │
│  │  └──────────────┘ └──────────────────────┘    │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## WiFi Service Layer

### mDNS Advertiser

- Advertises `_ezansi._tcp.local` on the school LAN.
- Phones discover the edge device automatically — zero configuration for
  learners.
- Implementation: Avahi daemon (Linux) or built-in mDNS in the gateway
  service.

### Local API Gateway

All phone-to-edge communication goes through a single REST API:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/capabilities` | GET | Returns available services + versions |
| `/content/packs` | GET | Lists available content packs with versions |
| `/content/packs/{id}` | GET | Downloads a content pack (streamed) |
| `/content/packs/{id}/delta` | GET | Downloads incremental update |
| `/stt` | POST | Speech-to-text (audio upload, text response) |
| `/tts` | POST | Text-to-speech (text input, audio stream response) |
| `/infer` | POST | Forward a grounded prompt to the edge LLM (V2) |
| `/health` | GET | Device health status |

**Authentication:** None in V1 (LAN-only, trusted network). V2 will add
a simple shared secret for the school.

**Transport:** HTTP/1.1 over plain TCP on LAN. No TLS in V1 (devices are
on the same physical network). HTTPS can be added via self-signed cert in V2.

### Content Distribution Server

- Serves content packs as compressed archives (`.tar.zst`).
- Supports resumable downloads (HTTP Range headers) for reliability on
  flaky WiFi.
- Delta updates: edge computes binary diff between pack versions; phone
  downloads only the delta.
- Bandwidth: designed for 20+ concurrent phone downloads on a typical
  school WiFi AP (throttled to prevent saturation).

### Update Server

- Maintains a version manifest of all available content packs.
- Phones poll `/content/packs` on connection and compare against local
  versions.
- Updates are never forced — learner or teacher chooses when to update.

---

## AI Services

### Shared STT (Speech-to-Text)

| Property | Detail |
|----------|--------|
| Model | Whisper small or base (244 MB / 142 MB) |
| Languages | English, Afrikaans (V1) |
| Input | 16 kHz PCM WAV, max 30 s per request |
| Latency target | < 5 s for 10 s audio on RPi 5 |
| Concurrency | Queue-based; max 3 concurrent transcriptions |
| Runtime | whisper.cpp (C++, ARM-optimised) |

Phones record audio locally, send compressed audio to `/stt`, receive text.
If the edge is unreachable, voice input is unavailable and the UI falls back
to text-only with no error — the voice button simply does not appear.

### Shared TTS (Text-to-Speech)

| Property | Detail |
|----------|--------|
| Engine | Piper TTS (lightweight, offline) |
| Voices | English (SA accent preferred), Afrikaans |
| Output | 22 kHz PCM WAV, streamed to phone |
| Latency target | < 3 s first audio for short text |

### Optional Larger LLM (V2)

For queries that the phone's small model handles with low confidence:

| Property | Detail |
|----------|--------|
| Model class | 7B parameter (Mistral-7B, Qwen2-7B) |
| Quantisation | Q4_K_M GGUF |
| Size | ~4 GB |
| Requirement | ≥ 16 GB RAM on edge device |
| Protocol | Phone sends the same grounded prompt it would run locally |
| Decision logic | Phone runs locally first; if confidence heuristic is low, forwards to edge |

This is explicitly a V2 feature. V1 edge devices provide content + speech only.

---

## Data Services

### Shared Content Cache

- Mirrors the canonical content pack repository.
- Updated opportunistically when the edge device has internet access
  (e.g., teacher tethers a phone, or school has periodic connectivity).
- Acts as a local CDN for pack distribution to phones.

### School Knowledge Base (Future)

- Optional school-specific content (e.g., exam schedules, local resources).
- Authored by teachers via a simple web form on the edge device.
- Stored separately from curriculum packs; not mixed into retrieval unless
  explicitly opted in.

---

## Management Layer

### Initial Setup

The edge device is configured via a one-time setup script:

```bash
curl -sSL https://install.ezansi.org | bash -s -- --school-name "Mkhize Primary"
```

Or offline via USB:

```bash
sudo ./ezansi-setup.sh --offline --school-name "Mkhize Primary"
```

The setup script:
1. Installs dependencies (Python, whisper.cpp, Piper, llama.cpp).
2. Downloads or copies content packs.
3. Configures mDNS advertising.
4. Creates a systemd service for auto-start on boot.
5. Enables watchdog for crash recovery.

### Health Monitor

- Checks disk space, memory, CPU temperature, service status.
- Exposes `/health` endpoint for remote monitoring (if internet is available).
- Logs locally; log rotation to prevent disk fill.
- Auto-restarts crashed services via systemd.

### Load Shedding Resilience

- All data is stored on-disk (not in-memory only).
- Services start automatically on boot.
- Filesystem journaling prevents corruption on power loss.
- Content pack integrity is verified on startup (manifest check).

---

## Network Topology

```
    School WiFi Router (existing)
         │
    ┌────┴────┐
    │         │
Edge Device   Phones (10–40 per classroom)
(wired or     (connected to same WiFi)
 WiFi)
```

- The edge device does **not** act as a WiFi access point (avoids complexity).
- It joins the existing school WiFi network as a regular client.
- Phones and edge device must be on the same subnet for mDNS discovery.

---

## Capacity Planning

| Resource | 20 phones | 40 phones |
|----------|-----------|-----------|
| Content pack download (concurrent) | 10 streams | 20 streams (throttled) |
| STT requests (queued) | 3 concurrent | 3 concurrent, ~15 s queue |
| TTS requests (queued) | 5 concurrent | 5 concurrent, ~10 s queue |
| Disk (content cache) | 2 GB | 2 GB (same packs) |
| Disk (models) | 5 GB | 5 GB (same models) |
| RAM (idle) | ~1 GB | ~1 GB |
| RAM (peak: STT + TTS) | ~3 GB | ~4 GB |

---

## Related Documents

- [System Overview](system-overview.md)
- [Phone Architecture](phone-architecture.md)
- [Deployment Modes](deployment-modes.md)
