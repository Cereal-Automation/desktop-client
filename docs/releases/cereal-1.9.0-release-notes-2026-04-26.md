# Cereal 1.9.0 Release Notes — 2026-04-26

---

## Discord

🥣 **Cereal 1.9.0 is out!**

A quick rundown of what's new 👇

✨ **What's new**
- Settings now shows a notification dot when a new update is ready — no more popups on startup. The "Check for updates" row also tells you exactly which version is waiting.
- Scripts that need a newer version of Cereal to run now show a clear warning, and can't be started (or installed) until you update. No more silent failures.
- A confirmation dialog now appears before you delete a task — accidental taps won't cost you anymore.
- Optional dropdown fields in script setup now have a × button to clear your selection.
- Scripts can now include list-of-text fields in their configuration — useful for scripts that need multiple values from you.
- Script concurrency can now be configured up to 25 simultaneous runs.

🐛 **Fixes**
- Cancelled or interrupted tasks no longer get stuck showing "Running" after you reopen the app.
- The log viewer now shows only the current run's logs — not logs from a previous run of the same script.
- Cancelling a script now stops it immediately instead of letting it keep going in the background.
- Network slowdowns (like a timeout) are handled quietly — they no longer show up as unexpected errors.
- Fixed a crash that could occur on launch in certain environments.
- Dropdown fields in script setup now open correctly on tap, and the clear button works reliably.
- Fixed a bug where the wrong message appeared after a script finished running.

⚡ **Improvements**
- Loading script configuration groups is noticeably faster.

---
Really happy with the update indicator this release — it's been a long-requested quality-of-life change and it's much cleaner than the old startup dialog. Let us know what you think!

Full changelog: https://cereal-automation.com/changelog

---

## Instagram

Cereal 1.9.0 just landed ✨

Settings now quietly lets you know when an update is ready — no more popups on startup, just a small dot that says "hey, something's waiting for you." You can see exactly which version is available right from Settings.

We also added smarter script compatibility checks: if a script needs a newer version of the app to run, you'll see a clear warning instead of a silent failure. And before you delete a task, the app now asks for confirmation — because accidental taps happen to everyone 😅

Plus a solid round of bug fixes: cancelled tasks no longer get stuck as "Running," the log viewer shows the right run's history, and cancelling a script stops it right away. Things feel a lot more reliable.

Link in bio for the full changelog 🔗

#cerealapp #cereal #automation #appupdate #newrelease #androidapp #taskautomation #softwareupdate #productivity #mobileapp #opensource #buildinpublic

---

## X (Twitter) Thread

**Tweet 1**
🥣 Cereal 1.9.0 just dropped. Here's what's new 🧵

**Tweet 2**
✨ Settings now shows a dot when an update is ready — no more startup popups. You can also see which version is available right in the "Check for updates" row.

**Tweet 3**
⚠️ Scripts that need a newer version of Cereal now show a clear warning instead of failing silently. Same check runs at install time too — no surprises.

**Tweet 4**
🗑️ Deleting a task now asks "are you sure?" first. Optional dropdown fields in script setup also got a clear (×) button so you can unset a selection easily.

**Tweet 5**
🐛 Big batch of fixes: cancelled tasks no longer get stuck as "Running," the log viewer shows only the current run, and cancelling a script stops it immediately.

**Tweet 6**
Full changelog: https://cereal-automation.com/changelog — feedback welcome 🙌

---

## Formal Changelog

## [1.9.0] - 2026-04-26

### Added
- Settings menu now shows a notification dot when a new version of Cereal is available — no more startup popups, just a quiet indicator that something's ready.
- The "Check for updates" row in Settings now shows the version number of the available update.
- Scripts that require a newer version of the Cereal app now show a clear warning in the UI and cannot be started or installed until you update.
- A confirmation dialog now appears before deleting a task, preventing accidental removals.
- Optional dropdown fields in script configuration now include a clear (×) button to unset a selection.
- Script configuration forms can now include list-of-text fields, allowing scripts to ask for multiple text values.
- Script concurrency can now be configured up to a maximum of 25 simultaneous runs.

### Fixed
- Tasks that were cancelled or interrupted no longer get stuck showing "Running" after reopening the app.
- The task log viewer now correctly shows only the logs from the most recent run, not from a previous run of the same script.
- Cancelling a script now stops it immediately — it no longer continues running in the background.
- Network timeouts are now handled gracefully and no longer appear as unexpected errors.
- Fixed a crash that could occur when launching in certain environments.
- Dropdown fields in script configuration now open correctly on tap, and the clear button responds to taps reliably.
- Fixed an issue where the wrong success message was shown after a script finished running.
- Fixed a bug where scripts could fail silently at startup if they required a newer version of the app.

### Improved
- Loading dataset group configurations in script setup is noticeably faster.

### Behind the scenes
- Various stability and maintenance improvements.

---

## Blog Post

## Cereal 1.9.0 is out

The headline for this release is simpler, smarter update awareness — and a big batch of reliability fixes that have been a long time coming.

### What's new

Settings now quietly checks for updates in the background and shows a small dot on the Settings menu item when a new version is ready. No more popups on startup. Head into Settings and you'll see exactly which version is waiting for you in the "Check for updates" row.

We also added proper script compatibility checks. If a script you have installed — or are trying to install — requires a newer version of the Cereal app to run, you'll now see a clear warning. The script won't start until you update, so there are no more silent failures to scratch your head over.

Two smaller but useful additions: a confirmation dialog now appears before deleting a task (so accidental taps don't cost you), and optional dropdown fields in script setup now have a × button to clear your selection. Scripts can also now include list-of-text fields in their configuration, which opens up more flexible script setups.

### Fixes and improvements

A lot of reliability work went into this release. Cancelled tasks no longer get stuck showing "Running" when you reopen the app. The log viewer now correctly shows only the logs from the current run — not old ones from a previous run of the same script. Cancelling a script stops it immediately rather than letting it keep going in the background. Network timeouts are handled more gracefully. And loading script configuration groups is noticeably faster.

### How to update

Update through the Cereal desktop app — it'll prompt you on next launch, or you can check for updates manually from the settings menu.

Thanks to everyone who reported the task-status and log viewer issues — this one's for you.

Full changelog: https://cereal-automation.com/changelog
