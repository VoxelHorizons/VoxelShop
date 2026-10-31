# VoxelShop — Release engineering and operator checklist

This project uses read-only CI for pull requests and branch pushes. A normal commit to `main` **must not** create or overwrite a GitHub Release. CI artifacts are test outputs, not certified public distributions.

## Version policy

- Adopt independent [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`, with pre-releases such as `1.0.0-rc.1`.
- Minecraft server versions belong in distribution JAR names and the tested compatibility table, **not** in the plugin's SemVer.
- Retain the existing Maven `-SNAPSHOT` version until the product passes its release checks; do not mark an unverified build stable merely to exercise the workflow.
- Before a release, update every module/parent `pom.xml` as appropriate. Check plugin descriptor versions, supported server versions, dependencies, and current changelog.
- Keep the JARs produced by a single reviewed release build together with a `SHA256SUMS` file.

## No stable release yet

VoxelShop is not part of the first public launch. Its CI is build/test only, and it intentionally has no release workflow until gameplay, currency, crash safety and compatibility are certified. Do not create a `v1.0.0` tag for this repository as part of the initial ecosystem launch.
