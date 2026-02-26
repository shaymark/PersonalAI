# PersonalAI

> A personal AI assistant for Android — chat, voice input, task scheduling, persistent memory, and real-time web search.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)
![Android API](https://img.shields.io/badge/API-26%2B-brightgreen)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## Screenshots

| Chat | Scheduled Tasks | Settings |
|------|----------------|---------|
| ![Chat welcome](screenshots/screen_01_chat_welcome.png) | ![Schedule empty](screenshots/screen_02_schedule_empty.png) | ![Settings](screenshots/screen_04_settings.png) |

| Chat Conversation |
|-------------------|
| ![Chat conversation](screenshots/screen_03_chat_conversation.png) |

---

## Features

### 💬 AI Chat
- Natural language conversation with a personal AI assistant
- Message bubbles with timestamps and smooth scroll-to-bottom
- Typing indicator while the AI is responding
- Error feedback via Snackbar

### 🎤 Voice Input
- Tap the microphone button to speak your message
- Powered by Android's built-in speech recognition

### 🔍 Real-Time Web Search
- The AI automatically searches the web when you ask about current events, news, or live data
- Powered by OpenAI's `web_search_preview` tool — no extra API key needed

### 📅 Smart Task Scheduling
- Ask the AI to set a reminder — it creates and schedules it automatically
  - *"Remind me to call the dentist tomorrow at 10am"*
  - *"Schedule a meeting with John in 2 hours"*
- Tasks appear on the Scheduled Tasks screen with their due time
- Overdue tasks are visually highlighted
- Android notifications fire at the scheduled time (even when the app is in the background)
- Manually add tasks via the ➕ button

### 🧠 Persistent Memory
- The AI remembers things across conversations
- Save memories naturally:
  - *"Remember that my name is Alex"*
  - *"Remember I prefer concise answers"*
- Retrieve memories: *"What do you remember about me?"*
- Forget specific topics: *"Forget my name"*
- Clear everything: *"Forget everything about me"*

### ⚙️ Settings
- Add your OpenAI API key to switch from mock mode to real GPT-4o-mini responses
- Clear chat history with one tap

### 🤖 Mock Mode (No API Key Required)
- The app runs fully offline with a built-in mock AI
- Supports scheduling, memory, and common conversational intents
- Useful for testing or when you don't have an OpenAI key

---

## Tech Stack

| Category | Library / Tool |
|----------|---------------|
| Language | Kotlin 2.1.20 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Architecture | Clean Architecture + MVVM |
| Navigation | Navigation Compose 2.8.5 |
| Dependency Injection | Hilt 2.55 |
| Database | Room 2.6.1 |
| Preferences | DataStore Preferences 1.1.1 |
| Background Work | WorkManager 2.9.1 |
| Networking | OkHttp 4.12.0 |
| AI Backend | OpenAI Responses API (GPT-4o-mini + web_search_preview) |
| Async | Kotlin Coroutines 1.10.1 |
| Testing | JUnit 4, MockK 1.13.12, Compose UI Test |

---

## Architecture

```
┌─────────────────────────────────────────────┐
│               Presentation Layer             │
│   ChatScreen · ScheduleScreen · Settings    │
│   (Jetpack Compose + ViewModels)            │
├─────────────────────────────────────────────┤
│                Domain Layer                  │
│   Use Cases · Repository Interfaces         │
│   Domain Models (Message, Memory, Task)     │
├─────────────────────────────────────────────┤
│                 Data Layer                   │
│  ┌─────────────────┐  ┌────────────────┐   │
│  │  OpenAiDataSource│  │MockAiDataSource│   │
│  │  (Responses API) │  │  (offline)     │   │
│  └────────┬─────────┘  └───────┬────────┘   │
│           └──────────┬──────────┘            │
│               AiRepositoryImpl               │
│   Room DB (messages, tasks, memories)        │
│   DataStore (API key)                        │
│   WorkManager (notifications)                │
└─────────────────────────────────────────────┘
```

## Project Structure

```
app/src/main/java/com/personal/personalai/
├── data/
│   ├── datasource/ai/
│   │   ├── OpenAiDataSource.kt       # OpenAI Responses API + web search
│   │   └── MockAiDataSource.kt       # Offline fallback responses
│   ├── local/                        # Room entities, DAOs, database
│   ├── repository/                   # Repository implementations
│   └── preferences/                  # DataStore for API key
├── domain/
│   ├── model/                        # Message, Memory, ScheduledTask
│   ├── repository/                   # Repository interfaces
│   └── usecase/                      # Business logic (10 use cases)
├── presentation/
│   ├── chat/                         # ChatScreen + ChatViewModel
│   ├── schedule/                     # ScheduledTasksScreen + ViewModel
│   ├── settings/                     # SettingsScreen + ViewModel
│   └── navigation/                   # Bottom nav setup
├── di/                               # Hilt modules
└── worker/                           # WorkManager notification worker
```

---

## How It Works — AI Action Tags

The AI appends machine-readable tags to its responses to trigger app actions. These tags are parsed by `SendMessageUseCase` and stripped before the message is displayed to the user.

| Tag | Trigger phrase | Action |
|-----|---------------|--------|
| `[TASK:{"title":"...","scheduledAt":"..."}]` | *"Remind me to..."* | Creates a scheduled notification |
| `[MEMORY:{"content":"...","topic":"..."}]` | *"Remember that..."* | Saves to Room DB, injected into every future prompt |
| `[FORGET:{"topic":"..."}]` | *"Forget my name"* | Deletes all memories with that topic |
| `[FORGET_ALL]` | *"Forget everything"* | Clears all stored memories |

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11+
- Android device or emulator (API 26+)

### 1. Clone the repository

```bash
git clone https://github.com/shaymark/PersonalAI.git
cd PersonalAI
```

### 2. Open in Android Studio

Open the `PersonalAI` folder in Android Studio. Gradle will sync automatically.

### 3. Run the app

```bash
./gradlew installDebug
```

Or press **Run ▶** in Android Studio.

The app launches in **mock mode** — no API key needed to try it out.

### 4. (Optional) Enable Real AI with OpenAI

1. Get an API key from [platform.openai.com](https://platform.openai.com/api-keys)
2. Open the app → tap ⚙️ **Settings** → paste your key → tap **Save**
3. The app now uses **GPT-4o-mini** with real-time web search

> **Note:** Web search calls use OpenAI's `web_search_preview` tool and are billed to your OpenAI account per the current pricing.

---

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | OpenAI API calls |
| `RECORD_AUDIO` | Voice input (speech-to-text) |
| `POST_NOTIFICATIONS` | Task reminder notifications |
| `RECEIVE_BOOT_COMPLETED` | Restore scheduled tasks after device reboot |

---

## License

```
MIT License

Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
