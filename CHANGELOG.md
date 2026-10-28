# Changelog

## 0.1.1 - 2026-10-29

- New `VoipConfig.callIdKey` (default `"callId"`): the push field that holds the call ID. The SDK tries it first, then `callId`, then `uuid`. If your server sends the call ID in another field, set `callIdKey` to that field's name.
- Call payloads and caller names are no longer written to the device log.

## 0.1.0

- Initial release.
