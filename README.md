# BlockBallRanked

Sistema de **partidas rankeds con Elo** montado encima del plugin
[BlockBall](https://github.com/Shynixn/BlockBall) de Shynixn.

* Minecraft **1.21.7** (Paper / Spigot), Java 21
* Desarrollado contra **BlockBall 7.43.0**

---

## Que hace

| | |
|---|---|
| **Cola de emparejamiento** | por modo (1v1, 2v2, 3v3...), con ventana de Elo que se ensancha segun el tiempo de espera |
| **Equilibrado de equipos** | reparte a los jugadores en rojo/azul minimizando la diferencia de Elo |
| **Elo por equipos** | formula clasica de Elo con K dinamico, partidas de colocacion y bonus por diferencia de goles |
| **Divisiones** | Hierro -> Gran Maestro, configurables por umbral de Elo |
| **Anti-abandono** | el que se va pierde la partida y Elo extra, sus companeros quedan protegidos |
| **Persistencia** | SQLite (por defecto) o MySQL, con historial de partidas |
| **Ranking** | `/ranked top` con cache y placeholders de top N |
| **PlaceholderAPI** | `%bbranked_elo%`, `%bbranked_rank%`, `%bbranked_position%`... |
| **Comparativa al emparejar** | al formarse la partida, cada jugador ve el Elo/rango de su(s) rival(es) y el suyo debajo |
| **MVP** | en modos por equipos, el maximo goleador se lleva Elo extra |
| **Anti-multicuentas** | dos jugadores con la misma IP nunca coinciden en la misma partida |
| **Baneos** | por jugador o por IP, temporales o permanentes, con persistencia |
| **Parties** | hasta 3 amigos con Elo parecido entran juntos a la cola y juegan en el mismo equipo |
| **Barra de accion** | mientras esperas te muestra el modo, cuanta gente falta y el tiempo |
| **Sonidos** | cada evento (cola, gol, victoria, MVP, subir de rango...) con su sonido configurable |
| **Confirmacion de partida** | boton clicable en el chat antes de teleportar, para que nadie entre AFK |
| **Temporadas** | cierre con premios, ranking historico y reset suave del Elo |
| **Historial** | `/ranked history` con tus ultimas partidas y el Elo de cada una |
| **Menu GUI** | `/ranked menu` para entrar a la cola y ver tus datos sin comandos |
| **Anti-boosting** | ganar muchas veces al mismo rival da cada vez menos Elo |
| **Decay** | quien no juega baja poco a poco, para que la cima no se congele |
| **Discord** | avisos de resultados, ascensos y fin de temporada por webhook |

---

## Como esta hecho por dentro

BlockBall expone una API que **no esta en Maven Central**: hay que referenciar
su jar directamente. Lo que usa este plugin:

### Servicio

```java
// BlockBall registra sus servicios en el ServicesManager de Bukkit
GameService gameService = Bukkit.getServicesManager()
        .getRegistration(GameService.class).getProvider();

SoccerGame game = gameService.getByName("ranked_2v2_1");
JoinResult result = game.join(player, Team.RED);
```

`SoccerGame` da acceso a `getStatus()`, `getRedScore()`, `getBlueScore()`,
`getRedTeam()`, `getBlueTeam()`, `getPlayers()`, `isDisposed()` y `close()`.

### Eventos

Paquete `com.github.shynixn.blockball.event`:

| Evento | Uso aqui |
|---|---|
| `GameJoinEvent` | bloquear entradas manuales a las arenas ranked |
| `GameStartEvent` | marcar la partida como empezada de verdad |
| `GameGoalEvent` | contar goles por jugador (`getPlayer()`, `getTeam()`) |
| `GameEndEvent` | recoger el ganador (`getWinningTeam()`) |
| `GameLeaveEvent` | detectar abandonos |

**Tres detalles importantes de la API que condicionan el diseño:**

1. `GameEndEvent` **no se dispara en los empates**. BlockBall solo lo lanza
   desde `onWin(team)`; cuando se acaba el tiempo con marcador igualado llama
   a `onDraw()`, que no dispara nada. Por eso `MatchManager` lleva un
   **monitor propio** que corre cada segundo: cachea el marcador y, si la
   instancia de la partida se marca como `isDisposed()` sin haber recibido
   `GameEndEvent`, deduce el resultado del marcador guardado.

   Esto tiene una consecuencia menos obvia: `SoccerGameImpl.close()` pone
   `isDisposed = true` y **despues** llama a `leave()` por cada jugador, lo
   que dispara un `GameLeaveEvent` por cabeza. En una victoria da igual,
   porque `GameEndEvent` llega ~3s antes y para entonces el monitor ya ha
   liquidado la partida. Pero en un empate no llega ningun evento, asi que
   esas salidas parecian abandonos y sancionaban a los dos equipos. El
   listener comprueba `game.isDisposed()` para distinguir "la partida se esta
   cerrando" de "alguien se ha ido".

   Y al reves: cuando la partida acaba **por abandono**, BlockBall no se entera
   de nada (ni se ha llegado al marcador maximo ni se ha acabado el tiempo).
   El plugin aplica el Elo pero la arena se queda en marcha con los que
   quedan jugando solos, asi que hay que llamar a `close()` a mano. Por eso
   `RankedMatch` lleva una marca `forfeited`: en un final normal no se toca la
   arena, que ya se cierra sola con sus mensajes de victoria.
2. Cuando una partida termina, BlockBall la **cierra y crea una instancia
   nueva** para la misma arena (`GameServiceImpl.runGames()` llama a
   `reload(arena)` en cuanto ve `isDisposed`). Por eso el plugin guarda la
   *referencia* al `SoccerGame` en vez de buscarlo por nombre cada tick: si no,
   leeria el marcador 0-0 de la partida recien reiniciada.
3. `GameJoinEvent`, `GameLeaveEvent` y el resto de eventos de partida heredan
   de `BlockBallEvent(isAsync = true)`: BlockBall los marca como asincronos a
   proposito. Bukkit **prohibe por contrato** disparar un evento asincrono
   desde el hilo principal (lanza `IllegalStateException: ... may only be
   triggered asynchronously`), asi que `game.join()`, `game.leave()` y
   `game.close()` **no se pueden llamar desde el hilo principal**. El
   emparejamiento y el tick del servidor si corren en el hilo principal, asi
   que `MatchManager.startMatch()` reserva la arena de forma sincrona pero
   despacha la llamada real a `game.join()` con
   `Bukkit.getScheduler().runTaskAsynchronously(...)`, y solo vuelve al hilo
   principal (via `runTask`) para tocar el estado propio del plugin y mandar
   mensajes. Lo mismo aplica a `game.close()` en `abort()`. Durante
   `onDisable()` no se puede depender del scheduler asincrono (el servidor
   puede cancelar las tareas pendientes de un plugin justo despues de que
   `onDisable()` retorne), asi que `shutdown()` usa un `Thread` aparte con
   espera acotada (`CountDownLatch`, 5s) en vez del scheduler de Bukkit.

### Formula del Elo

```
E_rojo  = 1 / (1 + 10^((Elo_azul_medio - Elo_rojo_medio) / 400))
delta   = K * (S - E) * multiplicador_de_goles
```

* `S` = 1 victoria, 0.5 empate, 0 derrota
* `K` = `placement-k` durante las partidas de colocacion, `high-k` por encima
  de `high-elo-threshold`, `base-k` el resto del tiempo
* `multiplicador_de_goles` = `1 + factor * ln(1 + diferencia)`, con tope

El Elo del equipo es la **media** de sus jugadores, pero cada uno se mueve con
su propio K, asi que un novato sube mas rapido que un veterano en el mismo
equipo.

### Estructura

```
src/main/java/com/braiszx/bbranked/
├── BlockBallRankedPlugin.java      arranque, tareas, servicios
├── config/   RankedConfig, QueueMode, RankTier
├── data/     PlayerStats, StatsStorage (JDBC), StatsManager (cache)
├── elo/      EloCalculator, EloChange, MatchWinner
├── queue/    QueueManager, QueueEntry
├── match/    MatchManager, RankedMatch
├── listener/ BlockBallListener, PlayerConnectionListener
├── command/  RankedCommand
├── hook/     RankedPlaceholderExpansion
└── util/     Messages
```

Toda la base de datos corre en **un hilo propio** (`Executors.newSingleThreadExecutor`),
asi que nunca bloquea el hilo principal y no hace falta pool de conexiones.

---

## Instalacion

### 1. Compilar

Pon el jar de BlockBall en `libs/`:

```
libs/BlockBall.jar
```

Y compila:

```bash
./gradlew build
```

El resultado queda en `build/libs/BlockBallRanked-1.0.0.jar`.

### 2. Instalar

Copia el jar a `plugins/` junto a BlockBall y arranca el servidor una vez para
que se generen `config.yml` y `messages.yml`.

### 3. Crear las arenas en BlockBall

Las arenas ranked son arenas normales de BlockBall, con dos requisitos:

* **Game Type: MINIGAME** (las hubgame no tienen lobby ni countdown)
* **`minAmount` y `maxAmount` de cada equipo iguales al `team-size` del modo**

Ejemplo completo para un 2v2. Todos estos comandos son de BlockBall:

```bash
# --- crear ---
/blockball create ranked_2v2_1 &7Ranked 2v2
/blockball axe                        # te da el hacha de seleccion
/blockball highlight ranked_2v2_1     # marca las zonas con particulas

# --- zonas (click izq = esquina A, click der = esquina B, luego el comando) ---
/blockball select ranked_2v2_1 field
/blockball select ranked_2v2_1 red_goal
/blockball select ranked_2v2_1 blue_goal

# --- puntos (ponte de pie donde quieras cada uno) ---
/blockball location ranked_2v2_1 ball
/blockball location ranked_2v2_1 leave_spawnpoint
/blockball location ranked_2v2_1 red_spawnpoint
/blockball location ranked_2v2_1 blue_spawnpoint

# --- convertir en minigame y anadir los lobbies ---
/blockball gamerule gameType ranked_2v2_1 minigame
/blockball location ranked_2v2_1 red_lobby
/blockball location ranked_2v2_1 blue_lobby

# --- activar ---
/blockball toggle ranked_2v2_1
/blockball list                       # debe salir [enabled]
```

El tamano de equipo **no tiene comando**: se edita en el archivo
`plugins/BlockBall/arena/ranked_2v2_1.yml`.

```yaml
meta:
  redTeamMeta:
    minAmount: 2      # cuantos hacen falta para que arranque la cuenta atras
    maxAmount: 2      # tope del equipo
  blueTeamMeta:
    minAmount: 2
    maxAmount: 2
```

Y aplica el cambio sin reiniciar:

```bash
/blockball reload ranked_2v2_1
```

> Los valores por defecto son `minAmount: 0` y `maxAmount: 10`. Si los dejas
> asi, BlockBall aceptaria hasta 10 por equipo y el ranked no controlaria el
> tamano de las partidas.

Y despues apuntala en el config de este plugin:

```yaml
modes:
  2v2:
    display-name: "2v2"
    team-size: 2
    arenas:
      - ranked_2v2_1
      - ranked_2v2_2   # cuantas mas, mas partidas simultaneas
```

> Una arena solo puede tener una partida a la vez. Si quieres 3 partidas de
> 2v2 en paralelo, necesitas 3 arenas.

Con `queue.block-manual-join: true` nadie puede entrar a esas arenas con
`/blockball join`: solo el sistema ranked.

### 4. Comprobar

Antes de probar nada, pregunta al propio plugin si las arenas estan bien:

```
/ranked check
```

Te dira, arena por arena, si no existe, si no es MINIGAME o si el tamano de
equipo no cuadra con el modo. Lo mismo sale por consola unos segundos despues
de arrancar el servidor.

### 5. Listo

```
/ranked join 2v2
```

> Recuerda que un 2v2 necesita **4 jugadores** en cola. El mensaje al entrar
> te dice cuantos faltan: `(1/4)`, `(2/4)`...

> **Si pruebas con dos cuentas desde el mismo ordenador**, el anti-multicuentas
> las bloquea (misma IP) y nunca se emparejaran. Para probar en local pon
> `queue.block-same-ip: false` en el config y acuerdate de volver a ponerlo en
> `true` cuando abras el servidor al publico.

---

## Comandos

| Comando | Permiso | Que hace |
|---|---|---|
| `/ranked join <modo>` | `bbranked.play` | entrar a la cola |
| `/ranked leave` | `bbranked.play` | salir de la cola |
| `/ranked stats [jugador]` | `bbranked.stats` | ver estadisticas |
| `/ranked top [pagina]` | `bbranked.stats` | ranking global |
| `/ranked queues` | — | colas activas |
| `/ranked reload` | `bbranked.admin` | recargar config y mensajes (revisa las arenas de paso) |
| `/ranked check` | `bbranked.admin` | revisar que las arenas del config existan y esten bien |
| `/ranked setelo <jugador> <elo>` | `bbranked.admin` | cambiar el Elo |
| `/ranked reset <jugador>` | `bbranked.admin` | reiniciar estadisticas |
| `/ranked matches` | `bbranked.admin` | partidas ranked en curso |
| `/ranked forceend <jugador>` | `bbranked.admin` | cancelar su partida sin tocar el Elo |
| `/ranked ban <jugador> [tiempo] [motivo]` | `bbranked.admin` | banear del ranked |
| `/ranked unban <jugador>` | `bbranked.admin` | quitar el baneo |
| `/ranked banip <jugador\|ip> [tiempo] [motivo]` | `bbranked.admin` | banear una IP del ranked |
| `/ranked unbanip <ip>` | `bbranked.admin` | quitar el baneo de IP |
| `/ranked bans` | `bbranked.admin` | listar los baneos activos |
| `/ranked unpenalize <jugador\|*>` | `bbranked.admin` | quitar la sancion por abandonar (`*` = todas) |
| `/ranked party invite <jugador>` | — | invitar a tu party |
| `/ranked party accept <jugador>` | — | aceptar una invitacion |
| `/ranked party kick <jugador>` | — | expulsar (solo el lider) |
| `/ranked party leave` | — | salir de la party |
| `/ranked party disband` | — | deshacer la party (solo el lider) |
| `/ranked party info` | — | ver quien esta en tu party |
| `/ranked accept` | — | confirmar una partida encontrada |
| `/ranked menu` | — | abrir el menu grafico |
| `/ranked history [jugador] [pagina]` | `bbranked.stats` | ultimas partidas |
| `/ranked season` | — | ver la temporada actual |
| `/ranked season end [nombre]` | `bbranked.admin` | cerrar temporada y repartir premios |

Los tiempos se escriben `30m`, `2h`, `7d`, `1w` o `perm`. Si te saltas el
tiempo, el baneo es permanente y todo lo que escribas se toma como motivo.
Banear a alguien lo saca de la cola al momento, y funciona aunque este
desconectado (se busca en la base de datos del plugin).

Alias: `/rk`, `/elo`, `/bbranked`.

---

## Temporadas

```bash
/ranked season              # ver la temporada actual
/ranked season end Temporada 2   # cerrarla (admin)
```

Al cerrar: se guarda el ranking final en `bbranked_season_results`, se
ejecutan los premios, el Elo se acerca al centro y se abre la siguiente.

```yaml
season:
  soft-reset:
    target: 1000
    factor: 0.5     # alguien con 2000 empieza la siguiente en 1500
  rewards:
    top:
      1: ["give {player} diamond_block 10"]
    ranks:
      gran-maestro: ["give {player} netherite_ingot 2"]
```

No se resetea a 1000 fijo a proposito: si todo el mundo vuelve al mismo sitio,
las primeras semanas de temporada son un caos de emparejamientos absurdos.
Acercando al centro se conserva parte de lo que ya sabemos de cada jugador.

## Anti-boosting

Ganar muchas veces seguidas al **mismo** rival da cada vez menos Elo:

```yaml
elo:
  repeat-opponent:
    free-matches: 2         # las 2 primeras dan el Elo completo
    reduction-per-match: 0.25   # cada una mas, un 25% menos
    min-multiplier: 0.15    # nunca baja del 15%
    reset-hours: 24         # 24h sin veros y el contador vuelve a cero
    alert-after: 6          # avisa al staff (y a Discord) a partir de aqui
```

Solo afecta a lo que **ganas**. Perder cuesta siempre lo mismo — si no, dejarse
perder seria una forma barata de escaquearse de las derrotas.

Los enfrentamientos se cargan al empezar la partida (asincrono, hay minutos de
margen) y se apuntan al terminarla.

## Confirmacion de partida

Cuando se forma la partida, en vez de teleportar directamente sale un boton en
el chat:

```
-------- Partida encontrada --------
Modo: 2v2. Tienes 20s para confirmar.
       [ ACEPTAR PARTIDA ]
------------------------------------
```

Es MiniMessage con `<click:run_command:'/ranked accept'>`, con sonido y cuenta
atras en la barra de accion. Quien no confirme se lleva penalizacion de cola y
**los demas vuelven a la cola sin perder su sitio**.

## Parties

Hasta 3 amigos pueden entrar juntos a la cola y jugar **en el mismo equipo**.

```bash
/ranked party invite Pepe     # se crea la party sola con la primera invitacion
/ranked party accept Juan     # Pepe acepta
/ranked join 3v3              # solo el lider encola a la party
```

Reglas:

* **Maximo 3** jugadores (`party.max-size`).
* **Maximo 600 de diferencia de Elo** entre dos miembros cualesquiera
  (`party.max-elo-difference`). Se comprueba contra todos, no solo contra el
  lider, y se vuelve a comprobar al aceptar. Asi nadie mete a un amigo mucho
  peor o mucho mejor para desequilibrar la partida.
* La party tiene que **caber en un equipo**: una party de 3 solo puede jugar
  3v3, una de 2 puede jugar 2v2 o 3v3. Si no cabe, el plugin lo dice.
* Solo el **lider** mete a la party en la cola.
* Si alguien se sale de la party o se desconecta, la party sale de la cola.
* Las invitaciones caducan a los 60s (`party.invite-timeout-seconds`).

Internamente la cola no guarda jugadores sueltos sino **tickets**: un jugador
solo es un ticket de 1 y una party es un ticket de N. El reparto de equipos
prueba todas las combinaciones de tickets y elige la que menos diferencia de
Elo deja entre los dos equipos, sin partir nunca un ticket. Si un grupo suma
los jugadores necesarios pero no se puede repartir sin separar una party (dos
parties de 2 en un 3v3, por ejemplo), se descarta y se prueba otro grupo.

> El anti-multicuentas se aplica **entre tickets distintos**, asi que nunca te
> puedes cruzar con tu propia alt. Dentro de una party no se comprueba, porque
> sus miembros son companeros de equipo por definicion (dos hermanos en la
> misma casa pueden jugar juntos).

## Placeholders

Necesitan PlaceholderAPI:

```
%bbranked_elo%          %bbranked_wins%       %bbranked_streak%
%bbranked_peak%         %bbranked_losses%     %bbranked_best_streak%
%bbranked_rank%         %bbranked_draws%      %bbranked_leaves%
%bbranked_rank_id%      %bbranked_matches%    %bbranked_position%
%bbranked_winrate%      %bbranked_goals%      %bbranked_in_queue%
%bbranked_in_match%     %bbranked_mvps%
%bbranked_top_name_1%   %bbranked_top_elo_1%   (1..N)
```

---

## Configuracion destacada

```yaml
elo:
  starting: 1000            # Elo inicial
  placement-matches: 10     # partidas de colocacion
  placement-k: 60           # K alto durante la colocacion
  base-k: 32
  high-elo-threshold: 1800  # a partir de aqui el Elo se mueve menos
  high-k: 16

  goal-difference:
    enabled: true           # ganar 5-0 da mas que ganar 1-0
    factor: 0.18
    max-multiplier: 1.6

  draw:
    loss-percent: 40        # empatar cuesta el 40% de lo que costaria perder

  mvp:
    enabled: true           # el maximo goleador se lleva Elo extra
    bonus: 5
    min-team-size: 2        # no hay MVP en 1v1
    share-on-tie: true      # si empatan a goles, lo comparten

  leaver:
    forfeit-on-leave: true  # abandonar = perder la partida
    extra-penalty: 25       # Elo extra que pierde el que se va
    protect-teammates: true # sus companeros no pierden Elo
    queue-ban-seconds: 300  # y no puede encolar durante 5 min

queue:
  block-same-ip: true       # anti-multicuentas (ver nota de arriba)

party:
  max-size: 3               # amigos por party
  max-elo-difference: 600   # diferencia de Elo maxima entre miembros

queue:
  initial-range: 100              # diferencia de Elo aceptada al entrar
  range-expansion-per-second: 10  # se ensancha esperando
  max-range: 1000
```

### Sonidos

Cada evento tiene su sonido, configurable en `config.yml`:

```yaml
sounds:
  enabled: true
  queue-join: "entity.experience_orb.pickup 1.0 1.5"
  match-found: "block.anvil_land 0.6 1.6"
  victory: "ui.toast.challenge_complete 1.0 1.0"
  mvp: "ui.toast.challenge_complete 1.0 1.4"
  # ...
```

Formato: `"nombre volumen tono"` (volumen y tono opcionales, el tono va de 0.5
a 2.0). Pon `"none"` para quitar uno. La lista completa de sonidos esta en la
[wiki de Minecraft](https://minecraft.wiki/w/Sounds.json).

> Se reproducen con la sobrecarga de `playSound` que acepta texto, no con el
> enum `org.bukkit.Sound`. Ese enum cambia entre versiones de Minecraft y
> habria que recompilar el plugin cada vez; con texto, poner un sonido que no
> existe simplemente no suena en vez de romper nada.

### Discord

Avisos de resultados, ascensos, fin de temporada y posible boosting.

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
```

Para conseguir la URL: **Ajustes del servidor → Integraciones → Webhooks →
Nuevo webhook →** elige canal **→ Copiar URL**.

> Esa URL es una contrasena: cualquiera que la tenga puede publicar en tu
> canal. No subas tu `config.yml` a GitHub ni la ensenes en capturas. Si se te
> escapa, borra el webhook en Discord y crea otro.

Las peticiones van de forma asincrona con `HttpClient`, asi que un Discord
caido o lento nunca bloquea el servidor.

### Decay por inactividad

```yaml
decay:
  enabled: false       # desactivado por defecto
  inactive-days: 14
  elo-per-day: 10
  floor: 1200          # no baja de aqui
  require-placements: true
```

Solo afecta a quien esta por encima del suelo: un jugador normal no nota nada,
solo la gente de arriba que deja de jugar.

Los mensajes estan en `messages.yml` en formato
[MiniMessage](https://docs.advntr.dev/minimessage/format.html).

---

## Notas y limitaciones

* **Folia**: el plugin usa el scheduler clasico de Bukkit. BlockBall si tiene
  build para Folia, pero este plugin no esta adaptado a los schedulers por
  region.
* **Version de BlockBall**: la API de BlockBall no es estable entre versiones
  mayores. Si actualizas BlockBall, recompila con el jar nuevo.
* **Version gratuita de BlockBall**: solo soporta la ultima version de
  Minecraft. Para 1.21.7 concretamente puede que necesites la version premium
  de BlockBall — eso depende de BlockBall, no de este plugin.
* Cambiar `database:` en el config requiere reiniciar el servidor;
  `/ranked reload` solo recarga el resto.
