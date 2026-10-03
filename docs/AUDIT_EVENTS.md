# Mysterria Stuff audit events

Rows are emitted best-effort through the shaded audit client (producer `mysterria-stuff`) into the
shared spool at `plugins/mysterria-audit-spool`, which the per-server audit engine ingests. A full
queue or failed spool write never blocks gameplay. All event types carry the `mysterria-stuff.`
prefix. Unless noted, rows are `STAFF_RESTRICTED`, risk `NORMAL`, with a fresh correlation UUID;
rows with no actor carry `actor_name` `console`, `system` (automatic) or `unknown` (not exposed). Player rows carry
`world`, `x`, `y`, `z`.

## Tokens, items, cosmetics, join messages

| Event | Outcome(s) | Business ID | Key facts |
| --- | --- | --- | --- |
| `token.granted` | COMMITTED, FAILED | `token:universal`, `token:joinmsg` | `token_type`, `amount`, `delivery` (`admin_give`, `wrap_exchange_refund`, `joinmsg_session_cancelled`); row-only lot `item_uuid` (`item_uuid_scope=lot`), `item_mint_qty`, `item_origin` (`STAFF_GRANT`/`SHOP`), `item_minted_by`; refunds carry `parent_item_uuid` and reuse the consumption's correlation |
| `token.consumed` | COMMITTED | `token:universal`, `token:joinmsg` | `delivery` (`wrap_exchange`, `joinmsg_session_started`); `item_uuid` (scope `instance`) if stamped, else `token_marker`, `material`, `display_name_sha256`, `item_sha256` |
| `item.granted` | COMMITTED, FAILED | `item:reinforced_elytra` | staff give; `grant_type`, `amount`; `failure=item_creation_failed` when the elytra cannot be built |
| `kit.granted` | COMMITTED, FAILED | `kit:last_sprint` | `delivery` `admin_give` or `first_join`; `stack_count`, `gift_flag_saved`; `failure=gift_flag_unsaved` when the gift flag cannot be saved (kit not delivered) |
| `cosmetic.unlocked` | COMMITTED, FAILED | `wrap:<wrap id>` | universal-token exchange; `wrap_id`, `wrap_name`, `item_type`, `item_amount`, `item_uuid` (`instance` when stamped, else `lot`), `parent_item_uuid`; FAILED with `failure_stage=exchange` and `failure` `wrap_physical_missing`, `wrapper_creation_failed` or `wrapper_tagging_failed` (token refunded) |
| `cosmetic.preview_unavailable` | OBSERVED (LOW) | `wrap:<wrap id>` | `unavailable_reason`; at most one row per player per wrap per 10 minutes |
| `joinmsg.message_set` | COMMITTED, FAILED | `joinmsg:<player uuid or pending name>` | reason `self_service` (correlation of the token session) or `admin_mutation`; `message_type`, `target_name`, `message_sha256` |
| `joinmsg.message_removed` | COMMITTED, FAILED | `joinmsg:<player uuid or pending name>` | `admin_mutation`; `message_type` (`join`, `quit`, `join_and_quit`) |
| `joinmsg.default_changed` | COMMITTED, FAILED | `joinmsg:default:join`, `joinmsg:default:quit` | `message_type`, `message_sha256` |
| `joinmsg.firstjoin_changed` | COMMITTED, FAILED | `joinmsg:first_join` | `message_sha256` |

Message contents are never logged, only SHA-256 hashes. Join-message FAILED rows carry
`failure=write_error`. Admin join-message rows use the online subject's position
(`location_source=subject`), else the in-game admin's (`location_source=actor`).

Grant rows (`token.granted`, `item.granted`, `kit.granted`, `cosmetic.unlocked`) carry
`delivery_mode`, `delivered_amount`, `dropped_amount`; an incomplete delivery is FAILED with
`failure=items_undelivered` and `undelivered_amount`, a delivery that throws is FAILED with
`failure=delivery_exception` and `error_class`.

## MythicDungeons parties and dungeons

Bound only while MythicDungeons is enabled. All rows are OBSERVED, `INTERNAL`, risk `LOW`, with
`source_plugin=MythicDungeons`.

| Event | Actor / subject | Correlation | Business ID | Key facts |
| --- | --- | --- | --- | --- |
| `party.created` | leader / - | party id | `party:<party id>` | `party_id`, `party_id_source`, `members`, `member_count` |
| `party.joined` | joiner / leader | party id | `party:<party id>` | `members` (includes the joiner), `member_count` |
| `party.left` | leaver / - | party id | `party:<party id>` | `remaining_members`, `remaining_count` |
| `party.kicked` | kicker (if exposed) / kicked | party id | `party:<party id>` | `kicker_exposed`, `remaining_members`, `remaining_count`, `location_source` |
| `party.chat` | - / - | party id | `party:<party id>` | privacy `CHAT_CONTENT`; `message`, `message_length`, `recipient_count`, `sender_exposed=false` |
| `dungeon.started` | leader or first participant | instance UUID | `party:<party id>` or `dungeon_instance:<uuid>` | `instance_id`, `dungeon`, `dungeon_display_name`, `instance_name`, `participants`, `participant_count` |
| `dungeon.ended` | - (`actor_name=system`) | instance UUID | as above | `result` (`completed`, `not_completed`, `unknown`), `status`, `time_elapsed`, `duration_ms` |
| `dungeon.player_left` | player | instance UUID | as above | `edit_mode`, `reason_exposed=false` |
| `dungeon.loot_generated` | player | instance UUID | as above | risk `NORMAL`; one row per stack: `loot_table`, `material`, `amount`, `item_uuid` when stamped |

Parties expose no id, so `party_id` is derived from the leader UUID and first-seen time
(`party_id_source` `leader_created`, `leader_first_seen`, `anonymous_first_seen`, or `exposed`).
Member and participant lists hold at most 6 UUIDs (`<key>_truncated=true` beyond that).
