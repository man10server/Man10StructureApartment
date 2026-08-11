package red.man10.man10structureapartment

import org.bukkit.Material
import org.bukkit.entity.Player

class MainMenu(p: Player) : MenuFramework(p, 9, "§b§l[CloudApartment]") {

    override fun init() {

        val fill = Button(Material.CYAN_STAINED_GLASS_PANE)
        fill.setClickAction{ it.isCancelled = true }

        fill(fill)

        val jumpButton = Button(Material.OAK_DOOR)
        jumpButton.title("§a§lマンションにテレポートする")
        jumpButton.lore(mutableListOf("§fマンションにテレポートします"
            ,"§e§l利用料が未払いの場合は30日分(${StructureManager.dailyRent*30}円)を支払います"
            ,"§c§l電子マネーが足りない場合はテレポートできません"))
        jumpButton.setClickAction{
            StructureManager.payAndJump(p,30)
        }

        setButton(jumpButton,4)
    }

}