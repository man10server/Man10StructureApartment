package red.man10.man10structureapartment

import com.google.gson.Gson
import org.bukkit.*
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.structure.Structure
import org.bukkit.structure.StructureManager
import red.man10.man10structureapartment.Man10StructureApartment.Companion.instance
import red.man10.man10structureapartment.Man10StructureApartment.Companion.msg
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min


object StructureManager {

    private const val POS_Y = 100.0
    private const val POS_Z = 0.0

    private var distance = 64
    private var maxApartCount = 128
    var dailyRent = 1000.0
    private lateinit var world: World
    private var backCommand = ""

    private var addressMap = ConcurrentHashMap<UUID,ApartData>()
    private val livingList = mutableListOf<UUID>()
    private lateinit var manager : StructureManager
    private lateinit var defaultBuilding : Structure
    private lateinit var vault : VaultManager
    private lateinit var jumpOffset : Triple<Double,Double,Double>

    val thread = Executors.newSingleThreadExecutor()

    fun pluginLoad(){
        manager = instance.server.structureManager
        vault = VaultManager(instance)

        instance.saveDefaultConfig()
        instance.reloadConfig()

        distance = instance.config.getInt("Distance")
        maxApartCount = instance.config.getInt("MaxApartCount")
        dailyRent = instance.config.getDouble("DailyRent")
        world = instance.server.getWorld(instance.config.getString("BuilderWorld")?:"world")!!
        backCommand = instance.config.getString("BackCommand")?:""

        val relativeX = instance.config.getDouble("RelativeX")
        val relativeY = instance.config.getDouble("RelativeY")
        val relativeZ = instance.config.getDouble("RelativeZ")

        jumpOffset = Triple(relativeX,relativeY,relativeZ)

        loadDefault()
        loadAddress()
    }

    fun pluginClose(){

        saveAddress()
        addressMap.values.forEach {
            saveStructure(it.owner)
        }
        //キューに積まれた保存処理が終わるのを待つ
        thread.shutdown()
        try {
            if (!thread.awaitTermination(30, TimeUnit.SECONDS)){
                Bukkit.getLogger().warning("保存処理が時間内に終わりませんでした")
                thread.shutdownNow()
            }
        }catch (e:InterruptedException){
            thread.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    //初期建築の読み込み
    private fun loadDefault(){
        val default = File("${instance.dataFolder.path}/Apart/Default")
        if (!default.exists()){
            Bukkit.getLogger().warning("初期建築がありません")
            return
        }
        defaultBuilding = manager.loadStructure(default)
        Bukkit.getLogger().info("初期建築を読み込みました")
    }

    //住所情報をロード
    private fun loadAddress(){

        addressMap.clear()

        val file = File("${instance.dataFolder.path}/Address.json")

        if (!file.exists()){
            Bukkit.getLogger().warning("住所情報がありません")
            return
        }

        val reader = FileReader(file)

        val array = Gson().fromJson(reader.readText(),Array<ApartData>::class.java)

        array.forEach { addressMap[it.owner] = it }

        reader.close()
    }

    private fun saveAddress(){

        val jsonStr = Gson().toJson(addressMap.values.toTypedArray())

        val file = File("${instance.dataFolder.path}/Address.json")

        val writer = FileWriter(file)

        writer.write(jsonStr)

        writer.close()
    }

    private fun updateAddress(data: ApartData){
        //住所が重複している部分を削除
        addressMap.filterValues { it.sx == data.sx}.forEach { addressMap.remove(it.key) }
        //保存
        addressMap[data.owner] = data
        saveAddress()
    }

    //  ストラクチャーの保存(必ずメインスレッドで呼び出す)
    fun saveStructure(uuid:UUID,retry:Boolean = true,op:Boolean = false){

        val data = addressMap[uuid]?:return

        val structure = manager.createStructure()

        val pos1 = Location(world,data.sx,data.sy,data.sz)
        val pos2 = Location(world,data.ex,data.ey,data.ez)

        structure.fill(pos1,pos2,true)
        structure.persistentDataContainer.set(NamespacedKey(instance,"RentDue"), PersistentDataType.LONG,data.rentDue.time)

        //コマンドによる呼び出しの時は住所情報から消す
        if (op){
            addressMap.remove(uuid)
            saveAddress()
        }

        thread.execute {

            try {
                val file = File("${instance.dataFolder.path}/Apart/${uuid}")
                val destination = File("${instance.dataFolder.path}/Apart/backup/${uuid}/${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(Date())}")
                if (file.exists() && !destination.exists()){
                    file.copyTo(destination)
                }

                manager.saveStructure(file,structure)

//                if (!file.exists()){
//                    manager.saveStructure(file,structure)
//                }else{
//                    manager.saveStructure(file,structure)
//                }
            }catch (e:Exception){
                val p = Bukkit.getOfflinePlayer(uuid).name
                Bukkit.getLogger().warning("アパートの保存に失敗！(持ち主:$p)")
                Bukkit.getLogger().warning(e.message)
                if (retry){
                    Bukkit.getScheduler().runTask(instance, Runnable {
                        Bukkit.getLogger().info("保存のやり直しを試みます")
                        saveStructure(uuid,false)
                    })
                    return@execute
                }
            }

            Bukkit.getLogger().info("保存完了")
        }
    }

    //  ストラクチャーの呼び出し
    //  ストラクチャーが生成できたらtrueを返す
    @Synchronized
    fun placeStructure(uuid:UUID,location: Location? = null):Boolean{

        //アドレスがすでにある場合は建物があるとしてリターン
//        if (addressMap[uuid]!=null){
//            return true
//        }

        var posX = -1.0
        //利用中の部屋のリスト
        val filtered = addressMap.values.filter { Bukkit.getOfflinePlayer(it.owner).isOnline }

        Bukkit.getLogger().info("現在:${filtered.size}人が利用中")

        if (filtered.size >= maxApartCount){
            Bukkit.getLogger().info("定員オーバー")
            return false
        }

        //順番に探っていって、空き部屋を決定
        for (i in 0 until maxApartCount){
            if (filtered.any { it.sx == (i * distance).toDouble() })continue
            posX = (i * distance).toDouble()
            break
        }

        if (posX == -1.0){
            Bukkit.getLogger().info("アパートの確保に失敗")
            return false
        }

        Bukkit.getLogger().info("アパートの確保に成功:sx=${posX}")

        //座標を設定
        val pos1 = location?:Location(world,posX, POS_Y, POS_Z)


        val pos2 = pos1.clone()

        val file = File("${instance.dataFolder.path}/Apart/${uuid}")

        val structure = if (file.exists()){
            //呼び出し前にバックアップをとっておく
            val destination = File("${instance.dataFolder.path}/Apart/backup/${uuid}/${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(Date(file.lastModified()))}")
            if (!destination.exists())file.copyTo(destination)
            manager.loadStructure(file)
        } else {
            val default = File("${instance.dataFolder.path}/Apart/Default")
            if (!default.exists()){
                return false
            }
            manager.loadStructure(default)
        }

        pos2.x+=structure.size.x
        pos2.y+=structure.size.y
        pos2.z+=structure.size.z

        Bukkit.getScheduler().runTask(instance, Runnable { place(pos1, pos2, structure) })

        val date = Date()
        date.time = structure.persistentDataContainer[NamespacedKey(instance,"RentDue"), PersistentDataType.LONG]?:Date().time

        //住所情報をJsonファイルに登録
        updateAddress(ApartData(uuid, pos1.x,pos1.y,pos1.z, pos2.x,pos2.y,pos2.z, Date(),date))

        Bukkit.getLogger().info("アパートの設置完了(現在のアパート数:${addressMap.size}")

        return true
    }

    private fun place(pos1:Location,pos2:Location,structure: Structure){
        val world = pos1.world

        val minX = min(pos1.blockX,pos2.blockX)
        val minY = min(pos1.blockY,pos2.blockY)
        val minZ = min(pos1.blockZ,pos2.blockZ)
        val maxX = max(pos1.blockX,pos2.blockX)
        val maxY = max(pos1.blockY,pos2.blockY)
        val maxZ = max(pos1.blockZ,pos2.blockZ)

        //Blockを削除
        for (x in minX..maxX){
            for (y in minY..maxY){
                for (z in minZ..maxZ){
                    world.getBlockAt(x,y,z).type = Material.AIR
                }
            }
        }

        Bukkit.getLogger().info("ブロックを削除")

        structure.place(pos1,true,StructureRotation.NONE,Mirror.NONE,0,1F, Random())

        Bukkit.getLogger().info("設置")

        //エンティティを削除
        for (e in world.entities){
            if (e.type == EntityType.PLAYER)continue
            val loc = e.location
            if (loc.blockX in minX..maxX && loc.blockY in minY..maxY && loc.blockZ in minZ .. maxZ){
                e.remove()
            }
        }
        Bukkit.getLogger().info("エンティティを削除")
    }

    fun addPayment(p:Player,day:Int){

        if (day <= 0){
            msg(p,"§c日数は1以上を指定してください")
            return
        }

        val data = addressMap[p.uniqueId]

        if (data==null){
            thread.execute {
                if (!placeStructure(p.uniqueId)){
                    p.sendMessage("§c現在アパートは満室です")
                }else{
                    p.sendMessage("§aアパートを確保しました。もう一度クリックしてください")
                }
            }
            return
        }

        if (!vault.withdraw(p.uniqueId,day* dailyRent)){
            msg(p,"§c電子マネーが足りません")
            return
        }

        var date = LocalDateTime.ofInstant(data.rentDue.toInstant(), ZoneId.systemDefault())
        if (date.isBefore(LocalDateTime.now())) {
            date = LocalDateTime.now()
        }
        date = date.plusDays(day.toLong())
        data.rentDue = Date.from(date.toInstant(ZoneOffset.of("+9")))

        addressMap[p.uniqueId] = data
        saveAddress()
        saveStructure(p.uniqueId)

        msg(p,"§e§l利用料の支払いを行いました(利用可能期間:${SimpleDateFormat("MM月dd日").format(data.rentDue)}まで)")
    }

    fun jump(p:Player){

        //マンションにいたら戻る
        if (livingList.contains(p.uniqueId)){
            exit(p)
            p.performCommand(backCommand)
            return
        }

        val data = addressMap[p.uniqueId]

        if (data == null){
            thread.execute {
                if (!placeStructure(p.uniqueId)){
                    msg(p,"§c現在アパートは満室です")
                }else{
                    msg(p,"§c§lもう一度クリックしてください")
                }
            }
            return
        }

        if (Date().after(data.rentDue)){
            msg(p,"§c§l利用料の支払いがされていません！")
            return
        }

        val pos1 = Location(world,data.sx,data.sy,data.sz)

        val spawnX = pos1.x+ jumpOffset.first
        val spawnY = pos1.y+ jumpOffset.second
        val spawnZ = pos1.z+ jumpOffset.third

        val loc = Location(pos1.world,spawnX,spawnY,spawnZ)

        p.teleport(loc)
        enter(p)
        msg(p,"§a§lマンションにジャンプしました")
        msg(p,"§a§l戻る時は§n§lドアを右クリック§a§lしてください")
    }

    fun getBackupList(uuid: UUID): List<File> {

        val folder = File("${instance.dataFolder.path}/Apart/backup/${uuid}").listFiles()
        if (folder.isNullOrEmpty()) return emptyList()

        return folder.sortedByDescending { it.lastModified() }
    }

    fun restore(uuid: UUID, index:Int): Boolean {
        val files = getBackupList(uuid)
        if (files.size<=index)return false
        val data = files[index]

        val file = File("${instance.dataFolder.path}/Apart/${uuid}")
        if (file.exists())file.delete()
        data.copyTo(file)
        addressMap.remove(uuid)
        saveAddress()
        return true
    }

    fun enter(p:Player){
        livingList.add(p.uniqueId)
    }

    fun exit(p:Player){
        livingList.remove(p.uniqueId)
    }
}

data class ApartData(
    val owner : UUID,
    val sx : Double,
    val sy : Double,
    val sz : Double,
    val ex : Double,
    val ey : Double,
    val ez : Double,
    val lastAccess : Date,
    var rentDue : Date = Date()
)