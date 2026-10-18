import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Throwaway harness for issue #161. Not client code.
 *
 * child <scriptClass>
 *     Runs inside a child JVM whose classpath already holds kotlin-stdlib + cereal-sdk + the script jar.
 *     Does the "idle task" warm-up once, prints READY, then blocks on stdin until the driver closes it.
 *
 * inproc <scriptJar> <scriptClass> <packages> <tasksPerPackage>
 *     In-process baseline: one JVM, one URLClassLoader per script package (like ScriptJarReader),
 *     tasks of the same package share the loader. Prints STEP lines and waits for a stdin line after
 *     each so the driver can sample memory at that point.
 */
public class Harness {
    // Keep everything reachable, like live tasks would.
    static final List<Object> LIVE = new ArrayList<>();

    /** What a typical script has touched by the time a task is running idle. */
    static void warmUp(ClassLoader cl, String scriptClass) throws Exception {
        // 1. The script entry point, instantiated the way TaskExecutor does (no-arg constructor).
        LIVE.add(Class.forName(scriptClass, true, cl).getDeclaredConstructor().newInstance());
        // 2. OkHttp client.
        LIVE.add(Class.forName("okhttp3.OkHttpClient", true, cl).getDeclaredConstructor().newInstance());
        // 3. Ktor HttpClient on the OkHttp engine (scripts' usual HTTP stack).
        Object okHttpEngine = Class.forName("io.ktor.client.engine.okhttp.OkHttp", true, cl).getField("INSTANCE").get(null);
        Class<?> factory = Class.forName("io.ktor.client.engine.HttpClientEngineFactory", true, cl);
        Function1<Object, Unit> noConfig = c -> Unit.INSTANCE;
        LIVE.add(Class.forName("io.ktor.client.HttpClientKt", true, cl)
            .getMethod("HttpClient", factory, Function1.class).invoke(null, okHttpEngine, noConfig));
        // 4. kotlinx.serialization Json + kdriver browser classes initialised (no Chrome is spawned).
        LIVE.add(Class.forName("kotlinx.serialization.json.Json", true, cl).getField("Default").get(null));
        Class.forName("dev.kdriver.core.browser.DefaultBrowser", true, cl);
        Class.forName("dev.kdriver.core.browser.ConfigBuilder", true, cl);
    }

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        if (args[0].equals("child")) {
            warmUp(Harness.class.getClassLoader(), args[1]);
            System.out.println("READY");
            System.out.flush();
            while (in.readLine() != null) { /* idle until stdin closes */ }
            return;
        }
        // inproc
        URL jar = new File(args[1]).toURI().toURL();
        int packages = Integer.parseInt(args[3]);
        int tasks = Integer.parseInt(args[4]);
        step(in, "base");
        for (int p = 0; p < packages; p++) {
            URLClassLoader cl = new URLClassLoader(new URL[]{jar}, Harness.class.getClassLoader());
            LIVE.add(cl);
            for (int t = 0; t < tasks; t++) {
                long t0 = System.nanoTime();
                warmUp(cl, args[2]);
                step(in, "pkg=" + (p + 1) + " task=" + (t + 1) + " ms=" + (System.nanoTime() - t0) / 1_000_000);
            }
        }
    }

    static void step(BufferedReader in, String label) throws Exception {
        System.gc(); // so samples are comparable; the driver also samples before any GC in child mode
        System.out.println("STEP " + label);
        System.out.flush();
        in.readLine();
    }
}
