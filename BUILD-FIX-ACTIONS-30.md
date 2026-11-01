# Fix for GitHub Actions build #30

## Cause
The repository's remaining `app/src/main/java/com/retropod/player/ui/screens/Screens.kt` references `androidx.navigation.NavController`, `navigate`, and `popBackStack`. The app module did not declare Navigation Compose, so Kotlin could not resolve those symbols.

## Change
Added:
`implementation("androidx.navigation:navigation-compose:2.8.5")`

This provides `NavController`, `navigate`, and `popBackStack` for Compose navigation. It is compatible with the project's Compose BOM 2024.12.01.

## Important
The uploaded source ZIP did not contain `ui/screens/Screens.kt`, but GitHub Actions still compiled that path. This indicates the file remained in the GitHub repository (for example, because a ZIP was extracted/merged over existing files rather than replacing the repository tree). This dependency is added so that the leftover file can compile rather than relying on its deletion.

## Verification
ZIP integrity and project XML structure were checked. Android Gradle compilation was not run in this environment. Re-run the GitHub Actions workflow to confirm.
