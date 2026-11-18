# Spike OKOK · APK de captura y decodificación

Spike técnico del proyecto **Gooluco** (App IMC-Edge, SRS-OKOK-DM-002 v2). Valida **solo** la captura
de las tramas BLE de balanzas con protocolo **OKOK** (chip Chipsea y marcas OEM que usan la app
«OKOK International»): escanear, reconocer la variante, decodificar peso/impedancia y exportar
las tramas reales. No incluye perfil, IMC, riesgo ni cuestionarios: eso va en la app principal.

> Esta carpeta es aislada (`spike-okok/`) y no define la estructura de la app principal.

## Objetivo

1. Confirmar con hardware real qué variante OKOK emite la balanza elegida (C0, V20, V11, VF0 o GATT).
2. Verificar que el decodificador (`okok-decoder/`, Kotlin puro) obtiene el mismo peso que la pantalla de la balanza.
3. Recoger un CSV de tramas reales para cerrar **TBD-01**, **TBD-02** y **TBD-03** del SRS.

## Qué hace el APK

- Pide permisos BLE: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` en Android 12+, ubicación en Android 8–11.
  En Android 12+ también pide ubicación precisa: el permiso de escaneo se declara **sin**
  `neverForLocation` para que el sistema no filtre anuncios (más robusto para un spike).
- Escanea anuncios BLE sin filtros de hardware (en C0 el Company ID cambia en cada trama porque el
  byte alto es un contador), y filtra por software las variantes OKOK.
- Por cada bloque de *manufacturer data* muestra en vivo: hora (ms), origen, MAC, nombre, RSSI,
  Company ID, hex crudo, variante, peso en kg (valor crudo, divisor, unidad), impedancia,
  checksum, cifrado, MAC embebida, product ID y contador.
- Marca la **LECTURA ESTABLE**: primera trama con flag estable de cada pesaje (C0, V20, V11) o,
  en VF0 (sin flag), tres pesos iguales seguidos. Las tramas no estables salen como «provisional»;
  las que fallan longitud o checksum, como «RECHAZADA» con el motivo.
- Interruptor «Solo OKOK»: apagado registra también anuncios de otros dispositivos (útil si la
  balanza usa un formato no documentado).
- **Simular**: inyecta TV-01..TV-04 y tramas sintéticas V20/V11/VF0 (incluida una V20 con
  checksum alterado) para probar sin balanza.
- **CSV**: exporta todo el log (hasta 50 000 filas) y abre el menú de compartir (correo, Drive, WhatsApp…).
- **Probar GATT FFF0** (opcional, en cada trama OKOK real): se conecta, lista los servicios,
  se suscribe a las notificaciones de `0xFFF1` y las registra en crudo (`GATT_RAW`).
  No envía el «user sync» por `0xFFF2`; es solo para ver si la balanza expone ese servicio.

## Instalar el APK

1. Descarga el artifact **`spike-okok-debug-apk`** del último run verde del workflow
   *Spike OKOK · APK debug* (pestaña **Actions** del repositorio) y descomprímelo.
2. Pásalo al teléfono (Android 8.0 / API 26 o superior, con BLE) y ábrelo; acepta «instalar apps
   de origen desconocido». También con `adb install -r app-debug.apk`.
3. El APK está firmado con la clave *debug* de cada run de CI: si al actualizar dice que la firma no
   coincide, desinstala la versión anterior. El paquete es `pe.cravapp.gooluco.spike.okok.debug`.

## Cómo probar

**Sin balanza:** abre la app → **Simular**. Debes ver un pesaje C0 que sube como «provisional» y
se estabiliza en **67,05 kg · 600,0 Ω** (TV-01), luego TV-04 (50,75 kg provisional), TV-03 (81,50 kg),
TV-02 (76,30 kg · 500,0 Ω), la V20 sintética (72,40 kg), su copia con checksum malo (RECHAZADA),
V11 (65,3 kg) y VF0 (58,6 kg, estable a la tercera repetición).

**Con balanza real:**
1. Bluetooth activado (y ubicación del sistema activada en Android 8–11).
2. **Escanear** y súbete a la balanza descalzo (para que mida impedancia, si la soporta).
3. Quédate quieto hasta que la balanza fije el peso. Anota el peso que muestra su pantalla.
4. Repite 3–5 pesajes; si es posible, uno en lb/st (cambiando la unidad en la balanza) y uno de
   otra persona.
5. Pulsa **Detener** y luego **CSV** para compartir el archivo.

## Qué capturar (para cerrar los TBD del SRS)

Para cada balanza probada envía el CSV junto con: marca/modelo, foto de la etiqueta, nombre BLE
mostrado, pesos que mostró la pantalla en cada pesaje y si la app OKOK oficial muestra el IMC.

| TBD | Pregunta | Qué mirar en el CSV |
|-----|----------|---------------------|
| TBD-01 | Variante de la balanza | columna `variant` y `company_id`; si sale vacío con «Solo OKOK» apagado, el formato es otro |
| TBD-02 | Formato GATT / C0 cifrado | resultado de «Probar GATT FFF0» (servicios y filas `GATT_RAW`), columna `encrypted` |
| TBD-03 | ¿La balanza transmite IMC? | comparar `data_hex` / `scan_record_hex` con el IMC de la pantalla o de la app OKOK |

Comprobaciones clave: `weight_kg` = peso de la pantalla; `stable_reading=true` solo una vez por
pesaje; checksum OK en V20/V11; MAC embebida en C0 = MAC del anunciante.

## Estructura

```
spike-okok/
├── okok-decoder/   Kotlin/JVM puro: OkokDecoder, StableReadingDetector, CsvLog, vectores de prueba + tests JUnit
└── app/            Android (Compose): permisos, escaneo BLE, simulador, GATT opcional, exportación CSV
```

Compilar localmente (JDK 17 y Android SDK 34):

```bash
cd spike-okok
./gradlew :okok-decoder:test :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

CI: `.github/workflows/spike-okok-apk.yml` (push a `spike/okok-capture` o ejecución manual): tests
+ `assembleDebug` y sube el APK como artifact. No usa secretos.

## Protocolo implementado

Índices sobre los bytes que siguen al Company ID (Android ya lo entrega como entero).

| Variante | Company ID | Longitud | Campos |
|----------|------------|----------|--------|
| C0 | byte bajo `0xC0` (alto = contador) | 13 | [0–1] peso BE; [2–3] impedancia BE ÷10 (bit 7 de [2] = cifrada); [4–5] product ID; [6] bit0 estable, bits1–2 decimales (0→÷10, 1→÷1, 2→÷100), bits3–4 unidad (kg, jin, lb, st:lb); [7–12] MAC |
| V20 | `0x20CA` | 19 | [6] bit0 final, bit2 ÷100 (si no ÷10); [8–9] peso BE; [10–11] impedancia BE ÷10; [12] XOR desde `0x20` sobre [0–11] (o [0–10] en algunas firmwares) |
| V11 | `0x11CA` | 23 | [3–4] peso BE; [9] bits1–2 decimales, bits3–4 unidad; [16] XOR desde `0xCA⊕0x11` sobre [0–15]; [1] bit0 final (solo según okokscale.py) |
| VF0 | `0xF0FF` | 18 (se acepta ≥4) | peso = [3]·256 + [2], ÷10; sin checksum, estabilidad ni impedancia |

Vectores de prueba (tests en `okok-decoder/src/test`): TV-01 `0x07C0` → 67,05 kg, 600,0 Ω, estable;
TV-02 `0x3FC0` → 76,30 kg, 500,0 Ω; TV-03 `0xB1C0` → 81,50 kg estable; TV-04 `0x08C0` → 50,75 kg no
estable. Las tramas V20/V11/VF0 de los tests son **sintéticas** (construidas desde la especificación),
no capturas reales.

## Fuentes y licencias

El decodificador es una **implementación propia** escrita a partir de la especificación del SRS v2
(§2.2) y contrastada con estas fuentes abiertas; no se copió su código:

- openScale — `OkOkHandler.kt`, `ChipseaBleHandler.kt` (GPLv3): https://github.com/oliexdev/openScale
- okokscale.py — `parser.py` (Apache-2.0), usado por Home Assistant: https://github.com/rrooggiieerr/okokscale.py
- okok-bia-formulas (formato C0, TV-01): https://github.com/100nandoo/okok-bia-formulas
- Capturas TV-02..TV-04: openScale issues #1177 y #950.

## Limitaciones

- No hay especificación oficial del fabricante: todo sale de ingeniería inversa de terceros.
- V20, V11 y VF0 solo se probaron con tramas sintéticas; V10/V26 no están implementadas.
- C0 con bit de cifrado: se muestra el peso, pero no la impedancia (algoritmo/clave desconocidos).
- El flag «final» de V11 y la regla de 3 repeticiones de VF0 son supuestos a validar.
- GATT: solo suscripción cruda a `0xFFF1`; no envía el «user sync», así que muchas balanzas no
  mandarán composición corporal por ahí.
- Algunos teléfonos limitan el escaneo en segundo plano o con la pantalla apagada: mantén la app
  abierta. Android permite como máximo 5 inicios de escaneo cada 30 s.
- Los datos quedan solo en la memoria de la app y en el CSV que compartas: **no subas CSV con datos
  de pacientes al repositorio** (es público).
