# Anuncios recompensados en SynapseAR — diseño

**Fecha:** 2026-09-08
**Estado:** aprobado, pendiente de plan de implementación

## Problema

SynapseAR monetiza hoy con un banner en la pantalla principal y una compra Pro única de
US$ 3,99. El banner rinde muy poco: la app se usa parado en un techo con el teléfono en alto,
donde nadie mira la franja de abajo.

Al mismo tiempo, la compra Pro **no se puede cobrar**: el perfil de pagos de Play Console está
incompleto y completarlo requiere una inscripción fiscal que el usuario no quiere hacer hasta
saber si la app vende algo.

Un usuario gratis que necesita orientar hacia un satélite fuera de los 3 incluidos hoy se queda
sin nada: no puede pagar (el flujo está roto) y no tiene alternativa. Se va y no vuelve.

## Objetivo

**Generar ingresos ahora, aun a costa de canibalizar la compra Pro.** AdMob es un canal de cobro
independiente de Play Billing, así que es el único que puede producir algo mientras la situación
fiscal no se resuelva.

Decisión explícita del usuario, tomada sabiendo que un premio generoso reduce la razón de comprar
Pro.

## Decisiones de producto

| Decisión | Valor | Razón |
|---|---|---|
| Qué desbloquea | **Un anuncio por cada función bloqueada** | Maximiza impresiones por sesión (2-3 en vez de 1) |
| Cuánto dura | **30 minutos**, persistido en disco | Cubre una sesión de orientar una antena con margen, y sobrevive si Android mata la app en segundo plano |
| Tope diario | **Ninguno** | El límite natural ya existe: no se puede mirar un anuncio por una función ya desbloqueada, así que el máximo son 5 anuncios cada 30 min |
| Banner | **Se queda** | Dos canales en vez de uno; ya está implementado y funcionando |

Funciones que se pueden desbloquear (las mismas 5 que hoy están detrás de Pro):

- `AR` — tab de realidad aumentada
- `COMPASS_THEMES` — temas de la brújula
- `WIFI_SCANNER` — escáner de redes WiFi
- `TDT_PICKER` — selector de transmisores TDT
- `SATELLITE` — satélites fuera de los 3 gratis

**`SATELLITE` es una sola función, no uno por satélite.** Un anuncio desbloquea *todos* los
satélites durante 30 minutos, no solamente el que el usuario había tocado. Hacerlo por satélite
individual obligaría a mirar un anuncio por cada intento de un instalador que compara varios, lo
que es hostil y además complicaría el almacenamiento (una clave por satélite en vez de una).

## Arquitectura

Se eligió una **fachada única** (`AccessManager`) sobre las alternativas de extender `ProManager`
o dejar la condición `isPro || desbloqueado` repetida en cada call site.

Razón: con 5 gates distintos, que cada uno arme su propia condición booleana es donde aparecen los
agujeros. Ya ocurrió en el proyecto hermano Oráculo Pitagórico, donde
`adAvailable = isAdReady && canWatchAdForBonus()` mezclaba "el SDK tiene un anuncio" con "al
usuario le quedan bonos", y el resultado fueron consultas ilimitadas gratis evadiendo el límite y
la suscripción.

### Componentes

| Componente | Responsabilidad | Depende de |
|---|---|---|
| `Feature` (enum) | Nombra las 5 funciones desbloqueables | — |
| `AccessManager` | `canUse(ctx, feature)`: es Pro **o** tiene desbloqueo vigente. `grantTemporary(ctx, feature)` | `ProManager`, `UnlockStore` |
| `UnlockStore` | Lee y escribe los vencimientos en `SharedPreferences` | — |
| `RewardedAdManager` | Carga y muestra el anuncio; avisa cuando se ganó la recompensa | SDK de AdMob |

`ProManager` **no se modifica**. Sigue siendo únicamente el estado de la compra; `AccessManager`
lo consulta.

### Contrato de `AccessManager`

```kotlin
fun canUse(context: Context, feature: Feature): Boolean
fun grantTemporary(context: Context, feature: Feature)
fun remainingMillis(context: Context, feature: Feature): Long
```

Un solo punto de decisión. Si en el futuro aparece otra forma de acceso (código promocional,
prueba gratuita), se agrega adentro sin tocar ningún call site.

### Persistencia

Una clave por función en el archivo `synapse_prefs` (el que ya usa `ProManager`):

```
unlock_until_AR = 1757012345678   // epoch millis
```

La comprobación es `System.currentTimeMillis() < valor`. No se limpian los vencidos: un valor
viejo simplemente falla la comparación.

*Limitación aceptada:* atrasar el reloj del dispositivo extiende el desbloqueo. No se defiende —
es acceso a una función, no dinero.

## Flujo

1. El usuario toca una función bloqueada
2. `AccessManager.canUse()` devuelve `false` → se abre `UpgradeDialog` con el `source` que ya se
   pasa hoy para analytics
3. El diálogo ofrece tres opciones:
   - `✨ Desbloquear Pro — US$ 3,99`
   - `▶ Ver un anuncio y usar [función] por 30 min`
   - `Restaurar compra`
4. Si toca ver el anuncio y **gana la recompensa** → `grantTemporary(feature)` y **se ejecuta
   automáticamente la acción que estaba bloqueada**, sin obligarlo a volver a tocar. Qué significa
   "la acción" en cada caso:

   | Función | Qué pasa al ganar la recompensa |
   |---|---|
   | `AR` | Navega al tab de realidad aumentada |
   | `COMPASS_THEMES` | Abre el selector de temas |
   | `WIFI_SCANNER` | Abre el escáner de WiFi |
   | `TDT_PICKER` | Abre el selector de transmisores TDT |
   | `SATELLITE` | Selecciona el satélite que el usuario había tocado y cierra el selector |
5. Si cierra el anuncio antes de completarlo → sin recompensa, sin desbloqueo, vuelve al diálogo

No se crea un diálogo nuevo: `UpgradeDialog` ya es el punto donde se ofrece la elección, igual que
`OracleLimitDialog` en Oráculo.

## Manejo de errores

**Si no hay anuncio cargado, el botón no se renderiza.** No se muestra deshabilitado con texto de
carga.

Ese es el error exacto que hubo que corregir en Oráculo: un botón permanentemente deshabilitado
que decía "Cargando anuncio…" cuando la razón real era otra, y que nunca se habilitaba.

- Si el anuncio falla al mostrarse: Toast y el diálogo queda abierto con la opción de comprar Pro
- Tras consumir un anuncio se precarga el siguiente

## Consentimiento

La app ya tiene el flujo UMP para GDPR y `MobileAds.initialize()` corre **después** del
consentimiento. `RewardedAdManager` no puede precargar antes de eso: se engancha al mismo callback
que hoy dispara la carga del banner en `MainActivity.initializeMobileAds()`.

## Analytics

Se extiende el `Analytics.kt` existente, con el mismo parámetro `source` que ya usan los eventos
del embudo de compra:

| Evento | Cuándo |
|---|---|
| `rewarded_offered` | El diálogo mostró la opción de anuncio |
| `rewarded_started` | El usuario tocó "Ver un anuncio" |
| `rewarded_earned` | Completó el anuncio y ganó el desbloqueo |

Combinados con el `upgrade_button_tapped` que ya existe, permiten responder algo que hoy no se
sabe: **de los que llegan al bloqueo, qué proporción elige el anuncio y qué proporción elige
pagar.** Ese ratio indica si los anuncios están canibalizando Pro o si simplemente estaban
perdiendo usuarios que nunca iban a pagar.

## Configuración de AdMob

Hay que crear una unidad **Recompensada** en la app `ca-app-pub-5417760954645863~5110678257` (la
verificada y vinculada a la ficha de Play; **no** la duplicada `~1056694271`, que tiene servicio
de anuncios limitado).

Se agrega `rewarded_ad_unit_id` siguiendo el mismo patrón por build type que ya usa el banner:

| Archivo | Valor |
|---|---|
| `app/src/debug/res/values/admob.xml` | `ca-app-pub-3940256099942544/5224354917` (ID de prueba oficial de Google) |
| `app/src/release/res/values/admob.xml` | La unidad recompensada real |

⚠️ **Nunca tocar el botón de anuncio en un build con IDs reales durante las pruebas.** Google
suspende cuentas por tráfico inválido cuando el desarrollador clickea sus propios anuncios.

## Testing

**Unitarios** (`AccessManagerTest`, lógica pura sin Android):

- Un usuario Pro puede usar todas las funciones sin desbloqueo
- Un desbloqueo vencido deniega el acceso
- Un desbloqueo vigente permite el acceso
- Desbloquear una función no desbloquea las otras
- `remainingMillis` devuelve 0 para una función sin desbloqueo

**Manual** en el Motorola (ZY32LK2337), con los IDs de prueba:

- Tocar cada una de las 5 funciones bloqueadas y verificar que aparece la opción de anuncio
- Completar un anuncio y confirmar que entra directo a la función
- Cerrar un anuncio a la mitad y confirmar que NO desbloquea
- Esperar el vencimiento y confirmar que vuelve a pedir

## Fuera de alcance

- Tope diario de anuncios (se decidió no ponerlo)
- Sacar o mover el banner
- Cambiar el precio o el modelo de Pro
- Intersticiales o anuncios nativos
