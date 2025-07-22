package com.codenzi.ceparsivi

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CategoryEntryDialogFragment : DialogFragment() {

    // Bu listener, sonucu çağıran Activity'e geri göndermek için kullanılacak.
    interface CategoryDialogListener {
        fun onCategorySaved(newName: String, oldName: String?)
    }

    var listener: CategoryDialogListener? = null
    private var existingCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            existingCategory = it.getString(ARG_EXISTING_CATEGORY)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = if (existingCategory == null) getString(R.string.add_new_category) else getString(R.string.rename_category)

        // Güvenli arayüzü (layout) çağırıyoruz.
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_text, null, false)
        val editText = dialogView.findViewById<EditText>(R.id.dialogEditText)
        editText.setText(existingCategory ?: "")
        editText.requestFocus()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotBlank()) {
                    listener?.onCategorySaved(newName, existingCategory)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    companion object {
        private const val ARG_EXISTING_CATEGORY = "existing_category"

        fun newInstance(existingCategory: String?): CategoryEntryDialogFragment {
            val fragment = CategoryEntryDialogFragment()
            val args = Bundle()
            args.putString(ARG_EXISTING_CATEGORY, existingCategory)
            fragment.arguments = args
            return fragment
        }
    }
}
