# CLAUDE_MEMORY
_Última actualización: 2026-06-26_

## Proyecto
- **Nombre**: SynapseAR
- **Stack**: Android / Kotlin + ViewBinding + Navigation Component
- **Ruta local**: `C:\Users\rodolfo.diaz\AndroidStudioProjects\SynapseAR`
- **applicationId**: `com.quantixlabs.synapsear`
- **Objetivo**: App orientación de antenas con guía AR, base de datos global de satélites, modo offline

## Estado actual (2026-06-26)
- **Última tarea**: Rediseño estético HUD aplicado al app real — funcionando en ZY32LK2337
- **versionCode en Play**: 7 / versionName 1.0.6 (subido a prueba cerrada)
- **APK debug en dispositivo**: v7 base + rediseño estético de la sesión 2026-06-26

## Pendiente para publicar
1. Fotografiar app real en dispositivo (sin cable USB) para screenshots Play Store
2. Setear `TESTING_MODE = false` en ProManager.kt antes de release público
3. Crear producto in-app `synapse_ar_pro` (PAGO ÚNICO $9.99) en Play Console si no existe
4. Build release AAB (versionCode=8): `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew bundleRelease`
5. Política de privacidad: https://marti200811.github.io/SynapseAR/privacy.html ✓ (ya hosteada)

## Decisiones arquitecturales clave
- OrientationManager: NO modificar (alpha=0.20, dual-matrix, 30Hz) — cualquier cambio rompe la brújula
- CompassView: diseño "aro rotante + aguja fija" (aro rota con azimut, aguja siempre apunta arriba)
- Material3 BottomNav: `itemIconTintList = null` en código + `setIcon()` por tab (XML `@null` solo no funciona)
- Brújula: siempre probar sin cable USB (el cable interfiere con el magnetómetro)
- MapFragment/ArFragment: usan `sharedVm.target` como fuente de verdad para líneas/overlays

## Convenciones
- Idioma del proyecto: español; respuestas de Claude: español, concisas
- JDK para compilar CLI: `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"` (o Android Studio JBR en `C:\Program Files\Android\Android Studio1\jbr`)
- ADB dispositivo: ZY32LK2337 (Motorola G15)
- Plan Claude: Pro — límite de tokens compartido entre Code y claude.ai
