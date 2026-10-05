# PRD – Reels Limiter

Oct 3, 2026 · @Akash

## Overview

Reels Limiter is a personal Android app that counts the Reels I watch on Instagram and pauses Reels for 5–10 minutes once I hit a limit I set. It only reacts on the Reels screen, so chatting in DMs is never interrupted. No backend, no account, no network; built for my own phone, not for the Play Store.

**Problem.** I use Instagram mainly for chatting, so a time-in-app limit would punish normal use. But I often drift from a chat into Reels and lose far more time than I meant to. I need a limit on Reels specifically, enforced at the moment I cross it.

**Solution.** An Accessibility Service detects when Instagram is on the Reels screen and counts each new reel. At the limit, a full-screen cooldown overlay covers Reels with a countdown and a button back to home. DMs and the feed stay usable.

## Goals, non-goals and success metrics

**Goals**

- Count Reels watched on Instagram, and nothing else.
- Pause Reels for a set cooldown (default 8 minutes) when the daily or per-session limit is reached.
- Leave DMs, feed and stories fully usable at all times.
- Run fully on-device with no INTERNET permission.

**Non-goals (v1)**

- Publishing to the Play Store (Accessibility Service policy does not apply to personal use).
- Blocking Instagram entirely, or limiting other apps (YouTube Shorts is a possible v2).
- Reading, storing or sending any message or screen text.
- Protection against me deliberately uninstalling or disabling it.

**Success metrics**

| Metric | Target |
| --- | --- |
| Reels count accuracy vs. my manual count over 50 reels | within 10% |
| False counts while in DMs or feed | 0 |
| Time from reaching the limit to overlay shown | under 1.5 s |
| Reels watched per day after 2 weeks | below my chosen limit on 5 of 7 days |
| Service killed by the OS in a day | 0 |

## User stories and settings

Primary user: me, on my own Android phone, using Instagram for chat and sometimes losing long stretches to Reels.

| # | As a user, I want to… | So that… | Priority |
| --- | --- | --- | --- |
| U1 | set a Reels limit (e.g. 30 reels) | I decide my cap once, calmly | P0 |
| U2 | have Reels paused for 5–10 minutes when I hit it | I get a real break, not just a warning | P0 |
| U3 | keep using DMs during the pause | the limiter never gets in the way of chatting | P0 |
| U4 | see a live count and the countdown | I know where I stand | P0 |
| U5 | choose daily cap, per-session cap, or both | the rule fits how I actually scroll | P1 |
| U6 | see today's and this week's counts | I can see whether it is working | P1 |
| U7 | add friction before ending a pause early | I can't dismiss it in a weak moment | P1 |
| U8 | get a gentle warning at 80% of the limit | the cutoff isn't a surprise | P2 |

**Settings**

| Setting | Default | Notes |
| --- | --- | --- |
| Reels limit | 30 | per day, per session, or both |
| Session definition | 20 min gap in Reels = new session | only used for per-session cap |
| Cooldown length | 8 min | range 5–10 min |
| Enforcement | Soft overlay | or Hard (Back action on every Reels visit) |
| Early exit friction | Type a phrase | or wait 30 s, or off |
| Daily reset time | 00:00 | adjustable |

## Functional requirements

**F1. Reels detection**

1. Listen to accessibility events only from Instagram's package (`com.instagram.android`); ignore every other app.
2. On each window or content change, inspect the accessibility tree and decide: Reels, or Not Reels (DMs, feed, stories, profile).
3. Reels = the Reels tab is selected in the bottom bar and a full-screen vertical pager is on screen. Reels opened from inside a chat count as Reels if the same pager is present.
4. Prefer content descriptions and structure over view IDs, since Instagram obfuscates IDs and changes layouts.
5. All detection rules live in one small, replaceable file so a layout change is a quick fix.

**F2. Counting**

1. Add 1 when the visible reel changes while in Reels.
2. Only count a reel if the previous one stayed on screen for at least 1.5 s, which filters fast flicks and duplicate scroll events.
3. Debounce events: one swipe can fire several.
4. Keep today's count, current session count and the last Reels timestamp locally.
5. Reset the daily count at the reset time; start a new session after the session gap.

**F3. Warning and limit**

1. At 80% of the limit, show a small non-blocking toast over Reels ("24 of 30").
2. At 100%, start the cooldown immediately: record the end time and trigger enforcement.

**F4. Enforcement during cooldown**

1. **Soft (default):** when Reels is detected during cooldown, draw a full-screen overlay with "Reels paused", the countdown and a "Back to chats" button. The button sends Back so I land in DMs or the feed.
2. **Hard:** on detecting Reels during cooldown, send the Back action immediately, with the overlay as a fallback if Back fails.
3. The overlay never shows outside Reels, so chat is untouched.
4. The cooldown is stored as an absolute end timestamp, so it survives a service restart or reboot.

**F5. Ending a pause early**

1. Optional friction: type a fixed phrase, or wait 30 s with a visible countdown.
2. Ending early is logged so the history shows it.

**F6. App screens**

- Home: today's count, limit, cooldown state, service on/off status.
- Settings: all values in the settings table.
- History: counts per day for the last 30 days.
- Setup: guides me through the Accessibility, overlay and battery-optimisation permissions.

## How it works

&#91;embedded content: counter and cooldown states\]

The counter only moves while the detector says Reels; at the limit the cooldown starts, and the overlay appears whenever Reels is on screen until it ends, then the count resets.

## Tech approach, permissions and privacy

**Platform.** Android 8.0+ (API 26+), Kotlin + Jetpack Compose for the app screens. Personal use, so it is installed as an APK, not through the Play Store.

**Components**

| Component | Responsibility |
| --- | --- |
| `ReelsAccessibilityService` | Receives events from Instagram only, runs the detector, updates the counter, triggers enforcement |
| `ReelsDetector` | Pure function: accessibility tree in, Reels / Not Reels out; all rules in one file |
| `CounterEngine` | Debounce, 1.5 s dwell rule, daily and session counts, limit check |
| `CooldownManager` | Stores the absolute end time, exposes remaining time, handles early exit |
| `OverlayController` | Draws the soft-block overlay and the 80% toast via WindowManager |
| Room / DataStore | Daily counts, settings, cooldown end time, early-exit log |

**Permissions**

- Accessibility Service (the core of the app). On Android 13+ a sideloaded app needs "Allow restricted settings" turned on in App info first.
- Display over other apps (overlay).
- Notification permission, for an optional "limit reached" notice.
- Battery optimisation exemption, so the OS doesn't kill the service.
- **No INTERNET permission.**

**Privacy.** The service listens to Instagram's package name and scroll or content-change events only. It reads structure (is a pager present, is the Reels tab selected), never message text or usernames, and stores nothing from the screen. Counts and settings stay on the device.

**Non-functional**

- Event handling under 20 ms, with work skipped when the foreground app is not Instagram.
- Negligible battery impact: no polling, no wake locks.
- Survives reboot: the service restarts with the system, and an unfinished cooldown resumes from its stored end time.
- Detection failure is safe: if the detector can't decide, it treats the screen as Not Reels, so it can never block chat by mistake.

## Edge cases and risks

| Case / risk | Handling |
| --- | --- |
| Instagram update changes the layout and breaks detection | Rules in one file; rely on content descriptions and structure; a "detector self-test" screen shows what the service sees right now |
| Detector can't decide | Treat as Not Reels: never block chat on a guess |
| Reel shared in a DM opens in a viewer | Count it only if the same vertical pager is present |
| Reel auto-advances or replays without a swipe | Counted only when the visible reel changes, not on replays |
| Overlay blocked or fails to draw | Fall back to the Back action |
| Battery saver kills the service | Setup screen links to the battery exemption; Home shows a clear "service off" warning |
| I disable the service in a weak moment | Out of scope for v1; the early-exit friction covers the in-app case only |
| Phone reboots mid-cooldown | End time is stored as a timestamp, so the pause resumes |
| Limit reached just as I open a DM from Reels | Overlay appears only while Reels is on screen, so the DM stays reachable |
| Time zone or clock change | Daily reset uses local time; cooldown uses the stored end time |

## Open questions

- [ ] Daily cap, per-session cap, or both as the default?
- [ ] Soft overlay or hard Back as the default enforcement?
- [ ] Add YouTube Shorts in v2, using the same engine with a second detector?
- [ ] A weekly report notification: useful or one more thing to ignore?

## Milestones

1. **M1: Detection.** Accessibility Service that only logs "Reels" vs "Not Reels" correctly across real use, plus a debug screen. Do not start M2 until this is reliable.
2. **M2: Counting.** Debounce, dwell rule, daily count, Home screen with the live number.
3. **M3: Enforcement.** Limit, cooldown timer, soft overlay, Back action, persistence across reboot.
4. **M4: Polish.** Settings, history, 80% warning, early-exit friction, setup guide for permissions.
