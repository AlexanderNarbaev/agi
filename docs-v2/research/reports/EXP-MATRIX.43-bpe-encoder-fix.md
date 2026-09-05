# EXP-MATRIX.43 — BPE encoder/decoder fix (RUN 78)

## Hypothesis

Standard GPT-2 byte-level BPE encoding should produce tokens with
`Ġ` (U+0120) prefix for words that follow a space, and these
should round-trip correctly through encode/decode.

## Bug

The original `BpeTokenizer` had an incomplete `BYTE_CHARS` map:
control bytes (including space=0x20) were not remapped to the
U+0100+ range used by GPT-2 byte-level encoding. Instead, space
was just left as ` ` (space char) in the symbols list, which meant
BPE merges never produced `Ġ...` tokens.

## Consequence

Encoded `"You are a helpful assistant."` produced tokens
`You`, `are`, `a`, `help`, `ful`, `assistant` — all WITHOUT the
`Ġ` prefix. When decoded, the spaces between words disappeared.

## Fix

Run 78 replaced the byte-level char map with the proper GPT-2
algorithm: printable bytes map to themselves, non-printable bytes
(including space) get assigned codepoints U+0100..U+0143.

## Verification

Real conversation after fix:
- T1: "Hello Alex! How can I assist you today?"
- T2: "Your name is Alex."
- T3: "The sky is usually blue."

These are now CORRECTLY formatted English sentences with proper
spacing. The model is producing good output - we just couldn't
read it before the fix.

## Cross-references

- EXP-MATRIX.40: end-to-end pipeline
- EXP-MATRIX.42: real conversation verification
