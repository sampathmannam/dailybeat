Google-free candidate for F-Droid review, signed with DailyBeat's permanent signing certificate.

This separate prerelease channel supplies the reference APK for reproducible builds. It does not
mean the app has been accepted into F-Droid. Existing Obtainium stable updates remain on the regular
release channel.

The store build uses Android location APIs and contains no Google Play Services. It is built with
`-PdailybeatFoss=true -PdailybeatStore=true`; it does not embed private managed-backup configuration.
Notes, local drafts, capture, map viewing, Tamil Nadu map downloads and PDF/ZIP sharing work without
an account. Optional cloud AI is off by default. Online maps remain on by default and can be disabled.

F-Droid rebuilds the public source and must verify it against this APK before using our signature.
The source recipe, dependency and asset provenance, privacy information and build instructions are
in the source repository. Review the F-Droid submission for the current inclusion status.
