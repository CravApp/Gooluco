# Gooluco

App Android **Equilibra** (App IMC-Edge, SRS-OKOK-DM-002 v2): peso desde balanzas BLE con protocolo
**OKOK**, IMC calculado con la talla del perfil, agenda diaria de cuestionarios de estrés (PSS-4 + contexto
del paper SOSW), FINDRISC, Cuestionario Nórdico y un resumen de riesgo físico para personas con diabetes
o prediabetes. Todo funciona sin conexión: los datos no salen del teléfono.

| Carpeta | Contenido |
|---|---|
| `gooluco-app/` | App principal (Kotlin, Jetpack Compose, Room, LiteRT). Ver [gooluco-app/README.md](gooluco-app/README.md). |
| `spike-okok/` | Spike de captura BLE OKOK y el módulo compartido `okok-decoder` (Kotlin puro, con tests). |

## CI

- `.github/workflows/gooluco-app.yml`: tests + APK debug de la app en cada push a `feat/**` y en los PR.
- `.github/workflows/spike-okok-apk.yml`: APK del spike (rama `spike/okok-capture`).

El APK se descarga como artifact (`gooluco-app-debug-apk`) del run en la pestaña **Actions**.

> Apoyo, no diagnóstico. El repositorio es público: no subir datos de pacientes, capturas con MAC reales ni secretos.
