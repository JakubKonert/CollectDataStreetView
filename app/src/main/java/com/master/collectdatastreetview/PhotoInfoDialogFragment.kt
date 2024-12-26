package com.master.collectdatastreetview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class PhotoInfoDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_PHOTO_INFO = "photo_info"

        fun newInstance(photoInfo: String): PhotoInfoDialogFragment {
            val fragment = PhotoInfoDialogFragment()
            val args = Bundle()
            args.putString(ARG_PHOTO_INFO, photoInfo)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_photo_info_dialog, container, false)

        val tvPhotoInfo = view.findViewById<TextView>(R.id.tvPhotoInfo)
        val btnCloseDialog = view.findViewById<Button>(R.id.btnCloseDialog)

        val photoInfo = arguments?.getString(ARG_PHOTO_INFO) ?: "Brak informacji o zdjęciu"
        tvPhotoInfo.text = photoInfo

        btnCloseDialog.setOnClickListener {
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}