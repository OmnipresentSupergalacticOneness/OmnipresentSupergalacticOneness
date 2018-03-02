package io.csabatechnology.android.omnipresent;

import android.content.Context;
import android.media.MediaPlayer;

/**
 * https://stackoverflow.com/questions/18254870/play-a-sound-from-res-raw
 */

class AudioPlayer {

    private MediaPlayer mMediaPlayer;

    private void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    void play(Context c, int rid) {
        if (rid == 0)
            return;

        stop();

        mMediaPlayer = MediaPlayer.create(c, rid);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                stop();
            }
        });

        mMediaPlayer.start();
    }

}