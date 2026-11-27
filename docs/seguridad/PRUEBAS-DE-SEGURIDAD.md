# Pruebas de seguridad: estado y cómo repetirlas

Estado al 02/11/2026 (medido sobre el commit `2f58163`; la etiqueta `v1.0-testing` hoy apunta a un commit posterior, con las correcciones del 02 y 03/11) y los comandos para repetir cada prueba en una
máquina propia (macOS o Linux con Docker). Qué protege Rabbit y cómo está en [SEGURIDAD.md](SEGURIDAD.md); acá está
**cómo se comprueba**.

## 1. Resumen

- **Matriz SEC de la Clase 11 (Trello):** los 10 criterios en CUMPLE, cada uno con un test ejecutado en verde. El 19/10
  había 2 CUMPLE, 2 NO CUMPLE (SEC-05 HSTS y SEC-09 logs) y 6 sin verificar.
- **Las 12 pruebas que la cátedra ejecuta en vivo (eje D):** las 12 pasan. Se corrieron a mano el 02/11 y quedaron
  automatizadas en el CI (pruebas de integración, E2E y el script de la sección 3).
- **Escaneos en el CI de cada PR y de `main`:** ZAP baseline con 0 alertas altas y 2 medias (el `unsafe-inline` de la CSP,
  justificado); ZAP API activo autenticado con 0 altas y 0 medias; OSV-Scanner y Trivy sin vulnerabilidades críticas
  ni altas; gitleaks y GitGuardian sin secretos; CodeQL sin hallazgos.
- **Defectos encontrados el 02/11 y corregidos** (PR #17): un XSS en el tooltip del mapa del personal (SEG-01) y la sesión
  vencida que terminaba en "Algo salió mal" con 500 (SES-01). Cada uno tiene una prueba E2E que falla si vuelve.

### Matriz SEC

| ID | Criterio | Estado | Prueba automática |
|---|---|---|---|
| SEC-01 | Límite de intentos de login (web y API) | CUMPLE | `LimiteDeIntentosTest`, `LoginBeanTest`, `SeguridadIT.laFuerzaBrutaContraLaApiTerminaEn429` |
| SEC-02 | Contraseñas con bcrypt | CUMPLE | `ClavesTest` |
| SEC-03 | Pedido de otro comercio → 404 (BOLA) | CUMPLE | `ApiRestIT.unPedidoDeOtroComercioEs404`, E2E-5 |
| SEC-04 | Sin credenciales 401, otro rol 403 | CUMPLE | `ApiRestIT.sinCredencialesEs401`, `unUsuarioConRolComercioEs403`, `SeguridadIT` |
| SEC-05 | Solo HTTPS, HSTS y cookie segura | CUMPLE | `FiltroHstsTest`, `SeguridadIT`, E2E SEG-3, ZAP |
| SEC-06 | Errores en Problem Details sin detalles internos | CUMPLE | `ApiRestIT`, `SeguridadIT.losErroresNoMuestranDetallesInternos` |
| SEC-07 | Banco sin respuesta en 5 s → el pedido no se confirma | CUMPLE | `CircuitBreakerBancoTest`, `BancoClientTest`; prueba manual (sección 4.7) |
| SEC-08 | Mensajes JMS con el mínimo de datos | CUMPLE | `MensajesJmsMinimosTest` |
| SEC-09 | Logs sin datos personales | CUMPLE | `DatosPersonalesTest`, `RuteoServiciosTest.elLogNoTraeLaDireccionNiLasCoordenadas` (el geocodificador escribía direcciones y coordenadas hasta el 02/11; corregido); `revisar_logs.sh` (sección 4.2) |
| SEC-10 | Seguimiento público sin datos de quien recibe | CUMPLE | `MejorasUltimaMillaIT.elSeguimientoPublicoNoMuestraAQuienRecibe`, E2E-3 |

### Las 12 pruebas del eje D

| Prueba de la cátedra | Resultado en vivo | Dónde se automatiza |
|---|---|---|
| BOLA/IDOR | 404 en API, remito y foto de visita ajenos; `?idComercio=` ajeno se ignora | `ApiRestIT`, E2E-5 |
| BFLA | API: 403. Web: cada rol vuelve a su inicio en las 25 pantallas (no 403, ver sección 5) | `ApiRestIT`, `pruebas_vivo.py roles` |
| JWT vencido / alterado / alg=none | No hay JWT. Equivalentes: Bearer, Basic mal formado, otro esquema → 401; cookie inventada, alterada o posterior al logout → login | `SeguridadIT`, E2E SEG-3, `pruebas_vivo.py sesion` |
| Mass assignment | Campos extra ignorados | `ApiRestIT.losCamposNoPermitidosEnElCuerpoSeIgnoran` |
| Inyección SQL / NoSQL / XSS | 0 errores 500 en los 6 campos de texto; se guarda literal; se muestra escapado | `ApiRestIT.cadaCampoDeTextoAguantaUnaInyeccion`, E2E SEG-1 y SEG-2 |
| XXE en SOAP | Rechazado, sin leer el archivo ni expandir entidades | `SeguridadIT.elCanalSoapRechaza…` |
| Fuerza bruta | 429 al 6.º intento, también con la clave correcta | `SeguridadIT`, `pruebas_vivo.py fuerza` |
| ZAP baseline + activo | 0 altas; 2 medias justificadas | Workflow Seguridad |
| Dependencias | 0 críticas o altas | OSV, Trivy, Dependency-Check |
| Headers, CORS, cookies | Todas las cabeceras; sin CORS para orígenes ajenos; cookie Secure, HttpOnly, SameSite=Lax | `SeguridadIT`, E2E SEG-3 |
| CSRF (condición de ADR-067) | Un POST con la cookie válida pero sin el token de la vista, con uno inventado o con el de otra sesión no ejecuta la acción ni da 500 | E2E SEG-6 |
| Logs y mensajes de error | Sin stack traces; el log tiene solo IDs | `SeguridadIT`, `revisar_logs.sh` |
| Datos sensibles | Solo TLS ≥ 1.2; bcrypt costo 10; seguimiento con solo código y estado | `SeguridadIT.porHttpRedirigeAHttps`, E2E-3 |

## 2. Levantar Rabbit en la notebook

```bash
cp .env.example .env        # completar DB_PASSWORD, ADMIN_USER, ADMIN_PASSWORD y DEMO_PASSWORD
docker compose up -d --build
docker compose logs -f wildfly   # esperar "Deployed \"Rabbit.war\"" y cortar con Ctrl+C
```

Variables que usan las pruebas (en cada terminal nueva):

```bash
set -a; . ./.env; set +a
q() { docker compose exec -T db psql -U rabbit -d rabbit -tAc "$1"; }
export PUNTO=$(q "SELECT p.id FROM puntos_picking p JOIN comercios c ON c.id=p.comercio_id WHERE c.nombre='Almacén Demo 1' AND p.nombre='Local Palermo'")
export ITEM=$(q "SELECT i.id FROM items_inventario i JOIN comercios c ON c.id=i.idcomercio WHERE c.nombre='Almacén Demo 1' AND i.producto='Yerba mate 1 kg'")
```

Cuentas de la semilla, todas con `DEMO_PASSWORD`: `demo.operador`, `demo.erp` y `demo.erp2` (ERP de dos comercios),
`demo.comercio` y `demo.comercio2`, `demo.repartidor`. El administrador es `ADMIN_USER` / `ADMIN_PASSWORD`.

## 3. Todo de una vez: el script en vivo

```bash
uv run scripts/seguridad/pruebas_vivo.py          # 40 chequeos, unos 30 segundos
uv run scripts/seguridad/pruebas_vivo.py roles    # un grupo: cabeceras, roles, sesion, fuerza, inyeccion, xss, errores, seguimiento
scripts/seguridad/revisar_logs.sh                 # después del script: busca datos sensibles en el log
```

`pruebas_vivo.py` deja el detalle en `resultado-seguridad.json` y sale con código 1 si algo falla. Sin `uv`: `pip install
requests` y `python3 scripts/seguridad/pruebas_vivo.py`. Resultado del 02/11: 40 de 40 OK y el log sin hallazgos.

## 4. Cada prueba por separado

### 4.1 Pruebas de integración (API, SOAP, BOLA, 401/403/429)

```bash
mvn -B verify -Pintegracion -Dtest=NoHayUnitarios -Dsurefire.failIfNoSpecifiedTests=false \
  -Drabbit.base=https://localhost:8443/Rabbit -Drabbit.soap.base=http://localhost:8080/Rabbit \
  -Drabbit.erp.usuario=demo.erp -Drabbit.erp.clave="$DEMO_PASSWORD" \
  -Drabbit.erp2.usuario=demo.erp2 -Drabbit.erp2.clave="$DEMO_PASSWORD" \
  -Drabbit.comercio.usuario=demo.comercio2 -Drabbit.comercio.clave="$DEMO_PASSWORD" \
  -Drabbit.punto="$PUNTO"
```

51 pruebas (`ApiRestIT`, `SeguridadIT`, `MejorasUltimaMillaIT`). Solo las de seguridad: agregar `-Dit.test=SeguridadIT`.

### 4.2 Logs sin datos personales

Correr primero el script de la sección 3 (carga datos de quien recibe y provoca errores) y después
`scripts/seguridad/revisar_logs.sh`. Busca contraseñas del `.env`, cabeceras `Authorization`, hashes bcrypt, cookies de
sesión, emails, direcciones y las cargas de inyección. Nunca imprime una contraseña: solo cuántas veces aparece.

### 4.3 Seguridad en el navegador (XSS, cookie, sesión vencida)

```bash
cd e2e && npm ci && npx playwright install chromium
BASE=https://localhost:8443/Rabbit npx playwright test tests/seguridad.spec.js
```

SEG-1 (HTML escapado en el portal del comercio), SEG-2 (tooltip del mapa como texto), SEG-3 (cookie y logout), SEG-4
(sesión vencida → login), SEG-5 (código de seguimiento con byte nulo) y SEG-6 (CSRF: sin el token de la vista no se
ejecuta la acción). Con `--headed` se ve el navegador.

### 4.4 ZAP baseline y API activo

En macOS, Docker Desktop no tiene `--network host`: el contenedor de ZAP llega a Rabbit por `host.docker.internal`.

```bash
mkdir -p zap
docker run --rm -v "$PWD/zap:/zap/wrk:rw" -v "$PWD/.zap:/zap/conf:ro" ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t https://host.docker.internal:8443/Rabbit/ -c /zap/conf/reglas.tsv -I -j \
  -r zap.html -J zap.json \
  -z "-config anticsrf.tokens.token(0).name=jakarta.faces.ViewState -config anticsrf.tokens.token(0).enabled=true"
```

```bash
AUTH="Basic $(printf '%s' "demo.erp:$DEMO_PASSWORD" | base64)"
docker run --rm -v "$PWD/zap:/zap/wrk:rw" -v "$PWD/docs/integraciones:/zap/api:ro" -v "$PWD/.zap:/zap/conf:ro" \
  ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py \
  -t /zap/api/openapi.yaml -f openapi -O https://host.docker.internal:8443/Rabbit/api \
  -c /zap/conf/reglas-api.tsv -I -r zap-api.html -J zap-api.json \
  -z "-config replacer.full_list(0).description=auth -config replacer.full_list(0).enabled=true \
      -config replacer.full_list(0).matchtype=REQ_HEADER -config replacer.full_list(0).matchstr=Authorization \
      -config replacer.full_list(0).regex=false -config replacer.full_list(0).replacement=$AUTH"
```

Los reportes quedan en `zap/zap.html` y `zap/zap-api.html` (la carpeta `zap/` no se versiona). Las reglas que cortan
(FAIL) y las aceptadas (WARN) están en `.zap/reglas.tsv` y `.zap/reglas-api.tsv`. En Linux se puede usar `--network host`
y `localhost`, como en el CI.

### 4.5 Dependencias, imagen y secretos

```bash
brew install osv-scanner trivy gitleaks        # o el gestor de paquetes de tu sistema
osv-scanner scan source -r .
docker compose build wildfly && trivy image --severity CRITICAL,HIGH --ignore-unfixed "$(docker compose images -q wildfly)"
gitleaks git --config .gitleaks.toml .         # todo el historial, con las excepciones de .gitleaksignore
```

Dependency-Check necesita una clave del NVD (gratuita, en nvd.nist.gov) y la primera vez baja la base entera (de 15
minutos a varias horas, según cómo esté la API del NVD):

```bash
mvn org.owasp:dependency-check-maven:13.0.0:check -DnvdApiKey=<clave> -DossindexAnalyzerEnabled=false \
  -DskipTestScope=true -DfailBuildOnCVSS=7 -Dformats=HTML,JSON
open target/dependency-check-report.html
```

Al WAR llega una sola dependencia propia (`org.mindrot:jbcrypt`); el resto lo provee WildFly, y Trivy lo revisa en la
imagen.

### 4.6 Cabeceras y TLS a mano

```bash
curl -sk -D - -o /dev/null https://localhost:8443/Rabbit/login.xhtml | grep -iE "set-cookie|strict|content-security|x-frame|x-content|permissions|referrer"
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://localhost:8080/Rabbit/login.xhtml   # 302 a https
curl -sk --tlsv1.1 --tls-max 1.1 -o /dev/null -w "%{http_code}\n" https://localhost:8443/Rabbit/   # 000: rechazado
```

### 4.7 SEC-07: banco caído (manual)

```bash
docker compose exec wildfly /opt/jboss/wildfly/bin/jboss-cli.sh --connect \
  --command="/system-property=rabbit.banco.simular.caida:add(value=true)"
```

Dar de alta un pedido PREPAGO (por la API o "Simular un pedido del ERP") y confirmarlo como `demo.operador` cuatro veces.
Lo esperado, y lo que dio el 02/11: los intentos 1 a 3 cortan a unos 6 s con "El banco no respondió: el pedido no se
confirmó"; el 4.º responde al instante (circuito abierto); el pedido sigue PENDIENTE y
`q "select count(*) from cobros where idpedido=<id>"` da 0. En el log: `[Pagos][Circuito] 3 fallas seguidas del banco:
ABIERTO`. Para volver a la normalidad, el mismo comando con `:remove` en lugar de `:add(value=true)`.

## 5. Para seguir analizando

Temas abiertos o con un costo aceptado, ordenados por lo que más conviene mirar:

| Tema | Situación | Qué se podría probar o mejorar |
|---|---|---|
| BFLA en la web | Un rol que abre una pantalla ajena vuelve a su inicio (302), no recibe 403 | La autorización real está en los EJB (`@RolesAllowed`); probar un POST JSF forzado a una acción del personal con sesión de comercio y confirmar que el EJB la rechaza |
| CSP con `unsafe-inline` | Necesario por los manejadores de `f:ajax` de JSF; por eso el XSS del mapa era ejecutable | Buscar otros lugares donde JavaScript propio use `innerHTML` o APIs de Leaflet con texto de usuarios (`grep -rn "innerHTML\|bindPopup\|bindTooltip" src/main/webapp`) |
| Bloqueo por usuario | 5 fallos bloquean la cuenta 15 min: alguien puede bloquear una cuenta ajena | Medir el impacto; alternativa: bloqueo por usuario + IP o captcha después de N fallos |
| Escrituras públicas por código | Calificación (ADR-052) y opciones de quien recibe (ADR-057) se autorizan con el código de seguimiento | Probar límites (5 cambios, 500 / 200 / 100 caracteres), caracteres de control y HTML en esos campos |
| Código de entrega de 4 dígitos | 10.000 combinaciones, 5 intentos por pedido | Confirmar que el 6.º intento se rechaza aun con el código correcto (`CodigoDeEntregaTest`) |
| Enumeración del seguimiento | Sin límite de consultas; el código tiene unos 50 bits de azar | Medir si hace falta un límite por IP |
| Subida de fotos | Se valida por los primeros bytes (JPEG, PNG, WEBP) y hasta 5 MB | Probar un archivo políglota o un SVG renombrado; se sirve con `nosniff` y el tipo guardado |
| Mensajería | El conector in-vm de Artemis no usa un usuario por servicio | Fuera del alcance del TP; documentado en SEC-08 |
| Dependency-Check | Depende de la API del NVD (503 y lentitud el 02/11) | La corrida semanal o manual arma la base en caché; OSV y Trivy cubren lo mismo en segundos |
| Historial de git | El 29/10 se limpió un secreto del historial; queda pedido a GitHub Support por `refs/pull/1` | Confirmar que GitHub borró esa referencia; gitleaks sobre todo el historial (4.5) |
