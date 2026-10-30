# Consulta remota: orientar una antena que está en otro lado

Fecha: 2026-10-30
Estado: aprobado para implementar la Fase 1

## El problema

Hoy la app calcula **siempre desde el GPS del teléfono**. Si un cliente llama por teléfono
para que le digas cómo apuntar su parabólica, no hay forma de resolverlo: habría que estar ahí.

El instalador necesita poder marcar la casa del cliente, elegir el satélite, y mandarle por
WhatsApp un mensaje que esa persona pueda seguir sola.

## Lo que ya está resuelto en el código

Esto no se construye de cero. Las piezas de cálculo ya existen y **no dependen del GPS**:

| Pieza | Firma | Qué aporta |
|---|---|---|
| `SatelliteCalculator.calculate()` | `(obsLat, obsLon, satLon, altitudeM)` | Ya recibe la posición del observador como parámetro |
| `SatelliteCalculator.LookAngles` | `azimuthDeg, elevationDeg, visible` | **Ya modela el caso "no se ve desde ahí"** con `visible` |
| `GeoUtils.bearingDegrees()` | `(lat1, lon1, lat2, lon2)` | Rumbo entre dos puntos, para Yagi y punto a punto |
| `GeoUtils.distanceMeters()` | `(lat1, lon1, lat2, lon2)` | Distancia del enlace |
| `GeomagneticField` (Android) | `(lat, lon, alt, fecha).declination` | Declinación de **cualquier** coordenada, no solo la propia |

Lo único que falta es **separar dos conceptos que hoy están pegados**: dónde estás y para
dónde calculás. Los cuatro lugares que llaman a `calculate()` le pasan siempre `loc.latitude`
y `loc.longitude` del GPS.

## Decisiones tomadas con el usuario

1. **El modo consulta vive solo dentro de la pestaña Mapa.** Es una hoja sobre el mapa; al
   cerrarla o al navegar a otra pestaña, se cierra sola.
2. **La brújula y el AR no participan.** No se muestran números ajenos junto a una aguja que
   apunta desde otro lugar. Como el modo no existe fuera del mapa, esto sale solo: no hay que
   apagar nada ni poner carteles de advertencia que el usuario podría no leer.
3. **Se comparte un texto legible que además lleva un enlace al final.** Quien tiene la app lo
   toca y se le configura; quien no, igual lee los números. Nadie queda con un enlace muerto.
4. **Se mandan los dos azimuts, etiquetados**: verdadero y magnético.
5. **La entrada es desde el mapa**, reutilizando el buscador de direcciones, el historial y
   "Mis puntos" que ya funcionan.
6. **Alcance de tipos de antena: los cinco.** Solo queda afuera el escáner de señal WiFi,
   porque medir una señal a distancia es imposible.

## El detalle que hace o rompe la función: verdadero vs magnético

**La declinación magnética de la casa del cliente no es la misma que la tuya.** En Argentina
varía de forma apreciable entre Buenos Aires, Jujuy y Ushuaia. Si mandás el azimut verdadero y
la persona lo mide con una brújula común, apunta varios grados afuera — y en una parabólica,
con 2° ya no hay señal.

La convención, tomada de cómo lo hace hoy `OrientationManager` (`sensor + declinación = verdadero`):

```
magnético = verdadero − declinación(en la casa del cliente)
```

Ejemplo con la declinación de Buenos Aires (−10,3°): verdadero 310° → magnético 320,3°.

**Ese caso exacto va como test**, porque un signo invertido daría 299,7°: un número que parece
perfectamente razonable y manda la antena a ningún lado sin que nada falle a la vista.

## Arquitectura

Paquete nuevo `com.example.ar.consulta`. **`MapFragment` ya tiene 505 líneas**, así que la
lógica no va ahí adentro: el mapa solo abre la hoja y le pasa el punto.

| Pieza | Responsabilidad |
|---|---|
| `ObserverPoint` | Dónde está la antena: `lat`, `lon`, `label` |
| `RemoteTarget` | Sellada: `Sat(satellite)`, `Tdt(transmitter)`, `Point(lat, lon, name)` |
| `RemoteResult` | `azimutVerdadero`, `azimutMagnetico`, `elevacion?`, `distancia?`, `declinacion`, `visible` |
| `RemoteQuery` | **El cálculo. Sin imports de Android: se testea con JUnit común** |
| `RemoteShare` | Arma el texto y el enlace |
| `RemoteSheet` | `BottomSheetDialogFragment` con la ficha y el botón de compartir |

Cambios afuera del paquete, todos chicos:

- `SharedViewModel`: suma `consultaObserver: MutableLiveData<ObserverPoint?>`. Null = modo normal.
- `MapFragment`: la acción "Calcular para acá", el chinche naranja, y dibujar la línea desde el
  origen elegido en vez de desde el GPS.

### Por qué `RemoteQuery` recibe la declinación en vez de calcularla

`GeomagneticField` es una clase del framework de Android. Si `RemoteQuery` la llamara adentro,
no se podría testear sin emulador. Entonces **la recibe como parámetro** y queda matemática
pura; quien la llama la calcula.

Es el mismo patrón que ya usan `AccessRules` y `SunMoon`, que tienen 21 tests corriendo sin
dispositivo.

## Qué recibe el cliente

```
📡 Orientación de antena — Av. San Martín 1842, San Martín

Satélite: Amazonas 3 (61.0°O)

Azimut:    310° (norte verdadero)
           con brújula común: 320°
Elevación:  49,7°

Antes de medir:
• Calibrá la brújula: movimiento en forma de 8 durante 5-10 segundos.
• Medí parado a 2-3 metros de la antena, NO apoyado en el caño:
  el metal desvía la brújula del celular.
• La elevación es la inclinación del plato hacia arriba.

Ver en el mapa: https://marti200811.github.io/SynapseAR/a?...
```

Las tres indicaciones las eligió el usuario. El aviso del metal es el que más desalineaciones
evita en la práctica y casi nadie lo sabe. El texto de calibración reutiliza el que la app ya
tiene en `CalibrationDialog`, para que diga lo mismo que el usuario ya leyó.

## Fases

### Fase 1 — lo que se implementa ahora

- Modo consulta desde el mapa, con el buscador y "Mis puntos" ya existentes.
- **Solo satelital.** Es el caso que motivó el pedido.
- Ficha con azimut verdadero, magnético, elevación y las tres indicaciones.
- Compartir **como texto plano**, sin enlace.

Deliberadamente **sin enlace profundo**: es la parte más lenta (toca el manifest y hay que
armar una página en el repo de GitHub Pages) y es la que menos falta hace. El mensaje de texto
resuelve el problema completo por sí solo.

### Fase 2 — después

- TDT: buscar los repetidores cercanos **a la casa del cliente**, no a la posición del usuario.
- Yagi, WiFi Direccional y Punto a Punto: marcar el segundo punto en el mapa y dibujar la línea.
  Los tres son el mismo cálculo, así que salen juntos.
- Enlace profundo + página web de respaldo para quien no tiene la app.

## Qué puede salir mal

| Situación | Comportamiento |
|---|---|
| **El satélite está bajo el horizonte para esa casa** | La ficha dice "Desde ese lugar no se ve <satélite>". Nunca se muestra una elevación negativa como si fuera válida. `LookAngles.visible` ya da este dato |
| La dirección no se encuentra | Avisa y deja tocar el mapa a mano |
| Sin internet | El buscador necesita red, pero **tocar el mapa y calcular funcionan igual**: la matemática es toda local |
| Sin señal de GPS | **El modo consulta no necesita GPS.** Se puede resolver desde adentro de una casa |
| Se navega a Brújula o AR | El modo se cierra solo. No queda estado colgado entre pantallas |

## Cómo se verifica

### Tests automáticos sobre `RemoteQuery`

- **El signo de la declinación**, con el caso exacto de arriba (310 verdadero, −10,3 → 320,3).
- Elevación negativa → `visible = false`, y la ficha lo dice.
- Azimut y elevación contra valores de una calculadora pública de *look angles*, en **tres
  latitudes bien separadas** (Buenos Aires, Jujuy, Ushuaia) con el mismo satélite. Tres puntos
  distantes atrapan un error que crezca con la latitud; un solo punto no.
- El texto compartido contiene los números y las tres indicaciones.

### Prueba manual en el Moto (ZY32LK2337)

**Paso obligatorio, y es la verificación más fuerte que tenemos:** calcular en modo consulta
**para la dirección del propio usuario**, salir del modo, y comparar con lo que muestra la app
en modo normal parado en ese mismo lugar. **Los dos azimuts verdaderos tienen que dar idéntico.**

Si coinciden, queda probado que el camino nuevo pasa bien los parámetros, porque el azimut del
modo normal ya está validado contra la realidad: es el que se usa para apuntar antenas que
efectivamente enganchan.

## Riesgo asumido

Los tests confirman que la cuenta es la que decidimos, **no que la antena enganche**. Verificar
el azimut magnético contra el mundo real exigiría ir al lugar con una brújula.

Las tres verificaciones de arriba reducen esto a un solo punto ciego: que la declinación que
devuelve `GeomagneticField` sea incorrecta. Esa es **la misma fuente que la app ya usa hoy**
para su brújula, así que no se agrega riesgo nuevo.

Recomendación para la primera vez: probarlo con una casa cuya orientación ya se conozca.

## Fuera de alcance

- El escáner de señal WiFi. Medir una señal a distancia es imposible.
- La inclinación del LNB (*skew*). La app no la calcula hoy, y no se promete lo que no existe.
- Cambiar el modo normal. Todo lo que funciona hoy sigue igual: el modo consulta es aparte.
