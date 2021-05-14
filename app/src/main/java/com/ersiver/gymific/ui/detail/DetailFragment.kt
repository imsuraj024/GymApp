package com.ersiver.gymific.ui.detail

import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import at.huber.youtubeExtractor.VideoMeta
import at.huber.youtubeExtractor.YouTubeExtractor
import at.huber.youtubeExtractor.YtFile
import com.ersiver.gymific.R
import com.ersiver.gymific.databinding.FragmentDetailBinding
import com.ersiver.gymific.model.Workout
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailFragment : Fragment() {
    private val detailViewModel: DetailViewModel by viewModels()
    private lateinit var menuItem: MenuItem
    private val args: DetailFragmentArgs by navArgs()
    private lateinit var binding: FragmentDetailBinding
    private val workout: Workout by lazy {
        args.workout
    }
    private lateinit var toolbar: Toolbar
    private lateinit var playerView: PlayerView
    private var pausedTime: Long = 0

    private var exoPlayer: SimpleExoPlayer? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var videoUrl: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDetailBinding.inflate(inflater, container, false)
        binding.apply {
            viewModel = detailViewModel
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupToolbarWithNavigation()
        onOptionsItemSelected()
        playerView = binding.exoPlayerView
        detailViewModel.start(workout.id)

        detailViewModel.workout.observe(viewLifecycleOwner) { workout ->
            updateMenuItemIcon(workout.isSaved)
        }

        detailViewModel.workoutTimeMillis.observe(viewLifecycleOwner) { workoutTimeMillis ->
            binding.workoutProgress.setDuration(workoutTimeMillis)
        }

        detailViewModel.savedPausedTime.observe(viewLifecycleOwner) { savedPausedTime ->
            detailViewModel.manageTimer(savedPausedTime)
        }

        detailViewModel.runningTime.observe(viewLifecycleOwner) {
            binding.workoutProgress.updateProgressBar(it)
        }

        detailViewModel.pausedWorkoutTimeMillis.observe(viewLifecycleOwner) {
            pausedTime = it
        }

    }

    private fun setupToolbarWithNavigation() {
        toolbar = binding.toolbarDetail
        toolbar.navigationContentDescription = resources.getString(R.string.navigate_up)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun updateMenuItemIcon(saved: Boolean) {
        menuItem = toolbar.menu.getItem(0)
        val iconDrawable =
            if (saved) R.drawable.ic_favourite else R.drawable.ic_add_favourite
        menuItem.setIcon(iconDrawable)
    }

    private fun onOptionsItemSelected() {
        toolbar.setOnMenuItemClickListener {
            detailViewModel.setFavourite(workout)
            true
        }
    }

    override fun onPause() {
        super.onPause()
        detailViewModel.savePausedTime(workout.id, pausedTime)
        releasePlayer()
    }

    private fun releasePlayer() {
        if (exoPlayer == null) {
            return
        }
        playWhenReady = exoPlayer!!.playWhenReady
        playbackPosition = exoPlayer!!.currentPosition
        currentWindow = exoPlayer!!.currentWindowIndex
        exoPlayer!!.release()
        exoPlayer = null
    }

    private fun initPlayer(){
        exoPlayer = SimpleExoPlayer.Builder(requireContext()).build()
        // Bind the player to the view.
        playerView.player = exoPlayer


        detailViewModel.workout.observe(viewLifecycleOwner, Observer {
            videoUrl = it.iconCode
            Log.e("URL", videoUrl)
            object: YouTubeExtractor(requireContext()){
                override fun onExtractionComplete(
                    ytFiles: SparseArray<YtFile>?,
                    videoMeta: VideoMeta?
                ) {
                    if (ytFiles != null){
                        val itag = 137
                        val audioTag = 140
                        val videoUrl = ytFiles[itag].url
                        val audioUrl = ytFiles[audioTag].url

                        val audioSource: MediaSource = ProgressiveMediaSource
                            .Factory(DefaultHttpDataSource.Factory())
                            .createMediaSource(MediaItem.fromUri(audioUrl))
                        val videoSource: MediaSource = ProgressiveMediaSource
                            .Factory(DefaultHttpDataSource.Factory())
                            .createMediaSource(MediaItem.fromUri(videoUrl))

                        exoPlayer!!.setMediaSource(MergingMediaSource(true, videoSource, audioSource)
                            ,true)
                        exoPlayer!!.prepare()
                        exoPlayer!!.playWhenReady = true
                        exoPlayer!!.seekTo(currentWindow,playbackPosition)
                    }

                }

            }.extract(videoUrl,false,true)
        })
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onResume() {
        super.onResume()
        if(exoPlayer == null){
            initPlayer()
        }
    }

}