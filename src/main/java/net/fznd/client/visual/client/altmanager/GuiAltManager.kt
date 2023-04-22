package net.aspw.client.visual.client.altmanager

import com.mojang.authlib.Agent
import com.mojang.authlib.exceptions.AuthenticationException
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
import com.thealtening.AltService
import com.thealtening.api.TheAltening
import me.liuli.elixir.account.CrackedAccount
import me.liuli.elixir.account.MicrosoftAccount
import me.liuli.elixir.account.MinecraftAccount
import me.liuli.elixir.account.MojangAccount
import net.aspw.client.Client
import net.aspw.client.Client.fileManager
import net.aspw.client.event.SessionEvent
import net.aspw.client.features.module.impl.visual.Hud
import net.aspw.client.utils.ClientUtils
import net.aspw.client.utils.login.LoginUtils
import net.aspw.client.utils.login.UserUtils.isValidTokenOffline
import net.aspw.client.utils.misc.MiscUtils
import net.aspw.client.utils.misc.RandomUtils
import net.aspw.client.visual.client.altmanager.menus.GuiLoginIntoAccount
import net.aspw.client.visual.client.altmanager.menus.GuiTheAltening
import net.aspw.client.visual.font.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import net.minecraft.client.gui.GuiTextField
import net.minecraft.util.Session
import org.lwjgl.input.Keyboard
import java.awt.Color
import java.net.Proxy
import java.util.*
import kotlin.concurrent.thread


class GuiAltManager(private val prevGui: GuiScreen) : GuiScreen() {

    var status = "§7Waiting..."

    private lateinit var loginButton: GuiButton
    private lateinit var randomButton: GuiButton
    private lateinit var randomCracked: GuiButton
    private lateinit var altsList: GuiList
    private lateinit var searchField: GuiTextField

    var lastSessionToken: String? = null

    override fun initGui() {
        val textFieldWidth = (width / 8).coerceAtLeast(70)
        searchField = GuiTextField(2, Fonts.fontSFUI40, width - textFieldWidth - 10, 10, textFieldWidth, 20)
        searchField.maxStringLength = Int.MAX_VALUE

        altsList = GuiList(this)
        altsList.registerScrollButtons(7, 8)

        val mightBeTheCurrentAccount =
            fileManager.accountsConfig.accounts.indexOfFirst { it.name == mc.session.username }
        altsList.elementClicked(mightBeTheCurrentAccount, false, 0, 0)
        altsList.scrollBy(mightBeTheCurrentAccount * altsList.getSlotHeight())

        // Setup buttons

        val startPositionY = 22
        buttonList.add(GuiButton(1, width - 80, startPositionY + 24, 70, 20, "Add"))
        buttonList.add(GuiButton(2, width - 80, startPositionY + 24 * 2, 70, 20, "Delete"))
        buttonList.add(GuiButton(7, width - 80, startPositionY + 24 * 3, 70, 20, "Import"))
        buttonList.add(GuiButton(12, width - 80, startPositionY + 24 * 4, 70, 20, "Export"))
        buttonList.add(GuiButton(5, width - 80, startPositionY + 24 * 5, 70, 20, "Free Alt"))
        buttonList.add(GuiButton(0, width - 80, height - 65, 70, 20, "Done"))
        buttonList.add(GuiButton(3, 5, startPositionY + 24, 90, 20, "Login").also { loginButton = it })
        buttonList.add(GuiButton(4, 5, startPositionY + 24 * 2, 90, 20, "Random Alt").also { randomButton = it })
        buttonList.add(GuiButton(99, 5, startPositionY + 24 * 3, 90, 20, "Random Cracked").also { randomCracked = it })
        buttonList.add(GuiButton(6, 5, startPositionY + 24 * 4, 90, 20, "Direct Login"))

        if (activeGenerators.getOrDefault("thealtening", true))
            buttonList.add(GuiButton(9, 5, startPositionY + 24 * 5, 90, 20, "The Altening"))
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
        altsList.drawScreen(mouseX, mouseY, partialTicks)
        Fonts.fontSFUI40.drawCenteredString("Alt Manager", width / 2.0f, 6f, 0xffffff)
        Fonts.fontSFUI35.drawCenteredString(
            if (searchField.text.isEmpty()) "${fileManager.accountsConfig.accounts.size} Alts" else altsList.accounts.size.toString() + " Search Results",
            width / 2.0f,
            18f,
            0xffffff
        )
        Fonts.fontSFUI35.drawCenteredString(status, width / 2.0f, 32f, 0xffffff)
        Fonts.fontSFUI35.drawStringWithShadow(
            "§7Ign: §a${mc.getSession().username}",
            6f,
            6f,
            0xffffff
        )
        Fonts.fontSFUI35.drawStringWithShadow(
            "§7Type: §a${
                if (altService.currentService == AltService.EnumAltService.THEALTENING) "TheAltening" else if (isValidTokenOffline(
                        mc.getSession().token
                    )
                ) "Microsoft" else "Cracked"
            }", 6f, 15f, 0xffffff
        )
        searchField.drawTextBox()
        if (searchField.text.isEmpty() && !searchField.isFocused) Fonts.fontSFUI40.drawStringWithShadow(
            "§7Search",
            (searchField.xPosition + 4).toFloat(),
            17f,
            0xffffff
        )
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    public override fun actionPerformed(button: GuiButton) {
        // Not enabled buttons should be ignored
        if (!button.enabled)
            return

        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> mc.displayGuiScreen(GuiLoginIntoAccount(this))
            2 -> {
                status = if (altsList.selectedSlot != -1 && altsList.selectedSlot < altsList.size) {
                    fileManager.accountsConfig.removeAccount(altsList.accounts[altsList.selectedSlot])
                    fileManager.saveConfig(fileManager.accountsConfig)
                    if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                        Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                    }
                    "§aThe account has been deleted."
                } else {
                    "§cSelect an account."
                }
            }

            3 -> {
                if (lastSessionToken == null)
                    lastSessionToken = mc.session.token

                status = altsList.selectedAccount?.let {
                    loginButton.enabled = false
                    randomButton.enabled = false
                    randomCracked.enabled = false

                    login(it, {
                        if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                            Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                        }
                        status = "§aLogged successfully to ${mc.session.username}."
                    }, { exception ->
                        if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                            Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                        }
                        status = "§cLogin failed to '${exception.message}'."
                    }, {
                        loginButton.enabled = true
                        randomButton.enabled = true
                        randomCracked.enabled = true
                    })

                    if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                        Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                    }
                    "§aLogging in..."
                } ?: "§cSelect an account."
            }

            4 -> {
                if (lastSessionToken == null)
                    lastSessionToken = mc.session.token

                status = altsList.accounts.randomOrNull()?.let {
                    loginButton.enabled = false
                    randomButton.enabled = false
                    randomCracked.enabled = false

                    login(it, {
                        if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                            Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                        }
                        status = "§aLogged successfully to ${mc.session.username}."
                    }, { exception ->
                        status = "§cLogin failed to '${exception.message}'."
                    }, {
                        loginButton.enabled = true
                        randomButton.enabled = true
                        randomCracked.enabled = true
                    })

                    if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                        Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                    }
                    "§aLogging in..."
                } ?: "§cYou do not have any accounts."
            }

            5 -> {
                if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                    Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                }
                val altening = TheAltening("api-qvo1-22iq-bt80")
                val asynchronous = TheAltening.Asynchronous(altening)
                asynchronous.accountData.thenAccept { account ->
                    if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                        Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                    }
                    try {
                        altService.switchService(AltService.EnumAltService.THEALTENING)
                        if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                            Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                        }
                        status = "§aLogged successfully to ${account.username}."
                        val yggdrasilUserAuthentication =
                            YggdrasilUserAuthentication(
                                YggdrasilAuthenticationService(Proxy.NO_PROXY, ""),
                                Agent.MINECRAFT
                            )
                        yggdrasilUserAuthentication.setUsername(account.token)
                        yggdrasilUserAuthentication.setPassword(Client.CLIENT_BEST)
                        yggdrasilUserAuthentication.logIn()
                        mc.session = Session(
                            yggdrasilUserAuthentication.selectedProfile.name, yggdrasilUserAuthentication
                                .selectedProfile.id.toString(),
                            yggdrasilUserAuthentication.authenticatedToken, "mojang"
                        )
                        Client.eventManager.callEvent(SessionEvent())
                    } catch (e: AuthenticationException) {
                        altService.switchService(AltService.EnumAltService.MOJANG)
                    }
                }
            }

            99 -> {
                if (lastSessionToken == null)
                    lastSessionToken = mc.session.token

                loginButton.enabled = false
                randomButton.enabled = false
                randomCracked.enabled = false

                val rand = CrackedAccount()
                rand.name = RandomUtils.randomString(RandomUtils.nextInt(5, 16))

                status = "§aGenerating..."

                login(rand, {
                    if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                        Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                    }
                    status = "§aLogged successfully to ${mc.session.username}."
                }, { exception ->
                    status = "§cLogin failed to '${exception.message}'."
                }, {
                    loginButton.enabled = true
                    randomButton.enabled = true
                    randomCracked.enabled = true
                })
            }

            6 -> { // Direct login button
                mc.displayGuiScreen(GuiLoginIntoAccount(this, directLogin = true))
            }

            7 -> { // Import button
                val file = MiscUtils.openFileChooser() ?: return

                file.readLines().forEach {
                    val accountData = it.split(":".toRegex(), limit = 2)
                    if (accountData.size > 1) {
                        // Most likely mojang account
                        fileManager.accountsConfig.addMojangAccount(accountData[0], accountData[1])
                    } else if (accountData[0].length < 16) {
                        // Might be cracked account
                        fileManager.accountsConfig.addCrackedAccount(accountData[0])
                    } // skip account
                }

                fileManager.saveConfig(fileManager.accountsConfig)
                status = "§aThe accounts were imported successfully."
            }

            12 -> { // Export button
                if (fileManager.accountsConfig.accounts.isEmpty()) {
                    status = "§cYou do not have any accounts to export."
                    return
                }

                val file = MiscUtils.saveFileChooser()
                if (file == null || file.isDirectory) {
                    return
                }

                try {
                    if (!file.exists()) {
                        file.createNewFile()
                    }

                    val accounts = fileManager.accountsConfig.accounts.joinToString(separator = "\n") { account ->
                        when (account) {
                            is MojangAccount -> "${account.email}:${account.password}" // EMAIL:PASSWORD
                            is MicrosoftAccount -> "${account.name}:${account.session.token}" // NAME:SESSION
                            else -> account.name
                        }
                    }
                    file.writeText(accounts)

                    status = "§aExported successfully!"
                } catch (e: Exception) {
                    status = "§cUnable to export due to error: ${e.message}"
                }
            }

            9 -> { // Altening Button
                mc.displayGuiScreen(GuiTheAltening(this))
            }

            727 -> {
                loginButton.enabled = false
                randomButton.enabled = false
                randomCracked.enabled = false
                if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                    Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                }
                status = "§aLogging in..."

                thread {
                    val loginResult = LoginUtils.loginSessionId(lastSessionToken!!)

                    status = when (loginResult) {
                        LoginUtils.LoginResult.LOGGED -> {
                            if (altService.currentService != AltService.EnumAltService.MOJANG) {
                                try {
                                    altService.switchService(AltService.EnumAltService.MOJANG)
                                } catch (e: NoSuchFieldException) {
                                    ClientUtils.getLogger()
                                        .error("Something went wrong while trying to switch alt service.", e)
                                } catch (e: IllegalAccessException) {
                                    ClientUtils.getLogger()
                                        .error("Something went wrong while trying to switch alt service.", e)
                                }
                            }

                            "§cYour name is now §f§l${mc.session.username}§c"
                        }

                        LoginUtils.LoginResult.FAILED_PARSE_TOKEN -> "§cFailed to parse Session ID!"
                        LoginUtils.LoginResult.INVALID_ACCOUNT_DATA -> "§cInvalid Session ID!"
                        else -> ""
                    }

                    loginButton.enabled = true
                    randomButton.enabled = true
                    randomCracked.enabled = true

                    lastSessionToken = null
                }
            }
        }
    }

    public override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (searchField.isFocused) {
            searchField.textboxKeyTyped(typedChar, keyCode)
        }

        when (keyCode) {
            Keyboard.KEY_ESCAPE -> { // Go back
                mc.displayGuiScreen(prevGui)
                return
            }

            Keyboard.KEY_UP -> { // Go one up in account list
                var i = altsList.selectedSlot - 1
                if (i < 0) i = 0
                altsList.elementClicked(i, false, 0, 0)
            }

            Keyboard.KEY_DOWN -> { // Go one down in account list
                var i = altsList.selectedSlot + 1
                if (i >= altsList.size) i = altsList.size - 1
                altsList.elementClicked(i, false, 0, 0)
            }

            Keyboard.KEY_RETURN -> { // Login into account
                altsList.elementClicked(altsList.selectedSlot, true, 0, 0)
            }

            Keyboard.KEY_NEXT -> { // Scroll account list
                altsList.scrollBy(height - 100)
            }

            Keyboard.KEY_PRIOR -> { // Scroll account list
                altsList.scrollBy(-height + 100)
                return
            }
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        altsList.handleMouseInput()
    }

    public override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        searchField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun updateScreen() {
        searchField.updateCursorCounter()
    }

    private inner class GuiList constructor(prevGui: GuiScreen) :
        GuiSlot(mc, prevGui.width, prevGui.height, 40, prevGui.height - 40, 30) {

        val accounts: List<MinecraftAccount>
            get() {
                var search = searchField.text
                if (search == null || search.isEmpty()) {
                    return fileManager.accountsConfig.accounts
                }
                search = search.lowercase(Locale.getDefault())

                return fileManager.accountsConfig.accounts.filter {
                    it.name.contains(
                        search,
                        ignoreCase = true
                    ) || (it is MojangAccount && it.email.contains(search, ignoreCase = true))
                }
            }

        var selectedSlot = 0
            get() {
                return if (field > accounts.size) {
                    -1
                } else {
                    field
                }
            }

        val selectedAccount: MinecraftAccount?
            get() = if (selectedSlot >= 0 && selectedSlot < accounts.size) {
                accounts[selectedSlot]
            } else {
                null
            }

        override fun isSelected(id: Int) = selectedSlot == id

        public override fun getSize() = accounts.size

        public override fun elementClicked(clickedElement: Int, doubleClick: Boolean, var3: Int, var4: Int) {
            selectedSlot = clickedElement

            if (doubleClick) {
                status = altsList.selectedAccount?.let {
                    loginButton.enabled = false
                    randomButton.enabled = false
                    randomCracked.enabled = false

                    login(it, {
                        if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                            Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                        }
                        status = "§aLogged successfully to ${mc.session.username}."
                    }, { exception ->
                        status = "§cLogin failed to '${exception.message}'."
                    }, {
                        loginButton.enabled = true
                        randomButton.enabled = true
                        randomCracked.enabled = true
                    })

                    if (Client.moduleManager.getModule(Hud::class.java)?.flagSoundValue!!.get()) {
                        Client.tipSoundManager.popSound.asyncPlay(Client.moduleManager.popSoundPower)
                    }
                    "§aLogging in..."
                } ?: "§cSelect an account."
            }
        }

        override fun drawSlot(id: Int, x: Int, y: Int, var4: Int, var5: Int, var6: Int) {
            val minecraftAccount = accounts[id]
            val accountName = if (minecraftAccount is MojangAccount && minecraftAccount.name.isEmpty()) {
                minecraftAccount.email
            } else {
                minecraftAccount.name
            }

            Fonts.fontSFUI40.drawCenteredString(accountName, width / 2f, y + 2f, Color.WHITE.rgb, true)
            Fonts.fontSFUI40.drawCenteredString(
                if (minecraftAccount is CrackedAccount) "Cracked" else if (minecraftAccount is MicrosoftAccount) "Microsoft" else if (minecraftAccount is MojangAccount) "Mojang" else "Something else",
                width / 2f,
                y + 15f,
                if (minecraftAccount is CrackedAccount) Color.GRAY.rgb else Color(118, 255, 95).rgb,
                true
            )
        }

        override fun drawBackground() {}
    }

    companion object {

        val altService = AltService()
        private val activeGenerators = mutableMapOf<String, Boolean>()

        fun login(
            minecraftAccount: MinecraftAccount,
            success: () -> Unit,
            error: (Exception) -> Unit,
            done: () -> Unit
        ) = thread(name = "LoginTask") {
            if (altService.currentService != AltService.EnumAltService.MOJANG) {
                try {
                    altService.switchService(AltService.EnumAltService.MOJANG)
                } catch (e: NoSuchFieldException) {
                    error(e)
                    ClientUtils.getLogger().error("Something went wrong while trying to switch alt service.", e)
                } catch (e: IllegalAccessException) {
                    error(e)
                    ClientUtils.getLogger().error("Something went wrong while trying to switch alt service.", e)
                }
            }

            try {
                minecraftAccount.update()
                Minecraft.getMinecraft().session = Session(
                    minecraftAccount.session.username,
                    minecraftAccount.session.uuid, minecraftAccount.session.token, "mojang"
                )
                Client.eventManager.callEvent(SessionEvent())

                success()
            } catch (exception: Exception) {
                error(exception)
            }
            done()
        }
    }
}