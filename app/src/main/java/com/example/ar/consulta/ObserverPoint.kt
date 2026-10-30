package com.example.ar.consulta

/**
 * Dónde está la antena que hay que orientar. No es necesariamente donde está
 * el usuario: ese es justamente el punto de la consulta remota.
 */
data class ObserverPoint(
    val lat: Double,
    val lon: Double,
    val label: String
)
