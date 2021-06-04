package com.hotels.styx.client

import com.google.common.annotations.VisibleForTesting
import com.google.common.eventbus.EventBus
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.Id
import com.hotels.styx.api.Metrics.APPID_TAG
import com.hotels.styx.api.Metrics.ORIGINID_TAG
import com.hotels.styx.api.extension.*
import com.hotels.styx.api.extension.RemoteHost.remoteHost
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.client.connectionpool.ConnectionPool
import com.hotels.styx.client.connectionpool.ConnectionPools.simplePoolFactory
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor
import com.hotels.styx.client.origincommands.DisableOrigin
import com.hotels.styx.client.origincommands.EnableOrigin
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot
import com.hotels.styx.common.EventProcessor
import com.hotels.styx.common.Preconditions.checkArgument
import com.hotels.styx.common.QueueDrainingEventProcessor
import com.hotels.styx.common.StateMachine
import com.hotels.styx.common.StyxFutures.await
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.slf4j.LoggerFactory.getLogger
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean


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
    fun setOrigins(vararg origins: Origin) = setOrigins(setOf(*origins))

    fun closed(): Boolean = closed.get()

    fun origins(): List<Origin> = origins.values.map { it.origin }

    fun originCount(state: OriginState): Int {
        return origins.values.stream()
            .map { it.state() }
            .filter { state == it }
            .count()
            .toInt()
    }

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

    private fun handleSetOriginsEvent(event: SetOriginsEvent) {
        val oldAndNewOriginIds = (origins.keys.asSequence() + event.newOrigins.asSequence().map { it.id() })
            .distinct()

        val newOriginsMap: Map<Id, Origin> = event.newOrigins.associateBy { it.id() }

        val monitoredOrigins = HashMap<Id, MonitoredOrigin>()
        var changed = false

        oldAndNewOriginIds
            .map {
                originChange(it, newOriginsMap)
            }.forEach {
                when (it) {
                    is NewOriginAdded -> {
                        monitoredOrigins[it.originId] = addMonitoredEndpoint(it.origin)
                        changed = true
                    }
                    is OriginRemoved -> {
                        removeMonitoredEndpoint(it.originId)
                        changed = true
                    }
                    is OriginUnchanged -> {
                        log.info("Existing origin has been left unchanged. Origin=$appId:${it.origin}")
                        monitoredOrigins[it.originId] = origins[it.originId]!!
                    }
                    is OriginUpdated -> {
                        monitoredOrigins[it.originId] = changeMonitoredEndpoint(it.newOrigin)
                        changed = true
                    }
                }
            }

        origins = monitoredOrigins

        if (changed) {
            notifyStateChange()
        }
    }

    private fun originChange(originId: Id, newOriginsMap: Map<Id, Origin>): OriginChange {
        return if (newOriginsMap.containsKey(originId)) {
            if (origins.containsKey(originId)) {
                if (origins[originId]!!.origin == newOriginsMap[originId]) {
                    OriginUnchanged(origins[originId]!!.origin)
                } else {
                    OriginUpdated(newOriginsMap[originId]!!)
                }
            } else {
                NewOriginAdded(newOriginsMap[originId]!!)
            }
        } else if (origins.containsKey(originId)) {
            OriginRemoved(origins[originId]!!.origin)
        } else {
            throw IllegalStateException("Invalid origin update ID $originId")
        }
    }

    private fun handleCloseEvent() {
        if (closed.compareAndSet(false, true)) {
            origins.values.forEach {
                removeMonitoredEndpoint(it.origin.id())
            }
            origins = mapOf()
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
        return MonitoredOrigin(origin).apply {
            startMonitoring()
            log.info("New origin added and activated. Origin={}:{}", appId, this.origin.id())
        }
    }

    private fun changeMonitoredEndpoint(origin: Origin): MonitoredOrigin {
        disconnectOrigin(origin.id())

        return MonitoredOrigin(origin).apply {
            startMonitoring()
            log.info("Existing origin has been updated. Origin={}:{}", appId, this.origin)
        }
    }

    private fun removeMonitoredEndpoint(originId: Id) {
        val host = disconnectOrigin(originId)
        log.info("Existing origin has been removed. Origin={}:{}", appId, host.origin.id())
    }

    private fun disconnectOrigin(originId: Id) = origins[originId]!!.apply { close() }

    private fun onEvent(origin: Origin, event: Any) = onEvent(origin.id(), event)

    private fun onEvent(originId: Id, event: Any) {
        origins[originId]?.onEvent(event)
    }

    private fun notifyStateChange() {
        val event = OriginsSnapshot(appId, pools(Active), pools(Inactive), pools(Disabled))
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

    private inner class MonitoredOrigin(val origin: Origin) {
        private val gaugeName = "origin.status"
        private val connectionPool: ConnectionPool = hostConnectionPoolFactory.create(origin)
        val hostClient: StyxHostHttpClient = hostClientFactory.create(connectionPool)
        private val machine: StateMachine<OriginState> =
            StateMachine.Builder<OriginState>()
                .initialState(Active)
                .onInappropriateEvent<Any> { state, _ -> state }
                .onStateChange { oldState, newState, _ -> onStateChange(oldState, newState) }
                .transition(Active, Unhealthy::class.java) { Inactive }
                .transition(Inactive, Healthy::class.java) { Active }
                .transition(Active, DisableOrigin::class.java) { Disabled }
                .transition(Inactive, DisableOrigin::class.java) { Disabled }
                .transition(Disabled, EnableOrigin::class.java) { Inactive }
                .build()

        private var statusGauge: Gauge? = null

        init {
            registerMeters()
        }

        fun state(): OriginState = machine.currentState()

        fun close() {
            stopMonitoring()
            connectionPool.close()
            deregisterMeters()
        }

        fun startMonitoring() {
            originHealthStatusMonitor.monitor(setOf(origin))
        }

        fun stopMonitoring() {
            originHealthStatusMonitor.stopMonitoring(setOf(origin))
        }

        @Synchronized
        fun onEvent(event: Any) = machine.handle(event)

        private fun onStateChange(oldState: OriginState, newState: OriginState) {
            if (oldState != newState) {
                log.info("Origin state change: origin=\"$appId=${origin.id()}\", change=\"$oldState->$newState\"")
                if (newState == Disabled) {
                    stopMonitoring()
                } else if (oldState == Disabled) {
                    startMonitoring()
                }
                notifyStateChange()
            }
        }

        private fun registerMeters() {
            val gaugeTags = Tags.of(APPID_TAG, appId.toString(), ORIGINID_TAG, origin.id().toString())
            statusGauge = Gauge.builder(gaugeName) { state().gaugeValue }
                .tags(gaugeTags)
                .register(meterRegistry)
        }

        private fun deregisterMeters() {
            statusGauge?.let {
                meterRegistry.remove(it)
            }
        }
    }

    fun newOriginsInventoryBuilder(appId: Id) = Builder(appId)

    fun newOriginsInventoryBuilder(metricRegistry: MeterRegistry, backendService: BackendService) =
        Builder(backendService.id())
            .meterRegistry(metricRegistry)
            .connectionPoolFactory(simplePoolFactory(backendService, metricRegistry))
            .initialOrigins(backendService.origins())

    /**
     * A builder for {@link com.hotels.styx.client.OriginsInventory}.
     */
    class Builder(val appId: Id) {
        private var originHealthMonitor: OriginHealthStatusMonitor = NoOriginHealthStatusMonitor()
        private var meterRegistry: MeterRegistry? = null
        private var eventBus = EventBus()
        private var connectionPoolFactory = simplePoolFactory()
        private var hostClientFactory: StyxHostHttpClient.Factory? = null
        private var initialOrigins: Set<Origin> = emptySet()

        fun meterRegistry(meterRegistry: MeterRegistry): Builder = apply {
            this.meterRegistry = meterRegistry
        }

        fun connectionPoolFactory(connectionPoolFactory: ConnectionPool.Factory): Builder = apply {
            this.connectionPoolFactory = connectionPoolFactory
        }

        fun hostClientFactory(hostClientFactory: StyxHostHttpClient.Factory): Builder = apply {
            this.hostClientFactory = hostClientFactory
        }

        fun originHealthMonitor(originHealthMonitor: OriginHealthStatusMonitor): Builder = apply {
            this.originHealthMonitor = originHealthMonitor
        }

        fun eventBus(eventBus: EventBus): Builder = apply {
            this.eventBus = eventBus
        }

        fun initialOrigins(origins: Set<Origin>): Builder = apply {
            initialOrigins = HashSet(origins)
        }

        fun build(): OriginsInventory {
            await(originHealthMonitor.start())
            if (hostClientFactory == null) {
                hostClientFactory = StyxHostHttpClient.Factory { StyxHostHttpClient.create(it) }
            }
            checkNotNull(meterRegistry) { "metricRegistry is required" }
            val originsInventory = OriginsInventory(
                eventBus,
                appId,
                originHealthMonitor,
                connectionPoolFactory,
                hostClientFactory,
                meterRegistry
            )
            originsInventory.setOrigins(initialOrigins)
            return originsInventory
        }
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


sealed class OriginState(val gaugeValue: Int) {
    override fun toString(): String = javaClass.simpleName.toUpperCase()
}

object Active : OriginState(1)
object Inactive : OriginState(0)
object Disabled : OriginState(-1)

sealed class OriginChange(val originId: Id)
data class NewOriginAdded(val origin: Origin) : OriginChange(origin.id())
data class OriginUpdated(val newOrigin: Origin) : OriginChange(newOrigin.id())
data class OriginUnchanged(val origin: Origin) : OriginChange(origin.id())
data class OriginRemoved(val oldOrigin: Origin) : OriginChange(oldOrigin.id())
