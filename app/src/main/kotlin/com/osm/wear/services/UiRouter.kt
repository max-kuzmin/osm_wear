package com.osm.wear.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

class UiRouter @Inject constructor() : IUiRouter {
    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val routingEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    override fun routeTo(route: String) {
        _navigationEvents.tryEmit(route)
    }
}
