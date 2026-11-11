import com.fs.starfarer.api.BaseModPlugin;

/**
 * Companion-mod plugin for the StarUIFramework test suite. Runs after every other mod's onApplicationLoad
 * (StarUIFramework's included), then hands over to the Kotlin suite compiled into the test-only mod jar.
 * Test-only: lives in native-smoke/, never in the shipped jar.
 */
public final class StarUITestSuiteProbe extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        // Runs now on the GL thread, or on the first title-screen frame under Fast Rendering (see StarUITestSuite.start).
        StarUITestSuite.start();
    }
}
