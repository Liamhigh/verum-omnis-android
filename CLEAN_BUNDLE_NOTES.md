# Verum Omnis Clean Rebuild Bundle

This bundle is a cleaned source snapshot of the current app.

Included:
- Android app source and resources
- constitutional assets, legal packs, templates, sealing PDFs, and model files
- scripts and tests
- current debug APK in `deliverables/app-debug.apk`
- signing and certificate material already present in the project root

Excluded as noise:
- `.gradle/`, `.idea/`, `.kotlin/`
- all `build/` folders except the copied APK deliverable
- temporary logs and ad-hoc local output files
- `local.properties`

Intent:
- give us one cleaner canonical package to rebuild from
- preserve sealing, watermarking, PDF, legal-pack, and UI assets
- reduce experimental clutter without changing the actual logic in source
