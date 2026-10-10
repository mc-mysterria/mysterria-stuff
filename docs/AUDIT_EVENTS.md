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
| `token.granted` | COMMITTED, FAILED | `token:universal`, `token:joinmsg` | `token_type`, `amount`, `delivery` (`admin_give`, `wrap_exchange_refund`, `joinmsg_session_cancelled`); row-only lot `item_uuid` (`item_uuid_scope=lot`), `item_mint_qty`, `item_origin` (`STAFF_GRANT`/`SHOP`), `item_minted_by`; refunds reuse the consumption's correlation |
| `token.consumed` | COMMITTED | `token:universal`, `token:joinmsg` | `delivery` (`wrap_exchange`, `joinmsg_session_started`); `material` |
| `item.granted` | COMMITTED, FAILED | `item:reinforced_elytra` | staff give; `grant_type`, `amount`; `failure=item_creation_failed` when the elytra cannot be built |
| `kit.granted` | COMMITTED, FAILED | `kit:last_sprint` | `delivery` `admin_give` or `first_join`; `stack_count`, `gift_flag_saved`; `failure=gift_flag_unsaved` when the gift flag cannot be saved (kit not delivered) |
| `cosmetic.unlocked` | COMMITTED, FAILED | `wrap:<wrap id>` | universal-token exchange; `wrap_id`, `wrap_name`, `item_type`, `item_amount`, `item_uuid` (`instance` when stamped, else `lot`); FAILED with `failure_stage=exchange` and `failure` `wrap_physical_missing`, `wrapper_creation_failed` or `wrapper_tagging_failed` (token refunded) |
| `cosmetic.preview_unavailable` | OBSERVED (LOW) | `wrap:<wrap id>` | `unavailable_reason`; at most one row per player per wrap per 10 minutes |
| `joinmsg.message_set` | COMMITTED, FAILED | `joinmsg:<player uuid or pending name>` | reason `self_service` (correlation of the token session) or `admin_mutation`; `message_type`, `target_name`; admin sets carry `previous_message_set`; self-service rows carry `join_message_sha256` and `quit_message_sha256` |
| `joinmsg.message_removed` | COMMITTED, FAILED | `joinmsg:<player uuid or pending name>` | `admin_mutation`; `message_type` as requested (`join`, `quit`, `join_and_quit`); `previous_join_set` / `previous_quit_set` for each requested type |
| `joinmsg.default_changed` | COMMITTED, FAILED | `joinmsg:default:join`, `joinmsg:default:quit` | reason `admin_set`; `message_type`, `previous_message_set` |
| `joinmsg.firstjoin_changed` | COMMITTED, FAILED | `joinmsg:first_join` | reason `admin_set`; `previous_message_set` |

Token rows name the token by business ID and `material` only: reading an item's data or hashing it
would be main-thread work done just for the log.

Join-message contents are never logged. Self-service rows carry SHA-256 hashes of the join and quit
message, computed on the async chat thread; staff command rows carry no hashes, because hashing there
would run on the main thread. Join-message FAILED rows carry `failure=write_error`. Admin join-message
rows use the in-game admin's position (`location_source=actor`).

Grant rows (`token.granted`, `item.granted`, `kit.granted`, `cosmetic.unlocked`) carry
`delivery_mode`, `delivered_amount`, `dropped_amount`; an incomplete delivery is FAILED with
`failure=items_undelivered` and `undelivered_amount`, a delivery that throws is FAILED with
`failure=delivery_exception` and `error_class`.

## Staff, booster and configuration actions

Every row here names the staff member: `actor` is the player uuid, and `actor_name` is the sender name
(`CONSOLE` for the console, the sender name for RCON). Automatic rows have no actor and carry
`actor_name=system`. The join-message admin rows above also carry `actor_name`. Nothing here adds work to
the server thread: rows are built from values already in hand and enqueued directly. Outcomes follow the
save: COMMITTED after a confirmed save or applied reload, FAILED when it failed, and OBSERVED where the
plugin gets no completion signal (a dispatched console command, or `config.yml` saved through Bukkit).

| Event | Outcome(s) | Business ID | Key facts |
| --- | --- | --- | --- |
| `boon.patriarch_granted` | COMMITTED, FAILED | `boon:patriarch:<player uuid>` | risk HIGH for staff, NORMAL for automatic; `trigger` (`admin_grant`, `booster_sync`, `booster_join`), `target_name`, `sequence`, optional `admin_reason`; FAILED with `failure` `coi_api_returned_false` or `coi_api_unavailable`. The grant writes the player file before it returns, so COMMITTED means saved |
| `boon.patriarch_revoked` | OBSERVED, FAILED | `boon:patriarch:<player uuid>` | `trigger` (`admin_revoke`, `booster_sync`, `booster_join`), `removal_cause` (`admin_revoke`, `not_booster`, `too_many_boons`), `target_name`, optional `admin_reason`; OBSERVED means the console command `coi outer remove` was dispatched; FAILED has `failure=command_dispatch_failed` |
| `boon.booster_refresh_requested` | OBSERVED | `boon:patriarch:booster_list` | risk HIGH; `known_booster_count`; its correlation id is shared by the list sync row and every grant or revoke it causes |
| `boon.booster_list_synced` | OBSERVED | `boon:patriarch:booster_list` | emitted only when the booster list changed; `trigger` (`booster_sync`, `admin_refresh`), `added_count`, `removed_count`, `booster_count`; names are not logged |
| `kit.gift_reset` | COMMITTED, FAILED | `kit:last_sprint` | `/mystuff lastsprint reset`; risk HIGH; `target_name`, `had_gift_flag` (previous value), `gift_flag_saved`, optional `admin_reason`; FAILED has `failure=write_error` (the in-memory flag is cleared either way) |
| `kit.rewards_updated` | COMMITTED, FAILED | `kit:last_sprint` | admin kit editor save or close; risk HIGH; `old_*` and `new_*` of `stack_count`, `total_amount` and `items` (`MATERIAL xN`, comma separated, cut at the length limit with `<prefix>_items_truncated=true`); no item hashes or serialized items; FAILED has `failure=write_error` |
| `kit.autogrant_toggled` | OBSERVED | `kit:last_sprint` | `active_old`, `active_new` |
| `joinmsg.store_repaired` | COMMITTED, OBSERVED, FAILED | `joinmsg:store` | `recovered_count`; OBSERVED when nothing was written (`result` `no_backup_files` or `nothing_to_fill`); FAILED has `failure=write_error` |
| `joinmsg.store_reloaded` | COMMITTED, FAILED | `joinmsg:store` | risk LOW; FAILED has `failure=load_failed` |
| `plugin.reloaded` | COMMITTED, FAILED | `plugin:config` | `/mystuff reload`; risk LOW; `debug_old`, `debug_new`, `recipe_count_old`, `recipe_count_new`, `joinmsg_store_loaded`; FAILED has `failure=joinmsg_store_load_failed` |
| `plugin.debug_toggled` | OBSERVED | `plugin:debug` | risk LOW; `debug_old`, `debug_new` |
| `recipe.reloaded` | COMMITTED | `plugin:recipes` | risk LOW; `recipe_count_old`, `recipe_count_new` |

`/mystuff lastsprint reset`, `booster grant` and `booster revoke` accept an optional trailing free-text
reason, stored as `admin_reason` (cut at 256 characters). Existing usage without it is unchanged.

Old pathway state is not recorded for `booster grant` and `booster revoke`: the plugin does not read it
at that point and a lookup only for the row is not added.

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
| `dungeon.loot_generated` | player | instance UUID | as above | risk `NORMAL`; one row per stack: `loot_table`, `material`, `amount` |

Parties expose no id, so `party_id` is derived from the leader UUID and first-seen time
(`party_id_source` `leader_created`, `leader_first_seen`, `anonymous_first_seen`, or `exposed`).
Member and participant lists hold at most 6 UUIDs (`<key>_truncated=true` beyond that).

## Main thread

Audit rows add no lookups to the server main thread: they read the event's own objects and values
gameplay already computed.

Dropped for that reason: item uuid reads on delivery, token consumption and dungeon loot rows (so
`item_uuid` is only present for a stamped wrapper, which already returns it, and for row-only lot ids);
`parent_item_uuid`; `token_marker`; the join-message target's position on staff commands; the staff
command message hashes (`message_sha256`, `previous_message_sha256`, `previous_join_sha256`,
`previous_quit_sha256`, now presence flags) and the combined self-service `message_sha256`.

Kept on the main thread: the provenance uuid stamp written onto a wrapper item (item data, not a
log read) and the MythicDungeons party member lists, which come from the plugin's own event objects.
