# Cereal 1.10.0 — Release Notes
_Release date: 2026-05-25_

These notes are written for everyday Cereal users. Copy the section you need for each channel.

---

## 1. Discord announcement

🥣 **Cereal 1.10.0 is out!**

This is a big one — Cereal got a fresh new look from top to bottom, plus a bunch of new tools and fixes. Here's the rundown 👇

✨ **What's new**
- **A whole new look.** We redesigned the entire app — the sidebar, Tasks, Marketplace, Proxies, Datasets, and Settings all got a cleaner, more modern feel with a new font and refined dark theme.
- **Proxy health checks.** You can now test your proxies to see which ones are working and which are failing, set Cereal to re-check them automatically (every 6h, every 24h, or off), and clear out all your failing proxies in one click. Scripts also skip failed proxies automatically.
- **Better logs.** The log panel now has Clear and Copy-all buttons, shows full dates on timestamps, can be dragged to resize, and lets you hide debug noise. Logs are now kept per task, so you always see the right output.
- **Marketplace upgrades.** Verified developers now get a badge, free-trial scripts show a clear trial label, and scripts the publisher has flagged as under maintenance show a warning dot before you install.
- **Clearer Tasks screen.** The Tasks toolbar now shows the script you've selected, you can filter tasks by status, restart all errored tasks at once, and there's a live count badge on the Tasks sidebar item.

🐛 **Fixes**
- Fixed Cereal failing to start on some Windows PCs (both with strict security policies and with accented characters in the username).
- Large downloads no longer risk crashing the app from running out of memory.
- Telegram notifications now retry properly instead of silently giving up.
- The "7-day free trial" label no longer shows up on scripts that don't actually offer one.
- Renaming a proxy or dataset group now updates instantly.
- Logging in is smoother and more reliable, and we fixed a few rare freezes and crashes.

🔒 **Behind the scenes**
- We strengthened how your saved credentials and sensitive data are stored, and made sure passwords and tokens never end up in log files.

Thanks as always for all the feedback — a lot of this release came straight from you. Enjoy the new look! 🎉

---
Full changelog: https://cereal-automation.com/blog

---

## 2. Instagram caption

Cereal just got a glow-up. ✨

Version 1.10.0 is here, and it's our biggest update yet. The entire app has a fresh new look — cleaner screens, a new font, and a sharper dark theme across the board. We also added proxy health checks so you can instantly see which proxies are working (and clear out the dead ones in one tap), gave the log panel a serious upgrade, and polished up the marketplace with verified-developer badges and clearer trial labels.

On top of all that: faster, more reliable startup, smoother logins, and a pile of fixes — including Windows startup issues and big-download crashes.

Update now and enjoy the new Cereal. 🥣

#cerealapp #cereal #automation #desktopapp #productivity #newupdate #appdesign #uiux #softwareupdate #opensource #devtools #techtools

---

## 3. X (Twitter) thread

**Tweet 1**
🥣 Cereal 1.10.0 just dropped — and it's a big one. Fresh new look, new tools, and a stack of fixes. Here's what's new 🧵

**Tweet 2**
The whole app got redesigned. Sidebar, Tasks, Marketplace, Proxies, Datasets, Settings — all rebuilt with a cleaner layout, a new font, and a refined dark theme. →

**Tweet 3**
New: proxy health checks. Test your proxies, see which ones are healthy or failing, auto re-check them on a schedule, and clear out all the dead ones in a single click. →

**Tweet 4**
The log panel leveled up too — Clear and Copy-all buttons, full dates on timestamps, drag-to-resize, and an option to hide debug noise so you see what matters. →

**Tweet 5**
Fixes: Cereal now starts reliably on Windows PCs that used to choke, big downloads won't crash the app anymore, Telegram alerts retry properly, and logging in is smoother. →

**Tweet 6**
Full changelog: https://cereal-automation.com/blog — feedback always welcome 🙌

---

## 4. Formal changelog

## [1.10.0] - 2026-05-25

### Added
- **A redesigned interface across the whole app.** The sidebar, Tasks, Marketplace, Proxies, Datasets, and Settings screens were all rebuilt with a cleaner layout, a new primary font, and a more polished dark theme.
- **Proxy health checks.** Test your proxies to see which are healthy or failing, with status, speed, and last-error shown per proxy. Cereal can automatically re-check them in the background on a schedule you choose (Off, every 6 hours, or every 24 hours).
- **"Delete failing proxies" bulk action.** Clear out every failing proxy in a group at once instead of removing them one by one.
- **Smarter proxy use in scripts.** Scripts now automatically skip proxies that have failed their health check.
- **A live task count on the sidebar.** The Tasks item shows how many tasks are currently running or queued.
- **Clearer Tasks toolbar.** It now shows the name and identifier of the script you've selected, instead of just a task count.
- **Filter tasks by status** and **restart all errored tasks** with a single button.
- **Log panel upgrades:** a Clear button, a Copy-all button, full dates on timestamps, drag-to-resize, and an option in Settings to hide debug entries. Logs are now kept per task, so you always see the output for the task you're viewing.
- **Marketplace badges:** verified developers get a verified indicator, scripts that offer a free trial show a clear trial label, and scripts a publisher has flagged as under maintenance show a warning dot before you install.
- **Dismissible filter chips in the Marketplace** so you can see and clear active filters at a glance.
- **Datasets details view:** click into a group to see its records, with a new "Created" column.
- **Uninstall scripts** directly from the script detail screen (now labeled "Uninstall" instead of "Remove"), with a safeguard that prevents removing a script while its tasks are still running. "My Scripts" now lives under Settings → General.

### Fixed
- Cereal could fail to start on some Windows PCs — both those with strict security policies and those with accented characters in the username. It now starts reliably.
- Large downloads could use too much memory and crash the app. Downloads now stream safely to disk with a size limit.
- The download progress bar no longer jumps around; it now fills smoothly to 100%.
- Telegram notifications could silently give up after one try. They now retry correctly.
- The "7-day free trial" label was showing on every paid script, even ones without a trial. It now only appears when a trial is actually offered.
- Renaming a proxy or dataset group now shows up immediately instead of needing a refresh.
- Fixed a startup crash and a crash when selecting text in the log viewer.
- Logging in is smoother and more reliable, and several rare freezes and slowdowns were resolved.

### Improved
- The app starts faster, especially if you have a large script library.
- Scrolling and screen updates are smoother across the redesigned screens.
- Log timestamps now include the date, so entries spanning multiple days are clear.

### Changed
- "My Scripts" moved from the main sidebar into Settings → General to keep the sidebar focused on your tasks.

### Behind the scenes
- We strengthened how your saved credentials and sensitive data are stored, and made sure passwords and tokens are never written to log files. Plus the usual round of stability, performance, and maintenance improvements.

---

## 5. Blog post

## Cereal 1.10.0 is out

This is our biggest update in a while: Cereal has a brand-new look from top to bottom, along with a set of new tools that make managing your proxies, scripts, and logs a lot easier.

### What's new

The most obvious change is the redesign. We rebuilt the entire app — the sidebar, Tasks, Marketplace, Proxies, Datasets, and Settings — with a cleaner layout, a new font, and a more refined dark theme. Everything should feel a little calmer and easier to read.

The headline new feature is **proxy health checks**. You can now test your proxies to instantly see which ones are working and which have failed, complete with their speed and last error. Cereal can re-check them automatically in the background on a schedule you pick, and a new "Delete failing proxies" button lets you clean out the dead ones in a single click. Your scripts will also automatically skip any proxy that's failing, so runs are more reliable.

We also gave the **log panel** a big upgrade: you can now clear it, copy everything at once, drag it to resize, and hide debug noise so you only see what matters. Over in the **Marketplace**, verified developers now have a badge, free-trial scripts are clearly labeled, and scripts flagged as under maintenance show a warning before you install.

### Fixes and improvements

We fixed Cereal failing to start on certain Windows PCs, stopped large downloads from crashing the app, and made Telegram notifications retry properly instead of silently giving up. Logging in is smoother, the app starts faster if you have a big script library, and we resolved several rare freezes along the way. We also strengthened how your saved credentials and sensitive data are protected behind the scenes.

### How to update

Update through the Cereal desktop app — it'll prompt you on next launch, or you can check for updates manually from the settings menu.

Thanks for all the feedback that shaped this release. We hope you enjoy the new look! 🥣

[Full changelog: https://cereal-automation.com/blog]
