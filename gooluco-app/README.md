# Equilibra · app base (Gooluco)

Esqueleto funcional de la app principal según el SRS v2 y el diseño `equilibra_mockup` (Material 3).

- **Paquete:** `pe.cravapp.gooluco` (debug: `pe.cravapp.gooluco.debug`) · minSdk 26 · targetSdk 34.
- **Módulos:** `:app` y `:okok-decoder`. El decodificador **no se copia**: `settings.gradle.kts` apunta a
  `../spike-okok/okok-decoder`, así el spike y la app usan el mismo código y los mismos 25 tests.
- Base Room local (v2, con migración 1→2); copia de seguridad en la nube desactivada; **funciona 100 % sin conexión**.
- Red (`INTERNET`, `ACCESS_NETWORK_STATE`) solo para la sincronización **opcional** con el servidor de prueba;
  solo HTTPS (`usesCleartextTraffic="false"` y `network_security_config`).

## Pantallas

| Pestaña / pantalla | Qué hace | SRS |
|---|---|---|
| Bienvenida / Perfil | Consentimiento, talla (100–230 cm, con historial), fecha de nacimiento, sexo, condición, cintura, meta; revocar y borrar todo | RF-01, RF-02, RF-37, RF-39 |
| **Hoy** | Chip BLE, peso actual vs meta (anillo) y Δ semanal, «Registro diario x/7», «Nórdico hoy», «ML local», tarjeta de riesgo, pesaje matutino, agenda del día, proyección lineal a 14 días, chip Offline, FAB | RF-32 |
| **Balanza** | Escaneo pasivo OKOK (C0/V20/V11/VF0) con el decodificador del spike, peso en vivo, guarda solo el estable, «¿es usted?» si difiere > 5 kg, deduplicación 60 s, vincular balanza, simulador C0, IMC del smartwatch opcional para contraste | RF-03..RF-11, RF-13..RF-15 |
| **Nórdico** | Cuestionario Nórdico (9 regiones: 12 meses, impedimento, 7 días) | (diseño, no SRS) |
| **Análisis** | Riesgo Capa A (reglas OMS/FINDRISC/cintura + alertas), estado de la Capa B (LiteRT), indicadores, gráfico 28 días, resúmenes diarios y Spearman estrés↔Δ24h (≥ 14 pares, «exploratorio») | RF-24..RF-33 |
| Estrés (EMA) | PSS-4 (ítems 2 y 3 invertidos) + actividad (13), lugar (5), social; omitir; ventana de 60 min | RF-18..RF-21 |
| FINDRISC | 8 ítems, edad e IMC autocompletados, corte peruano ≥ 11; no aplica a DM2 | RF-22 |
| Servidor (Perfil → Ajustes) | Sincronización opcional con gooluco-api: consentimiento, inicio de sesión SMART, estado, «Sincronizar ahora», ver pesos del servidor, cerrar sesión | — |
| Agenda | Horarios EMA (1–12) y avisos de pesaje; notificaciones locales que se reprograman tras reinicio o cambio de hora | RF-16, RF-17, RF-23 |

**El IMC no viene de la balanza**: la balanza OKOK solo envía peso (y a veces impedancia). La app calcula
`IMC = peso / talla²` con la talla del perfil. El IMC del smartwatch se puede ingresar a mano solo para contrastar.

## Pendiente (stubs)

- Capa B: `ModeloLiteRt` busca `assets/models/riesgo.tflite`; hoy no hay modelo y la app usa solo la Capa A.
  Faltan manifiesto firmado, SHA-256 y actualización (RF-35, RF-36; TBD-04/05).
- Base cifrada (SQLCipher/Keystore), exportación CSV (RF-38), GATT/«user sync» (RF-06), reproducción de capturas (RF-12).
- Umbrales de alertas provisionales (TBD-07) y traducción PSS-4 validada para Perú (TBD-06).

## Sincronización con servidor (opcional)

**Desactivada por defecto.** Se activa en *Perfil → Ajustes → Sincronización con servidor (opcional)* tras aceptar un
aviso: el servidor `https://gooluco-api.mercadeoperuviano.workers.dev` es **de prueba** y solo deben usarse datos
ficticios. Si no se activa, la app no hace ninguna llamada de red.

- **Inicio de sesión SMART on FHIR** con [AppAuth-Android](https://github.com/openid/AppAuth-Android): descubrimiento
  en `/fhir/.well-known/smart-configuration`, cliente público `gooluco-android`, PKCE S256, `state`, `nonce` y
  `aud=<base>/fhir`. Redirección `pe.cravapp.gooluco:/oauth2redirect` (igual en debug y release).
  Scopes: `launch/patient openid fhirUser offline_access patient/Patient.rs patient/Observation.crs patient/QuestionnaireResponse.crs`.
  El paciente se toma del campo `patient` de la respuesta del token.
- **Tokens** cifrados en `EncryptedSharedPreferences` (AES-256 con clave del Keystore). El refresh token rota en cada
  renovación y se guarda el nuevo. «Cerrar sesión» y «Borrar todo» eliminan los tokens.
- **Qué se envía** (un recurso por POST, `subject = Patient/<paciente del token>`, fecha con zona horaria):

  | Dato local | Recurso FHIR |
  |---|---|
  | Pesaje estable | `Observation` LOINC 29463-7, kg (categoría vital-signs; nota si es simulado) |
  | IMC del pesaje | `Observation` 39156-5, kg/m2, `derivedFrom` → peso |
  | Talla (cada cambio de perfil; la vigente siempre) | `Observation` 8302-2, cm |
  | Cintura (FINDRISC) | `Observation` 8280-0, cm |
  | EMA respondida | `QuestionnaireResponse` `pss4-ema` (opciones marcadas 0–4 sin invertir, total, actividad, lugar, social) |
  | FINDRISC | `QuestionnaireResponse` `findrisc` (respuestas reconstruidas desde los puntos, total) |
  | Nórdico | `QuestionnaireResponse` `nordico` (grupo por región con `REGION.molestias12m`, `.impidio12m`, `.molestias7d`) |

  **Nunca** se envían la MAC de la balanza, la trama cruda (`tramaHex`), la impedancia ni el RSSI.
  Cada recurso lleva un `identifier` (`…/fhir/sid/registro-local`, `<instalación>/<TIPO>:<id>`) sin datos personales.
- **Qué se sube:** por defecto solo lo registrado desde la activación (opción para incluir lo anterior).
- **Cola y estado:** tabla `sync_estado` (id remoto, estado, intentos, error). `WorkManager` sube con red disponible,
  con backoff exponencial ante fallos transitorios (sin red, 5xx, 429) y una pasada periódica cada 6 h. Un 401 provoca
  una renovación del token y un reintento; los rechazos 4xx quedan como «rechazados» y se reintentan a mano.
- **Pantalla Servidor:** paciente, última sincronización, pendientes/enviados/rechazados, errores, «Sincronizar ahora»,
  «Reintentar rechazados», «Ver mis pesos en el servidor» (solo lectura) y «Cerrar sesión».

### Probar con el usuario de prueba «ana»

1. Instalar el APK y completar el perfil con datos **ficticios**.
2. *Perfil → Ajustes → Sincronización con servidor (opcional)* → activar el interruptor y aceptar el aviso.
3. «Conectar e iniciar sesión»: se abre la página de consentimiento del servidor en una pestaña del navegador;
   elegir **ana** y pulsar **Autorizar**. La app vuelve y muestra `Patient/demo-ana`.
4. Hacer un pesaje con el simulador (pestaña Balanza) o responder una EMA / FINDRISC / Nórdico.
5. «Sincronizar ahora» y luego «Ver mis pesos en el servidor».

Ejemplos de JSON generados por el mapeador (paciente `demo-ana`): `./gradlew :app:testDebugUnitTest` los escribe en
`app/build/fhir-ejemplos/` y se pueden enviar con `curl` siguiendo el README de `gooluco-api`.

## Compilar

```bash
cd gooluco-app
./gradlew :okok-decoder:test :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
