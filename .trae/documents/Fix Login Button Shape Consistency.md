I have identified the issue:

**Issue**: The user noticed that when the login button is in the "Loading" state (showing "Logging in..."), the corner radius seems to increase or look different.

**Cause**: In `LoginScreen.kt`, the `Button` component uses `ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))` to set the shape. However, inside the `Button`, when `appModel.isLoading` is true, the content changes.

Wait, looking closely at `LoginScreen.kt`:
```kotlin
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
```
The shape is explicitly set to `8.dp`. It shouldn't change dynamically based on content.

However, `androidx.tv.material3.Button` might have internal state changes or animations that affect appearance when disabled (since `enabled = !appModel.isLoading`).

When `isLoading` is true, `enabled` becomes `false`.
The `ButtonDefaults.shape` might have different defaults for disabled state if not fully overridden, OR the visual perception changes because the button is disabled.

But actually, looking at `androidx.tv.material3.ButtonDefaults.shape`, it accepts a `shape` parameter.

Let's look at the implementation of `Button` in `LoginScreen.kt` again.

```kotlin
                Button(
                    onClick = { ... },
                    enabled = !appModel.isLoading,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp)
                        .focusRequester(loginButtonFocusRequester),
                    colors = ButtonDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                )
```

The `Button` from `androidx.tv.material3` has a `shape` parameter that takes a `ButtonShape`. `ButtonDefaults.shape(shape = ...)` creates a `ButtonShape` where all states use the same shape *unless* specified otherwise.

However, when `enabled` is false, the button enters a disabled state.

If the user says "round corner becomes bigger", maybe it's reverting to a default shape (usually Pill shape / 50% height) in the disabled state?

In TV Material3, `ButtonDefaults.shape()` allows specifying `focusedShape`, `pressedShape`, `disabledShape`, etc.
If I only provide `shape`, it *should* apply to all.

Let's check `ButtonDefaults.shape` signature. It typically has parameters like `shape`, `focusedShape`, `pressedShape`, `disabledShape`, `focusedDisabledShape`.

If `disabledShape` defaults to `CircleShape` (or similar) while `shape` defaults to something else, that would explain it. Or if `Button` internally switches shape.

To fix this decisively, I should explicitly set `disabledShape` to match the desired `RoundedCornerShape(8.dp)`.

**Plan**:
1.  Modify `LoginScreen.kt`.
2.  Update `ButtonDefaults.shape` usage to explicitly set `disabledShape` (and others if needed) to `RoundedCornerShape(8.dp)` to ensure consistency across all states.

Actually, looking at the code again:
```kotlin
shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
```
This sets the *base* shape.

I will modify it to:
```kotlin
shape = ButtonDefaults.shape(
    shape = RoundedCornerShape(8.dp),
    focusedShape = RoundedCornerShape(8.dp),
    pressedShape = RoundedCornerShape(8.dp),
    disabledShape = RoundedCornerShape(8.dp),
    focusedDisabledShape = RoundedCornerShape(8.dp)
)
```
This guarantees the shape never changes regardless of state.

Wait, `ButtonDefaults.shape` in `androidx.tv.material3` might simpler or different. I'll just set the main `shape` parameter of the `Button` composable if it accepts a `Shape` directly, BUT `tv.material3.Button` takes a `ButtonShape`.

The `ButtonDefaults.shape()` function parameters are:
`shape: Shape`, `focusedShape: Shape`, `pressedShape: Shape`, `disabledShape: Shape`, `focusedDisabledShape: Shape`.

So I will define a `val buttonShape = RoundedCornerShape(8.dp)` and pass it to all parameters of `ButtonDefaults.shape`.

### Plan:
1.  Define `val buttonShape = RoundedCornerShape(8.dp)` inside `LoginScreen` (or inline it).
2.  Update the `Button`'s `shape` parameter to explicitly define `disabledShape` and `focusedDisabledShape` to match `RoundedCornerShape(8.dp)`.

This ensures that when `isLoading` is true (and thus `enabled` is false), the button retains its 8.dp corner radius instead of morphing to a default (likely pill) shape.