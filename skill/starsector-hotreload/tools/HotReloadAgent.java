import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Test-only Java agent: reloads a mod's classes in the running game from a freshly built jar.
 *
 * The game loads every mod jar through one class loader and keeps the jars it opened (the staged copies). The agent
 * reads the new build (the source jar), compares it class by class with what the game has, and then
 * - redefines every changed class that is loaded, in one batch, with Instrumentation.redefineClasses. On the
 *   JetBrains Runtime with -XX:+AllowEnhancedClassRedefinition that may add, remove and change methods and fields and
 *   change the class hierarchy; on a plain JVM only method bodies may change;
 * - loads (without initializing) every changed class that is not loaded yet and redefines it too, because the class
 *   loader would otherwise load the old version from the staged jar later;
 * - puts new classes where the mod class loader finds them: a folder added to it (URLClassLoader.addURL), or, for a
 *   loader of another kind, defined right away.
 * Other agents' transformers (PatchLib) see the new bytes as they did the old ones, so patches stay applied.
 *
 * Control folder (the agent's argument): watch.txt lists SOURCE_JAR TAB STAGED_JAR per line. A file named "request"
 * reloads every watched jar now (its text is the request id); the result goes to result-ID.txt and last.txt. Without
 * a file named no-auto, a source jar that changed and then stayed the same for half a second is reloaded by itself.
 * Every result is also appended to reload.log, printed to stdout and logged to the game log.
 * Each reload that changed something raises the system property starsector.hotreload.generation by one, so a mod may
 * rebuild its open UI when it changes (System.getProperty is allowed to mods).
 */
public final class HotReloadAgent {
    private static final long POLL_MS = 250;
    private static final int LIST_MAX = 25;

    private static Instrumentation inst;
    private static Path control;
    private static boolean enhanced;
    private static int generation;
    private static int autoCount;
    private static Object gameLog;
    private static Method gameLogInfo;
    private static final List<Watched> watched = new ArrayList<>();

    private static final class Watched {
        final Path source;
        final Path staged;
        final String label;
        /** What the game has for each class: the staged jar at first, then each reloaded version. */
        Map<String, byte[]> current;
        /** Classes in the staged jar: the class loader finds those there, so they must be loaded before a redefine. */
        Set<String> inStagedJar;
        Path hotDir;
        boolean hotDirAdded;
        long seenTime = -1, seenSize = -1, candTime = -2, candSize = -2;
        /** A change seen while the mod was not loaded yet, or a read that failed; tried again on the next poll. */
        boolean retry;
        /** The last reload that changed something, for a manual reload that finds the build already in. */
        String lastResult;
        String lastTime;

        Watched(Path source, Path staged, int index) {
            this.source = source;
            this.staged = staged;
            this.label = source.getFileName().toString();
            this.hotDir = control.resolve("classes-" + index);
        }
    }

    public static void premain(String args, Instrumentation instrumentation) { start(args, instrumentation); }

    public static void agentmain(String args, Instrumentation instrumentation) { start(args, instrumentation); }

    private static void start(String args, Instrumentation instrumentation) {
        inst = instrumentation;
        try {
            if (args == null || args.isBlank()) throw new IllegalArgumentException("no control folder argument");
            control = Paths.get(args.trim());
            Files.createDirectories(control);
            enhanced = vmFlag("AllowEnhancedClassRedefinition");
            // addURL and defineClass are protected: open java.net and java.lang to this agent alone
            Module self = HotReloadAgent.class.getModule();
            inst.redefineModule(Object.class.getModule(), Set.of(), Map.of(),
                Map.of("java.lang", Set.of(self), "java.net", Set.of(self)), Set.of(), Map.of());
            Path list = control.resolve("watch.txt");
            int index = 0;
            if (Files.exists(list)) {
                for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\t");
                    if (parts.length != 2 || parts[0].isBlank()) continue;
                    Watched w = new Watched(Paths.get(parts[0].trim()), Paths.get(parts[1].trim()), index++);
                    stat(w);
                    w.seenTime = w.candTime;
                    w.seenSize = w.candSize;
                    watched.add(w);
                }
            }
            System.setProperty("starsector.hotreload.generation", "0");
            Thread thread = new Thread(HotReloadAgent::loop, "HotReload");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
            report("HOTRELOAD on: " + watched.size() + " jar(s) watched, enhanced redefinition "
                + (enhanced ? "on" : "OFF (only method bodies may change)") + ", auto "
                + (auto() ? "on" : "off") + ", control " + control);
        } catch (Throwable t) {
            System.out.println("HOTRELOAD off: " + t);
        }
    }

    private static boolean auto() { return !Files.exists(control.resolve("no-auto")); }

    private static void loop() {
        while (true) {
            try {
                Thread.sleep(POLL_MS);
                Path request = control.resolve("request");
                if (Files.exists(request)) {
                    String id = new String(Files.readAllBytes(request), StandardCharsets.UTF_8).trim();
                    Files.deleteIfExists(request);
                    StringBuilder out = new StringBuilder();
                    for (Watched w : watched) out.append(reload(w, true));
                    if (watched.isEmpty()) out.append("RELOAD_FAILED: no jars are watched (watch.txt is empty)\n");
                    finish(id.isEmpty() ? "manual" : id, out.toString());
                    continue;
                }
                boolean auto = auto();
                for (Watched w : watched) {
                    if (!auto && !w.retry) continue;
                    boolean changed = stat(w);
                    if (changed || w.retry) {
                        String result = reload(w, false);
                        if (!result.isEmpty()) finish("auto-" + (++autoCount), result);
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                System.out.println("HOTRELOAD error: " + t);
            }
        }
    }

    /** True once the source jar has changed and then kept the same time and size for one poll (the build is done). */
    private static boolean stat(Watched w) {
        long time, size;
        try {
            FileTime t = Files.getLastModifiedTime(w.source);
            time = t.toMillis();
            size = Files.size(w.source);
        } catch (IOException e) {
            time = -1;
            size = -1;
        }
        boolean stable = time == w.candTime && size == w.candSize;
        w.candTime = time;
        w.candSize = size;
        if (stable && time >= 0 && (time != w.seenTime || size != w.seenSize)) {
            w.seenTime = time;
            w.seenSize = size;
            return true;
        }
        return false;
    }

    private static void finish(String id, String text) throws IOException {
        report(text.strip());
        byte[] bytes = (text.endsWith("\n") ? text : text + "\n").getBytes(StandardCharsets.UTF_8);
        Path tmp = control.resolve("result.tmp");
        Files.write(tmp, bytes);
        Files.copy(tmp, control.resolve("last.txt"), StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, control.resolve("result-" + id.replaceAll("[^A-Za-z0-9._-]", "_") + ".txt"),
            StandardCopyOption.REPLACE_EXISTING);
    }

    private static void report(String text) {
        System.out.println(text);
        try {
            Files.write(control.resolve("reload.log"), (text + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
        try {
            if (gameLog == null) {
                Class<?> logger = Class.forName("org.apache.log4j.Logger");
                gameLog = logger.getMethod("getLogger", String.class).invoke(null, "HotReload");
                gameLogInfo = logger.getMethod("info", Object.class);
            }
            for (String line : text.split("\n")) gameLogInfo.invoke(gameLog, line);
        } catch (Throwable ignored) {
        }
    }

    /** Reloads one jar. Returns the result lines; empty when an automatic reload found nothing to do. */
    private static String reload(Watched w, boolean manual) {
        long start = System.nanoTime();
        String head = w.label + ": ";
        Map<String, byte[]> next;
        try {
            next = readJar(w.source);
        } catch (IOException e) {
            // a jar the build is still writing; the next poll tries again
            w.retry = !manual;
            return manual ? "RELOAD_FAILED: " + head + "cannot read " + w.source + ": " + e + "\n" : "";
        }
        try {
            if (w.current == null) {
                w.current = readJar(w.staged);
                w.inStagedJar = new HashSet<>(w.current.keySet());
            }
        } catch (IOException e) {
            w.retry = false;
            return "RELOAD_FAILED: " + head + "cannot read the staged jar " + w.staged + ": " + e + "\n";
        }

        List<String> changed = new ArrayList<>(), added = new ArrayList<>(), removed = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : next.entrySet()) {
            byte[] old = w.current.get(e.getKey());
            // a class removed by an earlier reload and now back is still in the staged jar: it is a change, not new
            if (old == null && !w.inStagedJar.contains(e.getKey())) added.add(e.getKey());
            else if (old == null || !Arrays.equals(old, e.getValue())) changed.add(e.getKey());
        }
        for (String name : w.current.keySet()) if (!next.containsKey(name)) removed.add(name);
        if (changed.isEmpty() && added.isEmpty() && removed.isEmpty()) {
            w.retry = false;
            if (!manual) return "";
            return "UNCHANGED: " + head + "the game already runs this build"
                + (w.lastResult == null ? "\n" : "; it was reloaded at " + w.lastTime + ":\n" + w.lastResult.indent(2));
        }

        // the mod's class loader, found from its classes that are loaded (by name, so any loader kind works)
        Map<String, Class<?>> loaded = new HashMap<>();
        Map<ClassLoader, Integer> votes = new HashMap<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.isArray() || c.isHidden() || c.getClassLoader() == null) continue;
            String name = c.getName().replace('.', '/');
            if (!w.current.containsKey(name) && !next.containsKey(name)) continue;
            loaded.put(name, c);
            votes.merge(c.getClassLoader(), 1, Integer::sum);
        }
        ClassLoader loader = null;
        int best = 0;
        for (Map.Entry<ClassLoader, Integer> e : votes.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); loader = e.getKey(); }
        }
        if (loader == null) {
            w.retry = true;
            return manual ? "WAITING: " + head + "none of its classes is loaded yet (the game has not loaded the mods); "
                + "it reloads by itself once they are\n" : "";
        }
        final ClassLoader modLoader = loader;
        loaded.values().removeIf(c -> c.getClassLoader() != modLoader);
        w.retry = false;

        StringBuilder notes = new StringBuilder();
        List<ClassDefinition> defs = new ArrayList<>();
        Map<Class<?>, String> defName = new LinkedHashMap<>();
        List<String> written = new ArrayList<>(), failed = new ArrayList<>();
        Map<String, byte[]> accepted = new HashMap<>();

        for (String name : changed) {
            byte[] bytes = next.get(name);
            Class<?> c = loaded.get(name);
            if (c == null && w.inStagedJar.contains(name)) {
                // not loaded yet: load the old version now (not initialized), so the staged jar can't supply it later
                try {
                    c = Class.forName(name.replace('/', '.'), false, modLoader);
                } catch (Throwable t) {
                    failed.add(name + " (could not load the old version: " + t + ")");
                    continue;
                }
            }
            if (c == null) {
                // a class an earlier reload added and nothing has loaded yet: the new file is all it takes
                if (writeHot(w, name, bytes, notes)) { written.add(name); accepted.put(name, bytes); }
                else failed.add(name);
                continue;
            }
            if (w.hotDirAdded && !w.inStagedJar.contains(name)) writeHot(w, name, bytes, notes);
            defs.add(new ClassDefinition(c, bytes));
            defName.put(c, name);
            if (w.current.containsKey(name)) warnShape(name, w.current.get(name), bytes, notes);
        }

        // redefine as one batch (a change that spans classes must land at once); on failure find the culprits
        List<String> redefined = new ArrayList<>();
        if (!defs.isEmpty()) {
            try {
                inst.redefineClasses(defs.toArray(new ClassDefinition[0]));
                for (ClassDefinition d : defs) { redefined.add(defName.get(d.getDefinitionClass())); }
            } catch (Throwable batch) {
                notes.append("  the batch was refused (").append(batch).append("); trying the classes one by one\n");
                for (ClassDefinition d : defs) {
                    String name = defName.get(d.getDefinitionClass());
                    try {
                        inst.redefineClasses(d);
                        redefined.add(name);
                    } catch (Throwable t) {
                        failed.add(name + " (" + t + ")");
                    }
                }
            }
            for (String name : redefined) accepted.put(name, next.get(name));
        }

        // new classes: where the mod's class loader finds them
        List<String> newOk = new ArrayList<>();
        if (!added.isEmpty()) {
            if (modLoader instanceof URLClassLoader) {
                for (String name : added) {
                    if (writeHot(w, name, next.get(name), notes)) { newOk.add(name); accepted.put(name, next.get(name)); }
                    else failed.add(name + " (new class not written)");
                }
                if (!newOk.isEmpty() && !w.hotDirAdded) {
                    try {
                        Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                        addUrl.setAccessible(true);
                        addUrl.invoke(modLoader, w.hotDir.toUri().toURL());
                        w.hotDirAdded = true;
                    } catch (Throwable t) {
                        failed.add("the folder for new classes could not be added to the class loader (" + t + ")");
                    }
                }
            } else {
                defineNow(modLoader, added, next, newOk, failed, accepted);
            }
        }

        for (String name : removed) {
            w.current.remove(name);
        }
        w.current.putAll(accepted);

        boolean changedSomething = !redefined.isEmpty() || !newOk.isEmpty() || !written.isEmpty();
        if (changedSomething) {
            generation++;
            System.setProperty("starsector.hotreload.generation", Integer.toString(generation));
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        StringBuilder out = new StringBuilder();
        out.append(failed.isEmpty() ? "RELOADED" : changedSomething ? "RELOAD_PARTIAL" : "RELOAD_FAILED")
            .append(" (generation ").append(generation).append("): ").append(head)
            .append(redefined.size()).append(" redefined, ").append(newOk.size() + written.size()).append(" new, ")
            .append(removed.size()).append(" removed, ").append(failed.size()).append(" failed, ").append(ms)
            .append(" ms").append(enhanced ? "" : ", enhanced redefinition OFF").append('\n');
        list(out, "redefined", redefined);
        List<String> news = new ArrayList<>(newOk);
        news.addAll(written);
        list(out, "new", news);
        list(out, "FAILED", failed);
        if (!removed.isEmpty()) {
            list(out, "removed", removed);
            out.append("  note: removed classes stay in the game (loaded, or loadable) until it restarts\n");
        }
        out.append(notes);
        if (changedSomething) {
            w.lastResult = out.toString();
            w.lastTime = java.time.LocalTime.now().withNano(0).toString();
        }
        return out.toString();
    }

    private static void list(StringBuilder out, String what, List<String> names) {
        if (names.isEmpty()) return;
        out.append("  ").append(what).append(": ");
        int n = 0;
        for (String name : new TreeSet<>(names)) {
            if (n == LIST_MAX) { out.append(", ... ").append(names.size() - LIST_MAX).append(" more"); break; }
            out.append(n++ == 0 ? "" : ", ").append(name.replace('/', '.'));
        }
        out.append('\n');
    }

    private static boolean writeHot(Watched w, String name, byte[] bytes, StringBuilder notes) {
        try {
            Path file = w.hotDir.resolve(name + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
            return true;
        } catch (IOException e) {
            notes.append("  could not write ").append(name).append(": ").append(e).append('\n');
            return false;
        }
    }

    /** For a class loader that takes no folders: define the new classes now, superclasses first (retry rounds). */
    private static void defineNow(ClassLoader loader, List<String> names, Map<String, byte[]> next, List<String> ok,
                                  List<String> failed, Map<String, byte[]> accepted) {
        Method define;
        try {
            define = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            define.setAccessible(true);
        } catch (Throwable t) {
            for (String n : names) failed.add(n + " (cannot define classes in " + loader + ": " + t + ")");
            return;
        }
        List<String> todo = new ArrayList<>(names);
        Map<String, Throwable> errors = new HashMap<>();
        boolean progress = true;
        while (!todo.isEmpty() && progress) {
            progress = false;
            for (int i = 0; i < todo.size(); i++) {
                String name = todo.get(i);
                byte[] bytes = next.get(name);
                try {
                    define.invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
                    ok.add(name);
                    accepted.put(name, bytes);
                    todo.remove(i--);
                    progress = true;
                } catch (Throwable t) {
                    errors.put(name, t.getCause() != null ? t.getCause() : t);
                }
            }
        }
        for (String n : todo) failed.add(n + " (" + errors.get(n) + ")");
    }

    // ---- what a redefinition cannot do: static initializers and field initializers do not run again ----

    private static void warnShape(String name, byte[] oldBytes, byte[] newBytes, StringBuilder notes) {
        ClassShape before, after;
        try {
            before = ClassShape.read(oldBytes);
            after = ClassShape.read(newBytes);
        } catch (Throwable t) {
            return;
        }
        String cls = name.replace('/', '.');
        List<String> newStatic = new ArrayList<>(), newInstance = new ArrayList<>();
        for (Map.Entry<String, Integer> f : after.fields.entrySet()) {
            if (before.fields.containsKey(f.getKey())) continue;
            ((f.getValue() & 0x0008) != 0 ? newStatic : newInstance).add(f.getKey());
        }
        if (!newStatic.isEmpty()) {
            notes.append("  WARN ").append(cls).append(": new static field(s) ").append(newStatic)
                .append(" stay 0/null: static initializers do not run again (restart for new enum constants and object properties)\n");
        } else if (!before.clinit.equals(after.clinit)) {
            notes.append("  WARN ").append(cls)
                .append(": its static initializer changed; the class keeps its old static values until a restart\n");
        }
        if (!newInstance.isEmpty()) {
            notes.append("  WARN ").append(cls).append(": new field(s) ").append(newInstance)
                .append(" are 0/null in objects that already exist; new objects are fine\n");
        }
        if (!before.superName.equals(after.superName)) {
            notes.append("  note ").append(cls).append(": superclass changed to ").append(after.superName.replace('/', '.')).append('\n');
        }
    }

    /**
     * Just enough of a class file: its fields (name and access), its superclass and its static initializer, the
     * latter as text with every constant pool reference resolved, so a renumbered constant pool (any edit elsewhere in
     * the class) does not count as a change.
     */
    private static final class ClassShape {
        final Map<String, Integer> fields = new LinkedHashMap<>();
        String clinit = "";
        String superName = "";

        private int[] tags;
        private int[] a, b;
        private Object[] values;

        static ClassShape read(byte[] bytes) throws IOException {
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
            ClassShape s = new ClassShape();
            in.readInt();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            s.tags = new int[count];
            s.a = new int[count];
            s.b = new int[count];
            s.values = new Object[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                s.tags[i] = tag;
                switch (tag) {
                    case 1: s.values[i] = in.readUTF(); break;
                    case 3: s.values[i] = in.readInt(); break;
                    case 4: s.values[i] = in.readFloat(); break;
                    case 5: s.values[i] = in.readLong(); i++; break;
                    case 6: s.values[i] = in.readDouble(); i++; break;
                    case 7: case 8: case 16: case 19: case 20: s.a[i] = in.readUnsignedShort(); break;
                    case 9: case 10: case 11: case 12: case 17: case 18:
                        s.a[i] = in.readUnsignedShort(); s.b[i] = in.readUnsignedShort(); break;
                    case 15: s.a[i] = in.readUnsignedByte(); s.b[i] = in.readUnsignedShort(); break;
                    default: throw new IOException("constant pool tag " + tag);
                }
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int superIndex = in.readUnsignedShort();
            if (superIndex != 0) s.superName = s.text(superIndex);
            int interfaces = in.readUnsignedShort();
            for (int i = 0; i < interfaces; i++) in.readUnsignedShort();
            int fields = in.readUnsignedShort();
            for (int i = 0; i < fields; i++) {
                int access = in.readUnsignedShort();
                String fieldName = s.text(in.readUnsignedShort());
                String desc = s.text(in.readUnsignedShort());
                s.fields.put(fieldName + ":" + desc, access);
                int attrs = in.readUnsignedShort();
                for (int k = 0; k < attrs; k++) {
                    in.readUnsignedShort();
                    in.skipNBytes(in.readInt());
                }
            }
            int methods = in.readUnsignedShort();
            for (int i = 0; i < methods; i++) {
                in.readUnsignedShort();
                String methodName = s.text(in.readUnsignedShort());
                in.readUnsignedShort();
                int attrs = in.readUnsignedShort();
                for (int k = 0; k < attrs; k++) {
                    String attr = s.text(in.readUnsignedShort());
                    byte[] body = new byte[in.readInt()];
                    in.readFully(body);
                    if (methodName.equals("<clinit>") && attr.equals("Code")) s.clinit = s.code(body);
                }
            }
            return s;
        }

        /** A constant pool entry as text. */
        String text(int i) {
            switch (tags[i]) {
                case 1: return (String) values[i];
                case 3: case 4: case 5: case 6: return String.valueOf(values[i]);
                case 7: case 16: case 19: case 20: return text(a[i]);
                case 8: return "\"" + text(a[i]) + "\"";
                case 9: case 10: case 11: return text(a[i]) + "." + text(b[i]);
                case 12: return text(a[i]) + ":" + text(b[i]);
                case 15: return "handle" + a[i] + " " + text(b[i]);
                case 17: case 18: return "dynamic " + text(b[i]);
                default: return "?";
            }
        }

        /** A Code attribute's instructions with constant pool operands resolved; line numbers and locals left out. */
        String code(byte[] attr) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(attr);
            int length = buf.getInt(4);
            int start = 8;
            StringBuilder out = new StringBuilder();
            int pc = 0;
            while (pc < length) {
                int op = attr[start + pc] & 0xff;
                out.append(op);
                int n = 0;
                boolean pool = false;
                switch (op) {
                    case 0x12: out.append(' ').append(text(attr[start + pc + 1] & 0xff)); n = 1; pool = true; break;
                    case 0x13: case 0x14: case 0xb2: case 0xb3: case 0xb4: case 0xb5: case 0xb6: case 0xb7: case 0xb8:
                    case 0xbb: case 0xbd: case 0xc0: case 0xc1:
                        out.append(' ').append(text(u2(attr, start + pc + 1))); n = 2; pool = true; break;
                    case 0xb9: case 0xba: out.append(' ').append(text(u2(attr, start + pc + 1))); n = 4; pool = true; break;
                    case 0xc5:
                        out.append(' ').append(text(u2(attr, start + pc + 1))).append(' ').append(attr[start + pc + 3]);
                        n = 3; pool = true; break;
                    case 0x10: case 0x15: case 0x16: case 0x17: case 0x18: case 0x19: case 0x36: case 0x37: case 0x38:
                    case 0x39: case 0x3a: case 0xa9: case 0xbc: n = 1; break;
                    case 0x11: case 0x84: case 0xc6: case 0xc7: n = 2; break;
                    case 0xc8: case 0xc9: n = 4; break;
                    case 0xc4: n = (attr[start + pc + 1] & 0xff) == 0x84 ? 5 : 3; break;
                    case 0xaa: case 0xab: {
                        int p = pc + 1;
                        while (p % 4 != 0) p++;
                        if (op == 0xaa) {
                            int low = buf.getInt(start + p + 4), high = buf.getInt(start + p + 8);
                            n = p - pc - 1 + 12 + 4 * (high - low + 1);
                        } else {
                            n = p - pc - 1 + 8 + 8 * buf.getInt(start + p + 4);
                        }
                        break;
                    }
                    default:
                        if (op >= 0x99 && op <= 0xa8) n = 2;
                }
                if (!pool) for (int k = 1; k <= n; k++) out.append(' ').append(attr[start + pc + k]);
                out.append('\n');
                pc += 1 + n;
            }
            return out.toString();
        }

        private static int u2(byte[] b, int at) { return ((b[at] & 0xff) << 8) | (b[at + 1] & 0xff); }
    }

    /** Offline check, no game: what a reload from OLD.jar to NEW.jar would change and warn about. */
    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !args[0].equals("diff")) {
            System.out.println("usage: java -cp HotReloadAgent.jar HotReloadAgent diff OLD.jar NEW.jar");
            System.exit(2);
        }
        Map<String, byte[]> before = readJar(Paths.get(args[1])), after = readJar(Paths.get(args[2]));
        StringBuilder notes = new StringBuilder();
        List<String> changed = new ArrayList<>(), added = new ArrayList<>(), removed = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : after.entrySet()) {
            byte[] old = before.get(e.getKey());
            if (old == null) added.add(e.getKey());
            else if (!Arrays.equals(old, e.getValue())) {
                changed.add(e.getKey());
                warnShape(e.getKey(), old, e.getValue(), notes);
            }
        }
        for (String n : before.keySet()) if (!after.containsKey(n)) removed.add(n);
        StringBuilder out = new StringBuilder();
        list(out, "changed", changed);
        list(out, "new", added);
        list(out, "removed", removed);
        System.out.print(out.length() == 0 ? "no class changed\n" : out.toString() + notes);
    }

    // ---- helpers ----

    /** Class name (a/b/C) to bytes, for every class in the jar. Opened and closed here: the build may rewrite it. */
    private static Map<String, byte[]> readJar(Path jar) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || n.startsWith("META-INF/") || n.endsWith("module-info.class")) continue;
                try (InputStream in = zip.getInputStream(e)) {
                    classes.put(n.substring(0, n.length() - 6), readAll(in));
                }
            }
        }
        return classes;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static boolean vmFlag(String name) {
        try {
            Class<?> bean = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            @SuppressWarnings("unchecked")
            Object mx = java.lang.management.ManagementFactory.getPlatformMXBean((Class) bean);
            Object option = bean.getMethod("getVMOption", String.class).invoke(mx, name);
            return "true".equals(option.getClass().getMethod("getValue").invoke(option));
        } catch (Throwable t) {
            return false;
        }
    }
}
