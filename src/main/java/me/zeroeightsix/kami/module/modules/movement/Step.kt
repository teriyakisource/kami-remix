package me.zeroeightsix.kami.module.modules.movement

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.BaritoneUtils
import me.zeroeightsix.kami.util.Bind
import me.zeroeightsix.kami.util.PacketHelper
import me.zeroeightsix.kami.util.event.listener
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayer.PositionRotation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.fml.client.FMLClientHandler
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Keyboard
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.math.max

/**
 * The packet mode code is licensed under MIT and can be found here:
 * https://github.com/fr1kin/ForgeHax/blob/2011740/src/main/java/com/matt/forgehax/mods/StepMod.java
 */
@Module.Info(
        name = "Step",
        description = "Changes the vanilla behavior for stepping up blocks",
        category = Module.Category.MOVEMENT
)
object Step : Module() {
    private val mode: Setting<Mode> = register(Settings.e("Mode", Mode.PACKET))
    private val speed = register(Settings.integerBuilder("Speed").withMinimum(1).withMaximum(100).withValue(40).withVisibility { mode.value == Mode.VANILLA }.build())
    private val height = register(Settings.floatBuilder("Height").withRange(0.0f, 10.0f).withValue(1.0f).withVisibility { mode.value == Mode.PACKET }.build())
    private val upStep = register(Settings.b("UpStep", true))
    private val downStep = register(Settings.b("DownStep", false))
    private val bindUpStep = register(Settings.custom("BindUpStep", Bind.none(), BindConverter()))
    private val bindDownStep = register(Settings.custom("BindDownStep", Bind.none(), BindConverter()))

    private val entityStep = register(Settings.booleanBuilder("Entities").withValue(true).withVisibility { mode.value == Mode.PACKET }.build())

    private var previousPositionPacket: CPacketPlayer? = null
    private var wasOnGround = false
    private const val defaultHeight = 0.6f

    private enum class Mode {
        VANILLA, PACKET
    }

    override fun onToggle() {
        BaritoneUtils.settings()?.assumeStep?.value = isEnabled
    }

    init {
        /**
         * Vanilla mode.
         */
        listener<SafeTickEvent> {
            if (mc.player.isElytraFlying || mc.player.capabilities.isFlying) return@listener
            if (mode.value == Mode.VANILLA
                    && mc.player.onGround
                    && !mc.player.isOnLadder
                    && !mc.player.isInWater
                    && !mc.player.isInLava) {
                if (upStep.value && mc.player.collidedHorizontally) {
                    mc.player.motionY = speed.value / 100.0
                } else if (downStep.value && !mc.player.collidedHorizontally) {
                    mc.player.motionY = -(speed.value / 100.0)
                }
            }
            if (mode.value == Mode.PACKET) {
                updateStepHeight(mc.player)
                updateUnStep(mc.player)
                mc.player.ridingEntity?.stepHeight = if (entityStep.value) 256f else 1f
            }
        }

        listener<InputEvent.KeyInputEvent> {
            if (bindUpStep.value.isDown(Keyboard.getEventKey())) {
                upStep.value = !upStep.value
                MessageSendHelper.sendChatMessage(upStep.toggleMsg())
            }

            if (bindDownStep.value.isDown(Keyboard.getEventKey())) {
                downStep.value = !downStep.value
                MessageSendHelper.sendChatMessage(downStep.toggleMsg())
            }
        }
    }

    private fun Setting<Boolean>.toggleMsg() = "$chatName Turned ${this.name} ${if (this.value) "&aon" else "&coff"}&f!"

    /**
     * Disable states to reset whatever was done in Packet mode
     */
    override fun onEnable() {
        if (mc.player != null) {
            wasOnGround = mc.player.onGround
        }
    }

    override fun onDisable() {
        mc.player?.let {
            it.stepHeight = defaultHeight
            it.ridingEntity?.stepHeight = 1f
        }
    }

    /**
     * Everything onwards is Packet mode
     */
    init {
        listener<PacketEvent.Send> {
            if (mc.player == null || mode.value != Mode.PACKET || mc.player.isElytraFlying) return@listener

            if (it.packet is CPacketPlayer.Position || it.packet is PositionRotation) {
                val packetPlayer = it.packet as CPacketPlayer

                if (previousPositionPacket != null && !PacketHelper.isIgnored(it.packet)) {
                    val diffY = packetPlayer.getY(0.0) - previousPositionPacket!!.getY(0.0)

                    /**
                     * The Y difference must be between 0.6 and 1.25
                     */
                    if (diffY > defaultHeight && diffY <= 1.25) {
                        val sendList = ArrayList<CPacketPlayer.Position>()

                        /**
                         * Send additional packets to bypass NCP
                         */
                        val x = previousPositionPacket!!.getX(0.0)
                        val y = previousPositionPacket!!.getY(0.0)
                        val z = previousPositionPacket!!.getZ(0.0)
                        sendList.add(CPacketPlayer.Position(x, y + 0.419, z, true))
                        sendList.add(CPacketPlayer.Position(x, y + 0.753, z, true))
                        sendList.add(CPacketPlayer.Position(packetPlayer.getX(0.0), packetPlayer.getY(0.0), packetPlayer.getZ(0.0), packetPlayer.isOnGround))

                        for (toSend in sendList) {
                            PacketHelper.ignore(toSend)
                            FMLClientHandler.instance().clientToServerNetworkManager.sendPacket(toSend)
                        }
                        it.cancel()
                    }
                }
                previousPositionPacket = it.packet
            }
        }
    }

    /**
     * Update player step height to the height setting
     */
    private fun updateStepHeight(player: EntityPlayer) {
        player.stepHeight = if (upStep.value && player.onGround) height.value else defaultHeight
    }

    /**
     * Used to finish walking off the edge and actually touch the block. Required on NCP
     */
    private fun updateUnStep(player: EntityPlayer) {
        try {
            if (downStep.value && wasOnGround && !player.onGround && player.motionY <= 0) {
                unStep(player)
            }
        } finally {
            wasOnGround = player.onGround
        }
    }

    /**
     * When talking off of the edge of a block, move yourself downwards
     */
    private fun unStep(player: EntityPlayer) {
        val range = player.entityBoundingBox.expand(0.0, (-height.value).toDouble(), 0.0).contract(0.0, player.height.toDouble(), 0.0)

        if (!player.world.collidesWithAnyBlock(range)) {
            return
        }

        val collisionBoxes = player.world.getCollisionBoxes(player, range)
        val newY = AtomicReference(0.0)

        collisionBoxes.forEach(Consumer { box: AxisAlignedBB -> newY.set(max(newY.get(), box.maxY)) })
        player.setPositionAndUpdate(player.posX, newY.get(), player.posZ)
    }

}