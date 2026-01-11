I will implement the focus logic as requested:

1.  **Add Focus Requesters**: Create `FocusRequester` instances for the server URL input and the Login button.
2.  **Determine Initial Focus**: In a `LaunchedEffect`, check if `serverUrl`, `username`, and `password` (initialized from `appModel`) are all non-empty.
    *   If all are present: Request focus on the **Login Button**.
    *   Otherwise: Request focus on the **Server URL Input**.
3.  **Apply Focus Requesters**: Attach the `FocusRequester`s to the corresponding UI components (`TvInputButton` and `Button`).

This will ensure the user experience is optimized for both new logins and re-logins with saved credentials.