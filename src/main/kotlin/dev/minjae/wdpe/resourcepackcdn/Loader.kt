package dev.minjae.wdpe.resourcepackcdn

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.waterdog.waterdogpe.event.defaults.ResourcePacksRebuildEvent
import dev.waterdog.waterdogpe.plugin.Plugin
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.brotli.BrotliInterceptor
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket.CDNEntry

class Loader : Plugin() {

    private val jsonMapper = jsonMapper()
        .registerKotlinModule()

    private val ip: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        config.getString("ip", "127.0.0.1")
    }

    private val port: Int by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        config.getInt("port", 36332)
    }

    private val javalin: Javalin by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Javalin.create { config ->
            config.http.defaultContentType = "application/gzip"
            config.http.gzipOnlyCompression()
            config.router.caseInsensitiveRoutes = true
            config.staticFiles.add(dataFolder.resolve(".cache").apply {
                if (!exists()) {
                    mkdirs()
                }
            }.toString(), Location.EXTERNAL)
        }
    }

    private val dev: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        config.getBoolean("dev", false)
    }

    override fun onEnable() {
        saveResource("config.yml")
        javalin.start(port)
        proxy.eventManager.subscribe(ResourcePacksRebuildEvent::class.java, ::onResourcePacksRebuild)
    }

    override fun onDisable() {
        javalin.stop()
    }

    private fun onResourcePacksRebuild(event: ResourcePacksRebuildEvent) {
        val packs = proxy.packManager.packs
        val cdnEntries = mutableListOf<CDNEntry>()
        val proceed = mutableListOf<String>()
        val rebuilt = mutableListOf<String>()
        val cacheDir = dataFolder.resolve(".cache").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        for ((uuid, pack) in packs) {
            val cache = cacheDir.resolve("$uuid.cache")
            val destination = cacheDir.resolve("$uuid.zip")
            cdnEntries.add(
                CDNEntry(
                    "${uuid}_${pack.version}",
                    "http://${if (dev) "127.0.0.1" else ip}:$port/$uuid.zip"
                )
            )
            proceed.add(uuid.toString())
            if (cache.exists() && destination.exists()) {
                val content = cache.readBytes().toString(StandardCharsets.UTF_8)
                if (getFileHash(pack.packPath.toFile()) == content) {
                    continue
                }
                destination.delete()
                cache.delete()
            }
            rebuildPack(pack.packPath, destination.toPath())
            rebuilt.add(uuid.toString())
            cache.writeText(getFileHash(destination))
        }
        for (file in cacheDir.listFiles { _, name -> name.endsWith(".cache") || name.endsWith(".zip") }!!) {
            if (file.nameWithoutExtension !in proceed) {
                file.delete()
            }
        }
        val packsInfo = proxy.packManager.packsInfoPacket
        packsInfo.cdnEntries = cdnEntries
        logger.info("${rebuilt.size}/${proceed.size} packs rebuilt")
    }

    private fun rebuildPack(oldPath: Path, newPath: Path) {
        val file = oldPath.toFile()
        if (!file.exists()) {
            return
        }

        val tempFile = Files.createTempFile("repacked", ".zip")
        ZipInputStream(file.inputStream()).use { zis ->
            ZipOutputStream(tempFile.toFile().outputStream()).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val fileNameWithoutExtension = file.name.substringBeforeLast('.')
                    val newEntry = ZipEntry("$fileNameWithoutExtension/${entry.name}")
                    zos.putNextEntry(newEntry)
                    zis.copyTo(zos)
                    zis.closeEntry()
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        Files.move(tempFile, newPath, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun getFileHash(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val inputStream = file.inputStream()

        val buffer = ByteArray(8192)
        var bytesRead = inputStream.read(buffer)

        while (bytesRead != -1) {
            md.update(buffer, 0, bytesRead)
            bytesRead = inputStream.read(buffer)
        }

        val hashBytes = md.digest()

        return hashBytes.joinToString(separator = "") { "%02x".format(it) }
    }
}