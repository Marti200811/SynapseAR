# CLAUDE_MEMORY
_Última actualización: 2026-06-12_

## Proyecto
- **Nombre**: SynapseAR
- **Stack**: Android / Kotlin
- **Objetivo**: App de orientación de antenas con guía AR en tiempo real, base de datos global de satélites y funcionamiento offline
- **Play Console app ID**: `4974507974991322608`
- **Cuenta desarrollador**: Vectrix Labs (ID: 7909046132187984785)

## Arquitectura
- Permisos: ubicación (GPS), cámara (AR), sensores (giroscopio/brújula/acelerómetro), internet
- Funciones core: brújula en tiempo real, azimut/elevación, base de datos satelital, modo offline
- Target: instaladores profesionales + usuarios generales
- Distribución: Google Play Store

## IDs de producción
- **applicationId**: `com.quantixlabs.synapsear`
- **namespace**: `com.example.ar` (interno, no cambiar)
- **AdMob App ID**: `ca-app-pub-5417760954645863~1056694271`
- **AdMob Banner Ad Unit**: `ca-app-pub-5417760954645863/7302944941`
- **Maps API Key**: en `local.properties` (no está en Git)
- **Firebase project**: `synapsear-f606d`

## Modelo de negocio
- App gratuita con banner AdMob
- **Versión Pro: pago único $9.99 USD** (BillingManager usa `ProductType.INAPP`)
- Free tier: brújula básica + 3 satélites + mapa + banner
- Pro desbloquea: ~55 satélites, AR, WiFi Directional, TDT, temas visuales, sin ads
- Debug siempre Pro (BuildConfig.DEBUG)

## Keystore (firma de release)
- Ruta: fuera del repo — copiar manualmente a `C:/TU_USUARIO/keys/synapsear-release.jks`
- Alias: `synapsear`
- `keystore.properties` va en la raíz del proyecto (gitignored)

## Archivos fuera de Git (crear manualmente en cada PC)
1. `local.properties`:
   ```
   sdk.dir=C:\Users\TU_USUARIO\AppData\Local\Android\Sdk
   MAPS_API_KEY=AIzaSyByhABdIiWgalOJnKSYRVIX4lLlso_J2fE
   ```
2. `keystore.properties`:
   ```
   storeFile=C:/Users/TU_USUARIO/keys/synapsear-release.jks
   storePassword=***
   keyAlias=synapsear
   keyPassword=***
   ```

## JDK para compilar por CLI
```
JAVA_HOME=C:\Program Files\Android\Android Studio1\jbr
```

## Pendiente para publicar
1. Play Console — Perfil de pagos: seleccionar 7250-7328-6801
2. Play Console — Producto in-app: crear `synapse_ar_pro` (PAGO ÚNICO, $9.99)
3. Play Console — Política de privacidad: `https://marti200811.github.io/SynapseAR/privacy.html`
4. Play Console — Subir capturas de pantalla + feature graphic (1024×500)
5. Bundle de release: `gradlew bundleRelease` → `app\build\outputs\bundle\release\app-release.aab`

## URLs importantes
- GitHub: `https://github.com/Marti200811/SynapseAR.git`
- Política de privacidad: `https://marti200811.github.io/SynapseAR/privacy.html`
- Términos: `https://marti200811.github.io/SynapseAR/terms.html`
- Landing: `https://marti200811.github.io/SynapseAR/`
- Email soporte: `SupportQuantixLabs@gmail.com`

## Convenciones
- Idioma principal del proyecto: español
- Respuestas de Claude: español, concisas

## Bugs resueltos (sesión 2026-06-11)
- **SatelliteCalculator azimut**: `atan2(sin(B), -sin(lat)*cos(B))` — válida para todos los cuadrantes
- **Clarke belt / elevación**: HORIZONTAL: `pitchElevation = -pitch`; VERTICAL: `pitchElevation = pitch`
- **Sensor jitter**: EMA alpha=0.05, sensor 50Hz, notificación UI 15Hz (throttle 67ms), wrap-around azimut
- **Near-center snap**: `isAligned=true` → reticulo se clava en (w/2, h/2)
- **CPU/calor**: `updateSensorData()` agrupa 5 setters en un único `invalidate()`
- **Pantalla activa**: `FLAG_KEEP_SCREEN_ON` en MainActivity.onCreate()
- **Vibración haptic**: al hacer lock en Brújula, AR y Mapa

## Bases de datos
- **Satélites**: ~55 totales (DirecTV, Dish, ViaSat, Galaxy, Anik/Canadá, ChinaSat, AsiaSat, Arabsat, etc.)
- **TDT**: +25 países (Centroamérica, Caribe, Rusia, Nórdicos, Europa del Este, Turquía, Oriente Medio, África, SE Asia)
