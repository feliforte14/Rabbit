# Informe de seguridad (Eje D)

Entregable 6 de los *Criterios de Testing y Aseguramiento de Calidad*
(Clase 15). Estados: **CUMPLE**, **NO CUMPLE**, **NO VERIFICABLE** (no hay
test ejecutado ni evidencia) y **NO APLICA**. Una afirmación sin test
ejecutado no cuenta como CUMPLE.

Base de referencia: OWASP ASVS 5.0 y OWASP API Security Top 10. MASVS no
aplica (no hay app móvil nativa en este alcance).

## Matriz de criterios

| # | Prueba de la cátedra | Estado | Evidencia |
|---|---|---|---|
| 1 | BOLA/IDOR: usuario A pide un recurso de B | CUMPLE | `ApiRestIT.unPedidoDeOtroComercioEs404` (ERP de otro comercio: 404, y el pedido no se cancela). En la web, los servicios resuelven el comercio con `IContextoUsuario` y nunca lo reciben del cliente. El CI pasa `-Drabbit.erp2.*`, así que el test corre. |
| 2 | BFLA: rol no administrador invoca endpoint de otro rol | CUMPLE | `ApiRestIT.unUsuarioConRolComercioEs403`; `@RolesAllowed` en los EJB (la vista solo oculta botones). |
| 3 | JWT vencido, firma alterada, `alg=none`, otro emisor | NO APLICA (equivalente: CUMPLE) | Rabbit no usa JWT (la cátedra confirmó que no es obligatorio si se cumplen sus condiciones; ver ADR-067): la API usa HTTP Basic y la web, sesión. `SeguridadIT.unaCredencialInvalidaOdeOtroEsquemaEs401` manda JWT falsos (`alg=none`, firma alterada, vencido) y Basic mal formado: todos dan 401 en Problem Details, nunca 200 ni 500. |
| 4 | Mass assignment | CUMPLE | `ApiRestIT.losCamposNoPermitidosEnElCuerpoSeIgnoran`: `idComercio`, `estadoPedido`, `resultado` e `idPedido` en el cuerpo se ignoran; el comercio sale de la cuenta del ERP. |
| 5 | Inyección SQL / NoSQL / XSS en todos los campos de texto | CUMPLE en la API; NO VERIFICABLE en formularios JSF | API: `cadaCampoDeTextoAguantaUnaInyeccion`, `unaDireccionConSqlYHtmlSeGuardaComoTexto`, `unCodigoDeSeguimientoConInyeccionNoRompeNada`, y ZAP activo con SQLi/XSS en `FAIL`. JSF: escapa por defecto (`#{...}`), pero no hay test automatizado de los formularios; se prueba a mano antes de la defensa. |
| 6 | XXE y entidades externas en SOAP | CUMPLE | `SeguridadIT.elCanalSoapRechazaEntidadesExternas` y `elCanalSoapRechazaLaExpansionDeEntidades` (ambos servicios SOAP). |
| 7 | Fuerza bruta sobre el login | CUMPLE | API: `SeguridadIT.laFuerzaBrutaContraLaApiTerminaEn429` (`FiltroAutenticacionErp`, 429 + `Retry-After`). Web: `LimiteDeIntentos` bloquea 5 intentos fallidos por 15 minutos y el login ni consulta al realm; responde con un mensaje en la pantalla, no con 429 (es un formulario JSF). |
| 8 | ZAP baseline + activo autenticado | CUMPLE en el CI | Jobs `zap` y `zap-api` de `seguridad.yml`; reglas en `.zap/`. Medium: ver "Alertas justificadas". |
| 9 | Dependencias (0 CVE críticas o altas sin mitigación) | CUMPLE | OSV-Scanner bloquea si la vulnerabilidad llega al WAR; Trivy sobre la imagen. Dependency-Check solo corre con el secret `NVD_API_KEY`; sin él, la cobertura es OSV. |
| 10 | Headers, CORS y cookies | CUMPLE | `FiltroCabecerasDeSeguridad` (CSP, `nosniff`, `X-Frame-Options`, `Permissions-Policy`, `Referrer-Policy`), `FiltroHsts`, cookie `Secure`/`HttpOnly`/`SameSite`; `unOrigenAjenoNoRecibeCabecerasCors`; `laCookieDeSesionYLasCabecerasDeSeguridad`. |
| 11 | Logs y errores sin stack traces, PII ni tokens | CUMPLE (parcial en logs) | Errores: `losErroresNoMuestranDetallesInternos`, `ProblemaMapper`, `error.html`. Logs: `DatosPersonales` (email enmascarado), probado en `DatosPersonalesTest`; el geocodificador no escribe direcciones ni coordenadas, probado en `RuteoServiciosTest.elLogNoTraeLaDireccionNiLasCoordenadas` (ver hallazgos). Que ningún log escriba credenciales no está cubierto por un test: NO VERIFICABLE. |
| 12 | Cifrado en tránsito | CUMPLE | `transport-guarantee CONFIDENTIAL` (HTTP redirige a HTTPS); `SeguridadIT.porHttpRedirigeAHttps`. |
| 13 | Contraseñas con hash adaptativo | **NO CUMPLE (parcial)** | Las cuentas de la base usan bcrypt (costo 10, `ClavesTest`). Las cuentas que siguen en los archivos de `add-user.sh` (el administrador inicial y las anteriores a ADR-031) están en MD5. |
| 14 | Datos sensibles minimizados | CUMPLE | Mensajes JMS mínimos (`MensajesJmsMinimosTest`); el seguimiento público no muestra datos de quien recibe. |
| G6 | Sin secretos en el historial | CUMPLE | Incidente del 27/10 documentado en `INCIDENTE-2026-10-27-SECRETOS-EN-HISTORIAL.md`: contraseñas rotadas, historial reescrito y `.gitleaks.toml` propio en el CI. |

## Hallazgos y tratamiento

| Hallazgo | Severidad | Tratamiento |
|---|---|---|
| Cuentas en MD5 (administrador inicial y viejas) | Mayor | Abierto. Mitigación: un administrador restablece la contraseña desde `personal/usuarios.xhtml` y la cuenta pasa a bcrypt. Pendiente de hacer en el entorno de la demo. |
| Contraseña mínima de 6 caracteres | Menor | **Corregido**: ahora 8 (ASVS 5.0, V6.2.1). `UsuarioService.LARGO_MINIMO_CLAVE`, probado en `UsuarioServiceTest`. |
| Documentación decía que la API del ERP no tenía freno de intentos | Menor | **Corregido** en `SEGURIDAD.md`: lo tiene (`FiltroAutenticacionErp`). |
| Test de mass assignment no verificaba el comercio | Menor | **Corregido**: ahora verifica que `idComercio` no sea el del cuerpo. |
| El geocodificador escribía en el log el texto de las direcciones (incluidas las de entrega) y sus coordenadas, contra SEC-09. Se vio al revisar el log de un arranque | Mayor | **Corregido**: ya no se loguean la dirección ni las coordenadas, con un test que lo verifica. |
| Error `SEVERE` al cargar Pedidos (`parseBigDecimal` no existe en `f:convertNumber`) | Menor | **Corregido**: se quitó el atributo, que Mojarra ignoraba. |
| Límite de intentos en memoria de cada servidor | Menor | Aceptado y documentado: se pierde al reiniciar y no se comparte entre nodos. |
| Certificado HTTPS autofirmado; HSTS sin efecto en desarrollo | Menor | Aceptado y documentado; en producción iría un certificado válido. |
| Banco y transportistas simulados sin autenticación | Menor | Aceptado: son sistemas de terceros simulados en el mismo WAR. |

## Alertas justificadas (ZAP)

- **CSP con `script-src 'unsafe-inline'` y `style-src 'unsafe-inline'`
  (Medium):** JSF (Mojarra) escribe en el HTML los manejadores de `f:ajax`
  y no ofrece nonce para eso. La CSP igual bloquea scripts de otro origen,
  conexiones a otros sitios, plugins, formularios hacia afuera y iframes.
  Regla `10055` en `WARN` en `.zap/reglas.tsv`.

Cualquier otra alerta Medium que aparezca en una corrida nueva hay que
justificarla acá, una por una, antes de la defensa.

## Escaneo de secretos

`.gitleaks.toml` (reglas propias: las por defecto no detectaron las
contraseñas del incidente) se corre en el CI sobre los commits nuevos. El
escaneo del historial completo se hizo a mano durante el incidente.

## IA en el proceso de seguridad

La declaración de uso de IA (sección 8 de los criterios) va en
`docs/consigna/USO-DE-IA.md`. Regla acordada: ningún CUMPLE se acepta
porque lo dijo la IA; vale solo con un test ejecutado o evidencia del CI.
