package eu.siacs.conversations.ui.fragment.settings;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.activity.SettingsActivity;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.worker.ExportBackupWorker;
import me.drakeet.support.toast.ToastCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BackupSettingsFragment extends XmppPreferenceFragment {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US);
    public static final String CREATE_ONE_OFF_BACKUP = "create_one_off_backup";
    private static final String RECURRING_BACKUP = "recurring_backup";
    public static final int REQUEST_EXPORT_SETTINGS = 0xbf8701;
    public static final int REQUEST_IMPORT_SETTINGS = 0xbf8703;

    private final ActivityResultLauncher<String> requestStorageForBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            startOneOffBackup();
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

    private final ActivityResultLauncher<Uri> pickBackupLocationLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocumentTree(),
                    uri -> {
                        if (uri == null) {
                            Log.d(Config.LOGTAG, "no backup location selected");
                            return;
                        }
                        submitBackupLocationPreference(uri);
                    });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_backup, rootKey);
        final var createOneOffBackup = findPreference(CREATE_ONE_OFF_BACKUP);
        final var export = findPreference("export");
        final ListPreference recurringBackup = findPreference(RECURRING_BACKUP);
        final var backupLocation = findPreference(AppSettings.BACKUP_LOCATION);
        if (createOneOffBackup == null || recurringBackup == null || backupLocation == null) {
            throw new IllegalStateException(
                    "The preference resource file is missing some preferences");
        }
        final var appSettings = new AppSettings(requireContext());
        backupLocation.setSummary(
                getString(
                        R.string.pref_create_backup_summary,
                        appSettings.getBackupLocationAsPath()));
        backupLocation.setOnPreferenceClickListener(this::onBackupLocationPreferenceClicked);
        createOneOffBackup.setOnPreferenceClickListener(this::onBackupPreferenceClicked);
        export.setOnPreferenceClickListener(this::onExportClicked);
        setValues(
                recurringBackup,
                R.array.recurring_backup_values,
                value -> timeframeValueToName(requireContext(), value));

        final var importSettingsPreference = findPreference("import_settings");
        if (importSettingsPreference != null) {
            importSettingsPreference.setOnPreferenceClickListener(preference -> {
                if (requireSettingsActivity().hasStoragePermission(REQUEST_IMPORT_SETTINGS)) {
                    openSettingsPicker();
                }
                return true;
            });
        }

        final var exportSettingsPreference = findPreference("export_settings");
        if (exportSettingsPreference != null) {
            exportSettingsPreference.setOnPreferenceClickListener(preference -> {
                if (requireSettingsActivity().hasStoragePermission(REQUEST_EXPORT_SETTINGS)) {
                    exportSettings();
                }
                return true;
            });
        }
    }

    public void openSettingsPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_settings_dat)), REQUEST_IMPORT_SETTINGS);

    }

    private boolean onBackupLocationPreferenceClicked(final Preference preference) {
        this.pickBackupLocationLauncher.launch(null);
        return false;
    }

    private void submitBackupLocationPreference(final Uri uri) {
        final var contentResolver = requireContext().getContentResolver();
        contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        final var appSettings = new AppSettings(requireContext());
        appSettings.setBackupLocation(uri);
        final var preference = findPreference(AppSettings.BACKUP_LOCATION);
        if (preference == null) {
            return;
        }
        preference.setSummary(
                getString(R.string.pref_create_backup_summary, AppSettings.asPath(uri)));
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        if (RECURRING_BACKUP.equals(key)) {
            final var sharedPreferences = getPreferenceManager().getSharedPreferences();
            if (sharedPreferences == null) {
                return;
            }
            final Long recurringBackupInterval =
                    Longs.tryParse(
                            Strings.nullToEmpty(
                                    sharedPreferences.getString(RECURRING_BACKUP, null)));
            if (recurringBackupInterval == null) {
                return;
            }
            Log.d(
                    Config.LOGTAG,
                    "recurring backup interval changed to: " + recurringBackupInterval);
            final var workManager = WorkManager.getInstance(requireContext());
            if (recurringBackupInterval <= 0) {
                workManager.cancelUniqueWork(RECURRING_BACKUP);
            } else {
                final Constraints constraints =
                        new Constraints.Builder()
                                .setRequiresBatteryNotLow(true)
                                .setRequiresStorageNotLow(true)
                                .build();

                final PeriodicWorkRequest periodicWorkRequest =
                        new PeriodicWorkRequest.Builder(
                                ExportBackupWorker.class,
                                recurringBackupInterval,
                                TimeUnit.SECONDS)
                                .setConstraints(constraints)
                                .setInputData(
                                        new Data.Builder()
                                                .putBoolean("recurring_backup", true)
                                                .build())
                                .build();
                workManager.enqueueUniquePeriodicWork(
                        RECURRING_BACKUP, ExistingPeriodicWorkPolicy.UPDATE, periodicWorkRequest);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.backup);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMPORT_SETTINGS) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                importSettings(uri, requireSettingsActivity());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_EXPORT_SETTINGS) {
                    exportSettings();
                }
                if (requestCode == REQUEST_IMPORT_SETTINGS) {
                    ToastCompat.makeText(requireActivity(), "permissions for open setting spicker granted", ToastCompat.LENGTH_SHORT).show();
                    openSettingsPicker();
                }
            } else {
                ToastCompat.makeText(
                        requireActivity(),

                        R.string.no_storage_permission,
                        ToastCompat.LENGTH_SHORT).show();
            }
        }
    }

    private boolean onBackupPreferenceClicked(final Preference preference) {
        new AlertDialog.Builder(requireActivity())
            .setTitle(R.string.disable_all_accounts)
            .setMessage(R.string.disable_all_accounts_question)
            .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                for (final var account : requireService().getAccounts()) {
                    account.setOption(Account.OPTION_DISABLED, true);
                    if (!requireService().updateAccount(account)) {
                        Toast.makeText(requireActivity(), R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
                    }
                }
                aboutToStartOneOffBackup();
            })
            .setNegativeButton(R.string.no, (dialog, whichButton) -> aboutToStartOneOffBackup()).show();
        return true;
    }

    private void aboutToStartOneOffBackup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStorageForBackupLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                startOneOffBackup();
            }
        } else {
            startOneOffBackup();
        }
    }

    private void startOneOffBackup() {
        final OneTimeWorkRequest exportBackupWorkRequest =
                new OneTimeWorkRequest.Builder(ExportBackupWorker.class)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build();
        WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(
                        CREATE_ONE_OFF_BACKUP, ExistingWorkPolicy.KEEP, exportBackupWorkRequest);
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setMessage(R.string.backup_started_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }

    private void importSettings(Uri uri, SettingsActivity settingsActivity) {
        boolean success = false;
        try {
            File file = new File(FileUtils.getPath(requireSettingsActivity(), uri));
            try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(file))) {
                SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(settingsActivity).edit();
                prefEdit.clear();
                Map<String, ?> entries = (Map<String, ?>) input.readObject();
                for (Map.Entry<String, ?> entry : entries.entrySet()) {
                    Object value = entry.getValue();
                    String key = entry.getKey();

                    if (value instanceof Boolean) {
                        prefEdit.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Float) {
                        prefEdit.putFloat(key, (Float) value);
                    } else if (value instanceof Integer) {
                        prefEdit.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        prefEdit.putLong(key, (Long) value);
                    } else if (value instanceof String) {
                        prefEdit.putString(key, (String) value);
                    }
                }
                prefEdit.commit();
                success = true;
            }
        } catch (Exception e) {
            success = false;
            Log.e("SettingsImport", "Error importing settings", e);
        }

        int messageResId = success ? R.string.success_import_settings : R.string.error_import_settings;
        ToastCompat.makeText(settingsActivity, messageResId, ToastCompat.LENGTH_SHORT).show();
    }

    private void exportSettings() {
        boolean success = false;
        ObjectOutputStream output = null;
        final var context = requireSettingsActivity();
        final var appSettings = new AppSettings(context);
        final String path = appSettings.getBackupLocationAsPath();
        try {
            final File file = new File(path, DATE_FORMAT.format(new Date()) + "_settings.dat");
            final File directory = file.getParentFile();
            if (directory != null && directory.mkdirs()) {
                Log.d(Config.LOGTAG, "created backup directory " + directory.getAbsolutePath());
            }
            output = new ObjectOutputStream(new FileOutputStream(file));
            SharedPreferences pref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireSettingsActivity());
            output.writeObject(pref.getAll());
            success = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (success) {
            new Thread(() -> runOnUiThread(() -> requireActivity().recreate())).start();
            ToastCompat.makeText(requireActivity(), R.string.success_export_settings, ToastCompat.LENGTH_SHORT).show();
        } else {
            ToastCompat.makeText(requireActivity(), R.string.error_export_settings, ToastCompat.LENGTH_SHORT).show();
        }
    }

    public SettingsActivity requireSettingsActivity() {
        final var activity = requireActivity();
        if (activity instanceof SettingsActivity settingsActivity) {
            return settingsActivity;
        }
        throw new IllegalStateException(
                String.format(
                        "%s is not %s",
                        activity.getClass().getName(), SettingsActivity.class.getName()));
    }

    private boolean onExportClicked(final Preference preference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStorageForBackupLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                startExport();
            }
        } else {
            startExport();
        }
        return true;
    }

    private void startExport() {
        final OneTimeWorkRequest exportBackupWorkRequest =
                new OneTimeWorkRequest.Builder(ExportBackupWorker.class)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build();
        WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(
                        CREATE_ONE_OFF_BACKUP, ExistingWorkPolicy.KEEP, exportBackupWorkRequest);
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setMessage(R.string.backup_started_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }
}
