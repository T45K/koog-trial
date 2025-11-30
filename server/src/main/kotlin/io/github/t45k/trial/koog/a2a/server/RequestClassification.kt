package io.github.t45k.trial.koog.a2a.server

sealed interface RequestClassification {
    data class Greeting(val message: String): RequestClassification
    data class WeatherSearch(val date: String?, val location: String?): RequestClassification
    data object Other: RequestClassification
}
