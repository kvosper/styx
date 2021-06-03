package com.hotels.styx.client

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.eventbus.EventBus
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.Id
import com.hotels.styx.api.extension.*
import com.hotels.styx.api.extension.RemoteHost.remoteHost
import com.hotels.styx.client.connectionpool.ConnectionPool
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor
import com.hotels.styx.client.origincommands.DisableOrigin
import com.hotels.styx.client.origincommands.EnableOrigin
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot
import com.hotels.styx.common.EventProcessor
import com.hotels.styx.common.Preconditions.checkArgument
import com.hotels.styx.common.QueueDrainingEventProcessor
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory.getLogger
import java.io.Closeable
import java.util.*
import java.util.Objects.isNull
import java.util.Objects.nonNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream.concat


class OriginsInventoryKotlin(
    val eventBus: EventBus,
    val appId: Id,
    val originHealthStatusMonitor: OriginHealthStatusMonitor,
    val hostConnectionPoolFactory: ConnectionPool.Factory,
    val hostClientFactory: StyxHostHttpClient.Factory,
    val meterRegistry: MeterRegistry
) : OriginHealthStatusMonitor.Listener, OriginsCommandsListener, ActiveOrigins, OriginsChangeListener.Announcer,
    Closeable, EventProcessor {
    private val log = getLogger(OriginsInventory::class.java)
    private val eventQueue: QueueDrainingEventProcessor
    private val inventoryListeners = Announcer.to(OriginsChangeListener::class.java)
    private var origins: Map<Id, MonitoredOrigin> = emptyMap()
    private val closed = AtomicBoolean(false)

    init {
        eventBus.register(this)
        originHealthStatusMonitor.addOriginStatusListener(this)
        eventQueue = QueueDrainingEventProcessor(this, true)
    }

    /**
     * Registers origins with this inventory. Connection pools will be created for them and added to the "active" set,
     * they will begin being monitored, and event bus subscribers will be informed that the inventory state has changed.
     *
     * @param newOrigins origins to add
     */
    fun setOrigins(newOrigins: Set<Origin>) {
        checkArgument(newOrigins.isNotEmpty(), "origins list is null or empty")
        eventQueue.submit(SetOriginsEvent(newOrigins))
    }

    @VisibleForTesting
    fun setOrigins(vararg origins: Origin) = setOrigins(ImmutableSet.copyOf(origins))

    fun closed(): Boolean = closed.get()

    override fun snapshot(): Iterable<RemoteHost> = pools(Active)

    override fun addOriginsChangeListener(listener: OriginsChangeListener) = inventoryListeners.addListener(listener)

    override fun onCommand(enableOrigin: EnableOrigin) = eventQueue.submit(EnableOriginCommand(enableOrigin))

    override fun onCommand(disableOrigin: DisableOrigin) = eventQueue.submit(DisableOriginCommand(disableOrigin))

    override fun onCommand(getOriginsInventorySnapshot: GetOriginsInventorySnapshot) = notifyStateChange()

    override fun monitoringEnded(origin: Origin) {
        // Do nothing
    }

    override fun originHealthy(origin: Origin) = eventQueue.submit(OriginHealthEvent(origin, Healthy))

    override fun originUnhealthy(origin: Origin) = eventQueue.submit(OriginHealthEvent(origin, Unhealthy))

    override fun close() = eventQueue.submit(CloseEvent)

    override fun submit(event: Any) {
        if (event is OriginEvent) {
            when (event) {
                is SetOriginsEvent -> handleSetOriginsEvent(event)
                is OriginHealthEvent -> handleOriginHealthEvent(event)
                is EnableOriginCommand -> handleEnableOriginCommand(event)
                is DisableOriginCommand -> handleDisableOriginCommand(event)
                CloseEvent -> handleCloseEvent()
            }
        }
    }

    private fun handleSetOriginsEvent(event : SetOriginsEvent) {
        val newOriginsMap : Map<Id, Origin> = event.newOrigins.associateBy { it.id() }

        val originChanges = OriginChanges()

        concat(origins.keys.stream(), newOriginsMap.keys.stream())
            .collect(Collectors.toSet())
            .forEach(
                Consumer { originId ->
                    val origin = newOriginsMap[originId]

                    when {
                        isNewOrigin(originId, origin) -> {
                            val monitoredOrigin = addMonitoredEndpoint(origin!!)
                            originChanges.addOrReplaceOrigin(originId!!, monitoredOrigin)
                        }
                        isUpdatedOrigin(originId, origin) -> {
                            val monitoredOrigin = changeMonitoredEndpoint(origin!!)
                            originChanges.addOrReplaceOrigin(originId!!, monitoredOrigin)
                        }
                        isUnchangedOrigin(originId, origin) -> {
                            log.info("Existing origin has been left unchanged. Origin=$appId:$origin")
                            originChanges.keepExistingOrigin(originId!!, origins[originId]!!)
                        }
                        isRemovedOrigin(originId, origin) -> {
                            removeMonitoredEndpoint(originId)
                            originChanges.noteRemovedOrigin()
                        }
                    }
                }
            )

        origins = originChanges.updatedOrigins()

        if (originChanges.changed()) {
            notifyStateChange()
        }
    }

    private fun handleCloseEvent() {
        if (closed.compareAndSet(false, true)) {
            origins.values.forEach(Consumer { host: MonitoredOrigin ->
                removeMonitoredEndpoint(
                    host.origin.id()
                )
            })
            origins = ImmutableMap.of()
            notifyStateChange()
            eventBus.unregister(this)
        }
    }

    private fun handleDisableOriginCommand(event: DisableOriginCommand) {
        if (event.disableOrigin.forApp(appId)) {
            onEvent(event.disableOrigin.originId(), event.disableOrigin)
        }
    }

    private fun handleEnableOriginCommand(event: EnableOriginCommand) {
        if (event.enableOrigin.forApp(appId)) {
            onEvent(event.enableOrigin.originId(), event.enableOrigin)
        }
    }

    private fun handleOriginHealthEvent(event: OriginHealthEvent) {
        if (originHealthStatusMonitor !is NoOriginHealthStatusMonitor) {
            onEvent(event.origin, event.healthStatus)
        }
    }

    private fun addMonitoredEndpoint(origin: Origin): MonitoredOrigin {
        val monitoredOrigin = MonitoredOrigin(origin)
        monitoredOrigin.startMonitoring()
        log.info("New origin added and activated. Origin={}:{}", appId, monitoredOrigin.origin.id())
        return monitoredOrigin
    }

    private fun changeMonitoredEndpoint(origin: Origin): MonitoredOrigin {
        val oldHost: MonitoredOrigin? = origins[origin.id()]
        oldHost!!.close()
        val newHost = MonitoredOrigin(origin)
        newHost.startMonitoring()
        log.info("Existing origin has been updated. Origin={}:{}", appId, newHost.origin)
        return newHost
    }

    private fun removeMonitoredEndpoint(originId: Id) {
        val host: MonitoredOrigin? = origins[originId]
        host!!.close()
        log.info("Existing origin has been removed. Origin={}:{}", appId, host.origin.id())
    }

    private fun isNewOrigin(originId: Id, newOrigin: Origin?): Boolean {
        return nonNull(newOrigin) && !origins.containsKey(originId)
    }

    private fun isUnchangedOrigin(originId: Id, newOrigin: Origin?): Boolean {
        val oldOrigin: MonitoredOrigin? = origins[originId]
        return nonNull(oldOrigin) && nonNull(newOrigin) && oldOrigin!!.origin == newOrigin
    }

    private fun isUpdatedOrigin(originId: Id, newOrigin: Origin?): Boolean {
        val oldOrigin: MonitoredOrigin? = origins[originId]
        return nonNull(oldOrigin) && nonNull(newOrigin) && oldOrigin!!.origin != newOrigin
    }

    private fun isRemovedOrigin(originId: Id, newOrigin: Origin?): Boolean {
        val oldOrigin: MonitoredOrigin? = origins[originId]
        return nonNull(oldOrigin) && isNull(newOrigin)
    }

    private fun onEvent(origin: Origin, event: Any) {
        onEvent(origin.id(), event)
    }

    private fun onEvent(originId: Id, event: Any) {
        val monitoredOrigin: MonitoredOrigin? = origins[originId]
        monitoredOrigin?.onEvent(event)
    }

    private fun notifyStateChange() {
        val event = OriginsSnapshot(
            appId,
            pools(Active),
            pools(Inactive),
            pools(Disabled)
        )
        inventoryListeners.announce().originsChanged(event)
        eventBus.post(event)
    }

    private fun pools(state: OriginState): Collection<RemoteHost> =
        origins.values
            .filter { it.state() == state }
            .map { origin ->
                val hostClient = HttpHandler { request, context ->
                    Eventual(origin.hostClient.sendRequest(request, context))
                }
                remoteHost(origin.origin, hostClient, origin.hostClient)
            }
}

private sealed class OriginEvent
private object CloseEvent : OriginEvent()
private data class SetOriginsEvent(val newOrigins: Set<Origin>) : OriginEvent()
private data class OriginHealthEvent(val origin: Origin, val healthStatus: OriginHealthStatus) : OriginEvent()
private data class EnableOriginCommand(val enableOrigin: EnableOrigin) : OriginEvent()
private data class DisableOriginCommand(val disableOrigin: DisableOrigin) : OriginEvent()


private sealed class OriginHealthStatus
private object Healthy : OriginHealthStatus()
private object Unhealthy : OriginHealthStatus()

private class MonitoredOrigin(val origin: Origin) {
    val hostClient: StyxHostHttpClient = TODO()

    fun state(): OriginState {
        TODO()
    }

    fun close() {
        TODO()
    }
    
    fun startMonitoring() {
        TODO()
    }
    
    fun stopMonitoring() {
        TODO()
    }

    @Synchronized
    fun onEvent(event: Any) {
        TODO()
    }
}

private sealed class OriginState(val gaugeValue: Int)
private object Active : OriginState(1)
private object Inactive : OriginState(0)
private object Disabled : OriginState(-1)

private class OriginChanges {
    var monitoredOrigins: ImmutableMap.Builder<Id, MonitoredOrigin> = ImmutableMap.builder()
    var changed = AtomicBoolean(false)

    fun addOrReplaceOrigin(originId: Id, origin: MonitoredOrigin) {
        monitoredOrigins.put(originId, origin)
        changed.set(true)
    }

    fun keepExistingOrigin(originId: Id, origin: MonitoredOrigin) {
        monitoredOrigins.put(originId, origin)
    }

    fun noteRemovedOrigin() {
        changed.set(true)
    }

    fun changed(): Boolean {
        return changed.get()
    }

    fun updatedOrigins(): Map<Id, MonitoredOrigin> {
        return monitoredOrigins.build()
    }
}