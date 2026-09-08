# Task 6: La opción de anuncio en `UpgradeDialog`

Agrega el tercer botón al diálogo de upgrade, sus textos en 3 idiomas, y los eventos de
analytics del embudo.

## Global Constraints (aplican a esta tarea)

- `ProManager.kt` NO se modifica.
- Los strings de UI van en los **3** idiomas: `values/` (español), `values-en/`, `values-pt/`.
  Si falta uno, ese idioma muestra el texto en español y queda inconsistente.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"`.
- Shell: PowerShell en Windows. Usar `.\gradlew.bat`.

## Files

- Modify: `app/src/main/res/layout/dialog_upgrade.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-pt/strings.xml`
- Modify: `app/src/main/java/com/example/ar/Analytics.kt`
- Modify: `app/src/main/java/com/example/ar/UpgradeDialog.kt`
- Modify: `app/src/main/java/com/example/ar/MainActivity.kt`

## Interfaces

- Consumes (Task 5): `RewardedAdManager` con `preload()`, `isAdReady(): Boolean`,
  `show(onEarned: () -> Unit, onFailed: () -> Unit)`; la propiedad `MainActivity.rewardedAdManager`
- Consumes (Task 3): `AccessManager.grantTemporary(context: Context, feature: Feature)`
- Consumes (Task 2): `Feature`
- Produces (la Task 7 depende de esta firma exacta):
  - `MainActivity.showUpgradeDialog(source: String, feature: Feature? = null, onUnlocked: (() -> Unit)? = null)`
  - Las vars públicas de `UpgradeDialog`: `rewardedAdManager`, `feature`, `onRewardEarned`

## Steps

### Step 1: Agregar el botón al layout

En `app/src/main/res/layout/dialog_upgrade.xml`, dentro del último `LinearLayout` (el de los
botones, el que contiene `btnBuyPro`), justo **después** del `TextView` que muestra
`@string/upgrade_trial_note` y **antes** del `TextView` con id `btnRestore`:

```xml
        <Button
            android:id="@+id/btnWatchAd"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/upgrade_btn_watch_ad"
            android:textSize="14sp"
            android:textColor="@color/accent_cyan"
            android:backgroundTint="@android:color/transparent"
            android:layout_marginTop="8dp"
            android:visibility="gone"/>
```

Empieza en `gone` a propósito: solo se muestra si hay un anuncio cargado.

### Step 2: Agregar los strings en los 3 idiomas

En `app/src/main/res/values/strings.xml` (español), junto a los otros `upgrade_*`:

```xml
    <string name="upgrade_btn_watch_ad">▶ Ver un anuncio y usar 30 min gratis</string>
    <string name="rewarded_failed">No se pudo cargar el anuncio. Probá de nuevo en un momento.</string>
```

En `app/src/main/res/values-en/strings.xml`:

```xml
    <string name="upgrade_btn_watch_ad">▶ Watch an ad and use it free for 30 min</string>
    <string name="rewarded_failed">Couldn\'t load the ad. Try again in a moment.</string>
```

En `app/src/main/res/values-pt/strings.xml`:

```xml
    <string name="upgrade_btn_watch_ad">▶ Ver um anúncio e usar 30 min grátis</string>
    <string name="rewarded_failed">Não foi possível carregar o anúncio. Tente novamente em instantes.</string>
```

El apóstrofo en inglés va escapado (`\'`) — sin escapar, el build de recursos falla.

### Step 3: Agregar los 3 eventos de analytics

En `app/src/main/java/com/example/ar/Analytics.kt`, después de la función `upgradeButtonTapped`:

```kotlin
    /** Al usuario se le ofreció la opción de mirar un anuncio (había uno cargado). */
    fun rewardedOffered(context: Context, source: String) =
        log(context, "rewarded_offered", source)

    /** Tocó "Ver un anuncio". */
    fun rewardedStarted(context: Context, source: String) =
        log(context, "rewarded_started", source)

    /** Completó el anuncio y ganó el desbloqueo. */
    fun rewardedEarned(context: Context, source: String) =
        log(context, "rewarded_earned", source)
```

### Step 4: Cablear el botón en `UpgradeDialog`

En `app/src/main/java/com/example/ar/UpgradeDialog.kt`:

**(a)** Agregar los imports:

```kotlin
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
import com.example.ar.access.RewardedAdManager
```

**(b)** Agregar las propiedades junto a las que ya existen (`billingManager`, `source`):

```kotlin
    /** Manager de anuncios recompensados. Si es null, no se ofrece la opción. */
    var rewardedAdManager: RewardedAdManager? = null

    /** Función que se desbloquea si el usuario gana la recompensa. */
    var feature: Feature? = null

    /** Se invoca tras ganar la recompensa, para ejecutar la acción que estaba bloqueada. */
    var onRewardEarned: (() -> Unit)? = null
```

**(c)** Dentro de `onCreateDialog`, después del bloque que configura `btnBuy` y antes de la
llamada a `applyPrice(...)`, agregar:

```kotlin
        val ads = rewardedAdManager
        val feat = feature
        val btnAd = view.findViewById<MaterialButton>(R.id.btnWatchAd)

        // Si no hay anuncio listo, se intenta cargar uno para la próxima vez que
        // se abra el diálogo. Sin esto, si la precarga inicial falló (sin red al
        // arrancar, por ejemplo), la opción no volvería a aparecer nunca.
        if (ads != null && !ads.isAdReady()) ads.preload()

        // Solo se ofrece si HAY un anuncio cargado ahora. Nunca se muestra un botón
        // deshabilitado con "Cargando…": ese fue un bug real en el proyecto hermano
        // Oráculo, donde el usuario veía un botón muerto sin saber por qué.
        if (ads != null && feat != null && ads.isAdReady()) {
            btnAd.visibility = android.view.View.VISIBLE
            Analytics.rewardedOffered(requireContext(), source)
            btnAd.setOnClickListener {
                Analytics.rewardedStarted(requireContext(), source)
                ads.show(
                    onEarned = {
                        AccessManager.grantTemporary(requireContext(), feat)
                        Analytics.rewardedEarned(requireContext(), source)
                        onRewardEarned?.invoke()
                        dismiss()
                    },
                    onFailed = {
                        android.widget.Toast.makeText(
                            requireContext(),
                            getString(R.string.rewarded_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
```

**Nota sobre `findViewById<MaterialButton>` con un `<Button>` en el XML:** es el mismo patrón que
ya usa este archivo para `btnBuyPro`. Con un tema Material, el inflater de AppCompat convierte
`<Button>` en `MaterialButton`, así que funciona. Seguí el patrón existente, no lo cambies.

### Step 5: Pasarle el manager desde `MainActivity`

En `app/src/main/java/com/example/ar/MainActivity.kt`, reemplazar la función
`showUpgradeDialog` completa por:

```kotlin
    /**
     * @param source cuál de las funciones bloqueadas trajo al usuario acá.
     * @param feature qué se desbloquea si mira un anuncio. Null = no se ofrece anuncio.
     * @param onUnlocked qué ejecutar si gana la recompensa.
     */
    fun showUpgradeDialog(
        source: String,
        feature: Feature? = null,
        onUnlocked: (() -> Unit)? = null
    ) {
        Analytics.upgradeDialogShown(this, source)
        val dialog = UpgradeDialog()
        dialog.billingManager = billingManager
        dialog.source = source
        dialog.rewardedAdManager = rewardedAdManager
        dialog.feature = feature
        dialog.onRewardEarned = onUnlocked
        dialog.show(supportFragmentManager, "upgrade")
    }
```

Los parámetros nuevos tienen valor por defecto, así que las 5 llamadas existentes siguen
compilando sin cambios. La Task 7 es la que les pasa `feature` y `onUnlocked`.

`Feature` ya está importado en este archivo desde la Task 4 — verificalo antes de agregarlo de nuevo.

### Step 6: Compilar y correr los tests

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, los 8 tests siguen pasando.

### Step 7: Verificar que el release también compila

```
.\gradlew.bat compileReleaseKotlin
```

Esperado: `BUILD SUCCESSFUL`.

### Step 8: Commit

```
git add app/src/main
git commit -m "feat: ofrecer un anuncio recompensado en el dialogo de upgrade"
```
