package red.man10.man10structureapartment

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.*


/**
 * Created by takatronix on 2017/03/04.
 */
class VaultManager(private val plugin: JavaPlugin) {
    private fun setupEconomy(): Boolean {
        Bukkit.getLogger().info("setupEconomy")
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            Bukkit.getLogger().info("Vault plugin is not installed")
            return false
        }
        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            Bukkit.getLogger().info("Can't get vault service")
            return false
        }
        economy = rsp.provider
        Bukkit.getLogger().info("Economy setup")
        return economy != null
    }

    /////////////////////////////////////
    //      残高確認
    /////////////////////////////////////
    fun getBalance(uuid: UUID?): Double {

        if (uuid == null)return 0.0
        val eco = economy ?: run {
            Bukkit.getLogger().warning("Economy is not available (Vault plugin is not installed?)")
            return 0.0
        }
        return eco.getBalance(Bukkit.getOfflinePlayer(uuid))
    }

    /////////////////////////////////////
    //      引き出し
    /////////////////////////////////////
    fun withdraw(uuid: UUID, money: Double): Boolean {
        val eco = economy ?: run {
            Bukkit.getLogger().warning("Economy is not available (Vault plugin is not installed?)")
            return false
        }
        val p = Bukkit.getOfflinePlayer(uuid)
        val resp = eco.withdrawPlayer(p, money)
        if (resp.transactionSuccess()) {
            if (p.isOnline) {
//                Utility.msg(p.player!!,"§e§l電子マネーを${Utility.format(money)}円支払いました")
            }
            return true
        }
        return false
    }

    /////////////////////////////////////
    //      お金を入れる
    /////////////////////////////////////
    fun deposit(uuid: UUID, money: Double): Boolean {
        val eco = economy ?: run {
            Bukkit.getLogger().warning("Economy is not available (Vault plugin is not installed?)")
            return false
        }
        val p = Bukkit.getOfflinePlayer(uuid)
        val resp = eco.depositPlayer(p, money)
        if (resp.transactionSuccess()) {
            if (p.isOnline) {
//                Utility.msg(p.player!!,"§e§l電子マネーを${Utility.format(money)}円受け取りました")
            }
            return true
        }
        return false
    }

    companion object {
        var economy: Economy? = null
    }

    init {
        setupEconomy()
    }
}