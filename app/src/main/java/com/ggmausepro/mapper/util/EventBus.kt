package com.ggmausepro.mapper.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple singleton event bus for inter-component communication.
 * Uses Kotlin SharedFlow for thread-safe, lifecycle-aware event delivery.
 */
object EventBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<Any>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private val subscribers = ConcurrentHashMap<Any, MutableList<EventSubscription<*>>>()

    private data class EventSubscription<T : Any>(
        val eventClass: Class<T>,
        val callback: (T) -> Unit
    )

    /**
     * Post an event to all registered subscribers.
     */
    fun post(event: Any) {
        scope.launch {
            _events.emit(event)
        }
        // Also deliver directly to callback-based subscribers
        deliverToCallbackSubscribers(event)
    }

    /**
     * Register a subscriber with a typed callback.
     */
    inline fun <reified T : Any> subscribe(subscriber: Any, noinline callback: (T) -> Unit) {
        subscribe(subscriber, T::class.java, callback)
    }

    /**
     * Register a subscriber with explicit event class.
     */
    fun <T : Any> subscribe(subscriber: Any, eventClass: Class<T>, callback: (T) -> Unit) {
        val subscription = EventSubscription(eventClass, callback)
        subscribers.getOrPut(subscriber) { mutableListOf() }.add(subscription)
    }

    /**
     * Register a subscriber object. The subscriber should have methods annotated or
     * can use the typed subscribe overload.
     */
    fun register(subscriber: Any) {
        if (!subscribers.containsKey(subscriber)) {
            subscribers[subscriber] = mutableListOf()
        }
    }

    /**
     * Unregister a subscriber, removing all its event subscriptions.
     */
    fun unregister(subscriber: Any) {
        subscribers.remove(subscriber)
    }

    /**
     * Deliver event to all callback-based subscribers.
     */
    @Suppress("UNCHECKED_CAST")
    private fun deliverToCallbackSubscribers(event: Any) {
        subscribers.values.forEach { subscriptionList ->
            subscriptionList.forEach { subscription ->
                if (subscription.eventClass.isInstance(event)) {
                    try {
                        (subscription as EventSubscription<Any>).callback(event)
                    } catch (e: Exception) {
                        android.util.Log.e("EventBus", "Error delivering event: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Clear all subscribers.
     */
    fun clearAll() {
        subscribers.clear()
    }

    // --- Event classes used throughout the app ---

    /** Fired when mapping active state changes. */
    data class MappingStateChanged(val isActive: Boolean)

    /** Fired when overlay edit mode changes. */
    data class EditModeChanged(val isEditing: Boolean)

    /** Fired when a key mapping is updated. */
    data class MappingUpdated(val keyCode: Int)

    /** Fired when profile is switched. */
    data class ProfileSwitched(val profileName: String)

    /** Fired to request toggling the mapping service. */
    object ToggleMappingRequest

    /** Fired to request opening the overlay editor. */
    object OpenEditorRequest

    /** Fired to request closing the overlay editor. */
    object CloseEditorRequest

    /** Fired when overlay transparency changes. */
    data class TransparencyChanged(val alpha: Float)

    /** Fired when mouse sensitivity changes. */
    data class SensitivityChanged(val sensitivity: Float)

    /** Fired when a key position is changed via drag. */
    data class KeyPositionChanged(val keyCode: Int, val newX: Float, val newY: Float)

    /** Fired to request the InputMapperService to stop. */
    object StopServiceRequest
}
