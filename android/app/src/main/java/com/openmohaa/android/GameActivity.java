package com.openmohaa.android;

import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the OpenMoHAA engine (libmain.so) through SDL2.
 *
 * Paths are handed to the engine via environment variables, the selected
 * game / renderer via command line arguments.
 */
public class GameActivity extends SDLActivity {
    private static final String TAG = "OpenMoHAA";

    public static final String EXTRA_TARGET_GAME = "target_game";
    public static final String EXTRA_RENDERER = "renderer";
    public static final String EXTRA_EXTRA_ARGS = "extra_args";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File base = GameData.getBaseDir(this);
        String libDir = getApplicationInfo().nativeLibraryDir;

        setEnv("OPENMOHAA_BASEDIR", base.getAbsolutePath());
        setEnv("OPENMOHAA_LIBDIR", libDir);
        setEnv("HOME", base.getAbsolutePath());
        // gl4es: use the OpenGL ES 2 backend
        setEnv("LIBGL_ES", "2");
        setEnv("LIBGL_GL", "21");
        // gl4es: no startup banner spam in logcat
        setEnv("LIBGL_NOBANNER", "1");

        super.onCreate(savedInstanceState);
    }

    private static void setEnv(String name, String value) {
        try {
            Os.setenv(name, value, true);
        } catch (ErrnoException e) {
            Log.e(TAG, "setenv " + name + " failed", e);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "c++_shared",
            "SDL2",
            "openal",
            "main"
        };
    }

    @Override
    protected String[] getArguments() {
        List<String> args = new ArrayList<>();
        int targetGame = getIntent().getIntExtra(EXTRA_TARGET_GAME, 0);
        String renderer = getIntent().getStringExtra(EXTRA_RENDERER);
        String extra = getIntent().getStringExtra(EXTRA_EXTRA_ARGS);

        if (renderer == null || renderer.isEmpty()) {
            renderer = "opengl1";
        }

        args.add("+set");
        args.add("com_target_game");
        args.add(Integer.toString(targetGame));

        args.add("+set");
        args.add("cl_renderer");
        args.add(renderer);

        // Native screen resolution, fullscreen
        args.add("+set");
        args.add("r_mode");
        args.add("-2");
        args.add("+set");
        args.add("r_fullscreen");
        args.add("1");

        if (extra != null && !extra.trim().isEmpty()) {
            for (String a : extra.trim().split("\\s+")) {
                args.add(a);
            }
        }

        Log.i(TAG, "Arguments: " + args);
        return args.toArray(new String[0]);
    }
}
