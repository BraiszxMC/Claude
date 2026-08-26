package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraPoint;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Comandos de cliente: no los ve el servidor, asi que funcionan en cualquier
 * servidor sin que tenga el mod instalado.
 */
public final class Commands {
    private Commands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("tvcam")
                        .then(literal("add")
                                .executes(context -> add(context.getSource(), null))
                                .then(argument("nombre", StringArgumentType.greedyString())
                                        .executes(context -> add(context.getSource(),
                                                StringArgumentType.getString(context, "nombre")))))
                        .then(literal("list").executes(context -> list(context.getSource())))
                        .then(literal("del")
                                .then(argument("numero", IntegerArgumentType.integer(1))
                                        .executes(context -> delete(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "numero")))))
                        .then(literal("clear").executes(context -> clear(context.getSource())))
                        .then(literal("cut")
                                .then(argument("numero", IntegerArgumentType.integer(0))
                                        .executes(context -> cut(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "numero")))))
                        .then(literal("window").executes(context -> toggleWindow(context.getSource())))
                        .executes(context -> help(context.getSource()))));
    }

    private static int add(FabricClientCommandSource source, String name) {
        CameraPoint point = CameraDirector.get().addHere(name);
        if (point == null) {
            source.sendError(Text.literal("No se pudo crear la camara"));
            return 0;
        }
        source.sendFeedback(Text.literal("Camara " + CameraDirector.get().cameras().size()
                + " creada: " + point.name()).formatted(Formatting.GREEN));
        return 1;
    }

    private static int list(FabricClientCommandSource source) {
        List<CameraPoint> cameras = CameraDirector.get().cameras();
        if (cameras.isEmpty()) {
            source.sendFeedback(Text.literal("No hay camaras en este mundo. Usa /tvcam add")
                    .formatted(Formatting.GRAY));
            return 1;
        }
        source.sendFeedback(Text.literal("Camaras de este mundo:").formatted(Formatting.AQUA));
        for (int i = 0; i < cameras.size(); i++) {
            CameraPoint camera = cameras.get(i);
            boolean live = i == CameraDirector.get().activeIndex();
            source.sendFeedback(Text.literal(String.format("  %d. %s  (%.1f, %.1f, %.1f)%s",
                            i + 1, camera.name(), camera.x(), camera.y(), camera.z(),
                            live ? "  <- al aire" : ""))
                    .formatted(live ? Formatting.GREEN : Formatting.WHITE));
        }
        return 1;
    }

    private static int delete(FabricClientCommandSource source, int number) {
        if (CameraDirector.get().remove(number - 1)) {
            source.sendFeedback(Text.literal("Camara " + number + " borrada").formatted(Formatting.YELLOW));
            return 1;
        }
        source.sendError(Text.literal("No existe la camara " + number));
        return 0;
    }

    private static int clear(FabricClientCommandSource source) {
        CameraDirector.get().clear();
        source.sendFeedback(Text.literal("Todas las camaras borradas").formatted(Formatting.YELLOW));
        return 1;
    }

    private static int cut(FabricClientCommandSource source, int number) {
        CameraDirector.get().cut(number - 1);
        return 1;
    }

    private static int toggleWindow(FabricClientCommandSource source) {
        CameraDirector.get().toggleWindow();
        source.sendFeedback(Text.literal(CameraDirector.get().window().isOpen()
                ? "Ventana de camara abierta" : "Ventana de camara cerrada").formatted(Formatting.AQUA));
        return 1;
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("TVCam").formatted(Formatting.AQUA, Formatting.BOLD));
        source.sendFeedback(Text.literal("""
                /tvcam add [nombre]  crea una camara donde estas mirando
                /tvcam list          lista las camaras del mundo
                /tvcam del <n>       borra una camara
                /tvcam clear         borra todas
                /tvcam cut <n>       corta a esa camara (0 = parar)
                /tvcam window        abre o cierra la ventana de camara
                Numpad 1-9 cortan, Numpad 0 para, Numpad * crea, Numpad . abre la ventana""")
                .formatted(Formatting.GRAY));
        return 1;
    }
}
