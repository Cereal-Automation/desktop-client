#!/usr/bin/env python3
"""PROTOTYPE - throwaway. Answers "How do script configuration forms work in the TUI?"
(map: Headless mode with a TUI, ticket: Prototype script configuration forms in the terminal).

Same stand-in style as prototypes/tui-navigation: stdlib Python, not Kotter. It only does what
Kotter can: one section re-rendered on every change, clipped to `height`, keys via onKeyPressed,
hand-rolled list selection, and a single-line input() at the bottom of the section.

One form renderer serves every form: the new-task configuration, a list row, the per-script
notification overrides, and Settings. The item types mirror ConfigItemType in the client.

Run:   python3 forms_prototype.py          (starts on Tasks; n = new task)
       python3 forms_prototype.py --dump   (prints every screen at 80x24, for SCREENS.md)
"""
import os, select, shutil, sys, termios, tty

HOME = "/home/cereal/Cereal"
DETACH = "Ctrl-P Ctrl-Q detaches (docker attach)"

# ---- field definitions (mirror ScriptConfigurationItemDefinition) -------------------------------
# kind: str int float double secret bool enum proxy dataset list sub header

def F(key, name, kind, required=False, desc="", **kw):
    return dict(key=key, name=name, kind=kind, required=required, desc=desc, **kw)

TASK_DATA_FIELDS = [F("email", "Account email", "str", True), F("password", "Account password", "secret", True),
                    F("size", "Shoe size", "int")]
TARGET_FIELDS = [F("product", "Product", "str", True), F("qty", "Quantity", "int", True),
                 F("size", "Size", "enum", options=["40", "41", "42", "43"])]
CHANNEL_FIELDS = [
    F("h1", "Discord webhook", "header"),
    F("d_on", "Enabled", "bool"), F("d_url", "Webhook URL", "secret", desc="Treated as a credential: masked."),
    F("h2", "Telegram", "header"),
    F("t_on", "Enabled", "bool"), F("t_token", "Bot token", "secret"), F("t_chat", "Chat id", "str"),
    F("h3", "Email", "header"),
    F("e_on", "Enabled", "bool"), F("e_host", "SMTP host", "str"), F("e_port", "SMTP port", "int"),
    F("e_user", "Username", "str"), F("e_pw", "Password", "secret"), F("e_from", "From", "str"),
    F("e_to", "To", "str"), F("e_tls", "Use TLS", "bool"),
]
SNKRS = [
    F("h0", "Nike SNKRS Monitor", "header"),
    F("mode", "Mode", "enum", True, "MONITOR only watches; CHECKOUT also buys.", options=["MONITOR", "CHECKOUT"]),
    F("max_price", "Max price", "float", desc="Only asked in CHECKOUT mode (a stateModifier).",
      visible=lambda v: v.get("mode") == "CHECKOUT", required_if=lambda v: v.get("mode") == "CHECKOUT"),
    F("api_key", "Monitor API key", "secret", True, "Key from your monitor provider. Never shown again."),
    F("delay", "Retry delay (s)", "double", desc="Default 2.5 (from the script)."),
    F("notify_restock", "Notify on restock", "bool"),
    F("proxy", "Proxy", "proxy", desc="A proxy group. Each task gets one proxy from it."),
    F("task_data", "Task data", "dataset", True, "One task per record. Fields: Account email*, Account password*, "
      "Shoe size", fields=TASK_DATA_FIELDS),
    F("targets", "Targets", "list", desc="Rows of Product*, Quantity*, Size. Import replaces all rows.",
      fields=TARGET_FIELDS),
    F("tasks", "Concurrent tasks", "int", True, "1-25. Shown because the script has a group item.", max=25),
    F("notif", "Notification overrides", "sub", desc="Per-script channel overrides.", fields=CHANNEL_FIELDS),
    F("h9", "Checkout helper (child script)", "header"),
    F("profile", "Payment profile name", "str"),
]
DEFAULTS = {"delay": 2.5, "notify_restock": False, "tasks": 1, "mode": "MONITOR", "e_tls": True, "e_port": 587}
SCRIPTS = [("Nike SNKRS Monitor", "1.4.2", SNKRS), ("Zalando Restock", "2.0.0", []), ("Footlocker Raffle", "0.9.1", [])]

S = {
    "proxies": [("Residential EU", 3), ("ISP US (MarsProxies)", 1)],
    "datasets": [("SNKRS accounts", 12, ["Account email", "Account password", "Shoe size"])],
    "settings": {"d_on": True, "d_url": "https://discord.com/api/webhooks/123/abc", "t_on": False, "e_on": False,
                 "e_tls": True, "e_port": 587},
    "stack": [], "modal": None, "buf": "", "err": None, "flash": None,
}

MASK = "••••••"

# ---- values, visibility, validation -------------------------------------------------------------

def visible(fields, vals):
    return [f for f in fields if f.get("visible", lambda v: True)(vals)]

def is_required(f, vals):
    return f["required"] or f.get("required_if", lambda v: False)(vals)

def value_of(f, vals):
    return vals.get(f["key"], DEFAULTS.get(f["key"]))

def show(f, vals):
    v = value_of(f, vals)
    k = f["kind"]
    if k == "sub":
        on = [n for n in ("Discord", "Telegram", "Email") if vals.get(f["key"], {}).get(n[0].lower() + "_on")]
        return ", ".join(on) + " override" if on else "off  (uses Settings)"
    if k == "list":
        rows = vals.get(f["key"], [])
        return f"{len(rows)} row(s)" if rows else "no rows"
    if v is None or v == "":
        return "not set"
    if k == "secret":
        return MASK + " set"                      # never echoed, not even its length
    if k == "bool":
        return "[x] yes" if v else "[ ] no"
    if k == "enum":
        return f"‹ {v} ›"
    if k in ("proxy", "dataset"):
        return f"{v[0]} ({v[1]} {'proxies' if k == 'proxy' else 'records'})"
    return str(v)

def problem(f, vals):
    v = value_of(f, vals)
    if f["kind"] in ("header", "sub"):
        return None
    if f["kind"] == "list":
        rows = vals.get(f["key"], [])
        bad = [i + 1 for i, r in enumerate(rows) if any(problem(x, r) for x in f["fields"])]
        return f"row {bad[0]} is incomplete" if bad else None
    if is_required(f, vals) and (v is None or v == ""):
        return "required"
    if f.get("max") and isinstance(v, int) and not 1 <= v <= f["max"]:
        return f"must be 1-{f['max']}"
    if f["kind"] == "dataset" and v and v[2][:2] != ["Account email", "Account password"]:
        return "dataset lacks required fields"
    return None

def parse(f, text):
    """Mirrors ScriptConfigurationItemDefinition.parseValue / describeRejection. Returns (value, error)."""
    t = text.strip()
    if t == "":
        return None, None
    try:
        if f["kind"] == "int":
            return int(t), None
        if f["kind"] in ("float", "double"):
            return float(t), None
    except ValueError:
        return None, f"'{t}' is not a {'whole number' if f['kind'] == 'int' else 'number'}."
    return text, None

# ---- screens ------------------------------------------------------------------------------------

def top():
    return S["stack"][-1]

def push(**scr):
    scr.setdefault("sel", 0)
    S["stack"].append(scr)

def pop():
    S["stack"].pop()
    S["modal"] = None

def form_rows(scr):
    fields, vals = visible(scr["fields"], scr["vals"]), scr["vals"]
    rows, sel_i, i = [], None, 0
    for f in fields:
        if f["kind"] == "header":
            rows.append(f" {f['name']}")
            continue
        cur = ">" if i == scr["sel"] else " "
        star = "*" if is_required(f, vals) else " "
        bad = problem(f, vals) if scr.get("checked") else None
        rows.append(f" {cur}{star} {f['name'][:24]:<24} {show(f, vals)[:34]:<34}{' ! ' + bad if bad else ''}")
        if i == scr["sel"]:
            sel_i = f
        i += 1
    return rows, i, sel_i

def selectable(scr):
    return [f for f in visible(scr["fields"], scr["vals"]) if f["kind"] != "header"]

def body():
    scr = top()
    k = scr["kind"]
    if k == "tasks":
        return [" Default", "   Nike SNKRS Monitor  (2/2 running)", " > (cursor on group Default)", "",
                " n  new task in this group      (Enter on a script row: v view config, D duplicate)"], \
               "n new task  v view config  D duplicate"
    if k == "pick_script":
        rows = [f" New task in group Default. Step 1 of 2: pick an installed script", ""]
        for i, (name, ver, _) in enumerate(SCRIPTS):
            rows.append(f" {'>' if i == scr['sel'] else ' '} {name:<30} v{ver}")
        rows += ["", " Only installed scripts. Buying scripts happens on the desktop or the web."]
        return rows, "↑↓ select  Enter configure  r refresh scripts  Esc cancel"
    if k == "form":
        rows, _, f = form_rows(scr)
        head = [f" {scr['title']}" + ("   * required" if scr.get("mode") != "settings" else ""), ""]
        desc = ["", " " + ((f or {}).get("desc") or " ")[:78]]
        if scr.get("checked"):
            n = sum(bool(problem(x, scr["vals"])) for x in selectable(scr))
            desc.append(f" ! {n} problem(s). Fix the rows marked ! first." if n else " ✓ ready")
        keys = {"settings": "↑↓/Tab field  Enter edit (saves)  Space toggle  ←→ choose  T send test",
                "row": "↑↓/Tab field  Enter edit  Space toggle  ←→ choose  Esc done",
                "view": "Esc back  D duplicate into a new task (the only way to change a config)"}.get(
            scr.get("mode"), "↑↓/Tab field  Enter edit  Space toggle  ←→ choose  s start  c copy  Esc cancel")
        return head + rows + desc, keys
    if k == "rows":
        f, rows = scr["field"], scr["vals"].setdefault(scr["field"]["key"], [])
        out = [f" {scr['title']} / {f['name']}   fields: " + ", ".join(x["name"] + ("*" if x["required"] else "")
                                                                   for x in f["fields"]), ""]
        for i, r in enumerate(rows):
            bad = [x["name"] for x in f["fields"] if problem(x, r)]
            desc = ", ".join(f"{x['name']}={r.get(x['key'], '-')}" for x in f["fields"])
            out.append(f" {'>' if i == scr['sel'] else ' '} {i + 1:>2}  {desc[:52]}{'  ! missing ' + bad[0] if bad else ''}")
        if not rows:
            out.append("   (no rows)")
        return out, "a add row  Enter edit row  d delete row  i import CSV (replaces)  Esc back"
    if k == "pick_group":
        f = scr["field"]
        opts = group_options(f)
        out = [f" {f['name']}: pick a {'proxy group' if f['kind'] == 'proxy' else 'dataset'}", ""]
        for i, o in enumerate(opts):
            out.append(f" {'>' if i == scr['sel'] else ' '} {o}")
        out += ["", "   Proxy groups are the ones on the Proxies tab." if f["kind"] == "proxy"
                else "   Datasets are only created here. There is no Datasets tab."]
        return out, "↑↓ select  Enter pick  Esc back"
    if k == "proxies":
        out = [" Proxy groups (stored encrypted)", ""]
        for i, (n, c) in enumerate(S["proxies"]):
            out.append(f" {'>' if i == scr['sel'] else ' '} {n:<28} {c} proxies")
        return out, "↑↓ select  Enter open  a add group (paste or file)  d delete"
    return [], ""

def group_options(f):
    src = S["proxies"] if f["kind"] == "proxy" else S["datasets"]
    none = ["(none)"] if not f["required"] else []
    what = "proxy list" if f["kind"] == "proxy" else "CSV"
    return none + [f"{g[0]}  ({g[1]})" for g in src] + [f"+ new from pasted {what}", f"+ new from a file in the volume"]

# ---- modals (the tail of the section: Kotter input() line, confirmations) ------------------------

def modal():
    m = S["modal"]
    if not m:
        return []
    kind = m["kind"]
    err = [f" ! {S['err']}"] if S["err"] else []
    if kind == "input":
        f = m["field"]
        shown = "*" * len(S["buf"]) if f["kind"] == "secret" else S["buf"]
        hint = (" Typed as *. Enter with nothing typed keeps the current value." if f["kind"] == "secret"
                else " Enter save  Esc cancel  (empty clears an optional value)")
        return [f" {f['name']} ({f['kind']}){' *' if f['required'] else ''}", f" > {shown}_"] + err + [hint]
    if kind == "paste":
        lines = len([l for l in S["buf"].split("\n") if l.strip()])
        head = m["header"]
        return [f" Paste the {m['what']} now (bracketed paste), then Ctrl-D.  {lines} line(s) so far",
                *([f" First line must be the header:  {head}"] if head else [" One proxy per line: host:port or host:port:user:pass"]),
                " Esc cancel"]
    if kind == "path":
        return [f" File in the volume (relative to {HOME}):", f" > {S['buf']}_"] + err + \
               [" e.g. Import/accounts.csv  - copy it in with: docker cp accounts.csv cereal:" + HOME + "/Import/"]
    if kind == "confirm":
        return [" " + l for l in m["text"]] + [" [y/N]"]
    if kind == "copy":
        return [" Copy values from an existing instance of this script:", " > Nike SNKRS Monitor #1 (Default)",
                " Enter copy (secrets are copied too, still masked)  Esc cancel"]
    return []

def frame(width=80, height=24):
    tab = {"tasks": 0, "pick_script": 0, "form": 0, "rows": 0, "pick_group": 0, "proxies": 2}[top()["kind"]]
    if top().get("mode") == "settings":
        tab = 3
    names = ["1 Tasks", "2 Waiting", "3 Proxies", "4 Settings", "5 Notifications"]
    tabs = "".join(f"[{n}]" if i == tab else f" {n} " for i, n in enumerate(names))
    head = [tabs + "v1.9.0".rjust(max(width - len(tabs) - 1, 0)), "─" * width]
    if S["flash"]:
        head.append(" " + S["flash"])
    b, keys = body()
    m = modal()
    footer = [keys, f"q quit (stops all tasks)  ·  {DETACH}"]
    room = height - 1 - len(footer)
    tail = (["─" * width] + m) if m else []
    # Too tall: drop body lines farthest from the cursor row, so the field being edited and the
    # input line at the tail stay visible (a long form scrolls with the cursor).
    cur = next((i for i, l in enumerate(b) if l.startswith(" >")), 0)
    keep = sorted(sorted(range(len(b)), key=lambda i: abs(i - cur))[:max(room - len(head) - len(tail), 0)])
    lines = [l[:width] for l in head + [b[i] for i in keep] + tail]
    lines += [""] * (room - len(lines))
    return lines + ["─" * width] + [" " + f[:width - 1] for f in footer]

# ---- actions ------------------------------------------------------------------------------------

def open_form(title, fields, vals=None, mode=None):
    push(kind="form", title=title, fields=fields, vals=dict(vals or {}), mode=mode)

def commit(scr, f, value):
    scr["vals"][f["key"]] = value
    if scr.get("mode") == "settings":
        S["settings"] = scr["vals"]
        S["flash"] = f"Saved {f['name']}."        # SaveAllNotificationSettingsInteractor on every commit

def import_done(field, scr, text):
    """Pasted or file text becomes a temp file -> the existing File-based import interactors."""
    lines = [l for l in text.splitlines() if l.strip()]
    if field["kind"] == "proxy":
        bad = [i + 1 for i, l in enumerate(lines) if l.count(":") not in (1, 3)]
        if bad:
            S["err"] = f"Line {bad[0]}: not host:port or host:port:user:pass. Nothing imported."
            return False
        name = f"Imported {len(S['proxies']) + 1}"
        S["proxies"].insert(0, (name, len(lines)))
        if scr:
            commit(scr, field, (name, len(lines)))
        S["flash"] = f"Created proxy group '{name}' with {len(lines)} proxies."
        return True
    header = [h.strip() for h in lines[0].split(",")] if lines else []
    want = [x["name"] for x in field["fields"]]
    if header != want:
        S["err"] = f"Header must be: {','.join(want)}"
        return False
    rows = lines[1:]
    if field["kind"] == "list":
        scr["vals"][field["key"]] = [dict(zip([x["key"] for x in field["fields"]], r.split(","))) for r in rows]
        S["flash"] = f"Replaced the list with {len(rows)} row(s)."
    else:
        name = f"Dataset {len(S['datasets']) + 1}"
        S["datasets"].insert(0, (name, len(rows), header))
        commit(scr, field, (name, len(rows), header))
        S["flash"] = f"Created dataset '{name}' with {len(rows)} records, selected."
    return True

def csv_header(field):
    return ",".join(x["name"] for x in field["fields"]) if field.get("fields") else None

def key(k):
    scr, m = top(), S["modal"]
    S["flash"] = None if not m else S["flash"]
    if m:
        return modal_key(k, scr, m)
    if k in ("q", "\x03"):
        return False
    if k == "\x1b":
        if len(S["stack"]) > 1:
            pop()
        return True
    if scr["kind"] == "tasks":
        if k == "n":
            push(kind="pick_script")
        elif k == "v":
            open_form("Nike SNKRS Monitor (Default) - configuration, read only", SNKRS, SAMPLE_VALUES, mode="view")
        elif k == "D":
            open_form("New task (duplicate of Nike SNKRS Monitor) - configuration", SNKRS, SAMPLE_VALUES)
        elif k == "3":
            S["stack"] = [dict(kind="proxies", sel=0)]
        elif k == "4":
            S["stack"] = [dict(kind="tasks", sel=0)]; open_form("Settings", CHANNEL_FIELDS, S["settings"], "settings")
        return True
    if k in ("\x1b[A", "k", "\x1b[Z"):
        scr["sel"] = max(0, scr["sel"] - 1)
        return True
    count = {"form": lambda: len(selectable(scr)), "pick_script": lambda: len(SCRIPTS),
             "rows": lambda: len(scr["vals"].get(scr["field"]["key"], [])),
             "pick_group": lambda: len(group_options(scr["field"])), "proxies": lambda: len(S["proxies"])}[scr["kind"]]()
    if k in ("\x1b[B", "j", "\t"):
        scr["sel"] = min(max(count - 1, 0), scr["sel"] + 1)
        return True
    enter = k in ("\r", "\n")
    if scr["kind"] == "pick_script" and enter:
        name, ver, fields = SCRIPTS[scr["sel"]]
        open_form(f"New task in Default / {name} v{ver}. Step 2 of 2: configuration", fields or SNKRS)
    elif scr["kind"] == "form":
        form_key(k, scr, enter)
    elif scr["kind"] == "rows":
        rows = scr["vals"].setdefault(scr["field"]["key"], [])
        if k == "a":
            rows.append({}); open_form(f"{scr['field']['name']} / row {len(rows)}", scr["field"]["fields"], mode="row")
            top()["vals"] = rows[-1]
        elif enter and rows:
            open_form(f"{scr['field']['name']} / row {scr['sel'] + 1}", scr["field"]["fields"], mode="row")
            top()["vals"] = rows[scr["sel"]]
        elif k == "d" and rows:
            rows.pop(scr["sel"]); scr["sel"] = max(0, scr["sel"] - 1)
        elif k == "i":
            if rows:
                S["modal"] = dict(kind="confirm", text=[f"Importing replaces the {len(rows)} row(s) you entered."],
                                  then=lambda: start_paste(scr["field"], scr))
            else:
                start_paste(scr["field"], scr)
    elif scr["kind"] == "pick_group" and enter:
        f, form = scr["field"], S["stack"][-2]
        opts = group_options(f)
        o = opts[scr["sel"]]
        if o.startswith("+ new from pasted"):
            start_paste(f, form, pop_after=True)
        elif o.startswith("+ new from a file"):
            S["buf"], S["err"] = "", None
            S["modal"] = dict(kind="path", field=f, form=form)
        else:
            src = S["proxies"] if f["kind"] == "proxy" else S["datasets"]
            commit(form, f, None if o == "(none)" else src[scr["sel"] - (0 if f["required"] else 1)])
            pop()
    elif scr["kind"] == "proxies" and k == "a":
        start_paste(dict(key="p", name="New proxy group", kind="proxy"), None)
    return True

def start_paste(field, form, pop_after=False):
    S["buf"], S["err"] = "", None
    S["modal"] = dict(kind="paste", field=field, form=form, pop_after=pop_after,
                      what="proxy list" if field["kind"] == "proxy" else "CSV", header=csv_header(field))

def form_key(k, scr, enter):
    fields = selectable(scr)
    if not fields:
        return
    f = fields[min(scr["sel"], len(fields) - 1)]
    vals = scr["vals"]
    if scr.get("mode") == "view":
        if k == "D":
            pop(); open_form("New task (duplicate) - configuration", SNKRS, SAMPLE_VALUES)
        return
    if k == " " and f["kind"] == "bool":
        commit(scr, f, not value_of(f, vals))
    elif k in ("\x1b[C", "\x1b[D", "l", "h") and f["kind"] == "enum":
        opts = ([None] if not is_required(f, vals) else []) + f["options"]
        i = opts.index(value_of(f, vals)) if value_of(f, vals) in opts else -1
        commit(scr, f, opts[(i + (1 if k in ("\x1b[C", "l") else -1)) % len(opts)])
    elif enter:
        if f["kind"] in ("str", "int", "float", "double", "secret"):
            v = value_of(f, vals)
            S["buf"] = "" if f["kind"] == "secret" or v is None else str(v)
            S["err"], S["modal"] = None, dict(kind="input", field=f, form=scr)
        elif f["kind"] in ("proxy", "dataset"):
            push(kind="pick_group", field=f)
        elif f["kind"] == "list":
            push(kind="rows", field=f, title=scr["title"].split(".")[0], vals=vals)
        elif f["kind"] == "sub":
            open_form(f["name"], f["fields"], vals.get(f["key"], {}), mode="row")
            vals[f["key"]] = top()["vals"]
    elif k == "c" and scr.get("mode") is None:
        S["modal"] = dict(kind="copy", form=scr)
    elif k == "s" and scr.get("mode") is None:
        scr["checked"] = True
        bad = [i for i, x in enumerate(fields) if problem(x, vals)]
        if bad:
            scr["sel"] = bad[0]
        elif not (S["settings"].get("d_on") or S["settings"].get("t_on") or S["settings"].get("e_on")):
            S["modal"] = dict(kind="confirm", text=["No notification channel is set up, so nobody hears about a",
                                                    "waiting task. Start anyway?"], then=launched)
        else:
            launched()
    elif k == "T" and scr.get("mode") == "settings":
        S["flash"] = "Test message sent to Discord."

def launched():
    S["stack"] = [dict(kind="tasks", sel=0)]
    S["flash"] = "Started Nike SNKRS Monitor with 5 tasks (StartScriptInteractor)."

def modal_key(k, scr, m):
    kind = m["kind"]
    if k == "\x1b":
        S["modal"], S["err"] = None, None
        return True
    if kind == "confirm":
        S["modal"] = None
        if k.lower() == "y":
            m["then"]()
        return True
    if kind == "copy":
        if k in ("\r", "\n"):
            m["form"]["vals"].update(SAMPLE_VALUES); S["modal"] = None; S["flash"] = "Copied from #1."
        return True
    if kind == "paste":
        if k == "\x04":
            ok = import_done(m["field"], m["form"], S["buf"])
            if ok:
                S["modal"] = None
                if m["pop_after"]:
                    pop()
            else:
                S["modal"] = dict(m, kind="path_err") if False else m
        elif k == "\x7f":
            S["buf"] = S["buf"][:-1]
        else:
            S["buf"] += k.replace("\r", "\n")
        return True
    if k in ("\r", "\n"):
        if kind == "input":
            f = m["field"]
            if f["kind"] == "secret" and S["buf"] == "":
                S["modal"] = None                 # keep the current secret
                return True
            v, err = parse(f, S["buf"])
            if err:
                S["err"] = err                    # stays open, nothing saved
                return True
            commit(m["form"], f, v)
            S["modal"], S["err"] = None, None
        elif kind == "path":
            path = os.path.join(HOME, S["buf"])
            S["err"] = f"No such file in the volume: {path}"  # prototype: the volume isn't mounted here
        return True
    if k == "\x7f":
        S["buf"] = S["buf"][:-1]
    elif k.isprintable():
        S["buf"] += k
    return True

SAMPLE_VALUES = {"mode": "CHECKOUT", "max_price": 180.0, "api_key": "sk-live-123", "delay": 2.5,
                 "notify_restock": True, "proxy": ("Residential EU", 3), "tasks": 5,
                 "task_data": ("SNKRS accounts", 12, ["Account email", "Account password", "Shoe size"]),
                 "targets": [{"product": "Air Max 1", "qty": "1", "size": "42"}, {"product": "Dunk Low", "qty": "2"}]}

# ---- main ---------------------------------------------------------------------------------------

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
    S["stack"] = [dict(kind="tasks", sel=0)]
    if "--dump" in sys.argv:
        return dump()
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        draw()
        while key(read_key(fd)):
            draw()
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)
        sys.stdout.write("\x1b[H\x1b[2J")

def dump():
    def shot(title, *keys, reset=True, text=None):
        if reset:
            S["stack"], S["modal"], S["flash"], S["err"] = [dict(kind="tasks", sel=0)], None, None, None
        for k in keys:
            key(k)
        if text is not None:
            S["buf"] = text
        print(f"### {title}\n\n```text\n" + "\n".join(l.rstrip() for l in frame()) + "\n```\n")
    down = "\x1b[B"
    shot("New task, step 1: pick an installed script", "n")
    shot("New task, step 2: the empty form (defaults filled, * = required)", "n", "\r")
    shot("Enum with a stateModifier: CHECKOUT reveals the required Max price", "n", "\r", "\x1b[C", down)
    shot("Number field: a bad value is rejected in the input line", "n", "\r", down, down, "\r", text="2,5")
    shot("…and Enter keeps the line open with the reason", "\r", reset=False)
    shot("Secret field: typed as *, never shown again", "n", "\r", down, "\r", text="sk-live-123")
    shot("Picking a proxy group (from the Proxies tab, or a new one)", "n", "\r", *[down] * 4, "\r")
    shot("Task data: paste a CSV into a new dataset", "n", "\r", *[down] * 5, "\r", down, "\r",
         text="Account email,Account password,Shoe size\na@x.com,pw1,42\nb@x.com,pw2,43\n")
    shot("Task data: or read a file from the volume", "n", "\r", *[down] * 5, "\r", down, down, "\r",
         text="Import/accounts.csv")
    open_form("New task (duplicate of Nike SNKRS Monitor)", SNKRS, SAMPLE_VALUES)
    S["stack"] = S["stack"][-1:]
    S["stack"][0]["sel"], S["modal"], S["err"] = 7, None, None
    shot("List item: rows, each edited with the same field widgets", "\r", reset=False)
    shot("List row form", "\r", reset=False)
    shot("Start with problems: cursor jumps to the first one", "n", "\r", "s")
    shot("View configuration (read only; secrets masked)", "v")
    shot("Settings: notification channels are the same fields, saved on Enter", "4", down)
    shot("Proxies: add a group by pasting a list", "3", "a", text="45.12.0.8:8000\n45.12.0.9:8000:user:pass\n")

if __name__ == "__main__":
    main()
