# TVCam

Mod **cliente** de Fabric para **Minecraft 1.21.11** que abre una **segunda ventana**
con la imagen limpia de una camara fija, para retransmitir estilo television: tu
juegas en tu ventana y el espectador ve el plano de camara.

Version **beta**: lo minimo que hace falta para emitir. Colocar camaras, cortar
entre ellas y una segunda ventana que capturar en OBS.

* Minecraft **1.21.11**, Fabric, Java 21
* **Solo cliente**: el servidor no necesita el mod. Funciona en singleplayer y en
  cualquier servidor.
* Requiere **Fabric API**

---

## Como se usa

1. Entra al mundo y ponte donde quieras la camara, mirando hacia donde quieras
   que enfoque.
2. `/tvcam add Torre` (o **Numpad &ast;**) crea la camara ahi.
3. **Numpad 1-9** corta a esa camara. Al cortar por primera vez se abre la ventana
   `TVCam`.
4. En OBS: *Captura de ventana* -> `TVCam`.
5. **Numpad 0** para la emision.

| Comando | Que hace |
|---|---|
| `/tvcam add [nombre]` | crea una camara donde estas, mirando a donde miras |
| `/tvcam list` | lista las camaras de este mundo |
| `/tvcam del <n>` | borra una camara |
| `/tvcam clear` | borra todas |
| `/tvcam cut <n>` | corta a esa camara (`0` = parar) |
| `/tvcam window` | abre o cierra la ventana de camara |

| Tecla | Que hace |
|---|---|
| Numpad 1-9 | cortar a la camara 1-9 |
| Numpad 0 | parar la emision |
| Numpad &ast; | crear camara aqui |
| Numpad . | abrir/cerrar la ventana de camara |

Las camaras se guardan en `config/tvcam.json`, separadas por servidor y por
dimension, asi que siguen ahi cuando vuelves a entrar.

---

## Como funciona por dentro

Minecraft dibuja **un** frame por vuelta, desde tus ojos, en un unico framebuffer.
Para emitir hacen falta dos imagenes distintas. Renderizar el mundo dos veces por
frame cuesta la mitad de los FPS, asi que TVCam **alterna**:

* **frames pares** -> se dibujan desde la camara, con el HUD y la mano ocultos, y
  se presentan en la ventana de camara;
* **frames impares** -> se dibujan normal y se presentan en tu ventana.

Cada ventana va a la mitad del refresco, pero el coste total es practicamente el
de jugar sin el mod: **no se renderiza el mundo dos veces**.

Piezas:

| Fichero | Papel |
|---|---|
| `camera/CameraDirector` | el realizador: que camaras hay, cual esta al aire y de quien es cada frame |
| `camera/CameraStore` | guardado en `config/tvcam.json` por mundo y dimension |
| `render/CameraWindow` | la segunda ventana GLFW; comparte el contexto de OpenGL con el juego y pinta su textura a pantalla completa |
| `render/FrameMirror` | copia de tu ultimo frame, para devolverlo a tu ventana en los frames de camara y que no parpadee |
| `mixin/MinecraftClientMixin` | marca el principio y el final de cada frame |
| `mixin/CameraMixin` | mueve el punto de vista a la camara en los frames de camara |
| `mixin/WindowMixin` | reparte cada frame a su ventana justo antes de presentarlo |
| `mixin/GameRendererMixin` | la mano y el objeto que llevas no salen en la emision |
| `mixin/SoundSystemMixin` | el oido se queda contigo y no salta a la camara |

La ventana secundaria se crea con `glfwCreateWindow` **compartiendo el contexto**
de la ventana del juego. Compartir contexto es lo que hace que esto sea barato: la
imagen ya esta en la GPU y solo se presenta en otra ventana, sin copiar pixeles por
la CPU.

---

## Limitaciones conocidas de la beta

* **Cada ventana va a la mitad de los FPS.** Con vsync a 60, unos 30 por ventana.
* **La camara solo ve chunks cargados.** Si la pones muy lejos de donde estas, esa
  zona no esta renderizada y se vera vacia o con niebla.
* **Sin zoom, sin sets, sin modos de seguimiento, sin transiciones.** Corte seco
  entre camaras fijas y poco mas: es una beta.
* **Sin probar con Sodium/Iris.** Es lo siguiente en la lista.
* El tamano de la ventana de camara es 1280x720 y se puede redimensionar a mano;
  la imagen se ajusta con barras negras para no deformarse.

---

## Compilar

```
./gradlew build
```

El jar sale en `build/libs/tvcam-<version>.jar`. Ponlo en `mods/` junto a
Fabric API.
