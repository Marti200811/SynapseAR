# Task 6 — Reporte

## Archivos modificados

- `app/src/main/res/layout/dialog_upgrade.xml` — agregado `<Button android:id="@+id/btnWatchAd" .../>`
  entre el `TextView` de `upgrade_trial_note` y el `TextView` `btnRestore`, `visibility="gone"` por defecto.
- `app/src/main/res/values/strings.xml` — agregados `upgrade_btn_watch_ad` y `rewarded_failed` (español).
- `app/src/main/res/values-en/strings.xml` — mismos dos strings en inglés (apóstrofo de `Couldn't`
  escapado como `Couldn\'t`).
- `app/src/main/res/values-pt/strings.xml` — mismos dos strings en portugués.
- `app/src/main/java/com/example/ar/Analytics.kt` — agregadas `rewardedOffered`, `rewardedStarted`,
  `rewardedEarned`, después de `upgradeButtonTapped`, mismo patrón (`log(context, event, source)`).
- `app/src/main/java/com/example/ar/UpgradeDialog.kt` —
  - imports: `com.example.ar.access.AccessManager`, `com.example.ar.access.Feature`,
    `com.example.ar.access.RewardedAdManager`.
  - propiedades públicas nuevas: `rewardedAdManager`, `feature`, `onRewardEarned`.
  - en `onCreateDialog`, entre el bloque de `btnBuy` y `applyPrice(...)`: precarga si no hay
    anuncio listo, y solo muestra `btnWatchAd` (visible + listener) si hay anuncio Y `feature`
    no es null. Sigue el patrón existente `findViewById<MaterialButton>` sobre un `<Button>` del
    XML (funciona porque el tema es Material y AppCompat infla `<Button>` como `MaterialButton`).
- `app/src/main/java/com/example/ar/MainActivity.kt` — `showUpgradeDialog` ahora acepta
  `feature: Feature? = null` y `onUnlocked: (() -> Unit)? = null` con valores por defecto (las 5
  llamadas existentes siguen compilando sin cambios); pasa `rewardedAdManager`, `feature` y
  `onUnlocked` al `UpgradeDialog`. `Feature` ya estaba importado en este archivo desde la Task 4,
  no se dupicó el import.

## Verificación

### `compileDebugKotlin testDebugUnitTest`

```
BUILD SUCCESSFUL in 9s
31 actionable tasks: 14 executed, 17 up-to-date
```

Resultado de tests: `app/build/test-results/testDebugUnitTest/TEST-com.example.ar.access.AccessRulesTest.xml`
→ `tests="8" failures="0" errors="0"`. Es la única clase de test del proyecto (no se agregaron
tests nuevos en esta tarea, no correspondía).

### `compileReleaseKotlin`

```
BUILD SUCCESSFUL in 7s
22 actionable tasks: 10 executed, 12 up-to-date
```

## Nota de entorno

El primer intento de compilar con la herramienta Bash (Git Bash) falló con
`java.io.IOException: Unable to establish loopback connection` — problema del entorno de la
herramienta, no del código. Se resolvió ejecutando el mismo comando vía PowerShell, tal como
indica el brief.

## Commit

`f93446e` — "feat: ofrecer un anuncio recompensado en el dialogo de upgrade"
(7 files changed, 86 insertions(+), 2 deletions(-), solo `app/src/main`).

## Dudas

Ninguna. `RewardedAdManager`, `AccessManager.grantTemporary`, `Feature` y
`MainActivity.rewardedAdManager` ya existían exactamente como los describe el contexto de la
Task 5/3/2 — no hizo falta ajustar nada de lo consumido. `R.string.rewarded_ad_unit_id` (usado por
`RewardedAdManager`, no tocado en esta tarea) vive en `src/release/res/values/admob.xml` y
`src/debug/res/values/admob.xml` (source-sets por variante, no en `src/main`) — mencionado solo
como contexto, no es parte de esta tarea ni bloquea nada.
