package com.example.videoeditor.colorfilter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.videoeditor.databinding.ColorFilterItemBinding

class ColorFilterRecyclerAdapter(
    private val list: List<ColorFilterItem>,
    private val listener: (Int) -> Unit
) :
    RecyclerView.Adapter<ColorFilterRecyclerAdapter.FilterHolder>() {

    override fun getItemCount() = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val filterBinding = ColorFilterItemBinding.inflate(layoutInflater, parent, false)
        return FilterHolder(filterBinding)
    }

    override fun onBindViewHolder(holder: FilterHolder, position: Int) {
        holder.bind(list[position])
        holder.itemView.setOnClickListener {
            listener(position)
        }
    }

    inner class FilterHolder(private val itemBinding: ColorFilterItemBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(item: ColorFilterItem) {
            itemBinding.filterItemImage.setImageResource(item.image)
        }
    }
}