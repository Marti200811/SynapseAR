package com.example.ar.tdt

/**
 * Base de datos de transmisores TDT del mundo.
 * Cubre los principales transmisores de alta potencia por país.
 *
 * Estándares:
 *  DVB-T2  → Europa, Australia, África, parte de Asia
 *  ISDB-T  → Japón
 *  ISDB-Tb → América Latina (Brasil, Argentina, Chile, etc.)
 *  ATSC    → Estados Unidos, Canadá, México, Corea del Sur
 *  ATSC3   → Estados Unidos (nuevo estándar)
 */
object TdtDatabase {

    val transmitters: List<TdtTransmitter> = listOf(

        // ══════════════════════════════════════════════════════
        // ARGENTINA  (ISDB-Tb — TDA)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Cerro Tres Picos", "Buenos Aires", "AR", "ISDB-Tb", -34.6037, -58.3816, 30.0),
        TdtTransmitter("Cerro Champaquí", "Córdoba", "AR", "ISDB-Tb", -31.9843, -64.9323, 10.0),
        TdtTransmitter("Rosario Centro", "Rosario", "AR", "ISDB-Tb", -32.9442, -60.6505, 5.0),
        TdtTransmitter("Cerro de la Gloria", "Mendoza", "AR", "ISDB-Tb", -32.8846, -68.8457, 10.0),
        TdtTransmitter("San Miguel de Tucumán", "Tucumán", "AR", "ISDB-Tb", -26.8241, -65.2226, 5.0),
        TdtTransmitter("Mar del Plata", "Mar del Plata", "AR", "ISDB-Tb", -38.0023, -57.5575, 5.0),
        TdtTransmitter("Salta Capital", "Salta", "AR", "ISDB-Tb", -24.7859, -65.4116, 5.0),
        TdtTransmitter("Santa Fe Capital", "Santa Fe", "AR", "ISDB-Tb", -31.6272, -60.6988, 5.0),
        TdtTransmitter("San Juan Capital", "San Juan", "AR", "ISDB-Tb", -31.5375, -68.5364, 5.0),
        TdtTransmitter("La Plata", "La Plata", "AR", "ISDB-Tb", -34.9215, -57.9545, 3.0),
        TdtTransmitter("Neuquén Capital", "Neuquén", "AR", "ISDB-Tb", -38.9516, -68.0591, 5.0),
        TdtTransmitter("Bariloche", "Río Negro", "AR", "ISDB-Tb", -41.1335, -71.3103, 5.0),
        TdtTransmitter("Comodoro Rivadavia", "Chubut", "AR", "ISDB-Tb", -45.8647, -67.4973, 5.0),
        TdtTransmitter("Posadas", "Misiones", "AR", "ISDB-Tb", -27.3671, -55.8969, 5.0),
        TdtTransmitter("Resistencia", "Chaco", "AR", "ISDB-Tb", -27.4514, -58.9867, 5.0),

        // ══════════════════════════════════════════════════════
        // BRASIL  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Sumaré (São Paulo)", "São Paulo", "BR", "ISDB-Tb", -23.5329, -46.6534, 100.0),
        TdtTransmitter("Pão de Açúcar", "Rio de Janeiro", "BR", "ISDB-Tb", -22.9519, -43.1574, 50.0),
        TdtTransmitter("Torre Digital Brasília", "Brasília", "BR", "ISDB-Tb", -15.7942, -47.8822, 20.0),
        TdtTransmitter("Salvador Itapuã", "Salvador", "BR", "ISDB-Tb", -13.0073, -38.4936, 20.0),
        TdtTransmitter("Fortaleza Messejana", "Fortaleza", "BR", "ISDB-Tb", -3.8343, -38.5434, 20.0),
        TdtTransmitter("Belo Horizonte Mangabeiras", "Belo Horizonte", "BR", "ISDB-Tb", -19.9621, -43.9385, 30.0),
        TdtTransmitter("Manaus Centro", "Manaus", "BR", "ISDB-Tb", -3.1019, -60.0250, 10.0),
        TdtTransmitter("Recife Olinda", "Recife", "BR", "ISDB-Tb", -8.0089, -34.8463, 20.0),
        TdtTransmitter("Porto Alegre Morro Santana", "Porto Alegre", "BR", "ISDB-Tb", -30.0346, -51.2177, 20.0),
        TdtTransmitter("Curitiba Morro do Cambirela", "Curitiba", "BR", "ISDB-Tb", -25.4284, -49.2733, 20.0),

        // ══════════════════════════════════════════════════════
        // CHILE  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Cerro San Cristóbal", "Santiago", "CL", "ISDB-Tb", -33.4224, -70.6273, 20.0),
        TdtTransmitter("Cerro Caracol", "Concepción", "CL", "ISDB-Tb", -36.8201, -73.0444, 10.0),
        TdtTransmitter("Cerro La Cruz", "Valparaíso", "CL", "ISDB-Tb", -33.0458, -71.6197, 10.0),
        TdtTransmitter("Antofagasta Centro", "Antofagasta", "CL", "ISDB-Tb", -23.6509, -70.3975, 5.0),
        TdtTransmitter("Temuco Cerro Ñielol", "Temuco", "CL", "ISDB-Tb", -38.7359, -72.5904, 5.0),

        // ══════════════════════════════════════════════════════
        // COLOMBIA  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Alto de las Palomas", "Bogotá", "CO", "ISDB-Tb", 4.3907, -74.1324, 20.0),
        TdtTransmitter("Alto de Boquerón", "Medellín", "CO", "ISDB-Tb", 6.2518, -75.5636, 10.0),
        TdtTransmitter("Cerro La Popa", "Cartagena", "CO", "ISDB-Tb", 10.4266, -75.5500, 5.0),
        TdtTransmitter("Barranquilla Centro", "Barranquilla", "CO", "ISDB-Tb", 10.9639, -74.7964, 5.0),
        TdtTransmitter("Cali Cerro de las Tres Cruces", "Cali", "CO", "ISDB-Tb", 3.4372, -76.5300, 10.0),

        // ══════════════════════════════════════════════════════
        // URUGUAY  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Montevideo Antel", "Montevideo", "UY", "ISDB-Tb", -34.9011, -56.1645, 10.0),
        TdtTransmitter("Maldonado Centro", "Maldonado", "UY", "ISDB-Tb", -34.9011, -54.9506, 3.0),

        // ══════════════════════════════════════════════════════
        // PARAGUAY  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Asunción Torre Digital", "Asunción", "PY", "ISDB-Tb", -25.2867, -57.6470, 5.0),

        // ══════════════════════════════════════════════════════
        // PERÚ  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Cerro Morro Solar", "Lima", "PE", "ISDB-Tb", -12.1504, -77.0282, 20.0),
        TdtTransmitter("Arequipa Chachani", "Arequipa", "PE", "ISDB-Tb", -16.1909, -71.5375, 5.0),

        // ══════════════════════════════════════════════════════
        // VENEZUELA  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Caracas Ávila", "Caracas", "VE", "ISDB-Tb", 10.5116, -66.9180, 20.0),
        TdtTransmitter("Maracaibo Centro", "Maracaibo", "VE", "ISDB-Tb", 10.6427, -71.6125, 5.0),

        // ══════════════════════════════════════════════════════
        // BOLIVIA  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("La Paz Chacaltaya", "La Paz", "BO", "ISDB-Tb", -16.4897, -68.1193, 5.0),
        TdtTransmitter("Santa Cruz Centro", "Santa Cruz", "BO", "ISDB-Tb", -17.7833, -63.1821, 3.0),

        // ══════════════════════════════════════════════════════
        // ECUADOR  (ISDB-Tb)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Pichincha Quito", "Quito", "EC", "ISDB-Tb", -0.1807, -78.4678, 10.0),
        TdtTransmitter("Guayaquil Centro", "Guayaquil", "EC", "ISDB-Tb", -2.1894, -79.8891, 5.0),

        // ══════════════════════════════════════════════════════
        // MÉXICO  (ATSC / ISDB-Tb en transición)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Chiquihuite CDMX", "Ciudad de México", "MX", "ATSC", 19.5273, -99.1202, 100.0),
        TdtTransmitter("Guadalajara Cerro Tepopote", "Guadalajara", "MX", "ATSC", 20.6597, -103.5005, 30.0),
        TdtTransmitter("Monterrey Cerro de la Silla", "Monterrey", "MX", "ATSC", 25.6300, -100.2468, 30.0),
        TdtTransmitter("Tijuana Cerro Colorado", "Tijuana", "MX", "ATSC", 32.4915, -116.9563, 10.0),
        TdtTransmitter("Puebla Cerro Zapotecas", "Puebla", "MX", "ATSC", 19.0414, -98.2063, 10.0),

        // ══════════════════════════════════════════════════════
        // ESTADOS UNIDOS  (ATSC / ATSC3)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Empire State Building", "New York, NY", "US", "ATSC", 40.7484, -73.9967, 100.0),
        TdtTransmitter("Mount Wilson", "Los Angeles, CA", "US", "ATSC", 34.2258, -118.0578, 100.0),
        TdtTransmitter("Willis Tower", "Chicago, IL", "US", "ATSC", 41.8789, -87.6359, 100.0),
        TdtTransmitter("Sutro Tower", "San Francisco, CA", "US", "ATSC", 37.7554, -122.4527, 100.0),
        TdtTransmitter("Space Needle area", "Seattle, WA", "US", "ATSC", 47.6205, -122.3493, 50.0),
        TdtTransmitter("South Mountain", "Phoenix, AZ", "US", "ATSC", 33.3228, -112.0695, 50.0),
        TdtTransmitter("Dallas Cedar Hill", "Dallas, TX", "US", "ATSC", 32.5735, -96.9561, 50.0),
        TdtTransmitter("Sandia Peak", "Albuquerque, NM", "US", "ATSC", 35.2109, -106.4494, 50.0),
        TdtTransmitter("Stone Mountain", "Atlanta, GA", "US", "ATSC", 33.8081, -84.1453, 50.0),
        TdtTransmitter("Lookout Mountain", "Denver, CO", "US", "ATSC", 39.7238, -105.2410, 50.0),
        TdtTransmitter("Miami Tower", "Miami, FL", "US", "ATSC", 25.7742, -80.1937, 50.0),
        TdtTransmitter("Channel Master", "Washington DC", "US", "ATSC", 38.8951, -77.0364, 50.0),

        // ══════════════════════════════════════════════════════
        // CANADÁ  (ATSC)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("CN Tower", "Toronto, ON", "CA", "ATSC", 43.6426, -79.3871, 100.0),
        TdtTransmitter("Mount Seymour", "Vancouver, BC", "CA", "ATSC", 49.3643, -122.9490, 50.0),
        TdtTransmitter("Mount Royal", "Montreal, QC", "CA", "ATSC", 45.5048, -73.5878, 50.0),

        // ══════════════════════════════════════════════════════
        // ESPAÑA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Torrespaña (Pirulí)", "Madrid", "ES", "DVB-T2", 40.4046, -3.6845, 100.0),
        TdtTransmitter("Torre de Collserola", "Barcelona", "ES", "DVB-T2", 41.4185, 2.1190, 100.0),
        TdtTransmitter("Aitana", "Valencia / Alicante", "ES", "DVB-T2", 38.6596, -0.2647, 50.0),
        TdtTransmitter("Mairena del Aljarafe", "Sevilla", "ES", "DVB-T2", 37.3629, -5.9842, 20.0),
        TdtTransmitter("Monte Cabezón", "Bilbao / País Vasco", "ES", "DVB-T2", 43.2081, -2.6930, 20.0),
        TdtTransmitter("Sierra Collcabra", "Zaragoza", "ES", "DVB-T2", 41.4654, -0.8772, 20.0),
        TdtTransmitter("Picorro", "Málaga", "ES", "DVB-T2", 36.7729, -4.4214, 20.0),
        TdtTransmitter("O Meda", "La Coruña / Galicia", "ES", "DVB-T2", 43.3623, -8.4115, 20.0),
        TdtTransmitter("Candelario", "Salamanca", "ES", "DVB-T2", 40.3547, -5.7451, 10.0),
        TdtTransmitter("Tibidabo", "Girona / Cataluña Norte", "ES", "DVB-T2", 41.4186, 2.1191, 10.0),
        TdtTransmitter("La Mola", "Palma de Mallorca", "ES", "DVB-T2", 39.8178, 2.9916, 10.0),
        TdtTransmitter("Teide", "Tenerife (Canarias)", "ES", "DVB-T2", 28.2723, -16.6424, 10.0),
        TdtTransmitter("Gran Canaria Norte", "Las Palmas (Canarias)", "ES", "DVB-T2", 28.0997, -15.4134, 10.0),

        // ══════════════════════════════════════════════════════
        // REINO UNIDO  (DVB-T2 Freeview)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Crystal Palace", "Londres", "GB", "DVB-T2", 51.4142, -0.0741, 100.0),
        TdtTransmitter("Emley Moor", "Yorkshire", "GB", "DVB-T2", 53.6169, -1.6653, 100.0),
        TdtTransmitter("Winter Hill", "Manchester", "GB", "DVB-T2", 53.6213, -2.5333, 100.0),
        TdtTransmitter("Black Hill", "Glasgow / Central Scotland", "GB", "DVB-T2", 55.8406, -3.8553, 100.0),
        TdtTransmitter("Mendip", "Bristol / Somerset", "GB", "DVB-T2", 51.2804, -2.6629, 100.0),
        TdtTransmitter("Sandy Heath", "East England", "GB", "DVB-T2", 52.1355, -0.2887, 100.0),
        TdtTransmitter("Rowridge", "Isle of Wight / Hampshire", "GB", "DVB-T2", 50.6629, -1.3087, 50.0),
        TdtTransmitter("Preseli", "Gales Occidental", "GB", "DVB-T2", 51.9706, -4.7944, 50.0),
        TdtTransmitter("Caldbeck", "Cumbria / Norte de Inglaterra", "GB", "DVB-T2", 54.7390, -3.0572, 50.0),

        // ══════════════════════════════════════════════════════
        // FRANCIA  (DVB-T2 TNT)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Tour Eiffel", "París", "FR", "DVB-T2", 48.8584, 2.2945, 100.0),
        TdtTransmitter("Fourvière", "Lyon", "FR", "DVB-T2", 45.7598, 4.8220, 50.0),
        TdtTransmitter("Notre-Dame de la Garde", "Marsella", "FR", "DVB-T2", 43.2838, 5.3706, 50.0),
        TdtTransmitter("Pic du Midi", "Toulouse / Pirineos", "FR", "DVB-T2", 42.9366, 0.1428, 50.0),
        TdtTransmitter("Nielles-lès-Calais", "Norte de Francia", "FR", "DVB-T2", 50.9079, 1.8268, 50.0),
        TdtTransmitter("Saint-Cyr", "Bordeaux", "FR", "DVB-T2", 44.8100, -0.5800, 20.0),

        // ══════════════════════════════════════════════════════
        // ALEMANIA  (DVB-T2 HD)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Fernsehturm Berlin", "Berlín", "DE", "DVB-T2", 52.5208, 13.4095, 100.0),
        TdtTransmitter("Olympiaturm München", "Múnich", "DE", "DVB-T2", 48.1736, 11.5527, 100.0),
        TdtTransmitter("Rheinturm Düsseldorf", "Düsseldorf / Colonia", "DE", "DVB-T2", 51.2170, 6.7651, 100.0),
        TdtTransmitter("Fernsehturm Hamburg", "Hamburgo", "DE", "DVB-T2", 53.5600, 10.0042, 100.0),
        TdtTransmitter("Blauen", "Frankfurt / Fráncfort", "DE", "DVB-T2", 47.7632, 7.7044, 50.0),
        TdtTransmitter("Wendelstein", "Bavaria", "DE", "DVB-T2", 47.7085, 12.0136, 50.0),

        // ══════════════════════════════════════════════════════
        // ITALIA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Monte Mario", "Roma", "IT", "DVB-T2", 41.9268, 12.4419, 100.0),
        TdtTransmitter("Erba", "Milán / Lombardía", "IT", "DVB-T2", 45.7945, 9.1903, 100.0),
        TdtTransmitter("Monte Faito", "Nápoles", "IT", "DVB-T2", 40.6637, 14.4736, 50.0),
        TdtTransmitter("Monte Pellegrino", "Palermo / Sicilia", "IT", "DVB-T2", 38.1657, 13.3540, 50.0),
        TdtTransmitter("Monte Serra", "Pisa / Toscana", "IT", "DVB-T2", 43.7040, 10.5855, 50.0),
        TdtTransmitter("Monte Venda", "Venecia / Véneto", "IT", "DVB-T2", 45.2661, 11.7095, 50.0),

        // ══════════════════════════════════════════════════════
        // PORTUGAL  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Monsanto", "Lisboa", "PT", "DVB-T2", 38.7223, -9.1849, 20.0),
        TdtTransmitter("Serra do Pilar", "Porto", "PT", "DVB-T2", 41.1337, -8.6148, 10.0),
        TdtTransmitter("Foia", "Algarve", "PT", "DVB-T2", 37.3277, -8.5981, 10.0),

        // ══════════════════════════════════════════════════════
        // PAÍSES BAJOS  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("IJsselstein (Gerbrandy Tower)", "Amsterdam / Países Bajos", "NL", "DVB-T2", 52.0210, 5.0474, 100.0),

        // ══════════════════════════════════════════════════════
        // BÉLGICA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Liège-Sart Tilman", "Bruselas / Lieja", "BE", "DVB-T2", 50.5833, 5.5667, 50.0),

        // ══════════════════════════════════════════════════════
        // SUIZA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Üetliberg", "Zúrich", "CH", "DVB-T2", 47.3497, 8.4917, 20.0),
        TdtTransmitter("Mont Salève", "Ginebra", "CH", "DVB-T2", 46.1165, 6.1773, 10.0),

        // ══════════════════════════════════════════════════════
        // AUSTRIA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Kahlenberg", "Viena", "AT", "DVB-T2", 48.2733, 16.3389, 50.0),
        TdtTransmitter("Patscherkofel", "Innsbruck", "AT", "DVB-T2", 47.2095, 11.4608, 20.0),

        // ══════════════════════════════════════════════════════
        // AUSTRALIA  (DVB-T)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Black Mountain Tower", "Canberra", "AU", "DVB-T2", -35.2745, 149.0984, 100.0),
        TdtTransmitter("Gore Hill", "Sydney", "AU", "DVB-T2", -33.8284, 151.1898, 100.0),
        TdtTransmitter("Mount Dandenong", "Melbourne", "AU", "DVB-T2", -37.8311, 145.3615, 100.0),
        TdtTransmitter("Mount Coot-tha", "Brisbane", "AU", "DVB-T2", -27.4758, 152.9383, 100.0),
        TdtTransmitter("Mount Lofty", "Adelaida", "AU", "DVB-T2", -34.9795, 138.7082, 100.0),

        // ══════════════════════════════════════════════════════
        // JAPÓN  (ISDB-T)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Tokyo Tower", "Tokio", "JP", "ISDB-T", 35.6586, 139.7454, 100.0),
        TdtTransmitter("Osaka Tower", "Osaka", "JP", "ISDB-T", 34.6937, 135.5023, 100.0),
        TdtTransmitter("Sapporo Tower", "Sapporo", "JP", "ISDB-T", 43.0618, 141.3545, 50.0),

        // ══════════════════════════════════════════════════════
        // COREA DEL SUR  (ATSC)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Namsan Tower", "Seúl", "KR", "ATSC", 37.5512, 126.9882, 100.0),
        TdtTransmitter("Busan Tower", "Busan", "KR", "ATSC", 35.1029, 129.0323, 50.0),

        // ══════════════════════════════════════════════════════
        // INDIA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Pitampura Tower (Delhi)", "Nueva Delhi", "IN", "DVB-T2", 28.6918, 77.1273, 100.0),
        TdtTransmitter("Mumbai Transmitter", "Mumbai", "IN", "DVB-T2", 19.0760, 72.8777, 50.0),

        // ══════════════════════════════════════════════════════
        // SUDÁFRICA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Brixton Tower", "Johannesburgo", "ZA", "DVB-T2", -26.2041, 28.0473, 100.0),
        TdtTransmitter("Cape Town Signal Hill", "Ciudad del Cabo", "ZA", "DVB-T2", -33.9249, 18.4241, 50.0),

        // ══════════════════════════════════════════════════════
        // CENTROAMÉRICA  (ISDB-Tb / ATSC)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Cerro Candelaria", "Guatemala Ciudad", "GT", "ISDB-Tb", 14.5833, -90.5222, 10.0),
        TdtTransmitter("San Salvador Centro", "San Salvador", "SV", "ISDB-Tb", 13.6929, -89.2182, 10.0),
        TdtTransmitter("Tegucigalpa Centro", "Tegucigalpa", "HN", "ISDB-Tb", 14.0818, -87.2068, 10.0),
        TdtTransmitter("Managua Centro", "Managua", "NI", "ISDB-Tb", 12.1328, -86.2504, 10.0),
        TdtTransmitter("San José Cerro Zurquí", "San José", "CR", "ISDB-Tb", 10.0357, -84.0124, 10.0),
        TdtTransmitter("Ciudad de Panamá", "Panamá", "PA", "ISDB-Tb", 8.9936, -79.5197, 10.0),
        TdtTransmitter("Santo Domingo", "Santo Domingo", "DO", "ATSC", 18.4861, -69.9312, 20.0),
        TdtTransmitter("Havana Centro", "La Habana", "CU", "ATSC", 23.1367, -82.3589, 20.0),

        // ══════════════════════════════════════════════════════
        // RUSIA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Ostankino Tower", "Moscú", "RU", "DVB-T2", 55.8197, 37.6118, 250.0),
        TdtTransmitter("Ekaterinburg Tower", "Ekaterimburgo", "RU", "DVB-T2", 56.8389, 60.5975, 100.0),
        TdtTransmitter("Novosibirsk Tower", "Novosibirsk", "RU", "DVB-T2", 54.9833, 82.8964, 100.0),
        TdtTransmitter("Kazan Tower", "Kazán", "RU", "DVB-T2", 55.7887, 49.1221, 100.0),
        TdtTransmitter("Saint Petersburg Tower", "San Petersburgo", "RU", "DVB-T2", 59.9311, 30.3609, 100.0),

        // ══════════════════════════════════════════════════════
        // PAÍSES NÓRDICOS  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Tryvannstårnet", "Oslo", "NO", "DVB-T2", 59.9724, 10.6820, 100.0),
        TdtTransmitter("Södra Kaknästornet", "Estocolmo", "SE", "DVB-T2", 59.3328, 18.1195, 100.0),
        TdtTransmitter("Gladsaxe Tower", "Copenhague", "DK", "DVB-T2", 55.7307, 12.4561, 100.0),
        TdtTransmitter("Digita Espoo", "Helsinki", "FI", "DVB-T2", 60.2052, 24.6556, 100.0),

        // ══════════════════════════════════════════════════════
        // EUROPA DEL ESTE  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Pałac Kultury", "Varsovia", "PL", "DVB-T2", 52.2319, 21.0067, 100.0),
        TdtTransmitter("Žižkov Tower", "Praga", "CZ", "DVB-T2", 50.0790, 14.4513, 100.0),
        TdtTransmitter("Piszkéstető", "Budapest", "HU", "DVB-T2", 47.9178, 19.8950, 50.0),
        TdtTransmitter("Dumbrava Rosie", "Bucarest", "RO", "DVB-T2", 44.4268, 26.1025, 100.0),
        TdtTransmitter("Sofia TV Tower", "Sofía", "BG", "DVB-T2", 42.6977, 23.3219, 100.0),
        TdtTransmitter("Zagreb Sljeme", "Zagreb", "HR", "DVB-T2", 45.8958, 15.9780, 50.0),
        TdtTransmitter("Belgrade Tower", "Belgrado", "RS", "DVB-T2", 44.8033, 20.4651, 100.0),

        // ══════════════════════════════════════════════════════
        // TURQUÍA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Çamlıca Tower", "Estambul", "TR", "DVB-T2", 41.0272, 29.0628, 100.0),
        TdtTransmitter("Ankara Transmitter", "Ankara", "TR", "DVB-T2", 39.9334, 32.8597, 50.0),
        TdtTransmitter("Izmir Tower", "Izmir", "TR", "DVB-T2", 38.4192, 27.1287, 50.0),

        // ══════════════════════════════════════════════════════
        // ORIENTE MEDIO  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Riyadh Transmitter", "Riad", "SA", "DVB-T2", 24.6877, 46.7219, 50.0),
        TdtTransmitter("Dubai Transmitter", "Dubái", "AE", "DVB-T2", 25.2048, 55.2708, 50.0),
        TdtTransmitter("Cairo Tower", "El Cairo", "EG", "DVB-T2", 30.0626, 31.2243, 100.0),
        TdtTransmitter("Jerusalem Tower", "Tel Aviv / Israel", "IL", "DVB-T2", 32.0853, 34.7818, 50.0),

        // ══════════════════════════════════════════════════════
        // OTROS PAÍSES DE AFRICA  (DVB-T2)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Lagos Transmitter", "Lagos", "NG", "DVB-T2", 6.5244, 3.3792, 100.0),
        TdtTransmitter("Nairobi Hill", "Nairobi", "KE", "DVB-T2", -1.2921, 36.8219, 50.0),
        TdtTransmitter("Addis Ababa Tower", "Addis Abeba", "ET", "DVB-T2", 9.0320, 38.7469, 50.0),
        TdtTransmitter("Casablanca Tower", "Casablanca", "MA", "DVB-T2", 33.5731, -7.5898, 50.0),
        TdtTransmitter("Algiers Tower", "Argel", "DZ", "DVB-T2", 36.7372, 3.0868, 50.0),

        // ══════════════════════════════════════════════════════
        // SUDESTE ASIÁTICO  (DVB-T2 / ISDB-T)
        // ══════════════════════════════════════════════════════
        TdtTransmitter("Wisma Nusantara", "Yakarta", "ID", "DVB-T2", -6.2088, 106.8456, 100.0),
        TdtTransmitter("Surabaya Tower", "Surabaya", "ID", "DVB-T2", -7.2575, 112.7521, 50.0),
        TdtTransmitter("Manila Transmitter", "Manila", "PH", "ISDB-T", 14.5995, 120.9842, 100.0),
        TdtTransmitter("Hanoi Transmitter", "Hanói", "VN", "DVB-T2", 21.0245, 105.8412, 100.0),
        TdtTransmitter("Ho Chi Minh City", "Ciudad Ho Chi Minh", "VN", "DVB-T2", 10.8231, 106.6297, 100.0),
        TdtTransmitter("Bangkok Transmitter", "Bangkok", "TH", "DVB-T2", 13.7563, 100.5018, 100.0),
        TdtTransmitter("Kuala Lumpur Tower", "Kuala Lumpur", "MY", "DVB-T2", 3.1478, 101.6953, 100.0),
        TdtTransmitter("Singapore Bukit Batok", "Singapur", "SG", "DVB-T2", 1.3521, 103.8198, 50.0)
    )

    /** Devuelve los N transmisores más cercanos con su distancia en km */
    fun nearest(lat: Double, lon: Double, maxCount: Int = 20): List<Pair<TdtTransmitter, Double>> {
        return transmitters
            .map { it to distanceKm(lat, lon, it.lat, it.lon) }
            .sortedBy { it.second }
            .take(maxCount)
    }

    /** Filtra por país con distancia */
    fun byCountry(isoCode: String, lat: Double? = null, lon: Double? = null): List<Pair<TdtTransmitter, Double?>> =
        transmitters
            .filter { it.country.equals(isoCode, ignoreCase = true) }
            .map { t ->
                val dist = if (lat != null && lon != null) distanceKm(lat, lon, t.lat, t.lon) else null
                t to dist
            }
            .sortedBy { it.second ?: Double.MAX_VALUE }

    /** Busca por nombre o ciudad con distancia opcional */
    fun search(query: String, lat: Double? = null, lon: Double? = null): List<Pair<TdtTransmitter, Double?>> =
        transmitters
            .filter {
                it.name.contains(query, ignoreCase = true) ||
                it.city.contains(query, ignoreCase = true) ||
                it.country.contains(query, ignoreCase = true)
            }
            .map { t ->
                val dist = if (lat != null && lon != null) distanceKm(lat, lon, t.lat, t.lon) else null
                t to dist
            }
            .sortedBy { it.second ?: Double.MAX_VALUE }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon/2) * Math.sin(dLon/2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    }
}
