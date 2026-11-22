package com.openmohaa.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Location of the game data and import of the original game files
 * (main / mainta / maintt folders of a PC installation).
 */
public final class GameData {
    /** Game folders: Allied Assault, Spearhead, Breakthrough */
    public static final String[] GAME_DIRS = { "main", "mainta", "maintt" };

    private GameData() {}

    public static File getBaseDir(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) {
            dir = ctx.getFilesDir();
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** True if the folder of the given game contains at least one .pk3 */
    public static boolean hasGame(Context ctx, int game) {
        File dir = findChildIgnoreCase(getBaseDir(ctx), GAME_DIRS[game]);
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".pk3")) {
                return true;
            }
        }
        return false;
    }

    private static File findChildIgnoreCase(File parent, String name) {
        File[] files = parent.listFiles();
        if (files == null) {
            return null;
        }
        for (File f : files) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Import through the Storage Access Framework
    // ------------------------------------------------------------------

    public interface Progress {
        void onProgress(String fileName, long copiedBytes, long totalBytes);
    }

    private static final class Entry {
        final String docId;
        final String name;
        final String mime;
        final long size;

        Entry(String docId, String name, String mime, long size) {
            this.docId = docId;
            this.name = name;
            this.mime = mime;
            this.size = size;
        }

        boolean isDir() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        }
    }

    private static List<Entry> list(ContentResolver cr, Uri tree, String parentDocId) {
        List<Entry> out = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId);
        String[] proj = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor c = cr.query(children, proj, null, null, null)) {
            if (c == null) {
                return out;
            }
            while (c.moveToNext()) {
                out.add(new Entry(c.getString(0), c.getString(1), c.getString(2),
                        c.isNull(3) ? 0 : c.getLong(3)));
            }
        }
        return out;
    }

    private static long totalSize(ContentResolver cr, Uri tree, Entry e) {
        if (!e.isDir()) {
            return e.size;
        }
        long total = 0;
        for (Entry child : list(cr, tree, e.docId)) {
            total += totalSize(cr, tree, child);
        }
        return total;
    }

    /**
     * Copies the game folders found in the picked folder.
     * The user may pick either the game installation folder (containing main/)
     * or the main/ folder itself.
     *
     * @return the number of game folders that were imported
     */
    public static int importFromTree(Context ctx, Uri tree, Progress progress) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        File base = getBaseDir(ctx);

        List<Entry> sources = new ArrayList<>();
        List<String> targets = new ArrayList<>();

        for (Entry e : list(cr, tree, rootId)) {
            if (!e.isDir()) {
                continue;
            }
            for (String g : GAME_DIRS) {
                if (e.name.equalsIgnoreCase(g)) {
                    sources.add(e);
                    targets.add(g);
                }
            }
        }

        if (sources.isEmpty()) {
            // Maybe the user picked the main folder itself
            Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
            String rootName = queryName(cr, rootDoc);
            boolean hasPk3 = false;
            for (Entry e : list(cr, tree, rootId)) {
                if (!e.isDir() && e.name.toLowerCase(Locale.ROOT).endsWith(".pk3")) {
                    hasPk3 = true;
                    break;
                }
            }
            if (hasPk3) {
                String target = "main";
                if (rootName != null) {
                    for (String g : GAME_DIRS) {
                        if (rootName.equalsIgnoreCase(g)) {
                            target = g;
                        }
                    }
                }
                sources.add(new Entry(rootId, rootName, DocumentsContract.Document.MIME_TYPE_DIR, 0));
                targets.add(target);
            }
        }

        if (sources.isEmpty()) {
            return 0;
        }

        long total = 0;
        for (Entry e : sources) {
            total += totalSize(cr, tree, e);
        }

        long[] copied = { 0 };
        for (int i = 0; i < sources.size(); i++) {
            File dest = new File(base, targets.get(i));
            copyDir(cr, tree, sources.get(i), dest, copied, total, progress);
        }
        return sources.size();
    }

    private static String queryName(ContentResolver cr, Uri doc) {
        try (Cursor c = cr.query(doc, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME },
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void copyDir(ContentResolver cr, Uri tree, Entry dir, File dest,
                                long[] copied, long total, Progress progress) throws IOException {
        if (!dest.isDirectory() && !dest.mkdirs()) {
            throw new IOException("Cannot create " + dest);
        }
        for (Entry e : list(cr, tree, dir.docId)) {
            File target = new File(dest, e.name);
            if (e.isDir()) {
                copyDir(cr, tree, e, target, copied, total, progress);
                continue;
            }
            if (target.isFile() && target.length() == e.size && e.size > 0) {
                // already imported
                copied[0] += e.size;
                progress.onProgress(e.name, copied[0], total);
                continue;
            }
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, e.docId);
            try (InputStream in = cr.openInputStream(doc);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    throw new IOException("Cannot open " + e.name);
                }
                byte[] buf = new byte[1 << 20];
                int n;
                long lastReport = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    copied[0] += n;
                    if (copied[0] - lastReport > (4 << 20)) {
                        lastReport = copied[0];
                        progress.onProgress(e.name, copied[0], total);
                    }
                }
            }
            progress.onProgress(e.name, copied[0], total);
        }
    }
}
