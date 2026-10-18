#!/usr/bin/env python3
"""Driver for issue #161: child-JVM-per-sandbox memory and startup. macOS only (uses `footprint`).

Usage: python3 run.py            (paths via env vars, see README section in ../child-jvm-overhead.md)
"""
import os, re, statistics, subprocess, sys, threading, time

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(os.environ["JAVA_HOME"], "bin", "java")
STDLIB, SDK, COROUTINES = os.environ["STDLIB_JAR"], os.environ["SDK_JAR"], os.environ["COROUTINES_JAR"]
SCRIPT_JAR, SCRIPT_CLASS = os.environ["SCRIPT_JAR"], os.environ["SCRIPT_CLASS"]
OUT = os.path.join(HERE, "out")
SETTLE_S = float(os.environ.get("SETTLE_S", "3"))
COUNTS = [int(n) for n in os.environ.get("COUNTS", "1,5,20").split(",")]
REPS = {1: 5, 5: 3, 20: 2}

HARNESS_JAR = os.path.join(OUT, "harness.jar")  # CDS refuses non-empty directories on the classpath
HOST_CP = os.pathsep.join([HARNESS_JAR, STDLIB, SDK, COROUTINES])
CHILD_CP = os.pathsep.join([HOST_CP, SCRIPT_JAR])
TUNED = ["-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=1", "-Xss256k",
         "-Xms8m", "-Xmx256m", "-XX:ReservedCodeCacheSize=32m"]
CONFIGS = {  # name -> (flags, build an AppCDS archive first?)
    "default (JDK CDS on)": ([], False),
    "no CDS (-Xshare:off)": (["-Xshare:off"], False),
    "default + AppCDS": ([], True),
    "tuned": (TUNED, False),
    "tuned + AppCDS": (TUNED, True),
}


def footprint_kb(pid):
    out = subprocess.run(["footprint", "-p", str(pid)], capture_output=True, text=True).stdout
    m = re.search(r"Footprint: ([\d.]+) (KB|MB|GB)", out)
    return float(m.group(1)) * {"KB": 1, "MB": 1024, "GB": 1024 * 1024}[m.group(2)] if m else float("nan")


def rss_kb(pid):
    return float(subprocess.run(["ps", "-o", "rss=", "-p", str(pid)], capture_output=True, text=True).stdout)


def child_cmd(flags):
    return [JAVA, *flags, "-cp", CHILD_CP, "Harness", "child", SCRIPT_CLASS]


def launch(flags, n):
    """Start n children at once; return (procs, startup seconds per child)."""
    times = [None] * n
    procs = []

    def wait_ready(i, p, t0):
        line = p.stdout.readline()
        assert line.strip() == "READY", f"child {i} failed: {line!r} {p.stderr.read()}"
        times[i] = time.perf_counter() - t0

    threads = []
    for i in range(n):
        t0 = time.perf_counter()
        p = subprocess.Popen(child_cmd(flags), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, text=True)
        procs.append(p)
        th = threading.Thread(target=wait_ready, args=(i, p, t0))
        th.start()
        threads.append(th)
    for th in threads:
        th.join()
    return procs, times


def stop(procs):
    for p in procs:
        p.stdin.close()
    for p in procs:
        p.wait()


def build_archive(name, flags):
    jsa = os.path.join(OUT, re.sub(r"\W+", "_", name) + ".jsa")
    procs, _ = launch([*flags, f"-XX:ArchiveClassesAtExit={jsa}"], 1)
    stop(procs)
    assert os.path.exists(jsa), "archive not written"
    return [*flags, f"-XX:SharedArchiveFile={jsa}"]


def run_children():
    print(f"\n## Child JVMs ({os.path.basename(SCRIPT_JAR)}, {SCRIPT_CLASS})\n")
    print("| config | JVMs | startup median s | startup max s | footprint/JVM MB | footprint total MB | RSS/JVM MB | RSS total MB |")
    print("|---|---|---|---|---|---|---|---|")
    for name, (flags, cds) in CONFIGS.items():
        if cds:
            flags = build_archive(name, flags)
        for n in COUNTS:
            starts, fps, rsss = [], [], []
            for _ in range(REPS.get(n, 1)):
                procs, times = launch(flags, n)
                time.sleep(SETTLE_S)
                starts += times
                fps.append(sum(footprint_kb(p.pid) for p in procs))
                rsss.append(sum(rss_kb(p.pid) for p in procs))
                stop(procs)
            fp, rss = statistics.median(fps) / 1024, statistics.median(rsss) / 1024
            print(f"| {name} | {n} | {statistics.median(starts):.2f} | {max(starts):.2f} | "
                  f"{fp / n:.0f} | {fp:.0f} | {rss / n:.0f} | {rss:.0f} |", flush=True)


def run_inproc(packages, tasks, label):
    p = subprocess.Popen([JAVA, "-cp", HOST_CP, "Harness", "inproc", SCRIPT_JAR, SCRIPT_CLASS, str(packages), str(tasks)],
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    rows = []
    while True:
        line = p.stdout.readline()
        if not line:
            break
        time.sleep(SETTLE_S if "base" in line else 0.5)
        rows.append((line.split(" ", 1)[1].strip(), footprint_kb(p.pid) / 1024, rss_kb(p.pid) / 1024))
        p.stdin.write("\n")
        p.stdin.flush()
    p.wait()
    assert p.returncode == 0, p.stderr.read()
    base_fp, base_rss = rows[0][1], rows[0][2]
    print(f"\n## In-process baseline: {label}\n")
    print("| step | load ms | footprint MB | +footprint vs base MB | RSS MB | +RSS vs base MB |")
    print("|---|---|---|---|---|---|")
    for step, fp, rss in rows:
        kv = dict(part.split("=") for part in step.split()[0:3] if "=" in part)
        if step == "base" or max(int(kv["pkg"]), int(kv["task"])) in COUNTS:
            print(f"| {step.split(' ms=')[0]} | {kv.get('ms', '-')} | {fp:.0f} | {fp - base_fp:.0f} | {rss:.0f} | {rss - base_rss:.0f} |")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    jdk_bin = os.path.join(os.environ["JAVA_HOME"], "bin")
    classes = os.path.join(OUT, "classes")
    subprocess.run([os.path.join(jdk_bin, "javac"), "-d", classes, "-cp", HOST_CP, os.path.join(HERE, "Harness.java")], check=True)
    subprocess.run([os.path.join(jdk_bin, "jar"), "cf", HARNESS_JAR, "-C", classes, "."], check=True)
    print(subprocess.run([JAVA, "-version"], capture_output=True, text=True).stderr.strip())
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("all", "inproc"):
        run_inproc(max(COUNTS), 1, f"{max(COUNTS)} script packages x 1 task (one loader each)")
        run_inproc(1, max(COUNTS), f"1 script package x {max(COUNTS)} tasks (shared loader)")
    if what in ("all", "child"):
        run_children()
