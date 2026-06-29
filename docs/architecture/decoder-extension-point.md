---
type: Extension Point Contract
title: Decoder Extension Point
description: How to implement and register a ByteDecoder, in this plugin or a dependent plugin.
tags: [decoders, extension-point, contract]
resource: src/main/kotlin/team/shade/lmdbviewer/decode/ByteDecoder.kt
---

# Decoder extension-point contract

To add a decoder (in this plugin or a dependent plugin), implement `ByteDecoder` and register:

```xml
<extensions defaultExtensionNs="team.shade.lmdbviewer">
  <byteDecoder implementation="com.example.MsgpackDecoder"/>
</extensions>
```

Rules: `canDecode` must be cheap and side-effect free; return false rather than throwing on bad
input. `priority` orders auto-detect (higher wins); keep structured-format decoders above plain
text and text above hex. `decode` must never throw — return a best-effort `DecodedView`.

## Related

* The decoder interface, built-ins and registry: [Decode Layer](/architecture/decode-layer.md).
