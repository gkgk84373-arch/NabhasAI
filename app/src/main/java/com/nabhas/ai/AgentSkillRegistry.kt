package com.nabhas.ai

enum class AgentSkill(
    val displayName: String
) {
    DEVICE_CONTROL("Device Control"),
    SCREEN_READER("Screen Reader"),
    OVERLAY_LIVE_VOICE("Overlay & Live Voice"),
    GENERAL_ASSISTANT("General Assistant")
}

data class AgentSkillDefinition(
    val skill: AgentSkill,
    val displayName: String,
    val instructions: String
)

object AgentSkillRegistry {
    private val definitions: Map<AgentSkill, AgentSkillDefinition> = listOf(
        AgentSkillDefinition(
            skill = AgentSkill.DEVICE_CONTROL,
            displayName = "Device Control",
            instructions = """
                You are the Device Control agent. Help the user perform requested actions on their Android device.
                When a device action is needed, return only valid JSON: either one action object or a JSON array of
                action objects in the order they should be performed. Do not wrap JSON in Markdown or add prose.
                Never invent a successful result. If a required value is missing, ask the user for it instead of
                guessing. Use these action names and argument shapes:

                {"action":"open_app","app_name":"App name"}
                {"action":"dial","phone_number":"Phone number"}
                {"action":"open_settings","settings_page":"Optional settings page"}
                {"action":"web_search","query":"Search query"}
                {"action":"click_text","text":"Visible text to click"}
                {"action":"scroll","direction":"up|down|left|right"}
                {"action":"type_text","text":"Text to enter"}
                {"action":"home"}
                {"action":"back"}

                For example, a sequence of actions must be a JSON array:
                [{"action":"open_app","app_name":"Maps"},{"action":"type_text","text":"Central Park"}]

                Only use the actions listed above. Keep values faithful to the user's request. For requests that
                need no device action, respond briefly in natural language.
            """.trimIndent()
        ),
        AgentSkillDefinition(
            skill = AgentSkill.SCREEN_READER,
            displayName = "Screen Reader",
            instructions = """
                You are the Screen Reader agent. Use the active-screen content provided by
                NabhasAccessibilityService to understand what is visible and help the user interact with it.
                Base your answer only on the screen content and context actually provided. If no screen content is
                available or it is incomplete, say so and ask the user to provide or refresh it; do not claim to
                have inspected the screen.

                Summarize relevant screen content in clear, concise language. Answer questions about visible text,
                controls, and the current screen. When asked to interact, identify the target using its visible
                text and produce the appropriate device action instruction: click with click_text, move with
                scroll, or enter text with type_text. Do not claim an action succeeded unless its result is
                provided. Ask a clarifying question when multiple visible targets could match.
            """.trimIndent()
        ),
        AgentSkillDefinition(
            skill = AgentSkill.OVERLAY_LIVE_VOICE,
            displayName = "Overlay & Live Voice",
            instructions = """
                You are the Overlay & Live Voice agent. Help with the floating side-edge overlay and continuous
                voice interaction. Explain that the overlay can be opened from its side-edge handle and that
                continuous voice input should remain active until the user stops it or the app reports an error.
                Do not claim to enable, disable, or inspect these features unless the app provides a result.

                In live voice conversations, respond quickly with short, natural answers. Handle barge-in gracefully:
                stop or yield to the user's new utterance, prioritize the newest request, and do not continue an
                interrupted answer. Avoid long introductions, unnecessary lists, and repeated confirmations.
                If the user asks for a device action, provide the appropriate device action instruction rather than
                implying the overlay or voice agent performed it.
            """.trimIndent()
        ),
        AgentSkillDefinition(
            skill = AgentSkill.GENERAL_ASSISTANT,
            displayName = "General Assistant",
            instructions = """
                You are a helpful general assistant. Respond to questions and requests in clear, direct language.
                Be accurate, conversational, and appropriately concise. Ask a focused clarifying question when
                important details are missing. Do not claim to have performed actions or accessed device data
                unless that information is explicitly provided.
            """.trimIndent()
        )
    ).associateBy { it.skill }

    fun get(skill: AgentSkill): AgentSkillDefinition =
        definitions.getValue(skill)

    fun all(): List<AgentSkillDefinition> =
        AgentSkill.entries.map { definitions.getValue(it) }

    fun route(prompt: String): AgentSkillDefinition =
        get(selectSkill(prompt))

    fun selectSkill(prompt: String): AgentSkill {
        val normalizedPrompt = prompt
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")

        if (normalizedPrompt.isBlank()) {
            return AgentSkill.GENERAL_ASSISTANT
        }

        val scores = mapOf(
            AgentSkill.DEVICE_CONTROL to scoreMatches(
                normalizedPrompt,
                listOf(
                    "open app", "launch app", "start app", "open the app",
                    "dial ", "call ", "phone number", "make a call",
                    "open settings", "settings page", "change my settings",
                    "web search", "search the web", "google ",
                    "click ", "tap ", "scroll ", "type ", "enter text",
                    "go to home", "press home", "go back", "press back"
                ),
                baseScore = 2
            ),
            AgentSkill.SCREEN_READER to scoreMatches(
                normalizedPrompt,
                listOf(
                    "read the screen", "read my screen", "what is on screen",
                    "what's on screen", "what is on the screen", "what's on the screen",
                    "describe the screen", "explain the screen", "screen content",
                    "visible on screen", "visible on the screen", "what do you see",
                    "read this page", "summarize this page", "current screen",
                    "on this screen", "on the screen", "accessibility screen"
                ),
                baseScore = 3
            ),
            AgentSkill.OVERLAY_LIVE_VOICE to scoreMatches(
                normalizedPrompt,
                listOf(
                    "floating overlay", "side-edge overlay", "side edge overlay",
                    "edge overlay", "floating button", "voice overlay",
                    "continuous voice", "continuous listening", "keep listening",
                    "live voice", "voice mode", "voice input", "barge-in",
                    "barge in", "interrupt the assistant", "interrupt your answer"
                ),
                baseScore = 3
            ),
            AgentSkill.GENERAL_ASSISTANT to 0
        )

        return scores.entries
            .filter { it.value > 0 }
            .maxWithOrNull(
                compareBy<Map.Entry<AgentSkill, Int>> { it.value }
                    .thenBy { routingPriority(it.key) }
            )
            ?.key
            ?: AgentSkill.GENERAL_ASSISTANT
    }

    private fun scoreMatches(
        prompt: String,
        phrases: List<String>,
        baseScore: Int
    ): Int {
        val matchedPhrases = phrases.filter { phrase ->
            prompt.contains(phrase)
        }
        return matchedPhrases.size * baseScore
    }

    private fun routingPriority(skill: AgentSkill): Int =
        when (skill) {
            AgentSkill.SCREEN_READER -> 3
            AgentSkill.OVERLAY_LIVE_VOICE -> 2
            AgentSkill.DEVICE_CONTROL -> 1
            AgentSkill.GENERAL_ASSISTANT -> 0
        }
}
