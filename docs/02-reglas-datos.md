# 02 · Reglas de tratamiento de datos

> Estado: borrador v0.1 · 2026-10-25
> Base: lámina "Ethics" del curso (ACM/IEEE Software Engineering Code of Ethics, Código de Ética del CIP, "Ethical issues": log files y protección de datos) + decisiones de producto tomadas en la planificación.

---

## 1. Principios

| # | Principio | Qué significa aquí |
|---|---|---|
| P1 | **Local primero y único** | Todos los datos viven solo en el teléfono. No hay servidor, cuenta de usuario ni analítica. |
| P2 | **Trazabilidad total** | Nada se sobrescribe ni se borra: todo hecho es un evento inmutable. |
| P3 | **Mínimo necesario** | Solo se guardan los datos que una funcionalidad usa. Nada "por si acaso". |
| P4 | **El usuario controla la salida** | Ningún dato sale del dispositivo sin una acción explícita del usuario (compartir, exportar, respaldar). |
| P5 | **Transparencia** | La app explica en lenguaje simple qué guarda, dónde y cómo borrarlo (pantalla "Privacidad"). |
| P6 | **Recuperable** | Ante un error de código o un registro equivocado, siempre se puede reconstruir y corregir sin perder historia. |

---

## 2. Qué datos se guardan

| Tipo | Ejemplos | Sensibilidad | Dónde |
|---|---|---|---|
| Financieros | Saldos, montos, cuentas (alias "BBVA", "BCP"), movimientos | **Alta** | BD cifrada |
| Terceros | Nombres o alias de personas (mamá, pareja) | **Alta** (datos personales de terceros) | BD cifrada |
| Notas libres | Texto que escribe el usuario | Alta | BD cifrada |
| Preferencias | Tema, frecuencia de recordatorio, último respaldo, seguridad (bloqueo, minutos, pantalla segura, ocultar montos) | Baja | DataStore |
| Clave de cifrado | Clave de la BD | **Crítica** | Android Keystore (envuelta), nunca en texto plano |

**No se guarda nunca**: números de cuenta o de tarjeta completos, CCI, claves bancarias, tokens, DNI, ubicación ni contactos del teléfono. Para personas, se recomienda usar alias ("Mamá"), no nombres completos.

> Marco legal de referencia: Ley N.º 29733 de Protección de Datos Personales (Perú). Al no haber transferencia a terceros ni servidor, la app no "trata" datos de otros usuarios; aun así se aplican sus principios (finalidad, proporcionalidad, seguridad).

---

## 3. Almacenamiento y seguridad

| Regla | Implementación |
|---|---|
| S1. BD cifrada | Room + **SQLCipher**. Clave aleatoria de 256 bits generada al primer inicio, envuelta con una clave AES del **Android Keystore**. |
| S2. Bloqueo de app | **BiometricPrompt** (huella/rostro) con credencial del dispositivo (PIN/patrón/clave) como alternativa; sin PIN propio. Bloqueo en arranque en frío y al volver de segundo plano tras N minutos (5 por defecto; 0/1/5/15). El bloqueo es de interfaz: la clave del Keystore no exige autenticación. Desactivarlo o borrar los datos pide autenticar. |
| S3. Sin respaldo automático de Android | `android:allowBackup="false"` + `dataExtractionRules` excluyendo todo (evita copias silenciosas a Google Drive y transferencia entre dispositivos). |
| S4. Sin Internet | El manifest **no** declara `android.permission.INTERNET`. Verificado en CI (falla el build si aparece). |
| S5. Pantallas seguras | `FLAG_SECURE` en pantallas con montos (oculta la vista previa en recientes y bloquea capturas). Configurable por el usuario. |
| S6. Logs | Prohibido loguear montos, nombres, notas o payloads de eventos. En release, los logs de depuración se eliminan con R8. |
| S7. Archivos temporales | Imágenes/PDF para compartir se crean en `cacheDir`, se exponen con `FileProvider` y se borran al terminar o al iniciar la app. |
| S8. Ofuscación | R8 activado en release. |

---

## 4. Integridad y trazabilidad (Event Sourcing)

### 4.1 Reglas del registro de eventos
| Regla | Detalle |
|---|---|
| E1. Append-only | Solo `INSERT` en la tabla `events`. Triggers SQLite abortan cualquier `UPDATE` o `DELETE`. |
| E2. Inmutabilidad lógica | Un error se corrige con un evento nuevo (`MovementCorrected`, `MovementVoided`) que referencia al original. |
| E3. Dos fechas | `occurredOn` (fecha real del hecho) y `recordedAt` (fecha de registro). Permite registrar hechos atrasados sin perder la verdad. |
| E4. Versionado | Cada evento lleva `schemaVersion` y `appVersion`. Eventos viejos se leen con *upcasters*, nunca se reescriben. |
| E5. Cadena de hashes | `hash = SHA-256(previousHash + contenido canónico)`. Al iniciar y antes de respaldar, se verifica la cadena; si se rompe, se avisa y se ofrece restaurar. |
| E6. Secuencia | `sequence` monotónico sin huecos; detecta eventos perdidos. |
| E7. Motivo | Correcciones y anulaciones exigen un `reason` (texto corto). |

### 4.2 Proyecciones
| Regla | Detalle |
|---|---|
| R1. Derivadas | Saldos, por cobrar, fondos, composición de origen, etc. **no son la fuente de verdad**. |
| R2. Reconstruibles | `ReplayProjectionsUseCase` borra y regenera todas las proyecciones desde el evento 1. |
| R3. Atómicas | Evento + actualización de proyección en la **misma transacción**. |
| R4. Versión de reglas | Cada proyección guarda `projectorVersion`. Si cambia la lógica, se re-proyecta automáticamente. |
| R5. Diagnóstico | Herramienta de desarrollador: comparar proyección actual vs. re-proyectada y listar diferencias. |

### 4.3 Realidad vs. código: conciliación en dos pasos
| Regla | Detalle |
|---|---|
| C1. Registrar no ajusta | `BalanceCheckRecorded` guarda saldo real vs. saldo de la app. **No cambia ningún saldo.** |
| C2. Investigar primero | Mientras la conciliación está abierta, los movimientos olvidados o las correcciones se registran **vinculados** a ella y la diferencia restante se recalcula en vivo. |
| C3. Ajustar solo al resolver | El ingreso o egreso **no identificado** se crea únicamente con `ReconciliationResolved`, por el monto exacto de la diferencia restante. |
| C4. Causa obligatoria | Toda resolución declara causa: `BALANCED`, `MISSING_RECORD`, `RECORDING_ERROR`, `SUSPECTED_APP_ERROR` o `UNKNOWN`. |
| C5. Origen aislado | Un ingreso no identificado usa la fuente `UNKNOWN_ADJUSTMENT`, que **no contamina** otras fuentes. |
| C6. Re-verificación | Tras cada re-proyección se recalculan todas las conciliaciones; si una diferencia cambió, se marca `needs_review` (indicio de error de código, antes o ahora). |
| C7. Cancelable | Si el saldo real se tipeó mal, la conciliación se cancela con motivo; no deja ajustes. |

---

## 5. Validaciones de datos (reglas de negocio mínimas)

| Dato | Regla |
|---|---|
| Monto | Entero en céntimos, **> 0**. El signo lo da el tipo de movimiento, nunca el monto. |
| Moneda | Código ISO 4217 (`PEN`, `USD`). Movimientos entre monedas distintas requieren tipo de cambio manual. |
| Fecha del hecho | No futura (excepto ingresos esperados). |
| Transferencia | Cuenta origen ≠ destino. |
| Gasto dividido | La suma de las partes = monto total, al céntimo. |
| Cúmulo | Siempre tiene `responsiblePersonId`. |
| Gasto | No puede estar en un cúmulo **y** en un fondo a la vez. |
| Reembolso | Monto asignado a gastos ≤ pendiente total; el excedente se rechaza o se registra explícitamente como saldo a favor. |
| Arqueo de fondo | Saldo declarado ≥ 0. |
| Categoría | Exactamente una por gasto/parte. |
| Conceptos (tags) | 0..n por gasto; no se suman entre sí en reportes de distribución. |

---

## 6. Salida de datos (compartir, exportar, respaldar)

| Acción | Contenido | Formato | Protección |
|---|---|---|---|
| Compartir rendición | Detalle de gastos de un cúmulo/periodo + total pendiente | Imagen PNG o PDF | El usuario elige app destino (Intent nativo). Vista previa antes de enviar. Sin saldos de cuentas. |
| Exportar reporte | Movimientos / reportes filtrados | CSV o PDF | Aviso: "este archivo no está cifrado". |
| Respaldo | Registro completo de eventos + metadatos | Archivo `.fbak` (JSON comprimido y cifrado AES-256-GCM, clave derivada de contraseña con PBKDF2/Argon2) | Contraseña obligatoria; destino elegido con Storage Access Framework. |
| Restaurar | Importa eventos, verifica hashes, reproduce proyecciones | `.fbak` | Requiere contraseña; muestra resumen antes de reemplazar. |
| Exportar diagnóstico | Eventos, asientos y proyecciones del alcance de una conciliación (ver `04-modelo-datos.md` §3.3.1) | `.fdiag` (ZIP: PDF + CSV + JSONL + JSON) | Anonimizado por defecto; contraseña opcional; vista previa de qué se incluye; queda registrado con `DiagnosticExportCreated`. |
| Abrir diagnóstico | Paquete recibido de otra persona | `.fdiag` | Se importa en una BD **separada** y de solo lectura; nunca se mezcla con los datos propios; borrable. |
| Exportar diccionario | Seudónimo → nombre real (y notas elegidas) de un paquete ya exportado | `.fkey` (JSON cifrado) | Solo tras la auditoría, desde la misma conciliación; parcial por defecto; contraseña obligatoria; queda registrado con `DiagnosticDictionaryExported`. |

**Reglas del paquete de diagnóstico**:
- DG1. Se genera solo por acción explícita desde una conciliación (o desde herramientas de desarrollador).
- DG2. Por defecto: alcance mínimo (`RECONCILIATION`), alias reemplazados por seudónimos y notas eliminadas. Montos, fechas e IDs se conservan porque son necesarios para reproducir el error.
- DG3. Antes de compartir se muestra exactamente qué contiene (cantidad de eventos, rango de fechas, campos anonimizados).
- DG4. El paquete nunca incluye la clave de la BD, el PIN ni preferencias del dispositivo.
- DG5. Quien lo recibe debe tratarlo como dato personal de un tercero: borrarlo del espacio de diagnóstico al terminar.
- DG6. **Paquete y diccionario siempre por separado.** El paquete nunca contiene el diccionario. Se recomienda enviarlos por canales o en momentos distintos.
- DG7. El diccionario solo se puede extraer **desde la conciliación que generó el paquete** y solo si existe ese paquete.
- DG8. Parcial por defecto: revela solo lo pedido en la solicitud del reporte de diferencias. Antes de exportar se muestra la lista exacta de nombres y notas que se revelarán.
- DG9. Contraseña **obligatoria** para el diccionario.
- DG10. El diccionario solo se aplica si su `exportId` y `packageHash` coinciden con el paquete abierto, y vive solo dentro del espacio de diagnóstico.

**Recordatorio de respaldo**: WorkManager periódico; notificación si pasaron más de N días (configurable, por defecto 15) desde el último respaldo exitoso.

---

## 7. Retención y borrado

| Dato | Retención |
|---|---|
| Eventos | Indefinida (son la historia). |
| Proyecciones | Mientras existan los eventos; regenerables. |
| Temporales de compartir | Se borran al terminar o en el siguiente inicio. |
| **Borrado total** | Única operación destructiva permitida: "Borrar todos mis datos". Exige autenticación biométrica + confirmación escrita, sugiere respaldar antes, elimina la BD y la clave del Keystore (los datos quedan irrecuperables). |

---

## 8. Ética de diseño (del curso, aplicada)
- **Uso del tiempo**: sin notificaciones de engagement; solo recordatorios útiles y configurables (respaldo, ingreso esperado vencido, cobro pendiente).
- **Peor escenario** (pregunta de la lámina "Considerar"): alguien con acceso al teléfono desbloqueado → mitigado con bloqueo propio de la app, `FLAG_SECURE` y BD cifrada.
- **Datos de terceros**: la rendición compartida solo muestra lo necesario para la persona que reembolsa.
- **Honestidad**: los cálculos estimados (fondos, proporción de origen) se rotulan como **estimados** en la UI.
