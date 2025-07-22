package com.codenzi.ceparsivi

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.codenzi.ceparsivi.databinding.BottomSheetFileDetailsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.Locale

class FileDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFileDetailsBinding? = null
    private val binding get() = _binding!!
    private var archivedFile: ArchivedFile? = null

    // DÜZELTME: Listener arayüzüne onMoveClicked eklendi.
    interface FileDetailsListener {
        fun onShareClicked(file: ArchivedFile)
        fun onDeleteClicked(file: ArchivedFile)
        fun onMoveClicked(file: ArchivedFile)
    }

    var listener: FileDetailsListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            archivedFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("file", ArchivedFile::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable("file")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFileDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        archivedFile?.let { file ->
            binding.textViewFileNameDetails.text = file.fileName
            binding.textViewCategory.text = getString(R.string.details_category, file.category)
            binding.textViewDate.text = getString(R.string.details_date, file.dateAdded)
            binding.textViewSize.text = getString(R.string.details_size, formatBytes(file.size))

            binding.buttonShare.setOnClickListener {
                listener?.onShareClicked(file)
                dismiss()
            }
            binding.buttonDelete.setOnClickListener {
                listener?.onDeleteClicked(file)
                dismiss()
            }
            // YENİ EKLENDİ: Taşıma butonunun tıklanma olayı
            binding.buttonMove.setOnClickListener {
                listener?.onMoveClicked(file)
                dismiss()
            }
        } ?: dismiss()
    }

    private fun formatBytes(bytes: Long): String {
        var a = bytes
        if (-1000 < a && a < 1000) {
            return "$a B"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (a <= -999950 || a >= 999950) {
            a /= 1000
            ci.next()
        }
        return String.format(Locale.getDefault(), "%.1f %cB", a / 1000.0, ci.current())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(file: ArchivedFile): FileDetailsBottomSheet {
            val args = Bundle().apply {
                putParcelable("file", file)
            }
            return FileDetailsBottomSheet().apply {
                arguments = args
            }
        }
    }
}