# Cereal design system

The visual language and shared components of the Cereal desktop client (Compose for Desktop, Material 3).

- **Tokens:** `cereal-client/src/main/java/com/cereal/client/presentation/theme/`
- **Shared components:** `cereal-client/src/main/java/com/cereal/client/presentation/view/`
- **Naming:** shared components are `Cereal[Component]` (see `presentation/AGENTS.md`). Older ones without the prefix (`Pill`, `Dialog`, `InfoBox`, …) are still the shared version, so reuse them rather than writing new ones.

The app is **dark-only**. Wrap every window in `CerealTheme { … }`. It provides the Material 3 color scheme, shapes and typography, plus the Cereal extras below.

---

## 1. Tokens

### Accessing tokens

| What | Access | Defined in |
|---|---|---|
| Material color roles | `MaterialTheme.colorScheme.primary` | `Colors.kt` → `darkColorScheme` |
| Cereal color extras | `CerealTheme.colorScheme.success` | `Colors.kt` → `CerealColors` |
| Spacing | `CerealTheme.spacing.lg` | `Spacing.kt` → `CerealSpacing` |
| Type scale | `MaterialTheme.typography.bodyMedium` | `Typography.kt` |
| Named text styles | `CerealTypography.paneTitle` | `CerealTypography.kt` |
| Corner radius | `MaterialTheme.shapes.small` | `Shapes.kt` |

### Color: Material roles (`darkColorScheme`)

| Role | Value | Use |
|---|---|---|
| `primary` / `secondary` / `tertiary` | `#E54848` | Brand red. Primary actions, focus cursor, progress, snackbar. |
| `primaryContainer` | `#3E1212` | Tinted red background |
| `onPrimary` | `#FFFFFF` | |
| `background` | `#1C1C1C` | Window background |
| `surface` | `#161616` | Panels, `Surface` buttons |
| `surfaceVariant` | `#222222` | `Neutral` button fill |
| `onSurface` / `onBackground` | `#FFFFFF` | Primary text |
| `onSurfaceVariant` | `#B4AFB6` | Secondary text on variant surfaces |
| `error` | `#E54848` | Field errors, error icons. Same hue as primary. |
| `outline` / `outlineVariant` | `#6A6A6A` / `#3E3E3E` | Material borders |

**White-label brands:** `ApplicationConfig.brandPrimaryColorArgb` replaces `primary`, `secondary`, `tertiary` and `surfaceTint` with the brand color. Read the accent through `MaterialTheme.colorScheme.primary` and never hardcode `#E54848`, or brands won't get their color.

### Color: Cereal extras (`CerealTheme.colorScheme`)

| Token | Value | Use |
|---|---|---|
| `border` | `#2A2A2A` | Default 1dp borders, input border, `Neutral` outline |
| `borderDark` | `#3E3E3E` | Card border, focused input border |
| `cardDark` | `#161616` | `CerealCard` fill |
| `backgroundDark` / `backgroundLight` | `#141414` / `#F6F6F6` | Pane backgrounds / rare light areas |
| `contentSecondary` | `#B4AFB6` | Secondary text, field labels, default `bodyLarge` color |
| `contentTertiary` | `#8A8A8A` | Disabled input text |
| `contentSubtle` | `#848484` | Section labels, meta icons |
| `success` / `onSuccess` | `#2ECC71` / `#0C2418` | Run, success states. Use dark text on green, because white fails contrast. |
| `warning` | `#F5B73A` | `WarningBox`, caution states |
| `info` | `#4F8CFF` | `InfoBox`, info links |
| `link` | `#64B5F6` | Inline links |
| `highlight` | `#0075FF` | Selection highlight |
| `progressTrack` | `#232323` | Unfilled part of progress bars |
| `securityWeak` / `Fair` / `Good` | `#E54848` / `#F2C94C` / `#3B82F6` | Password strength meter only |

### Spacing (`CerealTheme.spacing`)

| Token | dp | Typical use |
|---|---|---|
| `xxs` | 4 | Hairline gaps, icon to label |
| `xs` | 6 | Tight component spacing, label to field |
| `sm` | 8 | Chip/badge padding, small gaps |
| `md` | 12 | Medium gaps, box padding |
| `lg` | 16 | Standard section spacing |
| `xl` | 20 | Card inner padding |
| `xxl` | 24 | Screen edge padding |

### Shape (`MaterialTheme.shapes`)

| Slot | Radius | Used by |
|---|---|---|
| `extraSmall` | 4dp | Tooltips |
| `small` | 6dp | All buttons, `InfoBox`, `WarningBox` |
| `medium` | 8dp | General containers. `CerealTextField` hardcodes 8dp, which matches. |
| `large` | 12dp | Larger panels |
| `extraLarge` | 16dp | `CerealCard`, `Dialog` |
| `CircleShape` | — | `Pill`, `StatusDot` |

### Typography

Font family: **Montserrat** (Regular 400, Medium 500, SemiBold 600, Bold 700), bundled in `resources/fonts/`. Every Material slot uses it. Unmodified slots copy M3 sizes with the font swapped, so nothing falls back to the system font.

| Slot | Size / weight | Use |
|---|---|---|
| `headlineLarge` | 30 / Bold | Page hero |
| `headlineMedium` | 26 / Bold | |
| `headlineSmall` | 22 / Bold | `Dialog` title |
| `titleLarge` | 18 / Bold | |
| `titleMedium` | 16 / Bold | Large button label (as Medium) |
| `titleSmall` | 14 / Bold | |
| `bodyLarge` | 16 / Medium, `contentSecondary` | Lead/empty-state text |
| `bodyMedium` | 14 / Regular | Default body, input text |
| `bodySmall` | 12 / Regular | Captions |
| `labelLarge` | 14 / Regular | Small button label (as Medium), field label (as SemiBold) |
| `labelSmall` | 11 / Regular | Base for the named styles below |

Named styles in `CerealTypography`:

| Style | Spec | Use |
|---|---|---|
| `sectionLabel` | 11 / Bold, +0.14em | `SectionLabel`, usually uppercase |
| `controlLabel` | 14 / SemiBold | Labels next to controls |
| `paneTitle` | 19 / Bold, −0.01em | Pane/screen titles |
| `statusChipLabel` | 13 / Bold, +0.02em | `Pill` |
| `rowTitle` | `bodyMedium` SemiBold | Table/list row names |
| `logLine` | Monospace 13 / 19 line height | Log output |

### Not yet tokenized

- **Elevation and motion** have no tokens. Components use Material defaults (`ButtonDefaults.buttonElevation()`, `CardDefaults.cardElevation()`). The one custom animation is the 1000ms linear spin in `SyncIconButton`.
- **Local constants that should be tokens:** `InputBg #1C1C1C`, `InputPlaceholder #5A5A5A` (`SettingsComponents.kt`), `PaneTableRowDividerColor #232323`, `PaneTableHeaderBackground #1A1A1A`, plus the `PaneTable*` dimensions (`PaneTable.kt`).
- Outside `theme/`, `presentation/` has about 77 hardcoded `Color(0x…)` literals and 45 hardcoded `.sp` sizes. Run `/design-system audit` for the full list.

---

## 2. Components

### Catalog

Call counts are the number of files outside the defining file that use the component (as of this writing).

| Need | Use | File | In use |
|---|---|---|---|
| Any text | `CerealText` | `CerealText.kt` | everywhere |
| Button with label | `CerealButton` | `Button.kt` | 21 |
| Low-emphasis/link button | `CerealTextButton` | `Button.kt` | 3 |
| Bordered button | `CerealOutlinedButton` | `Button.kt` | 6 |
| Toolbar action | `CerealToolbarButton` | `button/CerealToolbarButton.kt` | 3 |
| Icon-only button | `CerealIconButton`, `CerealOutlinedIconButton` | `Button.kt` | 6, 4 |
| Refresh/sync icon with states | `SyncIconButton` | `button/LoadingIconButton.kt` | — |
| Text input (raw) | `CerealTextField` | `CerealTextField.kt` | 11 |
| Text input bound to form state | `TextField`, `SecretTextField`, `DropdownTextField`, `SwitchField`, `FileField`, `OptionCardsField` | `fields/` | — |
| Error text under any field | `FormField` | `fields/FormField.kt` | 6 |
| Status / price / filter chip | `Pill` | `chip/Pill.kt` | 4 |
| Status indicator | `StatusDot` | `StatusDot.kt` | 2 |
| Count badge | `CerealBadge` | `CerealBadge.kt` | 1 |
| Container | `CerealCard` | `Card.kt` | 5 |
| Group caption | `SectionLabel` | `SectionLabel.kt` | 4 |
| Inline info/warning | `InfoBox`, `WarningBox` | `InfoBox.kt`, `WarningBox.kt` | 2, 2 |
| Hover help | `InfoTooltip` | `InfoTooltip.kt` | 2 |
| Modal with custom content | `Dialog` | `Dialog.kt` | 6 |
| Yes/no question | `ConfirmationDialog` | `ConfirmationDialog.kt` | 8 |
| Transient message | `CerealSnackbarHost` | `CerealSnackbar.kt` | 4 |
| Empty / loading state | `EmptyListView`, `LoadingView` | `StateViews.kt` | 3, 3 |
| Spinner / progress | `CerealCircularProgressIndicator`, `LinearProgressBar`, `ActivityIndicator` | | — |
| List/table screen | `PaneScreen`, `PaneToolbar`, `PaneTableCard`, `PaneTableHeader`, `PaneTableRow`, `PaneDetailsHeader`, `PaneMetaStrip` | `table/PaneTable.kt` | — |
| Settings page | `SettingsSectionCard`, `SettingsToggleRow`, `SettingsActionRow`, `SettingsFieldRow`, `SettingsTextInput` | `SettingsComponents.kt` | — |

**Unused, candidates for deletion:** `H1Text`–`H6Text`, `SubtitleText`, `BodyText`, `OverlineText` (`Text.kt`; `Body2Text`, `Subtitle2Text` and `CaptionText` are still used), `CerealElevatedButton`, `CerealFilledTonalButton`, `CerealFloatingActionButton`, `CerealFilledIconButton`, `CerealFilledTonalIconButton`, and all four `*IconToggleButton` wrappers (`Button.kt`), and `TwoColumnLayout`.

The full entries below cover the components that set the system's look. The rest are thin wrappers, and their KDoc covers them.

---

### Component: CerealButton

#### Description
The app's filled button. Use it for any action with a text label, optionally with a leading or trailing icon. Every Cereal button shares the `small` shape (6dp), the two size heights and the label styles. For a borderless or bordered look, use `CerealTextButton` or `CerealOutlinedButton`, which take the same `type` and `size`.

#### Variants (`type: CerealButtonType`)
| Variant | Fill / content | Use when |
|---|---|---|
| `Primary` (default) | brand `primary`, darkened for contrast / white | The main action on a screen or dialog. Use at most one per area. |
| `Secondary` | brand `secondary`, darkened / white | Same as Primary today, because `secondary` equals `primary`. Prefer `Primary` or `Neutral`. |
| `Surface` | `surface` / `onSurface` | Actions sitting on a contrasting background |
| `Success` | `success` / `onSuccess` (dark text) | Positive, go actions: Run, Start |
| `Neutral` | `surfaceVariant` + 1dp `border` / `onSurface` | Supporting actions: Cancel, Import, Settings |

`CerealToolbarButton` maps toolbar intents onto these: `Primary`→Primary, `Green`→Success, `Flat`→Neutral, and `Ghost`→`CerealOutlinedButton(Neutral)`.

#### Sizes (`size: CerealButtonSize`)
| Size | Min height | Label |
|---|---|---|
| `Small` (default) | 40dp | `labelLarge`, Medium (14) |
| `Large` | 56dp | `titleMedium`, Medium (16). Use for auth and onboarding CTAs. |

#### Properties
| Property | Type | Default | Description |
|---|---|---|---|
| `onClick` | `() -> Unit` | — | Click handler |
| `text` | `String?` | `null` | Label. Ignored when `content` is set. |
| `leadingIcon` / `trailingIcon` | `DrawableResource?` | `null` | Icon at 18dp (`ButtonDefaults.IconSize`), 8dp from the label |
| `iconContentDescription` | `String?` | `null` | Accessible name for the icon. **Required for icon-only buttons.** |
| `enabled` | `Boolean` | `true` | |
| `type` | `CerealButtonType` | `Primary` | See variants |
| `size` | `CerealButtonSize` | `Small` | See sizes |
| `contentPadding` | `PaddingValues` | M3 default (icon-aware) | |
| `content` | `RowScope.() -> Unit` | `null` | Custom content. The label style is still provided. |

#### States
| State | Visual | Behavior |
|---|---|---|
| Default | Variant fill, 6dp radius, default M3 elevation | — |
| Hover / Pressed | M3 state layer over the fill | Ripple on press |
| Focused | M3 focus state layer | Activated with Enter or Space |
| Disabled | M3 disabled colors (12% fill / 38% content). `Success` uses green at 50%. | Not clickable |
| Loading | **No built-in state.** Swap the label for a `CerealCircularProgressIndicator` via `content` and set `enabled = false`. | — |

#### Accessibility
- **Role:** Button (from M3 `Button`).
- **Keyboard:** Tab to focus, Enter or Space to activate.
- **Contrast:** `Primary` and `Secondary` fills are darkened step by step (`readableUnderWhite`) until white text reaches WCAG AA 4.5:1. This also covers white-label brand colors. `Success` uses dark `onSuccess` text for the same reason.
- **Screen reader:** Announces the label. A trailing icon reuses `text` as its description, so it isn't announced as unlabeled.

#### Do's and Don'ts
| ✅ Do | ❌ Don't |
|---|---|
| Use one `Primary` per screen or dialog and `Neutral` for the rest | Put two red buttons side by side |
| Use `Success` for Run/Start | Use `Success` for a generic "OK" |
| Pass `iconContentDescription` on icon-only buttons | Leave an icon-only button unlabeled |
| Pass `size = Large` for full-width auth CTAs | Set `Modifier.height(…)` yourself; heights come from `size` |
| Read the accent from the theme | Pass a hardcoded red, which breaks white-label brands |

#### Code example
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm)) {
    CerealButton(onClick = onCancel, text = stringResource(Res.string.cancel), type = CerealButtonType.Neutral)
    CerealButton(onClick = onRun, text = stringResource(Res.string.start), leadingIcon = Res.drawable.ic_play, type = CerealButtonType.Success)
}
```

---

### Component: CerealTextField

#### Description
The app's only text input. It is a filled box with a 1dp border that darkens on focus and turns red on error. It draws **only the box**. Label, helper and error text belong to the caller. For inputs backed by a `TextFieldState`, use `fields/TextField` instead. It adds a label (`FieldLabel`: `labelLarge` SemiBold, `contentSecondary`), an error line (`FormField`), focus tracking and validation display.

#### Variants
| Variant | Use when |
|---|---|
| Single-line (default) | 38dp tall, content centered |
| Multi-line (`singleLine = false`) | At least 92dp tall, 12dp vertical padding, content top-aligned |
| Masked | `visualTransformation`. `SecretTextField` wraps this for secrets. |
| With icons | `leadingIcon` / `trailingIcon` slots, 8dp gap |

#### Properties
| Property | Type | Default | Description |
|---|---|---|---|
| `value` / `onValueChange` | `String` / `(String) -> Unit` | — | Hoisted state |
| `placeholder` | `String?` | `null` | Shown when empty, in `#5A5A5A` |
| `isError` | `Boolean` | `false` | Red border |
| `enabled` | `Boolean` | `true` | |
| `singleLine` | `Boolean` | `true` | See variants |
| `textStyle` | `TextStyle` | `bodyMedium` | |
| `visualTransformation`, `keyboardOptions`, `keyboardActions` | | defaults | Passed through to `BasicTextField` |
| `leadingIcon` / `trailingIcon` | `@Composable (() -> Unit)?` | `null` | |

#### States
| State | Visual | Behavior |
|---|---|---|
| Default | Fill `#1C1C1C`, border `border` (`#2A2A2A`), 8dp radius | — |
| Focused | Border `borderDark` (`#3E3E3E`), cursor in brand `primary` | Typing |
| Error | Border `error` | The caller shows the message, via `FormField` |
| Disabled | Text in `contentTertiary` | Not editable |

#### Accessibility
- **Role:** Editable text (from `BasicTextField`).
- **Keyboard:** Tab in or out. `imeAction` / `onImeAction` handle Enter (`TextField` defaults to `Next`).
- **Screen reader:** The box has no visible label of its own. Use `TextField`, or set `semantics { contentDescription = … }`. `TextField(labelVisible = false)` does this for you when the label is drawn elsewhere.
- **Gap:** The focus change (`#2A2A2A` → `#3E3E3E`) is subtle, about 1.3:1 against the fill. Keyboard users may not see focus clearly.

#### Do's and Don'ts
| ✅ Do | ❌ Don't |
|---|---|
| Use `fields/TextField` for anything bound to form state | Hand-roll label, error and focus handling around `CerealTextField` |
| Show the error message below with `FormField` | Rely on the red border alone |
| Use `SecretTextField` for keys and passwords | Build your own masking |
| Route every input through `CerealTextField` | Use M3 `TextField`/`OutlinedTextField` directly |

#### Code example
```kotlin
TextField(
    title = stringResource(Res.string.webhook_url),
    state = viewModel.webhookUrl,
    imeAction = ImeAction.Done,
    onImeAction = viewModel::save,
)
```

---

### Component: Pill

#### Description
A pill-shaped chip with a short label in `CerealTypography.statusChipLabel`. Use it for status (running, error, success), trial or price badges on script cards, and active-filter chips. The caller picks the colors. There are no preset variants, so use the recipes below.

#### Variants (recipes)
| Variant | Settings | Use when |
|---|---|---|
| Status | `leadingDot = true`, content = status color, background = status color at about 15% alpha | Task state |
| Badge | No dot, tinted background | Trial, price |
| Outlined | `borderColor` set | Low-emphasis tag |
| Filter chip | `trailingIcon = Icons.Default.Close`, `onClick` | Removable active filter |

#### Properties
| Property | Type | Default | Description |
|---|---|---|---|
| `text` | `String` | — | Label |
| `contentColor` / `backgroundColor` | `Color` | — | Required. Text, dot and icon use `contentColor`. |
| `borderColor` | `Color?` | `null` | 1dp border |
| `leadingDot` | `Boolean` | `false` | 6dp `StatusDot` |
| `trailingIcon` / `trailingIconContentDescription` | `ImageVector?` / `String?` | `null` | 12dp icon |
| `onClick` | `(() -> Unit)?` | `null` | Makes the pill clickable |
| `contentPadding` | `PaddingValues` | 10dp × 4dp | |
| `textStyle` | `TextStyle?` | `statusChipLabel` | |

#### States
| State | Visual | Behavior |
|---|---|---|
| Default | Fill, `CircleShape`, 6dp gap between parts | — |
| Hover / Pressed (with `onClick`) | Ripple clipped to the pill | Click |

#### Accessibility
- **Gap:** A clickable pill uses `Modifier.clickable` without `role = Role.Button`, so screen readers don't announce it as a button. Add the role when the pill is interactive.
- Never use the dot color alone to show status. The label must say it too.

#### Code example
```kotlin
val green = CerealTheme.colorScheme.success
Pill(text = "Running", contentColor = green, backgroundColor = green.copy(alpha = 0.15f), leadingDot = true)
```

---

### Component: CerealCard

#### Description
The standard container: `cardDark` fill, 1dp `borderDark` border, `extraLarge` (16dp) radius, and default M3 card elevation. `Dialog` uses it for its body. Padding is up to the caller. Use `CerealTheme.spacing.xl` (20dp) for the inside.

#### Properties
| Property | Type | Default | Description |
|---|---|---|---|
| `modifier` | `Modifier` | `Modifier` | |
| `content` | `@Composable () -> Unit` | — | |

No variants or interactive states. To make a card clickable, put `Modifier.clickable` on the modifier and add a `role`.

---

### Component: Dialog and ConfirmationDialog

#### Description
- **`Dialog`**: a modal for custom content, such as a form, details or an import flow. It is a centered `CerealCard` (600×500dp by default) over a 30% black scrim. It has a `TopAppBar` with the title in `headlineSmall` and a close icon button. Clicking outside does **not** close it.
- **`ConfirmationDialog`**: an M3 `AlertDialog` (at least 400dp wide) with a title, a message and two `CerealTextButton`s (confirm / cancel).

#### Properties: Dialog
| Property | Type | Default | Description |
|---|---|---|---|
| `title` | `String` | — | Header |
| `onDismissRequest` | `() -> Unit` | — | Called by the close button |
| `dismissButtonEnabled` | `Boolean` | `true` | Turn off during a blocking operation |
| `modifier` | `Modifier` | 600×500dp | Override the size here |
| `content` | `@Composable () -> Unit` | — | Laid out in a `Row` filling the remaining height |

#### Properties: ConfirmationDialog
| Property | Type | Default | Description |
|---|---|---|---|
| `title` / `message` | `String` | — | |
| `confirmButtonText` / `dismissButtonText` | `String` | "Confirm" / "Cancel" | Use a verb ("Delete task") instead of "Confirm" |
| `onConfirm` / `onDismiss` | `() -> Unit` | — | |

#### Accessibility
- **Keyboard:** Both take focus when they open, and Tab moves between their controls. `ConfirmationDialog` (`AlertDialog`) closes on Escape. `Dialog` is a plain `Popup` with no key handler, so Escape support isn't verified. Test it before relying on it.
- **Gap:** In `ConfirmationDialog` both buttons are `Primary` text buttons and look identical. For destructive actions the confirm button isn't marked as dangerous.

#### Do's and Don'ts
| ✅ Do | ❌ Don't |
|---|---|
| Use `ConfirmationDialog` for irreversible actions | Build an ad-hoc `AlertDialog` |
| Label the confirm button with the action | Use "OK" / "Confirm" for a delete |
| Disable the close button while saving | Let users close mid-save and lose data |

---

### Component: InfoBox and WarningBox

#### Description
Full-width inline callouts with an icon (20dp) and text, 12dp padding and the `small` radius.

| Component | Fill | Icon | Text | Use when |
|---|---|---|---|---|
| `InfoBox` | `info` at 8% | Info, `info` at 70% | `bodyMedium`, `onSurface` at 80%. URLs become links (`ClickableUrlText`). | Neutral guidance, setup hints |
| `WarningBox` | `warning` at 15% | Warning, `warning` | `Body2Text`, `onSurface` | Something risky the user should know before acting |

No error variant exists. Errors go through `ErrorResolver` → `ErrorDialog`/`ErrorView`, or through `FormField` for fields. Both icons carry a localized content description ("Information" / "Warning"), so screen readers announce the kind of callout before the text.

---

## 3. Patterns

- **Forms:** use a field-state class from `fields/state/` with a validator from `fields/validator/`, rendered by its `fields/` composable. The field composable wraps `FormField`, which wraps `CerealTextField` and friends. Labels sit 6dp above the box, and errors show 4dp below it.
- **List and table screens:** `PaneScreen(title)` → `PaneToolbar` with `CerealToolbarButton`s → `PaneTableCard` → `PaneTableHeader` + `PaneTableRow`s. Drill-down views swap the toolbar for `PaneDetailsHeader` + `PaneMetaStrip`. Pane padding is 24dp, rows are at least 52dp, and the corner radius is 10dp.
- **Settings:** `SettingsSectionCard(title)` contains `SettingsToggleRow`, `SettingsActionRow` and `SettingsFieldRow`.
- **Feedback:** use a snackbar (`CerealSnackbarHost`, brand fill) for transient success. Use `InfoBox`/`WarningBox` for persistent context, `ConfirmationDialog` before destructive actions, and `ErrorResolver` for errors.
- **Empty and loading:** every list `ViewState` renders `LoadingView` or `EmptyListView(message)`, never a blank pane.
