package com.nabhas.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.SearchManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object AgentActionEngine {

    fun execute(aiResponse: String, context: Context): Boolean {
        val actions = extractActions(aiResponse)
        if (actions.isEmpty()) return false

        val appContext = context.applicationContext
        var allSucceeded = true

        for (action in actions) {
            if (!executeAction(action, appContext)) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private fun executeAction(action: JSONObject, context: Context): Boolean {
        val parameters = action.optJSONObject("parameters")
        val actionName = getValue(action, parameters, "action", "type")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace('-', '_')
            ?.replace(' ', '_')
            ?: return false

        return when (actionName) {
            "open_app", "openapp" -> {
                val appName = getValue(action, parameters, "app", "name", "package", "package_name", "query")
                    ?: return false
                openApp(context, appName)
            }
            "dial", "make_call", "call" -> {
                val number = getValue(action, parameters, "number", "phone", "phone_number")
                    ?: return false
                openDialer(context, number)
            }
            "open_settings", "settings" -> {
                openSettings(context, getValue(action, parameters, "setting", "category", "name"))
            }
            "web_search", "search_web", "search" -> {
                val query = getValue(action, parameters, "query", "search", "text")
                    ?: return false
                webSearch(context, query)
            }
            "click_text", "click" -> {
                val target = getValue(action, parameters, "target", "text", "value")
                    ?: return false
                NabhasAccessibilityService.getInstance()?.clickText(target) ?: false
            }
            "scroll" -> {
                val direction = getValue(action, parameters, "direction") ?: "down"
                val target = getValue(action, parameters, "target", "text").orEmpty()
                NabhasAccessibilityService.getInstance()?.scroll(direction, target) ?: false
            }
            "type_text", "type", "set_text" -> {
                val target = getValue(action, parameters, "target", "field").orEmpty()
                val text = getValue(action, parameters, "value", "content", "text")
                    ?: return false
                NabhasAccessibilityService.getInstance()?.setText(text, target) ?: false
            }
            "home" -> NabhasAccessibilityService.getInstance()?.goHome() ?: false
            "back" -> NabhasAccessibilityService.getInstance()?.goBack() ?: false
            else -> false
        }
    }

    private fun openApp(context: Context, appNameOrPackage: String): Boolean {
        val pm = context.packageManager
        val query = appNameOrPackage.trim()
        if (query.isEmpty()) return false

        pm.getLaunchIntentForPackage(query)?.let {
            return startActivitySafely(context, it)
        }

        val normalizedQuery = query.lowercase(Locale.ROOT)
        val apps = try {
            pm.getInstalledApplications(0)
        } catch (_: RuntimeException) {
            return false
        }

        val match = apps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app)?.toString()?.lowercase(Locale.ROOT).orEmpty()
            label == normalizedQuery || app.packageName.lowercase(Locale.ROOT) == normalizedQuery
        } ?: apps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app)?.toString()?.lowercase(Locale.ROOT).orEmpty()
            label.contains(normalizedQuery)
        } ?: return false

        val intent = pm.getLaunchIntentForPackage(match.packageName) ?: return false
        return startActivitySafely(context, intent)
    }

    private fun openDialer(context: Context, number: String): Boolean {
        val value = number.trim()
        if (value.isEmpty()) return false
        return startActivitySafely(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null)))
    }

    private fun openSettings(context: Context, requestedSetting: String?): Boolean {
        val setting = requestedSetting
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace('-', '_')
            ?.replace(' ', '_')
            .orEmpty()

        val action = when (setting) {
            "wifi", "wi_fi", "network", "internet" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "sound", "audio" -> Settings.ACTION_SOUND_SETTINGS
            "display", "screen" -> Settings.ACTION_DISPLAY_SETTINGS
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "privacy" -> Settings.ACTION_PRIVACY_SETTINGS
            "battery", "power" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return startActivitySafely(context, Intent(action))
    }

    private fun webSearch(context: Context, query: String): Boolean {
        val value = query.trim()
        if (value.isEmpty()) return false
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, value)
        }
        if (startActivitySafely(context, searchIntent)) return true

        val encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        return startActivitySafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")))
    }

    private fun startActivitySafely(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun getValue(action: JSONObject, parameters: JSONObject?, vararg keys: String): String? {
        for (key in keys) {
            if (action.has(key) && !action.isNull(key)) return action.opt(key)?.toString()
            if (parameters != null && parameters.has(key) && !parameters.isNull(key)) {
                return parameters.opt(key)?.toString()
            }
        }
        return null
    }

    private fun extractActions(response: String): List<JSONObject> {
        val actions = mutableListOf<JSONObject>()
        var index = 0
        while (index < response.length) {
            val opening = response[index]
            if (opening != '{' && opening != '[') {
                index++
                continue
            }
            val endExclusive = findJsonValueEnd(response, index)
            if (endExclusive == null) {
                index++
                continue
            }
            val jsonText = response.substring(index, endExclusive)
            try {
                when (opening) {
                    '{' -> collectActions(JSONObject(jsonText), actions)
                    '[' -> collectActions(JSONArray(jsonText), actions)
                }
                index = endExclusive
            } catch (_: JSONException) {
                index++
            }
        }
        return actions
    }

    private fun findJsonValueEnd(text: String, start: Int): Int? {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{', '[' -> stack.addLast(ch)
                '}', ']' -> {
                    if (stack.isEmpty()) return null
                    val open = stack.removeLast()
                    if ((open == '{' && ch != '}') || (open == '[' && ch != ']')) return null
                    if (stack.isEmpty()) return index + 1
                }
            }
        }
        return null
    }

    private fun collectActions(value: JSONObject, output: MutableList<JSONObject>) {
        val actionsArray = value.optJSONArray("actions")
        if (actionsArray != null) {
            collectActions(actionsArray, output)
            return
        }
        val nestedAction = value.opt("action")
        if (nestedAction is JSONObject || nestedAction is JSONArray) {
            collectActions(nestedAction, output)
            return
        }
        if (value.has("action") || value.has("type")) {
            output.add(value)
            return
        }
        val keys = value.keys()
        while (keys.hasNext()) {
            when (val child = value.opt(keys.next())) {
                is JSONObject -> collectActions(child, output)
                is JSONArray -> collectActions(child, output)
            }
        }
    }

    private fun collectActions(value: JSONArray, output: MutableList<JSONObject>) {
        for (index in 0 until value.length()) {
            when (val child = value.opt(index)) {
                is JSONObject -> collectActions(child, output)
                is JSONArray -> collectActions(child, output)
            }
        }
    }
}
