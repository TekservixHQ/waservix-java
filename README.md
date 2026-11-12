# WAservix Java / Kotlin client

[![](https://jitpack.io/v/tekservixhq/waservix-java.svg)](https://jitpack.io/#tekservixhq/waservix-java)

**WAservix WhatsApp API Gateway** - official Java and Kotlin client: send WhatsApp messages from your own number. By Tekservix.

Java and Kotlin client for the [WAservix WhatsApp API Gateway](https://tekservix.com/waservix/).

WAservix links your own WhatsApp number by QR code (the same way WhatsApp Web does) and gives you a REST API to send text, images, video, audio, documents, locations, polls and more, with webhooks for incoming messages and status updates.

- Java 8+, works from Kotlin, Spring Boot, Ktor, Quarkus and plain Java
- One small dependency (Gson)
- Webhook check built in

## Maven Central

Gradle (Kotlin DSL):

```kotlin
implementation("com.tekservix:waservix:1.0.4")
```

Maven:

```xml
<dependency>
  <groupId>com.tekservix</groupId>
  <artifactId>waservix</artifactId>
  <version>1.0.4</version>
</dependency>
```

The JitPack coordinates below keep working.

## Install

Add the JitPack repository, then the dependency.

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.tekservixhq:waservix-java:v1.0.1")
}
```

**Gradle (Groovy)**

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.tekservixhq:waservix-java:v1.0.1'
}
```

**Maven**

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.tekservixhq</groupId>
  <artifactId>waservix-java</artifactId>
  <version>v1.0.1</version>
</dependency>
```

## Quick start

Copy your device's API key from the WAservix dashboard (Devices -> API key) and keep it on your server.

```java
import com.tekservix.waservix.WAservix;

WAservix wa = new WAservix(System.getenv("WASERVIX_KEY"));

JsonObject res = wa.sendText("+15551234567", "Hello Aline, your order is ready").getAsJsonObject();
System.out.println(res.get("msgId") + " " + res.get("status")); // 58213 "in_progress"
```

Kotlin:

```kotlin
val wa = WAservix(System.getenv("WASERVIX_KEY"))
wa.sendText("+15551234567", "Hello Aline, your order is ready")
```

## Sending

Messages are queued and sent one at a time per number, so each call returns right away with `status: "in_progress"` and a `msgId`.

```java
// Image with caption
wa.sendImage("+15551234567", "https://example.com/product.jpg", "Back in stock");

// Document
wa.sendDocument("+15551234567", "https://example.com/inv-1042.pdf", "Invoice-1042.pdf", "Your invoice");

// Anything the API supports, including groups (JID ending in @g.us)
wa.sendMessage("120363040000000000@g.us", Collections.singletonMap("text", "Team meeting at 3pm"));

Map<String, Object> shop = new HashMap<>();
shop.put("latitude", -1.9441);
shop.put("longitude", 30.0619);
shop.put("name", "Our shop");
wa.sendMessage("+15551234567", Collections.singletonMap("location", shop));
```

## Status, contacts and groups

```java
JsonElement info  = wa.getMessageInfo(58213);      // queued, sent, delivered, read, played or failed
JsonElement check = wa.onWhatsApp("+15551234567"); // {"exists": true, "jid": "..."}
JsonElement conn  = wa.getStatus();                // {"status": "connected"}

wa.editMessage(58213, "Corrected text"); // own text messages, about 15 minutes
JsonElement groups = wa.listGroups();
```

Anything else in the [API reference](https://tekservix.com/waservix/docs/) is one call away:

```java
wa.request("POST", "/messages/read", Collections.singletonMap("msgId", "3EB0X123456789"));
```

## Webhooks

Set a webhook URL and events for the number in the dashboard. Each request carries `X-Webhook-Signature`, which equals the device's webhook secret. Spring Boot example:

```java
@PostMapping("/wa/webhook")
public ResponseEntity<Void> waWebhook(@RequestBody String raw,
                                      @RequestHeader("X-Webhook-Signature") String signature) {
    if (!WAservix.verifyWebhook(signature, System.getenv("WASERVIX_WEBHOOK_SECRET"))) {
        return ResponseEntity.status(401).build();
    }
    JsonObject payload = JsonParser.parseString(raw).getAsJsonObject();
    if ("messages.received".equals(payload.get("event").getAsString())) {
        JsonObject m = payload.getAsJsonObject("data").getAsJsonObject("messages");
        log.info("{}: {}", m.getAsJsonObject("key").get("cleanedSenderPn"), m.get("messageBody"));
    }
    return ResponseEntity.ok().build(); // acknowledge fast; queue slow work
}
```

## Errors

Failed calls throw `WAservixException` (unchecked) with `getStatus()`, `getMessage()`, `getErrors()` and, on 429, `getRetryAfter()` in seconds. A 503 means the device is reconnecting: retry shortly.

**Android apps:** don't put the API key inside an APK, where anyone can extract it. Have the app call your own backend, and call WAservix from there.

## Migrating from WaSender

WAservix accepts the same `/send-message` body and webhook fields as WaSender, so most integrations switch by changing the base URL and key. See the [compatibility notes](https://tekservix.com/waservix/docs/).

## Use it responsibly

WAservix is not Meta's official WhatsApp Business Platform. There's no business verification and no per-conversation fee, but WhatsApp's spam rules apply to your number directly. Message people who expect to hear from you, warm up new numbers slowly and keep the built-in account protection on.

## More from Tekservix

- [SMS Gora SMS Gateway](https://tekservix.com/smsgora/): send SMS through your own Android phones ([smsgora-java](https://github.com/tekservixhq/smsgora-java))
- [AI Sales Agent](https://tekservix.com/ai-sales-agent): answers customers on WhatsApp and SMS using your website's content
- [Website to App](https://tekservix.com/mobile-apps): turn your website into Android and iOS apps
- WAservix is also available for Node.js (`npm install waservix`), Python (`pip install waservix`), PHP (`composer require tekservix/waservix`) and Go (`go get github.com/tekservixhq/waservix-go`)

## License

MIT
