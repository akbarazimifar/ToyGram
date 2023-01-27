package it.owlgram.android.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CreationTextCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import it.owlgram.android.OwlConfig;
import it.owlgram.android.components.EmojiSetCell;
import it.owlgram.android.helpers.CustomEmojiHelper;
import it.owlgram.android.helpers.FileDownloadHelper;
import it.owlgram.android.helpers.FileUnzipHelper;

public class EmojiPackSettings extends BaseSettingsActivity implements NotificationCenter.NotificationCenterDelegate, ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate {

    private int generalHeaderRow;
    private int emojiDividerRow;
    private int useSystemEmojiRow;
    private int useCustomEmojiHeaderRow;
    private int customEmojiHintRow;
    private int customEmojiStartRow;
    private int customEmojiEndRow;
    private int customEmojiAddRow;
    private int emojiPackHeaderRow;
    private int placeHolderRow;
    private int emojiPacksStartRow;
    private int emojiPacksEndRow;
    private int emojiHintRow;

    private ChatAttachAlert chatAttachAlert;
    private NumberTextView selectedCountTextView;
    private AlertDialog progressDialog;

    private static final int MENU_DELETE = 0;
    private static final int MENU_SHARE = 1;

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, AndroidUtilities.dp(54));
        actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54));
        return view;
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= customEmojiStartRow && position < customEmojiEndRow) {
            ((ListAdapter) listAdapter).toggleSelected(position);
            return true;
        }
        return super.onItemLongClick(view, position, x, y);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        super.onItemClick(view, position, x, y);
        ListAdapter adapter = (ListAdapter) listAdapter;
        if (position >= emojiPacksStartRow && position < emojiPacksEndRow) {
            EmojiSetCell cell = (EmojiSetCell) view;
            if (cell.isChecked() || adapter.hasSelected()) return;
            String currentDownloading = getCurrentDownloading();
            String currentUnzipping = getCurrentUnzipping();
            boolean isDownloading = FileDownloadHelper.isRunningDownload(cell.packId);
            boolean isUnzipping = FileUnzipHelper.isRunningUnzip(cell.packId);
            if (!isDownloading && currentDownloading != null) {
                FileDownloadHelper.cancel(currentDownloading);
            }
            if (!isUnzipping && currentUnzipping != null) {
                FileUnzipHelper.cancel(currentUnzipping);
            }
            if (isDownloading || isUnzipping) return;
            if (CustomEmojiHelper.emojiDir(cell.packId, cell.versionWithMD5).exists() || cell.packId.equals("default")) {
                OwlConfig.setEmojiPackSelected(cell.packId);
                cell.setChecked(true, true);
                Emoji.reloadEmoji();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                if (OwlConfig.useSystemEmoji) {
                    OwlConfig.toggleUseSystemEmoji();
                    listAdapter.notifyItemChanged(useSystemEmojiRow, PARTIAL);
                }
            } else {
                cell.setProgress(true, true);
                CustomEmojiHelper.mkDirs();
                FileDownloadHelper.downloadFile(ApplicationLoader.applicationContext, cell.packId, CustomEmojiHelper.emojiTmp(cell.packId), cell.packFileLink);
            }
            adapter.notifyEmojiSetsChanged();
        } else if (position == useSystemEmojiRow) {
            OwlConfig.toggleUseSystemEmoji();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(OwlConfig.useSystemEmoji);
            }
            Emoji.reloadEmoji();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
            if (OwlConfig.useSystemEmoji) {
                FileDownloadHelper.cancel(getCurrentDownloading());
                FileUnzipHelper.cancel(getCurrentUnzipping());
            }
            adapter.notifyEmojiSetsChanged();
        } else if (position == customEmojiAddRow) {
            chatAttachAlert = new ChatAttachAlert(context, EmojiPackSettings.this, false, false);
            chatAttachAlert.setEmojiPicker();
            chatAttachAlert.init();
            chatAttachAlert.show();
        } else if (position >= customEmojiStartRow && position < customEmojiEndRow) {
            EmojiSetCell cell = (EmojiSetCell) view;
            if (adapter.hasSelected()) {
                adapter.toggleSelected(position);
            } else {
                if (cell.isChecked()) return;
                cell.setChecked(true, true);
                adapter.notifyEmojiSetsChanged();
                OwlConfig.setEmojiPackSelected(cell.packId);
                FileDownloadHelper.cancel(getCurrentDownloading());
                FileUnzipHelper.cancel(getCurrentUnzipping());
                Emoji.reloadEmoji();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                if (OwlConfig.useSystemEmoji) {
                    OwlConfig.toggleUseSystemEmoji();
                    listAdapter.notifyItemChanged(useSystemEmojiRow, PARTIAL);
                }
            }
        }
    }

    private String getCurrentUnzipping() {
        return CustomEmojiHelper.getEmojiPacksInfo()
                .parallelStream()
                .map(CustomEmojiHelper.EmojiPackBase::getPackId)
                .filter(FileUnzipHelper::isRunningUnzip)
                .findFirst()
                .orElse(null);
    }

    private String getCurrentDownloading() {
        return CustomEmojiHelper.getEmojiPacksInfo()
                .parallelStream()
                .map(CustomEmojiHelper.EmojiPackBase::getPackId)
                .filter(FileDownloadHelper::isRunningDownload)
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void attachListeners(EmojiSetCell cell) {
        FileDownloadHelper.addListener(cell.packId, "emojiCellSettings", new FileDownloadHelper.FileDownloadListener() {
            @Override
            public void onPreStart(String id) {
                if (cell.packId.equals(id)) {
                    cell.setProgress(0, 0, true);
                }
            }

            @Override
            public void onProgressChange(String id, int percentage, long downBytes, long totBytes) {
                if (cell.packId.equals(id)) {
                    cell.setProgress(percentage, downBytes, false);
                }
            }

            @Override
            public void onFinished(String id, boolean isFailed) {
                if (cell.packId.equals(id)) {
                    if (CustomEmojiHelper.emojiTmpDownloaded(cell.packId)) {
                        FileUnzipHelper.unzipFile(ApplicationLoader.applicationContext, cell.packId, CustomEmojiHelper.emojiTmp(cell.packId), CustomEmojiHelper.emojiDir(cell.packId, cell.versionWithMD5));
                    } else {
                        CustomEmojiHelper.emojiTmp(cell.packId).delete();
                        ((ListAdapter) listAdapter).notifyEmojiSetsChanged();
                        if (isFailed) BulletinFactory.of(EmojiPackSettings.this).createErrorBulletin(LocaleController.getString("EmojiSetErrorDownloading", R.string.EmojiSetErrorDownloading)).show();
                    }
                }
                cell.checkDownloaded(true);
            }
        });
        FileUnzipHelper.addListener(cell.packId, "emojiCellSettings", (id) -> {
            if (cell.packId.equals(id)) {
                CustomEmojiHelper.emojiTmp(cell.packId).delete();
                if (CustomEmojiHelper.emojiDir(cell.packId, cell.versionWithMD5).exists()) {
                    OwlConfig.setEmojiPackSelected(cell.packId);
                    CustomEmojiHelper.deleteOldVersions(cell.packId, cell.versionWithMD5);
                    Emoji.reloadEmoji();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                    if (OwlConfig.useSystemEmoji) {
                        OwlConfig.toggleUseSystemEmoji();
                        listAdapter.notifyItemChanged(useSystemEmojiRow, PARTIAL);
                    }
                }
            }
            ((ListAdapter) listAdapter).notifyEmojiSetsChanged();
            cell.checkDownloaded(true);
        });
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiPacksLoaded);
        CustomEmojiHelper.loadEmojisInfo();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiPacksLoaded);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("EmojiSets", R.string.EmojiSets);
    }

    @Override
    protected void updateRowsId() {
        super.updateRowsId();
        generalHeaderRow =  rowCount++;
        useSystemEmojiRow = rowCount++;
        emojiDividerRow = rowCount++;
        useCustomEmojiHeaderRow = rowCount++;
        customEmojiStartRow = rowCount;
        rowCount += CustomEmojiHelper.getEmojiCustomPacksInfo().size();
        customEmojiEndRow = rowCount;
        customEmojiAddRow = rowCount++;
        customEmojiHintRow = rowCount++;

        emojiPackHeaderRow = rowCount++;
        if (CustomEmojiHelper.loadedPackInfo()) {
            emojiPacksStartRow = rowCount;
            rowCount += CustomEmojiHelper.getEmojiPacksInfo().size();
            emojiPacksEndRow = rowCount;
        } else {
            emojiPacksStartRow = -1;
            emojiPacksEndRow = -1;
            placeHolderRow = rowCount++;
        }
        emojiHintRow = rowCount++;
    }

    @Override
    protected BaseListAdapter createAdapter() {
        return new ListAdapter();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiPacksLoaded) {
            if (!CustomEmojiHelper.loadedPackInfo()) {
                AndroidUtilities.runOnUIThread(CustomEmojiHelper::loadEmojisInfo, 1000);
            } else {
                if (listAdapter != null) {
                    updateRowsId();
                    showItemsAnimated(emojiPacksStartRow + 1);
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    @Override
    public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<MessageObject> fMessages, boolean notify, int scheduleDate) {
        ArrayList<File> filesToUpload = new ArrayList<>();
        for (String file : files) {
            File f = new File(file);
            if (f.exists()) {
                filesToUpload.add(f);
            }
        }
        processFiles(filesToUpload);
    }

    private class ListAdapter extends BaseListAdapter {
        private final SparseBooleanArray selectedItems = new SparseBooleanArray();

        @Override
        protected void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (ViewType.fromInt(holder.getItemViewType())) {
                case HEADER:
                    HeaderCell headerViewHolder = (HeaderCell) holder.itemView;
                    if (position == emojiPackHeaderRow) {
                        headerViewHolder.setText(LocaleController.getString("EmojiSets", R.string.EmojiSets));
                    } else if (position == generalHeaderRow) {
                        headerViewHolder.setText(LocaleController.getString("General", R.string.General));
                    } else if (position == useCustomEmojiHeaderRow) {
                        headerViewHolder.setText(LocaleController.getString("MyEmojiSets", R.string.MyEmojiSets));
                    }
                    break;
                case EMOJI_PACK_SET_CELL:
                    EmojiSetCell emojiPackSetCell = (EmojiSetCell) holder.itemView;
                    CustomEmojiHelper.EmojiPackBase emojiPackInfo = null;
                    if (position >= emojiPacksStartRow && position < emojiPacksEndRow) {
                        emojiPackInfo = CustomEmojiHelper.getEmojiPacksInfo().get(position - emojiPacksStartRow);
                    } else if (position >= customEmojiStartRow && position < customEmojiEndRow) {
                        emojiPackInfo = CustomEmojiHelper.getEmojiCustomPacksInfo().get(position - customEmojiStartRow);
                    }
                    emojiPackSetCell.setSelected(selectedItems.get(position, false), partial);
                    if (emojiPackInfo != null) {
                        emojiPackSetCell.setChecked(!hasSelected() && emojiPackInfo.getPackId().equals(CustomEmojiHelper.getSelectedEmojiPackId()) && getCurrentDownloading() == null && getCurrentUnzipping() == null && !OwlConfig.useSystemEmoji, partial);
                        emojiPackSetCell.setData(
                                emojiPackInfo,
                                partial,
                                true
                        );
                        if (emojiPackInfo instanceof CustomEmojiHelper.EmojiPackInfo) {
                            attachListeners(emojiPackSetCell);
                        }
                    }
                    break;
                case TEXT_HINT_WITH_PADDING:
                    TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == emojiHintRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("EmojiSetHint", R.string.EmojiSetHint));
                    } else if (position == customEmojiHintRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("CustomEmojiSetHint", R.string.CustomEmojiSetHint));
                    }
                    break;
                case SWITCH:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == useSystemEmojiRow) {
                        if (!partial) textCheckCell.setTextAndValueAndCheck(LocaleController.getString("UseSystemEmojis", R.string.UseSystemEmojis), LocaleController.getString("UseSystemEmojisDesc", R.string.UseSystemEmojisDesc), OwlConfig.useSystemEmoji, true, false);
                        if (partial) textCheckCell.setChecked(OwlConfig.useSystemEmoji);
                    }
                    break;
                case CREATION_TEXT_CELL:
                    CreationTextCell creationTextCell = (CreationTextCell) holder.itemView;
                    if (position == customEmojiAddRow) {
                        Drawable drawable1 = creationTextCell.getContext().getResources().getDrawable(R.drawable.poll_add_circle);
                        Drawable drawable2 = creationTextCell.getContext().getResources().getDrawable(R.drawable.poll_add_plus);
                        drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                        drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
                        creationTextCell.setTextAndIcon(LocaleController.getString("AddEmojiSet", R.string.AddEmojiSet), combinedDrawable, false);
                    }
                    break;
                case PLACEHOLDER:
                    FlickerLoadingView flickerLoadingView = (FlickerLoadingView) holder.itemView;
                    flickerLoadingView.setViewType(FlickerLoadingView.STICKERS_TYPE);
                    flickerLoadingView.setIsSingleCell(true);
                    break;
            }
        }

        @Override
        protected ViewType getViewType(int position) {
            if (position == emojiPackHeaderRow || position == generalHeaderRow || position == useCustomEmojiHeaderRow) {
                return ViewType.HEADER;
            } else if ((position >= emojiPacksStartRow && position < emojiPacksEndRow) || (position >= customEmojiStartRow && position < customEmojiEndRow)) {
                return ViewType.EMOJI_PACK_SET_CELL;
            } else if (position == emojiHintRow || position == customEmojiHintRow) {
                return ViewType.TEXT_HINT_WITH_PADDING;
            } else if (position == useSystemEmojiRow) {
                return ViewType.SWITCH;
            } else if (position == emojiDividerRow) {
                return ViewType.SHADOW;
            } else if (position == customEmojiAddRow) {
                return ViewType.CREATION_TEXT_CELL;
            } else if (position == placeHolderRow) {
                return ViewType.PLACEHOLDER;
            }
            throw new IllegalArgumentException("Invalid position");
        }

        @Override
        protected boolean isEnabled(ViewType viewType, int position) {
            return viewType == ViewType.EMOJI_PACK_SET_CELL || viewType == ViewType.SWITCH || viewType == ViewType.CREATION_TEXT_CELL;
        }

        public void toggleSelected(int position) {
            selectedItems.put(position, !selectedItems.get(position, false));
            notifyEmojiSetsChanged();
            checkActionMode();
        }

        public boolean hasSelected() {
            return selectedItems.indexOfValue(true) != -1;
        }

        public void clearSelected() {
            selectedItems.clear();
            notifyEmojiSetsChanged();
            checkActionMode();
        }

        public int getSelectedCount() {
            int count = 0;
            for (int i = 0, size = selectedItems.size(); i < size; i++) {
                if (selectedItems.valueAt(i)) {
                    count++;
                }
            }
            return count;
        }

        private void notifyEmojiSetsChanged() {
            notifyItemRangeChanged(customEmojiStartRow, customEmojiEndRow - customEmojiStartRow, PARTIAL);
            notifyItemRangeChanged(emojiPacksStartRow, emojiPacksEndRow - emojiPacksStartRow, PARTIAL);
        }

        private void checkActionMode() {
            int selectedCount = getSelectedCount();
            boolean actionModeShowed = actionBar.isActionModeShowed();
            if (selectedCount > 0) {
                selectedCountTextView.setNumber(selectedCount, actionModeShowed);
                if (!actionModeShowed) {
                    actionBar.showActionMode();
                }
            } else if (actionModeShowed) {
                actionBar.hideActionMode();
            }
        }

        private void processSelectionMenu(int which) {

            if (which == MENU_DELETE || which == MENU_SHARE) {
                ArrayList<CustomEmojiHelper.EmojiPackBase> stickerSetList = new ArrayList<>(selectedItems.size());
                ArrayList<CustomEmojiHelper.EmojiPackBase> packs = CustomEmojiHelper.getEmojiCustomPacksInfo();
                for (int i = 0, size = packs.size(); i < size; i++) {
                    CustomEmojiHelper.EmojiPackBase pack = packs.get(i);
                    if (selectedItems.get(customEmojiStartRow + i, false)) {
                        stickerSetList.add(pack);
                    }
                }
                int count = stickerSetList.size();
                switch (count) {
                    case 0:
                        break;
                    case 1:
                        CustomEmojiHelper.EmojiPackBase pack = stickerSetList.get(0);
                        if (which == MENU_SHARE) {
                            Intent intent = new Intent(context, LaunchActivity.class);
                            intent.setAction(Intent.ACTION_SEND);
                            Uri uri = Uri.fromFile(new File(pack.getFileLocation()));
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            context.startActivity(intent);
                            clearSelected();
                        } else {
                            CustomEmojiHelper.cancelableDelete(EmojiPackSettings.this, pack, new CustomEmojiHelper.OnBulletinAction() {
                                @Override
                                public void onPreStart() {
                                    notifyItemRemoved(customEmojiStartRow + packs.indexOf(pack));
                                    notifyEmojiSetsChanged();
                                    updateRowsId();
                                    clearSelected();
                                }

                                @Override
                                public void onUndo() {
                                    notifyItemInserted(customEmojiStartRow + CustomEmojiHelper.getEmojiCustomPacksInfo().indexOf(pack));
                                    updateRowsId();
                                    notifyEmojiSetsChanged();
                                }
                            });
                        }
                        break;
                    default:
                        if (which == MENU_SHARE) {
                            Intent intent = new Intent(context, LaunchActivity.class);
                            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                            ArrayList<Uri> uriList = new ArrayList<>();
                            for (CustomEmojiHelper.EmojiPackBase packTmp : stickerSetList) {
                                uriList.add(Uri.fromFile(new File(packTmp.getFileLocation())));
                            }
                            if (!uriList.isEmpty()) {
                                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
                                context.startActivity(intent);
                            }
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.formatString("DeleteStickerSetsAlertTitle", R.string.DeleteStickerSetsAlertTitle, LocaleController.formatString("DeleteEmojiSets", R.string.DeleteEmojiSets, count)));
                            builder.setMessage(LocaleController.getString("DeleteEmojiSetsMessage", R.string.DeleteEmojiSetsMessage));
                            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which1) -> {
                                AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                                new Thread() {
                                    @Override
                                    public void run() {
                                        for (int i = 0, size = stickerSetList.size(); i < size; i++) {
                                            CustomEmojiHelper.deleteEmojiPack(stickerSetList.get(i));
                                        }
                                        AndroidUtilities.runOnUIThread(() -> {
                                            progressDialog.dismiss();
                                            clearSelected();
                                            List<int[]> consecutiveItems = stickerSetList.parallelStream()
                                                    .map(packs::indexOf)
                                                    .reduce(new ArrayList<>(), (acc, ordinalPos) -> {
                                                        boolean isConsecutive = acc.size() > 0 && ordinalPos == acc.get(acc.size() - 1)[1] + 1;
                                                        if (isConsecutive) {
                                                            int[] lastInterval = acc.get(acc.size() - 1);
                                                            lastInterval[1] = ordinalPos;
                                                        } else {
                                                            acc.add(new int[]{ordinalPos, ordinalPos});
                                                        }
                                                        return acc;
                                                    }, (acc1, acc2) -> acc1);
                                            for (int[] interval : consecutiveItems) {
                                                if (interval[0] == interval[1]) {
                                                    notifyItemRemoved(customEmojiStartRow + interval[0]);
                                                } else {
                                                    notifyItemRangeRemoved(customEmojiStartRow + interval[0], interval[1] - interval[0] + 1);
                                                }
                                            }
                                            updateRowsId();
                                            Emoji.reloadEmoji();
                                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                                        });
                                    }
                                }.start();
                                progressDialog.setCanCancel(false);
                                progressDialog.showDelayed(300);
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            AlertDialog dialog = builder.create();
                            showDialog(dialog);
                            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                            if (button != null) {
                                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                            }
                        }
                        break;
                }
            }
        }
    }

    public void startDocumentSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            photoPickerIntent.setType("font/*");
            startActivityForResult(photoPickerIntent, 21);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        progressDialog = new AlertDialog(getParentActivity(), 3);
        new Thread(() -> {
            if (requestCode == 21) {
                if (data == null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    });
                    return;
                }

                if (chatAttachAlert != null) {
                    ArrayList<File> files = CustomEmojiHelper.getFilesFromActivityResult(data);
                    AndroidUtilities.runOnUIThread(() -> {
                        boolean apply = files.parallelStream().allMatch(file -> chatAttachAlert.getDocumentLayout().isEmojiFont(file));
                        if (apply && !files.isEmpty()) {
                            chatAttachAlert.dismiss();
                            processFiles(files);
                        } else {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    });
                }
            }
        }).start();
        progressDialog.setCanCancel(false);
        progressDialog.showDelayed(300);
    }

    public void processFiles(ArrayList<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        if (progressDialog == null) {
            progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCancel(false);
            progressDialog.showDelayed(300);
        }
        new Thread(() -> {
            int count = 0;
            for (File file : files) {
                try {
                    if (CustomEmojiHelper.installEmoji(file) != null) {
                        count++;
                    }
                } catch (Exception e) {
                    FileLog.e("Emoji Font install failed", e);
                } finally {
                    CustomEmojiHelper.deleteTempFile(file);
                }
            }
            int finalCount = count;
            AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                progressDialog = null;
                listAdapter.notifyItemRangeInserted(customEmojiEndRow, finalCount);
                updateRowsId();
            });
        }).start();
    }

    @Override
    public boolean onBackPressed() {
        ListAdapter adapter = (ListAdapter) listAdapter;
        if (adapter.hasSelected()) {
            adapter.clearSelected();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    protected void onMenuItemClick(int id) {
        super.onMenuItemClick(id);
        if (id == MENU_DELETE || id == MENU_SHARE) {
            ListAdapter adapter = (ListAdapter) listAdapter;
            adapter.processSelectionMenu(id);
        }
    }
}
