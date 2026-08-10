package red.man10.man10structureapartment

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10structureapartment.StructureManager.jump
import red.man10.man10structureapartment.StructureManager.pluginLoad
import red.man10.man10structureapartment.StructureManager.restore
import red.man10.man10structureapartment.StructureManager.saveStructure
import red.man10.man10structureapartment.StructureManager.thread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors

class Man10StructureApartment : JavaPlugin(),Listener {

    companion object{
        lateinit var instance : Man10StructureApartment

        private const val PERMISSION = "man10apart.op"

        fun msg(sender:Player?,str: String){
            sender?.sendMessage("§f§l[Cloud§7§lApartment]§f§l${str}")
        }
    }

    override fun onEnable() {
        // Plugin startup logic
        instance = this

        MenuFramework.setup(this)

        getCommand("msa")!!.setExecutor(this)
        server.pluginManager.registerEvents(this,this)
        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)

        pluginLoad()
    }

    override fun onDisable() {
        // Plugin shutdown logic
        StructureManager.pluginClose()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return true

        if (args.isEmpty()){

            MainMenu(sender).open()
            return true
        }


        when(args[0]){

            "reload" ->{
                if (!sender.hasPermission(PERMISSION))return true
                Thread{
                    pluginLoad()
                }.start()
            }

            "place" ->{
                if (!sender.hasPermission(PERMISSION))return true

                val uuid = UUID.fromString(args[1])

                thread.execute {
                    val ret = StructureManager.placeStructure(uuid,sender.location)
                    if (ret){
                        msg(sender,"設置完了しました。内容変更があった場合は/msa save ${uuid}を打ってください")
                    }
                }
            }

            "save" ->{
                if (!sender.hasPermission(PERMISSION))return true

                val uuid = UUID.fromString(args[1])

                saveStructure(uuid,op = true)

                msg(sender,"保存しました。建物は手動で削除してください。")
            }

            "backup" ->{
                if (!sender.hasPermission(PERMISSION))return true

                val uuid = UUID.fromString(args[1])

                thread.execute {
                    val list = StructureManager.getBackupList(uuid)

                    msg(sender,"直近20件のバックアップを表示")

                    for (i in 0 .. 19){
                        if (list.size<=i)break
                        val file = list[i]
                        val date = Date(file.lastModified())
                        sender.sendMessage(text(SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)).clickEvent(
                            ClickEvent.runCommand("/msa restore $uuid $i"))
                            .hoverEvent(HoverEvent.showText(text("復元する"))))
                    }
                }
            }

            "restore" ->{
                if (!sender.hasPermission(PERMISSION))return true

                val uuid = UUID.fromString(args[1])
                val index = args[2].toInt()

                msg(sender,"復元中")
                thread.execute {
                    val ret = restore(uuid,index)
                    msg(sender,"$ret")
                }
            }

            "pay" ->{
                val day = args[1].toInt()
                StructureManager.addPayment(sender,day)
            }

            "jump" ->{
                jump(sender)
            }

            else ->{
                if (!sender.hasPermission(PERMISSION))return true

                msg(sender,"/msa reload ")
                msg(sender,"/msa place <uuid> ")
                msg(sender,"/msa save <uuid> ")
                msg(sender,"/msa backup <uuid> ")
            }
        }

        return true
    }

    @EventHandler
    fun logout(e:PlayerQuitEvent){

        StructureManager.exit(e.player)
        saveStructure(e.player.uniqueId)
    }

    //ドアクリックで飛ぶ
    @EventHandler
    fun clickEvent(e:PlayerInteractEvent){

        //オフハンドのクリックに反応しないように
        if (e.hand != EquipmentSlot.HAND)return

        if (e.action != Action.RIGHT_CLICK_BLOCK)return

        if (e.clickedBlock?.type != Material.IRON_DOOR)return

        jump(e.player)
    }
}