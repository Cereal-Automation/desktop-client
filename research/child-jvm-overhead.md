# What does a child JVM per sandbox cost in memory and startup?

Research for [#161](https://github.com/Cereal-Automation/desktop-client/issues/161), part of the sandbox map [#158](https://github.com/Cereal-Automation/desktop-client/issues/158).
Measured 2026-10-18. Harness: [`child-jvm-overhead/`](child-jvm-overhead/).

## Answer

An idle child JVM running the SDK plus a real kdriver/Ktor/OkHttp script costs about **82 MB (default flags) or 46 MB (tuned flags + AppCDS)** of physical footprint. It is ready in **0.25 s (default) or 0.13 s (tuned + AppCDS)**. Loading the same script in-process costs about **38 MB for the first package, about 2.7 MB for each extra package and about 0.1 MB for each extra task**.

| 20 concurrent tasks | Total footprint | Ready (median / max) |
|---|---|---|
| In-process, 20 tasks of 1 package (today) | +40 MB | first load 0.15 s, then ~0 ms |
| In-process, 20 different packages (today) | +90 MB | 0.15 s first, ~20 ms after |
| 20 child JVMs, default flags | 1583 MB | 1.13 / 1.22 s |
| 20 child JVMs, tuned | 1092 MB | 0.81 / 0.85 s |
| 20 child JVMs, tuned + AppCDS | 942 MB | 0.44 / 0.51 s |
| 1 child JVM per package, 20 tasks inside | ~47–82 MB (one JVM + ~0.1 MB/task) | 0.13–0.25 s once |

What this means for sandbox granularity:

- **A JVM per task** adds about 45–80 MB for each task. At 20 tasks that is about 0.9–1.6 GB, against about 0.1 GB in-process. Tuned flags with AppCDS halve the cost but do not close the gap.
- **A JVM per script package** costs about the same as a JVM per task when each task runs a different package. When several tasks share a package, it is close to in-process: the JVM is paid once and each extra task adds very little.
- **Startup is not the problem.** A child is ready in 0.13–0.25 s, and still under 1.3 s with 20 started at the same time on 12 cores. AppCDS roughly halves it.
- **AppCDS is the most effective single flag here.** It cuts about 25 MB and half the startup time from every JVM. The tuned heap and JIT flags cut about 27 MB but barely change startup. The two together give the lowest numbers.

## Setup

- **Machine:** Apple M4 Pro (12 cores, 24 GB), macOS (Darwin 25.6.0).
- **JDK:** Oracle JDK 21.0.10 (HotSpot). **This is not Zulu 21.** The release workflow builds with Zulu (`.github/workflows/release-libs.yml`), but Zulu 21 is not installed on this machine. Both use the same HotSpot VM, so the numbers should be close, but Zulu was not measured.
- **Script:** `Script-Monitoring/script-tgtg/build/libs/release.jar` (TGTG Monitor). It is a 42 MB unencrypted shadow jar with 20,049 classes, bundling kdriver, Ktor, OkHttp, kotlinx.serialization/coroutines and kotlin-reflect. It does not bundle the SDK or kotlin-stdlib, which the client provides. Installed marketplace jars in `~/Cereal/Scripts` are encrypted, so a local build was used. It is the same script, built from source. This is the shadow jar **before** ProGuard (`shadowJar`; the shipped jar comes from `scriptJar`, which shrinks and obfuscates it). The shipped jar loads fewer classes, so these numbers are an upper bound for this script.
- **Child classpath:** harness jar, kotlin-stdlib 2.4.21, cereal-sdk 1.14.0, kotlinx-coroutines-core-jvm 1.11.0 (the client's versions from `gradle/libs.versions.toml`), then the script jar.
- **What "idle task" means** (`Harness.warmUp`):
  - Instantiate the `Script` class with its no-arg constructor, as `TaskExecutor` does.
  - Create an `OkHttpClient` and a Ktor `HttpClient(OkHttp)`.
  - Touch `Json.Default`.
  - Initialise kdriver's `DefaultBrowser` and `ConfigBuilder` classes.

  The harness then prints `READY` and blocks. It does not call `onStart`/`execute`, because those need a host `ComponentProvider`. It does not spawn Chrome.
- **Startup:** wall time measured by the driver from `Popen` to the `READY` line. For N concurrent JVMs, all N are spawned back to back.
- **Memory:**
  - `footprint -p <pid>` gives macOS phys_footprint, which is what Activity Monitor shows as "Memory". It counts private dirty and compressed memory, but not shared clean mappings such as libjvm text or the CDS archive. It is the right number to sum across processes.
  - `ps -o rss` is listed too. It counts shared pages once per process, so the sum overstates the total. Under pressure from 20 JVMs it also drops as macOS compresses pages, which is why RSS per JVM sometimes falls at N=20.
  - Samples are taken 3 s after the last child is ready. Values are the median over 5 runs at N=1, 3 at N=5 and 2 at N=20.
- **Configs:**
  - *default*: no flags. G1, JIT tiered up to C2, and the JDK's base CDS archive is on.
  - *no CDS*: `-Xshare:off`.
  - *tuned*: `-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -Xss256k -Xms8m -Xmx256m -XX:ReservedCodeCacheSize=32m`.
  - *+ AppCDS*: a dynamic archive built from one training run with `-XX:ArchiveClassesAtExit`, then used with `-XX:SharedArchiveFile` (JEP 350). The archive is about 15 MB on disk.
- **In-process baseline:** one JVM using the same JDK and default flags, with the SDK, stdlib and coroutines on the app classpath. Each script package gets its own `URLClassLoader`, like `ScriptJarReader`, which builds one `ScriptClassLoader` per package (`FileSystemScriptsDataSource.readJar`). Tasks of the same package share that loader. The driver samples after each load, following a `System.gc()`. "+ vs base" is the delta from the same JVM before any script was loaded. The 20 "different packages" are the same jar loaded in 20 separate loaders, which stands in for 20 packages of similar size.

## Raw results

```
## In-process baseline: 20 script packages x 1 task (one loader each)
| step          | load ms | footprint MB | +footprint vs base MB | RSS MB | +RSS vs base MB |
| base          | -       | 21           | 0                     | 42     | 0               |
| pkg=1 task=1  | 155     | 59           | 38                    | 82     | 40              |
| pkg=5 task=1  | 24      | 74           | 53                    | 104    | 62              |
| pkg=20 task=1 | 18      | 111          | 90                    | 137    | 96              |

## In-process baseline: 1 script package x 20 tasks (shared loader)
| base          | -       | 21           | 0                     | 42     | 0               |
| pkg=1 task=1  | 152     | 58           | 37                    | 81     | 39              |
| pkg=1 task=5  | 0       | 60           | 39                    | 81     | 39              |
| pkg=1 task=20 | 0       | 61           | 40                    | 81     | 40              |

## Child JVMs (script-tgtg release.jar, com.cereal.tgtg.TgtgScript)
| config               | JVMs | startup median s | startup max s | footprint/JVM MB | footprint total MB | RSS/JVM MB | RSS total MB |
| default (JDK CDS on) | 1    | 0.25 | 0.25 | 82 | 82   | 105 | 105  |
| default (JDK CDS on) | 5    | 0.34 | 0.35 | 82 | 409  | 106 | 530  |
| default (JDK CDS on) | 20   | 1.13 | 1.22 | 79 | 1583 | 94  | 1882 |
| no CDS (-Xshare:off) | 1    | 0.28 | 0.29 | 87 | 87   | 115 | 115  |
| no CDS (-Xshare:off) | 5    | 0.40 | 0.41 | 87 | 437  | 111 | 555  |
| no CDS (-Xshare:off) | 20   | 1.25 | 1.32 | 81 | 1624 | 89  | 1780 |
| default + AppCDS     | 1    | 0.13 | 0.14 | 59 | 59   | 83  | 83   |
| default + AppCDS     | 5    | 0.17 | 0.18 | 57 | 286  | 82  | 410  |
| default + AppCDS     | 20   | 0.60 | 0.67 | 57 | 1134 | 55  | 1091 |
| tuned                | 1    | 0.24 | 0.25 | 54 | 54   | 74  | 74   |
| tuned                | 5    | 0.27 | 0.29 | 55 | 275  | 75  | 376  |
| tuned                | 20   | 0.81 | 0.85 | 55 | 1092 | 64  | 1274 |
| tuned + AppCDS       | 1    | 0.13 | 0.13 | 46 | 46   | 69  | 69   |
| tuned + AppCDS       | 5    | 0.14 | 0.16 | 47 | 235  | 70  | 348  |
| tuned + AppCDS       | 20   | 0.44 | 0.51 | 47 | 942  | 69  | 1372 |
```

## AppCDS facts that matter for a design

- A dynamic archive (`-XX:ArchiveClassesAtExit`, JDK 13+) sits on top of the JDK's base archive. It only holds classes from classpath **jar files**: CDS refuses a non-empty directory on the classpath, which is why the harness is packaged as a jar. When the archive is loaded, the classpath is checked against the one it was built with. That makes an archive specific to one script jar version and one JDK build. Sources: [JEP 350](https://openjdk.org/jeps/350); the "Application Class Data Sharing" section of the [JDK 21 `java` man page](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- JDK 19+ has `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=<f>`. It writes the archive on the first run and regenerates it if it no longer matches, so the client would not need a separate training step ([JDK-8261455](https://bugs.openjdk.org/browse/JDK-8261455); same man page). This was **not** benchmarked here; only the explicit two-step flow was.
- A jlink image gets a base CDS archive only if it is built with `jlink --generate-cds-archive` (JDK 17+, [JDK-8264322](https://bugs.openjdk.org/browse/JDK-8264322)). **Not verified:** whether the client's jpackage/jlink runtime ships `lib/server/classes.jsa`. If it doesn't, child JVMs launched from that runtime start like the *no CDS* row unless AppCDS is added.

## Not measured

- **Zulu 21 and the client's actual jlink runtime.** An Oracle JDK 21 install was used instead (see Setup).
- **Windows and Linux.** Process creation and memory accounting differ there. These numbers are macOS only.
- **The client's own footprint.** The client was not running, and the baseline is a bare JVM, not the Compose app. The "+ vs base" columns are the meaningful part of the baseline.
- **A task that is actually running.** Heap growth during `execute`, real HTTP traffic and Chrome processes are not included. Chrome is spawned by kdriver in both models and probably outweighs the JVM, but that is unmeasured.
- **IPC and SDK shim cost.** The host-side proxy and the child-side SDK stub needed to cross a process boundary do not exist yet, so they are not included.
- **A lighter script.** Only one script was measured. A script without kdriver or Ktor would load fewer classes, but `warmUp` assumes they are present.

## Rerun

Requirements: macOS (the driver uses `footprint`), JDK 21, python3, and the Gradle cache populated by a client build. You also need the script jar built: `cd ~/PProjects/Script-Monitoring && ./gradlew :script-tgtg:shadowJar`, which writes `script-tgtg/build/libs/release.jar`.

```bash
cd research/child-jvm-overhead
./run.sh                 # everything: in-process baseline + all child configs at 1/5/20 (~5 min)
./run.sh inproc          # baseline only
./run.sh child           # child JVMs only
COUNTS=1,5 ./run.sh      # other concurrency levels
JAVA_HOME=/path/to/zulu-21 ./run.sh    # a different JDK (e.g. the client's jlink runtime)
SCRIPT_JAR=/path/to/release.jar SCRIPT_CLASS=com.example.MyScript ./run.sh   # another script (must bundle Ktor+OkHttp+kdriver, see Harness.warmUp)
```

`run.sh` resolves the stdlib, SDK and coroutines jars from `~/.gradle/caches`; override them with `STDLIB_JAR`, `SDK_JAR` and `COROUTINES_JAR`. `run.py` compiles `Harness.java` into `out/` (gitignored), builds the AppCDS archives there, and prints the Markdown tables above to stdout.
