package io.github.steinbeck.seneca.app;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps macOS from throttling the application while a structure generation run is going on (App Nap: a
 * GUI application that is not in front, or whose windows are hidden, is moved to slow cores and its
 * timers are coalesced; a run then took about four times as long, gui-design.md DV-89). The documented
 * way out is {@code [[NSProcessInfo processInfo] beginActivityWithOptions:reason:]} with
 * {@code NSActivityUserInitiatedAllowingIdleSystemSleep} while the work lasts and {@code endActivity:}
 * afterwards. The {@code LSAppNapIsDisabled} Info.plist key does not help (measured).
 *
 * <p>Called through the Objective-C runtime ({@code objc_msgSend} in {@code libobjc}) with the Java
 * Foreign Function API, no native library of our own; the JVM needs
 * {@code --enable-native-access=ALL-UNNAMED} (set by the app launcher, scripts/run-app.sh and the test
 * configuration), otherwise it prints a warning. Off macOS, or if anything fails, it does nothing (the
 * failure is logged once at FINE level). Thread-safe.
 */
public final class MacActivity {

    private static final Logger LOG = Logger.getLogger(MacActivity.class.getName());

    /** {@code NSActivityUserInitiatedAllowingIdleSystemSleep} = UserInitiated (0x00FFFFFF) without IdleSystemSleepDisabled (1 << 20). */
    static final long USER_INITIATED_ALLOWING_IDLE_SYSTEM_SLEEP = 0x00FFFFFFL & ~(1L << 20);

    /** The reason shown by macOS (e.g. {@code pmset -g assertions}). */
    static final String REASON = "SENECA structure generation";

    private static Native nativeApi;
    private static boolean failed;
    private static MemorySegment token = MemorySegment.NULL;

    private MacActivity() {
    }

    /** True on macOS. */
    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }

    /**
     * {@code true}: begin the activity (if not already begun); {@code false}: end it. Returns whether an
     * activity is held afterwards.
     */
    public static synchronized boolean set(boolean on) {
        Native n = api();
        if (n == null) return false;
        try {
            if (on && (token.address() == 0)) token = n.begin();
            else if (!on && !(token.address() == 0)) {
                n.end(token);
                token = MemorySegment.NULL;
            }
        } catch (Throwable t) {
            fail(t);
        }
        return !(token.address() == 0);
    }

    /** True while an activity is held. */
    public static synchronized boolean held() {
        return !(token.address() == 0);
    }

    private static Native api() {
        if (nativeApi != null || failed) return nativeApi;
        if (!supported()) {
            failed = true;
            return null;
        }
        try {
            nativeApi = new Native();
        } catch (Throwable t) {
            fail(t);
        }
        return nativeApi;
    }

    private static void fail(Throwable t) {
        failed = true;
        nativeApi = null;
        LOG.log(Level.FINE, "App Nap cannot be suspended", t);
    }

    /** The Objective-C runtime calls. */
    private static final class Native {
        final MethodHandle getClass, selector, send0, sendVoid0, send1, sendBegin, sendStr, poolPush, poolPop;
        final MemorySegment processInfoClass, stringClass, selProcessInfo, selBegin, selEnd, selRetain, selRelease,
                selString;

        Native() throws Throwable {
            Linker linker = Linker.nativeLinker();
            SymbolLookup objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
            // Foundation must be loaded for NSProcessInfo/NSString (AWT has loaded it already; harmless otherwise)
            SymbolLookup.libraryLookup("/System/Library/Frameworks/Foundation.framework/Foundation", Arena.global());
            var A = ValueLayout.ADDRESS;
            getClass = linker.downcallHandle(objc.find("objc_getClass").orElseThrow(), FunctionDescriptor.of(A, A));
            selector = linker.downcallHandle(objc.find("sel_registerName").orElseThrow(), FunctionDescriptor.of(A, A));
            poolPush = linker.downcallHandle(objc.find("objc_autoreleasePoolPush").orElseThrow(), FunctionDescriptor.of(A));
            poolPop = linker.downcallHandle(objc.find("objc_autoreleasePoolPop").orElseThrow(), FunctionDescriptor.ofVoid(A));
            MemorySegment msgSend = objc.find("objc_msgSend").orElseThrow();
            send0 = linker.downcallHandle(msgSend, FunctionDescriptor.of(A, A, A));                       // id (id, SEL)
            sendVoid0 = linker.downcallHandle(msgSend, FunctionDescriptor.ofVoid(A, A));                  // void (id, SEL)
            send1 = linker.downcallHandle(msgSend, FunctionDescriptor.ofVoid(A, A, A));                   // void (id, SEL, id)
            sendBegin = linker.downcallHandle(msgSend, FunctionDescriptor.of(A, A, A, ValueLayout.JAVA_LONG, A));
            sendStr = linker.downcallHandle(msgSend, FunctionDescriptor.of(A, A, A, A));                  // id (id, SEL, char*)
            processInfoClass = cls("NSProcessInfo");
            stringClass = cls("NSString");
            selProcessInfo = sel("processInfo");
            selBegin = sel("beginActivityWithOptions:reason:");
            selEnd = sel("endActivity:");
            selRetain = sel("retain");
            selRelease = sel("release");
            selString = sel("stringWithUTF8String:");
            if (processInfoClass.address() == 0 || stringClass.address() == 0)
                throw new IllegalStateException("Foundation classes not found");
        }

        private MemorySegment cls(String name) throws Throwable {
            try (Arena a = Arena.ofConfined()) {
                return (MemorySegment) getClass.invokeExact(a.allocateFrom(name));
            }
        }

        private MemorySegment sel(String name) throws Throwable {
            try (Arena a = Arena.ofConfined()) {
                return (MemorySegment) selector.invokeExact(a.allocateFrom(name));
            }
        }

        /** Begins the activity; returns the retained token. */
        MemorySegment begin() throws Throwable {
            MemorySegment pool = (MemorySegment) poolPush.invokeExact();
            try (Arena a = Arena.ofConfined()) {
                MemorySegment info = (MemorySegment) send0.invokeExact(processInfoClass, selProcessInfo);
                MemorySegment reason = (MemorySegment) sendStr.invokeExact(stringClass, selString, a.allocateFrom(REASON));
                MemorySegment t = (MemorySegment) sendBegin.invokeExact(info, selBegin, USER_INITIATED_ALLOWING_IDLE_SYSTEM_SLEEP, reason);
                if (t.address() == 0) throw new IllegalStateException("beginActivity returned nil");
                MemorySegment retained = (MemorySegment) send0.invokeExact(t, selRetain);   // outlives the pool
                return retained;
            } finally {
                poolPop.invokeExact(pool);
            }
        }

        /** Ends the activity of {@code token} and releases it. */
        void end(MemorySegment token) throws Throwable {
            MemorySegment pool = (MemorySegment) poolPush.invokeExact();
            try {
                MemorySegment info = (MemorySegment) send0.invokeExact(processInfoClass, selProcessInfo);
                send1.invokeExact(info, selEnd, token);
                sendVoid0.invokeExact(token, selRelease);
            } finally {
                poolPop.invokeExact(pool);
            }
        }
    }
}
