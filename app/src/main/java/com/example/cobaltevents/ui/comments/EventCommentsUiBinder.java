package com.example.cobaltevents.ui.comments;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.CommentDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.model.Comment;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Reply;
import com.example.cobaltevents.util.EventGoneUi;
import com.example.cobaltevents.util.NetworkConnectivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Binds the event-card comments section (expanded area). Data from Firestore via {@link CommentDB}.
 *
 * Features:
 *  - Emoji reactions (Discord-style) replacing the old like button
 *  - Threaded replies with collapsible thread view and visual thread line
 */
public final class EventCommentsUiBinder {

    // ── Emoji palette ────────────────────────────────────────────────────────
    static final String[] REACTION_EMOJIS = {
            "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👀", "✅", "👎"
    };

    // ── Persistent UI state ───────────────────────────────────────────────────
    private static final Set<String>              EXPANDED_COMMENTS_EVENTS = new HashSet<>();
    private static final Map<String, String>      REPLY_TARGET_BY_EVENT    = new HashMap<>();
    private static final Map<String, String>      REPLY_TARGET_NAME_BY_EVENT = new HashMap<>();
    /** eventId → set of commentIds whose reply threads are currently expanded. */
    private static final Map<String, Set<String>> EXPANDED_THREADS         = new HashMap<>();

    private EventCommentsUiBinder() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static void setCommentsExpandedForEvent(String eventId, boolean expanded) {
        if (eventId == null || eventId.isEmpty()) return;
        if (expanded) EXPANDED_COMMENTS_EVENTS.add(eventId);
        else          EXPANDED_COMMENTS_EVENTS.remove(eventId);
    }

    /**
     * @param serverAllowsCommentWrite after a fresh server fetch, returns true if the user may comment
     *                                 (e.g. public event, on waitlist, or organizer). When the event is
     *                                 private and this returns false, shows the same string as for join
     *                                 ({@code R.string.event_switched_to_private})
     *                                 and runs {@code onPrivateCommentDeniedRefresh} with the server event.
     */
    public static void bind(View root, Event event, String deviceId, String userName,
                            Runnable onCommentsChanged, Runnable onEventDeleted,
                            Predicate<Event> serverAllowsCommentWrite,
                            Consumer<Event> onPrivateCommentDeniedRefresh) {
        bindInternal(root, event, deviceId, userName, onCommentsChanged, onEventDeleted, false,
                serverAllowsCommentWrite, onPrivateCommentDeniedRefresh);
    }

    public static void bindManage(View root, Event event, String deviceId, String userName,
                                  Runnable onCommentsChanged, Runnable onEventDeleted) {
        bindInternal(root, event, deviceId, userName, onCommentsChanged, onEventDeleted, true,
                null, null);
    }

    // ── Core binding ──────────────────────────────────────────────────────────

    private static void bindInternal(View root, Event event, String deviceId, String userName,
                                     Runnable onCommentsChanged, Runnable onEventDeleted,
                                     boolean manageStyle,
                                     Predicate<Event> serverAllowsCommentWrite,
                                     Consumer<Event> onPrivateCommentDeniedRefresh) {
        if (root == null || event == null || event.getEventId() == null) return;
        final String eid = event.getEventId();
        Context ctx = root.getContext();

        LinearLayout list        = root.findViewById(R.id.layout_comments_list);
        View composerRow         = root.findViewById(R.id.layout_comments_composer);
        TextView header          = root.findViewById(R.id.tv_comments_header);
        TextView toggle          = root.findViewById(R.id.tv_comments_toggle);
        EditText etComment       = root.findViewById(R.id.et_comment_input);
        View btnSend             = root.findViewById(R.id.btn_send_comment);

        if (list == null || header == null || etComment == null || btnSend == null) return;

        String displayName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "You";
        updateComposerHint(ctx, eid, etComment);

        EventDB eventDB = new EventDB();
        final Predicate<Event> accessPred = manageStyle ? null : serverAllowsCommentWrite;
        final Consumer<Event> accessDeny = manageStyle ? null : onPrivateCommentDeniedRefresh;
        Runnable rebindSelf = () -> bindInternal(root, event, deviceId, userName,
                onCommentsChanged, onEventDeleted, manageStyle,
                serverAllowsCommentWrite, onPrivateCommentDeniedRefresh);
        boolean hasExistingRows = eid.equals(list.getTag()) && list.getChildCount() > 0;
        list.setTag(eid);
        if (!hasExistingRows) {
            header.setText(ctx.getString(R.string.comments_loading));
            list.removeAllViews();
        }
        boolean expanded = EXPANDED_COMMENTS_EVENTS.contains(eid);
        list.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (composerRow != null) composerRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(false);

        CommentDB commentDB = new CommentDB();
        commentDB.loadCommentsForEvent(eid, deviceId,
                comments -> {
                    if (!eid.equals(list.getTag())) return;
                    btnSend.setEnabled(true);
                    header.setText(ctx.getString(R.string.comments_count_format, comments.size()));

                    renderCommentsList(ctx, list, toggle, comments, eid, deviceId, displayName,
                            commentDB, rebindSelf, etComment, composerRow, root, manageStyle,
                            eventDB, onEventDeleted, accessPred, accessDeny);

                    btnSend.setOnClickListener(v -> {
                        String text = etComment.getText() != null
                                ? etComment.getText().toString().trim() : "";
                        if (text.isEmpty()) return;
                        if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
                            Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        verifyServerEventAccessThen(ctx, eid, eventDB, onEventDeleted, accessPred,
                                accessDeny, () -> {
                            btnSend.setEnabled(false);
                            String replyCommentId = REPLY_TARGET_BY_EVENT.get(eid);
                            if (replyCommentId != null && !replyCommentId.isEmpty()) {
                                commentDB.addReply(eid, replyCommentId,
                                        deviceId != null ? deviceId : "", displayName, text,
                                        id -> {
                                            btnSend.setEnabled(true);
                                            etComment.setText("");
                                            expandThread(eid, replyCommentId);
                                            clearReplyTarget(eid);
                                            rebindSelf.run();
                                            if (onCommentsChanged != null) onCommentsChanged.run();
                                        },
                                        err -> {
                                            btnSend.setEnabled(true);
                                            if (isCommentDeletedError(err)) {
                                                Toast.makeText(ctx, R.string.comments_comment_deleted,
                                                        Toast.LENGTH_SHORT).show();
                                                clearReplyTarget(eid);
                                                rebindSelf.run();
                                            } else if (handleEventWriteFailure(ctx, err, onEventDeleted)) {
                                                // toast + callback done in helper
                                            } else {
                                                Toast.makeText(ctx, R.string.comments_action_failed,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            } else {
                                commentDB.addComment(eid, deviceId != null ? deviceId : "",
                                        displayName, text,
                                        id -> {
                                            btnSend.setEnabled(true);
                                            etComment.setText("");
                                            EXPANDED_COMMENTS_EVENTS.add(eid);
                                            rebindSelf.run();
                                            if (onCommentsChanged != null) onCommentsChanged.run();
                                        },
                                        err -> {
                                            btnSend.setEnabled(true);
                                            if (!handleEventWriteFailure(ctx, err, onEventDeleted)) {
                                                Toast.makeText(ctx, R.string.comments_action_failed,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        });
                    });
                },
                err -> {
                    if (!eid.equals(list.getTag())) return;
                    btnSend.setEnabled(true);
                    header.setText(ctx.getString(R.string.comments_count_format, 0));
                    if (EventGoneUi.isFirestoreNotFound(err)) {
                        EventGoneUi.toast(ctx);
                        if (onEventDeleted != null) {
                            onEventDeleted.run();
                        }
                    } else {
                        Toast.makeText(ctx, R.string.comments_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });

        View section = root.findViewById(R.id.layout_event_comments_section);
        if (section != null) section.setOnClickListener(v -> { /* block propagation */ });
    }

    // ── Render list ───────────────────────────────────────────────────────────

    /**
     * Fetches the event from the server; runs {@code ifAllowed} only when the doc exists and either
     * {@code allowIfPrivateScoped} is null (manage / no gate) or it tests true for the fresh event.
     */
    private static void verifyServerEventAccessThen(Context ctx, String eventId, EventDB eventDB,
                                                    Runnable onEventDeleted,
                                                    Predicate<Event> allowIfPrivateScoped,
                                                    Consumer<Event> onPrivateDeniedRefresh,
                                                    Runnable ifAllowed) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        eventDB.getEventFromServer(eventId, fresh -> EventGoneUi.runOnUi(ctx, () -> {
            if (fresh == null) {
                EventGoneUi.toast(ctx);
                if (onEventDeleted != null) {
                    onEventDeleted.run();
                }
            } else if (allowIfPrivateScoped != null && !allowIfPrivateScoped.test(fresh)) {
                Toast.makeText(ctx, R.string.event_switched_to_private, Toast.LENGTH_LONG).show();
                if (onPrivateDeniedRefresh != null) {
                    onPrivateDeniedRefresh.accept(fresh);
                }
            } else if (ifAllowed != null) {
                ifAllowed.run();
            }
        }), e -> EventGoneUi.runOnUi(ctx, () ->
                Toast.makeText(ctx, R.string.comments_action_failed, Toast.LENGTH_SHORT).show()));
    }

    private static boolean handleEventWriteFailure(Context ctx, Throwable err, Runnable onEventDeleted) {
        if (EventGoneUi.isFirestoreNotFound(err) || EventGoneUi.isEventDeletedReason(err)) {
            EventGoneUi.toast(ctx);
            if (onEventDeleted != null) {
                onEventDeleted.run();
            }
            return true;
        }
        return false;
    }

    private static void renderCommentsList(Context ctx, LinearLayout list, TextView toggle,
                                           List<Comment> comments, String eid, String deviceId,
                                           String displayName, CommentDB commentDB,
                                           Runnable rebindSelf, EditText composer,
                                           View composerRow, View root, boolean manageStyle,
                                           EventDB eventDB, Runnable onEventDeleted,
                                           Predicate<Event> accessPred,
                                           Consumer<Event> accessDeny) {
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View emptyCard = root.findViewById(R.id.layout_comments_empty);

        boolean expanded = EXPANDED_COMMENTS_EVENTS.contains(eid);
        list.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (composerRow != null) composerRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (emptyCard != null) {
            emptyCard.setVisibility(expanded && comments.isEmpty() ? View.VISIBLE : View.GONE);
        }

        int visibleCount = expanded ? comments.size() : 0;
        for (int i = 0; i < visibleCount; i++) {
            Comment c = comments.get(i);
            int rowLayout = manageStyle
                    ? R.layout.item_event_comment_manage
                    : R.layout.item_event_comment;
            View row = inflater.inflate(rowLayout, list, false);
            bindCommentRow(ctx, inflater, row, eid, c, deviceId, displayName,
                    commentDB, rebindSelf, composer, manageStyle, eventDB, onEventDeleted,
                    accessPred, accessDeny);
            if (manageStyle && i == visibleCount - 1) {
                View divider = row.findViewById(R.id.view_comment_divider);
                if (divider != null) divider.setVisibility(View.GONE);
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
                    if (expanded) EXPANDED_COMMENTS_EVENTS.remove(eid);
                    else          EXPANDED_COMMENTS_EVENTS.add(eid);
                    rebindSelf.run();
                });
            }
        }
    }

    // ── Bind individual comment row ───────────────────────────────────────────

    private static void bindCommentRow(Context ctx, LayoutInflater inflater, View row,
                                       String eid, Comment c, String deviceId,
                                       String displayName, CommentDB commentDB,
                                       Runnable rebindSelf, EditText composer,
                                       boolean manageStyle, EventDB eventDB,
                                       Runnable onEventDeleted,
                                       Predicate<Event> accessPred,
                                       Consumer<Event> accessDeny) {
        TextView avatar     = row.findViewById(R.id.tv_comment_avatar);
        TextView author     = row.findViewById(R.id.tv_comment_author);
        TextView time       = row.findViewById(R.id.tv_comment_time);
        TextView body       = row.findViewById(R.id.tv_comment_body);
        TextView btnReply   = row.findViewById(R.id.btn_comment_reply);
        View btnDelete      = row.findViewById(R.id.btn_comment_delete);
        LinearLayout reactionsLayout = row.findViewById(R.id.layout_reactions);
        TextView btnAddReaction      = row.findViewById(R.id.btn_add_reaction);
        TextView threadToggle        = row.findViewById(R.id.btn_thread_toggle);
        View threadContainer         = row.findViewById(R.id.layout_replies_thread);
        LinearLayout repliesLayout   = row.findViewById(R.id.layout_replies);
        LinearLayout replyInput      = row.findViewById(R.id.layout_reply_input);

        if (replyInput != null) replyInput.setVisibility(View.GONE);

        String name = c.getUserName() != null ? c.getUserName() : "";
        avatar.setText(initialLetter(name));
        author.setText(name.isEmpty() ? "User" : name);
        time.setText(formatTimeAgo(c.getCreatedAtMillis()));
        body.setText(c.getText() != null ? c.getText() : "");

        // ── Emoji reactions ──────────────────────────────────────────────────
        if (reactionsLayout != null) {
            bindReactions(ctx, reactionsLayout, btnAddReaction, c.getReactions(), deviceId,
                    emoji -> {
                        if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
                            Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        verifyServerEventAccessThen(ctx, eid, eventDB, onEventDeleted, accessPred,
                                accessDeny, () ->
                                commentDB.toggleCommentReaction(eid, c.getId(), deviceId, emoji,
                                        updatedReactions -> {
                                            c.setReactions(updatedReactions);
                                            bindReactions(ctx, reactionsLayout, btnAddReaction,
                                                    updatedReactions, deviceId,
                                                    e2 -> verifyServerEventAccessThen(ctx, eid, eventDB,
                                                            onEventDeleted, accessPred, accessDeny, () ->
                                                                    commentDB.toggleCommentReaction(
                                                                            eid, c.getId(),
                                                                            deviceId, e2,
                                                                            r2 -> {
                                                                                c.setReactions(r2);
                                                                                rebindSelf.run();
                                                                            },
                                                                            err -> {
                                                                                if (!handleEventWriteFailure(
                                                                                        ctx, err,
                                                                                        onEventDeleted)) {
                                                                                    Toast.makeText(ctx,
                                                                                            R.string.comments_action_failed,
                                                                                            Toast.LENGTH_SHORT).show();
                                                                                }
                                                                            })));
                                        },
                                        err -> {
                                            if (!handleEventWriteFailure(ctx, err, onEventDeleted)) {
                                                Toast.makeText(ctx, R.string.comments_action_failed,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        }));
                    });
        }

        // ── Reply button ─────────────────────────────────────────────────────
        boolean isTarget = c.getId() != null && c.getId().equals(REPLY_TARGET_BY_EVENT.get(eid));
        btnReply.setText(isTarget ? R.string.cancel_reply : R.string.reply);
        btnReply.setOnClickListener(v -> {
            if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
                Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }
            verifyServerEventAccessThen(ctx, eid, eventDB, onEventDeleted, accessPred, accessDeny,
                    () -> {
                        if (isTarget) {
                            clearReplyTarget(eid);
                        } else {
                            REPLY_TARGET_BY_EVENT.put(eid, c.getId());
                            REPLY_TARGET_NAME_BY_EVENT.put(eid, name.isEmpty() ? "User" : name);
                        }
                        updateComposerHint(ctx, eid, composer);
                        rebindSelf.run();
                    });
        });

        // ── Delete button ─────────────────────────────────────────────────────
        if (btnDelete != null) {
            boolean canDelete = deviceId != null && !deviceId.trim().isEmpty()
                    && c.getUserId() != null && deviceId.equals(c.getUserId());
            btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> {
                if (!canDelete) return;
                if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
                    Toast.makeText(ctx, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
                    return;
                }
                btnDelete.setEnabled(false);
                verifyServerEventAccessThen(ctx, eid, eventDB, onEventDeleted, accessPred, accessDeny,
                        () ->
                        commentDB.deleteCommentWithReplies(eid, c.getId(),
                                unused -> {
                                    btnDelete.setEnabled(true);
                                    rebindSelf.run();
                                },
                                err -> {
                                    btnDelete.setEnabled(true);
                                    if (!handleEventWriteFailure(ctx, err, onEventDeleted)) {
                                        Toast.makeText(ctx, R.string.comments_action_failed,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }));
            });
        }

        // ── Thread: replies with collapsible toggle ───────────────────────────
        List<Reply> replies = c.getReplies();
        if (threadToggle != null && threadContainer != null && repliesLayout != null) {
            if (replies.isEmpty()) {
                threadToggle.setVisibility(View.GONE);
                threadContainer.setVisibility(View.GONE);
            } else {
                threadToggle.setVisibility(View.VISIBLE);
                boolean threadExpanded = isThreadExpanded(eid, c.getId());
                int count = replies.size();
                threadToggle.setText(threadExpanded
                        ? "▼  Hide " + count + (count == 1 ? " reply" : " replies")
                        : "▶  " + count + (count == 1 ? " reply" : " replies"));
                threadContainer.setVisibility(threadExpanded ? View.VISIBLE : View.GONE);

                threadToggle.setOnClickListener(v -> {
                    toggleThread(eid, c.getId());
                    rebindSelf.run();
                });

                // Populate replies inside the thread container
                repliesLayout.removeAllViews();
                if (threadExpanded) {
                    for (Reply r : replies) {
                        int replyLayout = manageStyle
                                ? R.layout.item_event_reply_manage
                                : R.layout.item_event_reply;
                        View rView = inflater.inflate(replyLayout, repliesLayout, false);
                        bindReplyRow(ctx, inflater, rView, eid, c.getId(), r,
                                deviceId, commentDB, rebindSelf, manageStyle, eventDB, onEventDeleted,
                                accessPred, accessDeny);
                        repliesLayout.addView(rView);
                    }
                }
            }
        }
    }

    // ── Bind reply row ────────────────────────────────────────────────────────

    private static void bindReplyRow(Context ctx, LayoutInflater inflater, View rView,
                                     String eid, String commentId, Reply r,
                                     String deviceId, CommentDB commentDB,
                                     Runnable rebindSelf, boolean manageStyle,
                                     EventDB eventDB, Runnable onEventDeleted,
                                     Predicate<Event> accessPred,
                                     Consumer<Event> accessDeny) {
        TextView ra      = rView.findViewById(R.id.tv_reply_avatar);
        TextView rauth   = rView.findViewById(R.id.tv_reply_author);
        TextView rt      = rView.findViewById(R.id.tv_reply_time);
        TextView rb      = rView.findViewById(R.id.tv_reply_body);
        LinearLayout replyReactionsLayout = rView.findViewById(R.id.layout_reply_reactions);
        TextView btnAddReplyReaction      = rView.findViewById(R.id.btn_add_reply_reaction);

        String rn = r.getUserName() != null ? r.getUserName() : "";
        ra.setText(initialLetter(rn));
        rauth.setText(rn.isEmpty() ? "User" : rn);
        rt.setText(formatTimeAgo(r.getCreatedAtMillis()));
        rb.setText(r.getText() != null ? r.getText() : "");

        if (replyReactionsLayout != null) {
            bindReactions(ctx, replyReactionsLayout, btnAddReplyReaction,
                    r.getReactions(), deviceId,
                    emoji -> {
                        if (!NetworkConnectivity.hasValidatedInternet(ctx)) {
                            Toast.makeText(ctx, R.string.comments_no_internet,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        verifyServerEventAccessThen(ctx, eid, eventDB, onEventDeleted, accessPred,
                                accessDeny, () ->
                                commentDB.toggleReplyReaction(eid, commentId, r.getId(),
                                        deviceId, emoji,
                                        updatedReactions -> {
                                            r.setReactions(updatedReactions);
                                            bindReactions(ctx, replyReactionsLayout,
                                                    btnAddReplyReaction,
                                                    updatedReactions, deviceId,
                                                    e2 -> verifyServerEventAccessThen(ctx, eid, eventDB,
                                                            onEventDeleted, accessPred, accessDeny, () ->
                                                                    commentDB.toggleReplyReaction(
                                                                            eid, commentId,
                                                                            r.getId(), deviceId, e2,
                                                                            r2 -> {
                                                                                r.setReactions(r2);
                                                                                rebindSelf.run();
                                                                            },
                                                                            err -> {
                                                                                if (!handleEventWriteFailure(
                                                                                        ctx, err,
                                                                                        onEventDeleted)) {
                                                                                    Toast.makeText(ctx,
                                                                                            R.string.comments_action_failed,
                                                                                            Toast.LENGTH_SHORT).show();
                                                                                }
                                                                            })));
                                        },
                                        err -> {
                                            if (!handleEventWriteFailure(ctx, err, onEventDeleted)) {
                                                Toast.makeText(ctx, R.string.comments_action_failed,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        }));
                    });
        }
    }

    // ── Emoji reactions UI ────────────────────────────────────────────────────

    /** Renders reaction pills into {@code layout} and wires the "+" picker button. */
    private static void bindReactions(Context ctx,
                                      LinearLayout layout,
                                      TextView addBtn,
                                      Map<String, List<String>> reactions,
                                      String userId,
                                      EmojiCallback onToggle) {
        layout.removeAllViews();

        // Render one pill per emoji that has at least one reactor
        for (String emoji : REACTION_EMOJIS) {
            List<String> users = reactions.get(emoji);
            if (users == null || users.isEmpty()) continue;
            boolean active = userId != null && users.contains(userId);
            TextView pill = buildReactionPill(ctx, emoji, users.size(), active);
            pill.setOnClickListener(v -> onToggle.onEmoji(emoji));
            layout.addView(pill);
        }
        // Emojis not in the palette but present in the map (future-proofing)
        for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
            if (isPaletteEmoji(entry.getKey())) continue;
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            boolean active = userId != null && entry.getValue().contains(userId);
            TextView pill = buildReactionPill(ctx, entry.getKey(), entry.getValue().size(), active);
            pill.setOnClickListener(v -> onToggle.onEmoji(entry.getKey()));
            layout.addView(pill);
        }

        if (addBtn != null) {
            addBtn.setOnClickListener(v -> showEmojiPicker(ctx, addBtn, onToggle));
        }
    }

    private static TextView buildReactionPill(Context ctx, String emoji, int count, boolean active) {
        TextView pill = new TextView(ctx);
        pill.setText(emoji + " " + count);
        pill.setTextSize(12f);
        int hPad = dpToPx(ctx, 7);
        int vPad = dpToPx(ctx, 3);
        pill.setPadding(hPad, vPad, hPad, vPad);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(ctx, 10));

        int teal = ContextCompat.getColor(ctx, R.color.header_teal);
        if (active) {
            bg.setColor(Color.argb(40, Color.red(teal), Color.green(teal), Color.blue(teal)));
            bg.setStroke(dpToPx(ctx, 1), teal);
            pill.setTextColor(teal);
            pill.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(Color.argb(18, 0, 0, 0));
            bg.setStroke(dpToPx(ctx, 1), Color.argb(40, 0, 0, 0));
            pill.setTextColor(ContextCompat.getColor(ctx, R.color.grey_medium));
        }
        pill.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dpToPx(ctx, 4));
        pill.setLayoutParams(lp);
        return pill;
    }

    /** Shows a floating emoji picker anchored below {@code anchor}. */
    private static void showEmojiPicker(Context ctx, View anchor, EmojiCallback onPick) {
        // Build picker view programmatically (no extra layout file needed)
        HorizontalScrollView scroll = new HorizontalScrollView(ctx);
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dpToPx(ctx, 6);
        grid.setPadding(pad, pad, pad, pad);

        for (String emoji : REACTION_EMOJIS) {
            TextView tv = new TextView(ctx);
            tv.setText(emoji);
            tv.setTextSize(22f);
            tv.setGravity(Gravity.CENTER);
            int ep = dpToPx(ctx, 5);
            tv.setPadding(ep, ep, ep, ep);
            grid.addView(tv);
            // Capture reference for lambda
            final String e = emoji;
            tv.setOnClickListener(v -> {
                // dismiss happens after we get the PopupWindow reference; use tag trick
                Object tag = anchor.getTag(R.id.btn_add_reaction);
                if (tag instanceof PopupWindow) ((PopupWindow) tag).dismiss();
                onPick.onEmoji(e);
            });
        }

        scroll.addView(grid);

        GradientDrawable popupBg = new GradientDrawable();
        popupBg.setShape(GradientDrawable.RECTANGLE);
        popupBg.setCornerRadius(dpToPx(ctx, 12));
        popupBg.setColor(ContextCompat.getColor(ctx, R.color.white));
        popupBg.setStroke(dpToPx(ctx, 1), Color.argb(30, 0, 0, 0));

        PopupWindow popup = new PopupWindow(scroll,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(popupBg);
        popup.setElevation(12f);

        // Store popup in anchor tag so emoji click handlers can dismiss it
        anchor.setTag(R.id.btn_add_reaction, popup);

        // Measure before showing so dimensions are known
        scroll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.showAsDropDown(anchor, 0, -anchor.getHeight() - dpToPx(ctx, 4));
    }

    private interface EmojiCallback {
        void onEmoji(String emoji);
    }

    private static boolean isPaletteEmoji(String emoji) {
        for (String e : REACTION_EMOJIS) {
            if (e.equals(emoji)) return true;
        }
        return false;
    }

    // ── Thread expand/collapse state ─────────────────────────────────────────

    private static boolean isThreadExpanded(String eid, String commentId) {
        Set<String> expanded = EXPANDED_THREADS.get(eid);
        return expanded != null && expanded.contains(commentId);
    }

    private static void expandThread(String eid, String commentId) {
        EXPANDED_THREADS.computeIfAbsent(eid, k -> new HashSet<>()).add(commentId);
    }

    private static void toggleThread(String eid, String commentId) {
        Set<String> expanded = EXPANDED_THREADS.computeIfAbsent(eid, k -> new HashSet<>());
        if (!expanded.add(commentId)) expanded.remove(commentId);
    }

    // ── Misc helpers ─────────────────────────────────────────────────────────

    private static void clearReplyTarget(String eventId) {
        REPLY_TARGET_BY_EVENT.remove(eventId);
        REPLY_TARGET_NAME_BY_EVENT.remove(eventId);
    }

    private static void updateComposerHint(Context ctx, String eventId, EditText composer) {
        if (composer == null) return;
        String targetName = REPLY_TARGET_NAME_BY_EVENT.get(eventId);
        if (targetName != null && !targetName.trim().isEmpty()) {
            composer.setHint(ctx.getString(R.string.replying_to_hint, targetName));
        } else {
            composer.setHint(R.string.comment_input_hint);
        }
    }

    private static String initialLetter(String name) {
        if (name == null || name.isEmpty()) return "?";
        return String.valueOf(Character.toUpperCase(name.charAt(0)));
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static boolean isCommentDeletedError(Throwable err) {
        for (Throwable t = err; t != null; t = t.getCause()) {
            if (CommentDB.ERR_COMMENT_DELETED.equals(t.getMessage())) return true;
        }
        return false;
    }

    private static String formatTimeAgo(long millis) {
        long sec = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (sec < 60)    return "just now";
        if (sec < 3600)  return (sec / 60) + "m ago";
        if (sec < 86400) return (sec / 3600) + "h ago";
        return (sec / 86400) + "d ago";
    }
}
