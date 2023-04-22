package net.aspw.client.visual.hud.element.elements.targets.impl

import net.aspw.client.utils.extensions.getDistanceToEntityBox
import net.aspw.client.utils.render.RenderUtils
import net.aspw.client.value.BoolValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.aspw.client.visual.font.Fonts
import net.aspw.client.visual.hud.element.Border
import net.aspw.client.visual.hud.element.elements.Target
import net.aspw.client.visual.hud.element.elements.targets.TargetStyle
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import java.awt.Color
import kotlin.math.abs

class LiquidBounce(inst: Target) : TargetStyle("LiquidBounce", inst, true) {

    val hurtTimeAnim = BoolValue("HurtTimeAnim", true, { targetInstance.styleValue.get().equals("liquidbounce", true) })
    val borderColorMode = ListValue(
        "Border-Color",
        arrayOf("Custom", "MatchBar", "None"),
        "None",
        { targetInstance.styleValue.get().equals("liquidbounce", true) })
    val borderWidthValue =
        FloatValue("Border-Width", 3F, 0.5F, 5F, { targetInstance.styleValue.get().equals("liquidbounce", true) })
    val borderRedValue = IntegerValue(
        "Border-Red",
        0,
        0,
        255,
        {
            targetInstance.styleValue.get().equals("liquidbounce", true) && borderColorMode.get().equals("custom", true)
        })
    val borderGreenValue = IntegerValue(
        "Border-Green",
        0,
        0,
        255,
        {
            targetInstance.styleValue.get().equals("liquidbounce", true) && borderColorMode.get().equals("custom", true)
        })
    val borderBlueValue = IntegerValue(
        "Border-Blue",
        0,
        0,
        255,
        {
            targetInstance.styleValue.get().equals("liquidbounce", true) && borderColorMode.get().equals("custom", true)
        })
    val borderAlphaValue = IntegerValue(
        "Border-Alpha",
        0,
        0,
        255,
        {
            targetInstance.styleValue.get().equals("liquidbounce", true) && borderColorMode.get().equals("custom", true)
        })

    private var lastTarget: EntityPlayer? = null

    override fun drawTarget(entity: EntityPlayer) {
        if (entity != lastTarget || easingHealth < 0 || easingHealth > entity.maxHealth ||
            abs(easingHealth - entity.health) < 0.01
        ) {
            easingHealth = entity.health
        }

        val width = (38 + Fonts.fontSFUI40.getStringWidth(entity.name))
            .coerceAtLeast(118)
            .toFloat()

        val borderColor =
            getColor(Color(borderRedValue.get(), borderGreenValue.get(), borderBlueValue.get(), borderAlphaValue.get()))

        // Draw rect box
        if (borderColorMode.get().equals("none", true))
            RenderUtils.drawRect(0F, 0F, width, 36F, targetInstance.bgColor.rgb)
        else
            RenderUtils.drawBorderedRect(
                0F,
                0F,
                width,
                36F,
                borderWidthValue.get(),
                if (borderColorMode.get().equals("matchbar", true)) targetInstance.barColor.rgb else borderColor.rgb,
                targetInstance.bgColor.rgb
            )

        // Damage animation
        if (easingHealth > entity.health)
            RenderUtils.drawRect(
                0F, 34F, (easingHealth / entity.maxHealth) * width,
                36F, getColor(Color(252, 185, 65)).rgb
            )

        // Health bar
        RenderUtils.drawRect(
            0F, 34F, (entity.health / entity.maxHealth) * width,
            36F, targetInstance.barColor.rgb
        )

        // Heal animation
        if (easingHealth < entity.health)
            RenderUtils.drawRect(
                (easingHealth / entity.maxHealth) * width, 34F,
                (entity.health / entity.maxHealth) * width, 36F, getColor(Color(44, 201, 144)).rgb
            )

        updateAnim(entity.health)

        Fonts.fontSFUI40.drawString(entity.name, 36, 3, getColor(-1).rgb)
        Fonts.fontSFUI35.drawString(
            "Distance: ${decimalFormat.format(mc.thePlayer.getDistanceToEntityBox(entity))}",
            36,
            15,
            getColor(-1).rgb
        )

        // Draw info
        val playerInfo = mc.netHandler.getPlayerInfo(entity.uniqueID)
        if (playerInfo != null) {
            Fonts.fontSFUI35.drawString(
                "Ping: ${playerInfo.responseTime.coerceAtLeast(0)}",
                36, 24, getColor(-1).rgb
            )

            // Draw head
            val locationSkin = playerInfo.locationSkin
            if (hurtTimeAnim.get()) {
                val scaleHT =
                    (entity.hurtTime.toFloat() / entity.maxHurtTime.coerceAtLeast(1).toFloat()).coerceIn(0F, 1F)
                drawHead(
                    locationSkin,
                    2F + 15F * (scaleHT * 0.2F),
                    2F + 15F * (scaleHT * 0.2F),
                    1F - scaleHT * 0.2F,
                    30, 30,
                    1F, 0.4F + (1F - scaleHT) * 0.6F, 0.4F + (1F - scaleHT) * 0.6F
                )
            } else
                drawHead(skin = locationSkin, width = 30, height = 30, alpha = 1F - targetInstance.getFadeProgress())
        }

        lastTarget = entity
    }

    override fun handleBlur(entity: EntityPlayer) {
        val width = (38 + Fonts.fontSFUI40.getStringWidth(entity.name))
            .coerceAtLeast(118)
            .toFloat()

        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        RenderUtils.quickDrawRect(0F, 0F, width, 36F)
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
    }

    override fun handleShadowCut(entity: EntityPlayer) = handleBlur(entity)

    override fun handleShadow(entity: EntityPlayer) {
        val width = (38 + Fonts.fontSFUI40.getStringWidth(entity.name))
            .coerceAtLeast(118)
            .toFloat()

        RenderUtils.newDrawRect(0F, 0F, width, 36F, shadowOpaque.rgb)
    }

    override fun getBorder(entity: EntityPlayer?): Border {
        entity ?: return Border(0F, 0F, 118F, 36F)
        val width = (38 + Fonts.fontSFUI40.getStringWidth(entity.name))
            .coerceAtLeast(118)
            .toFloat()
        return Border(0F, 0F, width, 36F)
    }

}