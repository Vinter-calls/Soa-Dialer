# True Enquiry Dialer (Android) 📞

A Truecaller-style Android Dialer and Lead Management CRM built with **Kotlin** and **Jetpack Compose**. It connects to your backend **Enquiry Database**, detects unsaved contacts, and includes built-in sales and lead workflows.

---

## 🚀 Key Features Implemented

### 1. 📋 Post-Call Action Screen (`PostCallActivity.kt`)
- Automatically prompts when a call finishes (incoming or outgoing).
- **Quick Notes**: 1-tap discussion chips (*"Interested - Send Brochure"*, *"Callback requested"*, *"Pricing discussed"*).
- **Lead Status Update**: 1-tap change (*New Lead*, *Follow-up*, *Converted*, *Lost*, *Spam*).
- **Scheduled Follow-up Reminders**: Choose *"In 1 Hour"*, *"Tomorrow 10 AM"*, *"Next Week"* to schedule exact local notifications with direct "Call Now" buttons via `AlarmManager`.
- **Save to Phone Contacts**: Prompted if still not saved.
- **Auto-Sync to CRM**: Dispatches full call log, notes, and lead status to your CRM Webhook.

### 2. 💬 WhatsApp Business Message Templates (`WhatsAppBusinessHelper.kt`)
- Direct integration targeting **WhatsApp Business** (`com.whatsapp.w4b`) with automatic fallback to standard WhatsApp.
- **1-Tap Template Picker**: Previews messages with lead details auto-filled:
  - *Catalog & Brochure Introduction*: Auto-fills `{name}`, `{company}`, and brochure link.
  - *Post-Call Summary & Follow-up*: Auto-fills `{name}` and `{requirement}`.
  - *Missed Call Alert*: Professional prompt to assist the caller.
  - *Meeting Confirmation*.
- Send to customer without needing to save their number in contacts first!

### 3. ⚡ Offline SQLite Database & Instant Caller ID Cache (`EnquiryDatabaseHelper.kt`)
- Local SQLite database on the device storing all enquiries, call notes, templates, and scheduled follow-ups.
- **Sub-5ms Caller ID response time**: Identifies callers instantaneously even when traveling, in elevators, or offline with zero network connectivity.
- Automatically synchronizes with your remote backend when connected.

### 4. 📊 Call Analytics & Daily Lead Pipeline Dashboard (`AnalyticsScreen.kt`)
- Dedicated **Pipeline** tab in bottom navigation:
  - **KPI Cards**: Recent Calls, Converted Deals, Conversion Rate %, Due Follow-ups.
  - **Funnel Progress Bars**: Distribution of leads across *New Leads*, *Follow-ups*, *Converted*, *Lost*, *Spam*.
  - **Today's Due Follow-Up Tasks**: Actionable list of reminders with 1-tap "Call", "WhatsApp", and "Mark Complete" buttons.

### 5. 🚫 Spam & Unqualified Lead Tagging & Auto-Reject (`EnquiryCallScreeningService.kt`)
- 1-tap "Mark as Spam" on the Caller ID overlay, post-call card, or recents.
- Highlighted red alert banner on calls from flagged numbers.
- Optional **Auto-Reject Spam Calls** setting in Settings to automatically silence/reject unwanted calls.

### 6. 🔄 CRM / Google Sheets Webhook Sync (`CrmWebhookSync.kt`)
- Configurable **Webhook URL** in Settings (supports Google Sheets Apps Script, Make.com, Zapier, Airtable, HubSpot, Custom REST backend).
- Dispatches event payloads on call completion:
```json
{
  "event": "call_completed",
  "timestamp": 1730000000000,
  "phoneNumber": "+919876543210",
  "callType": "INCOMING",
  "callDurationSeconds": 142,
  "postCallNote": "Customer requested pricing quote by Friday",
  "scheduledFollowUpEpochMs": 1730086400000,
  "enquiry": {
    "id": "ENQ-1001",
    "name": "Rajesh Sharma",
    "company": "Apex Real Estate",
    "enquiryType": "3BHK Luxury Villa",
    "status": "Follow-up",
    "budget": "₹ 1.8 Cr"
  }
}
```
- Includes live **Test Ping** button in Settings to verify your webhook endpoint.

---

## 🏗️ Project Structure

```
app/src/main/java/com/dialer/truedialer/
├── AppConfig.kt                        # Database, CRM Webhook, and Spam settings
├── MainActivity.kt                     # Main entry point & runtime permissions
├── TrueDialerApplication.kt            # Notification channels & database init
│
├── data/
│   ├── local/
│   │   └── EnquiryDatabaseHelper.kt   # Offline SQLite cache (Enquiries, Notes, Reminders)
│   ├── model/
│   │   ├── Enquiry.kt                 # Lead model
│   │   ├── CallRecord.kt              # Call log model
│   │   ├── ContactRecord.kt           # Device contact model
│   │   ├── MessageTemplate.kt         # WhatsApp Business template model
│   │   └── FollowUpReminder.kt        # Scheduled reminder model
│   └── repository/
│       ├── EnquiryRepository.kt       # Interface
│       ├── LocalFirstEnquiryRepository.kt # Offline-first cache + remote sync
│       ├── MockEnquiryRepository.kt   # In-memory mock
│       ├── RemoteEnquiryRepository.kt # HTTP client for remote backend
│       └── ContactsRepository.kt      # Unsaved contact checker & saver
│
├── service/
│   ├── CallerOverlayService.kt        # Jetpack Compose floating overlay
│   ├── EnquiryCallScreeningService.kt # Android 10+ CallScreeningService
│   ├── DialerInCallService.kt         # Telecom Default Dialer InCallService
│   └── FollowUpNotificationReceiver.kt# AlarmManager broadcast receiver
│
├── sync/
│   └── CrmWebhookSync.kt              # Google Sheets / CRM Webhook dispatcher
│
├── util/
│   ├── WhatsAppBusinessHelper.kt      # WhatsApp Business intent dispatcher
│   ├── PhoneNumberUtils.kt            # Normalization & deep links
│   └── PermissionUtils.kt             # Telecom & overlay permission helpers
│
└── ui/
    ├── activities/
    │   └── PostCallActivity.kt        # Post-call action screen
    ├── components/
    │   ├── Dialpad.kt                 # T9 keypad
    │   ├── CallerIdCard.kt            # Live enquiry card above dialpad
    │   ├── PostCallDialog.kt          # Quick notes, lead status & follow-up scheduler
    │   ├── TemplateChooserSheet.kt    # WhatsApp Business template selector
    │   ├── SaveContactDialog.kt       # 1-tap contact saver
    │   └── EnquiryBadge.kt            # Status chip
    └── screens/
        ├── DialerScreen.kt            # T9 dialpad + live enquiry lookup
        ├── RecentsScreen.kt           # Call history with enquiry tags & WhatsApp
        ├── AnalyticsScreen.kt         # KPI metrics, pipeline funnel & follow-ups
        ├── EnquiryListScreen.kt       # Backend enquiry list & search
        ├── SettingsScreen.kt          # CRM Webhook, Spam filter, Database & Simulator
        └── MainScreen.kt              # Bottom navigation scaffold
```

---

## 🛠️ Testing the New Features in Android Studio

1. Open project in **Android Studio**.
2. Run on device or emulator (`Shift + F10`).
3. In the app's **Settings** tab:
   - Tap **Test Caller ID** to preview the Truecaller floating popup with lead details & WhatsApp button.
   - Tap **Test Post-Call** to preview the post-call note taking, lead status changer, and follow-up scheduler.
   - Enter your **CRM Webhook URL** and tap **Test Ping** to test live sync.
   - Switch between **Mock Database** and **Remote Database** whenever ready.
