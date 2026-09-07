# Mysterria Stuff audit events

Mysterria Stuff emits best-effort, staff-restricted events through its shaded neutral audit client. A full queue or failed spool write does not block token, cosmetic, or message-store operations.

The optional per-server audit engine owns SQLite and local staff searches. Each producer writes to its own bounded spool directory even when the engine is absent. Existing gameplay dependencies remain separate from audit transport.

## Canonical events

| Event | Authoritative commit | Business ID | Notes |
| --- | --- | --- | --- |
| `mysterria-stuff.token.granted` | Inventory add/drop completes | `token:universal` or `token:joinmsg` | Includes `token_type`, `amount`, and delivery/reason metadata. |
| `mysterria-stuff.token.consumed` | Token stack is decremented | `token:universal` or `token:joinmsg` | Emitted only after the decrement succeeds. |
| `mysterria-stuff.cosmetic.unlocked` | Wrapper item is added or dropped after a universal-token exchange | `wrap:<wrap uuid or loader id>` | Includes the stable wrap ID and physical item projection. |
| `mysterria-stuff.joinmsg.message_set` | Join/quit message store save succeeds | `joinmsg:<player uuid or pending name>` | Admin actor is recorded when the sender is a player. |
| `mysterria-stuff.joinmsg.message_removed` | Join/quit message removal reports a changed entry | `joinmsg:<player uuid or pending name>` | `message_type` identifies join, quit, or both. |
| `mysterria-stuff.joinmsg.default_changed` | Default message store save succeeds | `joinmsg:default:join` or `joinmsg:default:quit` | Message contents are intentionally not logged. |
| `mysterria-stuff.joinmsg.firstjoin_changed` | First-join message store save succeeds | `joinmsg:first_join` | Message contents are intentionally not logged. |

Every independent operation gets a fresh correlation UUID. Related lifecycle
events, such as a token consumption and its cancellation refund, reuse the same
correlation UUID. Player UUIDs are used as actor/subject IDs where available;
console actors remain unset. Metadata keys are snake_case and bounded by the
shared audit contract. This repository does not invent item UUIDs: `item_uuid`
is emitted only when an authoritative item owner exposes one.

## Deliberate exclusions

GUI previews, rendering, routine interactions, transient sprint/listener
effects, cosmetic equip/remove events, and HMCWraps ownership changes are not
emitted here. HMCWraps owns the durable cosmetic ownership model and does not
expose an ownership-mutation API in its integration contract; the HMCWraps
plugin's own event/owner records are the authoritative source for those changes.
