package com.example.cobaltevents.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.AdminController;
import com.example.cobaltevents.util.NetworkConnectivity;
import com.example.cobaltevents.db.CommentDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.model.Comment;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Reply;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * US 03.10.01 – Moderate comments for one event.
 *
 * <p>Lists comments and replies; admin can delete comments or replies and open a profile detail popup from a name/avatar.
 */
public class AdminCommentsActivity extends AppCompatActivity {

    /** Firestore event id passed from {@link AdminActivity}. */
    public static final String EXTRA_EVENT_ID   = "EXTRA_EVENT_ID";
    /** Display title for the toolbar / header. */
    public static final String EXTRA_EVENT_NAME = "EXTRA_EVENT_NAME";

    private String eventId;
    private String eventName;

    private RecyclerView commentsRecycler;
    private SwipeRefreshLayout swipeRefresh;
    private View loadingSpinner;
    private View emptyContainer;

    private AdminController adminController;
    private CommentDB commentDB;
    private ProfileDB profileDB;
    private List<Comment> commentList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_comments);

        eventId   = getIntent().getStringExtra(EXTRA_EVENT_ID);
        eventName = getIntent().getStringExtra(EXTRA_EVENT_NAME);

        adminController = new AdminController(this);
        commentDB       = new CommentDB();
        profileDB       = new ProfileDB();

        TextView btnBack      = findViewById(R.id.btnCommentsBack);
        TextView tvEventTitle = findViewById(R.id.tvCommentsEventTitle);
        commentsRecycler      = findViewById(R.id.commentsRecycler);
        swipeRefresh          = findViewById(R.id.commentsSwipeRefresh);
        loadingSpinner        = findViewById(R.id.commentsLoadingSpinner);
        emptyContainer        = findViewById(R.id.commentsEmptyContainer);

        tvEventTitle.setText(eventName != null ? eventName : "");
        btnBack.setOnClickListener(v -> finish());

        commentsRecycler.setLayoutManager(new LinearLayoutManager(this));

        swipeRefresh.setColorSchemeColors(
                android.graphics.Color.parseColor("#0D6EFD"),
                android.graphics.Color.parseColor("#0D1B2A"));
        swipeRefresh.setOnRefreshListener(this::loadComments);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadComments();
    }

    // =========================================================================
    // Data loading
    // =========================================================================

    private void loadComments() {
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            hideLoading();
            Toast.makeText(this, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading();
        commentDB.loadCommentsForEvent(eventId, null,
                comments -> {
                    commentList = comments != null ? comments : new ArrayList<>();
                    hideLoading();
                    updateList();
                },
                e -> {
                    hideLoading();
                    Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateList() {
        if (commentList.isEmpty()) {
            commentsRecycler.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.VISIBLE);
        } else {
            emptyContainer.setVisibility(View.GONE);
            commentsRecycler.setVisibility(View.VISIBLE);
            commentsRecycler.setAdapter(new CommentDetailAdapter(commentList));
        }
    }

    private void showLoading() {
        loadingSpinner.setVisibility(View.VISIBLE);
        commentsRecycler.setVisibility(View.GONE);
        emptyContainer.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingSpinner.setVisibility(View.GONE);
        if (swipeRefresh.isRefreshing()) swipeRefresh.setRefreshing(false);
    }

    // =========================================================================
    // Profile detail popup — same style as Browse Profiles
    // =========================================================================

    private void showProfileDialog(String userId, String userName) {
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
            return;
        }
        profileDB.getProfile(userId, profile -> {
            View dialogView = LayoutInflater.from(this)
                    .inflate(R.layout.dialog_view_detail, null);

            TextView tvType     = dialogView.findViewById(R.id.tvDetailType);
            TextView tvTitle    = dialogView.findViewById(R.id.tvDetailTitle);
            TextView tvSubtitle = dialogView.findViewById(R.id.tvDetailSubtitle);
            LinearLayout rowDetail   = dialogView.findViewById(R.id.rowDetail);
            TextView tvDetLabel = dialogView.findViewById(R.id.tvDetailDetailLabel);
            TextView tvDetail   = dialogView.findViewById(R.id.tvDetailDetail);
            LinearLayout rowMeta     = dialogView.findViewById(R.id.rowMeta);
            TextView tvMetaLabel= dialogView.findViewById(R.id.tvDetailMetaLabel);
            TextView tvMeta1    = dialogView.findViewById(R.id.tvDetailMeta1);
            TextView tvMeta2    = dialogView.findViewById(R.id.tvDetailMeta2);
            ImageView ivImage   = dialogView.findViewById(R.id.ivDetailImage);
            Button btnClose     = dialogView.findViewById(R.id.btnDetailClose);

            tvType.setText("PROFILE");

            if (profile != null) {
                tvTitle.setText(profile.getName() != null ? profile.getName() : userName);
                tvSubtitle.setText(profile.getEmail() != null ? profile.getEmail() : "");

                if (profile.getProfilePictureUrl() != null && !profile.getProfilePictureUrl().isEmpty()) {
                    ivImage.setVisibility(View.VISIBLE);
                    com.bumptech.glide.Glide.with(this)
                            .load(profile.getProfilePictureUrl())
                            .circleCrop()
                            .into(ivImage);
                }

                rowDetail.setVisibility(View.VISIBLE);
                tvDetLabel.setText("Contact");
                StringBuilder contact = new StringBuilder();
                if (profile.getEmail() != null && !profile.getEmail().isEmpty())
                    contact.append("\u2709 ").append(profile.getEmail());
                if (profile.getPhone() != null && !profile.getPhone().isEmpty())
                    contact.append("\n\ud83d\udcde ").append(profile.getPhone());
                tvDetail.setText(contact.toString());

                rowMeta.setVisibility(View.VISIBLE);
                tvMetaLabel.setText("Device ID");
                tvMeta1.setText(profile.getDeviceId() != null ? profile.getDeviceId() : "Unknown");
                tvMeta2.setText("");
            } else {
                tvTitle.setText(userName != null ? userName : "Unknown User");
                tvSubtitle.setText("Profile not found");
                rowMeta.setVisibility(View.VISIBLE);
                tvMetaLabel.setText("User ID");
                tvMeta1.setText(userId != null ? userId : "Unknown");
                tvMeta2.setText("");
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();
            btnClose.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
            if (dialog.getWindow() != null)
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            applyDetailDialogScrollMaxHeight(dialogView);

        }, e -> Toast.makeText(this, "Could not load profile", Toast.LENGTH_SHORT).show());
    }

    /** Same scroll max-height as admin browse detail / QR event popup. */
    private void applyDetailDialogScrollMaxHeight(View dialogRoot) {
        final View scroll = dialogRoot.findViewById(R.id.scroll_admin_detail_dialog);
        if (scroll == null) return;
        scroll.post(() -> {
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int maxH = (int) (screenH * 0.65f);
            if (scroll.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                lp.height = maxH;
                scroll.setLayoutParams(lp);
            }
        });
    }

    // =========================================================================
    // Delete helpers
    // =========================================================================

    private void confirmDeleteComment(Comment comment) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_delete_confirm, null);
        TextView tvTitle   = dialogView.findViewById(R.id.tvDeleteTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDeleteMessage);
        Button btnConfirm  = dialogView.findViewById(R.id.btnConfirmDelete);
        Button btnCancel   = dialogView.findViewById(R.id.btnCancelDelete);

        tvTitle.setText("Delete Comment?");
        tvMessage.setText("This will also delete all replies by other users.");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        btnConfirm.setOnClickListener(v -> {
            if (!NetworkConnectivity.hasValidatedInternet(this)) {
                Toast.makeText(this, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            adminController.removeComment(eventId, comment.getId(),
                    unused -> { Toast.makeText(this, "Comment deleted", Toast.LENGTH_SHORT).show(); loadComments(); },
                    e -> Toast.makeText(this, "Failed to delete comment", Toast.LENGTH_SHORT).show());
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void confirmDeleteReply(Comment comment, Reply reply) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_delete_confirm, null);
        TextView tvTitle   = dialogView.findViewById(R.id.tvDeleteTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDeleteMessage);
        Button btnConfirm  = dialogView.findViewById(R.id.btnConfirmDelete);
        Button btnCancel   = dialogView.findViewById(R.id.btnCancelDelete);

        tvTitle.setText("Delete Reply?");
        tvMessage.setText("Are you sure you want to delete this reply?");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        btnConfirm.setOnClickListener(v -> {
            if (!NetworkConnectivity.hasValidatedInternet(this)) {
                Toast.makeText(this, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            adminController.removeReply(eventId, comment.getId(), reply.getId(),
                    unused -> { Toast.makeText(this, "Reply deleted", Toast.LENGTH_SHORT).show(); loadComments(); },
                    e -> Toast.makeText(this, "Failed to delete reply", Toast.LENGTH_SHORT).show());
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private String formatMillis(long millis) {
        if (millis == 0) return "";
        return DateFormat.format("MMM d, h:mm a", new Date(millis)).toString();
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }

    // =========================================================================
    // Adapter
    // =========================================================================

    private class CommentDetailAdapter extends RecyclerView.Adapter<CommentDetailAdapter.VH> {

        private final List<Comment> items;

        CommentDetailAdapter(List<Comment> items) { this.items = items; }

        @Override public int getItemCount() { return items.size(); }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_comment_detail, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Comment comment = items.get(position);

            String author = comment.getUserName() != null ? comment.getUserName() : "?";
            String userId = comment.getUserId();

            // Avatar initials — show 1-2 letters
            holder.tvAvatar.setText(getInitials(author));
            holder.tvAuthor.setText(author);
            holder.tvTime.setText(formatMillis(comment.getCreatedAtMillis()));
            holder.tvText.setText(comment.getText() != null ? comment.getText() : "");

            // Reactions
            int reactions = comment.getTotalReactionCount();
            if (reactions > 0) {
                holder.tvReactions.setVisibility(View.VISIBLE);
                holder.tvReactions.setText("\uD83D\uDC4D " + reactions);
            } else {
                holder.tvReactions.setVisibility(View.GONE);
            }

            // Tappable avatar + name → profile popup
            View.OnClickListener profileClick = v -> showProfileDialog(userId, author);
            holder.tvAvatar.setOnClickListener(profileClick);
            holder.tvAuthor.setOnClickListener(profileClick);

            // Delete
            holder.btnDelete.setOnClickListener(v -> confirmDeleteComment(comment));

            // Replies
            List<Reply> replies = comment.getReplies();
            if (replies != null && !replies.isEmpty()) {
                holder.repliesDivider.setVisibility(View.VISIBLE);
                holder.repliesContainer.setVisibility(View.VISIBLE);
                holder.repliesContainer.removeAllViews();

                // Reply count label
                holder.tvReplyCount.setVisibility(View.VISIBLE);
                holder.tvReplyCount.setText(replies.size() + " repl" + (replies.size() == 1 ? "y" : "ies"));

                for (Reply reply : replies) {
                    View rv = LayoutInflater.from(AdminCommentsActivity.this)
                            .inflate(R.layout.item_admin_reply_detail, holder.repliesContainer, false);

                    TextView tvRA   = rv.findViewById(R.id.tvReplyAvatar);
                    TextView tvRAu  = rv.findViewById(R.id.tvReplyAuthor);
                    TextView tvRT   = rv.findViewById(R.id.tvReplyTime);
                    TextView tvRTx  = rv.findViewById(R.id.tvReplyText);
                    ImageView btnDR = rv.findViewById(R.id.btnDeleteReply);

                    String rAuthor = reply.getUserName() != null ? reply.getUserName() : "?";
                    String rUserId = reply.getUserId();

                    tvRA.setText(getInitials(rAuthor));
                    tvRAu.setText(rAuthor);
                    tvRT.setText(formatMillis(reply.getCreatedAtMillis()));
                    tvRTx.setText(reply.getText() != null ? reply.getText() : "");

                    // Tappable avatar + name for reply author
                    View.OnClickListener rProfileClick = v -> showProfileDialog(rUserId, rAuthor);
                    tvRA.setOnClickListener(rProfileClick);
                    tvRAu.setOnClickListener(rProfileClick);

                    btnDR.setOnClickListener(v -> confirmDeleteReply(comment, reply));
                    holder.repliesContainer.addView(rv);
                }
            } else {
                holder.repliesDivider.setVisibility(View.GONE);
                holder.repliesContainer.setVisibility(View.GONE);
                holder.tvReplyCount.setVisibility(View.GONE);
            }
        }

        class VH extends RecyclerView.ViewHolder {
            TextView  tvAvatar, tvAuthor, tvTime, tvText, tvReactions, tvReplyCount;
            ImageView btnDelete;
            View      repliesDivider;
            LinearLayout repliesContainer;

            VH(View v) {
                super(v);
                tvAvatar         = v.findViewById(R.id.tvCommentAvatar);
                tvAuthor         = v.findViewById(R.id.tvCommentAuthor);
                tvTime           = v.findViewById(R.id.tvCommentTime);
                tvText           = v.findViewById(R.id.tvCommentText);
                tvReactions      = v.findViewById(R.id.tvCommentReactions);
                tvReplyCount     = v.findViewById(R.id.tvReplyCount);
                btnDelete        = v.findViewById(R.id.btnDeleteComment);
                repliesDivider   = v.findViewById(R.id.repliesDivider);
                repliesContainer = v.findViewById(R.id.repliesContainer);
            }
        }
    }
}