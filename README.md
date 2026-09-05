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

## Architecture

```
data/
  model/      Message, Conversation, Contact
  repo/       SmsRepository (system Telephony provider), ContactsRepository
  db/         Room: NotificationRule, ThreadMeta (pin/archive/mute/draft)
notifications/
  RuleEngine       pure matching logic — no Android deps, fully unit-tested
  ChannelManager   per-rule channel lifecycle
  MessageNotifier  builds the notification, applies the winning rule
sms/          SMS_DELIVER / WAP_PUSH receivers, sender, status receivers
ui/           Compose screens + ViewModels, Navigation
```

Conversations and messages live in the **system SMS provider**, so nothing is
lost if the user switches default SMS apps. Only the state the platform has
nowhere to store — rules, pins, archives, mutes, drafts — lives in Room.

## Building

```bash
./gradlew assembleDebug     # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest # 30 unit tests covering the rule engine and number matching
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
`POST_NOTIFICATIONS`, `VIBRATE`. The app must be set as the default SMS handler
to receive messages — Android only delivers `SMS_DELIVER` to the default app.

## Note on MMS

The MMS `WAP_PUSH_DELIVER` receiver is registered (a hard requirement for being
selectable as the default SMS app) but full MMS download, which needs a
transaction against the carrier's MMSC, is not implemented. SMS is complete.
