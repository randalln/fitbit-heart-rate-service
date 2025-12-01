package org.noblecow.hrservice.data.repository

import co.touchlab.kermit.Logger
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.error_advertise
import heartratemonitor.composeapp.generated.resources.error_web_server
import org.jetbrains.compose.resources.StringResource
import org.noblecow.hrservice.data.source.local.AdvertisingState
import org.noblecow.hrservice.data.source.local.WebServerState

sealed class ServicesState {
    data object Starting : ServicesState(), ServicesTransitionState
    data object Started : ServicesState()
    data object Stopping : ServicesState(), ServicesTransitionState
    data object Stopped : ServicesState()
    data class Error(val text: StringResource) : ServicesState()
}

internal interface ServicesTransitionState

internal class ServicesStateMachine(private val logger: Logger) {
    fun nextState(
        previousState: ServicesState,
        advertisingState: AdvertisingState,
        webServerState: WebServerState
    ): ServicesState = getErrorState(advertisingState, webServerState)
        ?: getNormalState(previousState, advertisingState, webServerState)

    private fun getErrorState(
        advertisingState: AdvertisingState,
        webServerState: WebServerState
    ): ServicesState? = when {
        advertisingState is AdvertisingState.Failure ->
            ServicesState.Error(Res.string.error_advertise)

        webServerState.error != null ->
            ServicesState.Error(Res.string.error_web_server)

        else -> null
    }

    /**
     * Determines the next ServicesState based on the current state of both services (Bluetooth and web server).
     *
     * This function treats Bluetooth advertising and the web server as a unified service:
     * - Both must be ready for the overall state to be [ServicesState.Started]
     * - Both must be stopped for the overall state to be [ServicesState.Stopped]
     * - Any intermediate combination (one ready, one not) is a transition state
     *
     * The [previousState] determines the direction of transition when services are in mixed states:
     * - If transitioning from [ServicesState.Started], we're shutting down → [ServicesState.Stopping]
     * - If transitioning from [ServicesState.Stopped], we're starting up → [ServicesState.Starting]
     * - If already in a transition state, preserve the direction (Starting stays Starting, Stopping stays Stopping)
     *
     * @param previousState The previous ServicesState before this state change
     * @param advertisingState Current Bluetooth advertising state (Started, Stopped, or Failure)
     * @param webServerState Current web server state (isReady flag and optional error)
     * @return The next ServicesState based on the combination of service states
     */
    private fun getNormalState(
        previousState: ServicesState,
        advertisingState: AdvertisingState,
        webServerState: WebServerState
    ): ServicesState {
        val result = when (advertisingState) {
            AdvertisingState.Stopped if !webServerState.isReady -> {
                ServicesState.Stopped
            }

            AdvertisingState.Started if webServerState.isReady -> {
                ServicesState.Started
            }

            else -> {
                when (previousState) {
                    ServicesState.Started -> ServicesState.Stopping
                    ServicesState.Stopping -> ServicesState.Stopping
                    ServicesState.Starting -> ServicesState.Starting
                    ServicesState.Stopped -> ServicesState.Starting
                    is ServicesState.Error -> ServicesState.Starting
                }
            }
        }

        logger.d {
            "State calculation: advertisingState=$advertisingState, " +
                "webServerReady=${webServerState.isReady}, " +
                "previousState=$previousState -> result=$result"
        }

        return result
    }
}
