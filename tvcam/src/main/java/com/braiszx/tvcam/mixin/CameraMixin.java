package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraPoint;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En los frames de camara, mueve el punto de vista a la camara al aire. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void tvcam$override(World world, Entity focusedEntity, boolean thirdPerson,
                                boolean inverseView, float tickDelta, CallbackInfo ci) {
        CameraDirector director = CameraDirector.get();
        if (!director.isCameraFrame()) {
            return;
        }
        CameraPoint point = director.activeCamera();
        if (point == null) {
            return;
        }
        setRotation(point.yaw(), point.pitch());
        setPos(point.x(), point.y(), point.z());
    }
}
