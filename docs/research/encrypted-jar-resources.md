# Can the encrypted script jar serve resource and service lookups?

Research for [#152](https://github.com/Cereal-Automation/desktop-client/issues/152), part of the map
[#150](https://github.com/Cereal-Automation/desktop-client/issues/150) (isolate script jars at runtime).

JDK facts are cited against the OpenJDK 21 sources (`lib/src.zip` of JDK 21.0.10, the client's
`jvmTarget`). The same text is in the published javadoc for
[`ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html),
[`ServiceLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html) and
[`URL`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URL.html).

## Answer

**Yes.** `ScriptClassLoader` can serve `getResource`, `getResources`, `getResourceAsStream` and
`ServiceLoader` straight from the encrypted jar, with no plaintext on disk. It overrides
`findResource`/`findResources` to return URLs with a custom `URLStreamHandler` that decrypts the entry
when the URL is opened. Entry names are already plaintext in the jar, so listing is free. A
throwaway prototype (below) confirms `getResourceAsStream` and `ServiceLoader` work this way on JDK 21.

Two things need deciding in the spec:

1. **Delegation order.** The default `getResource`/`getResources` is parent-first and also returns the
   client's resources. For isolation, `ScriptClassLoader` has to override `getResource`/`getResources`
   too (child-first, matching its `loadClass`), not only the `find*` methods.
2. **URL round-tripping.** A handler passed to the URL instance lives only on that object. Code that
   re-parses the URL from a string (`new URL(url.toString())`, `url.toURI().toURL()`) fails with
   `unknown protocol`. To make that work, register the protocol globally with a
   `java.net.spi.URLStreamHandlerProvider` on the client's classpath.

Decrypting to a temp jar and using `jar:` URLs or a `URLClassLoader` also works and round-trips cleanly.
But it writes the script's plaintext to disk, decrypts every entry up front, and keeps the file open. That
undoes the reason the jar is encrypted, so it is the fallback, not the recommendation.

## What `Encryption` offers today

Source: `cereal-client/.../filesystem/security/Encryption.kt`, `reader/ScriptClassLoader.kt`.

- `writeJar` copies the jar entry by entry. It keeps each **entry name in plaintext**
  (`JarEntry(currentEntry.name)`) and replaces each entry's **contents** with
  `GCM_MAGIC + IV + AES-GCM ciphertext`. The output is a valid zip whose central directory is
  readable by `java.util.jar.JarFile`.
- `readJarEntry(file, name, key)` opens a new `JarFile` **on every call**, reads one entry (capped at
  50 MB), and decrypts it. It returns `null` on any failure, after logging and reporting to Sentry.
- There is **no list-entries method**. None is needed for decryption: `JarFile(file).entries()` or
  `getJarEntry(name) != null` answers "does the jar contain X" without decrypting, because names are
  plaintext.
- Decryption is whole-entry: `Cipher.doFinal` over the full ciphertext, and GCM must check the tag before
  it returns plaintext. A resource cannot be streamed. Each open costs a full read plus decrypt of
  that entry, and the result sits in memory.
- In `Environment.LOCAL` the jar is stored and read unencrypted.
- `ScriptClassLoader` overrides only `loadClass`/`findClass`. It keeps an unbounded `classCache` of
  decrypted class bytes and calls `ClassLoader()`, so its parent is the system class loader.
  `findResource`/`findResources` are not overridden, so today every resource lookup resolves only
  against the client (the base implementations return `null` / an empty enumeration; `ClassLoader.java`
  `findResource` "@implSpec The default implementation returns null").
- Side finding: `writeJar` reads the original jar with `JarInputStream`, which consumes
  `META-INF/` and `META-INF/MANIFEST.MF` when they are the first entries and never returns them as entries
  (`JarInputStream.java` class javadoc and constructor, lines 44–66, 127–151). The stored jar therefore
  has **no `META-INF/MANIFEST.MF`**, so a script calling `getResource("META-INF/MANIFEST.MF")` (or
  `Package.getImplementationVersion()`) will never see its own manifest. Other `META-INF/*` entries,
  including `META-INF/services/*`, survive.

## The `ClassLoader` resource contract

From `java/lang/ClassLoader.java` (JDK 21):

- `getResource(name)`: "@implSpec The default implementation will first search the parent class loader
  for the resource ... If not found, this method will invoke `findResource(String)`." So by default the
  parent wins.
- `getResources(name)`: returns "the URLs found by searching the parent class loader followed by the
  elements found with `findResources`" (a `CompoundEnumeration` of parent then self). The apiNote asks
  overriders to keep it consistent with `getResource`: "the first element returned ... is the same
  resource that the `getResource(String)` method would return."
- `getResourceAsStream(name)` is implemented as `getResource(name)` then `url.openStream()`. **A
  resource must have a `URL`.** Returning bytes without a URL is not possible through the standard API.
- `findResource` / `findResources` are the documented extension points ("Class loader implementations
  should override this method").

Consequence for #150: overriding only `findResource(s)` adds the script's resources **after** the
client's. When both ship `foo.properties` or the same `META-INF/services/X`, the client's copy wins
`getResource`, and `getResources` returns both. Isolation needs `getResource`/`getResources`
overridden to check the script jar first, with the same parent-first exceptions `loadClass` uses
(`com.cereal.sdk`, `kotlinx.coroutines`). It also has to decide whether parent results are appended at
all.

## What `ServiceLoader` needs

From `java/util/ServiceLoader.java` (JDK 21), `LazyClassPathLookupIterator` (lines 1116–1260):

1. It calls `loader.getResources("META-INF/services/" + service.getName())` (line 1203). The class loader's
   public `getResources` is the only hook, so whatever delegation it implements decides which
   provider files are seen and in what order. The javadoc says: "The ordering is based on the order that the class
   loader's `getResources` method finds the service configuration files".
2. For each URL it calls `u.openConnection()`, `uc.setUseCaches(false)`, `uc.getInputStream()`, and parses
   it as UTF-8, one binary class name per line, `#` comments allowed (lines 1167–1183). **So the URL
   only needs a `URLConnection` whose `getInputStream()` returns the decrypted bytes.** No length,
   content type, or `JarURLConnection` is required.
3. Names are deduplicated across files (`providerNames`). Each is loaded with
   `Class.forName(cn, false, loader)` (line 1217), so the provider must be loadable through the script
   loader. It must be in an unnamed module (named-module classes are skipped, line 1232) and
   `service.isAssignableFrom(clazz)` must hold (line 1237). That means the service interface the
   provider implements must be the **same `Class` object** the caller passed. A shared (parent-first) API
   type works. A type the script bundles works only if the caller also got it from the script loader.
4. `ServiceLoader.load(Class)` without a loader uses `Thread.currentThread().getContextClassLoader()`
   (line 1697). **The client never sets a context class loader** (`grep contextClassLoader` over
   `cereal-client/src/main` finds nothing). So script code (or a library it bundles) calling
   `ServiceLoader.load(X::class.java)` searches the **client's** classpath, even after
   `ScriptClassLoader` serves resources. To fix it, set the TCCL to the script's loader on the threads or
   coroutines that run script code (for coroutines, a `ThreadContextElement`), or require scripts to pass
   the loader explicitly.

Side finding: Script-Template's `shadowJar` (local checkout, `build.gradle.kts`, not re-fetched) does
not call `mergeServiceFiles()`. When two bundled libraries ship the same `META-INF/services/X`, only one
survives in the fat jar. The jar is also ProGuard-processed after shadowing, so provider class names
in service files only stay valid if ProGuard keeps or adapts them. These belong to the build-time
enforcement ticket, not to the loader.

## Options for a `URL` over bytes that live in memory

### A. Custom `URLStreamHandler` per script loader (recommended)

```kotlin
// inside ScriptClassLoader
private val handler = object : URLStreamHandler() {
    override fun openConnection(u: URL) = object : URLConnection(u) {
        override fun connect() {}
        override fun getInputStream(): InputStream =
            encryption.readJarEntry(file, u.path.removePrefix("/"), encryptionKey)
                ?.inputStream() ?: throw FileNotFoundException(u.toString())
    }
}

override fun findResource(name: String): URL? =
    if (jarHas(name)) URL.of(URI("cereal-script", scriptId, "/$name", null), handler) else null
```

- API: `URL.of(URI, URLStreamHandler)` (since 20) is the non-deprecated way to bind a handler. The
  `URL(URL, String, URLStreamHandler)` constructors are `@Deprecated(since = "20")`. `URL.of` refuses a
  handler only for `jrt` and `file` (`URL.isOverrideable`, lines 1568–1584), so a custom scheme is fine.
  (Overriding `jar` is also allowed, but there is no reason to reuse that name.)
- `URLStreamHandler.openConnection(URL)` is the only abstract method (`URLStreamHandler.java` line 73).
  The default `equals`/`hashCode` compare protocol, host, path and ref, so put a per-script id in the host
  to keep URLs from different scripts distinct.
- **Disk:** none. Plaintext never leaves memory.
- **Startup:** nothing extra beyond reading the jar's central directory once to build the name set
  (`JarFile.entries()`, no decryption). The loader could build it lazily on first resource lookup.
- **Per lookup:** `findResource` is a set lookup. Bytes are decrypted when the URL is **opened**,
  not when it is created. Each open costs, as today, a new `JarFile`, a whole-entry read and an AES-GCM
  decrypt. Fine for config and service files. For hot or large resources, keep one `JarFile` open per
  loader instead of reopening per call.
- **Memory:** one decrypted copy per open stream, released after reading. Caching plaintext like
  `classCache` does is optional. Resources are usually read once, so caching is not worth it by default.
- **Caveat (verified):** the handler lives only on the URL instance. `URL.toString()` gives
  `cereal-script://<id>/<path>`, and re-parsing that string fails because no global handler exists for
  the scheme. Libraries that round-trip resource URLs through strings or `URI` (some logging/config
  frameworks do) would break. Ways to fix it:
  - Ship a `java.net.spi.URLStreamHandlerProvider` for `cereal-script` in the **client**, registered
    in `META-INF/services`. `URL` looks providers up with `ServiceLoader` on the **system** class loader
    only (`URL.java` lines 1468–1473, and the constructor javadoc lines 330–355), so it cannot live in a
    script. It would dispatch on the URL host (script id) to a registry of live `ScriptClassLoader`s. This
    is global state, but it is the documented mechanism.
  - `URL.setURLStreamHandlerFactory` can be called **once per JVM**, and a second call throws
    `Error("factory already defined")` (lines 1389–1399). Avoid it: a library in the client or a script
    could already have claimed it.
  - Or do nothing until a real script needs it. Most consumers (`getResourceAsStream`, `ServiceLoader`,
    `url.openStream()`) never re-parse.

### B. Decrypt the whole jar to a temp file, then use `jar:` URLs (or a `URLClassLoader`)

- Gives standard `jar:file:/tmp/...!/name` URLs that round-trip through strings. You could even
  replace `ScriptClassLoader` with a `URLClassLoader` and get `getResources`, `ServiceLoader`, the
  manifest and `Package` metadata for free (`jdk/internal/loader/URLClassPath.java`, `JarLoader`).
- **Disk:** the full plaintext jar on disk for as long as the script is loaded. That undoes the
  encryption of script jars at rest (ADR 0001 lists script jars among the encrypted data). This is the
  deciding cost.
- **Startup:** decrypt every entry before the first class loads. That is O(jar size) of AES-GCM plus a
  write, instead of only the entries actually used.
- **Lifecycle:** `jar:` connections cache open `JarFile`s by default
  (`sun/net/www/protocol/jar/JarFileFactory.java` `fileCache`, `JarURLConnection` `getUseCaches()`). The
  temp file stays open until the loader is closed. On Windows an open file cannot be deleted, so cleanup
  after crashes needs a sweep on startup.
- **Memory:** lowest of the options (the OS pages the file).

### C. Decrypt once into memory and keep plaintext bytes (a variation of A)

Option A with eager decryption: build a `Map<String, ByteArray>` of all non-class entries at load
time. It makes opens free but holds every resource's plaintext for the loader's lifetime and adds startup
cost. Only worth it if profiling shows the per-open `JarFile` reopen plus decrypt matters. A small LRU
or a single open `JarFile` handle is the cheaper middle ground.

## Prototype

A single-file JDK 21 program (not committed) mirrored option A. It had a `ClassLoader` whose `findResource`
returns `URL.of(URI("cereal-script", ...), handler)` over an in-memory map that stands in for
`readJarEntry`. It contained `META-INF/services/java.lang.Runnable` and a data file. Output:

```
hello from memory                       <- getResourceAsStream via custom URL
url=cereal-script:/data/hello.txt
provider ran                            <- ServiceLoader.load(Runnable, loader) found + instantiated it
providers=1
re-parse fails: unknown protocol: cereal-script
toURI().toURL fails: unknown protocol: cereal-script
```

## Inputs for the spec (#150)

- Resource serving is feasible with today's on-disk format. **Published jars need no rebuild**: the
  names are already plaintext and the contents already decrypt per entry. Only the missing
  `META-INF/MANIFEST.MF` is lost, and it was lost at install time.
- The loader change: override `getResource`/`getResources` (child-first, same parent-first list as
  `loadClass`), plus `findResource`/`findResources` backed by a custom-scheme handler that calls
  `readJarEntry`.
- Separate decision: set the thread context class loader for script execution, or `ServiceLoader.load`
  without an explicit loader keeps resolving against the client.
- Optional, defer until needed: a client-side `URLStreamHandlerProvider` so script resource URLs
  survive string round-trips.
