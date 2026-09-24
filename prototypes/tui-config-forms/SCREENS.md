# PROTOTYPE: script configuration forms in the TUI (throwaway)

Answers **Prototype script configuration forms in the terminal** (map: Headless mode with a TUI). Not production code, not Kotter: a stdlib Python stand-in in the style of `prototypes/tui-navigation`. It has one section, a single-line input at the tail, hand-rolled lists, and keys via `onKeyPressed`. One form renderer serves the new-task form, list rows, notification overrides and Settings.

```sh
python3 forms_prototype.py          # n new task, v view config, D duplicate, 3 Proxies, 4 Settings, Esc back, q quit
python3 forms_prototype.py --dump   # regenerates the screens below (80x24)
```

The verdict lives on the ticket's resolution comment. Field types mirror `ConfigItemType`: Boolean, String, Secret, Int, Float, Double, Enum, List, Proxy/ProxyGroup, and Grouped (Task data, the custom dataset). There is no File item type: the SDK's `ScriptConfigurationDefinitionBuilder` rejects `File` return types, so "files" only enter through imports.

### New task, step 1: pick an installed script

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task in group Default. Step 1 of 2: pick an installed script

 > Nike SNKRS Monitor             v1.4.2
   Zalando Restock                v2.0.0
   Footlocker Raffle              v0.9.1

 Only installed scripts. Buying scripts happens on the desktop or the web.












────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter configure  r refresh scripts  Esc cancel
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### New task, step 2: the empty form (defaults filled, * = required)

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task in Default / Nike SNKRS Monitor v1.4.2. Step 2 of 2: configuration   *

 Nike SNKRS Monitor
 >* Mode                     ‹ MONITOR ›
  * Monitor API key          not set
    Retry delay (s)          2.5
    Notify on restock        [ ] no
    Proxy                    not set
  * Task data                not set
    Targets                  no rows
  * Concurrent tasks         1
    Notification overrides   off  (uses Settings)
 Checkout helper (child script)
    Payment profile name     not set

 MONITOR only watches; CHECKOUT also buys.



────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit  Space toggle  ←→ choose  s start  c copy  Esc cancel
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Enum with a stateModifier: CHECKOUT reveals the required Max price

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task in Default / Nike SNKRS Monitor v1.4.2. Step 2 of 2: configuration   *

 Nike SNKRS Monitor
  * Mode                     ‹ CHECKOUT ›
 >* Max price                not set
  * Monitor API key          not set
    Retry delay (s)          2.5
    Notify on restock        [ ] no
    Proxy                    not set
  * Task data                not set
    Targets                  no rows
  * Concurrent tasks         1
    Notification overrides   off  (uses Settings)
 Checkout helper (child script)
    Payment profile name     not set

 Only asked in CHECKOUT mode (a stateModifier).


────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit  Space toggle  ←→ choose  s start  c copy  Esc cancel
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Number field: a bad value is rejected in the input line

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task in Default / Nike SNKRS Monitor v1.4.2. Step 2 of 2: configuration   *

 Nike SNKRS Monitor
  * Mode                     ‹ MONITOR ›
  * Monitor API key          not set
 >  Retry delay (s)          2.5
    Notify on restock        [ ] no
    Proxy                    not set
  * Task data                not set
    Targets                  no rows
  * Concurrent tasks         1
    Notification overrides   off  (uses Settings)
 Checkout helper (child script)
    Payment profile name     not set

────────────────────────────────────────────────────────────────────────────────
 Retry delay (s) (double)
 > 2,5_
 Enter save  Esc cancel  (empty clears an optional value)
────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit  Space toggle  ←→ choose  s start  c copy  Esc cancel
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### …and Enter keeps the line open with the reason

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task in Default / Nike SNKRS Monitor v1.4.2. Step 2 of 2: configuration   *

 Nike SNKRS Monitor
  * Mode                     ‹ MONITOR ›
  * Monitor API key          not set
 >  Retry delay (s)          2.5
    Notify on restock        [ ] no
    Proxy                    not set
  * Task data                not set
    Targets                  no rows
  * Concurrent tasks         1
    Notification overrides   off  (uses Settings)
 Checkout helper (child script)
    Payment profile name     not set
────────────────────────────────────────────────────────────────────────────────
 Retry delay (s) (double)
 > 2,5_
 ! '2,5' is not a number.
 Enter save  Esc cancel  (empty clears an optional value)
────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit  Space toggle  ←→ choose  s start  c copy  Esc cancel
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Secret field: typed as *, never shown again

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task in Default / Nike SNKRS Monitor v1.4.2. Step 2 of 2: configuration   *

 Nike SNKRS Monitor
  * Mode                     ‹ MONITOR ›
 >* Monitor API key          not set
    Retry delay (s)          2.5
    Notify on restock        [ ] no
    Proxy                    not set
  * Task data                not set
    Targets                  no rows
  * Concurrent tasks         1
    Notification overrides   off  (uses Settings)
 Checkout helper (child script)
    Payment profile name     not set

────────────────────────────────────────────────────────────────────────────────
 Monitor API key (secret) *
 > ***********_
 Typed as *. Enter with nothing typed keeps the current value.
────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit  Space toggle  ←→ choose  s start  c copy  Esc cancel
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Picking a proxy group (from the Proxies tab, or a new one)

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Proxy: pick a proxy group

 > (none)
   Residential EU  (3)
   ISP US (MarsProxies)  (1)
   + new from pasted proxy list
   + new from a file in the volume

   Proxy groups are the ones on the Proxies tab.










────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter pick  Esc back
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Task data: paste a CSV into a new dataset

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Task data: pick a dataset

   SNKRS accounts  (12)
 > + new from pasted CSV
   + new from a file in the volume

   Datasets are only created here. There is no Datasets tab.
────────────────────────────────────────────────────────────────────────────────
 Paste the CSV now (bracketed paste), then Ctrl-D.  3 line(s) so far
 First line must be the header:  Account email,Account password,Shoe size
 Esc cancel








────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter pick  Esc back
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Task data: or read a file from the volume

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Task data: pick a dataset

   SNKRS accounts  (12)
   + new from pasted CSV
 > + new from a file in the volume

   Datasets are only created here. There is no Datasets tab.
────────────────────────────────────────────────────────────────────────────────
 File in the volume (relative to /home/cereal/Cereal):
 > Import/accounts.csv_
 e.g. Import/accounts.csv  - copy it in with: docker cp accounts.csv cereal:/hom








────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter pick  Esc back
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### List item: rows, each edited with the same field widgets

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task (duplicate of Nike SNKRS Monitor) / Targets   fields: Product*, Quanti

 >  1  Product=Air Max 1, Quantity=1, Size=42
    2  Product=Dunk Low, Quantity=2, Size=-















────────────────────────────────────────────────────────────────────────────────
 a add row  Enter edit row  d delete row  i import CSV (replaces)  Esc back
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### List row form

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Targets / row 1   * required

 >* Product                  Air Max 1
  * Quantity                 1
    Size                     ‹ 42 ›














────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit  Space toggle  ←→ choose  Esc done
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Start with problems: cursor jumps to the first one

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 New task in Default / Nike SNKRS Monitor v1.4.2. Step 2 of 2: configuration   *

 Nike SNKRS Monitor
  * Mode                     ‹ MONITOR ›
 >* Monitor API key          not set                            ! required
    Retry delay (s)          2.5
    Notify on restock        [ ] no
    Proxy                    not set
  * Task data                not set                            ! required
    Targets                  no rows
  * Concurrent tasks         1
    Notification overrides   off  (uses Settings)
 Checkout helper (child script)
    Payment profile name     not set

 Key from your monitor provider. Never shown again.
 ! 2 problem(s). Fix the rows marked ! first.


────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit  Space toggle  ←→ choose  s start  c copy  Esc cancel
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### View configuration (read only; secrets masked)

```text
[1 Tasks] 2 Waiting  3 Proxies  4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Nike SNKRS Monitor (Default) - configuration, read only   * required

 Nike SNKRS Monitor
 >* Mode                     ‹ CHECKOUT ›
  * Max price                180.0
  * Monitor API key          •••••• set
    Retry delay (s)          2.5
    Notify on restock        [x] yes
    Proxy                    Residential EU (3 proxies)
  * Task data                SNKRS accounts (12 records)
    Targets                  2 row(s)
  * Concurrent tasks         5
    Notification overrides   off  (uses Settings)
 Checkout helper (child script)
    Payment profile name     not set

 MONITOR only watches; CHECKOUT also buys.


────────────────────────────────────────────────────────────────────────────────
 Esc back  D duplicate into a new task (the only way to change a config)
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Settings: notification channels are the same fields, saved on Enter

```text
 1 Tasks  2 Waiting  3 Proxies [4 Settings] 5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Settings

 Discord webhook
    Enabled                  [x] yes
 >  Webhook URL              •••••• set
 Telegram
    Enabled                  [ ] no
    Bot token                not set
    Chat id                  not set
 Email
    Enabled                  [ ] no
    SMTP host                not set
    SMTP port                587
    Username                 not set
    Password                 not set
    From                     not set
    To                       not set
    Use TLS                  [x] yes

────────────────────────────────────────────────────────────────────────────────
 ↑↓/Tab field  Enter edit (saves)  Space toggle  ←→ choose  T send test
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

### Proxies: add a group by pasting a list

```text
 1 Tasks  2 Waiting [3 Proxies] 4 Settings  5 Notifications              v1.9.0
────────────────────────────────────────────────────────────────────────────────
 Proxy groups (stored encrypted)

 > Residential EU               3 proxies
   ISP US (MarsProxies)         1 proxies
────────────────────────────────────────────────────────────────────────────────
 Paste the proxy list now (bracketed paste), then Ctrl-D.  2 line(s) so far
 One proxy per line: host:port or host:port:user:pass
 Esc cancel











────────────────────────────────────────────────────────────────────────────────
 ↑↓ select  Enter open  a add group (paste or file)  d delete
 q quit (stops all tasks)  ·  Ctrl-P Ctrl-Q detaches (docker attach)
```

