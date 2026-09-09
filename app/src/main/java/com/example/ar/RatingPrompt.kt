package com.example.ar

import android.app.Activity
import android.content.Context
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
 * - **Google decide si el diálogo aparece.** Hay una cuota por usuario y por
 *   ventana de tiempo. Se lo llama y muy seguido no pasa nada, sin aviso.
 * - **No hay forma de saber si calificó**, ni con cuántas estrellas. El callback
 *   `onComplete` se dispara igual, haya calificado o no. Por eso acá se consume el
 *   intento apenas se lanza el flujo: no se puede distinguir "no quiso" de "Google
 *   no lo mostró", y reintentar sería acosar.
 *
 * ## Lo que la política de Play prohíbe (no "cambiar" sin leer esto)
 *
 * - Filtrar por contentos: preguntar "¿te gusta la app?" y mostrar el diálogo solo
 *   a los que dicen que sí **está prohibido**.
 * - Incentivar la reseña con cualquier cosa — incluidos los 30 minutos de desbloqueo.
 * - Un botón "Calificá la app" NO va por esta API: para eso se abre la ficha de Play.
 */
object RatingPrompt {

    private const val TAG = "RatingPrompt"
    private const val PREFS = "synapse_prefs"

    /** Cuántas sesiones con alineación exitosa antes de preguntar. */
    private const val REQUIRED_SUCCESSES = 3

    private const val KEY_SUCCESSES = "rating_success_count"
    private const val KEY_ASKED_FOR_VERSION = "rating_asked_for_version"
    private const val KEY_SESSION_COUNTED = "rating_session_counted"

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
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SESSION_COUNTED, false)) return

        val count = prefs.getInt(KEY_SUCCESSES, 0) + 1
        prefs.edit()
            .putBoolean(KEY_SESSION_COUNTED, true)
            .putInt(KEY_SUCCESSES, count)
            .apply()
        Log.d(TAG, "Alineación exitosa registrada ($count/$REQUIRED_SUCCESSES)")
    }

    /** Llamar al crear MainActivity, para que [recordSuccess] pueda contar de nuevo. */
    fun resetSession(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SESSION_COUNTED, false).apply()
    }

    /**
     * Pide la calificación si corresponde. Silencioso si no.
     *
     * Se pregunta como mucho **una vez por versión publicada**: alguien que ya
     * decidió no calificar no tiene que volver a ver esto en cada apertura.
     */
    fun maybeAsk(activity: Activity) {
        val prefs = activity.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val successes = prefs.getInt(KEY_SUCCESSES, 0)
        if (successes < REQUIRED_SUCCESSES) return

        val currentVersion = BuildConfig.VERSION_CODE
        if (prefs.getInt(KEY_ASKED_FOR_VERSION, 0) >= currentVersion) return

        // Se consume el intento ANTES de lanzar el flujo, no en el callback: si el
        // proceso muere con el diálogo abierto, es preferible perder una pregunta
        // a repetírsela en la apertura siguiente.
        prefs.edit().putInt(KEY_ASKED_FOR_VERSION, currentVersion).apply()

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                Log.w(TAG, "requestReviewFlow falló: ${request.exception?.message}")
                return@addOnCompleteListener
            }
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener

            manager.launchReviewFlow(activity, request.result)
                .addOnCompleteListener {
                    // Se dispara siempre, haya calificado o no. No hay nada que medir acá.
                    Log.d(TAG, "Flujo de calificación terminado")
                }
        }
    }
}
