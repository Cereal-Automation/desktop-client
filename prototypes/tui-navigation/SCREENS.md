# PROTOTYPE: TUI navigation and screens (throwaway)

Answers **Prototype TUI navigation and screens** (map: Headless mode with a TUI). Not production code, not Kotter: a stdlib Python stand-in that behaves like a Kotter TUI (one active section, re-rendered on every change, clipped to the terminal height, repainted on resize, keys via `onKeyPressed`, hand-rolled list selection).

```sh
python3 tui_prototype.py                    # fake tasks that tick; 1-4 switch tabs, arrows/j/k, Enter, Esc, q
python3 tui_prototype.py --update=available
python3 tui_prototype.py --update=required
python3 tui_prototype.py --dump             # regenerates the screens below (80x24)
```

The verdict lives on the ticket's resolution comment. Every screen below is 80x24, the smallest size the TUI targets.

### Tasks (home)

```text
[1 Tasks] 2 Waiting 3!  3 Proxies  4 Settings                 4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Default
   Nike SNKRS Monitor  (2/2 running)
     ▶ #1  Running  Checking 12 products
 >   ▶ #2  Running  Waiting for browser interaction... [! BROWSER]
   Zalando Restock  (1/3 running)
     ▶ #1  Running  Waiting for input [! INPUT]
     · #2  Idle     Task was interrupted because the app
     ✗ #3  Error    Proxy 45.12.0.9 refused connection
 Drops
   Footlocker Raffle  (1/2 running)
     ✓ #1  Success  Entered raffle
     ▶ #2  Running  Press continue after checking the en [! CONTINUE]







────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter open  s start  x stop  S/X all in script  n new task
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Task detail: browser prompt

```text
[1 Tasks] 2 Waiting 3!  3 Proxies  4 Settings                 4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Default / Nike SNKRS Monitor / task #2
 Status: ▶ Running - Waiting for browser interaction...

 ! Waiting for browser interaction: Solve captcha on nike.com
   Open http://<server-ip>:6080/vnc.html
   Password: k7Qm-2xPa
   Finish it in the Chrome window there. Only way to cancel: x (stop task).

 Logs [ALL]  (f cycles ALL/INFO/WARN/ERR)
   INFO Started
   WARN Captcha shown
   INFO Waiting for browser interaction...







────────────────────────────────────────────────────────────────────────────────
 Esc back  s start  x stop  i input  c continue  f filter  e edit config
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Task detail: text input answered inline

```text
[1 Tasks] 2 Waiting 3!  3 Proxies  4 Settings                 4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Default / Zalando Restock / task #1
 Status: ▶ Running - Waiting for input

 ! 2FA code: Enter the code sent to your phone
   i  type the answer

 Logs [ALL]  (f cycles ALL/INFO/WARN/ERR)
   INFO Started
   INFO Logging in
   INFO Waiting for input

────────────────────────────────────────────────────────────────────────────────
 2FA code: Enter the code sent to your phone
 > 4821_
 Enter submit  Esc cancel (task keeps waiting)




────────────────────────────────────────────────────────────────────────────────
 Esc back  s start  x stop  i input  c continue  f filter  e edit config
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Waiting (every pending user interaction, by task)

```text
 1 Tasks [2 Waiting 3!] 3 Proxies  4 Settings                 4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Tasks waiting for you (browser prompts share one noVNC display):

 > BROWSER  Nike SNKRS Monitor #2  - Solve captcha on nike.com
   INPUT    Zalando Restock #1  - 2FA code
   CONTINUE Footlocker Raffle #2  - continue

 noVNC: http://<server-ip>:6080/vnc.html
 Password: k7Qm-2xPa
 Each prompt is its own Chrome window, titled with script + task number.










────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter answer/open task  x stop task
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Proxies

```text
 1 Tasks  2 Waiting 3! [3 Proxies] 4 Settings                 4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Proxy groups (stored encrypted)

 > Residential EU               3 proxies  1 failing
   ISP US (MarsProxies)         1 proxies  0 failing















────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter open  a add group (paste list)  d delete  r rename
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Proxy group

```text
 1 Tasks  2 Waiting 3! [3 Proxies] 4 Settings                 4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Proxies / Residential EU

   45.12.0.8:8000  ok 120ms
   45.12.0.9:8000  FAIL
   45.12.0.10:8000 ok 98ms














────────────────────────────────────────────────────────────────────────────────
 Esc back  t test all  D delete failing  a append (paste)  d delete
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Settings

```text
 1 Tasks  2 Waiting 3!  3 Proxies [4 Settings]                4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Notifications
     Telegram                   on  - chat 123456789
 >   Webhook (Discord)          on  - https://discord.com/api/webhooks/...***
     Email                      off
     Send test message          Enter
 General
     Check for updates          Enter
     Installed scripts          3 scripts
     Logs directory             /home/cereal/Cereal/Logs
     Discord activity status    n/a in headless mode
 Get in touch
     Discord                    https://discord.gg/...
     Website                    https://cereal-automation.com






────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter toggle/edit (same inline fields as script config)
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Quit with tasks running

```text
[1 Tasks] 2 Waiting 3!  3 Proxies  4 Settings                 4 running  v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Default
   Nike SNKRS Monitor  (2/2 running)
 >   ▶ #1  Running  Checking 12 products
     ▶ #2  Running  Waiting for browser interaction... [! BROWSER]
   Zalando Restock  (1/3 running)
     ▶ #1  Running  Waiting for input [! INPUT]
     · #2  Idle     Task was interrupted because the app
     ✗ #3  Error    Proxy 45.12.0.9 refused connection
 Drops
   Footlocker Raffle  (1/2 running)
     ✓ #1  Success  Entered raffle
     ▶ #2  Running  Press continue after checking the en [! CONTINUE]

────────────────────────────────────────────────────────────────────────────────
 4 task(s) running. Quitting stops them and shuts headless mode down.
 To leave it running, detach instead: Ctrl-P Ctrl-Q detaches (docker attach)
 Quit? [y/N]


────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter open  s start  x stop  S/X all in script  n new task
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Update available

```text
[1 Tasks] 2 Waiting 3!  3 Proxies  4 Settings                 4 running  v1.9.0
 Update 1.10.0 available. Press U for the upgrade commands.
────────────────────────────────────────────────────────────────────────────────
 Default
   Nike SNKRS Monitor  (2/2 running)
 >   ▶ #1  Running  Checking 12 products
     ▶ #2  Running  Waiting for browser interaction... [! BROWSER]
   Zalando Restock  (1/3 running)
     ▶ #1  Running  Waiting for input [! INPUT]
     · #2  Idle     Task was interrupted because the app
     ✗ #3  Error    Proxy 45.12.0.9 refused connection
 Drops
   Footlocker Raffle  (1/2 running)
     ✓ #1  Success  Entered raffle
     ▶ #2  Running  Press continue after checking the en [! CONTINUE]






────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter open  s start  x stop  S/X all in script  n new task
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Required update (blocks everything)

```text
 Cereal 1.9.0 - headless

 Update required: 1.10.0. No tasks can start on this version.

 Upgrade:
   docker pull ghcr.io/cereal-automation/cereal:latest
   docker rm -f cereal
   docker run -dit --name cereal -p 6080:6080 \
     -v cereal:/home/cereal/Cereal ghcr.io/cereal-automation/cereal:latest --hea

 (AppImage: download the new Cereal AppImage and restart it with --headless)











────────────────────────────────────────────────────────────────────────────────
 q quit
```

