package hotreloadhelper;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Runs the console command hot.sh console sent. The game does not let mods read files, so the reload agent reads the
 * request and puts it in the system property KEY ("id" newline "command"); this polls the property on the game's
 * thread, where commands must run, and logs HOTCONSOLE markers around Console Commands' own output for hot.sh to read.
 * Every failure is caught: an exception from a campaign script or combat plugin would end the game.
 */
public final class HotHelperConsole {
    public static final String KEY = "hotreload.console";
    private static final Logger LOG = Global.getLogger(HotHelperConsole.class);
    private static String last = System.getProperty(KEY);
    private static Boolean enabled;

    private HotHelperConsole() {}

    static boolean consoleEnabled() {
        if (enabled == null) {
            try {
                enabled = Global.getSettings().getModManager().isModEnabled("lw_console");
            } catch (Throwable t) {
                enabled = false;
            }
        }
        return enabled;
    }

    static void poll(String context) {
        String request = System.getProperty(KEY);
        if (request == null || request.equals(last)) return;
        last = request;
        int nl = request.indexOf('\n');
        String id = nl < 0 ? request : request.substring(0, nl);
        String command = nl < 0 ? "" : request.substring(nl + 1).trim();
        LOG.info("HOTCONSOLE " + id + " start (" + context + "): " + command);
        try {
            Run.command(command, context);
        } catch (Throwable t) {
            LOG.info("HOTCONSOLE " + id + " failed: " + t);
        }
        LOG.info("HOTCONSOLE " + id + " end");
    }

    /** The only class that touches Console Commands: loaded only once a command runs, with the mod enabled. */
    private static final class Run {
        static void command(String command, String context) {
            org.lazywizard.console.Console.parseInput(command,
                    org.lazywizard.console.BaseCommand.CommandContext.valueOf(context));
        }
    }
}
