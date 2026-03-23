package com.example.cobaltevents.ui.comments;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.CommentDB;
import com.example.cobaltevents.model.Comment;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Reply;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Binds the event-card comments section (expanded area). Data from Firestore via {@link CommentDB}.
 */
public final class EventCommentsUiBinder {

    private static final Set<String> EXPANDED_COMMENTS_EVENTS = new HashSet<>();
    private static final Map<String, String> REPLY_TARGET_BY_EVENT = new HashMap<>();
    private static final Map<String, String> REPLY_TARGET_NAME_BY_EVENT = new HashMap<>();

    private EventCommentsUiBinder() {}

    public static void bind(View root, Event event, String deviceId, String userName, Runnable onCommentsChanged) {
        if (root == null || event == null || event.getEventId() == null) {
            return;
        }
        final String eid = event.getEventId();
        Context ctx = root.getContext();

        LinearLayout list = root.findViewById(R.id.layout_comments_list);
        View composerRow = root.findViewById(R.id.layout_comments_composer);
        TextView header = root.findViewById(R.id.tv_comments_header);
        TextView toggle = root.findViewById(R.id.tv_comments_toggle);
        EditText etComment = root.findViewById(R.id.et_comment_input);
        View btnSend = root.findViewById(R.id.btn_send_comment);
        TextView inputAvatar = root.findViewById(R.id.tv_comment_input_avatar);

        if (list == null || header == null || etComment == null || btnSend == null) {
            return;
        }

        String displayName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "You";
        inputAvatar.setText(String.valueOf(Character.toUpperCase(displayName.charAt(0))));
        updateComposerHint(ctx, eid, etComment);

        Runnable rebindSelf = () -> bind(root, event, deviceId, userName, onCommentsChanged);
        boolean hasExistingRowsForSameEvent = eid.equals(list.getTag()) && list.getChildCount() > 0;
        list.setTag(eid);
        if (!hasExistingRowsForSameEvent) {
            header.setText(ctx.getString(R.string.comments_loading));
            list.removeAllViews();
        }
        boolean expanded = EXPANDED_COMMENTS_EVENTS.contains(eid);
        list.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (composerRow != null) {
            composerRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        btnSend.setEnabled(false);

        CommentDB commentDB = new CommentDB();
        commentDB.loadCommentsForEvent(eid, deviceId,
                comments -> {
                    if (!eid.equals(list.getTag())) {
                        return;
                    }
                    btnSend.setEnabled(true);
                    header.setText(ctx.getString(R.string.comments_count_format, comments.size()));

                    renderCommentsList(ctx, list, toggle, comments, eid, deviceId, displayName, commentDB, rebindSelf, etComment, composerRow);

                    btnSend.setOnClickListener(v -> {
                        String text = etComment.getText() != null ? etComment.getText().toString().trim() : "";
                        if (text.isEmpty()) {
                            return;
                        }
                        if (!isNetworkAvailable(ctx)) {
                            Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        btnSend.setEnabled(false);

                        String replyCommentId = REPLY_TARGET_BY_EVENT.get(eid);
                        if (replyCommentId != null && !replyCommentId.isEmpty()) {
                            commentDB.addReply(eid, replyCommentId, deviceId != null ? deviceId : "", displayName, text,
                                    id -> {
                                        btnSend.setEnabled(true);
                                        etComment.setText("");
                                        clearReplyTarget(eid);
                                        rebindSelf.run();
                                        if (onCommentsChanged != null) onCommentsChanged.run();
                                    },
                                    err -> {
                                        btnSend.setEnabled(true);
                                        Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            commentDB.addComment(eid, deviceId != null ? deviceId : "", displayName, text,
                                    id -> {
                                        btnSend.setEnabled(true);
                                        etComment.setText("");
                                        rebindSelf.run();
                                        if (onCommentsChanged != null) onCommentsChanged.run();
                                    },
                                    err -> {
                                        btnSend.setEnabled(true);
                                        Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
                                    });
                        }
                    });
                },
                err -> {
                    if (!eid.equals(list.getTag())) {
                        return;
                    }
                    btnSend.setEnabled(true);
                    header.setText(ctx.getString(R.string.comments_count_format, 0));
                    Toast.makeText(ctx, R.string.comments_load_failed, Toast.LENGTH_SHORT).show();
                });

        View section = root.findViewById(R.id.layout_event_comments_section);
        if (section != null) {
            section.setOnClickListener(v -> { /* block propagation */ });
        }
    }

    private static void renderCommentsList(Context ctx,
                                           LinearLayout list,
                                           TextView toggle,
                                           List<Comment> comments,
                                           String eid,
                                           String deviceId,
                                           String displayName,
                                           CommentDB commentDB,
                                           Runnable rebindSelf,
                                           EditText composer,
                                           View composerRow) {
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(ctx);

        boolean expanded = EXPANDED_COMMENTS_EVENTS.contains(eid);
        int visibleCount = expanded ? comments.size() : 0;
        list.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (composerRow != null) {
            composerRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }

        for (int i = 0; i < visibleCount; i++) {
            Comment c = comments.get(i);
            View row = inflater.inflate(R.layout.item_event_comment, list, false);
            bindCommentRow(ctx, inflater, row, eid, c, deviceId, displayName, commentDB, rebindSelf, composer);
            list.addView(row);
        }

        if (toggle != null) {
            toggle.setVisibility(View.VISIBLE);
            toggle.setText(expanded ? R.string.comments_show_less : R.string.comments_show_more);
            toggle.setOnClickListener(v -> {
                if (expanded) {
                    EXPANDED_COMMENTS_EVENTS.remove(eid);
                } else {
                    EXPANDED_COMMENTS_EVENTS.add(eid);
                }
                rebindSelf.run();
            });
        }
    }

    private static void bindCommentRow(Context ctx,
                                       LayoutInflater inflater,
                                       View row,
                                       String eid,
                                       Comment c,
                                       String deviceId,
                                       String displayName,
                                       CommentDB commentDB,
                                       Runnable rebindSelf,
                                       EditText composer) {
        TextView avatar = row.findViewById(R.id.tv_comment_avatar);
        TextView author = row.findViewById(R.id.tv_comment_author);
        TextView time = row.findViewById(R.id.tv_comment_time);
        TextView body = row.findViewById(R.id.tv_comment_body);
        TextView btnLike = row.findViewById(R.id.btn_comment_like);
        TextView btnReply = row.findViewById(R.id.btn_comment_reply);
        LinearLayout repliesLayout = row.findViewById(R.id.layout_replies);
        LinearLayout replyInput = row.findViewById(R.id.layout_reply_input);

        if (replyInput != null) {
            replyInput.setVisibility(View.GONE);
        }

        String name = c.getUserName() != null ? c.getUserName() : "";
        avatar.setText(initialLetter(name));
        author.setText(name.isEmpty() ? "User" : name);
        time.setText(formatTimeAgo(c.getCreatedAtMillis()));
        body.setText(c.getText() != null ? c.getText() : "");
        bindLikeButtonVisual(ctx, btnLike, c.getLikes(), c.isLikedByCurrentUser());

        btnLike.setOnClickListener(v -> {
            if (!isNetworkAvailable(ctx)) {
                Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }
            if (deviceId == null || deviceId.trim().isEmpty()) {
                Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            commentDB.toggleCommentLike(eid, c.getId(), deviceId,
                    nowLiked -> {
                        bindLikeButtonVisual(ctx, btnLike,
                                nowLiked ? c.getLikes() + 1 : Math.max(0, c.getLikes() - 1), nowLiked);
                        rebindSelf.run();
                    },
                    err -> Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show());
        });

        boolean isTarget = c.getId() != null && c.getId().equals(REPLY_TARGET_BY_EVENT.get(eid));
        btnReply.setText(isTarget ? R.string.cancel_reply : R.string.reply);
        btnReply.setOnClickListener(v -> {
            if (isTarget) {
                clearReplyTarget(eid);
            } else {
                REPLY_TARGET_BY_EVENT.put(eid, c.getId());
                REPLY_TARGET_NAME_BY_EVENT.put(eid, name.isEmpty() ? "User" : name);
            }
            updateComposerHint(ctx, eid, composer);
            rebindSelf.run();
        });

        repliesLayout.removeAllViews();
        for (Reply r : c.getReplies()) {
            View rView = inflater.inflate(R.layout.item_event_reply, repliesLayout, false);
            TextView ra = rView.findViewById(R.id.tv_reply_avatar);
            TextView rauth = rView.findViewById(R.id.tv_reply_author);
            TextView rt = rView.findViewById(R.id.tv_reply_time);
            TextView rb = rView.findViewById(R.id.tv_reply_body);
            TextView rLike = rView.findViewById(R.id.btn_reply_like);
            String rn = r.getUserName() != null ? r.getUserName() : "";
            ra.setText(initialLetter(rn));
            rauth.setText(rn.isEmpty() ? "User" : rn);
            rt.setText(formatTimeAgo(r.getCreatedAtMillis()));
            rb.setText(r.getText() != null ? r.getText() : "");
            bindLikeButtonVisual(ctx, rLike, r.getLikes(), r.isLikedByCurrentUser());
            rLike.setOnClickListener(v -> {
                if (!isNetworkAvailable(ctx)) {
                    Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (deviceId == null || deviceId.trim().isEmpty()) {
                    Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                commentDB.toggleReplyLike(eid, c.getId(), r.getId(), deviceId,
                        nowLiked -> {
                            bindLikeButtonVisual(ctx, rLike,
                                    nowLiked ? r.getLikes() + 1 : Math.max(0, r.getLikes() - 1), nowLiked);
                            rebindSelf.run();
                        },
                        err -> Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show());
            });
            repliesLayout.addView(rView);
        }
    }

    private static void clearReplyTarget(String eventId) {
        REPLY_TARGET_BY_EVENT.remove(eventId);
        REPLY_TARGET_NAME_BY_EVENT.remove(eventId);
    }

    private static void updateComposerHint(Context ctx, String eventId, EditText composer) {
        if (composer == null) {
            return;
        }
        String targetName = REPLY_TARGET_NAME_BY_EVENT.get(eventId);
        if (targetName != null && !targetName.trim().isEmpty()) {
            composer.setHint(ctx.getString(R.string.replying_to_hint, targetName));
        } else {
            composer.setHint(R.string.comment_input_hint);
        }
    }

    private static String initialLetter(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return String.valueOf(Character.toUpperCase(name.charAt(0)));
    }

    private static void bindLikeButtonVisual(Context ctx, TextView view, int likes, boolean isLiked) {
        view.setText(ctx.getString(R.string.comment_like_count, Math.max(0, likes)));
        view.setCompoundDrawablePadding(dpToPx(ctx, 4));
        int color = ContextCompat.getColor(ctx, isLiked ? R.color.header_teal : R.color.grey_medium);
        Drawable icon = ContextCompat.getDrawable(ctx,
                isLiked ? R.drawable.ic_thumb_up_filled : R.drawable.ic_thumb_up_outline);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon, color);
        }
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        view.setTextColor(color);
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static String formatTimeAgo(long millis) {
        long now = System.currentTimeMillis();
        long sec = Math.max(0, (now - millis) / 1000);
        if (sec < 60) {
            return "just now";
        }
        if (sec < 3600) {
            return (sec / 60) + "m ago";
        }
        if (sec < 86400) {
            return (sec / 3600) + "h ago";
        }
        return (sec / 86400) + "d ago";
    }
}
