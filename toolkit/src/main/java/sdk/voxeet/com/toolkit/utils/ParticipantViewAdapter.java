package sdk.voxeet.com.toolkit.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.voxeet.android.media.MediaStream;
import com.voxeet.toolkit.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sdk.voxeet.com.toolkit.views.android.RoundedImageView;
import sdk.voxeet.com.toolkit.views.uitookit.nologic.VideoView;
import voxeet.com.sdk.models.ConferenceUserStatus;
import voxeet.com.sdk.models.impl.DefaultConferenceUser;

/**
 * Created by romainbenmansour on 22/02/2017.
 */
public class ParticipantViewAdapter extends RecyclerView.Adapter<ParticipantViewAdapter.ViewHolder> {

    private final String TAG = ParticipantViewAdapter.class.getSimpleName();

    private boolean namesEnabled = true;

    private List<DefaultConferenceUser> users;

    private Context context;

    private int avatarSize;

    private int lastPosition = -1;

    private int selectedPosition = -1;

    private IParticipantViewListener listener;

    private Map<String, MediaStream> mMediaStreamMap;
    private Map<String, MediaStream> mScreenShareMediaStreams;

    private int overlayColor;

    private int parentWidth;
    private int parentHeight;

    private String mRequestUserIdChanged;

    private ParticipantViewAdapter() {
        mScreenShareMediaStreams = new HashMap<>();
        mMediaStreamMap = new HashMap<>();
    }

    /**
     * Instantiates a new Participant view adapter.
     *
     * @param context the context
     */
    public ParticipantViewAdapter(Context context) {
        this();
        this.overlayColor = context.getResources().getColor(R.color.blue);

        this.context = context;

        this.users = new ArrayList<>();

        this.namesEnabled = true;

        this.avatarSize = context.getResources().getDimensionPixelSize(R.dimen.meeting_list_avatar_double);
    }

    /**
     * Removes the conference user if being part of the conference.
     *
     * @param conferenceUser the conference user
     */
    public void removeUser(DefaultConferenceUser conferenceUser) {
        if (users.contains(conferenceUser))
            users.remove(conferenceUser);
    }

    /**
     * Adds the conference user if not already added.
     *
     * @param conferenceUser the conference user
     */
    public void addUser(DefaultConferenceUser conferenceUser) {
        if (!users.contains(conferenceUser))
            users.add(conferenceUser);
    }

    /**
     * Sets the color when a conference user has been selected.
     *
     * @param color the color
     */
    public void setOverlayColor(int color) {
        overlayColor = color;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_participant_view_cell, parent, false);

        parentWidth = parent.getWidth();

        parentHeight = parent.getHeight();

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        Log.d(TAG, "onBindViewHolder: " + position);
        final DefaultConferenceUser user = getItem(position);

//        if ((parentWidth / getItemCount()) <= parentHeight) {
//            holder.itemView.getLayoutParams().height = parentWidth / getItemCount();
//            holder.itemView.getLayoutParams().width = parentWidth / getItemCount();
//        }

        if (user.getStatus() != null && !user.getStatus().equalsIgnoreCase(ConferenceUserStatus.ON_AIR.name())) {
            holder.itemView.setAlpha(0.5f);
        } else
            holder.itemView.setAlpha(1f);

        holder.name.setText(user.getUserInfo().getName());
        holder.name.setVisibility(namesEnabled ? View.VISIBLE : View.GONE);

        loadViaPicasso(user, holder.avatar);

        if (selectedPosition == position) {
            holder.name.setTypeface(Typeface.DEFAULT_BOLD);
            holder.name.setTextColor(context.getResources().getColor(R.color.white));

            holder.overlay.setVisibility(View.VISIBLE);
            holder.overlay.setBackgroundColor(overlayColor);
        } else {
            holder.name.setTypeface(Typeface.DEFAULT);
            holder.name.setTextColor(context.getResources().getColor(R.color.grey999));

            holder.overlay.setVisibility(View.GONE);
        }

        //updateMediaScreenFor(user.getUserId(), holder);

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {

                if (selectedPosition == holder.getAdapterPosition()) {
                    selectedPosition = -1;

                    if (listener != null)
                        listener.onParticipantUnselected(user);
                }
                return true;
            }
        });

        if (null != mRequestUserIdChanged && mRequestUserIdChanged.equals(user.getUserId())) {
            VideoView.MediaStreamType type = null;
            MediaStream stream = null;
            if (hasScreenShareMediaStream(mRequestUserIdChanged)) {
                stream = getScreenShareMediaStream(mRequestUserIdChanged);
                type = getPrevious(mRequestUserIdChanged, VideoView.MediaStreamType.SCREEN_SHARE);
            } else if (hasCameraMediaStream(mRequestUserIdChanged)) {
                stream = getCameraMediaStream(mRequestUserIdChanged);
                type = getPrevious(mRequestUserIdChanged, VideoView.MediaStreamType.VIDEO);
            }

            if (null != type) {
                loadStreamOnto(mRequestUserIdChanged, type, holder);

                if (listener != null)
                    listener.onParticipantSelected(user, stream);
            }

            //prevent any modification until next event
            mRequestUserIdChanged = null;
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userId = user.getUserId();
                //toggle media screen call next stream
                VideoView.MediaStreamType current_type = holder.videoView.getCurrentMediaStreamType();

                VideoView.MediaStreamType next = getNext(user.getUserId(), current_type);

                loadStreamOnto(userId, next, holder);

                //now get the one for the main view
                next = getNext(user.getUserId(), next);

                MediaStream stream = null;

                switch (next) {
                    case SCREEN_SHARE:
                        stream = getScreenShareMediaStream(userId);
                        break;
                    case VIDEO:
                        stream = getCameraMediaStream(userId);
                        break;
                }

                if (listener != null)
                    listener.onParticipantSelected(user, stream);
            }
        });

        setAnimation(holder.itemView, position);
    }

    private void loadStreamOnto(String userId, VideoView.MediaStreamType type, ViewHolder holder) {
        holder.videoView.setAutoUnAttach(true);
        switch (type) {
            case NONE:
                holder.videoView.unAttach();
                holder.videoView.setVisibility(View.GONE);
                break;
            case SCREEN_SHARE:
                holder.videoView.attach(userId, getScreenShareMediaStream(userId), true);
                holder.videoView.setVisibility(View.VISIBLE);
                break;
            case VIDEO:
                holder.videoView.attach(userId, getCameraMediaStream(userId), true);
                holder.videoView.setVisibility(View.VISIBLE);
                break;
        }
    }

    private VideoView.MediaStreamType getPrevious(String userId, VideoView.MediaStreamType type) {
        switch (type) {
            case NONE:
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                break;
            case SCREEN_SHARE:
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                break;
            case VIDEO:
        }
        return VideoView.MediaStreamType.NONE;
    }

    private VideoView.MediaStreamType getNext(String userId, VideoView.MediaStreamType type) {
        switch (type) {
            case NONE:
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                break;
            case VIDEO:
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                break;
            case SCREEN_SHARE:
        }
        return VideoView.MediaStreamType.NONE;
    }

    /**
     * Animation when a new participant is joining the conference.
     *
     * @param viewToAnimate
     * @param position
     */
    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) { // If the bound view wasn't previously displayed on screen, it's animated
            AlphaAnimation animation = new AlphaAnimation(0.2f, 1.0f);
            animation.setDuration(500);
            animation.setFillAfter(true);
            viewToAnimate.startAnimation(animation);

            lastPosition = position;
        }
    }

    private DefaultConferenceUser getItem(int position) {
        return users.get(position);
    }

    /**
     * Displays user's avatar in the specified imageView.
     *
     * @param conferenceUser
     * @param imageView
     */
    private void loadViaPicasso(DefaultConferenceUser conferenceUser, ImageView imageView) {
        try {
            Picasso.with(context)
                    .load(conferenceUser.getUserInfo().getAvatarUrl())
                    .noFade()
                    .resize(avatarSize, avatarSize)
                    .error(R.color.red)
                    .into(imageView);
        } catch (Exception e) {
            Log.e(TAG, "error " + e.getMessage());
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    /**
     * Sets participant listener.
     *
     * @param listener the listener
     */
    public void setParticipantListener(IParticipantViewListener listener) {
        this.listener = listener;
    }

    /**
     * On media stream updated. Needs to call notifyDataSetChanged to refresh the videos.
     *
     * @param mediaStreams the media streams
     */
    public void onMediaStreamUpdated(@Nullable String userId, @NonNull Map<String, MediaStream> mediaStreams) {
        mMediaStreamMap = mediaStreams;
        if (null != userId && mediaStreams.containsKey(userId)) {
            MediaStream stream = mediaStreams.get(userId);
            if (null != stream && stream.hasVideo()) {
                mRequestUserIdChanged = userId;
            }
        }

        notifyDataSetChanged();
    }

    public void onScreenShareMediaStreamUpdated(String userId, Map<String, MediaStream> screenSharemediaStreams) {
        mScreenShareMediaStreams = screenSharemediaStreams;

        if (screenSharemediaStreams.containsKey(userId)) {
            MediaStream stream = screenSharemediaStreams.get(userId);
            if (null != stream && stream.isScreenShare()) {
                mRequestUserIdChanged = userId;
            }
        }

        notifyDataSetChanged();
    }

    /**
     * Clear participants.
     */
    public void clearParticipants() {
        this.users.clear();
    }

    /**
     * Sets names enabled.
     *
     * @param enabled the enabled
     */
    public void setNamesEnabled(boolean enabled) {
        namesEnabled = enabled;
    }

    /**
     * The type View holder.
     */
    class ViewHolder extends RecyclerView.ViewHolder {
        private VideoView videoView;

        private TextView name;

        private RoundedImageView avatar;

        private ImageView overlay;

        /**
         * Instantiates a new View holder.
         *
         * @param view the view
         */
        ViewHolder(@NonNull View view) {
            super(view);

            videoView = (VideoView) view.findViewById(R.id.participant_video_view);

            name = (TextView) view.findViewById(R.id.name);

            overlay = (ImageView) view.findViewById(R.id.overlay_avatar);

            avatar = (RoundedImageView) view.findViewById(R.id.avatar);
        }
    }

    @Nullable
    private MediaStream getScreenShareMediaStream(@NonNull String userId) {
        if (hasScreenShareMediaStream(userId)) {
            return mScreenShareMediaStreams.get(userId);
        }
        return null;
    }

    @Nullable
    private MediaStream getCameraMediaStream(@NonNull String userId) {
        if (hasCameraMediaStream(userId)) {
            return mMediaStreamMap.get(userId);
        }
        return null;
    }

    private boolean hasScreenShareMediaStream(@NonNull String userId) {
        if (null != mScreenShareMediaStreams && mScreenShareMediaStreams.containsKey(userId))
            return mScreenShareMediaStreams.get(userId).isScreenShare();
        return false;
    }

    private boolean hasCameraMediaStream(@NonNull String userId) {
        if (null != mMediaStreamMap && mMediaStreamMap.containsKey(userId))
            return mMediaStreamMap.get(userId).hasVideo();
        return false;
    }
}
