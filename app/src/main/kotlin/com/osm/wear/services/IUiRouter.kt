package com.osm.wear.services

import kotlinx.coroutines.flow.SharedFlow

interface IUiRouter {
    val routingEvents: SharedFlow<String>
    fun routeTo(route: String)
}
