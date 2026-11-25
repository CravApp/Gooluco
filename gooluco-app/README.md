# Equilibra · app base (Gooluco)

Esqueleto funcional de la app principal según el SRS v2 y el diseño `equilibra_mockup` (Material 3).

- **Paquete:** `pe.cravapp.gooluco` (debug: `pe.cravapp.gooluco.debug`) · minSdk 26 · targetSdk 34.
- **Módulos:** `:app` y `:okok-decoder`. El decodificador **no se copia**: `settings.gradle.kts` apunta a
  `../spike-okok/okok-decoder`, así el spike y la app usan el mismo código y los mismos 25 tests.
- **Sin INTERNET** en el manifiesto; base Room local; copia de seguridad en la nube desactivada.

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
| Agenda | Horarios EMA (1–12) y avisos de pesaje; notificaciones locales que se reprograman tras reinicio o cambio de hora | RF-16, RF-17, RF-23 |

**El IMC no viene de la balanza**: la balanza OKOK solo envía peso (y a veces impedancia). La app calcula
`IMC = peso / talla²` con la talla del perfil. El IMC del smartwatch se puede ingresar a mano solo para contrastar.

## Pendiente (stubs)

- Capa B: `ModeloLiteRt` busca `assets/models/riesgo.tflite`; hoy no hay modelo y la app usa solo la Capa A.
  Faltan manifiesto firmado, SHA-256 y actualización (RF-35, RF-36; TBD-04/05).
- Base cifrada (SQLCipher/Keystore), exportación CSV/FHIR (RF-38), GATT/«user sync» (RF-06), reproducción de capturas (RF-12).
- Umbrales de alertas provisionales (TBD-07) y traducción PSS-4 validada para Perú (TBD-06).

## Compilar

```bash
cd gooluco-app
./gradlew :okok-decoder:test :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
