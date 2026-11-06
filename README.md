# Soa Dialer 📞

An enterprise-grade **Android Default Dialer and Lead Management CRM** built with **Kotlin** and **Jetpack Compose**. Specifically tailored for admissions teams, telecallers, and center managers to streamline calling workflows, record interactions, manage lead pipelines, and sync directly with **MongoDB Atlas** and **Webhooks**.

---

## 🚀 Key Features

### 1. 🔒 Role-Based CRM Session Lock & Manager Authentication
- **Secure Access Control**: Database enquiries, student lead profiles, follow-up queues, and pipeline statistics remain strictly locked until an authorized Manager logs in.
- **Account Profiles**: Fast profile selection for center managers (e.g. *Manager Whitefield*, *Manager Brookfield*, *Assistant Manager*) or direct login with username/email.
- **BCrypt Password & PIN Verification**: Verifies against MongoDB Atlas password hashes with fallback to the center manager PIN (`9999`).
- **One-Tap Logout**: Immediate session termination locks the CRM and clears in-memory customer data.

### 2. 📱 Full Telecom Default Phone Dialer
- **Complete Phone Handling**: Incoming/Outgoing calls, native call state management, and heads-up notifications.
- **Active In-Call Screen (`InCallActivity.kt`)**:
  - Mute microphone, Loudspeaker toggle, Hold call.
  - Redial and Dual-tone Multi-Frequency (DTMF) dial pad.
  - Multi-party / Conference calling (`Merge Calls` and `Swap Calls`).
- **Call History & Contact Management**:
  - Filter by Incoming, Outgoing, and Missed calls.
  - Direct in-app contact **Save, Edit, and Delete**.
  - **Favorite Contacts** starring and Quick Call / Speed Dial.
  - Redial last dialed number by tapping Call on an empty dial pad.

### 3. 🎙️ Auto Call Recording & Audibility
- **Auto Call Recording (`CallRecorder.kt`)**: Automatically starts recording when an incoming or outgoing call connects.
- **Hardware Voice Optimization**: Uses `VOICE_COMMUNICATION` with Automatic Gain Control (AGC) and Acoustic Echo Cancellation (AEC), multi-source fallback, and high-fidelity 44.1kHz / 128kbps AAC encoding.
- **Audio Mode Reset**: Explicitly resets the system `AudioManager` to `MODE_NORMAL` upon call disconnection to prevent low speaker volume.
- **Built-in Player (`CallRecordingPlayerDialog.kt`)**: In-app playback dialog with audio waveform progress, loudspeaker volume boost, and speaker/earpiece routing toggle.

### 4. 📋 Lead Pipeline, Calling Reports & Demo Scheduling
- **Lead Pipeline Analytics**:
  - Daily, weekly, and monthly calling metrics (total calls, outgoing, incoming, missed, connected, average call duration, and total talk time).
  - Funnel breakdown: *New Lead, Contacted, Interested, Call Later, Demo Booked, Converted, Lost, Spam*.
  - Collapsible summary bar to keep the enquiries tab clean and uncluttered.
- **Schedule Demo & Tasks (`ScheduleTaskDialog`)**:
  - Select existing students/leads directly from the live enquiry search picker.
  - Automatically pre-fills course interest, updates lead status to *Demo Booked*, and sets exact follow-up alarms via `AlarmManager`.
- **Direct WhatsApp Integration**: One-click opening of WhatsApp (`wa.me`) directly from customer profiles without requiring pre-saved contacts.

### 5. 🔄 MongoDB Atlas Live Synchronization & Offline Cache
- **Offline-First SQLite Cache (`EnquiryDatabaseHelper.kt`)**: Instant sub-5ms Caller ID resolution and local caching for seamless offline operation.
- **Live MongoDB Sync (`MongoAtlasSyncManager.kt`)**: Two-way synchronization between device local SQLite and remote MongoDB Atlas collections (`users`, `enquiries`, `call_records`).
- **CLI Sync Scripts**:
  - `sync-leads.ps1` / `sync-leads.bat`: Direct CLI sync for leads and enquiries.
  - `sync-calls.ps1` / `sync-calls.bat`: Direct CLI sync for call logs and duration records.
- **CRM Webhook Integration (`CrmWebhookSync.kt`)**: Real-time push to Google Sheets, Make.com, Zapier, Airtable, or custom endpoints after every completed call.

---

## 🏗️ Project Architecture

```
app/src/main/java/com/dialer/truedialer/
├── AppConfig.kt                        # Global config, session state, centers & preferences
├── MainActivity.kt                     # Application entry point, navigation & permissions
├── TrueDialerApplication.kt            # Notification channels & database initialization
│
├── data/
│   ├── local/
│   │   └── EnquiryDatabaseHelper.kt   # SQLite engine (Users, Enquiries, Calls, Follow-ups)
│   ├── model/
│   │   ├── CenterUser.kt              # Center user profiles, manager roles & auth
│   │   ├── Enquiry.kt                 # Lead & student profile data model
│   │   ├── CallRecord.kt              # Historical call records & durations
│   │   ├── ContactRecord.kt           # Device contact model
│   │   ├── FollowUpReminder.kt        # Scheduled tasks and demo alarms
│   │   └── MessageTemplate.kt         # Message templates
│   └── repository/
│       ├── EnquiryRepository.kt       # Lead data abstraction
│       ├── LocalFirstEnquiryRepository.kt # Offline SQLite + remote sync
│       ├── ContactsRepository.kt      # Phonebook reader & direct editor
│       └── CallLogRepository.kt       # System CallLog provider
│
├── receiver/
│   ├── BootReceiver.kt                # Re-registers alarms on device restart
│   ├── CallActionReceiver.kt          # Call actions (Hang up, Answer, Mute)
│   └── CallReceiver.kt                # Telephony state broadcaster
│
├── service/
│   ├── ActiveCallManager.kt           # Call states, durations & recording triggers
│   ├── CallNotificationManager.kt     # Heads-up and silent call notification channels
│   ├── CallerOverlayService.kt        # Truecaller-style floating caller ID
│   ├── DialerInCallService.kt         # Android Telecom InCallService implementation
│   ├── EnquiryCallScreeningService.kt # Spam filtering and number screening
│   └── FollowUpNotificationReceiver.kt# Exact alarm notifications
│
├── sync/
│   ├── MongoAtlasSyncManager.kt       # REST sync with MongoDB Atlas Data API
│   └── CrmWebhookSync.kt              # Real-time webhook dispatcher
│
├── ui/
│   ├── activities/
│   │   ├── InCallActivity.kt          # Active in-call UI (Keypad, Hold, Speaker, Merge)
│   │   └── PostCallActivity.kt        # Post-call outcome dialog & note taker
│   ├── components/
│   │   ├── CrmLoginView.kt            # Manager login & session gate
│   │   ├── UserSwitcherDialog.kt      # Profile switcher with logout button
│   │   ├── CallRecordingPlayerDialog.kt # Audio playback with loudspeaker boost
│   │   ├── Dialpad.kt                 # T9 interactive dial pad
│   │   ├── CallerIdCard.kt            # Live caller lookup card
│   │   ├── ContactDetailDialog.kt     # In-app contact detail & editor
│   │   └── SaveContactDialog.kt       # Quick contact saver
│   └── screens/
│       ├── DialerScreen.kt            # T9 dial pad, speed dial & redial
│       ├── RecentsScreen.kt           # Call history with uniform cards & recording playback
│       ├── ContactsScreen.kt          # Phonebook with Favorites and search
│       ├── EnquiryListScreen.kt       # Lead management, calling reports & demo bookings
│       ├── AnalyticsScreen.kt         # Pipeline analytics & follow-up queue
│       ├── SettingsScreen.kt          # Preferences, user switcher & logout
│       └── MainScreen.kt              # Bottom navigation host
│
└── util/
    ├── CallRecorder.kt                # MediaRecorder with AGC/AEC voice optimization
    ├── CallRecordingUtils.kt          # File management & duration formatters
    ├── PhoneNumberUtils.kt            # Number normalization & formatting
    ├── DeviceUnlockHelper.kt          # Keyguard bypass for incoming call screens
    └── PermissionUtils.kt             # Telecom, overlay, audio & contact permissions
```

---

## 🛠️ Building & Installing

### Prerequisites
- **Android SDK**: API 34 (Android 14) or higher.
- **Java**: JDK 17.
- **Build System**: Gradle 9.x / AGP 9.0.

### Build APK
Run the following command from the project root:

```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

The compiled APK will be output to:
- `app/build/outputs/apk/debug/Soa Dialer.apk`
- Pre-packaged standalone binary: `.\Soa Dialer.apk`

### Install to Connected Device via ADB

```bash
adb install -r ".\Soa Dialer.apk"
```

### Launch the App

```bash
adb shell am start -n com.soa.phone/com.dialer.truedialer.MainActivity
```

---

## 🔐 Credentials & Default Settings

- **Default Manager PIN**: `9999`
- **Supported Login Roles**: `MANAGER`, `ACADEMIC_MANAGER`, `OPERATIONS_MANAGER`, `MEDIA_MANAGER`, `PURCHASE_MANAGER`, `ADMIN`.
- **Package Name**: `com.soa.phone`
- **Default Center**: `SOA-Whitefield` (`67cd080a040cb823ea6c0744`)

---

## 📄 License
Internal proprietary application developed for School of Athens (SOA). All rights reserved.
