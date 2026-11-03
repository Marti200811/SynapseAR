package com.example.ar

/**
 * Inventario de los diálogos de la app, en nombres literales de clase.
 *
 * Va escrito a mano y no por escaneo del classpath a propósito: así agregar un diálogo obliga
 * a pasar por acá y a decidirlo, en vez de que un escaneo lo cubra (o lo deje afuera) sin que
 * nadie lo mire. Son nombres literales porque es exactamente lo que Android guarda en el
 * estado y lo que le pasa después a `FragmentFactory`.
 *
 * Está en un solo lugar porque hay **dos** tests que recorren la misma lista
 * ([DialogosConstructorVacioTest] y [DialogosSinEstadoInyectadoTest]). Con una lista por test,
 * agregar un diálogo nuevo a una y olvidarse de la otra dejaría media red de seguridad.
 */
internal val DIALOGOS_DE_LA_APP = listOf(
    "com.example.ar.CalibrationDialog",
    "com.example.ar.SettingsDialog",
    "com.example.ar.UpgradeDialog",
    "com.example.ar.antenna.AntennaPickerDialog",
    "com.example.ar.astro.SunMoonDialog",
    "com.example.ar.satellite.SatellitePickerDialog",
    "com.example.ar.tdt.TdtPickerDialog",
    "com.example.ar.waypoint.WaypointDialog",
    "com.example.ar.wifi.WifiScannerDialog"
)
