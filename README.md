# PlaceableModelPlugin

Paper plugin that lets players place items with a custom model as `item_display` entities and pick them up again.

- **Platform:** Paper 1.21.11
- **Java:** 21
- **Dependencies:** none
- **Client requirement:** a resource pack that provides the models. The plugin does not distribute or require any specific pack.

## Behavior

| Action | Result |
|---|---|
| Right-click a block face with a matching item in the main hand | The item is placed at the exact click point. One item is consumed (not in Creative). |
| Right-click a placed model with an empty main hand | The model is removed and the original item is returned. |
| Right-click a placed model with the same item in the main hand (`placement.stackable: true`) | The item is placed on top of the stack. |
| Right-click a placed model with a matching item in the main hand (otherwise, or while sneaking) | The item is placed on the block behind the model (`settings.place_through_models`). |

Details:

- By default placement is allowed on any block face: floor, wall, ceiling. Allowed surfaces are configured per entry with `placement.surfaces`. If the clicked surface is not allowed, nothing is placed and the player sees an action bar message.
- The model faces the player at placement time (player yaw + 180°). Only the player's yaw is used; the player's pitch has no effect. Tilt is configured with `display.pitch`.
- Right-clicking an interactable block (chest, door, etc.) without sneaking does not place the item. Sneak to place.
- Only the main hand is processed.
- The original `ItemStack` is stored in the PDC of the interaction entity and returned unchanged on pickup. No data files are used.
- If the player's inventory is full, the returned item is dropped at the player's location.
- Placement honors cancellation of `PlayerInteractEvent` by other plugins.

## Item matching

An item is matched against four arrays in `config.yml`. The first matching entry wins; arrays are checked in this order:

| Array | Matched against | Example `match` |
|---|---|---|
| `custom_model_data` | First string of the `custom_model_data` component | `chair_wood` |
| `item_model` | `item_model` component (namespaced id) | `mypack:chair` |
| `equipable` | `equippable.asset_id` | `armor:statue_head` |
| `rename` | `custom_name` (plain text, exact, case-sensitive) | `Zombie` |

Notes:

- `item_model` and `equipable` values without a namespace default to `minecraft:`.
- `match` accepts a string or a list of strings.
- `base_item` (optional) additionally restricts the entry to one material.
- The array `rename` may also be written as `Rename`.

## Configuration

Each array is a YAML list. Every entry starts with `- ` and is independent of the others. Parameters that are not specified are taken from `defaults`, not from neighboring entries.

```yaml
custom_model_data:
  - match: "cap"
    display:
      scale: 0.5
    interaction:
      width: 0.5
      height: 0.5

  - match: ["back", "back_alt"]
    base_item: PAPER
    interaction:
      width: 0.9
```

Do not leave `[]` after the key when entries are present.

### Entry parameters

| Parameter | Default | Description |
|---|---|---|
| `match` | required | Value or list of values to match. |
| `base_item` | any | Material filter. |
| `placement.surfaces` | `[floor, wall, ceiling]` | Allowed surfaces: `floor` (top face of a block), `wall` (side faces), `ceiling` (bottom face). A list in an entry replaces the default list entirely. |
| `placement.stackable` | `false` | Allows stacking: right-clicking the hitbox of a placed model of the same item with that item places a new model on top. The bottom of the new hitbox is aligned with the top of the existing hitbox. Requires `floor` in `placement.surfaces`. |
| `placement.max_stack` | `8` | Maximum number of models of the same item in one stack. `0` = unlimited (a hard scan limit of 64 models still applies). |
| `placement.stack_pickup` | `all` | Behavior when a model with other models on top is picked up. `all`: the model and everything above it are picked up and all items are returned. `top_only`: only the topmost model can be picked up; the lower ones are protected. |
| `display.scale` | `[1, 1, 1]` | Scale. A single number applies uniformly. |
| `display.offset` | `[0, 0.5, 0]` | Model center offset in the model's local axes. Y points away from the surface. |
| `display.rotation_offset` | `0` | Additional rotation around the model's vertical axis, in degrees. |
| `display.pitch` | `0` | Model tilt around its own horizontal axis, in degrees. Independent of the player's pitch. A positive value tilts the front of the model down. The model tilts around its center; `display.offset` is not affected. |
| `display.align_to_surface` | `true` | `true`: the model's bottom lies on the clicked surface (floor, wall, ceiling). `false`: the model always stays upright. |
| `display.item_transform` | `NONE` | `ItemDisplayTransform` name. |
| `display.view_range` | `1.0` | View range multiplier. |
| `display.brightness` | unset | `{block: 0-15, sky: 0-15}`. Both keys required. |
| `interaction.width` | `1.0` | Hitbox width. |
| `interaction.height` | `1.0` | Hitbox height. |
| `interaction.y_offset` | `0.0` | Vertical hitbox shift (world Y axis). |
| `interaction.responsive` | `false` | Whether the player's arm swings on click. |
| `sounds.place` | `block.wood.place` | `{sound, volume, pitch}`. Empty `sound` disables it. |
| `sounds.pickup` | `block.wood.break` | Same format. |

The interaction hitbox is centered on the model's center and is always axis-aligned (an `interaction` entity cannot be rotated).

### Settings

| Key | Default | Description |
|---|---|---|
| `settings.language` | `auto` | `auto` (client locale), `ru`, `en`. |
| `settings.place_through_models` | `true` | Allows placing through the hitbox of an existing model. |

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/placeablemodel reload` (alias `/pmodel`) | `placeablemodel.reload` | op |

| Permission | Description | Default |
|---|---|---|
| `placeablemodel.place` | Place models. | everyone |
| `placeablemodel.pickup` | Pick models up. | everyone |
| `placeablemodel.reload` | Reload the config. | op |

## Stacking

With `placement.stackable: true`, right-clicking a placed model of the same item with that item places a new model on top of it.

- "Same item" means both items match the same config entry.
- The vertical position is derived from the interaction hitboxes only: the new hitbox starts where the lower one ends. Set `interaction.height` (and `interaction.y_offset`) so that it equals the visual height of the model.
- If the stack already has models on top, the new model is placed on the topmost model of that stack.
- If a model of a different item sits on top of the stack, nothing is placed and the player sees an action bar message.
- A stacked model is horizontally centered on the lower one and oriented as on the floor (player yaw).
- Sneaking bypasses stacking and places the item on the block behind the model.
- Stack height is limited by `placement.max_stack`. When the limit is reached, nothing is placed and the player sees an action bar message. Models of a different item under the stack (for example a table) are not counted.
- Picking up a model that has models on top is controlled by `placement.stack_pickup`: `all` returns the model and everything above it; `top_only` allows picking up only the topmost model.
- With `all`, every model resting on the picked-up one is returned, regardless of its item.

## Limitations

- No collision or free-space check is performed at the placement point.
- A placed model is not removed when the block it was placed on is broken.
- If one entity of a pair is removed by other means (for example `/kill`), the other remains in the world.
- Model orientation assumes the front of an `item_display` faces +Z at zero rotation. If a model faces away from the player, set `display.rotation_offset: 180`.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. Test server: `./gradlew runServer`.
