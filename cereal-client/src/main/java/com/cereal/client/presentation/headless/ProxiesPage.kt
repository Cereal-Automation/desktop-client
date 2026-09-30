package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.files.AddProxiesFromFileToGroupInteractor
import com.cereal.client.application.interactor.proxy.CheckProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.CreateProxyGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteFailedProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteProxyGroupInteractor
import com.cereal.client.application.interactor.proxy.GetProxiesInteractor
import com.cereal.client.application.interactor.proxy.GetProxyGroupsInteractor
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tab `3`: proxy groups with their count and number failing. `a` adds a group (a name, then a pasted
 * list or a file in the data [volume]), `Enter` opens a group's proxies, where `a` appends the same
 * way, `t` tests them all and `D` deletes the failing ones after confirming.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class ProxiesPage(
    private val scope: CoroutineScope,
    private val changed: () -> Unit,
    private val volume: File,
    private val getGroups: GetProxyGroupsInteractor,
    private val getProxies: GetProxiesInteractor,
    private val createGroup: CreateProxyGroupInteractor,
    private val deleteGroup: DeleteProxyGroupInteractor,
    private val addProxies: AddProxiesFromFileToGroupInteractor,
    private val deleteFailed: DeleteFailedProxiesInGroupInteractor,
    private val checkProxies: CheckProxiesInGroupInteractor,
) : TuiPage {
    private data class Group(
        val group: ProxyGroup,
        val proxies: List<Proxy>,
    ) {
        val failing get() = proxies.count { it.health.status == ProxyHealthStatus.FAILED }
    }

    /** Where an import goes: a new group named [newName], or appended to [group]. */
    private data class Target(
        val newName: String?,
        val group: ProxyGroup?,
    )

    private val groupList = RowList<Group>()
    private val proxyList = RowList<Proxy>()
    private val sourceList =
        RowList<Boolean>().apply { rows = listOf(RowList.Row("paste", "Paste a proxy list", true), RowList.Row("file", "A file in the data volume", false)) }

    @Volatile private var groups: List<Group>? = null

    @Volatile private var openId: String? = null

    @Volatile private var name: StringBuilder? = null

    @Volatile private var source: Target? = null

    @Volatile private var input: ImportInput? = null

    @Volatile private var confirmFailing: Int? = null

    /** Proxies tested so far and in total while `t` runs. */
    @Volatile private var testing: Pair<Int, Int>? = null

    @Volatile private var notice: String? = null

    private var observation: Job? = null

    private val open get() = groups?.firstOrNull { it.group.id == openId }

    override val title = "Proxies"

    override val keys: String
        get() =
            input?.keys ?: when {
                name != null -> "Enter next · Esc cancel"
                source != null -> "↑↓ move · Enter choose · Esc cancel"
                confirmFailing != null -> "y delete · any other key cancels"
                openId != null -> "a append · t test all · D delete failing · Esc back"
                else -> "↑↓ select · Enter open · a add group"
            }

    override fun onSignedIn() {
        observation?.cancel()
        observation =
            scope.launch {
                try {
                    getGroups(Interactor.None())
                        .map { it.get() }
                        .flatMapLatest { groups ->
                            if (groups.isEmpty()) {
                                flowOf(emptyList())
                            } else {
                                combine(groups.map { g -> getProxies(GetProxiesInteractor.Params(g)).map { Group(g, it.get()) } }) { it.toList() }
                            }
                        }.collect {
                            groups = it
                            changed()
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notice = e.message
                    changed()
                }
            }
    }

    override fun body(
        width: Int,
        height: Int,
    ): List<String> {
        input?.let { return listOf(" ${importHeading()}", "") + it.render(width, height - 2) }
        name?.let { return listOf(" New proxy group", "", "  Name: ${it}_") }
        source?.let { return listOf(" ${importHeading()}", "") + sourceList.render(width, height - 2) }
        val groups = groups ?: return listOf("", "  Loading…")
        val bottom =
            listOfNotNull(
                confirmFailing?.let { "  Delete the $it failing proxy(ies)? It can't be undone. [y/N]" },
                testing?.let { (done, total) -> "  Testing… $done/$total" },
            ) + notice?.lines().orEmpty().map { "  ! $it" }
        val bottomLines = if (bottom.isEmpty()) bottom else listOf("") + bottom
        val open = open
        val (head, list) =
            if (open != null) {
                proxyList.rows = open.proxies.map { RowList.Row(it.id.toString(), describe(it), it) }
                val head = listOf(" ${open.group.name}: ${open.proxies.size} proxies, ${open.failing} failing", "")
                head to if (open.proxies.isEmpty()) listOf("  No proxies yet. Press a to append some.") else proxyList.render(width, height - head.size - bottomLines.size)
            } else {
                groupList.rows = groups.map { RowList.Row(it.group.id, "${it.group.name}  ${it.proxies.size} proxies, ${it.failing} failing", it) }
                val head = listOf(" Proxy groups", "")
                head to if (groups.isEmpty()) listOf("  No proxy groups yet. Press a to add one.") else groupList.render(width, height - head.size - bottomLines.size)
            }
        return head + list + bottomLines
    }

    override fun onKey(key: Key): Boolean {
        input?.let { return it.onKey(key) }
        name?.let { return onNameKey(it, key) }
        source?.let { return onSourceKey(it, key) }
        confirmFailing?.let {
            confirmFailing = null
            if (key == CharKey('y') || key == CharKey('Y')) open?.let { deleteFailing(it.group) }
            return true
        }
        if (groups == null) return false
        notice = null
        val open = open
        if (open == null) {
            if (groupList.onKey(key)) return true
            when {
                key == Keys.Enter -> groupList.selected?.let { openId = it.group.id } ?: return false
                key == CharKey('a') -> name = StringBuilder()
                else -> return false
            }
        } else {
            if (proxyList.onKey(key)) return true
            when {
                key == Keys.Escape -> openId = null
                key == CharKey('a') -> source = Target(null, open.group)
                key == CharKey('t') -> testAll(open)
                key == CharKey('D') -> if (open.failing > 0) confirmFailing = open.failing else notice = "No proxy in this group is failing."
                else -> return false
            }
        }
        return true
    }

    private fun onNameKey(
        text: StringBuilder,
        key: Key,
    ): Boolean {
        when {
            key == Keys.Escape -> {
                name = null
            }

            key == Keys.Backspace -> {
                if (text.isNotEmpty()) text.setLength(text.length - 1)
            }

            key == Keys.Enter && text.isNotBlank() -> {
                name = null
                source = Target(text.toString().trim(), null)
            }

            key is CharKey -> {
                text.append(key.char)
            }
        }
        return true
    }

    private fun onSourceKey(
        target: Target,
        key: Key,
    ): Boolean {
        if (sourceList.onKey(key)) return true
        when (key) {
            Keys.Escape -> {
                source = null
            }

            Keys.Enter -> {
                val paste = sourceList.selected == true
                input =
                    ImportInput(paste, listOf("One proxy per line: ${FileSystemProxyTemplateDataSource.TEMPLATE}"), { runImport(target, it) }) {
                        input = null
                        source = null
                    }
            }
        }
        return true
    }

    private fun importHeading(): String = source?.let { target -> target.newName?.let { "New proxy group $it" } ?: "Append to ${target.group?.name}" }.orEmpty()

    private fun runImport(
        target: Target,
        from: ImportSource,
    ) {
        scope.launch {
            val result =
                if (target.group != null) {
                    from.import(volume, addProxies) { AddProxiesFromFileToGroupInteractor.Params(it, target.group) }.map { it.group }
                } else {
                    createGroup.result(CreateProxyGroupInteractor.Params(target.newName.orEmpty())).mapCatching { group ->
                        // Nothing imported means no group either: the file is read in full before anything is added.
                        from
                            .import(volume, addProxies) { AddProxiesFromFileToGroupInteractor.Params(it, group) }
                            .onFailure { deleteGroup(DeleteProxyGroupInteractor.Params(group)) }
                            .getOrThrow()
                            .group
                    }
                }
            result
                .onSuccess {
                    input = null
                    source = null
                    openId = it.id
                    groupList.select(it.id)
                    notice = "Imported ${it.numberOfItems} proxy(ies)."
                }.onFailure { input?.failed("Nothing was imported.\n${importError(it)}") }
            changed()
        }
    }

    private fun testAll(group: Group) {
        if (testing != null || group.proxies.isEmpty()) return
        testing = 0 to group.proxies.size
        changed()
        scope.launch {
            try {
                var failing = 0
                checkProxies.run(CheckProxiesInGroupInteractor.Params(group.group)).collect {
                    if (it.health.status == ProxyHealthStatus.FAILED) failing++
                    testing = testing?.let { (done, total) -> done + 1 to total }
                    changed()
                }
                notice = "Tested ${group.proxies.size} proxy(ies): $failing failing."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message
            } finally {
                testing = null
                changed()
            }
        }
    }

    private fun deleteFailing(group: ProxyGroup) {
        scope.launch {
            deleteFailed
                .result(DeleteFailedProxiesInGroupInteractor.Params(group))
                .onSuccess { notice = "Deleted $it failing proxy(ies)." }
                .onFailure { notice = it.message }
            changed()
        }
    }

    private companion object {
        fun describe(proxy: Proxy): String {
            val address = "${proxy.address}:${proxy.port}${proxy.username?.let { "  $it" }.orEmpty()}"
            val health = proxy.health
            val status =
                when (health.status) {
                    ProxyHealthStatus.UNKNOWN -> "untested"
                    ProxyHealthStatus.HEALTHY -> "ok${health.latencyMs?.let { " $it ms" }.orEmpty()}"
                    ProxyHealthStatus.FAILED -> "FAILED${health.lastError?.let { ": $it" }.orEmpty()}"
                }
            return "${address.padEnd(36)} $status"
        }

        suspend fun <T : Any, P> Interactor<T, P>.result(params: P): Result<T> {
            var result: Result<T> = Result.failure(IllegalStateException("The action didn't finish."))
            this(params) {
                result =
                    when (it) {
                        is SuspendableResult.Success -> Result.success(it.value)
                        is SuspendableResult.Failure -> Result.failure(it.error)
                    }
            }
            return result
        }
    }
}
