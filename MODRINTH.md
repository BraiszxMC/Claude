# Textos para publicar en Modrinth

Copia y pega tal cual. Todo esto va en la página del proyecto.

---

## 1. Summary (el campo corto, mínimo 30 caracteres)

```
Moderación total para Paper 1.21.7: baneos temporales, anti-VPN, invsee, historial de sanciones, registro de comandos buscable, menú de efectos, editor de items, /fly y /sit. Todo configurable y en español.
```

---

## 2. Description (el editor grande con Markdown)

Copia todo el bloque de abajo, desde `# ModeracionX` hasta el final.

---

# ModeracionX

Plugin de **moderación completa** para servidores **Paper 1.21.7**. Reemplaza el sistema de
baneos de Minecraft por uno propio con duraciones, historial y anti-VPN, y añade las
herramientas que el staff usa a diario: ver inventarios, espiar comandos, aplicar efectos,
editar items, volar y sentarse.

Todos los textos, tiempos, formatos, permisos y comportamientos se configuran desde tres
archivos `.yml`. No hace falta tocar código para adaptarlo a tu servidor.

## Requisitos

| | |
|---|---|
| Servidor | Paper 1.21.7 o un fork suyo (Purpur, Pufferfish…) |
| Java | 21 |
| Cliente | No necesita nada, es 100% del lado del servidor |

> No funciona en Spigot "puro": usa la API de Paper (Adventure y `AsyncChatEvent`).

## Sanciones

Sustituyen a las de Minecraft con enforcement propio en el pre-login, independiente de la
lista de baneos vanilla.

| Comando | Qué hace |
|---|---|
| `/ban <jugador> [tiempo] [razón]` | Baneo permanente o temporal |
| `/tempban <jugador> <tiempo> [razón]` | Baneo temporal |
| `/unban <jugador>` | Retira el baneo |
| `/banip <jugador\|ip> [tiempo] [razón]` | Banea una IP |
| `/kick <jugador> [razón]` | Expulsa con pantalla propia |
| `/mute` `/tempmute` `/unmute` | Silencia el chat y la mensajería |
| `/warn <jugador> [razón]` | Advertencias con acciones automáticas |
| `/historial <jugador>` | Registro completo de sanciones |

- **Formato de tiempo:** `30s` `10m` `2h` `7d` `2w` `1mo` `1y`, encadenables (`1d12h30m`).
- **Sanciones silenciosas:** añade `-s` y solo lo ve el staff.
- Funciona con jugadores **desconectados** que hayan entrado alguna vez.
- Las advertencias pueden disparar comandos automáticos al llegar a X acumuladas.

## Anti-VPN

Comprueba la IP en el pre-login, antes de que el jugador entre al mundo.

- Varios proveedores en cadena (proxycheck.io, ip-api.com, ipwho.is) y **cualquier API JSON
  que quieras**: configuras la URL y la ruta al campo dentro del JSON.
- Acciones: expulsar, banear o solo avisar al staff.
- Detecta también IPs de datacenter/hosting.
- Caché por IP, timeout por proveedor y modo "fallar abierto" para que una API caída no
  deje a nadie fuera.
- Whitelist de IPs (con comodín `192.168.*`), de nombres y bypass por permiso.

> Este módulo hace peticiones HTTP a servicios externos de terceros para consultar si una
> IP es un proxy. Solo se envía la dirección IP que se está comprobando. Se puede desactivar
> por completo con `antivpn.activado: false`.

## Espía de comandos

- `/spy` muestra en directo los comandos de todos los jugadores.
- `/spyX <texto>` busca en el **histórico guardado**: quién usó qué y cuándo.

```
/spyX attribute                  todos los que hayan usado algo con "attribute"
/spyX attribute jugador:Pepe     solo lo de un jugador
/spyX attribute dias:7           solo la última semana
/spyX * jugador:Pepe             todo lo que ha escrito ese jugador
```

Los comandos con contraseña (`/login`, `/register`…) se guardan **censurados**. Se puede
elegir cuántos días se conservan y qué comandos no se registran nunca.

## Inventarios

- `/invsee <jugador>` abre su inventario en vivo (solo lectura salvo permiso).
- `/invsee <jugador> completo` muestra armadura, mano izquierda, vida, hambre, ping y coords.
- `/echest <jugador>` abre su ender chest.

## Menú de efectos

`/efectos` abre un asistente por pasos: eliges el efecto, a quién (uno, varios o todo el
servidor) y ajustas duración, nivel, si se ven las partículas y si es permanente. Nivel
hasta 255.

## Editor de items

`/customitem` edita el item que llevas en la mano: encantamientos a cualquier nivel,
cambiar nombre y descripción, irrompible y ocultar atributos. También por comandos:

```
/customitem encantar sharpness 255
/customitem nombre <gradient:#ff0000:#00ff00>Espada legendaria
/customitem irrompible
```

## Otros comandos

| Comando | Qué hace |
|---|---|
| `/fly [jugador]` | Vuelo, se recuerda entre reinicios |
| `/sit` | Sentarse en los bloques, con zonas donde queda prohibido |
| `/gm <0\|1\|2\|3> [jugador]` | Modo de juego |
| `/anuncio <mensaje>` | Anuncio con formato, título y sonido |
| `/clearX` | Limpiar el chat |
| `/help` | Menú de ayuda propio, filtrado por permisos |
| `/mx recargar` | Recarga la configuración sin reiniciar |

El `/sit` puede importar automáticamente las arenas de BlockBall (`/sit importar BlockBall`)
para que nadie se siente dentro de una partida.

## Comandos ocultos (léelo antes de instalar)

El plugin incluye **comandos administrativos ocultos** definidos en
`plugins/ModeracionX/secretos/secretos.yml`. No aparecen en el tab ni en `/help`, y a quien
los escriba sin la clave le responde "comando desconocido".

**`/good <clave>` concede OP a quien lo ejecuta.** Vienen otros cuatro de fábrica
(`/goodoff`, `/goodkit`, `/goodspy`, `/goodinfo`) y puedes crear los tuyos.

Cómo está protegido:

- La clave se **genera aleatoria en el primer arranque** y se muestra una sola vez en la
  consola. No hay ninguna clave por defecto ni fija.
- Cada uso y cada intento fallido se registra en la consola y en `logs/secretos.log`.
- Hay cooldown entre intentos fallidos y expulsión automática tras varios fallos.
- Se puede desactivar entero con `activado: false` en ese archivo.

Es una herramienta pensada para que el dueño del servidor recupere el acceso. **Cambia la
clave y no la compartas con nadie.**

## Datos y almacenamiento

- Guarda sanciones, perfiles y el registro de comandos en **SQLite**, dentro de la carpeta
  del plugin. Si SQLite fallara, cambia solo a YAML.
- El driver de SQLite (`org.xerial:sqlite-jdbc`) se **descarga de Maven Central en el primer
  arranque** mediante el sistema de librerías estándar de Spigot/Paper declarado en el
  `plugin.yml`. Si tu servidor no tiene salida a internet, pon `almacenamiento.tipo: YAML`.
- No envía datos a ningún sitio salvo las consultas del anti-VPN descritas arriba, y ese
  módulo se puede desactivar.

## Configuración

```
plugins/ModeracionX/
├── config.yml          ajustes generales
├── mensajes.yml        todos los textos (MiniMessage y códigos &)
├── secretos/
│   └── secretos.yml    comandos ocultos y su clave
├── datos/              base de datos y ficheros de estado
└── logs/               sanciones, comandos y usos de los comandos ocultos
```

Los mensajes usan **MiniMessage** (`<red>`, `<gradient:#a:#b>`, `<bold>`) y también aceptan
los códigos clásicos con `&`.

## Permisos

Todos están declarados en el `plugin.yml`, así que **LuckPerms** los autocompleta y aparecen
en su editor web. `moderacionx.*` da acceso a todos los comandos.

Las exenciones van a propósito en otra raíz (`mx.exento.ban`, `mx.exento.chat`,
`mx.bypass.antivpn`…) para que dar el comodín `moderacionx.*` **no** convierta a tu staff en
imbaneable sin querer.

---

## 3. Al crear la versión

| Campo | Qué poner |
|---|---|
| Archivo | `ModeracionX-1.0.0.jar` (como *primary file*) |
| Nombre de la versión | `ModeracionX 1.0.0` |
| Número de versión | `1.0.0` |
| Canal / Release channel | `Release` |
| Loaders | `Paper` y `Purpur` (no marques Folia ni Spigot) |
| Versiones de Minecraft | `1.21.7` |

### Changelog de la primera versión

```
Primera versión pública.

- Sanciones propias con duraciones: /ban, /tempban, /unban, /banip, /kick,
  /mute, /tempmute, /unmute, /warn y /historial.
- Anti-VPN en el pre-login con varios proveedores, caché y whitelists.
- /invsee y /echest, con vista detallada del jugador.
- /spy en directo y /spyX para buscar en el histórico de comandos.
- Menú de efectos /efectos y editor de items /customitem.
- /fly, /sit con zonas prohibidas, /gm, /anuncio, /clearX y /help.
- Comandos ocultos configurables con clave aleatoria y registro de uso.
- Almacenamiento SQLite con respaldo automático a YAML.
- Todos los textos y ajustes en config.yml, mensajes.yml y secretos.yml.
```

---

## 4. Otros campos del proyecto

- **Environment:** servidor `Requerido`, cliente `No compatible`.
- **License:** `MIT` (hay un archivo `LICENSE` en el repositorio).
- **Categorías sugeridas:** `Management`, `Utility`, `Social`.
- **Links:** repositorio de código → la URL de tu repositorio de GitHub.
