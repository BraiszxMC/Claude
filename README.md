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

**Dos detalles importantes de la API que condicionan el diseño:**

1. `GameEndEvent` **no se dispara en los empates**. BlockBall solo lo lanza
   desde `onWin(team)`; cuando se acaba el tiempo con marcador igualado llama
   a `onDraw()`, que no dispara nada. Por eso `MatchManager` lleva un
   **monitor propio** que corre cada segundo: cachea el marcador y, si la
   instancia de la partida se marca como `isDisposed()` sin haber recibido
   `GameEndEvent`, deduce el resultado del marcador guardado.
2. Cuando una partida termina, BlockBall la **cierra y crea una instancia
   nueva** para la misma arena (`GameServiceImpl.runGames()` llama a
   `reload(arena)` en cuanto ve `isDisposed`). Por eso el plugin guarda la
   *referencia* al `SoccerGame` en vez de buscarlo por nombre cada tick: si no,
   leeria el marcador 0-0 de la partida recien reiniciada.

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

### 4. Listo

```
/ranked join 2v2
```

---

## Comandos

| Comando | Permiso | Que hace |
|---|---|---|
| `/ranked join <modo>` | `bbranked.play` | entrar a la cola |
| `/ranked leave` | `bbranked.play` | salir de la cola |
| `/ranked stats [jugador]` | `bbranked.stats` | ver estadisticas |
| `/ranked top [pagina]` | `bbranked.stats` | ranking global |
| `/ranked queues` | — | colas activas |
| `/ranked reload` | `bbranked.admin` | recargar config y mensajes |
| `/ranked setelo <jugador> <elo>` | `bbranked.admin` | cambiar el Elo |
| `/ranked reset <jugador>` | `bbranked.admin` | reiniciar estadisticas |
| `/ranked matches` | `bbranked.admin` | partidas ranked en curso |
| `/ranked forceend <jugador>` | `bbranked.admin` | cancelar su partida sin tocar el Elo |

Alias: `/rk`, `/elo`, `/bbranked`.

---

## Placeholders

Necesitan PlaceholderAPI:

```
%bbranked_elo%          %bbranked_wins%       %bbranked_streak%
%bbranked_peak%         %bbranked_losses%     %bbranked_best_streak%
%bbranked_rank%         %bbranked_draws%      %bbranked_leaves%
%bbranked_rank_id%      %bbranked_matches%    %bbranked_position%
%bbranked_winrate%      %bbranked_goals%      %bbranked_in_queue%
%bbranked_in_match%
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

  leaver:
    forfeit-on-leave: true  # abandonar = perder la partida
    extra-penalty: 25       # Elo extra que pierde el que se va
    protect-teammates: true # sus companeros no pierden Elo
    queue-ban-seconds: 300  # y no puede encolar durante 5 min

queue:
  initial-range: 100              # diferencia de Elo aceptada al entrar
  range-expansion-per-second: 10  # se ensancha esperando
  max-range: 1000
```

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
