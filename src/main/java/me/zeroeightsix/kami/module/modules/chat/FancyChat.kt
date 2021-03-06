package me.zeroeightsix.kami.module.modules.chat

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.event.listener
import me.zeroeightsix.kami.util.math.MathUtils
import net.minecraft.network.play.client.CPacketChatMessage

@Module.Info(
        name = "FancyChat",
        category = Module.Category.CHAT,
        description = "Makes messages you send fancy",
        showOnArray = Module.ShowOnArray.OFF
)
object FancyChat : Module() {
    private val uwu = register(Settings.b("uwu", true))
    private val leet = register(Settings.b("1337", false))
    private val mock = register(Settings.b("mOcK", false))
    private val green = register(Settings.b(">", false))
    private val blue = register(Settings.b("`", false))
    private val randomSetting = register(Settings.booleanBuilder("RandomCase").withValue(true).withVisibility { mock.value })
    private val commands = register(Settings.b("Commands", false))

    init {
        listener<PacketEvent.Send> {
            if (it.packet !is CPacketChatMessage) return@listener
            var s = it.packet.getMessage()

            if (!commands.value && isCommand(s)) return@listener
            s = getText(s)

            if (s.length >= 256) s = s.substring(0, 256)
            it.packet.message = s
        }
    }

    private fun getText(s: String): String {
        var string = s
        if (uwu.value) string = uwuConverter(string)
        if (leet.value) string = leetConverter(string)
        if (mock.value) string = mockingConverter(string)
        if (green.value) string = greenConverter(string)
        if (blue.value) string = blueConverter(string)
        return string
    }

    private fun greenConverter(input: String): String {
        return "> $input"
    }

    private fun blueConverter(input: String): String {
        return "`$input"
    }

    override fun getHudInfo(): String {
        val returned = StringBuilder()
        if (uwu.value) {
            returned.append("uwu")
        }
        if (leet.value) {
            returned.append(" 1337")
        }
        if (mock.value) {
            returned.append(" mOcK")
        }
        if (green.value) {
            returned.append(" >")
        }
        if (blue.value) {
            returned.append(" `")
        }
        return returned.toString()
    }

    private fun isCommand(s: String): Boolean {
        for (value in CustomChat.cmdCheck) {
            if (s.startsWith(value)) return true
        }
        return false
    }

    private fun leetConverter(input: String): String {
        val message = StringBuilder()
        for (element in input) {
            var inputChar = element.toString() + ""
            inputChar = inputChar.toLowerCase()
            inputChar = leetSwitch(inputChar)
            message.append(inputChar)
        }
        return message.toString()
    }

    private fun mockingConverter(input: String): String {
        val message = StringBuilder()
        for (i in input.indices) {
            var inputChar = input[i].toString() + ""
            val rand = if (randomSetting.value) (0..1).random() else 0
            inputChar = if (!MathUtils.isNumberEven(i + rand)) inputChar.toUpperCase() else inputChar.toLowerCase()
            message.append(inputChar)
        }
        return message.toString()
    }

    private fun uwuConverter(input: String): String {
        var lInput = input
        lInput = lInput.replace("ove", "uv")
        lInput = lInput.replace("the", "da")
        lInput = lInput.replace("is", "ish")
        lInput = lInput.replace("r", "w")
        lInput = lInput.replace("ve", "v")
        lInput = lInput.replace("l", "w")
        return lInput
    }

    private fun leetSwitch(i: String): String {
        return when (i) {
            "a" -> "4"
            "e" -> "3"
            "g" -> "6"
            "l", "i" -> "1"
            "o" -> "0"
            "s" -> "$"
            "t" -> "7"
            else -> i
        }
    }
}
