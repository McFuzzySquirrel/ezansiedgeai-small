# School Edge Node

An **optional capability booster** for eZansiEdgeAI — a low-cost device deployed at a school that enhances the learning experience over local WiFi, without ever being required.

## Purpose

The school edge node provides:

- **WiFi content distribution** — sync content packs to learner phones over the school LAN, eliminating the need for individual mobile data or USB transfers.
- **STT/TTS services** — shared Speech-to-Text and Text-to-Speech processing for learner devices that cannot run these models locally.
- **Optional heavier inference** — run a larger language model on the edge device to provide higher-quality answers when learners are in range.
- **Update server** — distribute app updates and new content pack versions across the school network.

## Design Principle

> **The phone must work without this.**
>
> The school edge node is an **enhancement, not a dependency**. Every learner-facing feature must function fully offline on the phone alone. The edge node makes things faster, richer, or more convenient — but never mandatory.

If a learner walks home, changes schools, or the edge node is offline, their learning experience continues uninterrupted.

## Key Components

1. **Local API Gateway** — lightweight HTTP/mDNS service that learner apps discover and connect to automatically on the school WiFi.
2. **Content Distribution** — serves content packs to phones, handles delta updates, and manages pack versioning.
3. **Update Server** — distributes APK updates and configuration changes to connected learner devices.
4. **Shared STT/TTS** — runs Whisper (or similar) for speech-to-text and a TTS engine, offloading voice processing from phones.
5. **Edge Inference (optional)** — hosts a larger quantized model for enhanced answer quality when the learner device is in WiFi range.

## Target Hardware

| Component         | Specification                                |
| ----------------- | -------------------------------------------- |
| Device            | Raspberry Pi 4/5 or similar SBC              |
| RAM               | 4–8 GB                                       |
| Storage           | 32–128 GB SD card or USB storage             |
| Network           | Connected to school WiFi router              |
| Power             | Standard mains power (with UPS recommended)  |
| Cost target       | < R3,000 total (device + storage + case)     |

The device must be low-maintenance, passively cooled, and operable by non-technical school staff.

## V1 Scope

> **Phase 3** — the school edge node is not part of the initial release.

Phase 3 deliverables:

- LAN node auto-discovery (mDNS/DNS-SD) by learner mobile app
- Content pack sync over school WiFi
- Basic health monitoring and remote diagnostics
- STT/TTS service hosting

## Setup Instructions

> **Phase 3** — setup instructions will be added when edge node development begins.
>
> The deployment target is a simple SD card image or containerised setup that school IT support (or a visiting technician) can deploy with minimal configuration.
