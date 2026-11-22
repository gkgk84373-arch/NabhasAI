# NABHAS AI — Gopal Projects

NABHAS AI एक Android chat assistant है। यह repository अभी एक `:app` module पर है; इसे चरणबद्ध, testable AI agent platform में विकसित किया जा रहा है। मौजूदा implementation की सीमाओं और migration नियमों के लिए [Architecture और roadmap](docs/ARCHITECTURE.md) देखें।

## वर्तमान स्थिति

वर्तमान source में Compose chat/settings UI, configurable OpenAI-compatible chat endpoint और model fallback, built-in prompt skills, सीमित Android intents/Accessibility actions, user-triggered voice input, optional text-to-speech तथा GitHub release updater मौजूद हैं। यह सूची source में मौजूद paths बताती है, हर Android device पर runtime verification नहीं।

अभी Gemini/Claude/Ollama के dedicated providers, persistent conversation/project memory, installable skills, MCP client, सामान्य file manager, dashboard/navigation suite तथा Hindi/Punjabi UI पूर्ण रूप से लागू नहीं हैं। `OverlayService` खाली है; Screen Reader prompt के बावजूद screen-content handoff स्थापित नहीं है। इन सुविधाओं को उपलब्ध बताने से पहले वास्तविक implementation और tests आवश्यक हैं।

## Build

JDK 17 और Android SDK Platform 34 आवश्यक हैं। स्थानीय debug APK के लिए:

```bash
./gradlew assembleDebug
```

Unit tests के लिए:

```bash
./gradlew testDebugUnitTest
```

GitHub Actions हर branch push, pull request और manual dispatch पर unit tests चलाता है और debug APK को `nabhas-ai-debug-apk` artifact के रूप में अपलोड करता है। `main` पर सफल push के बाद अलग release job मौजूदा `latest` GitHub Release asset को अपडेट करता है; यह debug APK है, signed production release नहीं।

## Credentials और privacy

API key source में hardcode नहीं करनी चाहिए। वह अभी ऐप की local preferences में रहती है; encrypted Keystore-backed storage और backup exclusion आगामी security work हैं। HTTP headers और bodies को log नहीं किया जाता। केवल भरोसेमंद HTTPS endpoint पर key दर्ज करें। Assistant action safety/confirmation भी जारी architecture work है।

## Package / app identity

अभी `namespace` और `applicationId` दोनों `com.nabhas.ai` हैं। Kotlin namespace को `com.gopal.projects` पर ले जाने की योजना अलग है; `applicationId` migration को अलग release निर्णय माना गया है, क्योंकि applicationId बदलने से Android एक अलग app install मानता है।