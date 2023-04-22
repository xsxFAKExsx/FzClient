package net.aspw.client.features.module.impl.combat;

import net.aspw.client.Client;
import net.aspw.client.event.EventTarget;
import net.aspw.client.event.TickEvent;
import net.aspw.client.event.UpdateEvent;
import net.aspw.client.features.module.Module;
import net.aspw.client.features.module.ModuleCategory;
import net.aspw.client.features.module.ModuleInfo;
import net.aspw.client.utils.MovementUtils;
import net.aspw.client.value.FloatValue;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import java.io.IOException;

@ModuleInfo(name = "Tickbase", spacedName = "Tick base", category = ModuleCategory.COMBAT)
public final class Tickbase extends Module {
    private final FloatValue rangeValue = new FloatValue("Range", 3.0f, 1, 8, "m");
    private int skippedTick, preTick;
    private boolean flag;
    private KillAura killAura = Client.moduleManager.getModule(KillAura.class);

    @Override
    public void onDisable() {
        mc.timer.timerSpeed = 1.0f;
    }

    @Override
    public void onEnable() {
        mc.timer.timerSpeed = 1.0f;
    }

    @EventTarget
    public void onUpdate(final UpdateEvent event) {
        if (!MovementUtils.isMoving() || killAura.getTarget() == null) {
            mc.timer.timerSpeed = 1.0f;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (flag) return;
        if (killAura.getTarget() == null) {
            sleep();
        } else {
            if (shouldSkip()) {
                flag = true;
                for (int i = 0; i < preTick; i++) {
                    try {
                        mc.runTick();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                flag = false;
            } else {
                sleep();
            }
        }
    }

    private void sleep() {
        if (skippedTick > 0) {
            try {
                Thread.sleep(2L * skippedTick);
                skippedTick = 0;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mc.timer.timerSpeed = 0.054f + skippedTick;
        }
    }

    public boolean shouldSkip() {
        final EntityLivingBase target = killAura.getTarget();
        if (target == null || skippedTick > 5 || !mc.thePlayer.isSprinting()) return false;
        final double dx = mc.thePlayer.posX - target.posX, dz = mc.thePlayer.posZ - target.posZ;
        if (MathHelper.sqrt_double(dx * dx + dz * dz) > rangeValue.getValue()) {
            preTick = (int) (2 * (MathHelper.sqrt_double(dx * dx + dz * dz) - rangeValue.getValue()));
            skippedTick += preTick;
            return true;
        }
        return false;
    }
}