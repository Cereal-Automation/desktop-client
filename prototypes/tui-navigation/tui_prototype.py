#!/usr/bin/env python3
"""PROTOTYPE - throwaway. Answers "How should the headless-mode TUI look and behave?"
(map: Headless mode with a TUI, ticket: Prototype TUI navigation and screens).

Not Kotter, not Kotlin: a stdlib-only stand-in so it runs with one command and no build.
It mimics what a Kotter TUI can do: ONE active section, re-rendered on every state change,
clipped to the terminal `height`, redrawn on resize (SIGWINCH), keys via onKeyPressed.
Every list selection below is hand-rolled, because Kotter has no list/select widget.

Run:   python3 tui_prototype.py                  (fake data, fake tasks that tick)
       python3 tui_prototype.py --update=available
       python3 tui_prototype.py --update=required
       python3 tui_prototype.py --dump           (print every screen at 80x24, for SCREENS.md)
"""
import os, select, shutil, signal, sys, termios, tty, zlib

PROMPTS = "http://<server-ip>:6080"        # prompt page served by Cereal (ticket: browser prompts)
PROMPT_PW = "k7Qm-2xPa"                     # generated at boot unless CEREAL_PROMPT_PASSWORD
DETACH = "Ctrl-P Ctrl-Q detaches (docker attach)"  # or "Ctrl-B d detaches (tmux)"
VERSION, NEW_VERSION = "1.9.0", "1.10.0"
UPGRADE = ["docker pull ghcr.io/cereal-automation/cereal:latest",
           "docker rm -f cereal",
           "docker run -dit --name cereal -p 6080:6080 \\",
           "  -v cereal:/home/cereal/Cereal ghcr.io/cereal-automation/cereal:latest --headless"]

def task(n, status, msg, ask=None, logs=()):
    return {"n": n, "status": status, "msg": msg, "ask": ask, "logs": list(logs)}

S = {
    "screen": "tasks", "back": [], "sel": 0, "modal": None, "buf": "", "filter": "ALL",
    "update": None, "tick": 0,
    "groups": [
        {"name": "Default", "scripts": [
            {"title": "Nike SNKRS Monitor", "tasks": [
                task(1, "Running", "Checking 12 products", logs=["INFO Started", "INFO Checking 12 products"]),
                task(2, "Running", "Waiting for browser interaction...", ask=("browser", "Solve captcha on nike.com"),
                     logs=["INFO Started", "WARN Captcha shown", "INFO Waiting for browser interaction..."]),
            ]},
            {"title": "Zalando Restock", "tasks": [
                task(1, "Running", "Waiting for input", ask=("text", "2FA code", "Enter the code sent to your phone"),
                     logs=["INFO Started", "INFO Logging in", "INFO Waiting for input"]),
                task(2, "Idle", "Task was interrupted because the application was closed."),
                task(3, "Error", "Proxy 45.12.0.9 refused connection", logs=["ERR  Proxy 45.12.0.9 refused connection"]),
            ]},
        ]},
        {"name": "Drops", "scripts": [
            {"title": "Footlocker Raffle", "tasks": [
                task(1, "Success", "Entered raffle"),
                task(2, "Running", "Press continue after checking the entry", ask=("continue",),
                     logs=["INFO Filled form", "INFO Press continue after checking the entry"]),
            ]},
        ]},
    ],
    "proxies": [
        {"name": "Residential EU", "items": ["45.12.0.8:8000  ok 120ms", "45.12.0.9:8000  FAIL", "45.12.0.10:8000 ok 98ms"]},
        {"name": "ISP US (MarsProxies)", "items": ["23.1.4.5:3128   ok 60ms"]},
    ],
    "settings": [
        ("Notifications", None),
        ("  Telegram", "on  - chat 123456789"), ("  Webhook (Discord)", "on  - https://discord.com/api/webhooks/...***"),
        ("  Email", "off"),
        ("  Send test message", "Enter"),
        ("General", None),
        ("  Check for updates", "Enter"), ("  Installed scripts", "3 scripts"),
        ("  Logs directory", "/home/cereal/Cereal/Logs"),
        ("  Discord activity status", "n/a in headless mode"),
        ("Get in touch", None),
        ("  Discord", "https://discord.gg/..."), ("  Website", "https://cereal-automation.com"),
    ],
}

def all_tasks():
    for g in S["groups"]:
        for s in g["scripts"]:
            for t in s["tasks"]:
                yield g, s, t

def waiting():
    return [(g, s, t) for g, s, t in all_tasks() if t["ask"]]

def running():
    return [t for _, _, t in all_tasks() if t["status"] == "Running"]

# ---- screen bodies: each returns (lines, selectable_row_count) --------------------------------

ICON = {"Running": "▶", "Idle": "·", "Success": "✓", "Error": "✗"}

def ask_label(ask):
    return {"browser": "BROWSER", "text": "INPUT", "continue": "CONTINUE"}[ask[0]]

def body_tasks():
    rows, i = [], 0
    for g in S["groups"]:
        rows.append(f" {g['name']}")
        for s in g["scripts"]:
            n_run = sum(t["status"] == "Running" for t in s["tasks"])
            rows.append(f"   {s['title']}  ({n_run}/{len(s['tasks'])} running)")
            for t in s["tasks"]:
                flag = f" [! {ask_label(t['ask'])}]" if t["ask"] else ""
                cur = ">" if i == S["sel"] else " "
                rows.append(f" {cur}   {ICON[t['status']]} #{t['n']:<2} {t['status']:<8} {t['msg'][:36]}{flag}")
                i += 1
    return rows, i, "↑↓ select  Enter open  s start  x stop  S/X all in script  n new task"

def sel_task():
    return list(all_tasks())[S["sel"]]

def ask_block(s, t):
    a = t["ask"]
    if not a:
        return []
    if a[0] == "browser":
        return [f" ! Waiting for browser interaction: {a[1]}",
                f"   Open {PROMPTS}/prompt/{zlib.crc32(f'{s["title"]}#{t["n"]}'.encode()) & 0xffff:04x}",
                f"   Password: {PROMPT_PW}",
                "   Finish it on that page. Only way to cancel: x (stop task)."]
    if a[0] == "text":
        return [f" ! {a[1]}: {a[2]}", "   i  type the answer"]
    return [" ! The script is waiting for you to continue.", "   c  continue"]

def body_task():
    g, s, t = S["detail"]
    rows = [f" {g['name']} / {s['title']} / task #{t['n']}", f" Status: {ICON[t['status']]} {t['status']} - {t['msg']}", ""]
    rows += ask_block(s, t) + ([""] if t["ask"] else [])
    rows.append(f" Logs [{S['filter']}]  (f cycles ALL/INFO/WARN/ERR)")
    logs = [l for l in t["logs"] if S["filter"] == "ALL" or l.startswith(S["filter"])]
    rows += ["   " + l for l in logs] or ["   (no log lines)"]
    return rows, 0, "Esc back  s start  x stop  i input  c continue  f filter  e edit config"

def body_waiting():
    w = waiting()
    rows = [" Tasks waiting for you:", ""]
    for i, (g, s, t) in enumerate(w):
        cur = ">" if i == S["sel"] else " "
        rows.append(f" {cur} {ask_label(t['ask']):<8} {s['title']} #{t['n']}  - {t['ask'][1] if len(t['ask']) > 1 else 'continue'}")
    if any(t["ask"][0] == "browser" for _, _, t in w):
        rows += ["", f" Prompt page: {PROMPTS}/", f" Password: {PROMPT_PW}",
                 " It lists each browser prompt by script + task number."]
    if not w:
        rows.append("   Nothing is waiting.")
    return rows, len(w), "↑↓ select  Enter answer/open task  x stop task"

def body_proxies():
    rows = [" Proxy groups (stored encrypted)", ""]
    for i, p in enumerate(S["proxies"]):
        cur = ">" if i == S["sel"] else " "
        bad = sum("FAIL" in x for x in p["items"])
        rows.append(f" {cur} {p['name']:<28} {len(p['items'])} proxies  {bad} failing")
    return rows, len(S["proxies"]), "↑↓ select  Enter open  a add group (paste list)  d delete  r rename"

def body_proxy_group():
    p = S["proxies"][S["pgroup"]]
    rows = [f" Proxies / {p['name']}", ""] + ["   " + x for x in p["items"]]
    return rows, 0, "Esc back  t test all  D delete failing  a append (paste)  d delete"

def body_settings():
    rows, i = [], 0
    for label, val in S["settings"]:
        if val is None:
            rows.append(f" {label}")
            continue
        cur = ">" if i == S["sel"] else " "
        rows.append(f" {cur} {label:<28} {val}")
        i += 1
    return rows, i, "↑↓ select  Enter toggle/edit (same inline fields as script config)"

BODIES = {"tasks": body_tasks, "task": body_task, "waiting": body_waiting, "proxies": body_proxies,
          "proxy_group": body_proxy_group, "settings": body_settings}
TABS = [("1", "Tasks", "tasks"), ("2", "Waiting", "waiting"), ("3", "Proxies", "proxies"), ("4", "Settings", "settings")]

# ---- frame -------------------------------------------------------------------------------------

def frame(width, height):
    if S["update"] == "required":
        lines = [f" Cereal {VERSION} - headless", "", f" Update required: {NEW_VERSION}. No tasks can start on this version.",
                 "", " Upgrade:"] + ["   " + c for c in UPGRADE] + ["", " (AppImage: download the new Cereal AppImage and restart it with --headless)"]
        return pad(lines, ["q quit"], width, height)
    w = len(waiting())
    active = {"task": "tasks", "proxy_group": "proxies"}.get(S["screen"], S["screen"])
    tabs = "".join((f"[{k} {name}{f' {w}!' if key == 'waiting' and w else ''}]" if key == active
                    else f" {k} {name}{f' {w}!' if key == 'waiting' and w else ''} ") for k, name, key in TABS)
    status = f"{len(running())} running  v{VERSION}"
    head = [tabs + status.rjust(max(width - len(tabs) - 1, 0))]
    if S["update"] == "available":
        head.append(f" Update {NEW_VERSION} available. Press U for the upgrade commands.")
    head.append("─" * width)
    body, _, keys = BODIES[S["screen"]]()
    if S["modal"]:
        body = body + [""] + ["─" * width] + S["modal"]()
    return pad(head + body, [keys, f"q quit (stops all tasks)  ·  {DETACH}"], width, height)

def pad(lines, footer, width, height):
    lines = [l[:width] for l in lines]
    room = height - 1 - len(footer)
    if len(lines) > room:  # clip, keep the tail visible (modal/input lives there)
        lines = lines[:1] + lines[len(lines) - room + 1:]
    lines += [""] * (room - len(lines))
    return lines + ["─" * width] + [" " + f[:width - 1] for f in footer]

# ---- modals ------------------------------------------------------------------------------------

def modal_quit():
    n = len(running())
    return [f" {n} task(s) running. Quitting stops them and shuts headless mode down.",
            " To leave it running, detach instead: " + DETACH, " Quit? [y/N]"]

def modal_input():
    _, _, t = S["detail"]
    return [f" {t['ask'][1]}: {t['ask'][2]}", f" > {S['buf']}_", " Enter submit  Esc cancel (task keeps waiting)"]

def modal_upgrade():
    return [" Upgrade (headless mode never installs updates itself):"] + ["   " + c for c in UPGRADE] + [" Esc close"]

def modal_new_task():
    return [" New task: pick script ↑↓ (installed scripts only), then the configuration form.",
            " → Configuration form is the next ticket: Script configuration forms in the terminal.", " Esc close"]

# ---- keys --------------------------------------------------------------------------------------

def go(screen):
    S["back"].append((S["screen"], S["sel"]))
    S["screen"], S["sel"] = screen, 0

def key(k):
    if S["modal"] is modal_input:
        _, _, t = S["detail"]
        if k in ("\r", "\n"):
            t["logs"].append(f"INFO Input received"); t["ask"] = None; t["msg"] = "Continuing"; S["modal"] = None
        elif k == "\x1b":
            S["modal"] = None
        elif k == "\x7f":
            S["buf"] = S["buf"][:-1]
        elif k.isprintable():
            S["buf"] += k
        return True
    if S["modal"] is modal_quit:
        S["modal"] = None
        return k.lower() != "y"
    if S["modal"]:
        S["modal"] = None if k == "\x1b" else S["modal"]
        return True
    if k in ("q", "\x03"):  # Ctrl-C arrives as a key in raw mode: same confirmation, never kills PID 1 outright
        if running() and S["update"] != "required":
            S["modal"] = modal_quit
            return True
        return False
    if S["update"] == "required":
        return True
    for n, _, scr in TABS:
        if k == n:
            S["back"].clear(); S["screen"], S["sel"] = scr, 0
            return True
    if k == "U" and S["update"]:
        S["modal"] = modal_upgrade
    _, count, _ = BODIES[S["screen"]]()
    if k in ("\x1b[A", "k"):
        S["sel"] = max(0, S["sel"] - 1)
    elif k in ("\x1b[B", "j"):
        S["sel"] = min(max(count - 1, 0), S["sel"] + 1)
    elif k == "\x1b" and S["back"]:
        S["screen"], S["sel"] = S["back"].pop()
    scr = S["screen"]
    if scr in ("tasks", "task", "waiting"):
        t = (S["detail"] if scr == "task" else sel_task() if scr == "tasks" else (waiting() or [None])[S["sel"]])
        if t is None:
            return True
        g, s, tk = t
        if k in ("\r", "\n"):
            S["detail"] = t
            if scr == "waiting" and tk["ask"] and tk["ask"][0] == "text":
                go("task"); S["buf"] = ""; S["modal"] = modal_input
            elif scr != "task":
                go("task")
        elif k == "s" and tk["status"] != "Running":
            tk["status"], tk["msg"] = "Running", "Started"; tk["logs"].append("INFO Started")
        elif k == "x" and tk["status"] == "Running":
            tk["status"], tk["msg"], tk["ask"] = "Idle", "Stopped by user", None; tk["logs"].append("INFO Stopped by user")
        elif k == "i" and tk["ask"] and tk["ask"][0] == "text":
            S["detail"] = t; S["buf"] = ""; S["modal"] = modal_input
        elif k == "c" and tk["ask"] and tk["ask"][0] == "continue":
            tk["ask"], tk["msg"] = None, "Continuing"
        elif k in ("S", "X"):
            for o in s["tasks"]:
                if k == "S" and o["status"] != "Running":
                    o["status"], o["msg"] = "Running", "Started"
                if k == "X" and o["status"] == "Running":
                    o["status"], o["msg"], o["ask"] = "Idle", "Stopped by user", None
        elif k == "f":
            S["filter"] = {"ALL": "INFO", "INFO": "WARN", "WARN": "ERR", "ERR": "ALL"}[S["filter"]]
        elif k == "n":
            S["modal"] = modal_new_task
    elif scr == "proxies" and k in ("\r", "\n"):
        S["pgroup"] = S["sel"]; go("proxy_group")
    elif scr == "proxy_group" and k == "D":
        p = S["proxies"][S["pgroup"]]; p["items"] = [x for x in p["items"] if "FAIL" not in x]
    return True

# ---- main loop ---------------------------------------------------------------------------------

def draw():
    w, h = shutil.get_terminal_size((80, 24))
    sys.stdout.write("\x1b[H\x1b[2J" + "\r\n".join(frame(w, h)))
    sys.stdout.flush()

def read_key(fd):
    b = os.read(fd, 1)
    if b == b"\x1b" and select.select([fd], [], [], 0.02)[0]:
        b += os.read(fd, 2)
    return b.decode(errors="ignore")

def main():
    for a in sys.argv[1:]:
        if a.startswith("--update="):
            S["update"] = a.split("=", 1)[1]
    if "--dump" in sys.argv:
        return dump()
    if not sys.stdin.isatty():  # decided: no TTY -> refuse, name the fix
        sys.exit("Headless mode needs a terminal. Run with `docker run -it` or inside tmux/screen.")
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    signal.signal(signal.SIGWINCH, lambda *_: draw())  # full repaint = how reattach repaints
    try:
        tty.setraw(fd)
        sys.stdout.write("\x1b[?25l")
        draw()
        while True:
            if select.select([fd], [], [], 1.0)[0]:
                if not key(read_key(fd)):
                    break
            else:
                S["tick"] += 1  # fake progress so the screen visibly lives
                for _, _, t in all_tasks():
                    if t["status"] == "Running" and not t["ask"] and S["tick"] % 3 == 0:
                        t["msg"] = f"Checking 12 products (pass {S['tick'] // 3})"
            draw()
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)
        sys.stdout.write("\x1b[?25h\x1b[H\x1b[2J")
        print("Headless mode stopped.")

def dump():
    def show(title, **kw):
        for k, v in kw.items():
            S[k] = v
        print(f"### {title}\n\n```text")
        print("\n".join(l.rstrip() for l in frame(80, 24)))
        print("```\n")
    upd = S["update"]
    S["update"] = None
    show("Tasks (home)", screen="tasks", sel=1)
    S["detail"] = sel_task()
    show("Task detail: browser prompt", screen="task")
    S["detail"] = list(all_tasks())[2]
    show("Task detail: text input answered inline", screen="task", modal=modal_input, buf="4821")
    show("Waiting (every pending user interaction, by task)", screen="waiting", sel=0, modal=None)
    show("Proxies", screen="proxies", sel=0)
    S["pgroup"] = 0
    show("Proxy group", screen="proxy_group")
    show("Settings", screen="settings", sel=1)
    show("Quit with tasks running", screen="tasks", sel=0, modal=modal_quit)
    show("Update available", screen="tasks", modal=None, update="available")
    show("Required update (blocks everything)", update="required")
    S["update"] = upd

if __name__ == "__main__":
    main()
