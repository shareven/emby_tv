I will implement the requested changes to the login flow and web server management.

### 1. Update `AppModel.kt`
- Add properties to expose saved credentials: `savedServerUrl`, `savedUsername`, `savedPassword`.
- Update `login()` method to save `username` and `password` to SharedPreferences upon successful authentication.

### 2. Update `LoginScreen.kt`
- **Initialize State**: Initialize `serverUrl`, `username`, and `password` states using the saved values from `AppModel`.
- **Manage Web Server**:
  - Store the `LocalServer` instance in a state or reference.
  - Ensure the web server is stopped when:
    - Login is successful (before navigating away).
    - The `LoginScreen` is disposed (using `DisposableEffect`).

### 3. Verify
- Build and verify the changes.