# W50-03 — Local model preflight and teacher evaluation

**Wave:** W50 — MATRIX local evidence-grounded demonstrator.
**Work item:** W50-03 — fresh hardware preflight + evaluation of ONE local open-weight
multilingual model for optional use as a *teacher* during knowledge acquisition.
**Measured:** 2026-11-07, 19:51–20:26 MSK.
**Verdict:** §7 — *the teacher does not improve extraction; the deterministic path stays
the critical path.*

Every number below is a measurement, with the command that produced it. Where a number
could not be taken, the text says **not measured** and why. No Java source, no test and
no file other than this one was modified; the wave's downloads live outside the
repository tree (`~/.cache/w50/`).

---

## 1. Hardware preflight (measured 2026-11-07)

### 1.1 The checked-in 2026-11-04 profile, re-verified

`docs-v2/operations/HARDWARE-PROFILE.md` claims *AMD Ryzen 9 9955HX* and *NVIDIA RTX
5070 Ti Laptop GPU, 12227 MiB VRAM*. Both were re-measured from scratch below and both
**hold**. Its `Root available | 198 GiB` row does **not** hold — free space is 192 GiB —
which is ordinary disk drift, not a wrong row.

### 1.2 CPU

```console
$ lscpu | grep -E "^(Architecture|CPU\(s\)|On-line CPU|Vendor ID|Model name|CPU family|Model:|Thread|Core|Socket|Stepping|CPU max MHz|L1d|L1i|L2 cache|L3 cache|NUMA)"
Architecture:                            x86_64
CPU(s):                                  32
On-line CPU(s) list:                     0-31
Vendor ID:                               AuthenticAMD
Model name:                              AMD Ryzen 9 9955HX 16-Core Processor
CPU family:                              26
Model:                                   68
Thread(s) per core:                      2
Core(s) per socket:                      16
Socket(s):                               1
Stepping:                                0
CPU(s) scaling MHz:                      80%
CPU max MHz:                             5460,5269
L1d cache:                               768 KiB (16 instances)
L1i cache:                               512 KiB (16 instances)
L2 cache:                                16 MiB (16 instances)
L3 cache:                                64 MiB (2 instances)
NUMA node(s):                            1

$ grep -m1 "^Flags" /proc/cpuinfo
Flags:                                   fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx mmxext fxsr_opt pdpe1gb rdtscp lm constant_tsc rep_good amd_lbr_v2 nopl xtopology nonstop_tsc cpuid extd_apicid aperfmperf rapl pni pclmulqdq monitor ssse3 fma cx16 sse4_1 sse4_2 movbe popcnt aes xsave avx f16c rdrand lahf_lm cmp_legacy svm extapic cr8_legacy abm sse4a misalignsse 3dnowprefetch osvw ibs skinit wdt tce topoext perfctr_core perfctr_nb bpext perfctr_llc mwaitx cpuid_fault cpb cat_l3 cdp_l3 hw_p_state ssbd mba perfmon_v2 ibrs ibpb stibp ibrs_enhanced vmmcall fsgsbase tsc_adjust bmi1 avx2 smep bmi2 erms invpcid cqm rdt_a avx512f avx512dq rdseed adx smap avx512ifma clflushopt clwb avx512cd sha_ni avx512bw avx512vl xsaveopt xsavec xgetbv1 xsaves cqm_llc cqm_occup_llc cqm_mbm_total cqm_mbm_local user_shstk avx_vnni avx512_bf16 clzero irperf xsaveerptr rdpru wbnoinvd cppc arat npt lbrv svm_lock nrip_save tsc_scale vmcb_clean flushbyasid decodeassists pausefilter pfthreshold v_vmsave_vmload vgif x2avic v_spec_ctrl vnmi avx512vbmi umip pku ospke avx512_vbmi2 gfni vaes vpclmulqdq avx512_vnni avx512_bitalg avx512_vpopcntdq rdpid bus_lock_detect movdiri movdir64b overflow_recov succor smca fsrm avx512_vp2intersect flush_l1d amd_lbr_pmc_freeze
```

**ISA flags of interest** (exact token match, not substring — `avx` is a substring of
`avx512f`, so a grep would report AVX-512 on an AVX2-only CPU): `avx` **yes**, `avx2`
**yes**, `avx512f` **yes**, `avx512dq` **yes**, `avx512cd` **yes**, `avx512bw` **yes**,
`avx512vl` **yes**, `fma` **yes**, `f16c` **yes**, `sha_ni` **yes**, `avx512_vnni`
**yes**, `avx512_vp2intersect` **yes**; `amx_tile` / `amx_int8` / `amx_bf16` **absent**
(Zen 5 has no AMX). **16 physical cores / 32 logical CPUs.**

### 1.3 RAM

```console
$ free -h
               total        used        free      shared  buff/cache   available
Mem:            59Gi        18Gi        22Gi       1,3Gi        20Gi        40Gi
Swap:            37Gi          0B        37Gi

$ head -4 /proc/meminfo
MemTotal:       62390524 kB
MemFree:        23283096 kB
MemAvailable:   42515924 kB
Buffers:          849204 kB
```

Total **59.5 GiB** (`MemTotal` 62390524 kB), available **40.6 GiB** (`MemAvailable`
42515924 kB). Swap 37.8 GiB (`zram0`, lzo-rle).

### 1.4 GPU, driver, CUDA

```console
$ nvidia-smi
Sat Nov  7 19:51:51 2026
+-----------------------------------------------------------------------------------------+
| NVIDIA-SMI 595.99.02              Driver Version: 595.99.02      CUDA Version: 13.2     |
+-----------------------------------------+------------------------+----------------------+
| GPU  Name                 Persistence-M | Bus-Id          Disp. | Volatile Uncorr. ECC |
| Fan  Temp   Perf          Pwr:Usage/Cap  |           Memory-Usage       GPU-Util  Compute M. |
|                                         |                        |               MIG M. |
|=========================================+========================+======================|
|   0  NVIDIA GeForce RTX 5070 ...    Off |   00000000:01:00.0 Off |                  N/A |
| N/A   42C    P2             18W  /  140W |      14MiB /  12227MiB |      6%      Default |
|                                         |                        |                  N/A |
+-----------------------------------------+------------------------+----------------------+

$ nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv
name, memory.total [MiB], driver_version
NVIDIA GeForce RTX 5070 Ti Laptop GPU, 12227 MiB, 595.99.02

$ ls /dev/nvidia*
/dev/nvidia-modeset  /dev/nvidia-uvm  /dev/nvidia-uvm-tools  /dev/nvidia0  /dev/nvidiactl

$ nvcc --version | tail -2
Cuda compilation tools, release 12.4, V12.4.131
Build cuda_12.4.r12.4/compiler.34097967_0

$ /usr/local/cuda-13.1/bin/nvcc --version | tail -2
Cuda compilation tools, release 13.1, V13.1.115
Build cuda_13.1.r13.1/compiler.37061995_0
```

- GPU: **NVIDIA GeForce RTX 5070 Ti Laptop GPU**, total VRAM **12227 MiB** (11.94 GiB) —
  the old profile's "~12 GiB" was right. **14 MiB** in use at rest.
- Driver **595.99.02**. Driver CUDA *compatibility* version **13.2** — the newest runtime
  the driver supports, **not** an installed toolkit.
- Toolkits installed: **nvcc 12.4** (`/usr/bin/nvcc`) and **nvcc 13.1**
  (`/usr/local/cuda-13.1/bin/nvcc`). Both `libcudart.so.12` (`/usr/lib/x86_64-linux-gnu`)
  and `libcudart.so.13` (`/usr/local/cuda/targets/x86_64-linux/lib`) are on the loader
  path; `libcublas.so.12`/`.13` likewise. cuDNN 9 present.
- llama.cpp reports the *usable* VRAM, smaller than the device total:
  `ggml_cuda_init: found 1 CUDA devices (Total VRAM: 11774 MiB)`,
  `Device 0: NVIDIA GeForce RTX 5070 Ti Laptop GPU, compute capability 12.0, VMM: yes`.

### 1.5 Disk available for a model cache

```console
$ df -h
Filesystem      Size  Used Avail Use% Mounted on
tmpfs            12G  4,5M   12G   1% /run
/dev/nvme0n1p2  469G  253G  192G  57% /
tmpfs            30G  8,0K   30G   0% /dev/shm
/dev/nvme0n1p1  1,1G   39M  1,1G   4% /boot/efi
tmpfs            30G  1,1G   29G   4% /tmp

$ du -sb /home/alexandr-narbaev/Projects/agi/models
14708572462	/home/alexandr-narbaev/Projects/agi/models
```

**192 GiB free on `/`** before this wave's downloads; **190 GiB** after. `/tmp` is a
30 GiB RAM-backed tmpfs, so a model cache belongs on `/dev/nvme0n1p2`. This wave's cache
(`~/.cache/w50/`) consumed **1,1 G** (GGUF) + **1,9 G** (llama.cpp) = **3,0 G**.

### 1.6 Is there a llama.cpp binary on the machine?

**Before this wave: no.** Nothing on `PATH`, nothing under `~/` or `/usr/local`.

```console
$ for b in llama-server llama-cli llama main ollama; do printf "%s: " "$b"; command -v "$b" || echo "NOT FOUND"; done
llama-server: NOT FOUND
llama-cli: NOT FOUND
llama: NOT FOUND
main: NOT FOUND
ollama: NOT FOUND

$ find /usr/local -maxdepth 4 -iname "*llama*" 2>/dev/null | head -40
(no output)

$ find /usr /opt /home/alexandr-narbaev -maxdepth 6 \( -name "libllama*" -o -name "libggml*" -o -name "*.gguf" \) -not -path "*/node_modules/*" 2>/dev/null | head -40
(no output)
```

**After this wave: yes** — installed outside the repository at
`~/.cache/w50/llamacpp/cuda/llama-b12189/` (`llama-server`, `llama-cli`, `llama-bench`,
`libggml-cuda.so`). See §3.

### 1.7 The repository's own probe, run unmodified

`scripts/hardware-probe.sh` (1060 lines) was run in `--stdout` mode, which **writes
nothing** (only `--markdown <path>` / the bare default write a file). Exit code 0.

```console
$ bash scripts/hardware-probe.sh --stdout > /tmp/w50-hardware-probe.md 2>/tmp/w50-hardware-probe.err
EXIT=0
--- stdout bytes: 8807 ---
```

The generated profile agrees with the ad-hoc probes above (RAM 59.5 GiB total / 40.8 GiB
available, VRAM 12227 MiB, driver 595.99.02, driver CUDA compatibility 13.2, installed
toolkit 12.4, ISA table, 192 GiB root available). Two facts it reports that the ad-hoc
probes did not are carried forward:

- `addCUDA() WITHOUT LD_LIBRARY_PATH | FAILED OrtException` — **this is MATRIX's own
  configuration**; `addCUDA() WITH LD_LIBRARY_PATH | OK`. MATRIX's Java ONNX path does
  **not** use the GPU as the code stands.
- `OnnxRuntimeAdapter` requests CUDA inside a try/catch that logs a warning and
  continues, and the provider diagnostic is a hardcoded `CPUExecutionProvider`.

---

## 2. Local model assets already present

Inventory of `models/` (repo tree, 14 708 572 462 B) and the model caches, with the
sha256 of every file found (`find models -type f … | xargs sha256sum`). A licence is
stated **only** where a licence file or a model card actually states it; otherwise the
cell reads *licence not recorded*.

### 2.1 Model weights and graphs

| Path | Bytes | sha256 | Licence | Note |
|---|---:|---|---|---|
| `models/onnx/qwen05b/model.onnx` | 1 116 250 | `5f59f2cd3eb36963746ca22411f83bde429e9dece9722389653882dfc4e0145d` | Apache-2.0 (`models/hf_cache/qwen05b/LICENSE`) | `Qwen2ForCausalLM`, hidden 896, 24 layers, 14 heads, vocab 151 936, `max_position_embeddings` 32768 → Qwen2.5-0.5B-Instruct exported to ONNX; `DEFAULT_MODEL` in `OnnxRuntimeAdapter.java` |
| `models/onnx/qwen05b/model.onnx_data` | 2 520 669 824 | `72303ef1d77c02e4efc46a35b5cc60a841213518baa0ddae6247d44f1686265a` | as above | external tensor data |
| `models/onnx/qwen05b_hidden/model.onnx` | 1 116 250 | `5f59f2cd3eb36963746ca22411f83bde429e9dece9722389653882dfc4e0145d` | licence not recorded | same weights, LM head removed (identical sha256) |
| `models/onnx/qwen05b_hidden/model.onnx_data` | 2 520 669 824 | `72303ef1d77c02e4efc46a35b5cc60a841213518baa0ddae6247d44f1686265a` | licence not recorded | |
| `models/hf_cache/qwen05b/model.safetensors` | 988 097 824 | `fdf756fa7fcbe7404d5c60e26bff1a0c8b8aa1f72ced49e7dd0210fe288fb7fe` | Apache-2.0 | Qwen2.5-0.5B-Instruct, HF revision `7ae557604adf67be50417f59c2c2f167def9a775` (`…/hub/models--Qwen--Qwen2.5-0.5B-Instruct/refs/main`) |
| `models/external/qwen2.5-0.5b/model.safetensors` | 988 097 824 | `88c142557820ccad55bb59756bfcfcf891de9cc6202816bd346445188a0ed342` | licence not recorded (no licence file in this dir) | |
| `models/external/qwen2.5-1.5b/model.safetensors` | 3 087 467 144 | `a961db72e75d52b18e6b0c9d379e51a26973b233385e0e127fdda7d648aec796` | Apache-2.0 (`models/external/qwen2.5-1.5b/LICENSE`, sha256 `832dd9e0…`, byte-identical to the Qwen2.5-0.5B licence file) | |
| `models/external/gpt2/model.safetensors` | 497 780 432 | `d0fd661db038078cd85aa37b183966c26a97dd9decc5a6cf4e50030826e31ec0` | licence not recorded | |
| `models/external/dialogpt-small/model.safetensors` | 248 897 656 | `5323892d7efe245c0d89a156946a32423537e7c97f08c4afa7909ff0268c9533` | licence not recorded | |
| `models/external/distilbert-base-sst2/model.safetensors` | 267 832 560 | `61afb985df6fbe77fd84138129481f0a4444c02893782984e330f874f43cee4b` | licence not recorded | |
| `models/external/distilbert-base-sst2/model.onnx` | 803 302 | `b7682868b053bf26c3b0d6d731f9dbeb7ff94d877c76e3835353c4b6d23e5268` | licence not recorded | |
| `models/external/distilbert-base-sst2/model.onnx.data` | 267 827 200 | `fcf39b6a54ca9d1db41f39327ac39087822826ca42fa69dee391a0059c41ebdf` | licence not recorded | |
| `models/external/distilbert-sst2/model.safetensors` | 253 064 | `0033443c96ef89801f6e347ea1b06814dcdfb57c5b766104c662740d63615d1b` | licence not recorded | |
| `models/external/distilbert-sst2/model.onnx` | 286 484 | `0967d5dbac55478cbb5ca5cb22e7bfe41577829464013496459e37d3f49d6813` | licence not recorded | |
| `models/external/distilbert-sst2/model.onnx.data` | 252 368 | `a21c8b1a730411ac87eb4f1512b53f3eac6d3f3593bc24c0be22e46df658056b` | licence not recorded | |
| `models/external/llama-3.2-1b/` | 7 712 (LICENSE) + 41 239 (README) | `0b4284c1f87029e67654c7953afa16279961632cf73dcfe33374c4c2f298fa35` (LICENSE.txt) | **Llama 3.2 Community License Agreement** | **README + licence only — the weights were never downloaded** (`original/` is empty) |
| `models/teacher/teacher_ffn16.onnx` | 5 024 | `7378d5d7aa9d7ced30a5e62f6c51ce75b93d186c7ee650eac4d16025c3026cc9` | licence not recorded | a 5 KiB ONNX graph, not a language model |
| `models/bitnet/chain-j.bin` | 42 906 742 | `7d242740a626d4f10196d5c450dbb3bd47c2e69cd82a9b96115f9ef75f97d487` | licence not recorded | |

### 2.2 Tokenizers, configs and licence files

```console
$ find models -type f \( -name "*.json" -o -name "*.txt" -o -name "*.jinja" \) -size +1k -print0 | xargs -0 sha256sum
c0382117ea329cdf097041132f6d735924b697924d6f6fc3945713e96ce87539  models/hf_cache/qwen05b/tokenizer.json
5b5d4f65d0acd3b2d56a35b56d374a36cbc1c8fa5cf3b3febbbfabf22f359583  models/hf_cache/qwen05b/tokenizer_config.json
ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910  models/hf_cache/qwen05b/vocab.json
599bab54075088774b1733fde865d5bd747cbcc7a547c5bc12610e874e26f5e3  models/hf_cache/qwen05b/merges.txt
9c5ae00e602b8860cbd784ba82a8aa14e8feecec692e7076590d014d7b7fdafa  models/onnx/qwen05b/tokenizer.json
0a04a9d7d4a62b28482bdfe726c122756de85714fb64166ace92ae75b8f57614  models/onnx/qwen05b/tokenizer_config.json
cd8e9439f0570856fd70470bf8889ebd8b5d1107207f67a5efb46e342330527f  models/onnx/qwen05b/chat_template.jinja
ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910  models/onnx/qwen05b/vocab.json
8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5  models/onnx/qwen05b/merges.txt
e8d5e8d6aaf17311a89b78e3914fca868cc77312513a276b1701e22fb73d62a3  models/onnx/qwen05b/config.json
9c5ae00e602b8860cbd784ba82a8aa14e8feecec692e7076590d014d7b7fdafa  models/onnx/qwen05b_hidden/tokenizer.json
0a04a9d7d4a62b28482bdfe726c122756de85714fb64166ace92ae75b8f57614  models/onnx/qwen05b_hidden/tokenizer_config.json
cd8e9439f0570856fd70470bf8889ebd8b5d1107207f67a5efb46e342330527f  models/onnx/qwen05b_hidden/chat_template.jinja
ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910  models/onnx/qwen05b_hidden/vocab.json
8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5  models/onnx/qwen05b_hidden/merges.txt
3cd111f8f36968066fd177c2dff842d534152d7a2934aea4b15aa8c3fc923a0d  models/onnx/qwen05b_hidden/config.json
1fe93b6152957cf9cfd6d89002467f789ce8b3f3e000b3a2edf27c808ddd0b9e  models/external/gpt2/tokenizer.json
b11d992d46b2bcec1b754e8993bb3a22f90d8f10d1c1e5133458591eb4ddc53a  models/external/dialogpt-small/tokenizer.json
d241a60d5e8f04cc1b2b3e9ef7a4921b27bf526d9f6050ab90f9267a1f9e5c66  models/external/distilbert-base-sst2/tokenizer.json
d241a60d5e8f04cc1b2b3e9ef7a4921b27bf526d9f6050ab90f9267a1f9e5c66  models/external/distilbert-sst2/tokenizer.json
c0382117ea329cdf097041132f6d735924b697924d6f6fc3945713e96ce87539  models/external/qwen2.5-1.5b/tokenizer.json
c91efca15ceff6e9ee9424db58a6f59cd41294e550a86cbd07e3c1fb500b34f9  models/external/qwen2.5-1.5b/tokenizer_config.json
ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910  models/external/qwen2.5-1.5b/vocab.json
599bab54075088774b1733fde865d5bd747cbcc7a547c5bc12610e874e26f5e3  models/external/qwen2.5-1.5b/merges.txt
832dd9e00a68dd83b3c3fb9f5588dad7dcf337a0db50f7d9483f310cd292e92e  models/hf_cache/qwen05b/LICENSE
832dd9e00a68dd83b3c3fb9f5588dad7dcf337a0db50f7d9483f310cd292e92e  models/external/qwen2.5-1.5b/LICENSE
```

### 2.3 Model caches outside the repository

```console
$ for d in ~/.cache/huggingface ~/.cache/llama.cpp ~/.ollama ~/.local/share/ollama /var/cache/models; do … done
/home/alexandr-narbaev/.cache/huggingface: 224K
/home/alexandr-narbaev/.cache/llama.cpp: absent
/home/alexandr-narbaev/.ollama: absent
/home/alexandr-narbaev/.local/share/ollama: absent
/var/cache/models: absent
```

The HF cache outside the repo is **224 KiB** — metadata only, no weights. The wave's own
cache is `~/.cache/w50/` (§3).

### 2.4 Download helpers already in the repository

- `scripts/download_models.sh` — a HuggingFace `snapshot_download` → Avro converter for
  ten models (SmolLM2-360M, Qwen2.5-1.5B, Qwen3-0.6B/1.7B, DeepSeek-R1-Distill-Qwen-1.5B,
  Gemma-3-1B, Llama-3.2-3B, Phi-4-mini, Yi-1.5-Chat-6B, Mistral-7B), writing to
  `models/pretrained/`. **Not run in this wave** — it produces Avro neuron tables, not
  what W50-03 measures — and it ignores `*.gguf` on download, so it is not a GGUF path.
- `scripts/download_distilbert_onnx.py`, `scripts/distill-onnx.sh`,
  `scripts/import_qwen_weights.py`, `scripts/gen_teacher_onnx.py` — ONNX-side helpers.

### 2.5 The ONNX path the application can already start

`matrix-core/src/main/java/io/matrix/api/OnnxRuntimeAdapter.java` loads
`models/onnx/qwen05b/model.onnx` (the constant `DEFAULT_MODEL`) and logs
`OnnxRuntimeAdapter: loaded {} (inputs={}, outputs={}, useGpu={})`.
`QwenOnnxBridge.java` drives generation on top. The adapter keeps an `AtomicLong
inferenceCount` incremented per forward pass (`greedyNextToken`) and exposes it as
`inferenceCount()` — the existing instrumentation this wave proposes to build the
teacher counter on (§5).

---

## 3. Model evaluated

### 3.1 Identity

| Field | Value |
|---|---|
| Model | **Qwen2.5-1.5B-Instruct, GGUF** |
| Repository | `Qwen/Qwen2.5-1.5B-Instruct-GGUF` (Hugging Face) |
| Immutable revision | `91cad51170dc346986eccefdc2dd33a9da36ead9` (`lastModified 2024-09-20T06:31:38Z`) |
| Licence | **Apache-2.0** — model card `license: apache-2.0`; the `LICENSE` file is the Apache License 2.0 text |
| Licence URL | `https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/91cad51170dc346986eccefdc2dd33a9da36ead9/LICENSE` (the card's `license_link` points at `…/blob/main/LICENSE`) |
| Model file | `qwen2.5-1.5b-instruct-q4_k_m.gguf` |
| File size | **1 117 320 736 bytes** |
| **Model-file SHA-256** | **`6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`** |
| Quantization | **Q4_K_M** (llama.cpp: `Q4_K - Medium`), 1.78 B params, 1.04 GiB |
| Why this one | open-weight (Apache-2.0), officially multilingual — the card states *"Multilingual support for over 29 languages, including Chinese, English, French, Spanish, Portuguese, German, Italian, **Russian**, Japanese, Korean, Vietnamese, Thai, Arabic, and more"* — small enough for the 12 GiB VRAM, and already half-present in the repo as `models/external/qwen2.5-1.5b` |

```console
$ sha256sum qwen2.5-1.5b-instruct-q4_k_m.gguf
6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e  qwen2.5-1.5b-instruct-q4_k_m.gguf

$ head -4 LICENSE
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

$ grep -n "Multilingual" README.md
21:- **Multilingual support** for over 29 languages, including Chinese, English, French, Spanish, Portuguese, German, Italian, Russian, Japanese, Korean, Vietnamese, Thai, Arabic, and more.
```

### 3.2 Inference runtime

| Field | Value |
|---|---|
| Runtime | **llama.cpp**, tag `b12189`, build `0.9.0-dev (build 12189, commit 80d2c0c2c)`, published 2026-11-07T12:15:59Z |
| Binaries | `llama-b12189-bin-ubuntu-cuda-12.8-x64.tar.gz` (sha256 `664f720005135f621960b8f1550979a1bdd73b8ba887e2a47a314b0154fcaa0a`) + `cudart-llama-b12189-bin-ubuntu-cuda-12.8-x64.tar.gz` (sha256 `81408740817d383bd18b9f2f8d4fda14bbd1cf9dac63331e7b56c6fb82380650`) |
| Install path | `~/.cache/w50/llamacpp/cuda/llama-b12189/` — **outside the repository** |
| GPU backend | CUDA, **full offload** (`-ngl 99`), device compute capability 12.0, VMM enabled |
| CPU fallback | `-ngl 0`, 16 threads, `libggml-cpu-zen4.so` (matched to this CPU) |
| Context length | `-c 8192`, `--parallel 1` (`n_slots = 1, n_ctx_slot = 8192`) |
| Decoding | greedy (`temperature 0.0`, `top_k 1`), `repeat_penalty 1.15`, `seed 20261107` |

```console
$ llama-cli --version
version: 0.9.0-dev (build 12189, commit 80d2c0c2c)
built with GNU 13.3.0 for Linux x86_64
```

### 3.3 The exact command lines

**Server** (used for every extraction and language measurement):

```bash
export LD_LIBRARY_PATH="$HOME/.cache/w50/llamacpp/cuda/llama-b12189:$HOME/.cache/w50/llamacpp/cudart/cudart-llama-b12189-bin-ubuntu-cuda-12.8-x64:$LD_LIBRARY_PATH"
$HOME/.cache/w50/llamacpp/cuda/llama-b12189/llama-server \
  -m $HOME/.cache/w50/gguf/qwen2.5-1.5b-instruct-q4_k_m.gguf \
  -ngl 99 -c 8192 --parallel 1 -t 16 --seed 20261107 \
  --host 127.0.0.1 --port 8099
```

**CLI** (used for the peak-RAM measurement):

```bash
/usr/bin/time -v $HOME/.cache/w50/llamacpp/cuda/llama-b12189/llama-cli \
  -m $HOME/.cache/w50/gguf/qwen2.5-1.5b-instruct-q4_k_m.gguf \
  -ngl 99 -c 2048 -n 128 -t 16 -p "Explain plate tectonics." --no-warmup
```

**Throughput:** `llama-bench -m …/qwen2.5-1.5b-instruct-q4_k_m.gguf -ngl 99 -t 16 -p 512 -n 128 -r 2`
and the same with `-ngl 0 … -r 1` for the CPU path.

### 3.4 Measurements

**Load time** (process launch → `/health` 200, warm page cache):

```console
HEALTHY after .839258348 s
T0=1794070971.958139248
T1=1794070972.797397596
```

Server-internal: `model loaded` at `0.00.643.436`, `listening on http://127.0.0.1:8099`
at `0.00.643.443`.

**Throughput (`llama-bench`, 512-token prompt / 128 generated):**

```console
| model                          |       size |     params | backend    | ngl |            test |                  t/s |
| qwen2 1.5B Q4_K - Medium       |   1.04 GiB |     1.78 B | CUDA       |  99 |           pp512 |   18336.97 ± 1057.17 |
| qwen2 1.5B Q4_K - Medium       |   1.04 GiB |     1.78 B | CUDA       |  99 |           tg128 |        307.76 ± 0.28 |

| qwen2 1.5B Q4_K - Medium       |   1.04 GiB |     1.78 B | CUDA       |   0 |           pp512 |       3420.65 ± 0.00 |
| qwen2 1.5B Q4_K - Medium       |   1.04 GiB |     1.78 B | CUDA       |   0 |           tg128 |         33.00 ± 0.00 |
```

**Latency** (`TTFT` = time to the first streamed token; `GEN_S` = total − TTFT), from the
`/v1/chat/completions` stream:

| Phase | Value |
|---|---|
| TTFT, language probes | 0.010 – 0.020 s |
| TTFT, extraction chunks (2 093-token prompt) | 0.013 – 0.049 s |
| Generation latency, language probes | 0.031 – 0.344 s |
| Generation latency, extraction chunks | 0.061 – 5.518 s (bounded by `max_tokens` 1400) |
| Throughput | 252 – 297 tok/s (GPU) / 33.0 tok/s (CPU) |

**Peak RAM and VRAM:**

```console
$ /usr/bin/time -v … llama-cli -ngl 99 -c 2048 -n 128 …
	User time (seconds): 0.78
	System time (seconds): 0.36
	Elapsed (wall clock) time (h:mm:ss or m:ss): 0:01.22
	Maximum resident set size (kbytes): 1480168

$ grep VmHWM /proc/$(pgrep -f "llama-server.*8099")/status
VmHWM:	 1497816 kB

$ sort -rn /tmp/w50/vram-cli.txt | head -3     # nvidia-smi sampled every 0.1 s
2685
2685
2685
```

- Peak **host RAM**: **1 480 168 kB ≈ 1.41 GiB** (llama-cli) / **1 497 816 kB ≈ 1.43 GiB**
  (llama-server `VmHWM`).
- Peak **VRAM**: **2685 MiB** during llama-cli (full offload, 2048 ctx); **1460 MiB**
  during llama-bench; **1442 MiB** with the server idle-loaded. Against the 12 227 MiB
  device total a 1.5B Q4_K_M leaves ~9.5 GiB free.

### 3.5 Observed language behaviour — real English and real Russian

Six probes through the chat endpoint, so the model's own ChatML template applies.
Prompts and outputs are pasted verbatim from the run. The **extraction** system prompt,
reused in §4:

```
You extract factual propositions from a text.
Rules:
1. Each proposition must be copied VERBATIM from the text. Do not paraphrase, do not translate, do not summarise.
2. Output one proposition per line. No numbering, no bullets, no commentary, no preamble.
3. Only output propositions that the text states as facts.
4. Output nothing except the propositions themselves.
5. Stop when you have listed them all.
```

The **QA** system prompts are `Answer using ONLY the text. One sentence. If the text does
not answer the question, say so.` and, in Russian, `Отвечай, используя ТОЛЬКО текст. Одно
предложение. Если текст не отвечает на вопрос, так и скажи.`

#### EN-1 — extract propositions VERBATIM from a real English paragraph

```
PROMPT[user]: TEXT:
Scientists now have a fairly good understanding of how the plates move and how such movements relate to earthquake activity. Most movement occurs along narrow zones between plates where the results of plate-tectonic forces are most evident. The rate of spreading along the Mid-Atlantic Ridge averages about 2.5 centimeters per year (cm/yr), or 25 km in a million years.

PROPOSITIONS:

OUTPUT:
Scientists now have a fairly good understanding of how the plates move and how such movements relate to earthquake activity.
Most movement occurs along narrow zones between plates where the results of plate-tectonic forces are most evident.
The rate of spreading along the Mid-Atlantic Ridge averages about 2.5 centimeters per year (cm/yr), or 25 km in a million years.
```

**Perfect**: all three sentences copied verbatim, one per line, no commentary.
`TTFT_S=0.020 TOTAL_S=0.312 GEN_S=0.292 PROMPT_TOKENS=185 COMPLETION_TOKENS=78 GEN_TPS=267.0`

#### EN-2 — answer a question in English, grounded in the text

```
PROMPT[user]: TEXT:
Divergent boundaries occur along spreading centers where plates are moving apart and new crust is created by magma pushing up from the mantle.

QUESTION: What happens at a divergent boundary?

OUTPUT:
At a divergent boundary, two tectonic plates move away from each other, creating new oceanic crust through the process of convection in the Earth's mantle.
```

Correct, but **paraphrased** ("two tectonic plates … through the process of convection"),
not a verbatim span of the text.
`TTFT_S=0.013 TOTAL_S=0.143 GEN_S=0.130 PROMPT_TOKENS=72 COMPLETION_TOKENS=35 GEN_TPS=268.8`

#### EN-3 — abstention on a question the text does not answer

```
PROMPT[user]: TEXT:
Divergent boundaries occur along spreading centers where plates are moving apart and new crust is created by magma pushing up from the mantle.

QUESTION: What is the boiling point of the Earth's core?

OUTPUT:
The text does not answer this question.
```

**Correct abstention** — this matters for MATRIX's "say what is not covered" rule.
`TTFT_S=0.010 TOTAL_S=0.041 GEN_S=0.031 PROMPT_TOKENS=82 COMPLETION_TOKENS=9 GEN_TPS=293.0`

#### RU-1 — extract propositions VERBATIM from a real Russian paragraph

```
PROMPT[system]: Ты извлекаешь фактические утверждения из текста.
Правила:
1. Каждое утверждение должно быть скопировано ДОСЛОВНО из текста. Не пересказывай, не переводи, не сокращай.
2. Выводи по одному утверждению в строке. Без нумерации, без списков, без комментариев, без вступления.
3. Выводи только те утверждения, которые текст утверждает как факты.
4. Выводи только сами утверждения.
5. Остановись, когда перечислил все.

PROMPT[user]: ТЕКСТ:
Учёные имеют довольно хорошее представление о том, как движутся плиты и как эти движения связаны с землетрясениями. Большая часть движения происходит вдоль узких зон между плитами, где наиболее заметны результаты тектонических сил. Скорость раздвижения Среднеатлантического хребта в среднем составляет около 2,5 сантиметра в год.

УТВЕРЖДЕНИЯ:

OUTPUT:
1. Ученые имеют хорошее представление о движении плит и связанных землетрясениях.
2. Большая часть движения происходит вдоль узких зон между плитами.
3. Результаты тектонических сил наиболее заметны там, где скорость раздвижения Среднеатлантического хребта составляет около 2,5 сантиметра в год.
```

**Russian works semantically but violates the verbatim rule.** It numbered the list, dropped
`ё` (`Учёные` → `Ученые`), rewrote sentence 1, and *merged* sentences 2 and 3 into a
claim the source does not make ("Результаты тектонических сил наиболее заметны там, где
скорость раздвижения … составляет …" — the source says nothing of the sort). Line 2 is
verbatim; lines 1 and 3 are not.
`TTFT_S=0.020 TOTAL_S=0.364 GEN_S=0.344 PROMPT_TOKENS=279 COMPLETION_TOKENS=94 GEN_TPS=273.2`

#### RU-2 — answer a question in Russian, grounded in the text

```
PROMPT[user]: ТЕКСТ:
Дивергентные границы проходят вдоль зон раздвижения, где плиты расходятся, а новая кора создаётся магмой, поднимающейся из мантии.

ВОПРОС: Что происходит на дивергентной границе?

OUTPUT:
На дивергентной границе создается новый материал в результате образования магмы.
```

**Grounded but not verbatim, and it weakened the claim**: the source says *новая кора*
("new crust"); the answer says *новый материал* ("new material") — a different substance.
`TTFT_S=0.016 TOTAL_S=0.090 GEN_S=0.075 PROMPT_TOKENS=117 COMPLETION_TOKENS=21 GEN_TPS=281.7`

#### MIX-1 — English instruction, Russian text

```
PROMPT[system]: You extract factual propositions from a text. Each proposition must be copied VERBATIM from the text. Output one proposition per line, no commentary.
PROMPT[user]: TEXT:
Дивергентные границы проходят вдоль зон раздвижения, где плиты расходятся, а новая кора создаётся магмой, поднимающейся из мантии.

PROPOSITIONS:

OUTPUT:
Дивергентные границы проходят вдоль зон раздвижения.
```

**Truncated**: it emitted only the first clause and dropped the information-bearing
second clause. Cross-lingual instruction-following is the weakest of the six.
`TTFT_S=0.013 TOTAL_S=0.085 GEN_S=0.072 PROMPT_TOKENS=96 COMPLETION_TOKENS=20 GEN_TPS=277.5`

**Summary.** The model is genuinely multilingual and does grounded English and Russian
QA with correct abstention. But it is **not faithful**: in Russian it paraphrases where
it was told to copy, and cross-lingually it truncates. Faithfulness is exactly what an
evidence-grounded store requires, and a paraphrase is not evidence.

### 3.6 A decoding failure worth recording

With plain greedy decoding (`temperature 0`, `top_k 1`, **no** `repeat_penalty`) the
model **degenerated into a repetition loop** on the extraction task — 61 output lines
for chunk 0, only 8 unique, mostly the same sentence repeated, ending in
`finish_reason=length` at the 1400-token cap:

```
A greedy, no penalty
  finish_reason=length completion_tokens=1400 lines=61 unique=8 wall=5.18s
  --- raw head (400 chars) ---
  Divergent boundaries occur along spreading centers where plates are moving apart and new crust is created by magma pushing up from the mantle.
  The size of the Earth has not changed significantly during the past 600 million years, and very likely not since shortly after its formation 4.6 billion years ago.
  There are four types of plate boundaries: Divergent boundaries, Convergent boundaries, Transf
```

`repeat_penalty 1.15` fixed it (`finish_reason=stop`, 5 unique lines, 0.49 s);
`repeat_penalty 1.1 + frequency_penalty 0.3` gave the same output. **All extraction
numbers in §4 use `repeat_penalty 1.15`.** A teacher that needs a repetition penalty to
be usable at all is a property of the teacher, not of the harness.

---

## 4. Extraction comparison against the deterministic baseline

### 4.1 Method

**Baseline.** The repository's own deterministic extractor, executed unmodified:
`matrix-core/src/main/java/io/matrix/knowledge/acquisition/ClaimExtractor.java`, reached
through `DocumentExtractorRegistry` → `TextDocumentExtractor` →
`ClaimExtractor.propose(...)`. `ClaimExtractor.java` was compiled **from source** into
`/tmp/w50java/classes` and placed **ahead of** `matrix-core-1.0.0.jar` on the classpath,
so the measured code is the working tree's code, not a stale jar. The driver lives in
`/tmp`; **nothing in the repository was modified**.

**Ground truth.** The source file itself: a proposition counts as present iff its
whitespace-normalised form is a substring of the whitespace-normalised file. The same
check is applied to both extractors.

**Same document subset.** Both documents of the W50 seed corpus
(`matrix-core/src/main/resources/corpus/plate-tectonics/`): `usgs-this-dynamic-earth.md`
(17 144 B) and `wikipedia-plate-tectonics.md` (58 972 B).

**Model side.** The document is cut into chunks of ≤ 9000 characters at sentence
boundaries (USGS 2 chunks, Wikipedia 7 chunks); each chunk goes to
`/v1/chat/completions` with the verbatim-extraction system prompt of §3.5.

**Recall denominator.** The *candidate universe*: the sentences the repository's own
`ClaimExtractor.sentences()` splitter produces over the document — the units both
extractors choose from. It is emitted by the same driver, so the denominator comes from
the repository's code, not from a hand count.

### 4.2 Result — `usgs-this-dynamic-earth.md`

```
CANDIDATE UNIVERSE (the repo's own sentence splitter)
  candidate sentences total          : 358
  accepted by ClaimExtractor.subjectOf: 92

DETERMINISTIC BASELINE (ClaimExtractor)
  propositions produced              : 92
  present VERBATIM in source document: 92/92 = 100.0%
  RECALL  vs candidate universe (358)  : 92/358 = 25.7%

MODEL-ASSISTED EXTRACTION
  propositions produced (deduplicated): 11
  present VERBATIM in source document: 8/11 = 72.7%
  also in the deterministic set       : 0/11 = 0.0%
  inside the candidate universe      : 0/11 = 0.0%
  RECALL  vs candidate universe (358)  : 0/358 = 0.0%
```

### 4.3 Result — `wikipedia-plate-tectonics.md`

```
CANDIDATE UNIVERSE (the repo's own sentence splitter)
  candidate sentences total          : 431
  accepted by ClaimExtractor.subjectOf: 236

DETERMINISTIC BASELINE (ClaimExtractor)
  propositions produced              : 236
  present VERBATIM in source document: 236/236 = 100.0%
  RECALL  vs candidate universe (431)  : 236/431 = 54.8%

MODEL-ASSISTED EXTRACTION
  propositions produced (deduplicated): 110
  present VERBATIM in source document: 34/110 = 30.9%
  also in the deterministic set       : 18/110 = 16.4%
  inside the candidate universe      : 26/110 = 23.6%
  RECALL  vs candidate universe (431)  : 26/431 = 6.0%

HEAD-TO-HEAD (deduplicated model set vs deterministic set)
  model propositions NOT in baseline : 92
  baseline propositions NOT in model : 218
  Jaccard (|A n B| / |A u B|)        : 18/328 = 5.5%
  F1 (precision & recall vs baseline): precision 18/110 = 16.4% | recall 18/236 = 7.6%
```

### 4.4 The mechanism behind the low overlap — the baseline extracts line fragments

The model scored 0/11 on the USGS document even though 8 of its 11 propositions are
verbatim in the file. The reason is in the repository's own splitter:
`ClaimExtractor.sentences()` splits on `.` `!` `?` **and on `\n`**. The corpus Markdown is
hard-wrapped, so `\n` cuts sentences in half.

```
1. IS THE BASELINE EXTRACTING SENTENCES OR LINE FRAGMENTS?
   ClaimExtractor.sentences() splits on '.' '!' '?' AND '\n'.
   The corpus Markdown is hard-wrapped, so '\n' cuts sentences.
   baseline propositions                       : 92
   ... ending with a sentence terminator       : 22 (23.9%)
   ... ending WITHOUT one (line fragment)      : 70 (76.1%)
   candidate units produced by the splitter    : 358
   ... candidate units that are fragments      : 237 (66.2%)
   longest baseline proposition (chars)        : 79
   longest model proposition (chars)           : 233
```

For the USGS document, **70 of the 92 baseline propositions (76.1%) are line fragments**,
not sentences. The longest baseline proposition is 79 characters — the file is wrapped at
~75 — while the model's longest is 233. The model returns whole logical sentences; the
baseline returns the wrapped lines.

A containment measure, which does not punish the model for merging two fragments:

```
2. RECALL OF THE BASELINE'S PROPOSITIONS BY THE MODEL (containment)   [USGS]
   baseline propositions CONTAINED in some model proposition: 7/92 = 7.6%
   baseline propositions EQUAL to some model proposition  : 0/92 = 0.0%
```
```
2. RECALL OF THE BASELINE'S PROPOSITIONS BY THE MODEL (containment)   [Wikipedia]
   baseline propositions CONTAINED in some model proposition: 45/236 = 19.1%
   baseline propositions EQUAL to some model proposition  : 18/236 = 7.6%
```

This is a finding about the **baseline**, not a defence of the model: the deterministic
extractor's output on hard-wrapped Markdown is not a set of sentences. W50-03 does not
change it — that is out of scope and would be a change to a working acquisition path.

### 4.5 Faithfulness of the model output

What the model produced that was **not** verbatim in the source (Wikipedia, first six):

```
- Plate tectonics is the scientific theory that Earth's lithosphere comprises a number of large tectonic plates, which have been slowly moving since 3–4 billion y
- Where the plates meet, their relative motion determines the type of plate boundary: convergent, divergent, or transform.
- Faults tend to be geologically active with earthquakes, volcanic activity, mountain-building, and oceanic trench formation.
- Tectonic plates are composed of the oceanic lithosphere and the thicker continental lithosphere, each topped by its own kind of crust: oceanic crust (in older t
- The division is based on differences in mechanical properties and in the method for the transfer of heat: the lithosphere is cooler and more rigid, while the as
- Oceanic crust is formed at sea-floor spreading centers, while Continental crust is formed through arc volcanism and accretion of terranes through plate tectonic
```

**76 of 110 model propositions (69.1%) are not verbatim in the source.** These are
paraphrases, not hallucinations of fact — they are semantically close — but a paraphrase
has no location in the document, so it cannot be linked to evidence, and a claim whose
evidence quote does not exist in the source fails the store's own evidence rule.

### 4.6 The numbers, side by side

| Document | Extractor | Produced | Verbatim in source | In candidate universe | Recall vs candidate universe |
|---|---|---:|---:|---:|---:|
| USGS | deterministic | 92 | **92/92 = 100.0%** | 92/92 = 100.0% | **92/358 = 25.7%** |
| USGS | model | 11 | **8/11 = 72.7%** | 0/11 = 0.0% | **0/358 = 0.0%** |
| Wikipedia | deterministic | 236 | **236/236 = 100.0%** | 236/236 = 100.0% | **236/431 = 54.8%** |
| Wikipedia | model | 110 | **34/110 = 30.9%** | 26/110 = 23.6% | **26/431 = 6.0%** |

Head-to-head against the baseline as the reference set (Wikipedia): precision
**18/110 = 16.4%**, recall **18/236 = 7.6%**, Jaccard **18/328 = 5.5%**.

**On both documents the deterministic extractor wins on every measure**: it produces
8–21× more propositions, every one of them is verbatim, and its recall against the
candidate universe is 25.7% / 54.8% against the model's 0.0% / 6.0%.

---

## 5. Teacher invocation accounting

### 5.1 The proposed instrumentation point

A teacher invocation is **one complete prompt → one answer task**, not one forward pass.
The existing `OnnxRuntimeAdapter.inferenceCount` counts *token-level* forward passes
(`greedyNextToken` increments it per token), so it cannot be reused directly — one
teacher call would count as dozens of "inferences" and the number would not mean "how
many times was the teacher consulted".

**Proposed:**

1. **Interface.** Extract the claim-proposal source from `DocumentImporter.java:206`
   (`for (ClaimExtractor.Proposal proposal : ClaimExtractor.propose(document))`) into an
   interface, e.g. `io.matrix.knowledge.acquisition.ProposalSource` with
   `List<ClaimExtractor.Proposal> propose(ExtractedDocument)`. The deterministic
   implementation is the existing `ClaimExtractor` — unchanged, and still the default.
2. **Counter.** `AtomicLong teacherInvocations` on the teacher implementation only,
   incremented **once per `propose(document)` call**, plus `AtomicLong teacherTokens` for
   the tokens spent. Both exposed next to the existing `inferenceCount()`.
3. **Report point.** The counter belongs in `DocumentImporter.ImportResult`, beside
   `claimsProposed` / `claimsAdded`, so the acquisition report of
   `acquisition/AcquisitionCycle` carries it — that report already states what was
   acquired, skipped and entered; this adds the *cost* of the acquisition.
4. **Kill switch.** The teacher must be off by default and reachable only through an
   explicit opt-in, so that "0 invocations" is a property of the default path rather
   than a convention.

### 5.2 Measured count during the deterministic acquisition path

**Measured: 0.** The llama-server was left running with its request counter visible and
the repository's own deterministic extraction was run against it.

```console
$ launchcount() { grep -c "processing task" /tmp/w50/llama-server.log; }

=== counter sanity: a KNOWN model call must increment it ===
launched_requests before=24 after=25 delta=1

=== now run the DETERMINISTIC acquisition path (repo's own code) ===
launched_requests before=25 after=25
TEACHER_INVOCATIONS_DURING_DETERMINISTIC_ACQUISITION_PATH=0
```

The counter was validated first with a known model call (delta = 1), so the zero is a
measurement and not a broken counter. The cost of the deterministic path, for the record:

```console
$ /usr/bin/time -v … W50ExtractionDriver usgs-this-dynamic-earth.md
	Elapsed (wall clock) time (h:mm:ss or m:ss): 0:00.04
	Maximum resident set size (kbytes): 57956

$ /usr/bin/time -v … W50ExtractionDriver wikipedia-plate-tectonics.md
	Elapsed (wall clock) time (h:mm:ss or m:ss): 0:00.05
	Maximum resident set size (kbytes): 61088
```

**0.04–0.05 s and ~58–61 MB RSS** for the whole deterministic extraction, against a
teacher round trip of **0.31–5.52 s** per chunk and **1.41 GiB** of resident memory. The
deterministic path is three to five orders of magnitude cheaper.

### 5.3 Why the final answering path must not call it

The answering path is `KnowledgeRetriever.answer(...)` → `new Answer(...)`
(`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRetriever.java:585`). It:

- renders stored `Representation`s at the requested abstraction level, and says plainly
  when a concept has claims but no authored representation yet
  (`statements.add(claims.get(0).proposition())`, line 515);
- applies a rule only when the workspace states it (`RuleApplication.apply`, line 533),
  and says so in the limitations;
- computes the support score from the claims' **own stored** `confidence` field and the
  retrieval coverage (lines 553–555) — a number the author wrote, not one a model
  produced;
- lists what is **not** covered (`unmatchedStems`) as limitations.

Three reasons a teacher must not be on this path:

1. **The answer would no longer be the workspace's.** The answer is currently a
   rendering of what the workspace holds, with a provenance chain back to a source and a
   location. A model-written answer has no such chain.
2. **`CONSTITUTION.md` line 15: *"LLM в путях решений остаётся ЗАПРЕЩЁН"*** — LLM calls
   in decision paths remain forbidden. The answering path is a decision path.
3. **Rule application and abstention would become unverifiable.** The store's value is
   that it can say "this workspace has no account of that concept" and "the sources
   disagree about X". A model in the loop makes both statements unverifiable — and the
   EN-3 probe shows the model *will* abstain when instructed to, but a model that
   abstains on command is a compliance mechanism, not an epistemic one.

**The teacher, if it is ever offered, belongs only in the acquisition path, behind the
adapter, off by default, and its output must pass the same verbatim-and-located
evidence rule the deterministic path already satisfies at 100%.**

---

## 6. Limitations and honest gaps

1. **One model, one size, one quantization.** Qwen2.5-1.5B-Instruct at Q4_K_M only. A
   7B–14B model would fit the 12 GiB VRAM and might behave differently; this wave did
   not measure that. No claim is made about "local models" in general.
2. **One prompt template.** The extraction prompt is one hand-written system message. A
   different template — few-shot, a schema, a JSON mode — could change the verbatim rate
   materially. Not swept.
3. **The repetition penalty is load-bearing.** With plain greedy decoding the model
   degenerates. Every number in §4 depends on `repeat_penalty 1.15`. A production
   adapter would need that setting to be non-optional and would need its own degeneracy
   detector.
4. **The comparison is not apples-to-apples by construction.** The baseline extracts
   *line fragments* from hard-wrapped Markdown; the model extracts *sentences*. The
   exact-match and candidate-universe numbers penalise the model for that difference
   (§4.4). The containment numbers (7/92, 45/236) are the fairer reading, and they are
   still far below the baseline's coverage. **A fair re-run would need the baseline's
   output normalised (the text re-wrapped) — that changes the repository's extractor and
   was out of scope here.**
5. **Two documents, one domain, English only.** Both corpus documents are English
   plate-tectonics text from one wave's seed corpus. No Russian corpus document exists
   in the repository, so the Russian measurements in §3.5 are prompt-level only and the
   extraction comparison was **not** run on Russian text.
6. **The corpus-licence interaction is untouched.** The seed corpus is USGS
   public-domain plus Wikipedia CC BY-SA 4.0. Model output is not a corpus entry; if any
   model-derived text were ever shipped, the licence interaction would have to be
   answered separately. W50-03 does not answer it.
7. **The ONNX path was not benchmarked in this wave.** `models/onnx/qwen05b/model.onnx`
   (Qwen2.5-0.5B-Instruct) exists and the application can load it, but the measurement
   here is the llama.cpp path. `hardware-probe.sh` reports that MATRIX's own Java
   configuration **fails** `addCUDA()` without `LD_LIBRARY_PATH`, i.e. MATRIX does not
   currently use the GPU through its own ONNX path. **Not measured:** the ONNX path's
   Russian behaviour.
8. **Peak VRAM sampling is coarse.** `nvidia-smi` was sampled every 0.1 s, so a transient
   spike between samples could be missed. The reported 2685 MiB is a lower bound on the
   true peak.
9. **Load time was measured with a warm page cache.** 0.839 s is the warm figure; the
   cold figure (first read from NVMe) was **not measured**. For a persistent service the
   cold number is the one that matters.
10. **The teacher was never wired into the application.** No Java was changed, so no
    end-to-end run of `DocumentImporter` with a teacher was performed. The §5.2
    experiment establishes that the deterministic chain issues zero model calls — not
    that a hypothetical teacher would be free of them.
11. **`llama-bench` figures are synthetic.** `pp512`/`tg128` use a fixed synthetic
    prompt, not corpus text. The 252–297 tok/s figures from the extraction run are the
    corpus-text numbers.
12. **No third-party evaluation suite was run.** No lm-evaluation-harness, no
    multilingual benchmark. The language findings in §3.5 are six hand-written probes
    and nothing more.
13. **Two `*.onnx.data` files were hashed in a separate pass** — the `find` pattern used
    for §2.2 matched `*.onnx` but not `*.onnx.data` for the two DistilBERT paths, so
    their sha256 was taken afterwards and is recorded in the §2.1 table.

---

## 7. Verdict for W50

> **The teacher does not improve extraction. The deterministic path stays the critical
> path.**

| Measure | Deterministic baseline | Teacher (Qwen2.5-1.5B-Instruct Q4_K_M) | Winner |
|---|---|---|---|
| Propositions from the USGS document | **92** | 11 | baseline (8.4×) |
| Propositions from the Wikipedia document | **236** | 110 | baseline (2.1×) |
| Propositions verbatim in the source (USGS) | **92/92 = 100.0%** | 8/11 = 72.7% | baseline |
| Propositions verbatim in the source (Wikipedia) | **236/236 = 100.0%** | 34/110 = 30.9% | baseline |
| Recall vs the candidate universe (USGS) | **92/358 = 25.7%** | 0/358 = 0.0% | baseline |
| Recall vs the candidate universe (Wikipedia) | **236/431 = 54.8%** | 26/431 = 6.0% | baseline |
| Precision vs the baseline (Wikipedia) | — | 18/110 = 16.4% | — |
| Recall vs the baseline (Wikipedia) | — | 18/236 = 7.6% | — |
| Cost of the acquisition pass | **0.04–0.05 s, 58–61 MB RSS** | 0.31–5.52 s/chunk, 1.41 GiB RSS, up to 2685 MiB VRAM | baseline |
| Teacher invocations in the deterministic path | **0** (measured) | — | — |

The hardware is not the obstacle: a 1.5B model at 307 tok/s on the RTX 5070 Ti, 1.41 GiB
of RAM, 2685 MiB of VRAM and 0.84 s to load is a perfectly usable local teacher. **The
model is the obstacle.** It is not faithful — 69.1% of its Wikipedia propositions are
paraphrases with no quotable span — and on Russian, where it was asked to copy verbatim,
it rewrote the text and merged two unrelated claims into one. A knowledge store whose
evidence rule is "the claim carries an excerpt and a location in the source" cannot use
that output without re-deriving the excerpt, which is the deterministic extractor's job
and which the model did worse.

**What is nevertheless worth keeping from this wave:**

1. The hardware is capable — the machine can host a local model, and the numbers for
   that are now on the record instead of assumed.
2. A model CAN be offered behind an adapter: it abstains correctly (EN-3), it answers
   grounded English and Russian questions, and it runs at interactive speed.
3. The deterministic extractor's own output needs attention — **70/92 (76.1%) of the
   USGS propositions are line fragments, not sentences**, because the splitter cuts on
   `\n`. That is a larger, cheaper win than anything a teacher could deliver, and it is
   a separate change to a path this wave did not touch.

**Recommended disposition:** the teacher stays **off**, the deterministic path stays the
critical path, and the adapter seam proposed in §5.1 is built **without** a teacher
implementation behind it, so that the counter, the cost accounting and the kill switch
exist before any model is plugged in. If a teacher is ever offered, it is offered as an
*additional candidate source* whose proposals must pass the same verbatim-and-located
evidence check the baseline passes at 100% — and that check, applied to the model's
output at 30.9%, is what currently keeps it out.

---

*Measurement artifacts, all outside the repository: `~/.cache/w50/gguf/` (GGUF +
LICENSE + model card), `~/.cache/w50/llamacpp/` (llama.cpp CPU + CUDA + CUDA runtime
bundles), `/tmp/w50/` (harness scripts and raw logs), `/tmp/w50java/` (the
compiled-from-source extraction driver). No repository file other than this one was
created or modified; no commit was made. The `llama-server` this wave started on
127.0.0.1:8099 was **stopped at the end of the wave** (VRAM back to 14 MiB); it restarts
from the cached binary with the command in §3.3.*
