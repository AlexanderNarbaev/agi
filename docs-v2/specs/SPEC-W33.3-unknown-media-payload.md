# SPEC-W33.3 — UnknownMediaPayload: perception gaps as first-class knowledge

**Status:** implemented in this wave
**Operator directive:** *Architecture-General — "the pipeline can accept any binary stream,
extract metadata/features via generic methods (or honest refusal), and pass it to the
reasoning core."*
**Supersedes:** the closed `enum Kind { WAV, PNG, TEXT, UNKNOWN }` dead end.

---

## 1. The problem, stated as a capability gap

`MediaDecoding.classify` already does the hard part correctly: it dispatches on **magic
bytes**, not filenames, and it refuses honestly. A real JPEG gets told *"MATRIX decodes PNG
only; convert it to PNG, or add a decoder"* rather than *"unrecognised content"* — which
would have told an operator their file was corrupt when it is fine.

But the verdict is a dead end. It comes back as `Classification(UNKNOWN, false, reason)` and
the pipeline stops. Nothing about the file survives. So the system knows, at the instant of
refusal, that it is looking at 4096 bytes whose first two bytes are `FF D8` — and that
observation is discarded.

**Consequence, stated concretely.** A user who drops a JPEG in the inbox and later asks
*"what was in that file?"* gets nothing. The honest answer — *"I saw a 4 KB file that begins
with a JPEG signature. I cannot show you its contents because I have no JPEG decoder"* — is
available at the moment of ingestion and is thrown away. The mind is asked a question it has
already seen the answer to.

**This is the architecture the directive asks to avoid:** capability special-cased to the
formats that happen to be implemented, with everything else becoming a hole.

## 2. The contract

```java
public record MediaMetadata(
        String fileName,
        long sizeBytes,
        String detectedMime,        // nullable: null means "no signature matched"
        String signatureHex,        // nullable: first bytes as hex, for the answer to cite
        double printableRatio,      // 0.0..1.0, how much of a sample decodes as text
        Map<String,String> headerFields  // never null, may be empty
)
```

And on `MediaDecoding`:

```java
public static MediaMetadata probe(String fileName, byte[] bytes)
```

### Rules the contract guarantees

| # | Guarantee | Why it is stated as a rule |
|---|---|---|
| R1 | `probe` **never throws**, for any input including `null` and `bytes == null` | The whole point is that *any* binary stream is acceptable. A probe that throws is a hardcoded gate wearing a different hat. |
| R2 | `sizeBytes` is always populated (0 when unknown) | Size is the one fact always knowable. Losing it would make the payload useless. |
| R3 | `detectedMime` and `signatureHex` are `null` when unknown — never a guess | Article VI. `image/jpeg` must come from bytes, never from `.jpg` in the name. |
| R4 | `printableRatio` is measured, not inferred | Computable for any byte[] and the cheapest honest signal that "this is not an image". |
| R5 | `headerFields` never returns `null`; an unparsed container yields an empty map | Avoids null-returning ambiguity (Clean-Code law). |
| R6 | Probing a *decodable* format still works | Generalisation must not degrade the path that already functioned. |
| R7 | No wall-clock, no RNG, no network, no filesystem access | Article I. `probe` is a pure function of its arguments. |

### What `probe` deliberately does NOT do

- It does not decode anything. No JPEG/BMP/GIF/WebP/TIFF parser is added, per directive.
- It does not infer MIME from the filename.
- It does not report dimensions for formats it cannot parse. Guessing width/height from a
  corrupt header is precisely the fabrication this project exists to prevent.

## 3. Header extraction, per container, honestly bounded

Only what is genuinely readable from the leading bytes. Everything else yields empty.

| signature | container | fields extracted | honesty note |
|---|---|---|---|
| `89 50 4E 47 0D 0A 1A 0A` | PNG | `width`, `height`, `bitDepth`, `colorType` from IHDR | Real: fixed-offset big-endian ints. |
| `FF D8 FF` | JPEG | `marker=SOI`, `appSegments` = count of `FF Ex` markers | Honest: segment *count* is countable; segment *contents* are not parsed. No dimensions — those need SOF scanning. |
| `52 49 46 46` | RIFF | `riffSize` | Header only. |
| `1F 8B` | gzip | `compression=gzip` | Header only. |
| `25 50 44 46` | PDF | `version` | Header only. |
| `7F 45 4C 46` | ELF | `class`, `endianness`, `machine` | Header only; useful for the "an ELF named photo.jpg" case. |
| — | anything else | `printableRatio` only | The generic fallback. |

**Security constraint, from the directive's review requirement.** `probe` parses arbitrary
hostile input, so:

- every offset read is bounds-checked before use — a 4-byte "PNG" must not index into a
  10-byte array;
- width/height use `& 0xFFL` style masks rather than unguarded casts;
- `headerFields` is capped in size so a crafted file cannot force unbounded allocation;
- the rendered line is truncated so a megabyte of bytes does not become a megabyte of prose;
- no exception from parsing escapes — R1 covers it.

## 4. Routing to the reasoning core

Perception gaps become retrievable knowledge via `SimpleKnowledgeBase.addDocument`:

```
title:   media metadata: photo.jpg
content: "File photo.jpg is 4096 bytes. Signature ffd8ff matches JPEG. Header fields:
          marker=SOI, appSegments=2. MATRIX has no decoder for JPEG, so its CONTENTS were
          not perceived."
```

Retrievable by `retrieve("photo.jpg", k)`. That makes *"what was in that file?"* answerable
from stored knowledge **even though the content was never decoded** — the architectural
capability the directive asks for: the reasoning core handles a perception gap without
special-casing the modality.

## 5. Acceptance criteria (BDD-executable)

| ID | Criterion |
|---|---|
| AC1 | `probe` on a truncated PNG returns `detectedMime=image/png` with no dimensions rather than throwing |
| AC2 | `probe(null, null)` returns `sizeBytes=0`, null signature, no throw |
| AC3 | A real JPEG header yields `detectedMime=image/jpeg`, `marker=SOI`, non-null `signatureHex` |
| AC4 | An ELF binary named `photo.jpg` yields an ELF description, **never** `image/jpeg` |
| AC5 | `probe` on bytes that are `PNG` but fewer than 33 bytes (short of a full IHDR) does not throw |
| AC6 | `printableRatio` for random binary is materially lower than for ASCII text |
| AC7 | Storing the rendered description makes `retrieve("photo.jpg")` return it |
| AC8 | `probe` on a real decodable PNG still reports `image/png` — no regression |
| AC9 | A rendered line derived from a large hostile input is bounded in length |
| AC10 | Probing is deterministic: identical input yields an identical record |

## 6. Explicitly out of scope

- Adding a JPEG/BMP/GIF/WebP/TIFF decoder. The directive forbids it and this capability does
  not need it: *"I know the format, I cannot read it"* is already useful and already honest.
- Replacing `Classification`. `decodable=false` remains the truthful answer to "can you show
  me the picture"; `MediaMetadata` answers a different question — "what did you see?".
  Merging them would make one field try to answer two questions, which is how `decodable`
  became ambiguous in the first place.