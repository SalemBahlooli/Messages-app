# Messages

A full-featured Android SMS client, built the way the stock Messages app works —
plus one feature the stock app doesn't have: **per-sender and per-keyword
notification rules**.

## The custom feature: notification rules

Stock Android gives you exactly one notification sound for every text. This app
lets you write rules that decide how each message announces itself.

A rule has **conditions** and **actions**.

**Conditions — when does this rule apply?**

| Sender | Message body |
| --- | --- |
| Anyone | Any text |
| Specific numbers (pick from contacts) | Contains any keyword |
| Anyone in my contacts | Contains all keywords |
| Unknown numbers only | Starts with |
| | Matches a regular expression |

Keyword matching is case-insensitive by default and can be made case-sensitive.

**Actions — how should it alert?**

- **Notification sound** — any tone on the device, chosen through the system
  ringtone picker
- **Vibration pattern** — Short, Double tap, Long, Heartbeat, S.O.S., or none
  (tap a pattern in the editor to feel it immediately)
- **Urgency** — Silent, Quiet, Normal, or Urgent (pops on screen)
- **Accent colour** for the notification and LED
- **Title badge**, e.g. prefix a matched notification with 🔐 or 🔴
- **Bypass Do Not Disturb**
- **Hide the message text on the lock screen**

Rules are evaluated top to bottom and the first match wins; each rule can be
reordered, and a rule may be marked "don't stop here" so a more specific rule
later in the list can take over. You can also pin a rule to a single
conversation from inside that chat, overriding the conditions entirely.

**Try it before you save.** The editor has a built-in tester: type a sample
sender and message, tap *Test rule*, and it tells you whether the rule fires and
exactly which condition matched.

Two example rules ship disabled on first launch — "Verification codes" (matches
`code`, `OTP`, `verification`, `رمز`, `تحقق`) and "Unknown senders stay quiet".

### How the custom tones actually work

Android freezes a notification channel's sound, vibration and importance at
creation time — you cannot change them afterwards. So each rule's channel id
embeds a hash of its alerting settings (`NotificationRule.channelId`). Editing a
rule's tone produces a new channel and the stale one is deleted, which is what
makes the per-rule ringtone take effect on Android 8+ rather than silently
keeping the old sound.

## Smart features

- **Scheduled messages** — tap the clock in the composer (or long-press Send) and
  pick *in an hour*, *tonight*, *tomorrow morning* or any date and time. Waiting
  messages show in the chat as outlined bubbles; tap one to send it now or cancel
  it (the text comes back to the composer). Alarms are re-armed after a reboot or
  an app update.
- **Undo send** — a configurable 3/5/10-second window with a countdown bar and an
  *Undo* button. Leaving the chat inside the window still sends the message.
- **Verification-code helper** — codes in OTP messages (English and Arabic,
  including Arabic-Indic digits) get a *Copy 482913* chip under the bubble and a
  *Copy* button on the notification.
- **Quick replies** — one-tap chips above an empty composer, editable in the chat
  or in Settings; seeded in the phone's language.
- **Starred messages** — star any message from its long-press menu; every starred
  message, from every chat, in one list.
- **Tappable links** — links, phone numbers and e-mail addresses inside messages
  open the browser, dialler or mail app. Amounts in bank messages are not
  mistaken for phone numbers.
- **Block senders** — from a chat's menu or by typing a number or sender name.
  A blocked sender's messages are still stored (only the default SMS app can do
  that) but arrive read, archived and silent.
- **Quiet hours** — a nightly (or any) window in which messages arrive silently;
  rules that bypass Do Not Disturb still ring.
- **App lock** — fingerprint, face or the screen lock, after a cold start or ten
  seconds in the background. Recents thumbnails are hidden while it is on.
- **Insights** — the last 90 days: sent, received and verification codes, the
  people you text most, the busiest time of day and the last two weeks.
- **Export a conversation** — the whole chat as a text file, through the share sheet.
- **Forward** and **read aloud** (text-to-speech, in Arabic for Arabic text) from
  the message menu, which also lets you select part of a message to copy.
- **Swipe** a conversation to archive it, or towards the other side to mark it read.
- **Filters** above the list — All, Unread, Contacts, Unknown — and search results
  that highlight the matched words.
- Long chats load older messages as you scroll up, and a button jumps back to the
  newest message.

## Everything else (the stock-app feature set)

- Registers as a **default SMS app**: `SMS_DELIVER` receiver, `WAP_PUSH_DELIVER`
  receiver, `RESPOND_VIA_MESSAGE` service, and `sms:`/`smsto:` intent handling
- Conversation list with contact names, photos, monogram avatars, unread badges,
  relative timestamps
- **Pin, archive, mute and multi-select** conversations; bulk delete and bulk
  mark-as-read
- Full-text **search** across all messages
- Chat view with bubbles, day separators, tap-for-timestamp, long-press to
  delete, and delivery state (sending → sent → delivered, with **Retry** on
  failure)
- Sending long messages as multipart SMS, with a live character/segment counter
- Per-conversation **drafts** that survive leaving the screen
- Contact picker for new messages, or text any typed number
- **MessagingStyle notifications** with a conversation transcript, **inline
  direct reply**, and mark-as-read — grouped under a summary
- Quick "respond via message" replies from the incoming-call screen
- Material 3 with dynamic colour and full dark theme

## Interface

Three tabs — **Chats**, **Rules**, **Settings** — so the notification rules that
are the point of the app are one tap away rather than hidden behind an app-bar
icon. Chats keeps pinned conversations in their own section, a round search
field, unread shown as a dot rather than a count, and a selection mode for bulk
pin / mute / archive / delete. A conversation shows grouped bubbles whose
corners tighten between consecutive messages from the same side, day separators
as pills, and delivery state per message.

The rule editor reads as the sentence it represents: a **When** card for the
conditions and an **Alert me like this** card for the sound, vibration, urgency,
colour and badge, with detail fields appearing only when the choice above them
needs one. A built-in tester explains which condition matched.

Fully translated into **Arabic**, with RTL layout and auto-mirrored icons; the
app follows the phone's language. All user-facing text lives in resources —
including the rule engine's match explanations, which are returned as structured
data and formatted in the presentation layer so they render in any locale.

## Architecture

```
data/
  model/      Message, Conversation, Contact
  repo/       SmsRepository (system Telephony provider), ContactsRepository
  db/         Room: NotificationRule, ThreadMeta (pin/archive/mute/draft),
              ScheduledMessage, StarredMessage, BlockedNumber (schema v2, migrated)
  prefs/      Appearance, FeaturePrefs (undo, quiet hours, lock, quick replies)
notifications/
  RuleEngine       pure matching logic — no Android deps, fully unit-tested
  ChannelManager   per-rule channel lifecycle
  MessageNotifier  builds the notification, applies the winning rule
sms/          SMS_DELIVER / WAP_PUSH receivers, sender, status receivers,
              MessageScheduler (alarms for scheduled messages)
util/         OtpDetector, TextLinks — pure Kotlin, unit-tested
notifications/RuleTextFormatter  renders engine results in the user's language
ui/           Compose screens + ViewModels, bottom-nav Navigation
```

Conversations and messages live in the **system SMS provider**, so nothing is
lost if the user switches default SMS apps. Only the state the platform has
nowhere to store — rules, pins, archives, mutes, drafts — lives in Room.

## Building

```bash
./gradlew assembleDebug     # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest # 73 unit tests: rule engine, matching, caching, OTP, links, quiet hours
```

Requires JDK 17+ and Android SDK 34.

## Installing from an APK

Android 13+ blocks apps installed from an APK — rather than from a store — from
taking sensitive roles such as the default SMS handler, and shows *"App was
denied access to be default SMS app"*. To allow it:

**Settings → Apps → Messages → ⋮ → Allow restricted settings**, then return to
the app and tap *Set as default*. The menu item only appears after the app has
been denied once. Installing with `adb install` bypasses the guard entirely.

The app's setup card links straight to this, with the steps spelled out.

## Permissions

`READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS`, `READ_CONTACTS`,
`POST_NOTIFICATIONS`, `VIBRATE`, `SCHEDULE_EXACT_ALARM` (scheduled messages),
`USE_BIOMETRIC` (app lock). The app must be set as the default SMS handler
to receive messages — Android only delivers `SMS_DELIVER` to the default app.

## Note on MMS

The MMS `WAP_PUSH_DELIVER` receiver is registered (a hard requirement for being
selectable as the default SMS app) but full MMS download, which needs a
transaction against the carrier's MMSC, is not implemented. SMS is complete.
