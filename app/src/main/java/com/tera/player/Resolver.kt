package com.tera.player

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Resolver {
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private val BASE = linkedMapOf<String, Any>("app_id" to 250528, "web" to 1, "channel" to "dubox", "clienttype" to 0)

    private val HOSTS = listOf("www.1024terabox.com", "1024terabox.com", "www.terabox.com")
    private var host = HOSTS[0]

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun tb(path: String, extra: Map<String, Any>, ndus: String, h: String = host): JSONObject? {
        val q = (BASE + extra).entries.joinToString("&") { enc(it.key) + "=" + enc(it.value.toString()) }
        return try {
            val c = URL("https://$h$path?$q").openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Cookie", "ndus=$ndus")
            c.setRequestProperty("Referer", "https://$h/")
            c.setRequestProperty("Accept", "application/json, text/plain, */*")
            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
            JSONObject(body)
        } catch (e: Exception) { null }
    }

    fun resolve(link: String, ndus: String): JSONObject {
        val linkHost = try { URL(link).host } catch (e: Exception) { "" }
        if (!Regex("(tera|4funbox|mirrobox|nephobox|momerybox|dubox|tibibox)", RegexOption.IGNORE_CASE).containsMatchIn(linkHost))
            throw Exception("Ye Terabox link nahi lag raha")
        val surl = Regex("/s/([\\w-]+)").find(link)?.groupValues?.get(1)
            ?: Regex("[?&]surl=([\\w-]+)").find(link)?.groupValues?.get(1)?.let { "1$it" }
            ?: throw Exception("Link se share ID nahi mili")

        var info: JSONObject? = null
        val notes = ArrayList<String>()
        for (h in HOSTS) {
            val r = tb("/api/shorturlinfo", mapOf("shorturl" to surl, "root" to 1), ndus, h)
            if (r != null && r.optInt("errno", -1) == 0) { info = r; host = h; break }
            notes.add(h + ": " + (if (r == null) "jawab nahi aaya" else "errno " + r.opt("errno")))
        }
        if (info == null)
            throw Exception("Terabox se info nahi mili.\n" + notes.joinToString("\n"))
        val shareid = info.opt("shareid")?.toString() ?: ""
        val uk = info.opt("uk")?.toString() ?: ""
        val sign = info.optString("sign")
        val ts = info.opt("timestamp")?.toString() ?: ""

        val l = tb("/share/list", mapOf("shorturl" to surl, "root" to 1, "page" to 1, "num" to 100), ndus)
        val items: JSONArray = l?.optJSONArray("list")?.takeIf { it.length() > 0 } ?: info.optJSONArray("list") ?: JSONArray()

        val all = ArrayList<JSONObject>()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            if (it.optInt("isdir", 0) == 1 && shareid.isNotEmpty()) {
                val sub = tb("/share/list", mapOf("shareid" to shareid, "uk" to uk, "dir" to it.optString("path"),
                    "page" to 1, "num" to 100, "root" to 0), ndus)?.optJSONArray("list")
                if (sub != null) for (j in 0 until sub.length()) all.add(sub.getJSONObject(j))
            } else all.add(it)
        }

        val files = JSONArray()
        for (it in all) {
            val name = it.optString("server_filename")
            val isVideo = it.optInt("category", 0) == 1 || Regex("\\.(mp4|mkv|mov|avi|webm|m4v|ts)$", RegexOption.IGNORE_CASE).containsMatchIn(name)
            val fsid = it.opt("fs_id")?.toString() ?: ""
            if (!isVideo || fsid.isEmpty()) continue
            val hls = "https://$host/share/streaming?" + linkedMapOf<String, Any>(
                "app_id" to 250528, "channel" to "dubox", "clienttype" to 0, "uk" to uk, "fid" to fsid,
                "sign" to sign, "timestamp" to ts, "shareid" to shareid, "type" to "M3U8_AUTO_720", "vip" to 0
            ).entries.joinToString("&") { enc(it.key) + "=" + enc(it.value.toString()) }
            files.put(JSONObject().put("name", name).put("mp4", it.optString("dlink")).put("hls", hls))
        }
        if (files.length() == 0) throw Exception("Is link me koi video nahi mili")
        return JSONObject().put("files", files).put("headers", JSONObject()
            .put("User-Agent", UA).put("Cookie", "ndus=$ndus").put("Referer", "https://$host/"))
    }
}
