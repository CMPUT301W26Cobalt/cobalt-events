package com.example.cobaltevents.ui.comments;

import android.content.Context;
import android.graphics.drawable.Drawable;
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
import com.example.cobaltevents.util.NetworkConnectivity;

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

    /**
     * For full-screen or tab UIs where comments should be visible without tapping “show more”
     * (e.g. Manage event → Comments tab).
     */
    public static void setCommentsExpandedForEvent(String eventId, boolean expanded) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        if (expanded) {
            EXPANDED_COMMENTS_EVENTS.add(eventId);
        } else {
            EXPANDED_COMMENTS_EVENTS.remove(eventId);
        }
    }

    /** Ensures the comment list is visible after the user sends a comment or reply. */
    private static void expandCommentsForEvent(String eventId) {
        if (eventId != null && !eventId.isEmpty()) {
            EXPANDED_COMMENTS_EVENTS.add(eventId);
        }
    }

    public static void bind(View root, Event event, String deviceId, String userName, Runnable onCommentsChanged) {
        bindInternal(root, event, deviceId, userName, onCommentsChanged, false);
    }

    public static void bindManage(View root, Event event, String deviceId, String userName, Runnable onCommentsChanged) {
        bindInternal(root, event, deviceId, userName, onCommentsChanged, true);
    }

    private static void bindInternal(View root, Event event, String deviceId, String userName,
                                     Runnable onCommentsChanged, boolean manageStyle) {
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

        if (list == null || header == null || etComment == null || btnSend == null) {
            return;
        }

        String displayName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "You";
        updateComposerHint(ctx, eid, etComment);

        Runnable rebindSelf = () -> bindInternal(root, event, deviceId, userName, onCommentsChanged, manageStyle);
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

                    renderCommentsList(ctx, list, toggle, comments, eid, deviceId, displayName, commentDB,
                            rebindSelf, etComment, composerRow, root, manageStyle);

                    btnSend.setOnClickListener(v -> {
                        String text = etComment.getText() != null ? etComment.getText().toString().trim() : "";
                        if (text.isEmpty()) {
                            return;
                        }
                        if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
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
                                        if (isCommentDeletedError(err)) {
                                            Toast.makeText(ctx, R.string.comments_comment_deleted, Toast.LENGTH_SHORT).show();
                                            clearReplyTarget(eid);
                                            rebindSelf.run();
                                        } else {
                                            Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } else {
                            commentDB.addComment(eid, deviceId != null ? deviceId : "", displayName, text,
                                    id -> {
                                        btnSend.setEnabled(true);
                                        etComment.setText("");
                                        expandCommentsForEvent(eid);
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
                                           View composerRow,
                                           View root,
                                           boolean manageStyle) {
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View emptyCard = root.findViewById(R.id.layout_comments_empty);

        boolean expanded = EXPANDED_COMMENTS_EVENTS.contains(eid);
        int visibleCount = expanded ? comments.size() : 0;
        list.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (composerRow != null) {
            composerRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (emptyCard != null) {
            emptyCard.setVisibility(expanded && comments.isEmpty() ? View.VISIBLE : View.GONE);
        }

        for (int i = 0; i < visibleCount; i++) {
            Comment c = comments.get(i);
            int rowLayout = manageStyle ? R.layout.item_event_comment_manage : R.layout.item_event_comment;
            View row = inflater.inflate(rowLayout, list, false);
            bindCommentRow(ctx, inflater, row, eid, c, deviceId, displayName, commentDB, rebindSelf, composer);
            if (manageStyle && i == visibleCount - 1) {
                View divider = row.findViewById(R.id.view_comment_divider);
                if (divider != null) {
                    divider.setVisibility(View.GONE);
                }
            }
            list.addView(row);
        }

        if (toggle != null) {
            if (manageStyle) {
                toggle.setVisibility(View.GONE);
                toggle.setOnClickListener(null);
            } else {
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
        View btnDelete = row.findViewById(R.id.btn_comment_delete);
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
            if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
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

        if (btnDelete != null) {
            boolean canDelete = deviceId != null
                    && !deviceId.trim().isEmpty()
                    && c.getUserId() != null
                    && deviceId.equals(c.getUserId());
            btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> {
                if (!canDelete) {
                    return;
                }
                if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
                    Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                    return;
                }
                btnDelete.setEnabled(false);
                commentDB.deleteCommentWithReplies(eid, c.getId(),
                        unused -> {
                            btnDelete.setEnabled(true);
                            rebindSelf.run();
                        },
                        err -> {
                            btnDelete.setEnabled(true);
                            Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
                        });
            });
        }

        repliesLayout.removeAllViews();
        for (Reply r : c.getReplies()) {
            int replyLayout = row.findViewById(R.id.layout_comment_main_content) != null
                    ? R.layout.item_event_reply_manage
                    : R.layout.item_event_reply;
            View rView = inflater.inflate(replyLayout, repliesLayout, false);
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
                if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
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

    private static boolean isCommentDeletedError(Throwable err) {
        for (Throwable t = err; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (CommentDB.ERR_COMMENT_DELETED.equals(message)) {
                return true;
            }
        }
        return false;
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
