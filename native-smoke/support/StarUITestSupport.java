import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only helper for the StarUIFramework suite. The runner compiles it with NativeSmokeSupport onto the game's
 * classpath (SMOKE_SUPPORT_SOURCES), outside the script classloader's security gate, so it may use reflection.
 * Public signatures use only gate-allowed types.
 *
 * It builds REAL engine input events instead of NativeSmokeSupport's proxies. Vanilla
 * UIComponent.processInput(List) casts every element to the engine's internal event class and wraps them in the
 * internal event list (an ArrayList subclass), so proxies throw ClassCastException there. Both internal classes are
 * found by shape from a live component, never by obfuscated name:
 *   the event list = the parameter type of the 1-argument processInput overload that is not java.util.List,
 *   the event      = that list's ArrayList type argument.
 * Coordinates are UI units (PositionAPI space); the engine's constructor takes screen pixels, so x/y are set
 * through setX/setY after construction.
 */
public final class StarUITestSupport {
    private static Class<?> eventListClass;
    private static Constructor<?> eventConstructor;
    private static Method setX;
    private static Method setY;

    private StarUITestSupport() {
    }

    /**
     * Writes a UTF-8 text file into the run's output directory (mod code may not do file I/O itself). The name is
     * reduced to letters, digits, '.', '-' and '_' so it cannot leave that directory.
     */
    public static void writeText(String fileName, String text) throws Exception {
        String safe = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        java.nio.file.Path dir = java.nio.file.Paths.get(System.getProperty("native.smoke.output"));
        java.nio.file.Files.write(dir.resolve(safe), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static synchronized void resolve() throws Exception {
        if (eventConstructor != null) return;
        CustomPanelAPI probe = Global.getSettings().createCustom(1f, 1f, null);
        Class<?> listType = null;
        for (Class<?> c = probe.getClass(); c != null && listType == null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("processInput") || m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                if (p != List.class && List.class.isAssignableFrom(p)) {
                    listType = p;
                    break;
                }
            }
        }
        if (listType == null) throw new IllegalStateException("internal processInput overload not found");
        Type generic = listType.getGenericSuperclass();
        if (!(generic instanceof ParameterizedType)) throw new IllegalStateException("event list is not generic: " + generic);
        Class<?> eventType = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
        if (!InputEventAPI.class.isAssignableFrom(eventType)) throw new IllegalStateException("not an event: " + eventType);
        eventConstructor = eventType.getConstructor(InputEventClass.class, InputEventType.class,
                int.class, int.class, int.class, char.class);
        setX = eventType.getMethod("setX", int.class);
        setY = eventType.getMethod("setY", int.class);
        eventListClass = listType;
        System.out.println("NATIVE_SMOKE support: internal event " + eventType.getName() + ", list " + listType.getName());
    }

    private static InputEventAPI make(InputEventClass kind, InputEventType type, float x, float y, int value, char ch)
            throws Exception {
        resolve();
        Object event = eventConstructor.newInstance(kind, type, 0, 0, value, ch);
        setX.invoke(event, Math.round(x));
        setY.invoke(event, Math.round(y));
        return (InputEventAPI) event;
    }

    /** Real left/right mouse button event. type: MOUSE_DOWN or MOUSE_UP. button: 0 = left, 1 = right. */
    public static InputEventAPI mouseButton(InputEventType type, float x, float y, int button) throws Exception {
        return make(InputEventClass.MOUSE_EVENT, type, x, y, button, '\0');
    }

    /** Real mouse-move event at (x, y). */
    public static InputEventAPI mouseMove(float x, float y) throws Exception {
        return make(InputEventClass.MOUSE_EVENT, InputEventType.MOUSE_MOVE, x, y, 0, '\0');
    }

    /**
     * Real mouse wheel event at (x, y). notches: positive = wheel up (towards the top / left), negative = down.
     * shift: whether Shift is held (the event's readable setShiftDown).
     */
    public static InputEventAPI mouseWheel(float x, float y, int notches, boolean shift) throws Exception {
        InputEventAPI event = make(InputEventClass.MOUSE_EVENT, InputEventType.MOUSE_SCROLL, x, y, notches, '\0');
        if (shift) event.getClass().getMethod("setShiftDown", boolean.class).invoke(event, true);
        return event;
    }

    /** Real keyboard event. type: KEY_DOWN, KEY_UP or KEY_REPEAT. */
    public static InputEventAPI key(InputEventType type, int keyCode, char ch) throws Exception {
        return make(InputEventClass.KEYBOARD_EVENT, type, 0f, 0f, keyCode, ch);
    }

    /**
     * Dispatches like the engine does inside the UI tree: wraps the events in the internal event list and calls the
     * internal processInput overload. Panels pass input to their children only through that overload.
     */
    public static void dispatchInternal(UIComponentAPI component, List<InputEventAPI> events) throws Exception {
        resolve();
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) eventListClass.getConstructor().newInstance();
        list.addAll(events);
        Method target = null;
        for (Class<?> c = component.getClass(); c != null && target == null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("processInput") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == eventListClass) {
                    target = m;
                    break;
                }
            }
        }
        if (target == null) throw new NoSuchMethodException("internal processInput on " + component.getClass().getName());
        target.setAccessible(true);
        target.invoke(component, list);
    }

    private static Method inputPoll;

    /**
     * Emulates the start of an engine frame for input. When a vanilla button fires, Logic sets a global
     * "a button fired this frame" flag and every other button ignores clicks until the engine's per-frame input
     * poll clears it. A startup probe has no frame loop, so without this only the first click of the whole boot
     * would register. The poll is found by shape: the only public static no-argument method in the event's package
     * that returns the internal event list. Its returned (real, normally empty) events are discarded.
     */
    public static void beginInputFrame() throws Exception {
        resolve();
        if (inputPoll == null) {
            Class<?> eventType = eventConstructor.getDeclaringClass();
            Class<?> owner = NativeSmokeSupport.findClass(eventType, eventType.getPackage().getName(), c -> {
                for (Method m : c.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && java.lang.reflect.Modifier.isPublic(m.getModifiers())
                            && m.getParameterCount() == 0 && m.getReturnType() == eventListClass) return true;
                }
                return false;
            });
            for (Method m : owner.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && m.getReturnType() == eventListClass) {
                    if (inputPoll != null) throw new IllegalStateException("several input poll methods on " + owner.getName());
                    inputPoll = m;
                }
            }
            System.out.println("NATIVE_SMOKE support: input poll " + owner.getName() + "." + inputPoll.getName());
        }
        inputPoll.invoke(null);
    }

    /** Convenience: a mutable list for UIComponentAPI.processInput(List). */
    public static List<InputEventAPI> list(InputEventAPI... events) {
        List<InputEventAPI> out = new ArrayList<>();
        for (InputEventAPI e : events) out.add(e);
        return out;
    }
}
