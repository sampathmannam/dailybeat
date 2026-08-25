# DailyBeat â€” Build Plan (Local-Model-Only, End-to-End)

> Hand this document to an LLM agent. The agent should be able to execute it start to finish and produce an installable APK. Every phase has a single, named verification check. If a check fails, the agent fixes it before moving on.

---

## 0. Project facts the executing agent MUST honor

**User:** Sampath M, IPS officer. Building a personal R&D product. No department support. Privacy is non-negotiable.

**Hardware (verified 2026-08-25):**
- CPU: AMD Ryzen 7 7435HS (16 cores)
- RAM: 16 GB (~4 GB free at idle; Chrome eats the rest)
- GPU: NVIDIA RTX 2050, **4 GB VRAM** (the absolute ceiling)
- Disk: 123 GB free on C:
- OS: Windows 11 24H2, **PowerShell only** (no bash, no `&&`, no `head/tail/grep`)
- Python: 3.11.9 at `C:\Users\Sampath\AppData\Local\Programs\Python\Python311\python.exe`
- CUDA: available (driver 566.07)
- Internet: available, but the product must not depend on it at runtime

**Already installed (verified):**
- Ollama desktop app (binary not on PATH; lives under `C:\Users\Sampath\AppData\Roaming\` or `C:\Users\Sampath\AppData\Local\Programs\Ollama\`)
- `.ollama/` config dir with ed25519 key
- `transformers 4.44.2`, `sentence-transformers 3.0.1`, `anthropic 0.109.2`, `langchain 1.3.9` in venv
- `.unsloth/`, `.triton/`, `.runpod/` config dirs (Unsloth is set up)
- GitHub CLI (`gh`), `winget` available
- 21 repos cloned at `C:\Users\Sampath\github\` (MindAnchor is a Kotlin Android app â€” the closest reference for this build)

**Available memory model targets (verified 2026 benchmarks):**
- Qwen2.5-1.5B-Instruct: ~1.1 GB Q4, **best fit for this product**
- Qwen2.5-3B-Instruct: ~2.0 GB Q4, alternative if 1.5B too dumb
- Phi-4-mini: ~2.5 GB Q4, third option
- Llama-3.2-3B: ~2.0 GB Q4, alternative
- DO NOT pick 7B+ â€” they will not fit the 4 GB VRAM ceiling

**Locked tech stack (do not deviate without explicit user approval):**
- Model: Qwen2.5-1.5B-Instruct as base, fine-tuned with QLoRA
- LLM runtime on Android: **MediaPipe LLM Inference** (Kotlin SDK, supports GGUF, Google-supported)
- STT on Android: **whisper.cpp** via JNI (tiny model, multilingual)
- UI: **Jetpack Compose** (matches MindAnchor)
- Local DB: **Room**
- Background: **Foreground service + WorkManager**
- GPS: **Fused Location Provider**
- PDF: **Android PdfDocument** (no extra dep)
- Build: **Gradle Kotlin DSL** in Android Studio
- Min SDK: 26 (Android 8.0, covers ~95% devices in India)
- Target SDK: 34

**Locked name:** **DailyBeat** (police "beat" + daily diary). Domain-agnostic, exportable, friendly.

---

## 1. Project layout (create these first, before any code)

```
C:\Users\Sampath\github\dailybeat\               <-- the cloned GitHub repo
â”œâ”€â”€ PLAN.md                          # this file
â”œâ”€â”€ README.md                        # one-page overview, written last
â”œâ”€â”€ pyproject.toml                   # Python project config (dev/train/eval extras)
â”œâ”€â”€ .aider.conf.yml                  # aider pinned to ollama_chat/qwen2.5:1.5b
â”œâ”€â”€ .gitignore                       # ignores .gguf, .venv, build outputs, etc.
â”œâ”€â”€ scripts\                         # Python training/eval/utility scripts
â”œâ”€â”€ data\                            # diary JSONLs (gitignored â€” sensitive)
â”œâ”€â”€ android\                         # the Android Studio project
â”‚   â”œâ”€â”€ build.gradle.kts             # root
â”‚   â”œâ”€â”€ settings.gradle.kts
â”‚   â”œâ”€â”€ gradle.properties
â”‚   â”œâ”€â”€ gradle\                      # wrapper
â”‚   â”œâ”€â”€ gradlew, gradlew.bat
â”‚   â””â”€â”€ app\
â”‚       â”œâ”€â”€ build.gradle.kts
â”‚       â”œâ”€â”€ proguard-rules.pro
â”‚       â””â”€â”€ src\main\
â”‚           â”œâ”€â”€ AndroidManifest.xml
â”‚           â”œâ”€â”€ assets\
â”‚           â”‚   â””â”€â”€ dailybeat-q4_k_m.gguf    # copied in Phase 11
â”‚           â”œâ”€â”€ java\com\dailybeat\app\
â”‚           â”‚   â”œâ”€â”€ DailyBeatApp.kt           # Application class
â”‚           â”‚   â”œâ”€â”€ MainActivity.kt
â”‚           â”‚   â”œâ”€â”€ ui\                       # Compose screens
â”‚           â”‚   â”‚   â”œâ”€â”€ theme\
â”‚           â”‚   â”‚   â”œâ”€â”€ home\                 # event list, today's dairy
â”‚           â”‚   â”‚   â”œâ”€â”€ capture\              # voice entry, manual entry
â”‚           â”‚   â”‚   â””â”€â”€ settings\             # geofences, contacts, format
â”‚           â”‚   â”œâ”€â”€ data\
â”‚           â”‚   â”‚   â”œâ”€â”€ db\                   # Room
â”‚           â”‚   â”‚   â”œâ”€â”€ repo\                 # repository
â”‚           â”‚   â”‚   â””â”€â”€ model\                # data classes
â”‚           â”‚   â”œâ”€â”€ capture\                  # voice, GPS, call log
â”‚           â”‚   â”‚   â”œâ”€â”€ VoiceCaptureService.kt
â”‚           â”‚   â”‚   â”œâ”€â”€ WhisperBridge.kt      # JNI
â”‚           â”‚   â”‚   â”œâ”€â”€ LocationService.kt
â”‚           â”‚   â”‚   â””â”€â”€ CallLogObserver.kt
â”‚           â”‚   â”œâ”€â”€ llm\
â”‚           â”‚   â”‚   â”œâ”€â”€ LlmEngine.kt          # MediaPipe wrapper
â”‚           â”‚   â”‚   â”œâ”€â”€ DairyGenerator.kt
â”‚           â”‚   â”‚   â””â”€â”€ EventExtractor.kt     # structured event from raw voice
â”‚           â”‚   â”œâ”€â”€ export\
â”‚           â”‚   â”‚   â””â”€â”€ PdfExporter.kt
â”‚           â”‚   â””â”€â”€ util\
â”‚           â””â”€â”€ res\                          # icons, strings, themes
```

The agent creates every directory with `New-Item -ItemType Directory -Force` (PowerShell) before writing files.

---

## 2. Phase 0 â€” Environment verification (30 min)

**Goal:** install Ollama, pull the two models we need, confirm every tool actually works on this box before writing any code.

**Steps:**

```powershell
# 0.1 â€” install Ollama (NOT yet installed; the .ollama folder is just a config dir
# from a prior partial install. Re-run the official installer to get the real binary.)
irm https://ollama.com/install.ps1 | iex

# This adds ollama to PATH, installs the server, and registers the Windows service.
# Verify:
ollama --version
# expected: ollama version 0.x.x

# 0.2 â€” start the server (auto-starts on login; for now start it manually)
ollama serve
# leave that shell open. Or run as a background job:
#   Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden

# 0.3 â€” verify the server is responding
curl http://127.0.0.1:11434/api/tags
# expected: JSON with "models": []   (no models yet)

# 0.4 â€” pull the two models we need
ollama pull nomic-embed-text        # 274 MB, used in eval
ollama pull qwen2.5:1.5b-instruct   # 1.1 GB, our base

# 0.5 â€” verify Python deps
py -3.11 -c "import torch; print('torch', torch.__version__, 'cuda', torch.cuda.is_available())"
py -3.11 -c "import transformers; print('transformers', transformers.__version__)"

# 0.6 â€” verify CUDA visible to PyTorch
py -3.11 -c "import torch; print(torch.cuda.get_device_name(0))"
# expected: NVIDIA GeForce RTX 2050

# 0.7 â€” verify disk space
Get-PSDrive C | Select-Object Used, Free
# expected: at least 30 GB free

# 0.8 â€” verify aider (already installed at v0.86.2+)
aider --version

# 0.9 â€” install the Python training toolchain (one-time, ~5 min)
cd C:\Users\Sampath\github\dailybeat
py -3.11 -m pip install -e ".[dev,train,eval]"
```

**Verification check (must pass before Phase 1):**
```powershell
ollama list
# expected: nomic-embed-text, qwen2.5:1.5b-instruct present
py -3.11 -c "import torch; assert torch.cuda.is_available(); print('OK')"
aider --version
# expected: aider 0.86.x or newer
```

**Output:** Ollama running on `127.0.0.1:11434`, two models downloaded, CUDA working, aider working, Python train/eval deps installed.

**Time:** 30-60 min including Ollama install + model download.

---

## 3. Phase 1 â€” Gather training data from past diaries (1 evening)

**Goal:** produce `diary_train.jsonl` with at least 30 (input, output) pairs. Each pair is one day's events â†’ final submitted dairy.

**Steps:**

The user has years of past dairy entries. They live in:
- Local Word/PDF files
- Email drafts sent to seniors
- Handwritten notes that need to be typed up
- The official e-dairy system if their district uses one

The agent's job is to:
1. Ask the user where the past diaries live (max 2 questions; do not over-ask)
2. If files exist locally, copy them to `dailybeat/raw_diaries/`
3. The agent writes a small script `scripts/parse_diaries.py` that:
   - Reads each file
   - Splits on day boundaries (date headers)
   - For each day, extracts (a) the raw events mentioned and (b) the final dairy text
   - Outputs `diary_train.jsonl` with one JSON object per line:
     ```json
     {"events": "raw event log for the day", "dairy": "final submitted dairy text"}
     ```
4. The user reviews 10 random samples and approves

**If the user has fewer than 30 days of past dairy:**
- Pad with 10-20 synthetic days the user writes manually (5 min each)
- Each synthetic day follows the same format: "events happened" â†’ "dairy text"
- Total: aim for 30+ real days, OK to top up with synthetic

**Verification check:**
```powershell
Get-Content diary_train.jsonl | Measure-Object -Line
# expected: >= 30 lines

# inspect first 2 samples, confirm they look right
Get-Content diary_train.jsonl -TotalCount 2
```

**Output:** `diary_train.jsonl` with â‰¥30 quality examples.

**Time:** 2-3 hours including data gathering.

---

## 4. Phase 2 â€” Held-out eval set (1 hour)

**Goal:** `diary_eval.jsonl` with 10 (events, dairy) pairs the model NEVER sees during training. This is the bar.

**Steps:**

1. From the diaries NOT used in training, set aside exactly 10 days as held-out
2. Same JSONL format as `diary_train.jsonl`
3. Manually grade each (events, dairy) pair:
   - Score 1-5: how good is the existing dairy as a target?
   - Drop any pair you would score < 3
4. Save to `diary_eval.jsonl`

**Verification check:**
```powershell
Get-Content diary_eval.jsonl | Measure-Object -Line
# expected: >= 10 lines
# all pairs must be from days not in diary_train.jsonl
```

**Output:** `diary_eval.jsonl` with â‰¥10 hand-picked, high-quality target diaries.

**Time:** 1 hour.

---

## 5. Phase 3 â€” Prompt engineering with base model (1 evening)

**Goal:** discover how good Qwen2.5-1.5B is at this task BEFORE fine-tuning. If the base is already great, fine-tuning is optional. If it's bad, you know exactly what failure modes to fix.

**Steps:**

1. Write `scripts/eval_base.py`:
   - For each row in `diary_eval.jsonl`, send the `events` to the base model via Ollama
   - Use this prompt template (locked):
     ```
     You are an Indian Police Service officer writing your official daily diary.
     Convert the following raw events from the day into a formal dairy entry
     in standard IPS dairy format. Use only the information given. Do not invent
     details. Use present tense for completed actions. Keep it concise.
     
     EVENTS:
     {events}
     
     DAIRY:
     ```
   - Save outputs to `eval_base_outputs.jsonl`

2. Run:
   ```powershell
   py -3.11 scripts/eval_base.py
   ```

3. The user reviews 10 outputs side-by-side with their originals. For each, mark:
   - **PASS** if the output is â‰¥80% as good as the human dairy
   - **FAIL** if it invents, misses key events, or changes facts

4. Count passes. Record in `eval_results.json`:
   ```json
   {"phase": "base", "passes": N, "total": 10, "pass_rate": 0.NN}
   ```

**Decision gate:**
- pass_rate â‰¥ 0.7: the base model is already strong. Light fine-tune only. Move to Phase 6.
- pass_rate 0.4-0.7: full QLoRA fine-tune needed. Move to Phase 6.
- pass_rate < 0.4: switch to Qwen2.5-3B base. Re-run this phase. If still <0.4, re-check prompt.

**Verification check:** `eval_results.json` exists, pass_rate recorded, decision documented in a comment at top of file.

**Output:** `eval_base_outputs.jsonl`, `eval_results.json`.

**Time:** 2 hours.

---

## 6. Phase 4 â€” Fine-tune with QLoRA on RTX 2050 (1 evening)

**Goal:** produce a LoRA adapter that makes Qwen2.5-1.5B write dairy in the user's style.

**Steps:**

1. Install Unsloth (skip if already installed; verify first):
   ```powershell
   py -3.11 -m pip install --upgrade unsloth
   py -3.11 -c "import unsloth; print('unsloth', unsloth.__version__)"
   ```

2. Write `scripts/train_dairy.py`:
   ```python
   from unsloth import FastLanguageModel
   from trl import SFTTrainer, SFTConfig
   from datasets import load_dataset
   import torch

   # 4-bit base, fits in 4 GB VRAM
   model, tokenizer = FastLanguageModel.from_pretrained(
       model_name="unsloth/Qwen2.5-1.5B-Instruct",
       max_seq_length=2048,
       load_in_4bit=True,
       dtype=None,
   )

   model = FastLanguageModel.get_peft_model(
       model,
       r=16,                  # rank
       lora_alpha=32,         # 2x rank
       lora_dropout=0.05,
       target_modules=[
           "q_proj", "k_proj", "v_proj", "o_proj",
           "gate_proj", "up_proj", "down_proj",
       ],
       bias="none",
       use_gradient_checkpointing="unsloth",
   )

   # Build dataset: events -> dairy, ChatML format
   def to_chat(example):
       events = example["events"]
       dairy = example["dairy"]
       return {
           "text": (
               f"<|im_start|>system\nYou are an Indian Police Service "
               f"officer writing your official daily diary. Convert raw "
               f"events into formal IPS dairy format. Use only the given "
               f"information. Do not invent.<|im_end|>\n"
               f"<|im_start|>user\n{events}<|im_end|>\n"
               f"<|im_start|>assistant\n{dairy}<|im_end|>"
           )
       }

   ds = load_dataset("json", data_files="diary_train.jsonl", split="train")
   ds = ds.map(to_chat)

   trainer = SFTTrainer(
       model=model,
       tokenizer=tokenizer,
       train_dataset=ds,
       args=SFTConfig(
           per_device_train_batch_size=2,
           gradient_accumulation_steps=4,    # effective batch 8
           num_train_epochs=3,
           learning_rate=2e-4,
           fp16=True,                       # RTX 2050 supports fp16
           logging_steps=5,
           output_dir="outputs/dairy_lora",
           save_strategy="no",
           warmup_steps=10,
           max_seq_length=2048,
       ),
   )

   trainer.train()
   model.save_pretrained_gguf(
       "dailybeat-merged",
       tokenizer,
       quantization_method="q4_k_m",
   )
   print("DONE: model saved to dailybeat-merged/")
   ```

3. Run from the project root:
   ```powershell
   cd C:\Users\Sampath\.minimax-agent\projects\dailybeat
   py -3.11 scripts/train_dairy.py
   ```

4. Expected: ~20-30 min training, ~2 GB peak VRAM, ends with `dailybeat-merged/dailybeat-q4_k_m.gguf`

**Verification check:**
```powershell
Get-ChildItem dailybeat-merged -Recurse -Filter *.gguf
# expected: 1 file, ~1.0-1.2 GB
```

**Output:** `dailybeat-merged/dailybeat-q4_k_m.gguf`.

**Time:** 30-60 min wall clock.

---

## 7. Phase 5 â€” Re-eval fine-tuned model (30 min)

**Goal:** prove the fine-tune actually improved quality on the held-out set.

**Steps:**

1. Register the GGUF with Ollama:
   ```
   ollama create dailybeat -f - <<EOF
   FROM ./dailybeat-merged/dailybeat-q4_k_m.gguf
   PARAMETER temperature 0.1
   PARAMETER num_ctx 2048
   SYSTEM "You are an Indian Police Service officer writing your official daily diary. Convert raw events into formal IPS dairy format. Use only the given information. Do not invent."
   EOF
   ```

2. Re-run `scripts/eval_base.py` against the new model name `dailybeat` (small change, save as `scripts/eval_finetuned.py`)

3. Update `eval_results.json`:
   ```json
   {
     "base": {"passes": N1, "total": 10, "pass_rate": 0.NN1},
     "finetuned": {"passes": N2, "total": 10, "pass_rate": 0.NN2}
   }
   ```

**Decision gate:**
- finetuned pass_rate > base pass_rate: ship it. Move to Phase 6.
- equal or worse: your training data is too small or too noisy. Add 20 more examples, retrain. Repeat.
- way worse (overfitting): reduce epochs to 1, retrain.

**Verification check:** `eval_results.json` shows finetuned > base on held-out set.

**Output:** updated `eval_results.json`.

**Time:** 30 min.

---

## 8. Phase 6 â€” Android project skeleton (1 evening)

**Goal:** an Android Studio project that builds, installs, and shows a "Hello DailyBeat" screen.

**Steps:**

The agent does NOT need to open Android Studio interactively. Use the Android command-line build.

1. Create the project structure (see layout in Section 1) by hand, file by file
2. Key files to write:

   **`android/build.gradle.kts`:**
   ```kotlin
   plugins {
       id("com.android.application") version "8.5.0" apply false
       id("org.jetbrains.kotlin.android") version "1.9.24" apply false
   }
   ```

   **`android/settings.gradle.kts`:**
   ```kotlin
   pluginManagement {
       repositories {
           google()
           mavenCentral()
           gradlePluginPortal()
       }
   }
   dependencyResolutionManagement {
       repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
       repositories { google(); mavenCentral() }
   }
   rootProject.name = "DailyBeat"
   include(":app")
   ```

   **`android/app/build.gradle.kts`:**
   ```kotlin
   plugins {
       id("com.android.application")
       id("org.jetbrains.kotlin.android")
   }
   android {
       namespace = "com.dailybeat.app"
       compileSdk = 34
       defaultConfig {
           applicationId = "com.dailybeat.app"
           minSdk = 26
           targetSdk = 34
           versionCode = 1
           versionName = "0.1.0"
       }
       buildFeatures { compose = true }
       composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_17
           targetCompatibility = JavaVersion.VERSION_17
       }
       kotlinOptions { jvmTarget = "17" }
       packaging {
           resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
       }
   }
   dependencies {
       implementation("androidx.core:core-ktx:1.13.1")
       implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
       implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
       implementation("androidx.activity:activity-compose:1.9.1")
       implementation(platform("androidx.compose:compose-bom:2024.06.00"))
       implementation("androidx.compose.ui:ui")
       implementation("androidx.compose.material3:material3")
       implementation("androidx.room:room-runtime:2.6.1")
       implementation("androidx.room:room-ktx:2.6.1")
       annotationProcessor("androidx.room:room-compiler:2.6.1")
       kapt("androidx.room:room-compiler:2.6.1")
       implementation("com.google.mediapipe:tasks-genai:0.10.14")
       implementation("com.google.android.gms:play-services-location:21.3.0")
   }
   ```

   **`android/app/src/main/AndroidManifest.xml`:**
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <manifest xmlns:android="http://schemas.android.com/apk/res/android">
       <uses-permission android:name="android.permission.RECORD_AUDIO" />
       <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
       <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
       <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
       <uses-permission android:name="android.permission.READ_CALL_LOG" />
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
       <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
       <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

       <application
           android:name=".DailyBeatApp"
           android:label="DailyBeat"
           android:icon="@mipmap/ic_launcher"
           android:roundIcon="@mipmap/ic_launcher"
           android:theme="@style/Theme.DailyBeat"
           android:allowBackup="false"
           android:supportsRtl="true">

           <activity
               android:name=".MainActivity"
               android:exported="true"
               android:theme="@style/Theme.DailyBeat">
               <intent-filter>
                   <action android:name="android.intent.action.MAIN" />
                   <category android:name="android.intent.category.LAUNCHER" />
               </intent-filter>
           </activity>
       </application>
   </manifest>
   ```

   **`android/app/src/main/java/com/dailybeat/app/MainActivity.kt`:**
   ```kotlin
   package com.dailybeat.app
   import android.os.Bundle
   import androidx.activity.ComponentActivity
   import androidx.activity.compose.setContent
   import androidx.compose.foundation.layout.*
   import androidx.compose.material3.*
   import androidx.compose.runtime.*
   import androidx.compose.ui.Modifier
   import androidx.compose.ui.unit.dp

   class MainActivity : ComponentActivity() {
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           setContent {
               MaterialTheme {
                   Surface(Modifier.fillMaxSize()) {
                       Column(Modifier.padding(16.dp)) {
                           Text("DailyBeat", style = MaterialTheme.typography.headlineMedium)
                           Spacer(Modifier.height(8.dp))
                           Text("Local dairy writer, fully offline.")
                       }
                   }
               }
           }
       }
   }
   ```

3. Build the APK from CLI:
   ```powershell
   cd C:\Users\Sampath\.minimax-agent\projects\dailybeat\android
   .\gradlew.bat assembleDebug
   ```
   Expected output: `app/build/outputs/apk/debug/app-debug.apk`

**Verification check:**
```powershell
Test-Path android\app\build\outputs\apk\debug\app-debug.apk
# expected: True
Get-Item android\app\build\outputs\apk\debug\app-debug.apk | Select-Object Length
# expected: ~5-10 MB at this stage
```

**Output:** `app-debug.apk` that installs and shows "DailyBeat / Local dairy writer, fully offline."

**Time:** 1-2 hours including Gradle download.

---

## 9. Phase 7 â€” LLM engine wrapper (1 evening)

**Goal:** `LlmEngine.kt` loads the GGUF and exposes `generate(prompt): String`. UI can call it.

**Steps:**

1. Copy the GGUF into assets:
   ```powershell
   Copy-Item dailybeat-merged\dailybeat-q4_k_m.gguf android\app\src\main\assets\dailybeat-q4_k_m.gguf
   Get-Item android\app\src\main\assets\dailybeat-q4_k_m.gguf | Select-Object Length
   # expected: ~1.0-1.2 GB
   ```

2. Add to `app/build.gradle.kts` under `android`:
   ```kotlin
   android {
       ...
       androidResources {
           noCompress += listOf("gguf")
       }
   }
   ```

3. Write `LlmEngine.kt`:
   ```kotlin
   package com.dailybeat.app.llm

   import android.content.Context
   import com.google.mediapipe.tasks.genai.llminference.LlmInference
   import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
   import kotlinx.coroutines.Dispatchers
   import kotlinx.coroutines.withContext

   class LlmEngine(private val ctx: Context) {
       private val llm: LlmInference by lazy {
           LlmInference.createFromOptions(
               ctx,
               LlmInferenceOptions.builder()
                   .setModelPath(ctx.filesDir.absolutePath + "/dailybeat-q4_k_m.gguf")
                   .setMaxTokens(1024)
                   .setTemperature(0.1f)
                   .setTopK(40)
                   .build()
           )
       }

       suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
           // On first run, copy the asset to internal storage so MediaPipe can mmap it
           copyAssetIfNeeded()
           llm.generateResponse(prompt)
       }

       private fun copyAssetIfNeeded() {
           val out = java.io.File(ctx.filesDir, "dailybeat-q4_k_m.gguf")
           if (!out.exists() || out.length() == 0L) {
               ctx.assets.open("dailybeat-q4_k_m.gguf").use { input ->
                   out.outputStream().use { input.copyTo(it) }
               }
           }
       }
   }
   ```

4. Add a minimal UI in `MainActivity.kt` to test:
   - TextField for input
   - Button "Generate"
   - Below: spinner while generating, then output text
   - Use `rememberCoroutineScope` + `LaunchedEffect`

5. Build and install:
   ```powershell
   cd android
   .\gradlew.bat assembleDebug
   .\gradlew.bat installDebug      # if a device/emulator is connected
   ```

**Verification check (manual, on device):**
- Open DailyBeat
- Type: "Generate dairy from: 1140 Market Beat met IO Rajan inspected chain snatching FIR 247/26"
- Tap Generate
- Within 5-10 seconds, see a properly formatted dairy line
- Format should match the user's training data style

**Output:** working LLM inference on device.

**Time:** 1 evening.

---

## 10. Phase 8 â€” Room database for events (1 evening)

**Goal:** persist events of the day so the diary generator can use them.

**Steps:**

1. Define entities in `data/db/Entities.kt`:
   ```kotlin
   @Entity(tableName = "events")
   data class Event(
       @PrimaryKey(autoGenerate = true) val id: Long = 0,
       val timestamp: Long,             // epoch ms
       val type: String,               // "voice" | "manual" | "gps" | "call" | "photo" | "calendar"
       val rawText: String,            // original voice transcript or description
       val placeName: String? = null,  // geofence-resolved place
       val latitude: Double? = null,
       val longitude: Double? = null,
       val peopleMentioned: String? = null, // comma-separated
       val caseNumbers: String? = null,     // comma-separated
       val sourceId: String? = null,         // external ref (call log id, photo uri, etc.)
   )

   @Entity(tableName = "places")
   data class Place(
       @PrimaryKey val id: Long = 0,
       val name: String,
       val latitude: Double,
       val longitude: Double,
       val radiusM: Int = 100,
   )
   ```

2. DAO + Database:
   ```kotlin
   @Dao
   interface EventDao {
       @Query("SELECT * FROM events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
       suspend fun eventsForDay(start: Long, end: Long): List<Event>

       @Insert suspend fun insert(event: Event): Long
       @Update suspend fun update(event: Event)
       @Delete suspend fun delete(event: Event)
   }

   @Database(entities = [Event::class, Place::class], version = 1)
   abstract class DailyBeatDb : RoomDatabase() {
       abstract fun events(): EventDao
   }
   ```

3. App-level singleton in `DailyBeatApp.kt`:
   ```kotlin
   class DailyBeatApp : Application() {
       val db by lazy { Room.databaseBuilder(this, DailyBeatDb::class.java, "dailybeat.db").build() }
       val llm by lazy { LlmEngine(this) }
   }
   ```

4. UI: a simple "Add event manually" screen with a TextField, save button, event list for today.

**Verification check:**
- Add 3 events manually through UI
- Kill app, reopen
- Events still there
- Run a SQL query via `adb shell` (optional): `sqlite3 /data/data/com.dailybeat.app/databases/dailybeat.db "SELECT * FROM events"`

**Output:** persistent event store.

**Time:** 1 evening.

---

## 11. Phase 9 â€” Voice capture + Whisper (1-2 evenings)

**Goal:** the floating-mic feature. Tap once, speak, structured event saved.

**Steps:**

1. Add whisper.cpp Android integration. The agent pulls:
   ```
   git clone https://github.com/ggerganov/whisper.cpp android/whisper.cpp
   ```
   Then build only the JNI bindings using CMake (follow the official Android example at `examples/whisper.android`).

2. Bundle the Whisper tiny multilingual model in assets:
   - Download `ggml-tiny.bin` from whisper.cpp's Hugging Face
   - Copy to `app/src/main/assets/ggml-tiny.bin`

3. `WhisperBridge.kt` â€” JNI wrapper exposing `transcribe(samples: FloatArray): String`

4. `VoiceCaptureService.kt` â€” foreground service:
   - Shows persistent notification "Tap to dictate"
   - On tap: records audio, writes WAV, calls Whisper, gets text
   - Sends text to `EventExtractor.kt` (LLM) which extracts structured event
   - Saves to Room

5. `EventExtractor.kt` prompt:
   ```
   Extract a structured event from this voice note.
   Return ONLY valid JSON with these fields:
   - timestamp_guess (HH:MM or "unknown")
   - place_guess (string or "unknown")
   - people (array of names, empty if none)
   - case_numbers (array of strings, empty if none)
   - summary (one sentence)

   VOICE NOTE:
   {transcript}
   ```

6. UI: a small floating action button (FAB) on the main screen that:
   - Starts the foreground service if not running
   - Shows a recording overlay
   - Auto-saves when user stops talking (1.5s silence)

**Verification check (manual):**
- Open DailyBeat
- Tap the FAB
- Say: "Eleven forty, Market Beat, met IO Rajan, inspected chain snatching case FIR two four seven slash twenty six"
- Wait 2 seconds
- See a saved event with timestamp ~current, place "Market Beat", people "IO Rajan", case "FIR 247/26"

**Output:** voice â†’ structured event in ~3 seconds.

**Time:** 1-2 evenings. This is the highest-effort phase.

---

## 12. Phase 10 â€” Diary generator (1 evening)

**Goal:** "Generate today's dairy" button produces a clean, formatted dairy from the day's events.

**Steps:**

1. `DairyGenerator.kt`:
   ```kotlin
   class DairyGenerator(private val llm: LlmEngine, private val db: DailyBeatDb) {
       suspend fun generateForDay(date: LocalDate): String {
           val startMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
           val endMs = startMs + 24 * 3600 * 1000
           val events = db.events().eventsForDay(startMs, endMs)

           val eventsText = events.joinToString("\n") { e ->
               val time = Instant.ofEpochMilli(e.timestamp)
                   .atZone(ZoneId.systemDefault())
                   .toLocalTime()
                   .truncatedTo(ChronoUnit.MINUTES)
                   .toString()
               "[$time] ${e.placeName ?: "â€”"}: ${e.rawText}"
           }

           val prompt = """
               You are an Indian Police Service officer writing your official daily diary.
               Convert the following events of the day into a formal dairy entry in
               standard IPS dairy format. Use only the information given. Do not invent.
               Use 24-hour time. Keep entries concise.

               EVENTS OF THE DAY:
               $eventsText

               DAILY DIARY:
           """.trimIndent()

           return llm.generate(prompt)
       }
   }
   ```

2. UI: a "Generate" button on the home screen. Shows spinner. Output replaces the placeholder text.

3. Add an "Edit" mode: the user can correct anything the LLM got wrong before saving the final.

**Verification check (manual):**
- Add 5 events to today via voice + manual
- Tap "Generate"
- See a properly formatted dairy using only those 5 events
- No fabricated details

**Output:** working end-to-end dairy generation.

**Time:** 1 evening.

---

## 13. Phase 11 â€” PDF export (half evening)

**Goal:** export the final dairy as a PDF for email submission.

**Steps:**

1. `PdfExporter.kt` using `android.graphics.pdf.PdfDocument`:
   - A4 size
   - Title: "Daily Diary â€” {date}"
   - Officer name field (from settings, hardcoded for v1)
   - Body: the generated dairy
   - Sign-off line at the bottom

2. Save to `getExternalFilesDir(null)/DailyBeat/{date}.pdf`

3. UI: a "Share" button that opens Android's share sheet with the PDF attached.

**Verification check:**
- Generate a dairy
- Tap Share
- PDF opens in viewer
- All text is there, properly formatted

**Output:** exportable PDF.

**Time:** 3-4 hours.

---

## 14. Phase 12 â€” Passive capture (GPS + call log) (1 weekend)

**Goal:** the phone auto-captures places visited and calls made, so the user has to dictate less.

**Steps:**

1. **GPS breadcrumbs:** `LocationService.kt` foreground service:
   - Requests FINE_LOCATION + BACKGROUND_LOCATION
   - Uses Fused Location Provider with `Priority.PRIORITY_BALANCED_POWER_ACCURACY`
   - Saves a "gps" event every 5 min, or every 100m of movement (whichever first)
   - User can disable in settings

2. **Geofenced places:** settings UI to add named places (Office, Home, Court, Station, Beat areas)
   - User drops pin, sets radius (default 100m), names it
   - On detection, the system creates a "place" event with arrival/departure times

3. **Call log observer:** `CallLogObserver.kt`:
   - Polls `CallLog.Calls` content provider every 10 min (or on broadcast `ACTION_PHONE_STATE_CHANGED`)
   - Reads: number, type (incoming/outgoing/missed), duration, timestamp
   - Resolves number to contact name
   - Creates a "call" event

4. **Privacy controls:** every capture method can be toggled in Settings. Defaults: GPS ON, call log OFF (user opt-in).

5. **Battery:** foreground service shows a notification. "Pause capture" button in notification.

**Verification check:**
- Take a 30-min walk with phone
- Come back, see ~6 GPS events in today's event list
- Make a phone call
- See the call event after ~10 min (or on next refresh)

**Output:** passive event capture.

**Time:** 1 weekend.

---

## 15. Phase 13 â€” Build the release APK (half day)

**Goal:** signed APK ready to install on the user's daily phone, and optionally uploaded to Play Store.

**Steps:**

1. Generate a release keystore (one-time):
   ```powershell
   keytool -genkey -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias dailybeat
   ```
   Save the passwords in a password manager. **Do not commit `release.keystore` or `*.jks` to git.**

2. Add signing config to `app/build.gradle.kts`:
   ```kotlin
   android {
       signingConfigs {
           create("release") {
               storeFile = file("../release.keystore")
               storePassword = System.getenv("DAILYBEAT_STORE_PASSWORD")
               keyAlias = "dailybeat"
               keyPassword = System.getenv("DAILYBEAT_KEY_PASSWORD")
           }
       }
       buildTypes {
           getByName("release") {
               isMinifyEnabled = true
               isShrinkResources = true
               signingConfig = signingConfigs.getByName("release")
           }
       }
   }
   ```

3. Set env vars (PowerShell):
   ```powershell
   $env:DAILYBEAT_STORE_PASSWORD = Read-Host -AsSecureString "Store password"
   $env:DAILYBEAT_KEY_PASSWORD = Read-Host -AsSecureString "Key password"
   ```

4. Build the release APK:
   ```powershell
   cd android
   .\gradlew.bat assembleRelease
   ```

5. Locate and verify:
   ```powershell
   Get-Item app\build\outputs\apk\release\app-release.apk | Select-Object Name, Length
   # expected: ~3-5 MB (asset GGUF is NOT bundled by default; it must be downloaded at first run)
   ```

6. **Critical:** the 1.1 GB GGUF is too big to bundle. Add a one-time downloader at first run:
   - `MainActivity` on first launch detects missing model
   - Downloads from a private URL (user's GitHub Releases, IPFS, or just a local file the user copies)
   - Saves to `filesDir/`
   - Subsequent runs load from there

   For v1 the simplest: user copies `dailybeat-q4_k_m.gguf` to the phone's `Download/` folder via USB. The app detects and moves it. This avoids the downloader complexity.

**Verification check:**
- Install `app-release.apk` on a clean phone
- App opens, asks for the model file
- User copies the GGUF
- App loads it, asks for permissions
- Generate a test dairy, see output, export PDF

**Output:** `app-release.apk` (3-5 MB) + a documented model-installation step.

**Time:** half day.

---

## 16. Phase 14 â€” Dogfood + iterate (1 week of use)

**Goal:** use the app as the actual daily diary for a week. Fix the 5 most annoying things.

**Steps:**

1. Install the APK on the user's primary phone
2. Use it for 7 days as the real dairy
3. Track every annoyance in a `ISSUES.md`:
   - Voice transcription errors
   - GPS battery drain
   - LLM hallucinations
   - UI friction
   - Permission prompts
4. At end of week, pick top 5 issues, fix them
5. Rebuild, install, repeat

**Acceptance gate (must pass before "v1.0 done"):**
- [ ] Used for 7 consecutive days without falling back to manual diary
- [ ] Total diary writing time per day < 5 min (currently 20-30 min)
- [ ] Voice entry works in car, walking, at scenes
- [ ] PDF export works
- [ ] On-device-only verified: airplane mode end-to-end works
- [ ] No data leaves the phone (verified with `adb shell dumpsys netstats` showing zero outbound to non-local)

**Time:** 1 week calendar.

---

## 17. What "done" looks like (v1.0 acceptance)

A working APK the user installs on their phone, used as the primary daily diary, that:
- Captures events via voice, GPS, and call log
- Generates a clean IPS-formatted dairy from the day's events
- Exports as a PDF
- Runs fully offline, no cloud, no telemetry
- Has a personal fine-tuned model that writes in the user's own style
- Passes the held-out eval with pass_rate â‰¥ 0.7
- Has zero "fabricated details" failures on the held-out set

**Time to v1.0 from zero:** 3-4 weekends of focused work.

---

## 18. Out of scope for v1.0 (do not build)

- âŒ Cloud sync
- âŒ Multi-user / multi-device
- âŒ FIR case management
- âŒ WhatsApp message content capture (only metadata, only with explicit consent, and only in v2)
- âŒ Officer-to-officer dairy sharing
- âŒ Play Store publication (sideload for v1)
- âŒ Any feature that needs the internet

These are all v2+. Building them now breaks the "ship the smallest useful thing" discipline.

---

## 19. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Whisper-tiny too inaccurate in noisy environments | Fall back to manual entry button (always present); allow upload of "important" voice notes for offline re-transcription later |
| Fine-tune overfits on 30 examples | Held-out eval (Phase 5) catches it; reduce epochs; add more data if needed |
| GPS battery drain | Three presets: high-accuracy / balanced / battery-saver. Default = balanced. |
| LLM hallucinates case numbers or names | The prompt says "use only the information given"; the eval set has a fabricated-details test case; if it fails, reject the model |
| Unsloth install fails on Windows | Use WSL2 as fallback (user has Windows 11, WSL2 is available) |
| MediaPipe LLM Inference doesn't support Qwen2.5-1.5B | Fall back to llama.cpp Android JNI bindings; more work but proven |
| User forgets to use the app | Daily notification at 8 PM: "Don't forget your dairy. 5 min." with a deep link to the app |
| The 1.1 GB GGUF install is annoying for users | Make first-run copy from a file the user puts in `/Download/` via USB. Document clearly. |

---

## 20. Definition of failure

The product is a failure if any of these are true at v1.0:
- User does not use it for 7 consecutive days (i.e., it doesn't solve the actual pain)
- Diary generation takes > 10 min per day
- LLM invents details that aren't in the events
- App crashes more than once a week
- Any data leaves the device

If any of these are true, the agent should not declare done. It should iterate.

---

## 21. What the agent should NOT do

- âŒ Skip the held-out eval
- âŒ Use cloud LLM APIs at any point
- âŒ Add features not in this plan without asking
- âŒ Switch to a 7B+ model "to make it smarter"
- âŒ Publish to Play Store in v1
- âŒ Commit `release.keystore` or any passwords
- âŒ Add telemetry, analytics, crash reporting that phones home
- âŒ Use a database with network sync (Realm, Firebase, Supabase)

---

## 22. Order of operations (TL;DR)

1. **Verify env** (Phase 0, 30 min)
2. **Gather 30+ diary training pairs** (Phase 1, 1 evening)
3. **Set aside 10 held-out eval pairs** (Phase 2, 1 hour)
4. **Test base Qwen2.5-1.5B on the eval set** (Phase 3, 1 evening)
5. **Fine-tune with Unsloth QLoRA** (Phase 4, 1 evening)
6. **Re-eval; gate on pass_rate > base** (Phase 5, 30 min)
7. **Scaffold Android project, build hello APK** (Phase 6, 1 evening)
8. **Wire LLM into the app** (Phase 7, 1 evening)
9. **Add Room for events** (Phase 8, 1 evening)
10. **Add voice entry with Whisper** (Phase 9, 1-2 evenings)
11. **Add dairy generator** (Phase 10, 1 evening)
12. **Add PDF export** (Phase 11, half evening)
13. **Add GPS + call log passive capture** (Phase 12, 1 weekend)
14. **Build signed release APK** (Phase 13, half day)
15. **Dogfood for 1 week, fix top 5 issues** (Phase 14, 1 week)

**Total wall clock: 3-4 weekends of focused work + 1 week of dogfood = v1.0.**
