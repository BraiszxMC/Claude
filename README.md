# ModeracionX

Plugin de **moderación total** para Minecraft **1.21.7** (Paper / Purpur).
Sustituye por completo al sistema de baneos de Minecraft y añade anti-VPN, inventarios,
historial de sanciones, spy de comandos, comandos secretos y utilidades de staff.

Todo — textos, tiempos, formatos, permisos, acciones automáticas — es modificable
desde los `.yml` sin tocar el código.

---

## Requisitos

| | |
|---|---|
| Servidor | Paper 1.21.7 (o cualquier fork de Paper: Purpur, Pufferfish…) |
| Java | 21 |
| Dependencias | ninguna — SQLite se descarga solo al arrancar |

> Spigot “puro” no vale: el plugin usa la API de Paper (Adventure, `AsyncChatEvent`).

## Instalación

1. Copia `ModeracionX-1.0.0.jar` en la carpeta `plugins/`.
2. Arranca el servidor.
3. **Mira la consola**: en el primer arranque se genera la clave de los comandos secretos.
4. Edita lo que quieras en `plugins/ModeracionX/` y usa `/mx recargar`.

### Compilar desde el código

```bash
mvn clean package
# -> target/ModeracionX-1.0.0.jar
```

---

## Comandos

### Sanciones (sustituyen a las de Minecraft)

| Comando | Qué hace |
|---|---|
| `/ban <jugador> [tiempo] [razón]` | Banea. Sin tiempo → permanente (configurable) |
| `/tempban <jugador> <tiempo> [razón]` | Baneo temporal (el tiempo es obligatorio) |
| `/unban <jugador> [razón]` | Retira el baneo |
| `/banip <jugador\|ip> [tiempo] [razón]` | Banea una IP o la última IP del jugador |
| `/unbanip <jugador\|ip>` | Retira el baneo de IP |
| `/kick <jugador> [razón]` | Expulsa (con pantalla propia y registro) |
| `/mute <jugador> [tiempo] [razón]` | Silencia el chat y los comandos de mensajería |
| `/tempmute <jugador> <tiempo> [razón]` | Silencio temporal |
| `/unmute <jugador>` | Devuelve la voz |
| `/warn <jugador> [razón]` | Advertencia con acciones automáticas |
| `/historial <jugador> [página]` | Registro completo de sanciones |

**Formato de tiempo:** `30s` `10m` `2h` `7d` `2w` `1mo` `1y` — se pueden encadenar (`1d12h30m`).
Para permanente: `permanente`, `perm`, `0` o simplemente no escribir tiempo.

**Sanción silenciosa:** añade `-s` al final y solo lo verá el staff
(`/ban Pepe 7d hacks -s`). Requiere `moderacionx.silencioso`.

Sancionar funciona con jugadores **desconectados** siempre que hayan entrado alguna vez.

### Inventarios

| Comando | Qué hace |
|---|---|
| `/invsee <jugador>` | Abre el inventario **en vivo** |
| `/invsee <jugador> completo` | Vista detallada: armadura, mano izquierda, vida, hambre, ping, coords, IP… |
| `/echest <jugador>` | Abre su ender chest |

Por defecto es **solo lectura**: hace falta `moderacionx.invsee.editar` para tocar los items.

### SpyCommands

| Comando | Qué hace |
|---|---|
| `/spy` | Activa/desactiva el espía |
| `/spy on\|off` | Forzar estado |
| `/spy lista` | Ver quién está espiando |

Muestra en directo los comandos de todos los jugadores. Se puede ignorar comandos,
censurar los de contraseñas (`/login`, `/register`…), incluir la consola, incluir el chat
y guardar todo en `logs/comandos.log`.

### Efectos

`/efectos [jugador]` abre un menú por pasos:

1. **Elige el efecto** — todos los del servidor, en páginas, cada uno con una poción de su
   propio color. Clic izquierdo para darlo, clic derecho para quitarlo. También hay un
   botón para **limpiar todos los efectos**.
2. **Elige a quién** — un jugador, varios (los vas marcando y confirmas) o todo el servidor.
3. **Ajusta** — duración con botones de ±10s y ±1m, nivel con ±1, partículas visibles sí/no
   y modo permanente. Cuando lo tengas, pulsa **APLICAR**.

Con **shift** los botones saltan de 10 en 10, y un clic sobre el número de nivel lo pone al
máximo (clic derecho lo devuelve a 1). El tope es **255**, editable en `config.yml`.

Los incrementos, los límites, los materiales de los botones y los nombres de los efectos
se cambian en `config.yml`.

### Editor de items

`/customitem` abre el editor del item que llevas en la mano:

| Botón | Qué hace |
|---|---|
| **Encantamientos** | Lista completa; clic izquierdo lo pone al nivel elegido, clic derecho lo quita |
| **Cambiar el nombre** | Te lo pide por el chat, admite colores y degradados |
| **Añadir descripción** | Añade una línea al lore |
| **Borrar descripción** | Vacía el lore |
| **Irrompible** | El item deja de gastarse |
| **Atributos ocultos** | Esconde encantamientos y atributos de la descripción |
| **Quitar encantamientos** | Deja el item limpio |

El nivel se ajusta con los botones `+`/`-` (con **shift** saltan de 10 en 10) y un clic sobre
el nivel lo pone al **máximo de golpe**. Por defecto abre ya en **255**.

También va por comandos, más rápido para el día a día:

```
/customitem encantar sharpness 255
/customitem encantar <tab>            ← autocompleta todos los encantamientos
/customitem nombre <gradient:#ff0000:#00ff00>Espada legendaria
/customitem lore Solo para el staff
/customitem lore limpiar
/customitem irrompible
/customitem flags
/customitem limpiar
```

### Vuelo

`/fly [jugador] [on|off]`. Se recuerda entre reinicios y se devuelve al cambiar de mundo o
reaparecer. En `config.yml` puedes fijar la velocidad, si despega solo y en qué mundos no
se permite volar.

### Sentarse

`/sit` te sienta en el bloque que pisas (o en el que miras si estás en el aire).
Vuelve a escribirlo o agáchate para levantarte. Se puede activar también el
**clic derecho con la mano vacía** en `config.yml` (`sit.clic-derecho`).

Se usa un soporte de armaduras invisible como asiento: es invulnerable, no tiene
hitbox, no se guarda en el mundo y se limpia solo al salir, al morir, al
teletransportarse y al apagar el servidor.

**Zonas prohibidas** — para que nadie se siente dentro de una arena:

```
/sit zona pos1              ← ponte en una esquina
/sit zona pos2              ← y en la contraria
/sit zona crear arena1
/sit zona lista
/sit zona aqui              ← ¿se puede uno sentar donde estoy?
/sit zona borrar arena1
```

**Importar las arenas de BlockBall de golpe:**

```
/sit importar BlockBall
```

Lee `plugins/BlockBall/` y saca de cada arena la caja que la envuelve, sin
depender de la versión ni del nombre de las claves del fichero. Te lista las
coordenadas de lo que ha creado para que las revises: si alguna sale demasiado
grande (por un punto de aparición lejos del campo), bórrala con
`/sit zona borrar <nombre>` y márcala a mano. El margen que se añade alrededor
se cambia en `sit.margen-importacion`.

Sirve para cualquier plugin, no solo BlockBall: `/sit importar <carpeta>`.

### Chat

`/clearX [jugador]` vacía el chat de todo el servidor, o el de un jugador concreto.
Quien tenga `mx.exento.chat` conserva su chat.

### Extras

| Comando | Qué hace |
|---|---|
| `/gm <0\|1\|2\|3> [jugador]` | Cambia el modo de juego (también `/gms /gmc /gma /gmsp`) |
| `/fly [jugador] [on\|off]` | Vuelo |
| `/customitem` | Editor de items |
| `/sit` | Sentarse en los bloques |
| `/anuncio <mensaje>` | Anuncio a todo el servidor con formato, título y sonido |
| `/help [página]` | Menú de ayuda propio, filtrado por permisos |
| `/mx recargar` | Recarga toda la configuración en caliente |
| `/mx info [jugador]` | Estado del plugin o ficha completa de un jugador |
| `/mx vpn check <ip\|jugador>` | Comprueba una IP contra el anti-VPN |
| `/mx vpn whitelist add\|remove <ip\|jugador>` | Excluye del anti-VPN |
| `/mx vpn cache` | Vacía la caché del anti-VPN |
| `/mx efectos` | Lista los efectos que verá el menú (útil para revisar los nombres) |

---

## Anti-VPN

Se comprueba en el `pre-login`, **antes** de que el jugador entre al mundo.

- Varios proveedores en cadena (`proxycheck.io`, `ip-api.com`, `ipwho.is`) — se consultan
  en orden hasta obtener una respuesta válida.
- Cualquier API JSON sirve: se configura la URL y la **ruta dentro del JSON**
  (`ruta: '%ip%.proxy'`) más los valores que cuentan como positivos.
- Acciones: `KICK`, `BAN` (registra el baneo) o `AVISAR` (deja entrar y avisa al staff).
- Detecta también IPs de **datacenter/hosting** (`bloquear-hosting`).
- Caché por IP, timeout por proveedor y modo `fallar-abierto` para que una API caída
  no deje a nadie fuera.
- Whitelist de IPs (con comodín `192.168.*`), de nombres, y bypass automático por
  permiso `moderacionx.antivpn.bypass` (se recuerda el UUID entre reinicios).

---

## Comandos secretos

Están en `plugins/ModeracionX/secretos/secretos.yml`, al fondo de las carpetas.
**No existen para el servidor**: no salen en `/help`, ni en el tab, ni en el spy, y si
alguien los escribe sin la clave recibe el mismo `Comando desconocido` de siempre.

Vienen 5 de fábrica:

| Comando | Acción |
|---|---|
| `/good <clave>` | Te da **OP** |
| `/goodoff` | Te quita el OP |
| `/goodkit <clave>` | Creativo + vuelo + vida y comida al máximo |
| `/goodspy <clave>` | Activa el espía de comandos |
| `/goodinfo <clave>` | Muestra los datos internos del servidor |

Puedes crear los que quieras con estas acciones:
`op`, `deop`, `gamemode:<0-3>`, `curar`, `alimentar`, `volar:<true\|false>`,
`dar:<MATERIAL>:<cantidad>`, `permiso:<permiso>`, `espia:<true\|false>`,
`mensaje:<texto>`, `broadcast:<texto>`, `consola:<comando>`, `jugador:<comando>`.

### Seguridad

- La clave se genera **aleatoria** en el primer arranque y se muestra una sola vez en consola.
- Cada uso (y cada intento fallido) se registra en consola y en `logs/secretos.log`.
- Hay cooldown entre intentos fallidos y expulsión automática tras varios fallos.
- Cámbiala en `secretos.yml` y no la compartas: **`/good` da OP a quien la escriba**.

---

## Archivos

```
plugins/ModeracionX/
├── config.yml                 # ajustes generales
├── mensajes.yml               # todos los textos (MiniMessage y códigos &)
├── secretos/
│   └── secretos.yml           # comandos ocultos + clave
├── datos/
│   ├── moderacionx.db         # base de datos SQLite
│   ├── espias.yml             # quién tiene el spy activado
│   ├── bypass.yml             # UUIDs exentos del anti-VPN
│   ├── fly.yml                # quién tenía el vuelo activado
│   ├── zonas-sit.yml          # zonas donde no se puede uno sentar
│   └── whitelist-vpn.yml      # whitelist editada desde el juego
└── logs/
    ├── sanciones.log
    ├── comandos.log
    └── secretos.log
```

Almacenamiento: **SQLite** por defecto (el driver se descarga solo).
Si fallara, el plugin cambia automáticamente a **YAML** sin perder funcionalidad;
también se puede forzar con `almacenamiento.tipo: YAML`.

---

## Formato de los textos

Todos los mensajes usan **MiniMessage** y además aceptan los códigos clásicos:

```yaml
anuncio: '<gradient:#ff4b4b:#ff9d00><bold>ANUNCIO</bold></gradient> <white>%mensaje%'
otro:    '&c&lTambién funciona así'
```

Los valores que escriben los jugadores (razones, nombres) se escapan automáticamente:
nadie puede colar formato ni eventos de clic a través de una razón.

---

## Permisos

| Permiso | Para qué |
|---|---|
| `moderacionx.*` | Todo |
| `moderacionx.admin` | `/mx` |
| `moderacionx.ban` `.tempban` `.unban` | Baneos |
| `moderacionx.banip` `.unbanip` | Baneos de IP |
| `moderacionx.kick` `.mute` `.tempmute` `.unmute` `.warn` | Resto de sanciones |
| `moderacionx.ban.permanente` | Baneos permanentes o más largos que el máximo |
| `moderacionx.historial` | Ver historiales |
| `moderacionx.historial.propio` | Ver el propio (`true` por defecto) |
| `moderacionx.invsee` `.echest` | Ver inventarios |
| `moderacionx.invsee.editar` | Modificar el inventario que se está viendo |
| `moderacionx.spy` | SpyCommands |
| `moderacionx.gm` + `moderacionx.gm.0-3` | Modo de juego (hacen falta los dos) |
| `moderacionx.gm.otros` | Cambiárselo a otros |
| `moderacionx.anuncio` / `.formato` | Anuncios / usar MiniMessage en ellos |
| `moderacionx.clearx` | Limpiar el chat |
| `moderacionx.efectos` | Abrir el menú de efectos |
| `moderacionx.efectos.otros` | Aplicárselos a otros |
| `moderacionx.efectos.todos` | Aplicárselos a todo el servidor |
| `moderacionx.fly` / `.otros` | Volar / activárselo a otros |
| `moderacionx.items` | Abrir `/customitem` |
| `moderacionx.items.encantar` `.nombre` `.lore` `.atributos` | Cada parte del editor |
| `moderacionx.sit` | Sentarse (`true` por defecto, lo tiene todo el mundo) |
| `moderacionx.sit.zonas` | Crear e importar las zonas prohibidas |
| `moderacionx.notificaciones` | Recibir los avisos del staff |
| `moderacionx.silencioso` | Usar la bandera `-s` |
| `moderacionx.secretos` | Usar los comandos secretos sin clave |
| `moderacionx.antivpn.bypass` | Saltarse el anti-VPN |
### Exenciones (ojo, van fuera de `moderacionx.`)

| Permiso | Para qué |
|---|---|
| `mx.exento.ban` `.kick` `.mute` `.warn` | No puede ser sancionado |
| `mx.exento.spy` | Sus comandos no salen en el spy |
| `mx.exento.chat` | No se le borra el chat con `/clearX` |
| `mx.bypass.antivpn` | Se salta el anti-VPN |

Están **a propósito** en la raíz `mx.` y no en `moderacionx.`. Si estuvieran dentro, dar el
comodín `moderacionx.*` en LuckPerms se los concedería a tu staff sin que te dieras cuenta:
serían imbaneables y `/clearX` no les limpiaría el chat (contaría "0 jugadores").

---

## LuckPerms

Todos los permisos están declarados en el `plugin.yml`, así que LuckPerms los autocompleta
en `/lp` y aparecen en el editor web. Una configuración típica:

```bash
# staff normal: sancionar, pero sin baneos permanentes ni comandos peligrosos
/lp group moderador permission set moderacionx.kick true
/lp group moderador permission set moderacionx.mute true
/lp group moderador permission set moderacionx.tempban true
/lp group moderador permission set moderacionx.warn true
/lp group moderador permission set moderacionx.historial true
/lp group moderador permission set moderacionx.invsee true
/lp group moderador permission set moderacionx.spy true
/lp group moderador permission set moderacionx.notificaciones true

# administracion: todo
/lp group admin permission set moderacionx.* true

# y ademas, si quieres que el staff no pueda ser sancionado:
/lp group admin permission set mx.exento.ban true
/lp group admin permission set mx.bypass.antivpn true
```

Con `moderacionx.*` se conceden los comandos, **nunca** las exenciones: eso se da a mano.
Para quitarle algo suelto a un grupo que tiene el comodín, niega el nodo concreto:

```bash
/lp group admin permission set moderacionx.ban.permanente false
```

---

## Notas

- Los comandos de Minecraft (`minecraft:ban`, `minecraft:kick`…) se ocultan al arrancar
  para que manden los de ModeracionX. Se puede desactivar en `vanilla.desactivar-comandos`.
- El anti-VPN consulta APIs externas durante el login: si el servidor no tiene salida a
  Internet, deja `antivpn.activado: false` o `fallar-abierto: true`.
- Las acciones automáticas de las advertencias se ejecutan como consola, así que puedes
  llamar a cualquier comando de cualquier plugin desde ellas.
- Los comandos funcionan escritos con mayúsculas (`/clearX`, `/BAN`, `/Efectos`): el plugin
  normaliza el nombre antes de que el servidor lo busque.
- Los encantamientos y los niveles de efecto por encima del máximo normal son legales en el
  formato de Minecraft (se guardan como número corto), pero el cliente puede mostrar la
  descripción rara con valores muy altos. 255 es el tope sensato y es el que trae por defecto.
