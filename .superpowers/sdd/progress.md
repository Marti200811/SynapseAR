# Progreso — Anuncios recompensados en SynapseAR

Plan: `docs/superpowers/plans/2026-09-08-rewarded-ads.md`
Rama: `feature/rewarded-ads`
BASE (commit desde el que arranca la rama): `385d8b2`

## Decisiones de pre-vuelo (tomadas con el usuario)

- Se trabaja en la rama `feature/rewarded-ads`, no en `master` ni en un worktree
  (los worktrees causaron el bug del paywall en Oráculo).
- `SanityTest` se crea en la Task 1 y se borra en la Task 2, una vez que existen
  los 8 tests reales de `AccessRulesTest`.

## Estado

- [x] Task 1 — Habilitar los tests unitarios — **completa** (commit `cd53324`, revisión limpia)
- [x] Task 2 — `Feature` y `AccessRules` — **completa** (commit `8bb3f5f`, 8/8 tests, revisión limpia)
- [x] Task 3 — `UnlockStore` y `AccessManager` — **completa** (commit `ac71c2a`, revisión limpia)
- [x] Task 4 — Migrar los 5 call sites — **completa** (commits `e8b9822`..`975393a`, revisión limpia tras 1 fix)
- [x] Task 5 — `RewardedAdManager` y config de AdMob — **completa** (commit `e96f007`, debug y
  release compilan, revisión limpia). Bloqueo resuelto: la unidad recompensada se creó el
  2026-09-08 → **`ca-app-pub-5417760954645863/1848125012`** ("Recompensado desbloqueo", puja de
  partner sin marcar). Las unidades nuevas pueden tardar hasta 1 hora en servir anuncios.

<details><summary>Detalle del bloqueo original (resuelto)</summary>

  Falta el ID de una unidad **Recompensada** en la app `~5110678257`. Se intentó crearla desde
  el navegador el 2026-09-08 y la consola de AdMob quedó colgada cargando en 6 intentos
  seguidos (`Script injection timed out` / `document_idle` a los 45 s). No es un problema del
  código ni de la cuenta: la SPA de AdMob no respondía.

  **Para desbloquear:** crear la unidad a mano en AdMob → app "SynapseAR: Antena Parabólica"
  (`ca-app-pub-5417760954645863~5110678257`) → Bloques de anuncios → Añadir → **Bonificado /
  Recompensado** → nombre sugerido "Recompensado desbloqueo" → dejar **"Puja de partner" SIN
  marcar** (no se puede cambiar después) → copiar el ID resultante.

  Ese ID va en `app/src/release/res/values/admob.xml`. El de debug ya está definido en el brief
  y es el de prueba público de Google, no hace falta esperar para esa parte.

  ⚠️ Las tareas 6 y 7 dependen de la 5: `RewardedAdManager` lee `R.string.rewarded_ad_unit_id`,
  y ese string tiene que existir en ambos flavors o el build de release no compila. No se puede
  saltear con un placeholder: un ID inválido hace que los anuncios nunca carguen, en silencio.
</details>
- [x] Task 6 — La opción de anuncio en `UpgradeDialog` — **completa** (commit `f93446e`, debug y
  release compilan, revisión con 1 hallazgo Menor anotado abajo)
- [x] Task 7 — Ejecutar la acción bloqueada tras la recompensa — **completa** (commits
  `a1938f3`..`7d74d6b`, revisión limpia tras 1 fix Importante). El fix agregó guards `isAdded`
  en los 5 callbacks asíncronos, dejando el desbloqueo y el analytics FUERA del guard (con
  `applicationContext`) para que el usuario reciba su recompensa aunque el fragment haya muerto.

## Hallazgos menores acumulados (para la revisión final)

- **[Task 1, preexistente]** `app/src/androidTest/java/com/example/ar/ExampleInstrumentedTest.kt`
  afirma `assertEquals("com.example.synapsear", appContext.packageName)`, pero el
  `applicationId` real es `com.quantixlabs.synapsear`. Además `androidTest` tampoco tiene
  dependencias declaradas, así que ese archivo ni siquiera compila. Fuera del alcance del plan
  (el brief solo cubría `src/test`). Decidir en la revisión final si se arregla o se borra.
- **[Task 4, Menor]** En `SatellitePickerDialog.kt`, el parámetro del `SatelliteAdapter` sigue
  llamándose `isPro` (y su variable interna `locked = !isPro && !isFree(sat)`), aunque el valor
  que recibe ahora puede venir de un desbloqueo temporal. El valor es correcto; solo el nombre
  quedó desactualizado. Renombrarlo a `hasAccess` sería consistente con el call site.
- **[Task 4, Menor]** En `CompassFragment.kt`, los comentarios "(Pro)" de los listeners de la
  brújula ahora son imprecisos (el acceso puede venir de un anuncio), aunque no falsos.
- **[Task 6, Menor — MANDADO POR EL PLAN]** En `UpgradeDialog.kt:69-81`, los callbacks
  `onEarned`/`onFailed` llaman a `requireContext()`, `dismiss()` y muestran un Toast **sin
  chequear `isAdded`**. Corren 15-30 s después, cuando el fragment podría haberse desasociado.
  El mismo archivo ya tiene el guard correcto para un caso análogo
  (`setPriceListener { if (isAdded) ... }`), así que la inconsistencia es visible.
  Riesgo real bajo: las activities están fijadas a `portrait`, lo que descarta la rotación.
  **Este código es copia textual del brief** que escribió el controlador, no una desviación del
  implementador → decisión del usuario en la revisión final. Fix propuesto: envolver el cuerpo
  de ambos callbacks en `if (isAdded) { ... }`.
- **[observación, fuera de alcance]** `upgrade_title` sigue diciendo "Synapse AR Pro" en los 3
  idiomas, pero la app se renombró a "Antena AR" / "Antenna AR". Es el título del diálogo de
  compra, visible para el usuario.
- **[Task 7, informativo]** El `onUnlocked` del tab AR en `MainActivity` no tiene guard análogo,
  pero `MainActivity` no es un Fragment y `isAdded` no aplica. Si la Activity se destruyera
  durante la ventana del anuncio, `navController.navigate()` podría fallar igual. No estaba en
  el alcance del hallazgo; queda como dato.

**NOTA para la revisión final:** el hallazgo Menor de la Task 6 (guards `isAdded` faltantes en
`UpgradeDialog`) **ya quedó resuelto** por el fix de la Task 7, que lo cubrió junto con los otros
4 lugares. No hace falta volver a tratarlo.

## Revisión final de la rama — cerrada

Alcance revisado: `385d8b2..7d74d6b` (20 archivos, +411/−41). Veredicto: cumple el spec completo,
**0 hallazgos Críticos**, 4 Importantes, 6 Menores. Confirmó que no hay regresión para los
usuarios actuales: el banner sigue atado a `sharedVm.isPro` (un desbloqueo temporal NO lo oculta),
`BillingManager` quedó intacto, y el gate de AR mejora (lectura sincrónica de prefs en lugar de un
LiveData que arrancaba en `false`).

Los 4 Importantes se arreglaron en `d61f675` y `1b4a46a`:

- **I-1** — El Toast decía "no se pudo cargar" cuando el usuario cerraba el anuncio a propósito,
  que es el caso más frecuente. `show()` pasó de 2 callbacks a 3: `onEarned` / `onCancelled` /
  `onFailed`. Cancelar no muestra nada y deja el diálogo abierto con la opción de comprar.
- **I-2** — `addOnDestinationChangedListener` despacha el destino actual apenas se registra, y
  estaba registrado antes de inicializar `billingManager` y `rewardedAdManager`. Con el proceso
  muerto en la tab AR y el desbloqueo vencido → `UninitializedPropertyAccessException` al abrir
  la app. Se movió debajo de ambas inicializaciones (`MainActivity.kt:109`).
- **I-3** — El trabajo de UI corría dentro de `onUserEarnedReward`, o sea con el anuncio todavía
  en pantalla. Hoy no rompía sólo porque la `AdActivity` de AdMob es translúcida y la Activity
  queda *paused*, no *stopped*. Se movió a `onAdDismissedFullScreenContent`, que es donde Google
  lo pide. **Esto destraba activar mediación o puja de partner más adelante**; antes eran una
  bomba de tiempo (activities opacas de terceros → `IllegalStateException` en los 5 `commit()`).
- **I-4** — `UpgradeDialog` llama a `preload()` por su cuenta si no hay anuncio listo, salteando
  el gate de consentimiento UMP si el diálogo se abre antes de que el consentimiento se resuelva.
  Se agregó `RewardedAdManager.adsInitialized`, que prende `MainActivity` en el callback de
  `MobileAds.initialize()`.

También se borró `ExampleInstrumentedTest.kt` (afirmaba el package name viejo y `androidTest` ni
compila por falta de dependencias).

Verificación tras los fixes: `compileDebugKotlin`, `compileReleaseKotlin` y `testDebugUnitTest`
en `BUILD SUCCESSFUL`, 8/8 de `AccessRulesTest` con `--rerun-tasks`.

## Verificación de los arreglos post-revisión (2026-09-08/09)

Los commits `d61f675` y `1b4a46a` se hicieron **después** de la revisión final, así que nadie
los había revisado y estaban por entrar a master. Se les corrió una verificación adversarial
aparte (5 lentes + refutadores). **Encontró que el arreglo de I-3 había introducido dos
regresiones propias.** Ambas corregidas en `ec29d4e`:

- **La recompensa dejó de ser durable.** Al mover el trabajo de `onUserEarnedReward` a
  `onAdDismissedFullScreenContent`, `grantTemporary()` pasó a correr recién al cerrarse el
  anuncio. Si el sistema mata el proceso mientras está el end card — un Moto G15 con la app
  en background lo hace — el dismissal nunca llega, el flag `earned` muere en memoria y nada
  se escribe en `synapse_prefs`. El usuario mira el anuncio entero, AdMob ya contabilizó la
  impresión y la recompensa, y él se queda sin los 30 minutos. Es exactamente lo que el
  comentario original del `applicationContext` protegía.
- **Doble toque rompía el anuncio en curso.** `rewardedAd` no se anulaba antes de `ad.show()`,
  así que un segundo toque tomaba el mismo objeto, reasignaba `fullScreenContentCallback` a
  otro closure con su propio `earned = false`, y el `show()` duplicado disparaba
  `onAdFailedToShowFullScreenContent` → Toast de error encima del video que el usuario estaba
  mirando.

**Diseño corregido (`ec29d4e`):** `show()` pasó a 4 callbacks porque son dos momentos distintos
y mezclarlos es justamente lo que falló:

| Callback | Cuándo | Qué va acá |
|---|---|---|
| `onRewardEarned` | `onUserEarnedReward`, anuncio en pantalla | **Persistir.** Nada de UI |
| `onClosedAfterReward` | dismissal, habiendo ganado | La UI (navegar, `dismiss()`) |
| `onClosedWithoutReward` | dismissal, sin ganar | Nada — no es un error |
| `onFailedToShow` | no se pudo mostrar | El Toast |

Más un guard `showing` y `rewardedAd = null` antes de `ad.show()` para el doble toque.

Así se cumplen las dos restricciones a la vez: la recompensa se persiste en el instante más
temprano posible (sobrevive a que muera el proceso) y la UI corre recién con el anuncio
cerrado (no rompe si algún día se activa mediación).

> ⚠️ **Lección de proceso:** los arreglos que salen *después* de la revisión final no están
> revisados. Este los tuvo que verificar aparte y aparecieron dos regresiones importantes.
> No mergear arreglos post-revisión sin pasarlos por el mismo filtro.

## Pendiente antes de publicar (NO bloquea el merge)

1. **`versionCode` sigue en 14, que ya está publicado.** Hay que subirlo a 15.
2. **Probar en el Motorola (ZY32LK2337)** con `DEBUG_FORCE_FREE=true` temporal en `ProManager.kt`
   — ⚠️ revertir antes de commitear. Mirar en particular el hallazgo **M-5**: el diálogo no tiene
   `ScrollView` y el botón nuevo de 56dp podría recortar "Tal vez después" en pantallas chicas.
3. **`upgrade_title` dice "Synapse AR Pro"** en los 3 idiomas, pero la app se llama "Antena AR" /
   "Antenna AR". La revisión recomendó subirle la prioridad: ese diálogo ahora lo va a ver mucha
   más gente que antes.
4. **`CLAUDE.md` tiene mal el AdMob app ID**: dice `~1056694271`, el manifest declara
   `~5110678257`. La memoria quedó desactualizada tras el arreglo de la v14.

## Backlog (Menores, ninguno bloquea)

- **M-1** — Los cuerpos de acción están duplicados entre la rama `if` y el `onUnlocked` de los 5
  call sites. Un helper `Fragment.requireAccess(feature, source) { }` los colapsaría.
- **M-3** — Un anuncio recompensado compra un tema de brújula **para siempre** (queda persistido
  en prefs), no por 30 minutos. Decisión de producto, no bug.
- **M-4** — Doble toque rápido cuenta `rewarded_started` dos veces.
- **M-6** — `remainingMillis()` no tiene consumidor: no hay UI que muestre cuánto queda del
  desbloqueo. El usuario no sabe que tiene 30 minutos ni cuándo se le vencen.
- `SatelliteAdapter(isPro)` quedó mal nombrado (`hasAccess` sería lo correcto) y
  `SatellitePickerDialog.kt:142` pinta un "Pro" hardcodeado sin traducir.
