# Incidente: credenciales en el historial de Git (27/10/2026)

Registro para el informe de seguridad de la entrega de testing (G6).
Ningún valor secreto aparece en este documento. Los hashes de commit
son los del historial **anterior** a la reescritura (después cambian).

## Resumen

Un escaneo del historial completo encontró dos contraseñas reales que
estuvieron publicadas en el repositorio (que es público). Ninguna estaba
en la versión vigente de los archivos, pero seguían en commits viejos de
todas las ramas. Se rotaron, se reescribió el historial y se sumaron
reglas al escaneo del CI para que no vuelva a pasar.

## Cómo se detectó

| Herramienta | Configuración | Resultado sobre el historial original |
|---|---|---|
| gitleaks 8.30.1 | Reglas por defecto, `--log-opts=--all` | **Sin hallazgos** |
| trufflehog 3.99.7 | `git`, sin verificación en vivo | 3 hallazgos del detector JDBC: URLs de conexión, sin contraseña |
| Búsqueda manual | `git log -p --all` con patrones de contraseñas | La contraseña de la base (en el XML del datasource) |
| gitleaks con reglas propias | `.gitleaks.toml` del repo | Las **dos** contraseñas, en 3 lugares |

Lección: las reglas por defecto de gitleaks no reconocen una contraseña en
un atributo `password="..."` de XML ni en una tabla de Markdown, y
trufflehog marcó la URL de la base pero no la contraseña que estaba al
lado. La segunda contraseña apareció recién con la regla propia para
tablas de Markdown.

La verificación en vivo de trufflehog se desactivó a propósito
(`--no-verification`): habría intentado conectarse a la base compartida
con la contraseña filtrada.

## Hallazgos

| # | Qué | Dónde | Desde → hasta | Estado al detectarlo |
|---|---|---|---|---|
| S1 | Contraseña de la base PostgreSQL (Supabase), con el usuario que incluye el identificador del proyecto y el host del pooler | `README.md`: tabla de conexión y XML del datasource | `d04574e` (25/08) → borrada en `f572dfe` (09/09) | Ya no era la vigente: la contraseña actual es otra |
| S2 | Contraseña del usuario `admin` de la consola de management de WildFly (puerto 9990) | `pom.xml` (configuración del `wildfly-maven-plugin`) y `README.md` | `26c1492` (11/08) → sacada en `125cc77` (28/09) | **Vigente** en al menos una máquina del equipo |

Además se reemplazaron, aunque no son secretos, el identificador del
proyecto de Supabase y las URLs JDBC de ejemplo, para que los escáneres
no reporten falsos positivos.

Impacto:

- **S1:** con usuario, host y contraseña juntos se podía entrar a la base
  compartida desde afuera, mientras esa contraseña estuvo vigente.
- **S2:** el management de WildFly escucha solo en `127.0.0.1`, así que
  hacía falta acceso a la máquina. Igual es una credencial de
  administración que estuvo publicada.

## Qué se hizo

1. **Rotación.** Se cambió la contraseña de la base en Supabase y la de
   management en cada WildFly del equipo, junto con su copia en
   `~/.m2/settings.xml`. Decidido por el equipo el 27/10. PENDIENTE:
   confirmar fecha de cada rotación y responsable.
2. **Reescritura del historial** con `git filter-repo --replace-text` y
   `--replace-message`, sobre todas las ramas. Cada valor se reemplazó
   por un marcador (`<CONTRASEÑA>`, `<REF-PROYECTO>`,
   `<CONTRASEÑA-MANAGEMENT>`, y en las URLs de conexión, `<HOST>` y `<BASE>`).
   Antes se ensayó sobre una copia espejo (27/10). Se corrió el 29/10,
   después de llevar `main` por fast-forward a la rama de trabajo (sin
   PR, para no crear otra `refs/pull` con la historia vieja). Antes se
   tomó un respaldo completo (bundle), que se borró después del push.
   La rama local `feature/mejoras-trello`, que ya no estaba en GitHub,
   se borró sin pushear. `desafio-heterogeneidad` se dejó en la versión
   reescrita de lo que había en GitHub: no se subieron 3 commits locales
   que solo traían un merge viejo de `main`.
3. **Verificación** antes del push, sobre todas las refs locales
   (180 commits). El contenido de `main` quedó igual salvo los
   marcadores, con los mismos 178 commits.
   - gitleaks con reglas por defecto: 0 hallazgos;
   - gitleaks con reglas propias: 0;
   - trufflehog: 0 (4.192 fragmentos);
   - búsqueda literal de cada valor: 0 commits.
4. **Force push** el 29/10, con el OK del equipo, de `main`,
   `feature/retiro-almacenamiento-y-state` y `desafio-heterogeneidad`.
   Se usó `--force-with-lease` con el hash anterior de cada rama, para
   no pisar un push ajeno.

   | Rama | Antes | Después |
   |---|---|---|
   | `main` | `70e3e30` | `a176580` |
   | `feature/retiro-almacenamiento-y-state` | `70e3e30` | `a176580` |
   | `desafio-heterogeneidad` | `9411d4f` | `b76d5ad` |

   Escaneo de un clon nuevo bajado de GitHub, solo las ramas
   (179 commits): gitleaks por defecto 0, gitleaks con reglas propias 0,
   trufflehog 0, búsqueda literal 0.
5. **Re-clonado.** Cada integrante borra su copia y vuelve a clonar. No
   se mergea ninguna rama vieja, porque volvería a meter el historial
   anterior.
6. **Prevención en el CI.** gitleaks corre en cada push y en cada PR con
   `.gitleaks.toml`, que suma tres reglas a las de siempre:
   - `password="..."` en XML;
   - `--password=` y `add-user.sh -p`;
   - un valor junto a "Contraseña" en tablas de Markdown.

   Los marcadores de la documentación (`<contraseña>`, `CONTRASEÑA`,
   `...`) están permitidos.

## Riesgo residual

- **Commits viejos en GitHub (comprobado el 29/10).** El force push no
  toca las `refs/pull/1/head` a `refs/pull/10/head`, que GitHub mantiene
  para cada PR y que nadie puede borrar con un push. Todas contienen la
  historia vieja. `trufflehog git https://github.com/feliforte14/Rabbit.git`
  las incluye y reporta 3 hallazgos sin verificar (detector JDBC, commits
  `d04574e` y `bb9d65a`). En esos commits siguen estando las
  contraseñas, aunque trufflehog no las reconoce. Las ramas están
  limpias. Salidas posibles:
  - pedir a GitHub Support que borre esas referencias y la caché
    (procedimiento "Removing sensitive data from a repository");
  - recrear el repositorio, con lo que se pierden las PR y el historial
    de issues.

  Mientras tanto, la protección real es la **rotación**.
- **Clones y forks anteriores.** Cualquier clon o fork hecho antes del
  27/10 conserva el historial viejo.
