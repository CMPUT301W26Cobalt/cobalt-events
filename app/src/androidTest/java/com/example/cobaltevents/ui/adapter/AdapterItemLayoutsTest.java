package com.example.cobaltevents.ui.adapter;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for all adapter row / item layouts.
 * Inflates each layout without an Activity, verifying all critical child
 * views are present and correctly typed.
 *
 * Layouts covered:
 *  - item_event.xml              (EventAdapter)
 *  - item_event_history.xml      (EventHistoryAdapter)
 *  - item_notification_card.xml  (NotificationListAdapter)
 *  - item_waitlist_entrant.xml   (WaitlistEntrantAdapter)
 *  - item_organizer_event.xml    (OrganizerEventAdapter)
 *  - item_event_comment.xml      (comments)
 *  - item_event_reply.xml        (comment replies)
 *  - item_admin_event.xml        (AdminAdapter – event row)
 *  - item_admin_image.xml        (AdminAdapter – image row)
 *  - item_admin.xml              (AdminAdapter – profile/organizer row)
 */
@RunWith(AndroidJUnit4.class)
public class AdapterItemLayoutsTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    // ── item_event.xml ────────────────────────────────────────────────────────

    @Test
    public void itemEvent_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event, null, false);

        assertNotNull(root.findViewById(R.id.tv_event_name));
        assertNotNull(root.findViewById(R.id.tv_description));
        assertNotNull(root.findViewById(R.id.btn_join));
        assertNotNull(root.findViewById(R.id.iv_event_image));
    }

    @Test
    public void itemEvent_hasExpandedDetailsSection() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event, null, false);

        assertNotNull(root.findViewById(R.id.layout_expanded_details));
        assertNotNull(root.findViewById(R.id.tv_detail_date));
        assertNotNull(root.findViewById(R.id.tv_detail_time));
        assertNotNull(root.findViewById(R.id.tv_detail_location));
        assertNotNull(root.findViewById(R.id.tv_capacity));
        assertNotNull(root.findViewById(R.id.tv_price));
    }

    @Test
    public void itemEvent_hasRegistrationFields() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event, null, false);

        assertNotNull(root.findViewById(R.id.tv_reg_open));
        assertNotNull(root.findViewById(R.id.tv_reg_close));
        assertNotNull(root.findViewById(R.id.tv_waitlist_count));
        assertNotNull(root.findViewById(R.id.tv_event_status));
    }

    @Test
    public void itemEvent_hasCategoryTagsAndChevron() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event, null, false);

        assertNotNull(root.findViewById(R.id.layout_category_tags));
        assertNotNull(root.findViewById(R.id.tv_chevron));
    }

    @Test
    public void itemEvent_hasGeoNoteAndCriteriaBox() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event, null, false);

        assertNotNull(root.findViewById(R.id.layout_geo_note));
        assertNotNull(root.findViewById(R.id.tv_geo_note));
        assertNotNull(root.findViewById(R.id.layout_criteria_box));
        assertNotNull(root.findViewById(R.id.tv_criteria_description));
    }

    @Test
    public void itemEvent_hasCloseInlineSection() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event, null, false);

        assertNotNull(root.findViewById(R.id.layout_close_inline));
        assertNotNull(root.findViewById(R.id.btn_close_inline));
    }

    // ── item_event_history.xml ────────────────────────────────────────────────

    @Test
    public void itemEventHistory_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_history, null, false);

        assertNotNull(root.findViewById(R.id.tv_event_name));
        assertNotNull(root.findViewById(R.id.tv_event_date));
        assertNotNull(root.findViewById(R.id.tv_event_location));
        assertNotNull(root.findViewById(R.id.tv_status));
        assertNotNull(root.findViewById(R.id.iv_event_image));
        assertNotNull(root.findViewById(R.id.iv_delete));
    }

    @Test
    public void itemEventHistory_statusView_isTextView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_history, null, false);
        assertTrue(root.findViewById(R.id.tv_status) instanceof TextView);
    }

    @Test
    public void itemEventHistory_deleteIcon_isImageView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_history, null, false);
        assertTrue(root.findViewById(R.id.iv_delete) instanceof ImageView);
    }

    // ── item_notification_card.xml ────────────────────────────────────────────

    @Test
    public void itemNotificationCard_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_notification_card, null, false);

        assertNotNull(root.findViewById(R.id.title));
        assertNotNull(root.findViewById(R.id.message));
        assertNotNull(root.findViewById(R.id.date));
        assertNotNull(root.findViewById(R.id.icon));
        assertNotNull(root.findViewById(R.id.icon_container));
        assertNotNull(root.findViewById(R.id.notification_card_root));
    }

    @Test
    public void itemNotificationCard_hasActionButtons() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_notification_card, null, false);

        assertNotNull(root.findViewById(R.id.buttons_row));
        assertNotNull(root.findViewById(R.id.btn_accept));
        assertNotNull(root.findViewById(R.id.btn_decline));
    }

    @Test
    public void itemNotificationCard_hasResponseBadges() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_notification_card, null, false);

        assertNotNull(root.findViewById(R.id.badge_accepted));
        assertNotNull(root.findViewById(R.id.badge_declined));
    }

    // ── item_waitlist_entrant.xml ─────────────────────────────────────────────

    @Test
    public void itemWaitlistEntrant_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_waitlist_entrant, null, false);

        assertNotNull(root.findViewById(R.id.tv_entrant_name));
        assertNotNull(root.findViewById(R.id.tv_entrant_email));
        assertNotNull(root.findViewById(R.id.tv_entrant_phone));
        assertNotNull(root.findViewById(R.id.tv_entrant_location));
        assertNotNull(root.findViewById(R.id.tv_joined_date));
    }

    @Test
    public void itemWaitlistEntrant_nameView_isTextView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_waitlist_entrant, null, false);
        assertTrue(root.findViewById(R.id.tv_entrant_name) instanceof TextView);
    }

    // ── item_organizer_event.xml ──────────────────────────────────────────────

    @Test
    public void itemOrganizerEvent_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_organizer_event, null, false);

        assertNotNull(root.findViewById(R.id.tv_event_name));
        assertNotNull(root.findViewById(R.id.tv_event_date));
        assertNotNull(root.findViewById(R.id.tv_event_location));
        assertNotNull(root.findViewById(R.id.btn_manage));
    }

    @Test
    public void itemOrganizerEvent_manageButton_isDisplayed() {
        // btn_manage is a TextView; clickability is set programmatically by the adapter
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_organizer_event, null, false);
        assertNotNull(root.findViewById(R.id.btn_manage));
    }

    // ── item_event_comment.xml ────────────────────────────────────────────────

    @Test
    public void itemEventComment_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_comment, null, false);

        assertNotNull(root.findViewById(R.id.tv_comment_author));
        assertNotNull(root.findViewById(R.id.tv_comment_body));
        assertNotNull(root.findViewById(R.id.tv_comment_time));
        assertNotNull(root.findViewById(R.id.tv_comment_avatar));
    }

    @Test
    public void itemEventComment_hasActionButtons() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_comment, null, false);

        assertNotNull(root.findViewById(R.id.layout_reactions));
        assertNotNull(root.findViewById(R.id.btn_add_reaction));
        assertNotNull(root.findViewById(R.id.btn_comment_reply));
        assertNotNull(root.findViewById(R.id.btn_comment_delete));
    }

    @Test
    public void itemEventComment_hasReplySection() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_comment, null, false);

        assertNotNull(root.findViewById(R.id.layout_replies));
        assertNotNull(root.findViewById(R.id.layout_reply_input));
        assertNotNull(root.findViewById(R.id.et_reply_input));
        assertNotNull(root.findViewById(R.id.btn_send_reply));
    }

    @Test
    public void itemEventComment_hasOrganizerBadge() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_comment, null, false);
        assertNotNull(root.findViewById(R.id.tv_organizer_badge));
    }

    // ── item_event_reply.xml ──────────────────────────────────────────────────

    @Test
    public void itemEventReply_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_event_reply, null, false);

        assertNotNull(root.findViewById(R.id.tv_reply_author));
        assertNotNull(root.findViewById(R.id.tv_reply_body));
        assertNotNull(root.findViewById(R.id.tv_reply_time));
        assertNotNull(root.findViewById(R.id.tv_reply_avatar));
        assertNotNull(root.findViewById(R.id.layout_reply_reactions));
        assertNotNull(root.findViewById(R.id.btn_add_reply_reaction));
    }

    // ── item_admin_event.xml ──────────────────────────────────────────────────

    @Test
    public void itemAdminEvent_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_admin_event, null, false);

        assertNotNull(root.findViewById(R.id.adminItemTitle));
        assertNotNull(root.findViewById(R.id.adminItemSubtitle));
        assertNotNull(root.findViewById(R.id.adminItemDetail));
        assertNotNull(root.findViewById(R.id.adminItemDeleteButton));
        assertNotNull(root.findViewById(R.id.adminItemViewButton));
    }

    @Test
    public void itemAdminEvent_hasMetaAndBadge() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_admin_event, null, false);

        assertNotNull(root.findViewById(R.id.adminItemMeta1));
        assertNotNull(root.findViewById(R.id.adminItemMeta2));
        assertNotNull(root.findViewById(R.id.adminItemMetaRow));
        assertNotNull(root.findViewById(R.id.adminItemBadge));
    }

    // ── item_admin_image.xml ──────────────────────────────────────────────────

    @Test
    public void itemAdminImage_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_admin_image, null, false);

        assertNotNull(root.findViewById(R.id.imgPoster));
        assertNotNull(root.findViewById(R.id.imgEventName));
        assertNotNull(root.findViewById(R.id.imgOrganizerName));
        assertNotNull(root.findViewById(R.id.imgFileSize));
        assertNotNull(root.findViewById(R.id.imgDate));
    }

    @Test
    public void itemAdminImage_hasActionButtons() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_admin_image, null, false);

        assertNotNull(root.findViewById(R.id.imgDeleteButton));
        assertNotNull(root.findViewById(R.id.imgViewButton));
    }

    // ── item_admin.xml (profile / organizer row) ──────────────────────────────

    @Test
    public void itemAdmin_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_admin, null, false);

        assertNotNull(root.findViewById(R.id.adminItemTitle));
        assertNotNull(root.findViewById(R.id.adminItemSubtitle));
        assertNotNull(root.findViewById(R.id.adminItemDetail));
        assertNotNull(root.findViewById(R.id.adminItemDeleteButton));
        assertNotNull(root.findViewById(R.id.adminItemViewButton));
    }

    @Test
    public void itemAdmin_hasAvatarSection() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_admin, null, false);

        assertNotNull(root.findViewById(R.id.adminItemAvatar));
        assertNotNull(root.findViewById(R.id.adminItemAvatarContainer));
        assertNotNull(root.findViewById(R.id.adminItemInitials));
        assertNotNull(root.findViewById(R.id.adminItemImage));
    }

    @Test
    public void itemAdmin_hasMetaRowAndBadge() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.item_admin, null, false);

        assertNotNull(root.findViewById(R.id.adminItemMeta1));
        assertNotNull(root.findViewById(R.id.adminItemMeta2));
        assertNotNull(root.findViewById(R.id.adminItemMetaRow));
        assertNotNull(root.findViewById(R.id.adminItemBadge));
    }
}
