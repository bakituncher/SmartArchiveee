package com.codenzi.ceparsivi

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codenzi.ceparsivi.databinding.SlideItemContainerBinding

class IntroSliderAdapter(private val introSlides: List<IntroSlide>) :
    RecyclerView.Adapter<IntroSliderAdapter.IntroSlideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroSlideViewHolder {
        val binding = SlideItemContainerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return IntroSlideViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return introSlides.size
    }

    override fun onBindViewHolder(holder: IntroSlideViewHolder, position: Int) {
        holder.bind(introSlides[position])
    }

    inner class IntroSlideViewHolder(private val binding: SlideItemContainerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(introSlide: IntroSlide) {
            binding.textTitle.text = introSlide.title
            binding.textDescription.text = introSlide.description
            binding.imageIcon.setImageResource(introSlide.icon)
        }
    }
}