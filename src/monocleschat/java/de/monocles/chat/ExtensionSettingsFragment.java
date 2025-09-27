package de.monocles.chat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.google.common.io.Files;

import java.io.File;
import java.util.ArrayList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentExtensionSettingsBinding;
import eu.siacs.conversations.databinding.ExtensionItemBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.StubConversation;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.worker.ExportBackupWorker;

public class ExtensionSettingsFragment extends androidx.fragment.app.Fragment {
	FragmentExtensionSettingsBinding binding;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = DataBindingUtil.inflate(inflater, R.layout.fragment_extension_settings, container, false);
		binding.addExtension.setOnClickListener((v) -> {
			final var intent = new Intent();
			intent.setAction(Intent.ACTION_GET_CONTENT);
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			intent.setType("*/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.perform_action_with)), 0x1);
		});

		binding.extensionList.setAdapter(new RecyclerView.Adapter<WebxdcViewHolder>() {
			final ArrayList<WebxdcPage> xdcs = new ArrayList<>();

			@Override
			public int getItemCount() {
				xdcs.clear();
				final var activity = (XmppActivity) requireActivity();
				final var xmppConnectionService = activity.xmppConnectionService;
				if (xmppConnectionService == null) return xdcs.size();
				final var dir = new File(xmppConnectionService.getExternalFilesDir(null), "extensions");
				for (File file : Files.fileTraverser().breadthFirst(dir)) {
					if (file.isFile() && file.canRead()) {
						final var dummy = new Message(new StubConversation(null, "", null, 0), null, Message.ENCRYPTION_NONE);
						dummy.setStatus(Message.STATUS_DUMMY);
						dummy.setUuid(file.getName());
						xdcs.add(new WebxdcPage(activity, file, dummy));
					}
				}
				return xdcs.size();
			}

			@Override
			public WebxdcViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
				final ExtensionItemBinding binding = DataBindingUtil.inflate(inflater, R.layout.extension_item, container, false);
				return new WebxdcViewHolder(binding);
			}

			@Override
			public void onBindViewHolder(WebxdcViewHolder holder, int position) {
				holder.bind(xdcs.get(position));
			}
		});

		return binding.getRoot();
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
	}

	@Override
	public void onStart() {
		super.onStart();
		getActivity().setTitle(getString(R.string.pref_extensions_title));
	}

	public void addExtension(Uri uri) {
		final var xmppConnectionService = ((XmppActivity) requireActivity()).xmppConnectionService;
		if (xmppConnectionService == null) return;
		try {
			final var fileBackend = xmppConnectionService.getFileBackend();
			final var base = fileBackend.calculateCids(fileBackend.openInputStream(uri))[0].toString();
			final var target = new File(new File(xmppConnectionService.getExternalFilesDir(null), "extensions"), base + ".xdc");
			fileBackend.copyFileToPrivateStorage(target, uri);
		} catch (final Exception e) {
			Toast.makeText(requireActivity(), "Could not copy extension: " + e, Toast.LENGTH_SHORT).show();
		}
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		for (final var attachment : Attachment.extractAttachments(requireActivity(), data, Attachment.Type.FILE)) {
			if ("application/webxdc+zip".equals(attachment.getMime())) addExtension(attachment.getUri());
		}
		binding.extensionList.getAdapter().notifyDataSetChanged();
	}

	protected static class WebxdcViewHolder extends RecyclerView.ViewHolder {
		final ExtensionItemBinding binding;

		public WebxdcViewHolder(final ExtensionItemBinding binding) {
			super(binding.getRoot());
			this.binding = binding;
		}

		public void bind(WebxdcPage xdc) {
			binding.icon.setImageDrawable(xdc.getIcon());
			binding.name.setText(xdc.getName());
		}
	}
}
