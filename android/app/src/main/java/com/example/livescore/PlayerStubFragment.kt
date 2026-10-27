package com.example.livescore

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class PlayerStubFragment : Fragment() {
    companion object {
        fun newInstance(title: String) = PlayerStubFragment().apply {
            arguments = Bundle().apply { putString("title", title) }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = TextView(requireContext()).apply {
        text = "${arguments?.getString("title")} 준비 중"
        gravity = Gravity.CENTER
        setTextColor(android.graphics.Color.parseColor("#6B7280"))
        textSize = 14f
    }
}