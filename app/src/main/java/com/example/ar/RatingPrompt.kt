package com.example.ar

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Pide la calificación con la In-App Review API de Play.
 *
 * ## Por qué está partido en dos momentos
 *
 * [recordSuccess] se llama cuando el usuario logra alinear la antena — el instante
 * de éxito real. Pero ahí NO se le pregunta nada: está arriba de un techo, con el
 * teléfono apuntando a una antena y las dos manos ocupadas. Es el peor momento
 * posible para un diálogo.
 *
 * [maybeAsk] corre en la apertura siguiente, cuando la persona está tranquila. Es
 * lo que Google recomienda ("un punto natural de pausa") y además convierte mejor.
 *
 * ## Lo que esta API NO hace (para no esperar de más)
 *
 * - **Google decide si el diálogo aparece.** Hay una cuota por tiempo; la doc dice
 *   que llamarla más de una vez en un período corto ("por ejemplo, menos de un mes")
 *   puede no mostrar nada. **Esa cuota NO se aplica si la app se instaló desde la
 *   pista de prueba interna** — es la única forma confiable de probar esto.
 * - **No hay forma de saber si calificó**, ni con cuántas estrellas, ni siquiera si
 *   el diálogo llegó a mostrarse. `onComplete` se dispara igual en todos los casos.
 * - **Si el usuario ya dejó una reseña de la app, no aparece nunca más.** La doc lo
 *   lista como causa frecuente y la única solución es borrar la reseña desde Play.
 *
 * ## Por qué hay reintentos (y por qué el intento se cuenta al final)
 *
 * La versión anterior marcaba el intento como gastado ANTES de lanzar el flujo, y
 * solo permitía uno por versión. Como Google no avisa si mostró algo, un disparo que
 * caía dentro de la ventana de cuota se perdía para siempre: entre la v16 y la v20
 * la calificación no se vio nunca. Ahora el intento se cuenta recién cuando el flujo
 * termina, y se permiten [MAX_ATTEMPTS_PER_VERSION] separados por
 * [MIN_MILLIS_BETWEEN_ATTEMPTS], que es lo que Google pide: no insistir de más.
 *
 * ## Prohibido por política de Play (no "mejorar" sin leer esto)
 *
 * - Filtrar por contentos: preguntar "¿te gusta la app?" y mostrar el diálogo solo
 *   a los que dicen que sí **está prohibido**.
 * - Incentivar la reseña con cualquier cosa — incluidos los 30 min de desbloqueo.
 * - Disparar ESTA API desde un botón. Para eso está [openStoreListing], que lleva a
 *   la ficha de Play: es lo que la doc recomienda para quien ya agotó la cuota.
 */
object RatingPrompt {

    private const val TAG = "RatingPrompt"
    private const val PREFS = "synapse_prefs"

    /** Cuántas sesiones con alineación exitosa antes de preguntar. */
    private const val REQUIRED_SUCCESSES = 3

    /** Cuántas veces se puede lanzar el flujo por cada versión publicada. */
    private const val MAX_ATTEMPTS_PER_VERSION = 3

    /** Separación mínima entre intentos: 3 días. */
    private const val MIN_MILLIS_BETWEEN_ATTEMPTS = 3L * 24 * 60 * 60 * 1000

    private const val KEY_SUCCESSES = "rating_success_count"
    private const val KEY_SESSION_COUNTED = "rating_session_counted"
    private const val KEY_ATTEMPTS = "rating_attempts"
    private const val KEY_ATTEMPTS_VERSION = "rating_attempts_version"
    private const val KEY_LAST_ATTEMPT = "rating_last_attempt_millis"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Marca que en esta sesión el usuario logró alinear.
     *
     * Se llama desde el "lock" de brújula y de AR, que se disparan cada vez que el
     * error angular cruza el umbral. Apuntando una antena eso pasa muchas veces
     * seguidas mientras la mano tiembla, así que **se cuenta una sola vez por
     * sesión**: lo que interesa es "cuántas veces le sirvió la app", no cuántos
     * micro-locks hubo. [resetSession] limpia la marca al arrancar.
     */
    fun recordSuccess(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_SESSION_COUNTED, false)) return

        val count = p.getInt(KEY_SUCCESSES, 0) + 1
        p.edit()
            .putBoolean(KEY_SESSION_COUNTED, true)
            .putInt(KEY_SUCCESSES, count)
            .apply()
        // Log.i y no Log.d: el Moto de pruebas tiene log.tag=I y se come los debug.
        Log.i(TAG, "Alineación exitosa registrada ($count/$REQUIRED_SUCCESSES)")
    }

    /** Llamar al crear MainActivity, para que [recordSuccess] pueda contar de nuevo. */
    fun resetSession(context: Context) {
        prefs(context).edit().putBoolean(KEY_SESSION_COUNTED, false).apply()
    }

    /**
     * Pide la calificación si corresponde. Silencioso si no.
     */
    fun maybeAsk(activity: Activity) {
        val p = prefs(activity)

        val successes = p.getInt(KEY_SUCCESSES, 0)
        if (successes < REQUIRED_SUCCESSES) {
            Log.i(TAG, "Todavía no: $successes/$REQUIRED_SUCCESSES alineaciones")
            return
        }

        val version = BuildConfig.VERSION_CODE
        // Los intentos se cuentan por versión: al publicar una nueva, se empieza de cero.
        val attempts =
            if (p.getInt(KEY_ATTEMPTS_VERSION, 0) == version) p.getInt(KEY_ATTEMPTS, 0) else 0
        if (attempts >= MAX_ATTEMPTS_PER_VERSION) {
            Log.i(TAG, "Ya se intentó $attempts veces en la v$version, no se insiste más")
            return
        }

        val last = p.getLong(KEY_LAST_ATTEMPT, 0L)
        val sinceLast = System.currentTimeMillis() - last
        if (last != 0L && sinceLast < MIN_MILLIS_BETWEEN_ATTEMPTS) {
            Log.i(TAG, "Último intento hace ${sinceLast / 3_600_000} h, todavía no toca")
            return
        }

        Log.i(TAG, "Lanzando flujo de calificación (intento ${attempts + 1} de la v$version)")
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                // Error transitorio (sin red, Play Store raro). NO se cuenta el intento.
                Log.w(TAG, "requestReviewFlow falló: ${request.exception?.message}")
                return@addOnCompleteListener
            }
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener

            manager.launchReviewFlow(activity, request.result)
                .addOnCompleteListener {
                    // Acá Google ya mostró lo que haya querido mostrar — o nada. No hay
                    // manera de distinguirlo, así que el intento se cuenta igual: es el
                    // único punto donde se sabe que el flujo llegó hasta el final.
                    p.edit()
                        .putInt(KEY_ATTEMPTS_VERSION, version)
                        .putInt(KEY_ATTEMPTS, attempts + 1)
                        .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
                        .apply()
                    Log.i(TAG, "Flujo de calificación terminado")
                }
        }
    }

    /**
     * Abre la ficha de la app en Play Store.
     *
     * Esto NO es la In-App Review API: es el camino que la doc recomienda para un
     * botón explícito, justamente porque el usuario puede tener la cuota agotada y
     * el diálogo nunca aparecería.
     *
     * Ojo con el build de debug: su `packageName` termina en `.debug` y ese paquete
     * no existe en Play, así que se saca el sufijo para que la ficha abra igual.
     */
    fun openStoreListing(context: Context) {
        val pkg = context.packageName.removeSuffix(".debug")
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            // Sin app de Play Store (raro, pero pasa en algunos dispositivos): navegador.
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
