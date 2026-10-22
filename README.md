# ARGN Navigation Assist

Android experimental anti-spoofing navigation companion. The app never disables hardware GNSS. It gates GNSS fixes according to Kyiv / Kyiv oblast air alerts, performs basic speed/jump checks, and optionally publishes accepted fixes as Android mock locations. This is **not** a certified navigation system; spoofing can evade these heuristics.

Build with GitHub Actions `Android Debug APK`. Configure your own HTTPS alert status endpoint returning `{ "kyiv": false, "oblast": false }` (true means active alarm); do not commit tokens. Enable Developer Options > Select mock location app to test. The service uses a foreground notification.
