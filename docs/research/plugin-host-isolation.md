# How established JVM plugin hosts isolate plugin classes

Research for [#153](https://github.com/Cereal-Automation/desktop-client/issues/153), part of the map
[#150 Isolate script jars at runtime](https://github.com/Cereal-Automation/desktop-client/issues/150).
Researched on 2026-10-18 against each project's default branch and the JDK 21 / OSGi Core R8 specs.

**Gist:** the hosts that hold up best don't hand plugins the whole application classpath, child-first or
otherwise. They give each plugin a parent that exposes only a declared, small shared API (Gradle's
`FilteringClassLoader`, OSGi's system-bundle exports, JPMS exports). Hosts that hand out the full app
classpath (IntelliJ, Jenkins, Elasticsearch classic plugins) each add a patch on top: a hard-coded
"must load from platform" list (IntelliJ), "don't duplicate signature types" rules (Jenkins), or a
load-time duplicate-class check that rejects the plugin (Elasticsearch "jar hell").

## The failure modes, in one place

Every host below is working around the same JVM rules:

- **A class is identified by (name, defining loader).** Two loaders that each define `kotlin.Unit` produce
  two distinct types. Passing one where the other is expected gives `ClassCastException`, or at link time
  `LinkageError: loader constraint violation`. The JVM records a constraint whenever a method signature
  crosses loaders, and fails if the two sides would resolve a name to different classes
  ([JVMS 21 §5.3.4 Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.4)).
  This means **every type that appears in a host↔plugin signature has to come from one loader**: the
  API types themselves and anything they mention (Kotlin stdlib types, `Continuation`, coroutine types).
- **Version skew**: if the plugin compiled against a newer copy of a shared library than the host ships,
  it fails at runtime with `NoSuchMethodError` / `NoClassDefFoundError`, not at load.
- **Resolution uses the defining loader**: a class's references are resolved by *its own* defining loader,
  so a library loaded from the host can't see plugin classes (`Class.forName`, `ServiceLoader`). The usual
  fix is the thread context classloader.
- **Resources and `ServiceLoader` follow `getResources`**: if a loader delegates resources parent-first
  (the `java.lang.ClassLoader` default), plugin-bundled `META-INF/services` files are shadowed by the
  host's.

## Per host

### IntelliJ Platform: `PluginClassLoader`

- **Order: self-first (child-first).** `tryLoadingClass` first checks `mustBeLoadedByPlatform(name)`.
  Otherwise it loads from the plugin's own classpath (`loadClassInsideSelf`), then from each declared
  dependency plugin's loader, and finally the core loader
  ([PluginClassLoader.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/core-impl/src/com/intellij/ide/plugins/cl/PluginClassLoader.kt)).
  The docs say the same: "By default, the main IDE class loader loads classes that are not found in the
  plugin class loader"
  ([Plugin Class Loaders](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html)).
- **Parent: multi-parent.** There is no single `parent`. The parents are the declared dependency plugins'
  loaders plus `coreLoader`, which is the IDE's app classpath.
- **Shared API declaration: implicit.** The whole platform classpath is visible. Dependencies on other plugins are
  declared with `<depends>` in `plugin.xml`. The forced-platform set is a **hard-coded list**:
  `java.*`, `kotlin.jvm.functions.*`, `kotlin.reflect.*`, selected `kotlin.jvm.internal.*Reference`
  classes, plus `KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES` (`kotlin.Unit`, `kotlin.Pair`, `kotlin.Result`,
  `kotlin.coroutines.Continuation`, `CoroutineContext`, `kotlin.time.Duration`, …), extensible through the system
  property `idea.kotlin.classes.used.in.signatures`. The source comment gives the reason: if a plugin bundles
  its own kotlin-runtime, "it won't be possible to call the platform's methods with these types in a
  signature". This is the loader-constraint rule above, solved by enumeration.
- **Policy on top**: the docs say not to bundle the stdlib ("the platform already bundles it"), and that
  plugins "must always use the bundled library from the target IDE" for `kotlinx.coroutines`
  ([Using Kotlin](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)).
- **Resources / services**: `findResource`/`getResourceAsStream` go through the same multi-parent helper
  (self, then parents). For `java.util.ServiceLoader` the docs tell plugins to set the thread context
  classloader to the plugin loader around the call.
- **Trade-off**: plugins can bundle any library version (self-first wins), but the platform team must keep
  an ever-growing allow-list of classes that may appear in signatures. Misses show up as
  `LinkageError`/`ClassCastException` in the field, and the IDEA-295189 entries in that list are such a case.

### Gradle: classloader scopes and `FilteringClassLoader`

- **Order: parent-first**, but the parent is a filter. Plugin/buildscript classpaths are ordinary
  URL loaders arranged in `DefaultClassLoaderScope` (export and local loaders per scope, parent scope's export
  loader as parent)
  ([DefaultClassLoaderScope.java](https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/api/internal/initialization/DefaultClassLoaderScope.java)).
  At the root sits a `FilteringClassLoader` wrapped around Gradle's runtime loader
  ([DefaultClassLoaderRegistry.java](https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/initialization/DefaultClassLoaderRegistry.java)).
- **Parent: platform loader + allow-list.** `FilteringClassLoader` "hides all non-system classes,
  packages and resources" and only shows declared ones. Anything not allowed is loaded from
  `ClassLoaderUtils.getPlatformClassLoader()`, so JDK classes stay visible but Gradle's own
  dependencies (Guava, etc.) don't
  ([FilteringClassLoader.java](https://github.com/gradle/gradle/blob/master/platforms/core-runtime/classloaders/src/main/java/org/gradle/internal/classloader/FilteringClassLoader.java)).
- **Shared API declaration: explicit, in code.** `GradleApiSpecProvider` implementations (found through `ServiceLoader`
  and aggregated) list exported packages, classes, resources and resource prefixes. The default list is `org.gradle`,
  Groovy, Ant, `org.slf4j`, commons-logging, log4j, `javax.inject`, `javax.annotation`, jspecify. Resources
  are `META-INF/gradle-plugins` plus two specific service/extension files. There is an explicit *unexported*
  package (`org.gradle.unexported`)
  ([DefaultGradleApiSpecProvider.java](https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/initialization/DefaultGradleApiSpecProvider.java)).
  Kotlin DSL support contributes its own spec through the same SPI.
- **Resources / services**: filtered exactly like classes. `allowPackage` also exposes that package's
  resources, and other resources must be allowed one by one. A plugin's own `META-INF/services` are therefore
  visible, and the host's are not, apart from the listed ones.
- **Trade-off**: robust against host-internal collisions (a plugin can bundle any Guava). The cost is that
  everything Gradle *does* export (Groovy, Kotlin stdlib in the Kotlin DSL) is pinned to Gradle's version.
  Plugins can't override it, and skew there gives `NoSuchMethodError`. The filter is also the API contract,
  so adding to it is a compatibility commitment.

### Jenkins: per-plugin classloader

- **Order: parent-first by default.** Platform → servlet container → Jenkins core (`WEB-INF/lib`) →
  plugin. "Each (enabled) plugin gets its own `ClassLoader`." There is also an `UberClassLoader` that
  delegates to all enabled plugins, used for lookups such as views and resources
  ([Dependencies and Class Loading](https://www.jenkins.io/doc/developer/plugin-development/dependencies-and-class-loading/)).
- **Parent: Jenkins core loader**, so the whole core classpath is visible, plus loaders of plugins declared in
  `Plugin-Dependencies` (generated from Maven deps by maven-hpi).
- **Shared API: implicit.** All of core is visible. Library sharing is done by "library wrapper plugins".
- **Opt-outs**: `pluginFirstClassLoader` (child-first for the whole plugin) and `maskClasses` (hide
  listed parent packages). The docs warn that with plugin-first, any core/upstream type in a called method's
  signature must not be duplicated or linkage errors follow. They also warn that the masked set must be closed
  under the linker's transitive references, or classes become unresolvable.
- **Resources**: found mostly through `UberClassLoader`, so resource paths must be globally unique (namespace
  them under the package path). Extensions use `@Extension` + `ExtensionList`, not raw `ServiceLoader`.
- **Trade-off**: parent-first means the core's library versions always win, so plugins are stuck with them.
  The escape hatches exist and come with documented foot-guns. Jenkins also notes that `JenkinsRule` tests
  can hide classloading mistakes, and that `RealJenkinsRule` catches them.

### Elasticsearch: plugin loaders, jar-hell check, module layers

- **Order (classic plugins): parent-first, with duplicates forbidden.** A non-modular plugin gets
  `URLClassLoader.newInstance(urls, pluginParentLoader)`. The parent is an `ExtendedPluginsClassLoader`, "a
  union over the parent core classloader and classloaders of extended plugins"
  ([PluginsLoader.java](https://github.com/elastic/elasticsearch/blob/main/server/src/main/java/org/elasticsearch/plugins/PluginsLoader.java),
  [ExtendedPluginsClassLoader.java](https://github.com/elastic/elasticsearch/blob/main/server/src/main/java/org/elasticsearch/plugins/ExtendedPluginsClassLoader.java)).
- **Jar hell**: before loading, `checkBundleJarHell` rejects the plugin if its jars duplicate a codebase
  or *any class* on the server classpath ("jar hell! duplicate codebases between plugin and core") or in
  extended plugins
  ([PluginsUtils.java](https://github.com/elastic/elasticsearch/blob/main/server/src/main/java/org/elasticsearch/plugins/PluginsUtils.java),
  [JarHell.java](https://github.com/elastic/elasticsearch/blob/main/libs/core/src/main/java/org/elasticsearch/jdk/JarHell.java)).
  Because shadowing is impossible, a plugin that needs a different version of a server library has to shade it.
- **Shared API**: implicit for classic plugins (all of server). `extended.plugins` adds other plugins' SPI
  jars. Plugins can split an `spi/` part that extenders see from the implementation that they don't.
- **Modular and stable plugins**: modular plugins get `ModuleLayer.defineModulesWithOneLoader` layered
  over the SPI layers. Non-modular "stable" plugins get `UberModuleClassLoader`, which wraps all jars in
  a synthetic module and "needs to avoid parent-first search … This will keep libraries from clashing". It
  denies reads of `org.elasticsearch.server`, with a TODO to "allow only jvm + stable API modules"
  ([UberModuleClassLoader.java](https://github.com/elastic/elasticsearch/blob/main/server/src/main/java/org/elasticsearch/plugins/UberModuleClassLoader.java)).
  This is Elasticsearch moving toward a declared API surface.
- **Services**: after loading, `reloadLuceneSPI(pluginClassLoader)` re-scans Lucene codecs from the
  plugin loader. Plugin init runs with the thread context classloader set to the plugin loader "so that
  plugins that have dependencies with their own SPI endpoints have a chance to load"
  ([PluginsService.java](https://github.com/elastic/elasticsearch/blob/main/server/src/main/java/org/elasticsearch/plugins/PluginsService.java)).
- **Trade-off**: the duplicate check turns runtime `LinkageError`s into a clear load-time failure, at the
  price of forcing shading on plugin authors. The newer stable-plugin path pays for real isolation with JPMS
  machinery.

### OSGi bundles (Core R8)

- **Order: fully specified** (§3.9.4): (1) `java.*` → parent, (2) boot-delegation list → parent,
  (3) `Import-Package` → the exporter's loader, (4) `Require-Bundle`, (5) own bundle class path,
  (6) fragments, (7) `DynamicImport-Package`. An imported package that isn't found does **not** fall through
  to the bundle itself
  ([OSGi Core R8, Module Layer](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html)).
- **Parent: the boot loader by default.** `org.osgi.framework.bundle.parent` is `boot` by default (§4.2.2,
  [Life Cycle Layer](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.lifecycle.html)), so
  the application classpath is invisible. Host packages are shared only by having the system bundle export them
  (`org.osgi.framework.system.packages`, §3.9.5). Packages outside `java.*` (e.g. `sun.*`) need
  `org.osgi.framework.bootdelegation`. Setting it to `*` restores parent-first (R3 behaviour).
- **Shared API: explicit and versioned.** `Export-Package`/`Import-Package` carry version ranges. The
  resolver enforces **class space consistency**, a class space "never contains two classes with the same
  fully qualified name", and the `uses` directive propagates that across transitive API types. This is the
  loader-constraint problem handled at resolve time.
- **Resources**: searched with the same delegation model as classes. Split packages via `Require-Bundle`
  are allowed but discouraged as "open ended" (§3.13.3).
- **Trade-off**: the most precise model, with versioned imports, multiple versions side by side, and resolve-time
  errors instead of runtime `LinkageError`. It requires accurate manifests for every bundle and an OSGi
  framework at runtime. It also breaks libraries that assume flat-classpath visibility (`Class.forName`,
  `ServiceLoader`), which is why `DynamicImport-Package` and the boot-delegation knob exist.

### JPMS `ModuleLayer` (JDK 9+)

- **Order: direct delegation by package.** A `defineModulesWithOneLoader` loader maps a class's package to
  its module and asks that module's loader. Classes in packages that don't map to a module go to the
  supplied parent loader
  ([ModuleLayer javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html)).
  No parent-first/child-first choice exists because each package has exactly one owner.
- **Parent**: the caller picks the parent layer(s) *and* the parent class loader. Layers can repeat a
  package that a parent layer has, which is how two versions of a library can coexist.
- **Shared API: `exports`/`requires`** in `module-info`. Readability is resolved before any class loads.
- **Hard rules**: within one loader, "overlapping packages" or "split delegation" throw
  `LayerInstantiationException`. Modules named `java.base`, or packages starting with `java.`, are rejected.
- **Resources and services**: with one loader, resources are searched "in all modules in the layer before
  searching the parent class loader". `ServiceLoader` locates providers in named modules layer-first, then in
  parents. Classpath `META-INF/services` files go through the loader's `getResources`.
  `ServiceLoader.load(ModuleLayer, Class)` searches named modules only
  ([ServiceLoader javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html)).
- **Trade-off**: strong, JVM-enforced encapsulation, but both host API and plugins need module
  descriptors, or automatic/synthetic modules like Elasticsearch's `UberModuleClassLoader`. A classpath-launched host
  (the unnamed module) can't export a named API module to plugins without restructuring.

## Comparison

| Host | Plugin-vs-parent order | Parent sees | Shared set declared by | Resources / services | Typical failure |
|---|---|---|---|---|---|
| IntelliJ | self-first, then dep plugins, then core | whole IDE classpath | hard-coded must-load-from-platform list + docs policy | same multi-parent order; set TCCL for `ServiceLoader` | `LinkageError` on a signature type missing from the list |
| Gradle | parent-first | **platform loader + allow-list** | `GradleApiSpecProvider` package/resource lists | filtered like classes | `NoSuchMethodError` from skew in exported libs (Groovy/Kotlin) |
| Jenkins | parent-first (opt-in plugin-first, mask) | whole core classpath | implicit; library wrapper plugins | via `UberClassLoader`; unique paths | linkage errors with plugin-first and duplicated signature types |
| Elasticsearch (classic) | parent-first, duplicates **rejected** | whole server + extended SPI | implicit; `extended.plugins`, `spi/` | TCCL + explicit Lucene SPI reload | "jar hell" at load (by design) |
| OSGi | `java.*`/bootdelegation → imports → self | **boot loader only** by default | versioned `Export/Import-Package`, `uses` | same as classes | resolve failure; `ClassNotFoundException` for reflective lookups |
| JPMS layer | per-package owner | chosen parent loader/layers | `module-info` exports | layer-first, then parent | `LayerInstantiationException`, illegal access |

## Which patterns fit this client

Today `ScriptClassLoader` is IntelliJ-shaped: child-first over the *whole* client classpath (the
`ClassLoader()` default parent is the system loader), with a short forced-parent list (`com.cereal.sdk`,
`kotlinx.coroutines`) and a `com.cereal` block. Compared against the hosts above:

1. **Adopt Gradle's filtering parent. Best fit.** Make the parent a filter over the system loader that
   exposes the shared set (`com.cereal.sdk.*`, `kotlin.*`, `kotlinx.coroutines.*`, plus whatever else SDK
   signatures mention) and sends everything else to `ClassLoader.getPlatformClassLoader()`. Once nothing
   else is visible, child-first vs parent-first no longer matters for unshared packages, and the client's own
   libraries (OkHttp, Ktor, serialization, Compose…) can't leak into scripts. It's about a page of code, with
   no framework and no manifest changes to scripts. The `com.cereal` block also becomes unnecessary for
   classes, since non-SDK `com.cereal.*` isn't exported.
2. **Make the shared set cover every signature type.** IntelliJ's list shows that sharing
   `com.cereal.sdk` alone isn't enough: anything in SDK signatures (`kotlin.Unit`, `Continuation`,
   `CoroutineContext`, `Result`…) must come from one loader or calls fail with a loader-constraint
   `LinkageError` (JVMS §5.3.4). Whole `kotlin.*` and `kotlinx.coroutines.*` prefixes are the simple, safe
   choice. They match what Script-Template already excludes (stdlib) or should exclude (coroutines is
   bundled today, but the forced-parent rule makes that copy dead weight).
3. **Handle resources the same way.** `findResource`/`findResources` aren't overridden today, so a
   script's own `META-INF/services` and config resources are invisible and the client's are seen instead.
   This is the case Gradle's resource filter and Elasticsearch's TCCL/SPI reload exist for. Serve resources
   from the script jar, filter parent resources with the same allow-list, and set the thread context
   classloader to the script loader while running script code (IntelliJ and Elasticsearch both do this).
4. **Borrow Elasticsearch's jar-hell idea for build-time enforcement.** A check that fails the script build
   (or rejects the jar at load) if it bundles any class in the shared set turns silent skew into a clear
   error. Stamp the required SDK/Kotlin/coroutines versions into `manifest.json` and compare them to the
   client's, because skew in shared libraries is the one failure the filtering model can't prevent (it is
   Gradle's remaining weak spot too).
5. **Skip OSGi and JPMS layers for now.** Both give stronger guarantees, but need module or bundle metadata in
   every script jar and an SDK published as a named module. That conflicts with "published jars must keep
   loading without a rebuild" and isn't justified when the goal is preventing accidental collisions, not
   sandboxing. Elasticsearch's `UberModuleClassLoader` shows the route if that changes later.
6. **Legacy risk to check in the legacy-mode ticket.** Filtering removes client libraries that old
   scripts may silently depend on without bundling them, i.e. anything that was `compileOnly` in
   Script-Template or resolved from the client by accident. Jenkins's `maskClasses` warning applies: the
   visible set must be closed under transitive references. Old jars (by manifest `sdkVersion`) may need the
   current permissive loader as a fallback.
