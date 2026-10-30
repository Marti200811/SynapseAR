package com.example.ar.consulta

import android.content.Intent
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.ar.R
import com.example.ar.satellite.Satellite
import com.example.ar.satellite.SatellitePickerDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Hoja de consulta remota: calcula la orientación de una antena que está en el
 * lugar recibido por argumentos, y arma el mensaje para mandarle a quien la va
 * a apuntar.
 *
 * No guarda nada en el ViewModel a propósito. El "modo consulta" es exactamente
 * el tiempo que esta hoja está abierta: al cerrarla no queda estado dando vueltas
 * que pueda desincronizarse con la brújula o el AR, que siguen usando el GPS real.
 *
 * Para quien la abre (MapFragment): mostrarla con su `childFragmentManager` y con
 * [TAG]. Así la hoja muere junto con la pestaña Mapa y se puede evitar abrir dos.
 * `label` no debe venir vacío: es lo que lee el cliente en el encabezado del mensaje.
 *
 * Lo que se puede probar sin Android (cuándo se comparte, la declinación, los textos)
 * está en [ConsultaFicha].
 */
class RemoteSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "consulta_remota"

        private const val TAG_PICKER = "consulta_sat"
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"
        private const val ARG_LABEL = "label"

        fun newInstance(lat: Double, lon: Double, label: String) = RemoteSheet().apply {
            arguments = Bundle().apply {
                putDouble(ARG_LAT, lat)
                putDouble(ARG_LON, lon)
                putString(ARG_LABEL, label)
            }
        }
    }

    private lateinit var observer: ObserverPoint
    private var declinationDeg: Double = 0.0
    private var result: RemoteResult? = null

    private var tvResult: TextView? = null
    private var btnShare: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_consulta, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        observer = ObserverPoint(
            lat = args.getDouble(ARG_LAT),
            lon = args.getDouble(ARG_LON),
            label = args.getString(ARG_LABEL) ?: ""
        )

        // La declinación es la DEL LUGAR DEL CLIENTE, no la nuestra: en Argentina cambia
        // de forma apreciable entre Buenos Aires, Jujuy y Ushuaia. Es justamente lo que
        // hace que el azimut magnético le sirva a él y no a nosotros. Es la misma fuente
        // que usa OrientationManager para la brújula.
        declinationDeg = GeomagneticField(
            observer.lat.toFloat(),
            observer.lon.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination.toDouble()

        view.findViewById<TextView>(R.id.tvConsultaPlace).text = observer.label
        view.findViewById<TextView>(R.id.tvConsultaDeclination).text =
            getString(R.string.consulta_declination, ConsultaFicha.declinationText(declinationDeg))

        tvResult = view.findViewById(R.id.tvConsultaResult)
        btnShare = view.findViewById(R.id.btnConsultaShare)

        view.findViewById<Button>(R.id.btnConsultaSatellite).setOnClickListener {
            // Un solo selector a la vez: dos toques seguidos no apilan dos diálogos.
            if (childFragmentManager.findFragmentByTag(TAG_PICKER) != null) return@setOnClickListener

            // Se le pasa la ubicación del CLIENTE para que el listado muestre los
            // ángulos de esa casa. El control de acceso a satélites Pro queda como
            // está: SatellitePickerDialog ya consulta AccessManager por su cuenta.
            val loc = Location("consulta").apply {
                latitude = observer.lat
                longitude = observer.lon
            }
            // childFragmentManager: el selector vive y muere con esta hoja.
            SatellitePickerDialog(loc) { satellite -> onSatelliteChosen(satellite) }
                .show(childFragmentManager, TAG_PICKER)
        }

        btnShare?.setOnClickListener { shareResult() }
    }

    override fun onStart() {
        super.onStart()
        // Sin esto la hoja puede abrir "colapsada" (alto de 16:9) y dejar el resultado y el
        // botón Compartir debajo del borde, sin que se note que hay que arrastrar. Es una
        // hoja de una sola tarea: va siempre abierta del todo.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun onSatelliteChosen(satellite: Satellite) {
        if (!isAdded) return
        val r = RemoteQuery.satellite(observer, satellite, declinationDeg)
        result = r
        tvResult?.text = message(r)
        // Con un satélite bajo el horizonte la ficha dice que no se ve (sin ángulos para
        // apuntar) y no se ofrece compartir: ese aviso es para el instalador, no para el cliente.
        btnShare?.visibility = if (ConsultaFicha.canShare(r)) View.VISIBLE else View.GONE
    }

    /**
     * El texto que se ve en la hoja y el que se manda son el mismo: así el instalador
     * ve exactamente lo que le va a llegar al cliente.
     */
    private fun message(r: RemoteResult): String =
        RemoteShare.build(r, ConsultaFicha.shareTexts { getString(it) })

    private fun shareResult() {
        val r = result?.takeIf { ConsultaFicha.canShare(it) } ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message(r))
        }
        // El usuario elige la app y aprieta enviar él: acá no se manda nada solo.
        startActivity(Intent.createChooser(intent, getString(R.string.consulta_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvResult = null
        btnShare = null
    }
}
