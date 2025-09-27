package eu.siacs.conversations.ui.fragment.settings;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.monocles.chat.DownloadDefaultStickers;

import de.monocles.chat.StickersMigration;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.UIHelper;

public class AttachmentsSettingsFragment extends XmppPreferenceFragment {

    public static final int REQUEST_IMPORT_STICKERS = 0xbf8705;
    public static final int REQUEST_IMPORT_GIFS = 0xbf8706;

    private final ActivityResultLauncher<String> requestStorageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            downloadStickers();
                        } else {
                            Toast.makeText(
                                            requireActivity(),
                                            getString(
                                                    R.string.no_storage_permission,
                                                    getString(R.string.app_name)),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_attachments, rootKey);

        /*
        final var p = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        final var stickerDir = findPreference("sticker_directory");
        if (Build.VERSION.SDK_INT >= 29) {
            stickerDir.setSummary(p.getString("sticker_directory", "Documents/monocles chat/Stickers"));
            stickerDir.setOnPreferenceClickListener((pref) -> {
                final var intent = ((StorageManager) requireActivity().getSystemService(Context.STORAGE_SERVICE)).getPrimaryStorageVolume().createOpenDocumentTreeIntent();
                startActivityForResult(Intent.createChooser(intent, "Choose sticker location"), 0);
                return true;
            });
        } else {
            stickerDir.setVisible(false);
        }
        */

        final var importOwnStickers = findPreference("import_own_stickers");
        if (importOwnStickers != null) {
            importOwnStickers.setOnPreferenceClickListener(
                    preference -> {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                requestStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                            } else {
                                importStickers();
                            }
                        } else {
                            importStickers();
                        }
                        return true;
                    }
            );
        }

        final var importOwnGifs = findPreference("import_own_gifs");
        if (importOwnGifs != null) {
            importOwnGifs.setOnPreferenceClickListener(
                    preference -> {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                requestStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                            } else {
                                importGifs();
                            }
                        } else {
                            importGifs();
                        }
                        return true;
                    }
            );
        }

        final var downloadDefaultStickers = findPreference("download_default_stickers");
        if (downloadDefaultStickers != null) {
            downloadDefaultStickers.setOnPreferenceClickListener((pref) -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    } else {
                        downloadStickers();
                    }
                } else {
                    downloadStickers();
                }
                return true;
            });
        }

        final var clearBlockedMedia = findPreference("clear_blocked_media");
        if (clearBlockedMedia != null) {
            clearBlockedMedia.setOnPreferenceClickListener((pref) -> {
                requireService().clearBlockedMedia();
                runOnUiThread(() -> Toast.makeText(requireActivity(), "Blocked media will be displayed again", Toast.LENGTH_LONG).show());
                return true;
            });
        }

        final ListPreference autoAcceptFileSize = findPreference("auto_accept_file_size");
        if (autoAcceptFileSize == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }
        setValues(
                autoAcceptFileSize,
                R.array.file_size_values,
                value -> {
                    if (value <= 0) {
                        return getString(R.string.never);
                    } else {
                        return UIHelper.filesizeToString(value);
                    }
                });
    }

    private void importStickers() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*"); //allows any image file type. Change * to specific extension to limit it
        //**These following line is the important one!
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select images"), REQUEST_IMPORT_STICKERS); //REQUEST_IMPORT_STICKERS is simply a global int used to check the calling intent in onActivityResult
    }

    private void importGifs() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/gif"); //allows any image file type. Change * to specific extension to limit it
        //**These following line is the important one!
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select GIFs"), REQUEST_IMPORT_GIFS); //REQUEST_IMPORT_STICKERS is simply a global int used to check the calling intent in onActivityResult
    }

    protected void downloadStickers() {
        final var intent = new Intent(requireActivity(), DownloadDefaultStickers.class);
        intent.putExtra("tor", requireService().useTorToConnect());
        intent.putExtra("i2p", requireService().useI2PToConnect());
        ContextCompat.startForegroundService(requireActivity(), intent);
        runOnUiThread(() -> Toast.makeText(requireActivity(), R.string.sticker_download_started, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_attachments);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) return;
        /*
        final var p = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        if (data.getData() != null && requestCode == 0 ) {
            String newStickerDirPath = data.getData().toString();
            p.edit().putString("sticker_directory", newStickerDirPath).apply(); // Use apply() for asynchronous save
            final var stickerDirPreference = findPreference("sticker_directory");
            if (stickerDirPreference != null) {
                stickerDirPreference.setSummary(newStickerDirPath);
            }
        }
        */

        // Import and compress stickers
        if(requestCode == REQUEST_IMPORT_STICKERS) {
            if(resultCode == RESULT_OK) {
                if(data.getClipData() != null) {
                    for(int i = 0; i < data.getClipData().getItemCount(); i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        //do something with the image (save it to some directory or whatever you need to do with it here)
                        if (imageUri != null) {
                            InputStream in;
                            OutputStream out;
                            try {
                                File stickerfolder = new File(StickersMigration.getStickersDir(getContext()), "Custom");
                                //create output directory if it doesn't exist
                                if (!stickerfolder.exists()) {
                                    stickerfolder.mkdirs();
                                }

                                String filename = getFileName(imageUri);
                                File newSticker = new File(stickerfolder, filename);

                                in = requireXmppActivity().getContentResolver().openInputStream(imageUri);
                                out = new FileOutputStream(newSticker);
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, read);
                                }
                                in.close();
                                in = null;
                                // write the output file
                                out.flush();
                                out.close();
                                out = null;
                                if (!filename.endsWith(".webp") && !filename.endsWith(".svg")) {
                                    compressImageToSticker(newSticker, imageUri, 0);
                                }
                            } catch (IOException exception) {
                                Toast.makeText(requireActivity(),R.string.import_sticker_failed,Toast.LENGTH_LONG).show();
                                Log.d(Config.LOGTAG, "Could not import sticker" + exception);
                            }
                        }
                    }
                    Toast.makeText(requireActivity(),R.string.sticker_imported,Toast.LENGTH_LONG).show();
                    requireXmppActivity().xmppConnectionService.rescanStickers();       //TODO: Check again if really needed
                } else if(data.getData() != null) {
                    Uri imageUri = data.getData();
                    //do something with the image (save it to some directory or whatever you need to do with it here)
                    if (imageUri != null) {
                        InputStream in;
                        OutputStream out;
                        try {
                            File stickerfolder = new File(StickersMigration.getStickersDir(getContext()), "Custom");
                            //create output directory if it doesn't exist
                            if (!stickerfolder.exists()) {
                                stickerfolder.mkdirs();
                            }

                            String filename = getFileName(imageUri);
                            File newSticker = new File(stickerfolder, filename);

                            in = requireXmppActivity().getContentResolver().openInputStream(imageUri);
                            out = new FileOutputStream(newSticker);
                            byte[] buffer = new byte[4096];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                            in.close();
                            in = null;
                            // write the output file
                            out.flush();
                            out.close();
                            out = null;
                            if (!filename.endsWith(".webp") && !filename.endsWith(".svg")) {
                                compressImageToSticker(newSticker, imageUri, 0);
                            }
                            Toast.makeText(requireActivity(),R.string.sticker_imported,Toast.LENGTH_LONG).show();
                            requireXmppActivity().xmppConnectionService.rescanStickers();       //TODO: Check again if really needed
                        } catch (IOException exception) {
                            Toast.makeText(requireActivity(),R.string.import_sticker_failed,Toast.LENGTH_LONG).show();
                            Log.d(Config.LOGTAG, "Could not import sticker" + exception);
                        }
                    }
                }
            }
        }

        // Import GIFs
        if(requestCode == REQUEST_IMPORT_GIFS) {
            if(resultCode == RESULT_OK) {
                if(data.getClipData() != null) {
                    for(int i = 0; i < data.getClipData().getItemCount(); i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        //do something with the image (save it to some directory or whatever you need to do with it here)
                        if (imageUri != null) {
                            InputStream in;
                            OutputStream out;
                            try {
                                File gifsfolder = new File(StickersMigration.getStickersDir(getContext()), "GIFs");
                                //create output directory if it doesn't exist
                                if (!gifsfolder.exists()) {
                                    gifsfolder.mkdirs();
                                }
                                String filename = getFileName(imageUri);
                                File newGif = new File(gifsfolder, filename);

                                in = requireXmppActivity().getContentResolver().openInputStream(imageUri);
                                out = new FileOutputStream(newGif);
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, read);
                                }
                                in.close();
                                in = null;
                                // write the output file
                                out.flush();
                                out.close();
                                out = null;
                            } catch (IOException exception) {
                                Toast.makeText(requireActivity(),R.string.import_gif_failed,Toast.LENGTH_LONG).show();
                                Log.d(Config.LOGTAG, "Could not import GIF" + exception);
                            }
                        }
                    }
                    Toast.makeText(requireActivity(),R.string.gif_imported,Toast.LENGTH_LONG).show();
                    requireXmppActivity().xmppConnectionService.rescanStickers();       //TODO: Check again if really needed
                } else if(data.getData() != null) {
                    Uri imageUri = data.getData();
                    //do something with the image (save it to some directory or whatever you need to do with it here)
                    if (imageUri != null) {
                        InputStream in;
                        OutputStream out;
                        try {
                            File gifsfolder = new File(StickersMigration.getStickersDir(getContext()), "GIFs");
                            //create output directory if it doesn't exist
                            if (!gifsfolder.exists()) {
                                gifsfolder.mkdirs();
                            }
                            String filename = getFileName(imageUri);
                            File newGif = new File(gifsfolder, filename);

                            in = requireXmppActivity().getContentResolver().openInputStream(imageUri);
                            out = new FileOutputStream(newGif);
                            byte[] buffer = new byte[4096];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                            in.close();
                            in = null;
                            // write the output file
                            out.flush();
                            out.close();
                            out = null;
                            Toast.makeText(requireActivity(),R.string.gif_imported,Toast.LENGTH_LONG).show();
                            requireXmppActivity().xmppConnectionService.rescanStickers();       //TODO: Check again if really needed
                        } catch (IOException exception) {
                            Toast.makeText(requireActivity(),R.string.import_gif_failed,Toast.LENGTH_LONG).show();
                            Log.d(Config.LOGTAG, "Could not import gif" + exception);
                        }
                    }
                }
            }
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = requireXmppActivity().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void compressImageToSticker(File f, Uri image, int sampleSize) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        int IMAGE_QUALITY = 65;
        int ImageSize = (int) (0.04 * 1024 * 1024);
        try {
            if (!f.exists() && !f.createNewFile()) {
                throw new IOException(String.valueOf(R.string.error_unable_to_create_temporary_file));
            }
            is = requireXmppActivity().getContentResolver().openInputStream(image);
            if (is == null) {
                throw new IOException(String.valueOf(R.string.error_not_an_image_file));
            }
            final Bitmap originalBitmap;
            final BitmapFactory.Options options = new BitmapFactory.Options();
            final int inSampleSize = (int) Math.pow(2, sampleSize);
            Log.d(Config.LOGTAG, "reading bitmap with sample size " + inSampleSize);
            options.inSampleSize = inSampleSize;
            originalBitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            if (originalBitmap == null) {
                throw new IOException("Source file was not an image");
            }
            if (!"image/jpeg".equals(options.outMimeType) && hasAlpha(originalBitmap)) {
                originalBitmap.recycle();
                throw new IOException("Source file had alpha channel");
            }
            int size;
            int resolution = 480;
            if (resolution == 0) {
                int height = originalBitmap.getHeight();
                int width = originalBitmap.getWidth();
                size = height > width ? height : width;
            } else {
                size = resolution;
            }
            Bitmap scaledBitmap = resize(originalBitmap, size);
            final int rotation = getRotation(image);
            scaledBitmap = rotate(scaledBitmap, rotation);
            boolean targetSizeReached = false;
            int quality = IMAGE_QUALITY;
            while (!targetSizeReached) {
                os = new FileOutputStream(f);
                boolean success = scaledBitmap.compress(Config.IMAGE_FORMAT, quality, os);
                if (!success) {
                    throw new IOException(String.valueOf(R.string.error_compressing_image));
                }
                os.flush();
                targetSizeReached = (f.length() <= ImageSize && ImageSize != 0) || quality <= 50;
                quality -= 5;
            }
            scaledBitmap.recycle();
        } catch (final FileNotFoundException e) {
            cleanup(f);
            throw new IOException(String.valueOf(R.string.error_file_not_found));
        } catch (final IOException e) {
            cleanup(f);
            throw new IOException(String.valueOf(R.string.error_io_exception));
        } catch (SecurityException e) {
            cleanup(f);
            throw new IOException(String.valueOf(R.string.error_security_exception_during_image_copy));
        } catch (final OutOfMemoryError e) {
            ++sampleSize;
            if (sampleSize <= 3) {
                compressImageToSticker(f, image, sampleSize);
            } else {
                throw new IOException(String.valueOf(R.string.error_out_of_memory));
            }
        } finally {
            close(os);
            close(is);
        }
    }

    private int getRotation(final File f) {
        try (final InputStream inputStream = new FileInputStream(f)) {
            return getRotation(inputStream);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getRotation(final Uri image) {
        try (final InputStream is = requireXmppActivity().getContentResolver().openInputStream(image)) {
            return is == null ? 0 : getRotation(is);
        } catch (final Exception e) {
            return 0;
        }
    }

    public static int getRotation(final InputStream inputStream) throws IOException {
        final ExifInterface exif = new ExifInterface(inputStream);
        final int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static Bitmap rotate(final Bitmap bitmap, final int degree) {
        if (degree == 0) {
            return bitmap;
        }
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        final Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return result;
    }

    public static void close(final Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to close stream", e);
            }
        }
    }

    private static void cleanup(final File file) {
        try {
            file.delete();
        } catch (Exception e) {

        }
    }

    private Bitmap resize(final Bitmap originalBitmap, int size) throws IOException {
        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IOException("Decoded bitmap reported bounds smaller 0");
        } else if (Math.max(w, h) > size) {
            int scalledW;
            int scalledH;
            if (w <= h) {
                scalledW = Math.max((int) (w / ((double) h / size)), 1);
                scalledH = size;
            } else {
                scalledW = size;
                scalledH = Math.max((int) (h / ((double) w / size)), 1);
            }
            final Bitmap result = Bitmap.createScaledBitmap(originalBitmap, scalledW, scalledH, true);
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            return result;
        } else {
            return originalBitmap;
        }
    }

    private static boolean hasAlpha(final Bitmap bitmap) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final int yStep = Math.max(1, w / 100);
        final int xStep = Math.max(1, h / 100);
        for (int x = 0; x < w; x += xStep) {
            for (int y = 0; y < h; y += yStep) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 255) {
                    return true;
                }
            }
        }
        return false;
    }
}
