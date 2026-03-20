package `is`.xyz.mpv

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.ContentValues
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import `is`.xyz.mpv.databinding.FragmentVideoLibraryBinding

class VideoLibraryFragment : Fragment(R.layout.fragment_video_library) {

    private lateinit var binding: FragmentVideoLibraryBinding
    private val videos = mutableListOf<VideoItem>()
    private lateinit var adapter: VideoAdapter

    // Sort: 0=name, 1=date, 2=size, 3=duration
    private var sortMode = 1

    private val playerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* returned from player */ }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentVideoLibraryBinding.bind(view)

        adapter = VideoAdapter(videos,
            onPlay = { item -> playVideo(item) },
            onDelete = { item -> confirmDelete(item) },
            onShare = { item -> shareVideo(item) },
            onInfo = { item -> showInfo(item) }
        )

        val spanCount = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        binding.videoGrid.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.videoGrid.adapter = adapter

        binding.sortBtn.setOnClickListener { showSortMenu(it) }

        loadVideos()
    }

    private fun loadVideos() {
        binding.loadingSpinner.isVisible = true
        binding.emptyText.isVisible = false
        binding.videoGrid.isVisible = false

        Thread {
            val result = scanMediaStore()
            requireActivity().runOnUiThread {
                videos.clear()
                videos.addAll(result)
                applySortAndRefresh()
                binding.loadingSpinner.isVisible = false
                if (videos.isEmpty()) {
                    binding.emptyText.isVisible = true
                } else {
                    binding.videoGrid.isVisible = true
                    binding.videoCount.text = "${videos.size} videos"
                }
            }
        }.start()
    }

    private fun scanMediaStore(): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        val cursor = requireContext().contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        ) ?: return list

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "Unknown"
                val path = it.getString(pathCol) ?: continue
                val dur = it.getLong(durCol)
                val size = it.getLong(sizeCol)

                // Remove extension from display name
                val displayName = name.substringBeforeLast(".")

                list.add(VideoItem(id, displayName, path, dur, size))
            }
        }
        return list
    }

    private fun applySortAndRefresh() {
        val sorted = when (sortMode) {
            0 -> videos.sortedBy { it.title.lowercase() }
            1 -> videos // already sorted by date from MediaStore query
            2 -> videos.sortedByDescending { it.sizeBytes }
            3 -> videos.sortedByDescending { it.durationMs }
            else -> videos
        }
        videos.clear()
        videos.addAll(sorted)
        adapter.notifyDataSetChanged()
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, "Sort by Name")
        popup.menu.add(0, 1, 1, "Sort by Date")
        popup.menu.add(0, 2, 2, "Sort by Size")
        popup.menu.add(0, 3, 3, "Sort by Duration")
        popup.setOnMenuItemClickListener { item ->
            sortMode = item.itemId
            applySortAndRefresh()
            true
        }
        popup.show()
    }

    private fun playVideo(item: VideoItem) {
        val i = Intent()
        i.putExtra("filepath", item.path)
        i.setClass(requireContext(), MPVActivity::class.java)
        playerLauncher.launch(i)
    }

    private fun confirmDelete(item: VideoItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Video")
            .setMessage("Delete \"${item.title}\"?\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteVideo(item) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteVideo(item: VideoItem) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id
            )
            requireContext().contentResolver.delete(uri, null, null)
            val pos = videos.indexOfFirst { it.id == item.id }
            if (pos >= 0) {
                videos.removeAt(pos)
                adapter.notifyItemRemoved(pos)
                binding.videoCount.text = "${videos.size} videos"
                if (videos.isEmpty()) {
                    binding.videoGrid.isVisible = false
                    binding.emptyText.isVisible = true
                }
            }
            // Also clear saved position for this file
            VideoHistory.clearPosition(requireContext(), item.path)
            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not delete: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareVideo(item: VideoItem) {
        val uri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun showInfo(item: VideoItem) {
        val savedPos = VideoHistory.getPosition(requireContext(), item.path)
        val posText = if (savedPos > 0) Utils.prettyTime(savedPos.toInt()) else "Not started"
        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setMessage(
                "Duration: ${item.formattedDuration()}\n" +
                "Size: ${item.formattedSize()}\n" +
                "Last position: $posText\n\n" +
                "Path: ${item.path}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

    private inner class VideoAdapter(
        private val items: List<VideoItem>,
        private val onPlay: (VideoItem) -> Unit,
        private val onDelete: (VideoItem) -> Unit,
        private val onShare: (VideoItem) -> Unit,
        private val onInfo: (VideoItem) -> Unit
    ) : RecyclerView.Adapter<VideoAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
            val title: TextView = view.findViewById(R.id.videoTitle)
            val duration: TextView = view.findViewById(R.id.videoDuration)
            val progress: ProgressBar = view.findViewById(R.id.videoProgress)
            val menu: ImageButton = view.findViewById(R.id.videoMenu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.duration.text = item.formattedDuration()

            // Thumbnail via MediaStore
            val thumbUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id
            )
            try {
                val bmp = if (Build.VERSION.SDK_INT >= 29) {
                    requireContext().contentResolver.loadThumbnail(
                        thumbUri, android.util.Size(320, 180), null
                    )
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        requireContext().contentResolver,
                        item.id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                }
                holder.thumbnail.setImageBitmap(bmp)
            } catch (e: Exception) {
                holder.thumbnail.setImageResource(android.R.drawable.ic_media_play)
            }

            // Progress bar (resume position)
            val savedPos = VideoHistory.getPosition(requireContext(), item.path)
            if (savedPos > 0 && item.durationMs > 0) {
                val pct = ((savedPos * 1000f) / item.durationMs * 100).toInt().coerceIn(0, 100)
                holder.progress.progress = pct
                holder.progress.isVisible = true
            } else {
                holder.progress.isVisible = false
            }

            holder.itemView.setOnClickListener { onPlay(item) }
            holder.menu.setOnClickListener { anchor ->
                val popup = PopupMenu(requireContext(), anchor)
                popup.menu.add(0, 0, 0, "Play")
                popup.menu.add(0, 1, 1, "Delete")
                popup.menu.add(0, 2, 2, "Share")
                popup.menu.add(0, 3, 3, "Info")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        0 -> onPlay(item)
                        1 -> onDelete(item)
                        2 -> onShare(item)
                        3 -> onInfo(item)
                    }
                    true
                }
                popup.show()
            }
        }